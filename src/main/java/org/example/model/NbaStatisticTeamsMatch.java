package org.example.model;

import java.time.LocalDateTime;

public record NbaStatisticTeamsMatch(
        String homeTeamAlias,
        Integer homeTeamAliasQuarter1Points,
        Integer homeTeamAliasQuarter2Points,
        Integer homeTeamAliasQuarter3Points,
        Integer homeTeamAliasQuarter4Points,
        Integer homeTeamTotalPoints,
        String awayTeamAlias,
        Integer awayTeamAliasQuarter1Points,
        Integer awayTeamAliasQuarter2Points,
        Integer awayTeamAliasQuarter3Points,
        Integer awayTeamAliasQuarter4Points,
        Integer awayTeamTotalPoints,
        LocalDateTime gameDate
) {

}
