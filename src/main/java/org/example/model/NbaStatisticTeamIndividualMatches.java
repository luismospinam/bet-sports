package org.example.model;

import org.example.constant.HomeAway;

import java.time.LocalDateTime;

public record NbaStatisticTeamIndividualMatches(
        String alias,
        HomeAway homeAway,
        Integer quarter1Points,
        Integer quarter2Points,
        Integer quarter3Points,
        Integer quarter4Points,
        Integer totalPoints,
        LocalDateTime gameDate
) {

}
