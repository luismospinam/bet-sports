package org.example.logic.basketball;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.constant.NbaTeamShortNames;
import org.example.db.DB;
import org.example.model.BasketballTeam;
import org.example.util.HttpUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static java.util.Map.entry;

public class BasketballTeamsService {

    private static final String URL = "https://swishanalytics.com/nba/ajax/nba-points-per-quarter-ajax.php";
    private static boolean isUpdated = false;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Connection dbConnection = DB.getConnection();
    public static final Map<Integer, BasketballTeam> teamMap = new HashMap<>();
    public static final Map<String, BasketballTeam> teamMapShortName = new HashMap<>();

    public void loadTeamStatistics() throws Exception {
        if (!isUpdated) {
            startProcess();
            System.out.println("Finished loading NBA team statistics");
        } else {
            System.out.println("NBA team statistics has already been loaded");
        }

    }

    private void startProcess() throws Exception {
        String response = HttpUtil.sendRequestMatch(URL);
        JsonNode jsonNode = objectMapper.readTree(response);

        deleteTeamsData();
        JsonNode teams = jsonNode.findValue("teams");
        for (JsonNode node : teams) {
            String alias = node.findPath("nickname").asText();
            String shortName = NbaTeamShortNames.shortNameMap.get(alias);
            BasketballTeam team = new BasketballTeam(
                    node.findPath("team_id").asInt(),
                    node.findPath("location").asText(),
                    alias,
                    shortName,
                    node.findPath("gp").asInt(),
                    node.findPath("avg_total").asDouble()
            );

            teamMap.put(team.getId(), team);
            teamMapShortName.put(shortName, team);
        }

        JsonNode quarters = jsonNode.findValue("points");
        for (JsonNode node : quarters) {
            BasketballTeam team = teamMap.get(node.findPath("team_id").asInt());
            if (node.findPath("period").asInt() == 1)
                team.setFirstQuarterAverage(node.findPath("points").asDouble() / team.getGamesPlayed());
            else if (node.findPath("period").asInt() == 2)
                team.setSecondQuarterAverage(node.findPath("points").asDouble() / team.getGamesPlayed());
            else if (node.findPath("period").asInt() == 3)
                team.setThirdQuarterAverage(node.findPath("points").asDouble() / team.getGamesPlayed());
            else if (node.findPath("period").asInt() == 4)
                team.setFourthQuarterAverage(node.findPath("points").asDouble() / team.getGamesPlayed());
        }

        teamMap.forEach((key, value) -> {
            try {
                persistTeamValues(value);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        isUpdated = true;
    }

    private void deleteTeamsData() throws SQLException {
        String query = "DELETE FROM nba_team";
        PreparedStatement preparedStatement = dbConnection.prepareStatement(query);
        preparedStatement.execute();
    }

    private void persistTeamValues(BasketballTeam team) throws SQLException {
        String query = """
                INSERT INTO nba_team (id, name, alias, short_name, games_played, total_average, first_quarter_average, second_quarter_average, third_quarter_average, fourth_quarter_average)
                VALUES ('%d', '%s', '%s', '%s', %d, %f, %f, %f, %f, %f);
                """;

        String finalQuery = String.format(query, team.getId(), team.getName(), team.getAlias(), team.getShortName(), team.getGamesPlayed(), team.getTotalAverage(),
                team.getFirstQuarterAverage(), team.getSecondQuarterAverage(), team.getThirdQuarterAverage(), team.getFourthQuarterAverage());
        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        preparedStatement.execute();
    }
}
