package org.example.logic.basketball;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.db.basketball.NbaBetPlacerDao;
import org.example.model.AIResponse;
import org.example.model.BetPlacedData;
import org.example.model.EventNbaPoints;
import org.example.util.EncoderUtility;
import org.example.util.HttpUtil;
import org.example.util.PropertiesLoaderUtil;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class NbaBetPlacerService {

    private final NbaBetPlacerDao nbaBetPlacerDao;

    private static final Map<String, Double> minimumBetLineHistory = new HashMap<>();
    private static final Integer DESIRED_AVERAGE_DIFFERENCE = 17;
    private static final Integer AMOUNT_PESOS_BET = 500;
    private static final String BET_PLAY_LOGIN_URL = "https://betplay.com.co/reverse-proxy/accounts/sessions/complete";
    private static final String KAMBI_AUTH_URL = "https://cf-mt-auth-api.kambicdn.com/player/api/v2019/betplay/punter/login.json?market=CO&lang=es_CO&channel_id=1&client_id=2&settings=true";
    private static final String KAMBI_PLACE_BET_URL = "https://cf-mt-auth-api.kambicdn.com/player/api/v2019/betplay/coupon.json?lang=es_CO&market=CO&client_id=2&channel_id=1&";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static String tokenLoginKambi = "E771650C-7DF6-9CCF-D1B2-240414051909"; //TODO
    private static String jsonCredentialsBetPlay;


    static {
        Properties properties = PropertiesLoaderUtil.loadProperties("credentials.properties");

        String betPlayCredentials = properties.getProperty("BET_PLAY_CREDENTIALS");
        if (betPlayCredentials == null || betPlayCredentials.isEmpty()) {
            throw new RuntimeException("BET_PLAY_CREDENTIALS can not be empty in credentials.properties");
        }

        NbaBetPlacerService.jsonCredentialsBetPlay = betPlayCredentials;
    }


    public NbaBetPlacerService(NbaBetPlacerDao nbaBetPlacerDao) {
        this.nbaBetPlacerDao = nbaBetPlacerDao;
    }

    public void placeBet(List<EventNbaPoints> matchesPointsOdd, Map<String, List<AIResponse>> aiNbaMatchPoints) {
        Map<String, BetPlacedData> minimumMatchLine = new HashMap<>();

        for (EventNbaPoints eventNbaPoints : matchesPointsOdd) {
            String matchName = eventNbaPoints.team1().getAlias() + " - " + eventNbaPoints.team2().getAlias();

            List<JsonNode> linePoints = eventNbaPoints.pointEvents();
            for (JsonNode linePoint : linePoints) {
                JsonNode outcome = linePoint.findValue("outcomes");
                String type = outcome.findValue("type").asText();
                long id = outcome.findValue("id").longValue();
                double odds = outcome.findValue("odds").doubleValue() / 1000;
                double line = outcome.findValue("line").doubleValue() / 1000;

                if ("OT_OVER".equals(type)) {
                    BetPlacedData BetPlacedData = new BetPlacedData(matchName, odds, line, id, null, "OT_OVER"); //TODO should we include UNDER?
                    minimumMatchLine.compute(matchName,
                            (k, v) -> v == null ? BetPlacedData :
                                    BetPlacedData.line() < v.line() ? BetPlacedData : v);
                }
            }

            try {
                Double newLine = minimumMatchLine.get(matchName).line();
                Double newOdds = minimumMatchLine.get(matchName).odds();
                if (!minimumBetLineHistory.containsKey(matchName) || newLine < minimumBetLineHistory.get(matchName)) {
                    System.out.println("*** New minimum Line found for the match " + matchName + " line: " + newLine + " odds: " + newOdds);
                    minimumBetLineHistory.put(matchName, newLine);
                }
            } catch (Exception e) {
                System.err.println("ERROR IN MATCH NAME " + matchName);
            }
        }

        List<BetPlacedData> betsSatisfyAIData = findBetsSatisfyAIData(minimumMatchLine, aiNbaMatchPoints);
        List<List<BetPlacedData>> combinations = generateAllBetCombinations(betsSatisfyAIData);
        persistCombinations(combinations);
        placeBetInBetPlay(combinations);
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
        String kambiPayload = "{\"punterId\":\"326868\",\"ticket\":\":kambi-token\",\"customerSiteIdentifier\":\"\",\"requestStreaming\":true,\"channel\":\"WEB\",\"market\":\"CO\",\"sessionAttributes\":{\"fingerprintHash\":\"14d7f812de33de1371715c53d99cab23\"}}\n";
        kambiPayload = kambiPayload.replaceAll(":kambi-token", tokenLoginKambi);

        int length = kambiPayload.length();
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
                "X-Bpcstatus-X", inputTokenBase64 + "==.76d15211ba9d9b88dfc3d36bf225956a6701dad0f2816d9c18b308aa171eaec3",
                "Accept", "application/json, text/plain, */*",
                "X-Custom-Version", "4.0.28",
                "Origin", "https://betplay.com.co",
                "Priority", "u=1, i"
        );
        String betplayLogin = HttpUtil.sendPostRequestMatch(BET_PLAY_LOGIN_URL, jsonCredentialsBetPlay, headers);
        JsonNode jsonNode = objectMapper.readTree(betplayLogin);
        return jsonNode.findValue("kambiToken").textValue();
    }

    private void persistCombinations(List<List<BetPlacedData>> combinations) {
        for (List<BetPlacedData> combination : combinations) {
            double totalOdds = combination.stream()
                    .map(BetPlacedData::odds)
                    .mapToDouble(Double::doubleValue)
                    .reduce((d1, d2) -> d1 * d2)
                    .getAsDouble();

            try {
                nbaBetPlacerDao.persistCombinations(combination, totalOdds, AMOUNT_PESOS_BET);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

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

                if (averagePointsAI - DESIRED_AVERAGE_DIFFERENCE >= BetPlacedData.line()) {
                    System.out.println("You should bet " + BetPlacedDataEntry.getKey() + " with a line of " + BetPlacedData.line() + " and a odd of " + BetPlacedData.odds() + " and a AI prediction of " + averagePointsAI + " difference points of " + (averagePointsAI - BetPlacedData.line()));
                    returnList.add(BetPlacedData.withAIPrediction(averagePointsAI));
                }
            } else {
                System.out.println("Could not find AI response for match " + BetPlacedDataEntry.getKey());
            }
        }

        return returnList;
    }
}