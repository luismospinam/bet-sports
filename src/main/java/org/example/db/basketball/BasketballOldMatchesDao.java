package org.example.db.basketball;

import org.example.db.DB;
import org.example.model.BasketballMatch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

public class BasketballOldMatchesDao {
    private static final Connection dbConnection = DB.getConnection();

    public Optional<LocalDate> getLastStoredMatchDate() throws SQLException {
        String query = "SELECT game_date FROM nba_matches ORDER BY game_date DESC LIMIT 1";

        PreparedStatement preparedStatement = dbConnection.prepareStatement(query);
        ResultSet resultSet = preparedStatement.executeQuery();
        if (resultSet == null || !resultSet.next()) {
            return Optional.empty();
        }

        return Optional.of(resultSet.getDate("game_date").toLocalDate());
    }

    public void persistMatch(BasketballMatch basketballMatch) throws SQLException {
        String query = """
                INSERT INTO nba_matches (team1_id, team1_quarter1_points, team1_quarter2_points, team1_quarter3_points, team1_quarter4_points, team1_total_points,
                team2_id, team2_quarter1_points, team2_quarter2_points, team2_quarter3_points, team2_quarter4_points, team2_total_points, game_date)
                VALUES (%d, %f, %f, %f, %f, %f, %d, %f, %f, %f, %f, %f, '%s');
                """;

        String finalQuery = String.format(query, basketballMatch.team1Id(), basketballMatch.team1Quarter1Points(), basketballMatch.team1Quarter2Points(),
                basketballMatch.team1Quarter3Points(), basketballMatch.team1Quarter4Points(), basketballMatch.team1TotalPoints(),
                basketballMatch.team2Id(), basketballMatch.team2Quarter1Points(), basketballMatch.team2Quarter2Points(),
                basketballMatch.team2Quarter3Points(), basketballMatch.team2Quarter4Points(), basketballMatch.team2TotalPoints(),
                basketballMatch.gameDate());
        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        preparedStatement.execute();
    }
}

