package org.example.logic.basketball;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.db.basketball.NbaOldMatchesDao;
import org.example.model.NbaMatch;
import org.example.model.NbaTeam;
import org.example.util.HttpUtil;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class NbaOldMatchesService {
    private final NbaOldMatchesDao nbaOldMatchesDao;
    private static final String MATCHES_URL = "https://site.web.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard?region=ph&lang=en&contentorigin=espn&limit=100&calendartype=offdays&dates=%S";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int SECONDS_SLEEP_BETWEEN_REQUEST = 5;
    private static final LocalDate SEASON_START_DATE = LocalDate.of(2023, 10, 24);


    public NbaOldMatchesService(NbaOldMatchesDao nbaOldMatchesDao) {
        this.nbaOldMatchesDao = nbaOldMatchesDao;
    }

    public void populateOldMatches() throws Exception {
        Optional<LocalDate> lastStoredMatchDate = nbaOldMatchesDao.getLastStoredMatchDate();
        if (lastStoredMatchDate.isEmpty()) {
            loadAllPreviousMatches(SEASON_START_DATE);
        } else {
            loadAllPreviousMatches(lastStoredMatchDate.get().plusDays(1));
        }
    }

    private void loadAllPreviousMatches(LocalDate lowerLimitDate) throws Exception {
        LocalDate currentLocalDate = LocalDate.now();
        if (LocalTime.now().isBefore(LocalTime.of(23, 0))) {
            currentLocalDate = currentLocalDate.minusDays(1);
        }
        System.out.printf("Starting to load NBA previous matches from %s to %s %s", lowerLimitDate, currentLocalDate, System.lineSeparator());

        // TODO: Fix to start from lower limit until today
        while (currentLocalDate.isAfter(lowerLimitDate) || currentLocalDate.isEqual(lowerLimitDate)) {
            TimeUnit.SECONDS.sleep(SECONDS_SLEEP_BETWEEN_REQUEST); //to avoid getting blocked
            System.out.println("Finding games on " + currentLocalDate);
            String currentDate = currentLocalDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String currentUrl = String.format(MATCHES_URL, currentDate);

            String response = HttpUtil.sendGetRequestMatch(currentUrl);
            JsonNode jsonNode = objectMapper.readTree(response);
            JsonNode matches = jsonNode.findValue("events");

            for (JsonNode match : matches) {
                Optional<NbaMatch> basketballMatch = getBasketballMatch(match);
                if (basketballMatch.isPresent()) {
                    nbaOldMatchesDao.persistMatch(basketballMatch.get());
                }
            }
            currentLocalDate = currentLocalDate.minusDays(1);
        }
        System.out.println("Finished loading NBA previous matches" + System.lineSeparator());
    }

    private Optional<NbaMatch> getBasketballMatch(JsonNode match) {
        try {
            String shortName = match.findValue("shortName").textValue();
            String[] teams = shortName.contains(" @ ") ? shortName.split(" @ ") : shortName.split(" VS ");
            if (teams.length != 2) {
                System.out.println("No teams found in the match" + match);
                return Optional.empty();
            }

            String localTeamName = teams[1];
            String visitorTeamName = teams[0];

            JsonNode competitions = match.findValue("competitions");
            JsonNode competitors = competitions.findValue("competitors");
            JsonNode localTeamData = competitors.get(0);
            JsonNode lineScoresLocalTeam = localTeamData.findValue("linescores");
            JsonNode visitorTeamData = competitors.get(1);
            JsonNode lineScoresVisitorTeam = visitorTeamData.findValue("linescores");

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmX");
            NbaTeam localTeam = NbaTeamsService.teamMapShortName.get(localTeamName);
            NbaTeam awayTeam = NbaTeamsService.teamMapShortName.get(visitorTeamName);
            NbaMatch nbaMatch = new NbaMatch(
                    null,
                    localTeam.getId(),
                    lineScoresLocalTeam.get(0).findPath("value").doubleValue(),
                    lineScoresLocalTeam.get(1).findPath("value").doubleValue(),
                    lineScoresLocalTeam.get(2).findPath("value").doubleValue(),
                    lineScoresLocalTeam.get(3).findPath("value").doubleValue(),
                    Double.valueOf(localTeamData.findValue("score").textValue()),
                    awayTeam.getId(),
                    lineScoresVisitorTeam.get(0).findPath("value").doubleValue(),
                    lineScoresVisitorTeam.get(1).findPath("value").doubleValue(),
                    lineScoresVisitorTeam.get(2).findPath("value").doubleValue(),
                    lineScoresVisitorTeam.get(3).findPath("value").doubleValue(),
                    Double.valueOf(visitorTeamData.findValue("score").textValue()),
                    Instant.from(formatter.parse(competitions.findValue("date").textValue())).minus(5, ChronoUnit.HOURS)
            );

            NbaTeamsService.updateTeamWinsLosses(nbaMatch);

            return Optional.of(nbaMatch);
        } catch (Exception e) {
            System.out.println("Error in match " + match);
        }

        return Optional.empty();
    }

    public List<NbaMatch> findMatchesByNameAndGameDate(List<String> matchNames) {
        try {
            return nbaOldMatchesDao.findMatchesByNameAndGameDate(matchNames);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}

