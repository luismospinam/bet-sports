package org.example;

import org.example.constant.AIMessages;
import org.example.constant.HomeAway;
import org.example.db.ai.AIDao;
import org.example.db.basketball.NbaOldMatchesDao;
import org.example.db.basketball.NbaPointsDao;
import org.example.db.basketball.NbaStatisticsDao;
import org.example.db.basketball.NbaTeamsDao;
import org.example.logic.ai.AIService;
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
        AIService aiService = new AIService(new AIDao(), new NbaStatisticsService(new NbaStatisticsDao()));
        while (true) {
            List<String> matchesId = nbaMatchFinderService.findMatchIds();
            List<EventNbaPoints> matchesPointsOdd = nbaPointsService.findMatchesPointsOdd(URL, matchesId);

            matchesPointsOdd.forEach(aiService::createAIMessageQuestion);

            int sleepMins = new Random().nextInt(2, 5);
            System.out.println("Sleeping for: " + sleepMins + " minutes.");
            TimeUnit.MINUTES.sleep(sleepMins);
        }
    }

}