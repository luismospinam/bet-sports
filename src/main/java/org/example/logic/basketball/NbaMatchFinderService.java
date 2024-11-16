package org.example.logic.basketball;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.logic.ai.AIService;
import org.example.util.HttpUtil;
import org.example.util.PropertiesLoaderUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class NbaMatchFinderService {
    private static final String URL = "https://na-offering-api.kambicdn.net/offering/v2018/betplay/listView/basketball/nba/all/all/matches.json?lang=es_CO&market=CO&client_id=2&channel_id=1&ncid=1711679986982&useCombined=true";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> hardCodedMatches = new ArrayList<>();

    static {

    }

    public List<String> findMatchIds() throws Exception {
        if (!hardCodedMatches.isEmpty()) {
            return hardCodedMatches;
        }

        List<String> returnList = new ArrayList<>();
        String jsonResponse = HttpUtil.sendGetRequestMatch(URL);
        JsonNode jsonNode = objectMapper.readTree(jsonResponse);
        JsonNode events = jsonNode.findValue("events");

        for (JsonNode event: events) {
            JsonNode matchEvent = event.findPath("event");
            String id = matchEvent.findPath("id").asText();

            if (!"STARTED".equals(matchEvent.findValue("state").textValue())) {
                returnList.add(id);
            } else {
                System.out.println("- Match " + matchEvent.findValue("name").textValue() + " has already started, not including it" + System.lineSeparator());
            }
        }

        return returnList;
    }
}
