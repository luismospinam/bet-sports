package org.example.model;

public class BasketballTeam {
    private int id;
    private String name;
    private String alias;
    private String shortName;
    private int gamesPlayed;
    private Double totalAverage;
    private Double firstQuarterAverage;
    private Double secondQuarterAverage;
    private Double thirdQuarterAverage;
    private Double fourthQuarterAverage;

    public BasketballTeam(int id, String name, String alias, String shortName, int gamesPlayed, Double totalAverage) {
        this.id = id;
        this.name = name;
        this.alias = alias;
        this.shortName = shortName;
        this.gamesPlayed = gamesPlayed;
        this.totalAverage = totalAverage;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public Double getTotalAverage() {
        return totalAverage;
    }

    public void setTotalAverage(Double totalAverage) {
        this.totalAverage = totalAverage;
    }

    public Double getFirstQuarterAverage() {
        return firstQuarterAverage;
    }

    public void setFirstQuarterAverage(Double firstQuarterAverage) {
        this.firstQuarterAverage = firstQuarterAverage;
    }

    public Double getSecondQuarterAverage() {
        return secondQuarterAverage;
    }

    public void setSecondQuarterAverage(Double secondQuarterAverage) {
        this.secondQuarterAverage = secondQuarterAverage;
    }

    public Double getThirdQuarterAverage() {
        return thirdQuarterAverage;
    }

    public void setThirdQuarterAverage(Double thirdQuarterAverage) {
        this.thirdQuarterAverage = thirdQuarterAverage;
    }

    public Double getFourthQuarterAverage() {
        return fourthQuarterAverage;
    }

    public void setFourthQuarterAverage(Double fourthQuarterAverage) {
        this.fourthQuarterAverage = fourthQuarterAverage;
    }

    public int getGamesPlayed() {
        return gamesPlayed;
    }

    public void setGamesPlayed(int gamesPlayed) {
        this.gamesPlayed = gamesPlayed;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }
}