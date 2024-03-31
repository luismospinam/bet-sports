package org.example;

import org.example.constant.AIMessages;
import org.example.db.basketball.NbaOldMatchesDao;
import org.example.db.basketball.NbaPointsDao;
import org.example.db.basketball.NbaStatisticsDao;
import org.example.db.basketball.NbaTeamsDao;
import org.example.logic.basketball.*;
import org.example.model.EventNbaPoints;
import org.example.model.NbaStatisticTeamHomeAway;
import org.example.model.NbaStatisticTeamIndividualMatches;
import org.example.model.NbaStatisticTeamsMatch;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Main {
    private static final String URL = "https://na-offering-api.kambicdn.net/offering/v2018/betplay/betoffer/event/%s.json?lang=es_CO&market=CO&client_id=2&channel_id=1&ncid=1711254430052&includeParticipants=true";

    public static void main(String[] args) throws Exception {
        NbaTeamsService nbaTeamsService = new NbaTeamsService(new NbaTeamsDao());
        nbaTeamsService.loadTeamStatistics();
        nbaTeamsService.syncTeamWinsLosses();

        NbaOldMatchesService nbaOldMatchesService = new NbaOldMatchesService(new NbaOldMatchesDao());
        nbaOldMatchesService.populateOldMatches();
        nbaTeamsService.updateTeamWinLosses();

        NbaPointsService nbaPointsService = new NbaPointsService(new NbaPointsDao());

        NbaMatchFinderService nbaMatchFinderService = new NbaMatchFinderService();
        while (true) {
            List<String> matchesId = nbaMatchFinderService.findMatchIds();
            List<EventNbaPoints> matchesPointsOdd = nbaPointsService.findMatchesPointsOdd(URL, matchesId);

            matchesPointsOdd.forEach(Main::createAIMessageQuestion);

            int sleepMins = new Random().nextInt(2, 5);
            System.out.println("Sleeping for: " + sleepMins + " minutes.");
            TimeUnit.MINUTES.sleep(sleepMins);
        }
    }

    private static void createAIMessageQuestion(EventNbaPoints inputMatch) {
        NbaStatisticsService nbaStatisticsService = new NbaStatisticsService(new NbaStatisticsDao());
        String team1 = inputMatch.team1().getAlias();
        List<NbaStatisticTeamIndividualMatches> matchStatisticsTeam1 = nbaStatisticsService.findMatchesByTeam(team1);
        NbaStatisticTeamHomeAway nbaStatisticTeam1 = nbaStatisticsService.computeStatistics(matchStatisticsTeam1);


        String team2 = inputMatch.team2().getAlias();
        List<NbaStatisticTeamIndividualMatches> matchStatisticsTeam2 = nbaStatisticsService.findMatchesByTeam(team2);
        NbaStatisticTeamHomeAway nbaStatisticTeam2 = nbaStatisticsService.computeStatistics(matchStatisticsTeam2);

        List<NbaStatisticTeamsMatch> matchesBetweenTwoTeams = nbaStatisticsService.findMatchesBetweenTwoTeams(team1, team2);
        String recentMatchesResultString = matchesBetweenTwoTeams.stream()
                .map(match -> match.homeTeamAlias() + " at home with " + match.homeTeamTotalPoints() + " and " + match.awayTeamAlias() + " away with " + match.awayTeamTotalPoints() + " points")
                .collect(Collectors.joining(" and "));

        String AIMessage = AIMessages.AI_POINTS_MATCHES_MESSAGE.getMessage()
                .replaceAll(":teamAlias1", team1)
                .replaceAll(":teamAlias2", team2)
                .replaceAll(":team1WonMatchesHome", String.valueOf(inputMatch.team1().getWinsHome()))
                .replaceAll(":team1WonMatchesAway", String.valueOf(inputMatch.team1().getWinsAway()))
                .replaceAll(":team1LostMatchesHome", String.valueOf(inputMatch.team1().getLossesHome()))
                .replaceAll(":team1LostMatchesAway", String.valueOf(inputMatch.team1().getLossesAway()))
                .replaceAll(":team2WonMatchesHome", String.valueOf(inputMatch.team2().getWinsHome()))
                .replaceAll(":team2WonMatchesAway", String.valueOf(inputMatch.team2().getWinsAway()))
                .replaceAll(":team2LostMatchesHome", String.valueOf(inputMatch.team2().getLossesHome()))
                .replaceAll(":team2LostMatchesAway", String.valueOf(inputMatch.team2().getLossesAway()))
                .replaceAll(":pointsAverageHomeTeam1", String.valueOf(nbaStatisticTeam1.homeAverage()))
                .replaceAll(":pointsAverageAwayTeam1", String.valueOf(nbaStatisticTeam1.awayAverage()))
                .replaceAll(":pointsAverageHomeTeam2", String.valueOf(nbaStatisticTeam2.homeAverage()))
                .replaceAll(":pointsAverageAwayTeam2", String.valueOf(nbaStatisticTeam2.awayAverage()))
                .replaceAll(":minPointsHomeTeam1", String.valueOf(nbaStatisticTeam1.homeMinPoints()))
                .replaceAll(":minPointsAwayTeam1", String.valueOf(nbaStatisticTeam1.awayMinPoints()))
                .replaceAll(":maxPointsHomeTeam1", String.valueOf(nbaStatisticTeam1.homeMaxPoints()))
                .replaceAll(":maxPointsAwayTeam1", String.valueOf(nbaStatisticTeam1.awayMaxPoints()))
                .replaceAll(":minPointsHomeTeam2", String.valueOf(nbaStatisticTeam2.homeMinPoints()))
                .replaceAll(":minPointsAwayTeam2", String.valueOf(nbaStatisticTeam2.awayMinPoints()))
                .replaceAll(":maxPointsHomeTeam2", String.valueOf(nbaStatisticTeam2.homeMaxPoints()))
                .replaceAll(":maxPointsAwayTeam2", String.valueOf(nbaStatisticTeam2.awayMaxPoints()))
                .replaceAll(":sizeMatches", String.valueOf(matchesBetweenTwoTeams.size()))
                .replaceAll(":recentMatchesResults", recentMatchesResultString);

        System.out.println(AIMessage);
    }

}