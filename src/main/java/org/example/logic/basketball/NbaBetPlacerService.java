package org.example.logic.basketball;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.codec.digest.DigestUtils;
import org.example.constant.BetStatus;
import org.example.db.basketball.NbaBetPlacerDao;
import org.example.model.*;
import org.example.util.EncoderUtility;
import org.example.util.HttpUtil;
import org.example.util.PropertiesLoaderUtil;
import org.example.util.SoundUtil;

import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.example.logic.basketball.NbaPointsService.DESIRED_BET_NAME_HANDICAP;
import static org.example.logic.basketball.NbaPointsService.DESIRED_BET_NAME_TOTAL_POINTS;

public class NbaBetPlacerService {

    private final NbaOldMatchesService nbaOldMatchesService;
    private final NbaBetPlacerDao nbaBetPlacerDao;

    private static final Map<String, Double> minimumBetLineHistory = new HashMap<>();
    private static final Map<String, Double> maximumBetLineHistory = new HashMap<>();
    private static final Integer MIN_POINTS_OVER = 205;
    private static final Integer MIN_POINTS_UNDER = 230;

    private static final Integer AMOUNT_PESOS_SAFEST_BET = 1_000;
    private static final Integer COMBINATIONS_SAFEST_MINIMUM_COUNT = 2;
    private static final Integer COMBINATIONS_SAFEST_MAXIMUM_COUNT = 4;

    private static final Integer AMOUNT_PESOS_MINIMUM_BET = 500;
    private static final Integer COMBINATIONS_AGGRESSIVE_MINIMUM_COUNT = 3;
    private static final Integer COMBINATIONS_AGGRESSIVE_MAXIMUM_COUNT = 4;

    // Used in Safe Bets
    private static final Integer DESIRED_TOTAL_POINTS_AVERAGE_DIFFERENCE = 20;
    private static final Integer DESIRED_HANDICAP_DIFFERENCE = 17;

    // Used in aggressive Bets
    private static final Integer GRACE_POINTS_AI_AVERAGE_SUBTRACT = 8;
    private static final Integer GRACE_POINTS_AI_HANDICAP_SUBTRACT = 10;


    private static final String BET_PLAY_LOGIN_URL = "https://betplay.com.co/reverse-proxy/accounts/sessions/complete";
    private static final String KAMBI_AUTH_URL = "https://cf-mt-auth-api.kambicdn.com/player/api/v2019/betplay/punter/login.json?market=CO&lang=es_CO&channel_id=1&client_id=2&settings=true";
    private static final String KAMBI_PLACE_BET_URL = "https://cf-mt-auth-api.kambicdn.com/player/api/v2019/betplay/coupon.json?lang=es_CO&market=CO&client_id=2&channel_id=1&";
    private static final String KAMBI_COMBINED_BETS_SAME_MATCH_URL = "https://us1.offering-api.kambicdn.com/offering/v2018/betplay/onDemandPricing/event/%s/outcome/%s.json";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static String jsonCredentialsBetPlay;
    private static String tokenLoginKambi; //localStorage.getItem("kambiToken")


    static {
        Properties properties = PropertiesLoaderUtil.loadProperties("credentials.properties");

        String betPlayCredentials = properties.getProperty("BET_PLAY_CREDENTIALS");
        if (betPlayCredentials == null || betPlayCredentials.isEmpty()) {
            throw new RuntimeException("BET_PLAY_CREDENTIALS can not be empty in credentials.properties");
        }

        NbaBetPlacerService.jsonCredentialsBetPlay = betPlayCredentials;
    }


    public NbaBetPlacerService(NbaOldMatchesService nbaOldMatchesService, NbaBetPlacerDao nbaBetPlacerDao) {
        this.nbaOldMatchesService = nbaOldMatchesService;
        this.nbaBetPlacerDao = nbaBetPlacerDao;
    }

    public void placeBet(List<EventNbaPoints> matchesPointsOdd, Map<String, List<AIResponse>> aiNbaMatchPoints, boolean placeAutomaticBet) throws Exception {
        placeSafeBets(matchesPointsOdd, aiNbaMatchPoints, placeAutomaticBet);
        placeAIAverageAggressiveBets(matchesPointsOdd, aiNbaMatchPoints, placeAutomaticBet);
    }

    /**
     * Place bets using the AI average score. Aggressive
     */
    private void placeAIAverageAggressiveBets(List<EventNbaPoints> matchesPointsOdd, Map<String, List<AIResponse>> aiNbaMatchPoints, boolean placeAutomaticBet) throws Exception {
        Map<String, BetPlacedData> averageAIMatchLine = new HashMap<>();
        Map<String, BetPlacedData> handicapAIMatchLine = new HashMap<>();
        for (EventNbaPoints eventNbaPoints : matchesPointsOdd) {
            try {
                List<JsonNode> linePoints = eventNbaPoints.pointEvents();

                Map<String, List<JsonNode>> linePointByEventType = linePoints.stream()
                        .collect(Collectors.groupingBy(line -> line.findPath("criterion").findPath("label").textValue()));

                findAIAverageAggressiveLinesInMatches(eventNbaPoints, linePointByEventType.get(DESIRED_BET_NAME_TOTAL_POINTS), averageAIMatchLine, aiNbaMatchPoints);
                findAIHandicapAggressiveLinesInMatches(eventNbaPoints, linePointByEventType.get(DESIRED_BET_NAME_HANDICAP), handicapAIMatchLine, aiNbaMatchPoints);
            } catch (Exception e) {
                System.err.println("Error in Aggressive Bets finding minimum-maximum and handicap difference line points in match " + eventNbaPoints.matchMame() + " event id: " + eventNbaPoints.id() + " game date: " + eventNbaPoints.gameDate().toLocalDateTime());
                e.printStackTrace();
            }
        }

        List<BetPlacedData> betsAverageAIData = new ArrayList<>(averageAIMatchLine.values());
        List<BetPlacedData> betsHandicapAIData = new ArrayList<>(handicapAIMatchLine.values());
        betsAverageAIData.addAll(betsHandicapAIData);

        if (placeAutomaticBet) {
            List<List<BetPlacedData>> combinations = generateAllBetCombinations(betsAverageAIData);
            combinations = getCombinationsNotBetAlready(combinations).stream()
                    .filter(list ->  list.size() >= COMBINATIONS_AGGRESSIVE_MINIMUM_COUNT && list.size() <= COMBINATIONS_AGGRESSIVE_MAXIMUM_COUNT)
                    .collect(Collectors.toList());
            Collections.shuffle(combinations, new Random());

            int maxCombinationSize = 25;
            if (combinations.size() > maxCombinationSize) {
                combinations.subList(maxCombinationSize, combinations.size()).clear();
            }

            placeBetOfAGivenCollection(combinations, AMOUNT_PESOS_MINIMUM_BET);
            System.out.println("Finished placing Aggressive bets");
        }
    }

    /**
     * Place bets using the minimum and maximum lines as long as they satisfy the AI response minus a constant
     */
    private void placeSafeBets(List<EventNbaPoints> matchesPointsOdd, Map<String, List<AIResponse>> aiNbaMatchPoints, boolean placeAutomaticBet) throws Exception {
        Map<String, BetPlacedData> minimumMatchLine = new HashMap<>();
        Map<String, BetPlacedData> maximumMatchLine = new HashMap<>();
        Map<String, BetPlacedData> handicapMaxDifference = new HashMap<>();

        for (EventNbaPoints eventNbaPoints : matchesPointsOdd) {
            try {
                List<JsonNode> linePoints = eventNbaPoints.pointEvents();

                Map<String, List<JsonNode>> linePointByEventType = linePoints.stream()
                        .collect(Collectors.groupingBy(line -> line.findPath("criterion").findPath("label").textValue()));

                findMinimumAndMaximumLinesInMatches(eventNbaPoints, linePointByEventType.get(DESIRED_BET_NAME_TOTAL_POINTS), minimumMatchLine, maximumMatchLine);
                findMaximumHandicapDifference(eventNbaPoints, linePointByEventType.get(DESIRED_BET_NAME_HANDICAP), handicapMaxDifference);
            } catch (Exception e) {
                System.err.println("Error in Safe Bets finding minimum-maximum and handicap difference line points in match " + eventNbaPoints.matchMame() + " event id: " + eventNbaPoints.id() + " game date: " + eventNbaPoints.gameDate().toLocalDateTime());
                e.printStackTrace();
            }
        }

        List<BetPlacedData> betsSatisfyAIData = findMinimumMaximumBetsSatisfyAIData(minimumMatchLine, aiNbaMatchPoints);
        List<BetPlacedData> betsSatisfyAIData2 = findMinimumMaximumBetsSatisfyAIData(maximumMatchLine, aiNbaMatchPoints);
        List<BetPlacedData> betsSatisfyAIHandicap = findHandicapSafeBetsSatisfyAIData(handicapMaxDifference, aiNbaMatchPoints);

        betsSatisfyAIData.addAll(betsSatisfyAIData2);
        betsSatisfyAIData.addAll(betsSatisfyAIHandicap);

        if (placeAutomaticBet) {
            List<List<BetPlacedData>> combinations = generateAllBetCombinations(betsSatisfyAIData);
            combinations = getCombinationsNotBetAlready(combinations).stream()
                    .filter(list ->  list.size() >= COMBINATIONS_SAFEST_MINIMUM_COUNT && list.size() <= COMBINATIONS_SAFEST_MAXIMUM_COUNT)
                    .toList();

            placeBetOfAGivenCollection(combinations, AMOUNT_PESOS_SAFEST_BET);
            System.out.println("Finished placing Safe bets");
        }

    }

    private void placeBetOfAGivenCollection(
            List<List<BetPlacedData>> combinations,
            Integer amount
    ) throws Exception {
        if (!combinations.isEmpty()) {
            if (tokenLoginKambi == null || tokenLoginKambi.isEmpty()) {
                tokenLoginKambi = loginBetPlay();
            }
            String bearerToken = loginKambi(tokenLoginKambi);

            for (List<BetPlacedData> combination : combinations) {
                TimeUnit.SECONDS.sleep(new Random().nextInt(1, 2));
                boolean betPlaced = placeBetInBetPlay(combination, bearerToken, amount);
                if (betPlaced) {
                    persistCombinations(combination, amount);
                }
            }
        }
    }

    private void findMaximumHandicapDifference(EventNbaPoints eventNbaPoints, List<JsonNode> linePoints, Map<String, BetPlacedData> handicapMaxDifference) {
        String matchName = eventNbaPoints.team1().getAlias() + " - " + eventNbaPoints.team2().getAlias();

        for (JsonNode linePoint : linePoints) {
            JsonNode outcome = linePoint.findValue("outcomes");
            for (int i = 0; i < 2; i++) {
                String type = outcome.get(i).findValue("type").asText();
                long id = outcome.get(i).findValue("id").longValue();
                double odds = outcome.get(i).findValue("odds").doubleValue() / 1000;
                double line = outcome.get(i).findValue("line").doubleValue() / 1000;

                BetPlacedData betPlacedData = new BetPlacedData(eventNbaPoints.id(), matchName, eventNbaPoints.gameDate(), odds, line, id, null, type);
                handicapMaxDifference.compute(matchName,
                        (k, v) -> {
                            if (v == null || (betPlacedData.line() > v.line() && betPlacedData.line() % 1 != 0)) {
                                return betPlacedData;
                            } else {
                                return v;
                            }
                        });
            }
        }
    }

    private List<BetPlacedData> findMinimumMaximumBetsSatisfyAIData(Map<String, BetPlacedData> minimumMatchLine, Map<String, List<AIResponse>> aiNbaMatchPoints) {
        List<BetPlacedData> returnList = new ArrayList<>();
        for (Map.Entry<String, BetPlacedData> BetPlacedDataEntry : minimumMatchLine.entrySet()) {
            BetPlacedData BetPlacedData = BetPlacedDataEntry.getValue();
            List<AIResponse> aiResponses = aiNbaMatchPoints.get(BetPlacedDataEntry.getKey());
            if (aiResponses != null && !aiResponses.isEmpty()) {
                Double averagePointsAI = calculateAIResponseTotalPointsAverage(aiResponses);

                if ("OT_OVER".equals(BetPlacedData.betType())) {
                    if (averagePointsAI - DESIRED_TOTAL_POINTS_AVERAGE_DIFFERENCE >= BetPlacedData.line() && MIN_POINTS_OVER >= BetPlacedData.line()) {
                        System.out.println("You should bet Total points over in match: " + BetPlacedDataEntry.getKey() + " with a line of " + BetPlacedData.line() +
                                " and a odd of " + BetPlacedData.odds() + " and a AI prediction of " + averagePointsAI + " difference points of " + (averagePointsAI - BetPlacedData.line()) + " outcomeId: " + BetPlacedData.outcomeId());
                        returnList.add(BetPlacedData.withAIPrediction(averagePointsAI));
                    }
                } else {
                    if (averagePointsAI + DESIRED_TOTAL_POINTS_AVERAGE_DIFFERENCE <= BetPlacedData.line() && BetPlacedData.line() >= MIN_POINTS_UNDER) {
                        System.out.println("You should bet Total points under in match: " + BetPlacedDataEntry.getKey() + " with a line of " + BetPlacedData.line() +
                                " and a odd of " + BetPlacedData.odds() + " and a AI prediction of " + averagePointsAI + " difference points of " + (averagePointsAI - BetPlacedData.line()) + " outcomeId: " + BetPlacedData.outcomeId());
                        returnList.add(BetPlacedData.withAIPrediction(averagePointsAI));
                    }
                }
            } else {
                System.out.println("Could not find AI response for match " + BetPlacedDataEntry.getKey());
            }
        }

        return returnList;
    }

    private List<BetPlacedData> findHandicapSafeBetsSatisfyAIData(Map<String, BetPlacedData> handicapMatchLine, Map<String, List<AIResponse>> aiNbaMatchPoints) {
        List<BetPlacedData> returnList = new ArrayList<>();

        for (Map.Entry<String, BetPlacedData> BetPlacedDataEntry : handicapMatchLine.entrySet()) {
            BetPlacedData BetPlacedData = BetPlacedDataEntry.getValue();
            List<AIResponse> aiResponses = aiNbaMatchPoints.get(BetPlacedDataEntry.getKey());
            if (aiResponses != null && !aiResponses.isEmpty()) {
                String team1 = aiResponses.getFirst().team1();
                Double team1Points = calculateAIResponsePointsByTeamAverage(aiResponses, true);
                String team2 = aiResponses.getFirst().team2();
                Double team2Points = calculateAIResponsePointsByTeamAverage(aiResponses, false);

                Double betLine = BetPlacedData.line();
                Double differencePointsAi = Math.abs(team1Points - team2Points);
                if (betLine >= DESIRED_HANDICAP_DIFFERENCE && DESIRED_HANDICAP_DIFFERENCE > differencePointsAi) {
                    System.out.println("You should bet Handicap points in match: " + BetPlacedDataEntry.getKey() + " with a handicap of " + betLine + " points " +
                            " and a odd of " + BetPlacedData.odds() + " and a AI prediction of " + team1 + " expected points: " + team1Points + " and " + team2 + " expected points " + team2Points);
                    returnList.add(BetPlacedData.withAIPrediction(differencePointsAi));
                }
            } else {
                System.out.println("Could not find AI response for match " + BetPlacedDataEntry.getKey());
            }
        }

        return returnList;
    }

    private void findAIAverageAggressiveLinesInMatches(EventNbaPoints eventNbaPoints, List<JsonNode> linePoints, Map<String, BetPlacedData> averageAIMatchLine, Map<String, List<AIResponse>> aiResponses) {
        String matchName = eventNbaPoints.team1().getAlias() + " - " + eventNbaPoints.team2().getAlias();
        List<AIResponse> aiListResponses = aiResponses.get(matchName);
        if (aiListResponses != null && !aiListResponses.isEmpty()) {
            double averagePointsAI = calculateAIResponseTotalPointsAverage(aiListResponses);
            double desiredLineAveragePointsAI = averagePointsAI - 0.5; // Use the line of the AI response -0.5, I.E: AI response 210 use Line 209.5
            desiredLineAveragePointsAI -= GRACE_POINTS_AI_AVERAGE_SUBTRACT; // Giving a 3 points grace

            outerLoop:
            for (JsonNode linePoint : linePoints) {
                JsonNode outcome = linePoint.findValue("outcomes");

                double checkLine = outcome.get(0).findValue("line").doubleValue() / 1000;
                if (checkLine != desiredLineAveragePointsAI) {
                    continue;
                }

                for (int i = 0; i < 2; i++) {
                    String type = outcome.get(i).findValue("type").asText();
                    long id = outcome.get(i).findValue("id").longValue();
                    double odds = outcome.get(i).findValue("odds").doubleValue() / 1000;
                    double line = outcome.get(i).findValue("line").doubleValue() / 1000;

                    if ("OT_OVER".equals(type) && line == desiredLineAveragePointsAI) {
                        BetPlacedData betPlacedData = new BetPlacedData(eventNbaPoints.id(), matchName, eventNbaPoints.gameDate(), odds, line, id, averagePointsAI, "OT_OVER");
                        averageAIMatchLine.put(matchName, betPlacedData);
                        break outerLoop;
                    }
                }
            }
        } else {
            System.out.println("Could not find AI response for match " + matchName);
        }
    }

    private void findAIHandicapAggressiveLinesInMatches(EventNbaPoints eventNbaPoints, List<JsonNode> linePoints, Map<String, BetPlacedData> averageAIMatchLine, Map<String, List<AIResponse>> aiResponses) {
        String matchName = eventNbaPoints.team1().getAlias() + " - " + eventNbaPoints.team2().getAlias();
        List<AIResponse> aiListResponses = aiResponses.get(matchName);
        if (aiListResponses != null && !aiListResponses.isEmpty()) {
            Double team1Points = calculateAIResponsePointsByTeamAverage(aiListResponses, true);
            Double team2Points = calculateAIResponsePointsByTeamAverage(aiListResponses, false);
            double aiDifference = Math.abs(team1Points - team2Points);
            double desiredHandicapPointsAI = aiDifference - 0.5; // Use the line of the AI response +0.5, I.E: AI handicap response 10 use Line 9.5
            desiredHandicapPointsAI += GRACE_POINTS_AI_HANDICAP_SUBTRACT; // Giving x points grace

            for (JsonNode linePoint : linePoints) {
                JsonNode outcome = linePoint.findValue("outcomes");

                double checkLineTeam1 = outcome.get(0).findValue("line").doubleValue() / 1000;
                double checkLineTeam2 = outcome.get(1).findValue("line").doubleValue() / 1000;
                int index;
                if (checkLineTeam1 == desiredHandicapPointsAI) {
                    index = 0;
                } else if (checkLineTeam2 == desiredHandicapPointsAI) {
                    index = 1;
                } else {
                    continue ;
                }

                String type = outcome.get(index).findValue("type").asText();
                long id = outcome.get(index).findValue("id").longValue();
                double odds = outcome.get(index).findValue("odds").doubleValue() / 1000;
                double line = outcome.get(index).findValue("line").doubleValue() / 1000;

                if (line == desiredHandicapPointsAI) {
                    BetPlacedData betPlacedData = new BetPlacedData(eventNbaPoints.id(),matchName, eventNbaPoints.gameDate(), odds, line, id, aiDifference, type);
                    averageAIMatchLine.put(matchName, betPlacedData);
                    break;
                }

            }
        } else {
            System.out.println("Could not find AI response for match " + matchName);
        }
    }

    private void findMinimumAndMaximumLinesInMatches(EventNbaPoints eventNbaPoints, List<JsonNode> linePoints, Map<String, BetPlacedData> minimumMatchLine, Map<String, BetPlacedData> maximumMatchLine) {
        String matchName = eventNbaPoints.team1().getAlias() + " - " + eventNbaPoints.team2().getAlias();

        for (JsonNode linePoint : linePoints) {
            JsonNode outcome = linePoint.findValue("outcomes");
            for (int i = 0; i < 2; i++) {
                String type = outcome.get(i).findValue("type").asText();
                long id = outcome.get(i).findValue("id").longValue();
                double odds = outcome.get(i).findValue("odds").doubleValue() / 1000;
                double line = outcome.get(i).findValue("line").doubleValue() / 1000;

                if ("OT_OVER".equals(type)) {
                    BetPlacedData betPlacedData = new BetPlacedData(eventNbaPoints.id(), matchName, eventNbaPoints.gameDate(), odds, line, id, null, "OT_OVER");
                    minimumMatchLine.compute(matchName,
                            (k, v) -> {
                                if (v == null || (betPlacedData.line() < v.line() && betPlacedData.line() % 1 != 0)) {
                                    return betPlacedData;
                                } else {
                                    return v;
                                }
                            });
                } else {
                    BetPlacedData BetPlacedData = new BetPlacedData(eventNbaPoints.id(), matchName, eventNbaPoints.gameDate(), odds, line, id, null, "OT_UNDER");
                    maximumMatchLine.compute(matchName,
                            (k, v) -> {
                                if (v == null || (BetPlacedData.line() > v.line() && BetPlacedData.line() % 1 != 0)) {
                                    return BetPlacedData;
                                } else {
                                    return v;
                                }
                            });
                }
            }
        }

        try {
            Double newLine = minimumMatchLine.get(matchName).line();
            Double newOdds = minimumMatchLine.get(matchName).odds();
            if (!minimumBetLineHistory.containsKey(matchName) || newLine < minimumBetLineHistory.get(matchName)) {
                System.out.println("*** New minimum Line found for the match " + matchName + " line: " + newLine + " odds: " + newOdds);
                minimumBetLineHistory.put(matchName, newLine);
            }

            newLine = maximumMatchLine.get(matchName).line();
            newOdds = maximumMatchLine.get(matchName).odds();
            if (!maximumBetLineHistory.containsKey(matchName) || newLine > maximumBetLineHistory.get(matchName)) {
                System.out.println("*** New maximum Line found for the match " + matchName + " line: " + newLine + " odds: " + newOdds);
                maximumBetLineHistory.put(matchName, newLine);
            }

        } catch (Exception e) {
            System.err.println("ERROR IN MATCH NAME " + matchName + " " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<List<BetPlacedData>> getCombinationsNotBetAlready(List<List<BetPlacedData>> combinations) {
        List<List<BetPlacedData>> newReturnList = new ArrayList<>();

        try {
            for (List<BetPlacedData> combination : combinations) {
                String hashBetCombination = hashBetCombination(combination);

                Optional<BetPlacedParent> optionalBetPlacedParent = nbaBetPlacerDao.findByHashIdentifier(hashBetCombination, "total_odd", "desc");
                if (optionalBetPlacedParent.isEmpty() ||
                        (calculateTotalOddFromCombinations(combination) > optionalBetPlacedParent.get().totalOdd() + 0.01d) ||
                        calculateTotalLinePointsFromCombinations(combination) < nbaBetPlacerDao.findMinTotalLinePointsParentBetByHash(hashBetCombination)) {
                    newReturnList.add(combination);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return newReturnList;
    }


    private boolean placeBetInBetPlay(List<BetPlacedData> combination, String bearerToken, Integer amount) {
        boolean betPlaced = true;
        try {
            System.out.println("Performing bet of: " + combination.stream().map(BetPlacedData::toString).collect(Collectors.joining(" --- ")));
            ObjectNode jsonPayload = generateJsonBetPayload(combination, amount);
            System.out.println(jsonPayload);

            Map<String, String> headers = Map.of(
                    "Authorization", "Bearer " + bearerToken,
                    "Content-Type", "application/json",
                    "Accept", "application/json, text/javascript, */*; q=0.01"
            );
            String betplayLogin = HttpUtil.sendPostRequestMatch(KAMBI_PLACE_BET_URL, jsonPayload.toString(), headers);
            JsonNode jsonNode = objectMapper.readTree(betplayLogin);
            String status = jsonNode.findValue("status").textValue();
            System.out.println("Status of bet with " + combination.size() + " matches " + status);
            SoundUtil.makeSound(75100, 400);
        } catch (Exception e) {
            System.err.println("Error placing bets: " + e.getMessage());
            e.printStackTrace();
            betPlaced = false;
        }

        return betPlaced;
    }

    private static ObjectNode generateJsonBetPayload(List<BetPlacedData> combinationList, Integer amount) throws Exception {
        ObjectNode rootNode = objectMapper.createObjectNode();

        ArrayNode couponRows = objectMapper.createArrayNode();
        ArrayNode couponRowIndexes = objectMapper.createArrayNode();

        Map<String, List<BetPlacedData>> combinationsGroupedByMatchName = combinationList.stream().collect(Collectors.groupingBy(BetPlacedData::matchName));
        AtomicInteger index = new AtomicInteger();

        for (Map.Entry<String, List<BetPlacedData>> entry : combinationsGroupedByMatchName.entrySet()) {
            String matchName = entry.getKey();
            List<BetPlacedData> combinations = entry.getValue();

            int i = index.get();
            if (combinations.size() == 1) {
                ObjectNode couponRow = objectMapper.createObjectNode();
                couponRow.put("index", i);
                couponRow.put("odds", combinations.getFirst().odds() * 1_000);
                couponRow.put("outcomeId", combinations.getFirst().outcomeId());
                couponRow.put("type", "SIMPLE");

                couponRows.add(couponRow);
                couponRowIndexes.add(i);
            } else if (combinations.size() == 2) {
                ArrayNode groups = objectMapper.createArrayNode();
                for (BetPlacedData combination : combinations) {
                    ObjectNode groupNode = objectMapper.createObjectNode();
                    groupNode.put("operation", "AND");

                    ArrayNode outcomeIds = objectMapper.createArrayNode();
                    outcomeIds.add(combination.outcomeId());
                    groupNode.putIfAbsent("outcomeIds", outcomeIds);

                    groups.add(groupNode);
                }
                ObjectNode group = objectMapper.createObjectNode();
                group.put("operation", "AND");
                group.putIfAbsent("groups", groups);

                String odds = getOddsFromCombinedEventsSameMatch(combinations.getFirst().eventId(), combinations.stream().map(BetPlacedData::outcomeId).toList());

                ObjectNode couponRow = objectMapper.createObjectNode();

                couponRow.put("index", i);
                couponRow.put("odds", odds);
                couponRow.putIfAbsent("group", group);
                couponRow.put("type", "BET_BUILDER");

                couponRows.add(couponRow);
                couponRowIndexes.add(i);
            } else {
                System.err.println("More than 2 combinations per match ¿? " + matchName + " " + combinations.stream().map(BetPlacedData::toString).collect(Collectors.joining(" -- ")));
            }

            index.getAndIncrement();
        }

        ArrayNode bets = objectMapper.createArrayNode();
        ObjectNode bet = objectMapper.createObjectNode();
        bet.putIfAbsent("couponRowIndexes", couponRowIndexes);
        bet.put("eachWay", false);
        bet.put("stake", amount * 1_000);
        bets.add(bet);

        rootNode.putIfAbsent("couponRows", couponRows);
        rootNode.put("allowOddsChange", "NO");
        rootNode.putIfAbsent("bets", bets);
        rootNode.put("channel", "WEB");
        rootNode.put("requestId", "7f5ebbfb-1cbf-4c13-9bac-fe5822c66cad");

        return rootNode;
    }

    private static String getOddsFromCombinedEventsSameMatch(String eventId, List<Long> outcomeIds) throws Exception {
        String outcomeIdsComma = outcomeIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String urlCombinedMatches = String.format(KAMBI_COMBINED_BETS_SAME_MATCH_URL, eventId, outcomeIdsComma);
        String response = HttpUtil.sendGetRequestMatch(urlCombinedMatches);

        JsonNode jsonNode = objectMapper.readTree(response);
        JsonNode selectedOdds = jsonNode.findValue("selectedOdds");

        return selectedOdds.findValue("decimal").asText();
    }

    private String loginKambi(String tokenLoginKambi) throws Exception {
        String kambiPayload = """
                {
                    "punterId":"326868",
                    "ticket":":kambi-token"
                    ,"customerSiteIdentifier":"",
                    "requestStreaming":true,
                    "channel":"WEB",
                    "market":"CO",
                    "sessionAttributes":{"fingerprintHash":"14d7f812de33de1371715c53d99cab23"}
                }
                """;
        kambiPayload = kambiPayload.replaceAll(":kambi-token", tokenLoginKambi);

        Map<String, String> headers = Map.of(
                "Content-Type", "application/json"
        );
        String betplayLogin = HttpUtil.sendPostRequestMatch(KAMBI_AUTH_URL, kambiPayload, headers);
        JsonNode jsonNode = objectMapper.readTree(betplayLogin);
        return jsonNode.findValue("token").textValue();
    }

    private String loginBetPlay() throws Exception {
        long timeLong = LocalDateTime.now()
                .plusMinutes(37)
                .plusSeconds(33)
                .toEpochSecond(OffsetDateTime.now().getOffset());
        final String inputToken = """
                {"BPCValid":true,"iat":%d}""".formatted(timeLong);
        String inputTokenBase64 = EncoderUtility.encodeBase64(inputToken);

        /** How browser generates X-Bpcstatus-X:
             generateHash() {
                 const m = localStorage.getItem("currentTime");
                 const y = +parseInt(window.crypto.getRandomValues(new Uint8Array(16)).join(""));

                 return `${globalThis.btoa(JSON.stringify({BPCValid: window.BPCValid,iat: +m}))}
                    .${(0,u.sha3_512)(o.N.BPCKey + (BigInt(BigInt(y).toString(32).replace(/\D/g, "")) + BigInt(BigInt(m))))}
                    .${BigInt(y)}`
             }
         */

        Map<String, String> headers = Map.of(
                "Content-Type", "application/json; charset=UTF-8",
                "X-Bpcstatus-X", inputTokenBase64 + ".d90e9eadb9577789a8df4fef8ff658c1385f6381236e3c46a08a705a02ac07db5112428aa21e6a4db85e4fb7bb1a7986448f0f28b6df1601afc84e05e2717f5d.17242132891311975451975292351429472157696",
                "Accept", "application/json, text/plain, */*",
                "X-Custom-Version", "4.0.29",
                "Origin", "https://betplay.com.co",
                "Priority", "u=1, i",
                "X-Custom-Header", "1017199217",
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.88 Safari/537.36",
                "Host", "betplay.com.co"
        );

        String betplayLogin = HttpUtil.sendPostRequestMatch(BET_PLAY_LOGIN_URL, jsonCredentialsBetPlay, headers);
        JsonNode jsonNode = objectMapper.readTree(betplayLogin);
        return jsonNode.findValue("kambiToken").textValue();
    }

    private void persistCombinations(List<BetPlacedData> combination, Integer amount) {
        double totalOdds = calculateTotalOddFromCombinations(combination);
        String hashBetCombination = hashBetCombination(combination);

        try {
            nbaBetPlacerDao.persistCombinations(combination, totalOdds, amount, hashBetCombination);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static Double calculateTotalLinePointsFromCombinations(List<BetPlacedData> combination) {
        return combination.stream()
                .map(BetPlacedData::line)
                .mapToDouble(Double::doubleValue)
                .reduce(Double::sum)
                .getAsDouble();
    }

    private static Double calculateTotalOddFromCombinations(List<BetPlacedData> combination) {
        return combination.stream()
                .map(BetPlacedData::odds)
                .mapToDouble(Double::doubleValue)
                .reduce((d1, d2) -> d1 * d2)
                .getAsDouble();
    }

    private static String hashBetCombination(List<BetPlacedData> combination) {
        String matchNameJoin = combination.stream()
                .map(comb -> comb.matchName() + comb.matchDate().toLocalDate())
                .collect(Collectors.joining("--"));

        return DigestUtils.sha256Hex(matchNameJoin);

    }

    private List<List<BetPlacedData>> generateAllBetCombinations(List<BetPlacedData> betsSatisfyAIData) {
        int combinationSize = betsSatisfyAIData.size();
        List<List<BetPlacedData>> combinations = new ArrayList<>();

        for (int size = 1; size <= combinationSize; size++) {
            generateCombinationsOfSize(betsSatisfyAIData.toArray(new BetPlacedData[0]), new BetPlacedData[size], 0, betsSatisfyAIData.size() - 1, 0, size, combinations);
        }

        return combinations;
    }

    static void generateCombinationsOfSize(BetPlacedData[] arr, BetPlacedData[] data, int start, int end, int index, int size, List<List<BetPlacedData>> responseList) {
        if (index == size) {
            responseList.add(Arrays.stream(data).toList());
            return;
        }

        for (int i = start; i <= end && end - i + 1 >= size - index; i++) {
            data[index] = arr[i];
            generateCombinationsOfSize(arr, data, i + 1, end, index + 1, size, responseList);
        }
    }

    private Double calculateAIResponseTotalPointsAverage(List<AIResponse> aiResponses) {
        Double average = aiResponses.stream()
                .map(AIResponse::value)
                .collect(Collectors.averagingDouble(Double::valueOf));
        return Math.floor(average);
    }

    private Double calculateAIResponsePointsByTeamAverage(List<AIResponse> aiResponses, boolean isTeam1) {
        Double average = aiResponses.stream()
                    .map(aiRes -> isTeam1 ? aiRes.team1Points() : aiRes.team2Points())
                    .collect(Collectors.averagingDouble(Double::valueOf));
        return Math.floor(average);
    }

    public void finishPreviousBetsCompleted() {
        try {
            List<BetPlacedParent> betsByStatusWithChildren = nbaBetPlacerDao.findBetsByStatusWithChildren(BetStatus.PENDING);
            List<String> childrenMatchesNameGameDate = betsByStatusWithChildren.stream()
                    .map(BetPlacedParent::matchesChildren)
                    .flatMap(Collection::stream)
                    .map(children -> children.matchName() + " - " + children.matchDate().toLocalDate())
                    .distinct()
                    .toList();
            List<NbaMatch> matchesByName = nbaOldMatchesService.findMatchesByNameAndGameDate(childrenMatchesNameGameDate);
            Map<String, Double> mapMatchPoints = matchesByName.stream()
                    .collect(Collectors.toMap(
                            match -> NbaTeamsService.teamMap.get(match.team1Id()).getAlias() + " - " + NbaTeamsService.teamMap.get(match.team2Id()).getAlias() + " - " + LocalDate.ofInstant(match.gameDate(), ZoneId.systemDefault()),
                            match -> match.team1TotalPoints() + match.team2TotalPoints()
                    ));
            Map<String, Double> mapMatchDifferencePoints = matchesByName.stream()
                    .collect(Collectors.toMap(
                            match -> NbaTeamsService.teamMap.get(match.team1Id()).getAlias() + " - " + NbaTeamsService.teamMap.get(match.team2Id()).getAlias() + " - " + LocalDate.ofInstant(match.gameDate(), ZoneId.systemDefault()),
                            match -> Math.abs(match.team1TotalPoints() - match.team2TotalPoints())
                    ));


            for (BetPlacedParent betsByStatusWithChild : betsByStatusWithChildren) {
                Optional<BetPlacedChildren> nonExistingMatches = betsByStatusWithChild.matchesChildren().stream()
                        .filter(Predicate.not(children -> mapMatchPoints.containsKey(children.matchName() + " - " + children.matchDate().toLocalDate())))
                        .findAny();

                if (nonExistingMatches.isPresent()) {
                    //System.out.println("Non existing matches played for bet with id: " + betsByStatusWithChild.id() + " match not played yet " + nonExistingMatches.get().matchName());
                    continue;
                }


                boolean allWon = true;
                for (BetPlacedChildren matchBet : betsByStatusWithChild.matchesChildren()) {
                    String matchName = matchBet.matchName() + " - " + matchBet.matchDate().toLocalDate();
                    Double totalPoints = mapMatchPoints.get(matchName);
                    Double differencePoints = mapMatchDifferencePoints.get(matchName);
                    if (matchBet.type().equals("OT_OVER")) {
                        if (totalPoints > matchBet.line()) {
                            nbaBetPlacerDao.updateBetPlacedMatches(matchBet.withStatusAndRealPoints(BetStatus.WON, totalPoints.intValue()));
                        } else {
                            allWon = false;
                            nbaBetPlacerDao.updateBetPlacedMatches(matchBet.withStatusAndRealPoints(BetStatus.LOST, totalPoints.intValue()));
                        }
                    } else if (matchBet.type().equals("OT_UNDER")) {
                        if (totalPoints < matchBet.line()) {
                            nbaBetPlacerDao.updateBetPlacedMatches(matchBet.withStatusAndRealPoints(BetStatus.WON, totalPoints.intValue()));
                        } else {
                            allWon = false;
                            nbaBetPlacerDao.updateBetPlacedMatches(matchBet.withStatusAndRealPoints(BetStatus.LOST, totalPoints.intValue()));
                        }
                    } else if (matchBet.type().equals("OT_ONE") || matchBet.type().equals("OT_TWO")) {
                        if (differencePoints < matchBet.line()) {
                            nbaBetPlacerDao.updateBetPlacedMatches(matchBet.withStatusAndRealPoints(BetStatus.WON, differencePoints.intValue()));
                        } else {
                            allWon = false;
                            nbaBetPlacerDao.updateBetPlacedMatches(matchBet.withStatusAndRealPoints(BetStatus.LOST, differencePoints.intValue()));
                        }
                    }
                }

                if (allWon)
                    nbaBetPlacerDao.updateBetPlaceParent(betsByStatusWithChild.withStatusAndAmountEarned(BetStatus.WON, (long) (betsByStatusWithChild.amountBetPesos() * betsByStatusWithChild.totalOdd())));
                else
                    nbaBetPlacerDao.updateBetPlaceParent(betsByStatusWithChild.withStatusAndAmountEarned(BetStatus.LOST, betsByStatusWithChild.amountBetPesos() * -1));
            }

            System.out.println("Finished Updating completed matches bet results");

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}