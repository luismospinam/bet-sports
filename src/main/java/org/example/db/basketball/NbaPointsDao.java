package org.example.db.basketball;

import org.example.constant.EventOddChange;
import org.example.db.DB;
import org.example.model.EventNbaPoints;
import org.example.model.EventNbaPointsLineTypeOdd;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import static org.example.constant.Message.EXISTING_EVENT_CHANGE_ODD;

public class NbaPointsDao {
    private static final Connection dbConnection = DB.getConnection();


    public void updateExistingEvent(EventNbaPoints event,
                                    EventNbaPointsLineTypeOdd existingEvent, String type, Double line, Double newOdd) throws SQLException {
        String query = """
                UPDATE bets SET odd = %f, date = NOW()
                WHERE id = %d
                """;
        String finalQuery = String.format(query, newOdd, existingEvent.id());
        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        preparedStatement.execute();


        query = """
                INSERT INTO bet_change (bets_id, old_odd, new_odd, difference, action, date, message)
                VALUES (%d, %f, %f, %f, '%s', NOW(), '%s');
                """;
        Double oldOdd = existingEvent.odd();
        Double oddDifference = newOdd - oldOdd;
        String message = String.format(EXISTING_EVENT_CHANGE_ODD.getMessage(), existingEvent.id(), event.matchMame(), oldOdd, newOdd, line, type);
        EventOddChange eventOddChange = newOdd > oldOdd ? EventOddChange.INCREASE : EventOddChange.DECREASE;

        finalQuery = String.format(query, existingEvent.id(), oldOdd, newOdd, oddDifference, eventOddChange, message);
        preparedStatement = dbConnection.prepareStatement(finalQuery);
        preparedStatement.execute();
    }

    public void insertNewEvent(EventNbaPoints event, String type, double line, double odds) throws SQLException {
        String query = """
                INSERT INTO bets (match_name, bet_name, game_date, team1_id, team2_id, bet_type, date, line, odd, betplay_id)
                VALUES ('%s', '%s', '%s', '%s', '%s', '%s', NOW(), %f, %f, %s);
                """;
        String finalQuery = String.format(query, event.matchMame(), event.betName(), event.gameDate().toLocalDateTime(),
                event.team1().getId(), event.team2().getId(), type, line, odds, event.id());

        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        preparedStatement.execute();
    }

    public Optional<EventNbaPointsLineTypeOdd> checkEventAlreadyExist(EventNbaPoints event, double line, String type) throws SQLException {
        String query = """
                SELECT * FROM bets WHERE match_name = '%s' and bet_name = '%s' and game_date = '%s' and bet_type = '%s' and line = %f;
                """;
        query = String.format(query, event.matchMame(), event.betName(), event.gameDate().toLocalDateTime(), type, line);

        PreparedStatement preparedStatement = dbConnection.prepareStatement(query);
        ResultSet resultSet = preparedStatement.executeQuery();

        if (resultSet == null || !resultSet.next()) {
            return Optional.empty();
        }

        return Optional.of(new EventNbaPointsLineTypeOdd(
                resultSet.getInt("id"),
                resultSet.getString("bet_type"),
                resultSet.getDouble("line"),
                resultSet.getDouble("odd")
        ));
    }
}
