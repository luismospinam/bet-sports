package org.example.logic.basketball;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.db.DB;
import org.example.model.BasketballMatch;
import org.example.util.HttpUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class BasketballOldMatchesService {
    private static final String MATCHES_URL = "https://site.web.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard?region=ph&lang=en&contentorigin=espn&limit=100&calendartype=offdays&dates=%S";
    private static final Connection dbConnection = DB.getConnection();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public void populateOldMatches() throws Exception {
        LocalDate currentLocalDate = LocalDate.now().minusDays(1);
        String currentDate = currentLocalDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String currentUrl = String.format(MATCHES_URL, currentDate);

        String response = HttpUtil.sendRequestMatch(currentUrl);
        JsonNode jsonNode = objectMapper.readTree(response);
        JsonNode matches = jsonNode.findValue("events");

        for (JsonNode match : matches) {
            String[] teams = match.findValue("shortName").textValue().split(" @ ");
            String localTeamName = teams[1];
            String visitorTeamName = teams[0];

            JsonNode competitions = match.findValue("competitions");
            JsonNode competitors = competitions.findValue("competitors");
            JsonNode localTeamData = competitors.get(0);
            JsonNode lineScoresLocalTeam = localTeamData.findValue("linescores");
            JsonNode visitorTeamData = competitors.get(1);
            JsonNode lineScoresVisitorTeam = visitorTeamData.findValue("linescores");

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmX");
            BasketballMatch basketballMatch = new BasketballMatch(
                    null,
                    BasketballTeamsService.teamMapShortName.get(localTeamName).getId(),
                    lineScoresLocalTeam.get(0).findPath("value").doubleValue(),
                    lineScoresLocalTeam.get(1).findPath("value").doubleValue(),
                    lineScoresLocalTeam.get(2).findPath("value").doubleValue(),
                    lineScoresLocalTeam.get(3).findPath("value").doubleValue(),
                    Double.valueOf(localTeamData.findValue("score").textValue()),
                    BasketballTeamsService.teamMapShortName.get(visitorTeamName).getId(),
                    lineScoresVisitorTeam.get(0).findPath("value").doubleValue(),
                    lineScoresVisitorTeam.get(1).findPath("value").doubleValue(),
                    lineScoresVisitorTeam.get(2).findPath("value").doubleValue(),
                    lineScoresVisitorTeam.get(3).findPath("value").doubleValue(),
                    Double.valueOf(visitorTeamData.findValue("score").textValue()),
                    Instant.from(formatter.parse(competitions.findValue("date").textValue()))
            );

            persistMatch(basketballMatch);
        }
    }

    private void persistMatch(BasketballMatch basketballMatch) throws SQLException {
        String query = """
                INSERT INTO nba_matches (team1_id, team1_quarter1_points, team1_quarter2_points, team1_quarter3_points, team1_quarter4_points, team1_total_points,
                team2_id, team2_quarter1_points, team2_quarter2_points, team2_quarter3_points, team2_quarter4_points, team2_total_points, game_date)
                VALUES (%d, %f, %f, %f, %f, %f, %d, %f, %f, %f, %f, %f, '%s');
                """;

        String finalQuery = String.format(query, basketballMatch.team1Id(), basketballMatch.team1Quarter1Points(), basketballMatch.team1Quarter2Points(),
                basketballMatch.team1Quarter3Points(), basketballMatch.team1Quarter4Points(), basketballMatch.team1TotalPoints(),
                basketballMatch.team2Id(), basketballMatch.team2Quarter1Points(), basketballMatch.team2Quarter2Points(),
                basketballMatch.team2Quarter3Points(), basketballMatch.team2Quarter4Points(), basketballMatch.team2TotalPoints(),
                basketballMatch.gameDate());
        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        preparedStatement.execute();
    }
}

