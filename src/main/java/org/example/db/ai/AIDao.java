package org.example.db.ai;

import org.example.db.DB;
import org.example.model.AIResponse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AIDao {
    private static final Connection dbConnection = DB.getConnection();

    public void persistNBAAIResponse(AIResponse aiResponse) throws SQLException {
        String query = """
                INSERT INTO ai_nba_response (match_name, question, response, ai_provider, ai_model, value, response_date)
                VALUES ('%s', '%s', '%s', '%s', '%s', '%s', '%s');
                """;

        String finalQuery = String.format(query, aiResponse.matchName(),
                aiResponse.question().replaceAll("'", ""),
                aiResponse.response().replaceAll("'", ""),
                aiResponse.aiProvider(), aiResponse.aiModel(), aiResponse.value(), aiResponse.responseDate());
        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        preparedStatement.execute();
    }

    public int findCountPreviousAIRuns(String matchName) {
        int countResponse;
        String query = "SELECT count(*) as count FROM ai_nba_response WHERE match_name = '%s';";
        try {
            query = query.formatted(matchName);
            PreparedStatement preparedStatement = dbConnection.prepareStatement(query);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet == null || !resultSet.next()) {
                countResponse = 0;
            } else {
                countResponse = resultSet.getInt("count");
            }

        } catch (Exception e) {
            System.out.println("Error in findCountPreviousAIRuns" + e.getMessage());
            throw new RuntimeException(e);
        }

        return countResponse;

    }
}

