package org.example.logic.basketball;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.util.HttpUtil;

import java.util.ArrayList;
import java.util.List;

public class NbaMatchFinderService {
    private static final String URL = "https://na-offering-api.kambicdn.net/offering/v2018/betplay/listView/basketball/nba/all/all/matches.json?lang=es_CO&market=CO&client_id=2&channel_id=1&ncid=1711679986982&useCombined=true";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public List<String> findMatchIds() throws Exception {
        List<String> returnList = new ArrayList<>();
        String jsonResponse = HttpUtil.sendGetRequestMatch(URL);
        JsonNode jsonNode = objectMapper.readTree(jsonResponse);
        JsonNode events = jsonNode.findValue("events");

        for (JsonNode event: events) {
            JsonNode matchEvent = event.findPath("event");
            String id = matchEvent.findPath("id").asText();
            returnList.add(id);
        }

        return returnList;
    }
}
