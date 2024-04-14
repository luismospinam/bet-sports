package org.example.db.basketball;

import org.example.constant.BetStatus;
import org.example.db.DB;
import org.example.model.BetPlacedData;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

public class NbaBetPlacerDao {
    private static final Connection dbConnection = DB.getConnection();

    public void persistCombinations(List<BetPlacedData> betList, double totalOdds, int amountPesos) throws SQLException {
        String query = """
                INSERT INTO bet_placed (number_matches, total_odd, amount_bet_pesos, status, date)
                VALUES (%d, %f, %d, '%s', '%s');
                """;

        String finalQuery = String.format(query, betList.size(), totalOdds, amountPesos, BetStatus.PENDING, LocalDateTime.now());
        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery, Statement.RETURN_GENERATED_KEYS);
        preparedStatement.execute();

        long id = 0;
        try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                id = generatedKeys.getLong(1);
            } else {
                throw new SQLException("Creating bet, no ID obtained.");
            }
        }


        query = """
                INSERT INTO bet_placed_matches (bet_placed_id, match_name, type, line, odd, ai_prediction, outcome_id, status)
                VALUES (%d, '%s', '%s', %f, %f, %f, '%s', '%s');
                """;
        for (BetPlacedData bet : betList) {
            finalQuery = String.format(query, id, bet.matchName(), bet.betType(), bet.line(), bet.odds(), bet.aiPrediction(), bet.outcomeId(), BetStatus.PENDING);
            preparedStatement = dbConnection.prepareStatement(finalQuery);
            preparedStatement.execute();
        }
    }
}
