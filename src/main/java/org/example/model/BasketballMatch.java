package org.example.model;

import java.time.Instant;
import java.time.LocalDateTime;

public record BasketballMatch(
        Integer id,
        Integer team1Id,
        Double team1Quarter1Points,
        Double team1Quarter2Points,
        Double team1Quarter3Points,
        Double team1Quarter4Points,
        Double team1TotalPoints,
        Integer team2Id,
        Double team2Quarter1Points,
        Double team2Quarter2Points,
        Double team2Quarter3Points,
        Double team2Quarter4Points,
        Double team2TotalPoints,
        Instant gameDate
) {
}
