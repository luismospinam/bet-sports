package org.example;

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
        BasketballTeamsService basketballTeamsService = new BasketballTeamsService();
        basketballTeamsService.loadTeamStatistics();

        BasketballOldMatchesService basketballOldMatchesService = new BasketballOldMatchesService();
        basketballOldMatchesService.populateOldMatches();

        BasketballPointsService basketballPointsService = new BasketballPointsService();
        List<String> matchesId = List.of("1020031450", "1020031446", "1020031442", "1020031438");
        while (true) {
            List<EventBasketballPoints> matchesPointsOdd = basketballPointsService.findMatchesPointsOdd(URL, matchesId);
            for (EventBasketballPoints eventBasketballPoints : matchesPointsOdd) {
                basketballPointsService.persistEventValues(eventBasketballPoints);
            }

            int sleepMins = new Random().nextInt(9, 18);
            System.out.println("Sleeping for: " + sleepMins + " minutes.");
            TimeUnit.MINUTES.sleep(sleepMins);
        }
    }
}