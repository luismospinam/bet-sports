package org.example.model;

public record NbaStatisticTeamHomeAway(
        double homeAverage,
        double homeMinPoints,
        double homeMaxPoints,
        double awayAverage,
        double awayMinPoints,
        double awayMaxPoints
) {

}
