package org.example.constant;

import java.util.Map;

import static java.util.Map.entry;

public class NbaTeamShortNames {
    public static final Map<String, String> shortNameMap = Map.ofEntries(
            entry("Hawks", "ATL"),
            entry("Celtics", "BOS"),
            entry("Pelicans", "NO"),
            entry("Bulls", "CHI"),
            entry("Cavaliers", "CLE"),
            entry("Mavericks", "DAL"),
            entry("Nuggets", "DEN"),
            entry("Pistons", "DET"),
            entry("Warriors", "GS"),
            entry("Rockets", "HOU"),
            entry("Pacers", "IND"),
            entry("Clippers", "LAC"),
            entry("Lakers", "LAL"),
            entry("Heat", "MIA"),
            entry("Bucks", "MIL"),
            entry("Timberwolves", "MIN"),
            entry("Nets", "BKN"),
            entry("Knicks", "NY"),
            entry("Magic", "ORL"),
            entry("76ers", "PHI"),
            entry("Suns", "PHX"),
            entry("Trail Blazers", "POR"),
            entry("Kings", "SAC"),
            entry("Spurs", "SA"),
            entry("Thunder", "OKC"),
            entry("Jazz", "UTAH"),
            entry("Wizards", "WSH"),
            entry("Raptors", "TOR"),
            entry("Grizzlies", "MEM"),
            entry("Hornets", "CHA")
    );

    private NbaTeamShortNames() {
    }
}
