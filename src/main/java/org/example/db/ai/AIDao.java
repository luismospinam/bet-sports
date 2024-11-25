package org.example.db.ai;

import org.example.db.DB;
import org.example.model.AIResponse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AIDao {
    private static final Connection dbConnection = DB.getConnection();

    public void persistNBAAIResponse(AIResponse aiResponse) throws SQLException {
        String query = """
                INSERT INTO ai_nba_response (match_name, question, response, ai_provider, ai_model, value, team1, team1_points, team2, team2_points, response_date)
                VALUES ('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s');
                """;

        String finalQuery = String.format(query, aiResponse.matchName(),
                aiResponse.question().replaceAll("'", ""),
                aiResponse.response().replaceAll("'", ""),
                aiResponse.aiProvider(), aiResponse.aiModel(), aiResponse.value(),
                aiResponse.team1(), aiResponse.team1Points(),
                aiResponse.team2(), aiResponse.team2Points(),
                aiResponse.responseDate());
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

    public List<AIResponse> findPreviousAIRunsByMatchNameWithDateLike(String matchName) throws SQLException {
        String query = "SELECT * FROM ai_nba_response WHERE match_name like '%:matchName%'";
        String finalQuery = query.replaceAll(":matchName", matchName);

        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        ResultSet resultSet = preparedStatement.executeQuery();

        List<AIResponse> returnList = new ArrayList<>();
        while (resultSet.next()) {
            returnList.add(new AIResponse(
                    resultSet.getInt("id"),
                    resultSet.getString("match_name"),
                    resultSet.getString("question"),
                    resultSet.getString("response"),
                    resultSet.getString("ai_provider"),
                    resultSet.getString("ai_model"),
                    resultSet.getString("team1"),
                    resultSet.getString("team1_points"),
                    resultSet.getString("team2"),
                    resultSet.getString("team2_points"),
                    resultSet.getString("value"),
                    resultSet.getDate("response_date").toLocalDate().atStartOfDay()
            ));
        }

        return returnList;
    }
}

