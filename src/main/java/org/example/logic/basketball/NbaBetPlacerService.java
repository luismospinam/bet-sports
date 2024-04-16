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

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class NbaBetPlacerService {

    private final NbaOldMatchesService nbaOldMatchesService;
    private final NbaBetPlacerDao nbaBetPlacerDao;

    private static final Map<String, Double> minimumBetLineHistory = new HashMap<>();
    private static final Map<String, Double> maximumBetLineHistory = new HashMap<>();
    private static final Integer MIN_POINTS_UNDER = 250;
    private static final Integer DESIRED_AVERAGE_DIFFERENCE = 20;
    private static final Integer AMOUNT_PESOS_BET = 500;
    private static final String BET_PLAY_LOGIN_URL = "https://betplay.com.co/reverse-proxy/accounts/sessions/complete";
    private static final String KAMBI_AUTH_URL = "https://cf-mt-auth-api.kambicdn.com/player/api/v2019/betplay/punter/login.json?market=CO&lang=es_CO&channel_id=1&client_id=2&settings=true";
    private static final String KAMBI_PLACE_BET_URL = "https://cf-mt-auth-api.kambicdn.com/player/api/v2019/betplay/coupon.json?lang=es_CO&market=CO&client_id=2&channel_id=1&";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static String tokenLoginKambi;
    private static String jsonCredentialsBetPlay;


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

    public void placeBet(List<EventNbaPoints> matchesPointsOdd, Map<String, List<AIResponse>> aiNbaMatchPoints) {
        Map<String, BetPlacedData> minimumMatchLine = new HashMap<>();
        Map<String, BetPlacedData> maximumMatchLine = new HashMap<>();

        for (EventNbaPoints eventNbaPoints : matchesPointsOdd) {
            List<JsonNode> linePoints = eventNbaPoints.pointEvents();
            findMinimumAndMaximumLinesInMatches(eventNbaPoints, linePoints, minimumMatchLine, maximumMatchLine);
        }

        List<BetPlacedData> betsSatisfyAIData = findBetsSatisfyAIData(minimumMatchLine, aiNbaMatchPoints);
        List<BetPlacedData> betsSatisfyAIData2 = findBetsSatisfyAIData(maximumMatchLine, aiNbaMatchPoints);
        betsSatisfyAIData.addAll(betsSatisfyAIData2);

        List<List<BetPlacedData>> combinations = generateAllBetCombinations(betsSatisfyAIData);
        combinations = getCombinationsNotBetAlready(combinations);
        if (!combinations.isEmpty()) {
            placeBetInBetPlay(combinations);
            persistCombinations(combinations);
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
                    BetPlacedData BetPlacedData = new BetPlacedData(matchName, eventNbaPoints.gameDate(), odds, line, id, null, "OT_OVER");
                    minimumMatchLine.compute(matchName,
                            (k, v) -> v == null ? BetPlacedData :
                                    BetPlacedData.line() < v.line() ? BetPlacedData : v);
                } else {
                    BetPlacedData BetPlacedData = new BetPlacedData(matchName, eventNbaPoints.gameDate(), odds, line, id, null, "OT_UNDER");
                    maximumMatchLine.compute(matchName,
                            (k, v) -> v == null ? BetPlacedData :
                                    BetPlacedData.line() > v.line() ? BetPlacedData : v);
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
        }
    }

    private List<List<BetPlacedData>> getCombinationsNotBetAlready(List<List<BetPlacedData>> combinations) {
        List<List<BetPlacedData>> newReturnList = new ArrayList<>();

        try {
            for (List<BetPlacedData> combination : combinations) {
                String hashBetCombination = hashBetCombination(combination);
                Optional<BetPlacedParent> optionalBetPlacedParent;

                optionalBetPlacedParent = nbaBetPlacerDao.findByHashIdentifier(hashBetCombination);
                if (optionalBetPlacedParent.isEmpty() || calculateTotalOddFromCombinations(combination) > optionalBetPlacedParent.get().totalOdd()) {
                    newReturnList.add(combination);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return newReturnList;

    }


    private void placeBetInBetPlay(List<List<BetPlacedData>> combinations) {
        try {
            if (tokenLoginKambi == null || tokenLoginKambi.isEmpty()) {
                tokenLoginKambi = loginBetPlay();
            }
            String bearerToken = loginKambi(tokenLoginKambi);

            for (List<BetPlacedData> combination : combinations) {
                TimeUnit.SECONDS.sleep(2);
                ObjectNode jsonPayload = generateJsonBetPayload(combination);

                Map<String, String> headers = Map.of(
                        "Authorization", "Bearer " + bearerToken,
                        "Content-Type", "application/json",
                        "Accept", "application/json, text/javascript, */*; q=0.01"
                );
                String betplayLogin = HttpUtil.sendPostRequestMatch(KAMBI_PLACE_BET_URL, jsonPayload.toString(), headers);
                JsonNode jsonNode = objectMapper.readTree(betplayLogin);
                String status = jsonNode.findValue("status").textValue();
                System.out.println("Status of bet with " + combination.size() + " matches " + status);
            }
        } catch (Exception e) {
            System.err.println("Error placing bets: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static ObjectNode generateJsonBetPayload(List<BetPlacedData> combination) {
        ObjectNode rootNode = objectMapper.createObjectNode();

        ArrayNode couponRows = objectMapper.createArrayNode();
        ArrayNode couponRowIndexes = objectMapper.createArrayNode();
        for (int i = 0; i < combination.size(); i++) {
            ObjectNode couponRow = objectMapper.createObjectNode();
            couponRow.put("index", i);
            couponRow.put("odds", combination.get(i).odds() * 1_000);
            couponRow.put("outcomeId", combination.get(i).outcomeId());
            couponRow.put("type", "SIMPLE");
            couponRows.add(couponRow);

            couponRowIndexes.add(i);
        }

        ArrayNode bets = objectMapper.createArrayNode();
        ObjectNode bet = objectMapper.createObjectNode();
        bet.putIfAbsent("couponRowIndexes", couponRowIndexes);
        bet.put("eachWay", false);
        bet.put("stake", AMOUNT_PESOS_BET * 1_000);
        bets.add(bet);

        rootNode.putIfAbsent("couponRows", couponRows);
        rootNode.put("allowOddsChange", "NO");
        rootNode.putIfAbsent("bets", bets);
        rootNode.put("channel", "WEB");
        rootNode.put("requestId", "7f5ebbfb-1cbf-4c13-9bac-fe5822c66cad");

        return rootNode;
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
        long timeLong = new Date().getTime() / 1000;
        final String inputToken = """
                {"BPCValid":true,"iat":%d}""".formatted(timeLong);
        String inputTokenBase64 = EncoderUtility.encodeBase64(inputToken);

        Map<String, String> headers = Map.of(
                "Content-Type", "application/json; charset=UTF-8",
                "X-Bpcstatus-X", inputTokenBase64 + ".76d15211ba9d9b88dfc3d36bf225956a6701dad0f2816d9c18b308aa171eaec3",
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

    private void persistCombinations(List<List<BetPlacedData>> combinations) {
        for (List<BetPlacedData> combination : combinations) {
            double totalOdds = calculateTotalOddFromCombinations(combination);
            String hashBetCombination = hashBetCombination(combination);

            try {
                nbaBetPlacerDao.persistCombinations(combination, totalOdds, AMOUNT_PESOS_BET, hashBetCombination);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

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
                .map(BetPlacedData::matchName)
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

    private List<BetPlacedData> findBetsSatisfyAIData(Map<String, BetPlacedData> minimumMatchLine, Map<String, List<AIResponse>> aiNbaMatchPoints) {
        List<BetPlacedData> returnList = new ArrayList<>();
        for (Map.Entry<String, BetPlacedData> BetPlacedDataEntry : minimumMatchLine.entrySet()) {
            BetPlacedData BetPlacedData = BetPlacedDataEntry.getValue();
            List<AIResponse> aiResponses = aiNbaMatchPoints.get(BetPlacedDataEntry.getKey());
            if (aiResponses != null && !aiResponses.isEmpty()) {
                Double averagePointsAI = aiResponses.stream()
                        .map(AIResponse::value)
                        .collect(Collectors.averagingDouble(Double::valueOf));

                if ("OT_OVER".equals(BetPlacedData.betType())) {
                    if (averagePointsAI - DESIRED_AVERAGE_DIFFERENCE >= BetPlacedData.line()) {
                        System.out.println("You should bet " + BetPlacedDataEntry.getKey() + " with a line of " + BetPlacedData.line() + " and a odd of " + BetPlacedData.odds() + " and a AI prediction of " + averagePointsAI + " difference points of " + (averagePointsAI - BetPlacedData.line()));
                        returnList.add(BetPlacedData.withAIPrediction(averagePointsAI));
                    }
                } else {
                    if (averagePointsAI + DESIRED_AVERAGE_DIFFERENCE <= BetPlacedData.line() && BetPlacedData.line() >= MIN_POINTS_UNDER) {
                        System.out.println("You should bet " + BetPlacedDataEntry.getKey() + " with a line of " + BetPlacedData.line() + " and a odd of " + BetPlacedData.odds() + " and a AI prediction of " + averagePointsAI + " difference points of " + (averagePointsAI - BetPlacedData.line()));
                        returnList.add(BetPlacedData.withAIPrediction(averagePointsAI));
                    }
                }
            } else {
                System.out.println("Could not find AI response for match " + BetPlacedDataEntry.getKey());
            }
        }

        return returnList;
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
                            match -> NbaTeamsService.teamMap.get(match.team1Id()).getAlias() + " - " + NbaTeamsService.teamMap.get(match.team2Id()).getAlias(),
                            match -> match.team1TotalPoints() + match.team2TotalPoints()
                    ));

            for (BetPlacedParent betsByStatusWithChild : betsByStatusWithChildren) {
                boolean nonExistingMatches = betsByStatusWithChildren.stream()
                        .map(BetPlacedParent::matchesChildren)
                        .flatMap(Collection::stream)
                        .anyMatch(Predicate.not(children -> mapMatchPoints.containsKey(children.matchName())));

                if (nonExistingMatches) {
                    System.out.println("Non existing matches played for bet with id: " + betsByStatusWithChild.id());
                    continue;
                }


                boolean allWon = true;
                for (BetPlacedChildren matchBet : betsByStatusWithChild.matchesChildren()) {
                    Double totalPoints = mapMatchPoints.get(matchBet.matchName());
                    if (matchBet.type().equals("OT_OVER")) {
                        if (totalPoints > matchBet.line()) {
                            nbaBetPlacerDao.updateBetPlacedMatches(matchBet.withStatusAndRealPoints(BetStatus.WON, totalPoints.intValue()));
                        } else {
                            allWon = false;
                            nbaBetPlacerDao.updateBetPlacedMatches(matchBet.withStatusAndRealPoints(BetStatus.LOST, totalPoints.intValue()));
                        }
                    } else {
                        if (totalPoints < matchBet.line()) {
                            nbaBetPlacerDao.updateBetPlacedMatches(matchBet.withStatusAndRealPoints(BetStatus.WON, totalPoints.intValue()));
                        } else {
                            allWon = false;
                            nbaBetPlacerDao.updateBetPlacedMatches(matchBet.withStatusAndRealPoints(BetStatus.LOST, totalPoints.intValue()));
                        }
                    }
                }

                if (allWon)
                    nbaBetPlacerDao.updateBetPlaceParent(betsByStatusWithChild.withStatusAndAmountEarned(BetStatus.WON, (long) (betsByStatusWithChild.amountBetPesos() * betsByStatusWithChild.totalOdd())));
                else
                    nbaBetPlacerDao.updateBetPlaceParent(betsByStatusWithChild.withStatusAndAmountEarned(BetStatus.LOST, betsByStatusWithChild.amountBetPesos() * -1));
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}