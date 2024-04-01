package org.example.logic.basketball;

import org.example.constant.HomeAway;
import org.example.db.basketball.NbaStatisticsDao;
import org.example.model.NbaStatisticTeamHomeAway;
import org.example.model.NbaStatisticTeamIndividualMatches;
import org.example.model.NbaStatisticTeamsMatch;

import java.sql.SQLException;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NbaStatisticsService {

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
}
