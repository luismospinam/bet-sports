package org.example.logic.basketball;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.constant.HomeAway;
import org.example.constant.NbaTeamConference;
import org.example.constant.NbaTeamConstant;
import org.example.db.basketball.NbaTeamsDao;
import org.example.model.NbaMatch;
import org.example.model.NbaTeam;
import org.example.model.NbaTeamOtherStatistics;
import org.example.util.HttpUtil;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.example.constant.NbaTeamConstant.conferenceTeamMap;

public class NbaTeamsService {

    private final NbaStatisticsService nbaStatisticsService;
    private final NbaTeamsDao nbaTeamsDao;
    private static final String URL = "https://swishanalytics.com/nba/ajax/nba-points-per-quarter-ajax.php";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static final Map<Integer, NbaTeam> teamMap = new HashMap<>();
    public static final Map<String, NbaTeam> teamMapShortName = new HashMap<>();
    public static Map<String, NbaTeamOtherStatistics> teamStandingsOtherStatisticsMap;


    public NbaTeamsService(NbaStatisticsService nbaStatisticsService, NbaTeamsDao nbaTeamsDao) {
        this.nbaStatisticsService = nbaStatisticsService;
        this.nbaTeamsDao = nbaTeamsDao;
    }

    public static void updateTeamWinsLosses(NbaMatch nbaMatch) {
        NbaTeam winnerTeam, loserTeam;
        if (nbaMatch.team1TotalPoints() > nbaMatch.team2TotalPoints()) {
            winnerTeam = teamMap.get(nbaMatch.team1Id());
            loserTeam = teamMap.get(nbaMatch.team2Id());

            updateTeamWinsLossesMap(true, winnerTeam, HomeAway.HOME);
            updateTeamWinsLossesMap(false, loserTeam, HomeAway.AWAY);
        } else {
            loserTeam = teamMap.get(nbaMatch.team1Id());
            winnerTeam = teamMap.get(nbaMatch.team2Id());

            updateTeamWinsLossesMap(true, winnerTeam, HomeAway.AWAY);
            updateTeamWinsLossesMap(false, loserTeam, HomeAway.HOME);
        }
    }

    private static void updateTeamWinsLossesMap(boolean winner, NbaTeam team, HomeAway where) {
        if (winner) {
            if (where.equals(HomeAway.HOME)) {
                Integer wins = team.getWinsHome();
                team.setWinsHome(wins == null ? 1 : wins + 1);
            } else {
                Integer wins = team.getWinsAway();
                team.setWinsAway(wins == null ? 1 : wins + 1);
            }
        } else {
            if (where.equals(HomeAway.HOME)) {
                Integer losses = team.getLossesHome();
                team.setLossesHome(losses == null ? 1 : losses + 1);
            } else {
                Integer losses = team.getLossesAway();
                team.setLossesAway(losses == null ? 1 : losses + 1);
            }
        }
    }


    public void loadTeamStatistics() throws Exception {
        teamStandingsOtherStatisticsMap = nbaStatisticsService.findTeamStandingsOtherStatistics();
        startProcess();
        System.out.println("Finished loading NBA team statistics");
    }

    private void startProcess() throws Exception {
        String response = HttpUtil.sendGetRequestMatch(URL);
        JsonNode jsonNode = objectMapper.readTree(response);

        JsonNode teams = jsonNode.findValue("teams");
        for (JsonNode node : teams) {
            String alias = node.findPath("nickname").asText();
            String shortName = NbaTeamConstant.shortNameMap.get(alias);
            NbaTeam team = new NbaTeam(
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
            NbaTeam team = teamMap.get(node.findPath("team_id").asInt());
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
                NbaTeamOtherStatistics standingsOtherStatistics = teamStandingsOtherStatisticsMap.get(team.getAlias());
                team.setStandingConference(standingsOtherStatistics.getStandingConference());
                team.setStandingOverall(standingsOtherStatistics.getStandingOverall());

                if (nbaTeamsDao.teamAlreadyExist(team)) {
                    nbaTeamsDao.updateTeamValues(team);
                    System.out.println("updated team statistics " + team.getName());
                } else {
                    NbaTeamConference conference = conferenceTeamMap.get(NbaTeamConference.WEST).contains(team.getAlias()) ? NbaTeamConference.WEST : NbaTeamConference.EAST;
                    team.setConference(conference);
                    nbaTeamsDao.persistTeamValues(team);
                    System.out.println("inserted new team statistics " + team.getName());
                }

                nbaTeamsDao.reInsertOtherStatistics(team, standingsOtherStatistics.getOtherStatistics());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void updateTeamWinLosses() {
        teamMap.forEach((key, team) -> {
            try {
                nbaTeamsDao.updateTeamWinsLosses(team);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void syncTeamWinsLosses() {
        teamMap.entrySet().forEach(entry -> {
            try {
                Optional<NbaTeam> syncedTeam = nbaTeamsDao.findTeamById(entry.getValue());
                syncedTeam.ifPresent(entry::setValue);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
