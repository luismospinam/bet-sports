package org.example.model;

import java.time.LocalDateTime;

public record AIResponse(
        Integer id,
        String matchName,
        String question,
        String response,
        String aiModel,
        String value,
        LocalDateTime responseDate
) {
}