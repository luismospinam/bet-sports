package org.example.db.basketball;

import org.example.constant.BetStatus;
import org.example.db.DB;
import org.example.model.BetPlacedChildren;
import org.example.model.BetPlacedData;
import org.example.model.BetPlacedParent;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NbaBetPlacerDao {
    private static final Connection dbConnection = DB.getConnection();

    public void persistCombinations(List<BetPlacedData> betList, double totalOdds, int amountPesos, String hashIdentifier) throws SQLException {
        String query = """
                INSERT INTO bet_placed (number_matches, total_odd, amount_bet_pesos, status, hash_identifier, date)
                VALUES (%d, %f, %d, '%s', '%s', '%s');
                """;

        String finalQuery = String.format(query, betList.size(), totalOdds, amountPesos, BetStatus.PENDING, hashIdentifier, LocalDateTime.now());
        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery, Statement.RETURN_GENERATED_KEYS);
        preparedStatement.execute();

        long id = 0;
        try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                id = generatedKeys.getLong(1);
            } else {
                throw new SQLException("Creating bet, no ID obtained.");
            }
        }


        query = """
                INSERT INTO bet_placed_matches (bet_placed_id, match_name, match_date, type, line, odd, ai_prediction, outcome_id, status)
                VALUES (%d, '%s', '%s', '%s', %f, %f, %f, '%s', '%s');
                """;
        for (BetPlacedData bet : betList) {
            finalQuery = String.format(query, id, bet.matchName(), bet.matchDate().toLocalDateTime(), bet.betType(), bet.line(), bet.odds(), bet.aiPrediction(), bet.outcomeId(), BetStatus.PENDING);
            preparedStatement = dbConnection.prepareStatement(finalQuery);
            preparedStatement.execute();
        }
    }

    public List<BetPlacedParent> findBetsByStatusWithChildren(BetStatus betStatus) throws SQLException {
        String query = """
                SELECT * FROM bet_placed WHERE status = '%s';
                """;
        query = String.format(query, betStatus);

        PreparedStatement preparedStatement = dbConnection.prepareStatement(query);
        ResultSet resultSet = preparedStatement.executeQuery();

        if (resultSet == null) {
            return List.of();
        }

        List<BetPlacedParent> returnList = new ArrayList<>();
        while (resultSet.next()) {
            returnList.add(new BetPlacedParent(
                    resultSet.getInt("id"),
                    resultSet.getInt("number_matches"),
                    resultSet.getDouble("total_odd"),
                    resultSet.getLong("amount_bet_pesos"),
                    resultSet.getLong("amount_earned"),
                    BetStatus.valueOf(resultSet.getString("status")),
                    resultSet.getString("hash_identifier"),
                    resultSet.getTimestamp("date").toLocalDateTime(),
                    findBetChildrenMatches(resultSet.getInt("id"))
            ));
        }

        return returnList;
    }

    public List<BetPlacedChildren> findBetChildrenMatches(int id) throws SQLException {
        String query = """
                SELECT * FROM bet_placed_matches WHERE bet_placed_id = %d;
                """;
        query = String.format(query, id);

        PreparedStatement preparedStatement = dbConnection.prepareStatement(query);
        ResultSet resultSet = preparedStatement.executeQuery();

        if (resultSet == null) {
            return List.of();
        }

        List<BetPlacedChildren> returnList = new ArrayList<>();
        while (resultSet.next()) {
            returnList.add(new BetPlacedChildren(
                    resultSet.getInt("id"),
                    resultSet.getInt("bet_placed_id"),
                    resultSet.getString("match_name"),
                    resultSet.getTimestamp("match_date").toLocalDateTime().atZone(ZoneOffset.UTC),
                    resultSet.getString("type"),
                    resultSet.getDouble("line"),
                    resultSet.getDouble("odd"),
                    resultSet.getDouble("ai_prediction"),
                    resultSet.getString("outcome_id"),
                    BetStatus.valueOf(resultSet.getString("status")),
                    resultSet.getInt("real_points")
            ));
        }

        return returnList;

    }

    public void updateBetPlacedMatches(BetPlacedChildren betPlacedChildren) throws SQLException {
        String query = """
                UPDATE bet_placed_matches SET status = '%s', real_points = %d
                WHERE id = %d
                """;
        String finalQuery = String.format(query, betPlacedChildren.status(), betPlacedChildren.realPoints(), betPlacedChildren.id());
        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        preparedStatement.execute();
    }

    public void updateBetPlaceParent(BetPlacedParent betPlacedParent) throws SQLException {
        String query = """
                UPDATE bet_placed SET status = '%s', amount_earned = %d
                WHERE id = %d
                """;
        String finalQuery = String.format(query, betPlacedParent.betStatus(), betPlacedParent.amountEarned(), betPlacedParent.id());
        PreparedStatement preparedStatement = dbConnection.prepareStatement(finalQuery);
        preparedStatement.execute();
    }

    public Optional<BetPlacedParent> findByHashIdentifier(String hashBetCombination, String orderBy, String ascDesc) throws SQLException {
        if (ascDesc == null) {
            ascDesc = "asc";
        }
        String query = """
                SELECT * FROM bet_placed WHERE hash_identifier = '%s' ORDER BY %s %s;
                """;
        query = String.format(query, hashBetCombination, orderBy, ascDesc);

        PreparedStatement preparedStatement = dbConnection.prepareStatement(query);
        ResultSet resultSet = preparedStatement.executeQuery();

        if (resultSet == null || !resultSet.next()) {
            return Optional.empty();
        }

        return Optional.of(new BetPlacedParent(
                resultSet.getInt("id"),
                resultSet.getInt("number_matches"),
                resultSet.getDouble("total_odd"),
                resultSet.getLong("amount_bet_pesos"),
                resultSet.getLong("amount_earned"),
                BetStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("hash_identifier"),
                resultSet.getTimestamp("date").toLocalDateTime(),
                null
        ));

    }

    public Double findMinTotalLinePointsParentBetByHash(String hash) throws SQLException {
        String query = """
               SELECT sum(m.line) sum, b.id
               FROM bet_placed_matches m, bet_placed b
               WHERE m.bet_placed_id = b.id
                and b.hash_identifier = '%s'
               group by b.id
               order by sum asc
                """;
        query = String.format(query, hash);

        PreparedStatement preparedStatement = dbConnection.prepareStatement(query);
        ResultSet resultSet = preparedStatement.executeQuery();

        if (!resultSet.next()) {
            return Double.MAX_VALUE;
        }

        return resultSet.getDouble("sum");
    }
}
