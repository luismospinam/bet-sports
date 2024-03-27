package org.example.logic.basketball;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.db.basketball.BasketballPointsDao;
import org.example.model.EventBasketballPoints;
import org.example.model.EventBasketballPointsLineTypeOdd;
import org.example.util.HttpUtil;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BasketballPointsService {

    private final BasketballPointsDao basketballPointsDao;
    private static final String JSON_LIST_PATH = "[]";
    private final List<String> betPaths = List.of("betOffers", JSON_LIST_PATH, "criterion", "label");
    private static final String DESIRED_BET_NAME = "Total de puntos - Prórroga incluida";
    private static final ObjectMapper objectMapper = new ObjectMapper();


    public BasketballPointsService(BasketballPointsDao basketballPointsDao) {
        this.basketballPointsDao = basketballPointsDao;
    }


    public void persistEventValues(EventBasketballPoints event) throws SQLException {
        for (JsonNode betEvent : event.pointEvents()) {
            JsonNode outcomes = betEvent.findValue("outcomes");
            for (JsonNode node : outcomes) {
                String type = node.findValue("type").textValue();
                double line = node.findValue("line").asDouble() / 1_000;
                double odds = node.findValue("odds").asDouble() / 1_000;

                Optional<EventBasketballPointsLineTypeOdd> existingEvent = basketballPointsDao.checkEventAlreadyExist(event, line, type);
                if (existingEvent.isPresent()) {
                    EventBasketballPointsLineTypeOdd eventBasketballPointsLineTypeOdd = existingEvent.get();
                    if (eventBasketballPointsLineTypeOdd.odd() != odds) {
                        basketballPointsDao.updateExistingEvent(event, eventBasketballPointsLineTypeOdd, type, line, odds);
                    }
                } else {
                    basketballPointsDao.insertNewEvent(event, type, line, odds);
                }
            }

        }
    }

    public List<EventBasketballPoints> findMatchesPointsOdd(String url, List<String> matchesId) throws Exception {
        List<EventBasketballPoints> returnList = new ArrayList<>();

        for (String matchId : matchesId) {
            String finalUrl = String.format(url, matchId);
            String jsonResponse = HttpUtil.sendRequestMatch(finalUrl);
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            List<JsonNode> betEvents = findBetEvent(jsonNode, betPaths);
            EventBasketballPoints event = fillEventData(jsonNode, betEvents);
            returnList.add(event);

            this.persistEventValues(event);
        }

        return returnList;
    }

    private EventBasketballPoints fillEventData(JsonNode jsonNode, List<JsonNode> betEvents) {
        JsonNode events = jsonNode.findValue("events");
        String eventName = events.findValue("name").textValue();
        String[] teams = eventName.split(" - ");
        return new EventBasketballPoints(
                events.findValue("id").asText(),
                eventName,
                DESIRED_BET_NAME,
                Instant.parse(events.findValue("start").asText()),
                teams[0],
                teams[1],
                betEvents
        );
    }

    //TODO: refactor to not be hard-coded the find event
    private List<JsonNode> findBetEvent(JsonNode jsonNode, List<String> betPaths) {
        JsonNode offersNode = jsonNode.findPath(betPaths.getFirst());
        List<JsonNode> desiredNodes = new ArrayList<>();
        if (offersNode.isArray()) {
            for (JsonNode node : offersNode) {
                String eventName = node.findPath(betPaths.get(2)).findPath(betPaths.get(3)).asText();
                if (DESIRED_BET_NAME.equals(eventName)) {
                    desiredNodes.add(node);
                }
            }
        }

        return desiredNodes;
    }
}

