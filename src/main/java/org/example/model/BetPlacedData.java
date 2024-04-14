package org.example.model;

public record BetPlacedData(
        String matchName,
        Double odds,
        Double line,
        Long outcomeId,
        Double aiPrediction,
        String betType
) {
    public BetPlacedData withAIPrediction(Double aiPrediction) {
        return new BetPlacedData(matchName, odds(), line, outcomeId, aiPrediction, betType);
    }

    @Override
    public String toString() {
        return matchName.replaceAll(" ", "");
    }
}