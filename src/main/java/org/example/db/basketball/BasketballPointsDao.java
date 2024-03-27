package org.example.db.basketball;

import org.example.constant.EventOddChange;
import org.example.db.DB;
import org.example.model.EventBasketballPoints;
import org.example.model.EventBasketballPointsLineTypeOdd;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.example.constant.Message.EXISTING_EVENT_CHANGE_ODD;

public class BasketballPointsDao {
    private static final Connection dbConnection = DB.getConnection();


    public void updateExistingEvent(EventBasketballPoints event,
                                    EventBasketballPointsLineTypeOdd existingEvent, String type, Double line, Double newOdd) throws SQLException {
        String query = """
                UPDATE bets SET odd = %f, date = NOW()
                WHERE id = %d
                """;
        String finalQuery = String.format(query, newOdd, existingEvent.id());
        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        preparedStatement.execute();


        query = """
                INSERT INTO bet_change (bets_id, old_odd, new_odd, action, date, message)
                VALUES (%d, %f, %f, '%s', NOW(), '%s');
                """;
        Double oldOdd = existingEvent.odd();
        String message = String.format(EXISTING_EVENT_CHANGE_ODD.getMessage(), existingEvent.id(), event.matchMame(), oldOdd, newOdd, line, type);
        EventOddChange eventOddChange = newOdd > oldOdd ? EventOddChange.INCREASE : EventOddChange.DECREASE;
        finalQuery = String.format(query, existingEvent.id(), oldOdd, newOdd, eventOddChange, message);
        preparedStatement = dbConnection.prepareStatement(finalQuery);
        preparedStatement.execute();
    }

    public void insertNewEvent(EventBasketballPoints event, String type, double line, double odds) throws SQLException {
        String query = """
                INSERT INTO bets (match_name, bet_name, game_date, team1, team2, bet_type, date, line, odd, betplay_id)
                VALUES ('%s', '%s', '%s', '%s', '%s', '%s', NOW(), %f, %f, %s);
                """;
        String finalQuery = String.format(query, event.matchMame(), event.betName(), event.gameDate(), event.team1(), event.team2(), type, line, odds, event.id());

        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        preparedStatement.execute();
    }

    public Optional<EventBasketballPointsLineTypeOdd> checkEventAlreadyExist(EventBasketballPoints event, double line, String type) throws SQLException {
        String query = """
                SELECT * FROM bets WHERE match_name = '%s' and bet_name = '%s' and game_date = '%s' and bet_type = '%s' and line = %f;
                """;
        query = String.format(query, event.matchMame(), event.betName(), LocalDate.ofInstant(event.gameDate(), ZoneOffset.UTC), type, line);

        PreparedStatement preparedStatement = dbConnection.prepareStatement(query);
        ResultSet resultSet = preparedStatement.executeQuery();

        if (resultSet == null || !resultSet.next()) {
            return Optional.empty();
        }

        return Optional.of(new EventBasketballPointsLineTypeOdd(
                resultSet.getInt("id"),
                resultSet.getString("bet_type"),
                resultSet.getDouble("line"),
                resultSet.getDouble("odd")
        ));
    }
}
