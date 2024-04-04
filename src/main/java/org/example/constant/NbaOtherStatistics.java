package org.example.constant;

public enum NbaOtherStatistics {
    AWAY_RECORD("Away Record"),
    PROJECTED_SEED_PLAYOFF("Projected seed in the NBA Playoffs, according to BPI"),
    WINNING_PERCENTAGE("Winning Percentage"),
    CLINCHED_CONFERENCE("Clinched Conference"),
    CONFERENCE_RECORD("Clinched Conference"),
    HOME_RECORD("Home Record"),
    GAMES_BACK("Games Back"),
    POINTS_PER_GAME("Points Per Game"),
    CURRENT_STREAK("Current Streak"),
    OPPONENT_POINTS("Opponent Points Per Game"),
    POINT_DIFFERENTIAL("Point Differential"),
    LOSSES("Losses"),
    AVERAGE_POINT_DIFFERENTIAL("Average Point Differential"),
    DIVISION_WINNING_PERCENTAGE("Division Winning Percentage"),
    DIVISION_RECORD("Division Record"),
    WINS("Wins"),
    RECORD_LAST_GAMES("Record last 10 games"),
    LEAGUE_WINNING_PERCENTAGE("League Winning Percentage");

    private String value;

    NbaOtherStatistics(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
