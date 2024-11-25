package org.example.model;

import java.time.LocalDateTime;

public record AIResponse(
        Integer id,
        String matchName,
        String question,
        String response,
        String aiProvider,
        String aiModel,
        String team1,
        String team1Points,
        String team2,
        String team2Points,
        String value,
        LocalDateTime responseDate
) {
}
