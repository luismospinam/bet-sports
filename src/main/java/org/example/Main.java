package org.example;

import org.example.db.ai.AIDao;
import org.example.db.basketball.*;
import org.example.logic.ai.AIService;
import org.example.logic.basketball.*;
import org.example.model.EventNbaPoints;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final NbaStatisticsDao nbaStatisticsDao = new NbaStatisticsDao();
    private static final NbaStatisticsService nbaStatisticsService = new NbaStatisticsService(nbaStatisticsDao);
    private static final NbaTeamsDao nbaTeamsDao = new NbaTeamsDao();
    private static final NbaTeamsService nbaTeamsService = new NbaTeamsService(nbaStatisticsService, nbaTeamsDao);
    private static final NbaOldMatchesDao nbaOldMatchesDao = new NbaOldMatchesDao();
    private static final NbaOldMatchesService nbaOldMatchesService = new NbaOldMatchesService(nbaOldMatchesDao);
    private static final NbaPointsDao nbaPointsDao = new NbaPointsDao();
    private static final NbaPointsService nbaPointsService = new NbaPointsService(nbaPointsDao);
    private static final NbaMatchFinderService nbaMatchFinderService = new NbaMatchFinderService();
    private static final NbaBetPlacerDao nbaBetPlacerDao = new NbaBetPlacerDao();
    private static final NbaBetPlacerService nbaBetPlacerService = new NbaBetPlacerService(nbaOldMatchesService, nbaBetPlacerDao);

    public static final boolean isPlayoffs = false;
    public static final boolean placeAutomaticBet = true;


    private static final AIDao aiDao = new AIDao();
    private static final AIService aiService = new AIService(aiDao, nbaStatisticsService);


    public static void main(String[] args) throws Exception {
        nbaTeamsService.loadTeamStatistics();
        nbaTeamsService.syncTeamWinsLosses();

        nbaOldMatchesService.populateOldMatches();
        nbaTeamsService.updateTeamWinLosses();

        while (true) {
            List<String> matchesId = nbaMatchFinderService.findMatchIds();
            List<EventNbaPoints> matchesPointsOdd = nbaPointsService.findMatchesPointsOdd(matchesId);

            System.out.println("---------------------------------------------------------");
            matchesPointsOdd.forEach(aiService::createAIMessageQuestion);
            System.out.println("---------------------------------------------------------");
            nbaBetPlacerService.placeBet(matchesPointsOdd, AIService.aiNbaMatchPoints, placeAutomaticBet);
            nbaBetPlacerService.finishPreviousBetsCompleted();
            System.out.println("---------------------------------------------------------");

            int sleepMins = new Random().nextInt(2, 5);
            System.out.println("Sleeping for: " + sleepMins + " minutes.");
            TimeUnit.MINUTES.sleep(sleepMins);
        }
    }

}