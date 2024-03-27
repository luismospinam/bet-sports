package org.example;

import org.example.db.basketball.BasketballOldMatchesDao;
import org.example.db.basketball.BasketballPointsDao;
import org.example.db.basketball.BasketballTeamsDao;
import org.example.logic.basketball.BasketballOldMatchesService;
import org.example.logic.basketball.BasketballPointsService;
import org.example.logic.basketball.BasketballTeamsService;
import org.example.model.EventBasketballPoints;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final String URL = "https://na-offering-api.kambicdn.net/offering/v2018/betplay/betoffer/event/%s.json?lang=es_CO&market=CO&client_id=2&channel_id=1&ncid=1711254430052&includeParticipants=true";

    public static void main(String[] args) throws Exception {
        BasketballTeamsService basketballTeamsService = new BasketballTeamsService(new BasketballTeamsDao());
        basketballTeamsService.loadTeamStatistics();

        BasketballOldMatchesService basketballOldMatchesService = new BasketballOldMatchesService(new BasketballOldMatchesDao());
        basketballOldMatchesService.populateOldMatches();

        BasketballPointsService basketballPointsService = new BasketballPointsService(new BasketballPointsDao());

        List<String> matchesId = List.of("1020031452", "1020031433", "1020031428", "1020031424", "1020031420", "1020031417", "1020031415", "1020031441", "1020031437", "1020031435");
        while (true) {
            List<EventBasketballPoints> matchesPointsOdd = basketballPointsService.findMatchesPointsOdd(URL, matchesId);

            int sleepMins = new Random().nextInt(5, 12);
            System.out.println("Sleeping for: " + sleepMins + " minutes.");
            TimeUnit.MINUTES.sleep(sleepMins);
        }
    }
}