package org.example.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.ZonedDateTime;
import java.util.List;

public record EventNbaPoints(
        String id,
        String matchMame,
        String betName,
        ZonedDateTime gameDate,
        NbaTeam team1,
        NbaTeam team2,
        List<JsonNode> pointEvents
) {
}