package org.example.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record EventBasketballPoints(
        String id,
        String matchMame,
        String betName,
        java.time.Instant gameDate,
        String team1,
        String team2,
        List<JsonNode> pointEvents
) {}