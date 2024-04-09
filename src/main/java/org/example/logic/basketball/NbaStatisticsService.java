package org.example.logic.basketball;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.constant.HomeAway;
import org.example.db.basketball.NbaStatisticsDao;
import org.example.model.NbaStatisticTeamHomeAway;
import org.example.model.NbaStatisticTeamIndividualMatches;
import org.example.model.NbaStatisticTeamsMatch;
import org.example.model.NbaTeamOtherStatistics;
import org.example.util.HttpUtil;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NbaStatisticsService {

    private static final String CONFERENCE_STANDINGS_URL = "https://site.web.api.espn.com/apis/v2/sports/basketball/nba/standings?region=us&lang=en&contentorigin=deportes&type=0&level=2&sort=playoffseed:asc";
    private static final String OVERALL_STANDINGS_URL = "https://site.web.api.espn.com/apis/v2/sports/basketball/nba/standings?region=us&lang=en&contentorigin=deportes&type=0&level=1&sort=winpercent:desc,wins:desc,gamesbehind:asc";
    private static final String OTHER_TEAM_STATISTICS_URL = "https://site.web.api.espn.com/apis/common/v3/sports/basketball/nba/statistics/byteam?region=us&lang=en&contentorigin=deportes&sort=team.offensive.avgPoints:desc&limit=30";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final NbaStatisticsDao nbaStatisticsDao;

    public NbaStatisticsService(NbaStatisticsDao nbaStatisticsDao) {
        this.nbaStatisticsDao = nbaStatisticsDao;
    }

    public List<NbaStatisticTeamIndividualMatches> findMatchesByTeam(String alias) {
        try {
            return nbaStatisticsDao.findMatchesByTeam(alias);
        } catch (SQLException e) {
            System.err.println(e.getMessage() + " " + e.getErrorCode());
            throw new RuntimeException("Error while finding match statistics for team " + alias);
        }
    }

    public List<NbaStatisticTeamsMatch> findLastMatches(String alias, int amountMatches, HomeAway homeAway) {
        try {
            return nbaStatisticsDao.findLastMatches(alias, amountMatches, homeAway);
        } catch (SQLException e) {
            System.err.println(e.getMessage() + " " + e.getErrorCode());
            throw new RuntimeException(e);
        }
    }

    public NbaStatisticTeamHomeAway computeStatistics(List<NbaStatisticTeamIndividualMatches> matchStatisticsTeam1) {
            Map<HomeAway, List<NbaStatisticTeamIndividualMatches>> collect = matchStatisticsTeam1.stream()
                    .collect(Collectors.groupingBy(NbaStatisticTeamIndividualMatches::homeAway));

            IntSummaryStatistics homeStatistics = collect.get(HomeAway.HOME).stream()
                    .map(NbaStatisticTeamIndividualMatches::totalPoints)
                    .collect(Collectors.summarizingInt(Integer::intValue));
            IntSummaryStatistics awayStatistics = collect.get(HomeAway.AWAY).stream()
                    .map(NbaStatisticTeamIndividualMatches::totalPoints)
                    .collect(Collectors.summarizingInt(Integer::intValue));

            return new NbaStatisticTeamHomeAway(
                    homeStatistics.getAverage(),
                    homeStatistics.getMin(),
                    homeStatistics.getMax(),
                    awayStatistics.getAverage(),
                    awayStatistics.getMin(),
                    awayStatistics.getMax()
            );
    }

    public List<NbaStatisticTeamsMatch> findMatchesBetweenTwoTeams(String alias1, String alias2) {
        try {
            return nbaStatisticsDao.findMatchesBetweenTwoTeams(alias1, alias2);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error while finding match between teams  " + alias1 + " and " + alias2);
        }
    }

    public Map<String, NbaTeamOtherStatistics> findTeamStandingsOtherStatistics() throws Exception {
        Map<String, NbaTeamOtherStatistics> teamStandingsOtherStatisticsMap = new HashMap<>();

        String responseOverall = HttpUtil.sendRequestMatch(OVERALL_STANDINGS_URL);
        JsonNode jsonNodeOverall = objectMapper.readTree(responseOverall);
        JsonNode standings = jsonNodeOverall.findValue("standings").findValue("entries");
        int currentPositionOverall = 1;
        for (JsonNode overallStanding : standings) {
            String teamName = overallStanding.findValue("team").findValue("name").textValue();
            NbaTeamOtherStatistics teamStandings = new NbaTeamOtherStatistics(teamName);
            teamStandings.setStandingOverall(currentPositionOverall);
            teamStandingsOtherStatisticsMap.put(teamName, teamStandings);
            currentPositionOverall++;

            for (JsonNode stats : overallStanding.findValue("stats")) {
                JsonNode descriptionNode = stats.findValue("description");
                if (descriptionNode != null) {
                    String description = descriptionNode.textValue();
                    JsonNode value = stats.findValue("value");
                    if (value == null) {
                        value = stats.findValue("displayValue");
                    }
                    teamStandings.getOtherStatistics().put(description, value.asText());
                }
            }
        }


        String responseConference = HttpUtil.sendRequestMatch(CONFERENCE_STANDINGS_URL);
        JsonNode jsonNodeConference = objectMapper.readTree(responseConference);
        JsonNode children = jsonNodeConference.findValue("children");
        processConferenceStandingNode(children.get(0), teamStandingsOtherStatisticsMap);
        processConferenceStandingNode(children.get(1), teamStandingsOtherStatisticsMap);

        findOffensiveDefensiveStatistics(teamStandingsOtherStatisticsMap);

        return teamStandingsOtherStatisticsMap;
    }

    private void findOffensiveDefensiveStatistics(Map<String, NbaTeamOtherStatistics> teamStandingsOtherStatisticsMap) throws Exception {
        String responseOverall = HttpUtil.sendRequestMatch(OTHER_TEAM_STATISTICS_URL);
        JsonNode jsonNodeOverall = objectMapper.readTree(responseOverall);
        JsonNode categories = jsonNodeOverall.findValue("categories");
        JsonNode teams = jsonNodeOverall.findValue("teams");

        String categoryPrefix = "";
        for (int i = 0; i < categories.size(); i++) {
            JsonNode currentCategory = categories.get(i);
            JsonNode categoryNames = currentCategory.findValue("displayNames");
            if (categoryNames == null) {
                currentCategory = categories.get(i-1);
                categoryPrefix = categoryPrefix + "Opponent ";
                categoryNames = currentCategory.findValue("displayNames");
            }
            for (int j = 0; j < categoryNames.size(); j++) {
                String categoryName = categoryNames.get(j).textValue();
                for (JsonNode team: teams) {
                    String teamName = team.findValue("team").findValue("name").textValue();
                    JsonNode teamCategories = team.findValue("categories");
                    String value = teamCategories.get(i).findValue("values").get(j).asText();

                    teamStandingsOtherStatisticsMap.get(teamName).getOtherStatistics().put(categoryPrefix + categoryName, value);
                }
            }

            categoryPrefix = "";
        }
    }

    private void processConferenceStandingNode(JsonNode node, Map<String, NbaTeamOtherStatistics> teamStandingsOtherStatisticsMap) {
        JsonNode entries = node.findValue("entries");
        int currentPosition = 1;
        for (JsonNode entry : entries) {
            String teamName = entry.findValue("team").findValue("name").textValue();
            teamStandingsOtherStatisticsMap.get(teamName).setStandingConference(currentPosition);
            currentPosition++;
        }
    }
}
