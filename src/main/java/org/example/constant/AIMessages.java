package org.example.constant;

public enum AIMessages {
    AI_POINTS_MATCHES_MESSAGE("""
        Given that the :teamAlias1 has won :team1WonMatchesHome matches at home and :team1WonMatchesAway away and lost :team1LostMatchesHome matches at home and :team1LostMatchesAway away
        and :teamAlias2 has won :team2WonMatchesHome matches at home and :team2WonMatchesAway away and lost :team2LostMatchesHome matches at home and :team2LostMatchesAway away
        :teamAlias1 average points is :pointsAverageHomeTeam1 at home and :pointsAverageAwayTeam1 away and the :teamAlias2 average points is :pointsAverageHomeTeam2 at home and :pointsAverageAwayTeam2 away
        The last :numberMatches games at home the :teamAlias1 played scored the following points :pointScoredLastMatchesHomeTeam1 and the last :numberMatches games away scored :pointScoredLastMatchesAwayTeam1 points
        and last :numberMatches games at home the :teamAlias2 played scored the following points :pointScoredLastMatchesHomeTeam2 and the last :numberMatches games away scored :pointScoredLastMatchesAwayTeam2 points
        The minimum points the :teamAlias1 have scored in a game is :minPointsHomeTeam1 at home and :minPointsAwayTeam1 points away and the maximum is :maxPointsHomeTeam1 at home and :maxPointsAwayTeam1 points away
        and the minimum points the :teamAlias2 have scored in a game is :minPointsHomeTeam2 at home and :minPointsAwayTeam2 points away and the maximum is :maxPointsHomeTeam2 at home and :maxPointsAwayTeam2 points away
        The :teamAlias1 has a home record of :team1HomeRecord and a current streak of :team1Streak and :teamAlias2 has a away record of :team2AwayRecord and a current streak of :team2Streak
        The :teamAlias1 has an overall point differential of :team1OverallPointDiff and an average point differential per game of :team1AveragePointDiff and the :teamAlias2 has an overall point differential of :team2OverallPointDiff and an average point differential per game of :team2AveragePointDiff
        The :teamAlias1 has an overall standing of :team1OverallStanding and by conference of :team1ConferenceStanding and :teamAlias2 has an overall standing of :team2OverallStanding and by conference of :team2ConferenceStanding
        The :teamAlias1 has a ratio (Scored/Attempted) of 2 points of :team1TwoPointsRatio%, a Ratio of 3 points of :team1ThreePointsRatio%, a ratio of free throws of :team1FreePointsRatio%, Offensive rebounds per game average of :team1OffensiveRebounds and defensive rebounds per game average of :team1DefensiveRebounds, steals per game average of :team1Steals and blocks per game average of :team1Blocks
        The :teamAlias2 has a ratio (Scored/Attempted) of 2 points of :team2TwoPointsRatio%, a Ratio of 3 points of :team2ThreePointsRatio%, a ratio of free throws of :team2FreePointsRatio%, Offensive rebounds per game average of :team2OffensiveRebounds and defensive rebounds per game average of :team2DefensiveRebounds, steals per game average of :team2Steals and blocks per game average of :team2Blocks
        The :sizeMatches most recent matches results between these 2 teams were: :recentMatchesResults
        what do you expect the next match total points between these 2 teams to be if the :teamAlias1 are in home and :teamAlias2 are away.
        Start the answer with 'Expected points: XXX' where XXX is the number of expected points and then give the explanation.
        """);

    private final String message;

    AIMessages(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
