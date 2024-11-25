package org.example.model;

import java.time.ZonedDateTime;

public record BetPlacedData(
        String eventId,
        String matchName,
        ZonedDateTime matchDate,
        Double odds,
        Double line,
        Long outcomeId,
        Double aiPrediction,
        String betType
) {
    public BetPlacedData withAIPrediction(Double aiPrediction) {
        return new BetPlacedData(eventId, matchName, matchDate, odds(), line, outcomeId, aiPrediction, betType);
    }

    @Override
    public String toString() {
        return matchName.replaceAll(" ", "") + betType + "-" + line;
    }
}