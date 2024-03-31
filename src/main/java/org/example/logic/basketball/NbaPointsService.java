package org.example.logic.basketball;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.db.basketball.NbaPointsDao;
import org.example.model.NbaTeam;
import org.example.model.EventNbaPoints;
import org.example.model.EventNbaPointsLineTypeOdd;
import org.example.util.HttpUtil;
import org.example.util.SoundUtil;

import javax.sound.sampled.LineUnavailableException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NbaPointsService {

    private final NbaPointsDao nbaPointsDao;
    private static final String JSON_LIST_PATH = "[]";
    private final List<String> betPaths = List.of("betOffers", JSON_LIST_PATH, "criterion", "label");
    private static final String DESIRED_BET_NAME = "Total de puntos - Prórroga incluida";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final double MINIMUM_POINTS_NOTIFICATION = 194d;
    private static final double MAXIMUM_POINTS_NOTIFICATION = 250d;


    public NbaPointsService(NbaPointsDao nbaPointsDao) {
        this.nbaPointsDao = nbaPointsDao;
    }


    public void persistEventValues(EventNbaPoints event) throws SQLException, LineUnavailableException {
        for (JsonNode betEvent : event.pointEvents()) {
            JsonNode outcomes = betEvent.findValue("outcomes");
            for (JsonNode node : outcomes) {
                String type = node.findValue("type").textValue();
                double line = node.findValue("line").asDouble() / 1_000;
                double odds = node.findValue("odds").asDouble() / 1_000;

                Optional<EventNbaPointsLineTypeOdd> existingEvent = nbaPointsDao.checkEventAlreadyExist(event, line, type);
                if (existingEvent.isPresent()) {
                    EventNbaPointsLineTypeOdd eventNbaPointsLineTypeOdd = existingEvent.get();
                    if (eventNbaPointsLineTypeOdd.odd() != odds) {
                        nbaPointsDao.updateExistingEvent(event, eventNbaPointsLineTypeOdd, type, line, odds);
                    }
                } else {
                    nbaPointsDao.insertNewEvent(event, type, line, odds);
                }

                if ((line <= MINIMUM_POINTS_NOTIFICATION && "OT_OVER".equals(type)) || (line >= MAXIMUM_POINTS_NOTIFICATION && "OT_UNDER".equals(type))) {
                    System.out.printf("Event %s with a line of %f %s has an odd of %f %s", event.matchMame(), line, type, odds, System.lineSeparator());
                    SoundUtil.makeSound();
                }
            }

        }
    }

    public List<EventNbaPoints> findMatchesPointsOdd(String url, List<String> matchesId) throws Exception {
        List<EventNbaPoints> returnList = new ArrayList<>();

        for (String matchId : matchesId) {
            String finalUrl = String.format(url, matchId);
            String jsonResponse = HttpUtil.sendRequestMatch(finalUrl);
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            List<JsonNode> betEvents = findBetEvent(jsonNode, betPaths);
            EventNbaPoints event = fillEventData(jsonNode, betEvents);
            returnList.add(event);

            this.persistEventValues(event);
        }

        return returnList;
    }

    private EventNbaPoints fillEventData(JsonNode jsonNode, List<JsonNode> betEvents) {
        JsonNode events = jsonNode.findValue("events");
        String eventName = events.findValue("name").textValue();
        String[] teams = eventName.split(" - ");
        NbaTeam team1 = NbaTeamsService.teamMap.values().stream()
                .filter(team -> teams[0].contains(team.getAlias()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No team found for " + teams[0]));
        NbaTeam team2 = NbaTeamsService.teamMap.values().stream()
                .filter(team -> teams[1].contains(team.getAlias()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No team found for " + teams[1]));

        return new EventNbaPoints(
                events.findValue("id").asText(),
                eventName,
                DESIRED_BET_NAME,
                Instant.parse(events.findValue("start").asText()).atZone(ZoneId.systemDefault()),
                team1,
                team2,
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
