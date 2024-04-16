package org.example.model;

import org.example.constant.BetStatus;

import java.time.LocalDateTime;
import java.util.List;

public record BetPlacedParent(
        Integer id,
        Integer numberMatches,
        Double totalOdd,
        Long amountBetPesos,
        Long amountEarned,
        BetStatus betStatus,
        String hashIdentifier,
        LocalDateTime date,
        List<BetPlacedChildren> matchesChildren
) {

    public BetPlacedParent withStatusAndAmountEarned(BetStatus status, Long amountEarned) {
        return new BetPlacedParent(id, numberMatches, totalOdd, amountBetPesos, amountEarned, status, hashIdentifier, date, matchesChildren);
    }

}

