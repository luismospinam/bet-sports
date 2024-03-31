package org.example.constant;

public enum AIMessages {
    AI_POINTS_MATCHES_MESSAGE("""
        Given that the :teamAlias1 has won :team1WonMatchesHome matches at home and :team1WonMatchesAway away and lost :team1LostMatchesHome matches at home and :team1LostMatchesAway away
        and :teamAlias2 has won :team2WonMatchesHome matches at home and :team2WonMatchesAway away and lost :team2LostMatchesHome matches at home and :team2LostMatchesAway away
        :teamAlias1 average points is :pointsAverageHomeTeam1 at home and :pointsAverageAwayTeam1 away
        and the :teamAlias2 average points is :pointsAverageHomeTeam2 at home and :pointsAverageAwayTeam2 away
        The minimum points the :teamAlias1 have scored in a game is :minPointsHomeTeam1 at home and :minPointsAwayTeam1 points away and the maximum is :maxPointsHomeTeam1 at home and :maxPointsAwayTeam1 points away
        and the minimum points the :teamAlias2 have scored in a game is :minPointsHomeTeam2 at home and :minPointsAwayTeam2 points away and the maximum is :maxPointsHomeTeam2 at home and :maxPointsAwayTeam2 points away
        The :sizeMatches most recent matches results between these 2 teams were: :recentMatchesResults
        what do you expect the next match total points between these 2 teams to be if the :teamAlias1 are in home and :teamAlias2 are away.
        """);

    private final String message;

    AIMessages(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
