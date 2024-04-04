package org.example.model;

import java.util.HashMap;
import java.util.Map;

public class NbaTeamOtherStatistics {
    private final String team;
    private Integer standingOverall;
    private Integer standingConference;
    private final Map<String, String> otherStatistics = new HashMap<>();


    public NbaTeamOtherStatistics(String team) {
        this.team = team;
    }

    public String getTeam() {
        return team;
    }

    public Integer getStandingOverall() {
        return standingOverall;
    }

    public void setStandingOverall(Integer standingOverall) {
        this.standingOverall = standingOverall;
    }

    public Integer getStandingConference() {
        return standingConference;
    }

    public void setStandingConference(Integer standingConference) {
        this.standingConference = standingConference;
    }

    public Map<String, String> getOtherStatistics() {
        return otherStatistics;
    }
}
