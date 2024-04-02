package org.example.db.basketball;

import org.example.constant.NbaTeamConference;
import org.example.db.DB;
import org.example.model.NbaTeam;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class NbaTeamsDao {
    private static final Connection dbConnection = DB.getConnection();

    public void persistTeamValues(NbaTeam team) throws SQLException {
        String query = """
                INSERT INTO nba_team (id, name, alias, short_name, games_played, total_average,
                  first_quarter_average, second_quarter_average, third_quarter_average, fourth_quarter_average, conference)
                VALUES ('%d', '%s', '%s', '%s', %d, %f, %f, %f, %f, %f, '%s');
                """;

        String finalQuery = String.format(query, team.getId(), team.getName(), team.getAlias(), team.getShortName(), team.getGamesPlayed(), team.getTotalAverage(),
                team.getFirstQuarterAverage(), team.getSecondQuarterAverage(), team.getThirdQuarterAverage(), team.getFourthQuarterAverage(), team.getConference());
        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        preparedStatement.execute();
    }

    public void deleteTeamsData() throws SQLException {
        String query = "DELETE FROM nba_team";
        PreparedStatement preparedStatement = dbConnection.prepareStatement(query);
        preparedStatement.execute();
    }

    public boolean teamAlreadyExist(NbaTeam team) throws SQLException {
        String query = "SELECT * FROM nba_team WHERE alias = '%s'";
        String finalQuery = String.format(query, team.getAlias());

        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        ResultSet resultSet = preparedStatement.executeQuery();

        return resultSet.next();
    }

    public void updateTeamValues(NbaTeam team) throws SQLException {
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

    public void updateTeamWinsLosses(NbaTeam team) throws SQLException {
        String query = """
                UPDATE nba_team SET
                wins_home = %d, losses_home = %d, wins_away = %d, losses_away = %d
                WHERE id = %d;
                """;

        String finalQuery = String.format(query, team.getWinsHome(), team.getLossesHome(), team.getWinsAway(), team.getLossesAway(), team.getId());
        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        preparedStatement.execute();
    }

    public Optional<NbaTeam> findTeamById(NbaTeam team) throws SQLException {
        String query = "SELECT * FROM nba_team WHERE id = '%d'";
        String finalQuery = String.format(query, team.getId());

        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        ResultSet resultSet = preparedStatement.executeQuery();

        if (resultSet.next()) {
            return Optional.of(new NbaTeam(
                    resultSet.getInt("id"),
                    resultSet.getString("name"),
                    resultSet.getString("alias"),
                    resultSet.getString("short_name"),
                    resultSet.getInt("games_played"),
                    resultSet.getDouble("total_average"),
                    resultSet.getDouble("first_quarter_average"),
                    resultSet.getDouble("second_quarter_average"),
                    resultSet.getDouble("third_quarter_average"),
                    resultSet.getDouble("fourth_quarter_average"),
                    resultSet.getInt("wins_home"),
                    resultSet.getInt("losses_home"),
                    resultSet.getInt("wins_away"),
                    resultSet.getInt("losses_away"),
                    NbaTeamConference.valueOf(resultSet.getString("conference"))
            ));
        }

        return Optional.empty();
    }
}
