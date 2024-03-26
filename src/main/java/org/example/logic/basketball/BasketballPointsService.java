package org.example.logic.basketball;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.constant.EventOddChange;
import org.example.db.DB;
import org.example.model.EventBasketballPoints;
import org.example.model.EventBasketballPointsLineTypeOdd;
import org.example.util.HttpUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.example.constant.Message.EXISTING_EVENT_CHANGE_ODD;

public class BasketballPointsService {
    private static final String JSON_LIST_PATH = "[]";
    private final List<String> betPaths = List.of("betOffers", JSON_LIST_PATH, "criterion", "label");
    private static final String DESIRED_BET_NAME = "Total de puntos - Prórroga incluida";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Connection dbConnection = DB.getConnection();


    public void persistEventValues(EventBasketballPoints event) throws SQLException {
        for (JsonNode betEvent : event.pointEvents()) {
            JsonNode outcomes = betEvent.findValue("outcomes");
            for (JsonNode node : outcomes) {
                String type = node.findValue("type").textValue();
                double line = node.findValue("line").asDouble() / 1_000;
                double odds = node.findValue("odds").asDouble() / 1_000;

                Optional<EventBasketballPointsLineTypeOdd> existingEvent = checkEventAlreadyExist(event, line, type);
                if (existingEvent.isPresent()) {
                    EventBasketballPointsLineTypeOdd eventBasketballPointsLineTypeOdd = existingEvent.get();
                    if (eventBasketballPointsLineTypeOdd.odd() != odds) {
                        updateExistingEvent(event, eventBasketballPointsLineTypeOdd, type, line, odds);
                    }
                } else {
                    insertNewEvent(event, type, line, odds);
                }
            }

        }
    }

    private void updateExistingEvent(EventBasketballPoints event,
                                     EventBasketballPointsLineTypeOdd existingEvent, String type, Double line, Double newOdd) throws SQLException {
        String query = """
                UPDATE bets SET odd = %f, date = NOW()
                WHERE id = %d
                """;
        String finalQuery = String.format(query, newOdd, existingEvent.id());
        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        preparedStatement.execute();


        query = """
                INSERT INTO bet_change (bets_id, old_odd, new_odd, action, date, message)
                VALUES (%d, %f, %f, '%s', NOW(), '%s');
                """;
        Double oldOdd = existingEvent.odd();
        String message = String.format(EXISTING_EVENT_CHANGE_ODD.getMessage(), existingEvent.id(), event.matchMame(), oldOdd, newOdd, line, type);
        EventOddChange eventOddChange = newOdd > oldOdd ? EventOddChange.INCREASE : EventOddChange.DECREASE;
        finalQuery = String.format(query, existingEvent.id(), oldOdd, newOdd, eventOddChange, message);
        preparedStatement = dbConnection.prepareStatement(finalQuery);
        preparedStatement.execute();
    }

    private void insertNewEvent(EventBasketballPoints event, String type, double line, double odds) throws SQLException {
        String query = """
                INSERT INTO bets (match_name, bet_name, game_date, team1, team2, bet_type, date, line, odd, betplay_id)
                VALUES ('%s', '%s', '%s', '%s', '%s', '%s', NOW(), %f, %f, %s);
                """;
        String finalQuery = String.format(query, event.matchMame(), event.betName(), event.gameDate(), event.team1(), event.team2(), type, line, odds, event.id());

        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        preparedStatement.execute();
    }

    private Optional<EventBasketballPointsLineTypeOdd> checkEventAlreadyExist(EventBasketballPoints event, double line, String type) throws SQLException {
        String query = """
                SELECT * FROM bets WHERE match_name = '%s' and bet_name = '%s' and game_date = '%s' and bet_type = '%s' and line = %f;
                """;
        query = String.format(query, event.matchMame(), event.betName(), LocalDate.ofInstant(event.gameDate(), ZoneOffset.UTC), type, line);

        PreparedStatement preparedStatement = dbConnection.prepareStatement(query);
        ResultSet resultSet = preparedStatement.executeQuery();

        if (resultSet == null || !resultSet.next()) {
            return Optional.empty();
        }

        return Optional.of(new EventBasketballPointsLineTypeOdd(
                resultSet.getInt("id"),
                resultSet.getString("bet_type"),
                resultSet.getDouble("line"),
                resultSet.getDouble("odd")
        ));
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

