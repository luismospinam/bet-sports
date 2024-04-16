package org.example.model;

import org.example.constant.BetStatus;

import java.time.ZonedDateTime;

public record BetPlacedChildren (
        Integer id,
        Integer betPlacedId,
        String matchName,
        ZonedDateTime matchDate,
        String type,
        Double line,
        Double odd,
        Double aiPrediction,
        String outcomeId,
        BetStatus status,
        Integer realPoints
) {

    public BetPlacedChildren withStatusAndRealPoints(BetStatus status, Integer realPoints) {
        return new BetPlacedChildren(id, betPlacedId, matchName, matchDate, type, line, odd, aiPrediction, outcomeId, status, realPoints);
    }
}
