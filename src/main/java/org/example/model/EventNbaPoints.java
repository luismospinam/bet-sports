package org.example.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

public record EventNbaPoints(
        String id,
        String matchMame,
        ZonedDateTime gameDate,
        NbaTeam team1,
        NbaTeam team2,
        List<JsonNode> pointEvents
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventNbaPoints that = (EventNbaPoints) o;
        return Objects.equals(id, that.id) && Objects.equals(team1, that.team1) && Objects.equals(team2, that.team2) && Objects.equals(matchMame, that.matchMame) && Objects.equals(gameDate, that.gameDate) && Objects.equals(pointEvents, that.pointEvents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, matchMame, gameDate, team1, team2, pointEvents);
    }
}