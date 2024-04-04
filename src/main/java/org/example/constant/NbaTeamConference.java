package org.example.constant;

import java.util.stream.Stream;

public enum NbaTeamConference {
    EAST("East"),
    WEST("West");

    private final String name;

    NbaTeamConference(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public NbaTeamConference getByName(String name) {
        return Stream.of(NbaTeamConference.values())
                .filter(conference -> conference.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
}
