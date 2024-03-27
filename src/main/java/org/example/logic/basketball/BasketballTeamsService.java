package org.example.logic.basketball;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.constant.NbaTeamShortNames;
import org.example.db.basketball.BasketballTeamsDao;
import org.example.model.BasketballTeam;
import org.example.util.HttpUtil;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class BasketballTeamsService {

    private final BasketballTeamsDao basketballTeamsDao;
    private static final String URL = "https://swishanalytics.com/nba/ajax/nba-points-per-quarter-ajax.php";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static final Map<Integer, BasketballTeam> teamMap = new HashMap<>();
    public static final Map<String, BasketballTeam> teamMapShortName = new HashMap<>();


    public BasketballTeamsService(BasketballTeamsDao basketballTeamsDao) {
        this.basketballTeamsDao = basketballTeamsDao;
    }


    public void loadTeamStatistics() throws Exception {
        startProcess();
        System.out.println("Finished loading NBA team statistics");
    }

    private void startProcess() throws Exception {
        String response = HttpUtil.sendRequestMatch(URL);
        JsonNode jsonNode = objectMapper.readTree(response);

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

        teamMap.forEach((key, team) -> {
            try {
                if (basketballTeamsDao.teamAlreadyExist(team)) {
                    basketballTeamsDao.updateTeamValues(team);
                    System.out.println("updated team statistics " + team.getName());
                } else {
                    basketballTeamsDao.persistTeamValues(team);
                    System.out.println("inserted new team statistics " + team.getName());
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
