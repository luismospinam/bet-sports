package org.example.db.basketball;

import org.example.db.DB;
import org.example.model.BasketballTeam;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BasketballTeamsDao {
    private static final Connection dbConnection = DB.getConnection();

    public void persistTeamValues(BasketballTeam team) throws SQLException {
        String query = """
                INSERT INTO nba_team (id, name, alias, short_name, games_played, total_average, first_quarter_average, second_quarter_average, third_quarter_average, fourth_quarter_average)
                VALUES ('%d', '%s', '%s', '%s', %d, %f, %f, %f, %f, %f);
                """;

        String finalQuery = String.format(query, team.getId(), team.getName(), team.getAlias(), team.getShortName(), team.getGamesPlayed(), team.getTotalAverage(),
                team.getFirstQuarterAverage(), team.getSecondQuarterAverage(), team.getThirdQuarterAverage(), team.getFourthQuarterAverage());
        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        preparedStatement.execute();
    }

    public void deleteTeamsData() throws SQLException {
        String query = "DELETE FROM nba_team";
        PreparedStatement preparedStatement = dbConnection.prepareStatement(query);
        preparedStatement.execute();
    }

    public boolean teamAlreadyExist(BasketballTeam team) throws SQLException {
        String query = "SELECT * FROM nba_team WHERE alias = '%s'";
        String finalQuery = String.format(query, team.getAlias());

        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        ResultSet resultSet = preparedStatement.executeQuery();

        return resultSet.next();
    }

    public void updateTeamValues(BasketballTeam team) throws SQLException {
        String query = """
                UPDATE nba_team SET
                games_played = %d, total_average = %f, first_quarter_average = %f, second_quarter_average = %f, third_quarter_average = %f, fourth_quarter_average = %f
                WHERE id = %d;
                """;

        String finalQuery = String.format(query, team.getGamesPlayed(),
                team.getTotalAverage(), team.getFirstQuarterAverage(), team.getSecondQuarterAverage(), team.getThirdQuarterAverage(), team.getFourthQuarterAverage(),
                team.getId());
        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        preparedStatement.execute();
    }
}
