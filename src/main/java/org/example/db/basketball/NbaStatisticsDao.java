package org.example.db.basketball;

import org.example.constant.HomeAway;
import org.example.db.DB;
import org.example.model.NbaStatisticTeamIndividualMatches;
import org.example.model.NbaStatisticTeamsMatch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class NbaStatisticsDao {
    private static final Connection dbConnection = DB.getConnection();

    public List<NbaStatisticTeamIndividualMatches> findMatchesByTeam(String alias) throws SQLException {
        String query = """
                select nt1.alias "alias", 'HOME' "at", nmm.team1_quarter1_points "q1", nmm.team1_quarter2_points "q2", nmm.team1_quarter3_points "q3", nmm.team1_quarter4_points "q4",
                    nmm.team1_total_points "total", nmm.game_date "game_date"
                from nba_matches nmm, nba_team nt1
                where nmm.team1_id = nt1.id and (nt1.alias = '%s')
                union all
                select nt1.alias "alias", 'AWAY' "at", nmm.team2_quarter1_points "q1", nmm.team2_quarter2_points "q2", nmm.team2_quarter3_points "q3", nmm.team2_quarter4_points "q4",
                    nmm.team2_total_points "total", nmm.game_date "game_date"
                from nba_matches nmm, nba_team nt1
                where nmm.team2_id = nt1.id and (nt1.alias = '%s')
                """;
        query = String.format(query, alias, alias);

        PreparedStatement preparedStatement = dbConnection.prepareStatement(query);
        ResultSet resultSet = preparedStatement.executeQuery();
        if (resultSet == null) {
            return List.of();
        }

        List<NbaStatisticTeamIndividualMatches> returnList = new ArrayList<>();
        while (resultSet.next()) {
            returnList.add(new NbaStatisticTeamIndividualMatches(
                    resultSet.getString("alias"),
                    HomeAway.valueOf(resultSet.getString("at")),
                    resultSet.getInt("q1"),
                    resultSet.getInt("q2"),
                    resultSet.getInt("q3"),
                    resultSet.getInt("q4"),
                    resultSet.getInt("total"),
                    resultSet.getTimestamp("game_date").toLocalDateTime()
            ));
        }

        return returnList;
    }

    public List<NbaStatisticTeamsMatch> findMatchesBetweenTwoTeams(String alias1, String alias2) throws SQLException {
        String query = """
              select nt1.alias "homeAlias", nmm.team1_quarter1_points "homeQ1", nmm.team1_quarter2_points "homeQ2", nmm.team1_quarter3_points "homeQ3", nmm.team1_quarter4_points "homeQ4", nmm.team1_total_points "homeTotal",
                 nt2.alias "awayAlias", nmm.team2_quarter1_points "awayQ1", nmm.team2_quarter2_points "awayQ2", nmm.team2_quarter3_points "awayQ3", nmm.team2_quarter4_points "awayQ4", nmm.team2_total_points "awayTotal",
                   nmm.game_date "game_date"
              from nba_matches nmm, nba_team nt1, nba_team nt2
              where nmm.team1_id = nt1.id and nmm.team2_id = nt2.id
                and ((nt1.alias = '%s' and nt2.alias = '%s') or (nt1.alias = '%s' and nt2.alias = '%s'))
                """;
        query = String.format(query, alias1, alias2, alias2, alias1);

        PreparedStatement preparedStatement = dbConnection.prepareStatement(query);

        ResultSet resultSet = preparedStatement.executeQuery();
        if (resultSet == null) {
            return List.of();
        }

        List<NbaStatisticTeamsMatch> returnList = new ArrayList<>();
        while (resultSet.next()) {
            returnList.add(new NbaStatisticTeamsMatch(
                    resultSet.getString("homeAlias"),
                    resultSet.getInt("homeQ1"),
                    resultSet.getInt("homeQ2"),
                    resultSet.getInt("homeQ3"),
                    resultSet.getInt("homeQ4"),
                    resultSet.getInt("homeTotal"),
                    resultSet.getString("awayAlias"),
                    resultSet.getInt("awayQ1"),
                    resultSet.getInt("awayQ2"),
                    resultSet.getInt("awayQ3"),
                    resultSet.getInt("awayQ4"),
                    resultSet.getInt("awayTotal"),
                    resultSet.getTimestamp("game_date").toLocalDateTime()
            ));
        }

        return returnList;
    }
}

