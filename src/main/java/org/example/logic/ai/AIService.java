package org.example.logic.ai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.example.constant.AIMessages;
import org.example.constant.HomeAway;
import org.example.constant.OpenAIModels;
import org.example.db.ai.AIDao;
import org.example.logic.basketball.NbaStatisticsService;
import org.example.model.*;
import org.example.util.PropertiesLoaderUtil;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AIService {
    private static final Properties properties;
    private static final OpenAIModels DEFAULT_MODEL_NAME = OpenAIModels.GPT_3_5_TURBO;
    private static final Integer MAX_AI_RUNS = 20;
    private static final Integer COUNT_LAST_MATCHES = 5;
    private static final Integer MAX_TOKENS_COUNT = 400;

    private static String OPEN_API_KEY;

    private final AIDao aiDao;
    private final NbaStatisticsService nbaStatisticsService;


    static {
        properties = PropertiesLoaderUtil.loadProperties("src/test/credentials.properties");

        String openApiKey = properties.getProperty("OPEN_AI_KEY");
        if (openApiKey == null || openApiKey.isEmpty()) {
            throw new RuntimeException("OPEN_AI_KEY can not be empty in credentials.properties");
        }

        AIService.OPEN_API_KEY = openApiKey;
    }


    public AIService(AIDao aiDao, NbaStatisticsService nbaStatisticsService) {
        this.aiDao = aiDao;
        this.nbaStatisticsService = nbaStatisticsService;
    }

    private String sendQuestion(String question, OpenAIModels modelName) {
        String modelNameString = modelName == null ? DEFAULT_MODEL_NAME.getName() : modelName.getName();

        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(OPEN_API_KEY)
                .maxTokens(AIService.MAX_TOKENS_COUNT)
                .modelName(modelNameString)
                .logRequests(true)
                .logResponses(true)
                .build();


        return model.generate(question);
    }

    private void persistAIResponse(AIResponse aiResponse) {
        try {
            aiDao.persistNBAAIResponse(aiResponse);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void createAIMessageQuestion(EventNbaPoints inputMatch) throws RuntimeException {
        String matchName = inputMatch.matchMame() + " - " + inputMatch.gameDate().toLocalDate();
        int countPreviousAIRuns = aiDao.findCountPreviousAIRuns(matchName);
        if (countPreviousAIRuns >= MAX_AI_RUNS) {
            System.out.printf("%s has already ran the AI model %d times%s", matchName, MAX_AI_RUNS, System.lineSeparator());
            return;
        }

        String team1 = inputMatch.team1().getAlias();
        List<NbaStatisticTeamIndividualMatches> matchStatisticsTeam1 = nbaStatisticsService.findMatchesByTeam(team1);
        NbaStatisticTeamHomeAway nbaStatisticTeam1 = nbaStatisticsService.computeStatistics(matchStatisticsTeam1);
        List<NbaStatisticTeamsMatch> team1HomeMatches = nbaStatisticsService.findLastMatches(team1, COUNT_LAST_MATCHES, HomeAway.HOME);
        List<NbaStatisticTeamsMatch> team1AwayMatches = nbaStatisticsService.findLastMatches(team1, COUNT_LAST_MATCHES, HomeAway.AWAY);

        String team2 = inputMatch.team2().getAlias();
        List<NbaStatisticTeamIndividualMatches> matchStatisticsTeam2 = nbaStatisticsService.findMatchesByTeam(team2);
        NbaStatisticTeamHomeAway nbaStatisticTeam2 = nbaStatisticsService.computeStatistics(matchStatisticsTeam2);
        List<NbaStatisticTeamsMatch> team2HomeMatches = nbaStatisticsService.findLastMatches(team2, COUNT_LAST_MATCHES, HomeAway.HOME);
        List<NbaStatisticTeamsMatch> team2AwayMatches = nbaStatisticsService.findLastMatches(team2, COUNT_LAST_MATCHES, HomeAway.AWAY);


        List<NbaStatisticTeamsMatch> matchesBetweenTwoTeams = nbaStatisticsService.findMatchesBetweenTwoTeams(team1, team2);

        String AIQuestion = formatAiQuestion(inputMatch, team1, team2, nbaStatisticTeam1, nbaStatisticTeam2, team1HomeMatches, team1AwayMatches, team2HomeMatches, team2AwayMatches, matchesBetweenTwoTeams);

        AIResponse aiResponse;
        if (countPreviousAIRuns == 0) {
            aiResponse = invokeAIAPI(matchName, AIQuestion, OpenAIModels.GPT_4_TURBO_PREVIEW);
        } else {
            aiResponse = invokeAIAPI(matchName, AIQuestion, DEFAULT_MODEL_NAME);
        }
        persistAIResponse(aiResponse);

        System.out.println("Finished AI for " + inputMatch.matchMame());
    }

    @NotNull
    private AIResponse invokeAIAPI(String matchName, String AIQuestion, OpenAIModels model) {
        String regexPoints = "(?<=points: )[0-9]+";

        String response = sendQuestion(AIQuestion, model);

        String value = "XXX";
        Matcher matcher1 = Pattern.compile(regexPoints, Pattern.CASE_INSENSITIVE).matcher(response);
        if (matcher1.find()) {
           value = matcher1.group();
        } else {
            System.out.println("Could not find points value in " + response);
        }

        return new AIResponse(null, matchName, AIQuestion, response, model.getName(), value, LocalDateTime.now());
    }

    @NotNull
    private static String formatAiQuestion(EventNbaPoints inputMatch, String team1, String team2,
                                           NbaStatisticTeamHomeAway nbaStatisticTeam1, NbaStatisticTeamHomeAway nbaStatisticTeam2,
                                           List<NbaStatisticTeamsMatch> team1HomeMatches, List<NbaStatisticTeamsMatch> team1AwayMatches,
                                           List<NbaStatisticTeamsMatch> team2HomeMatches, List<NbaStatisticTeamsMatch> team2AwayMatches,
                                           List<NbaStatisticTeamsMatch> matchesBetweenTwoTeams) {
        String recentMatchesResultString = matchesBetweenTwoTeams.stream()
                .map(match -> match.homeTeamAlias() + " at home with " + match.homeTeamTotalPoints() + " and " + match.awayTeamAlias() + " away with " + match.awayTeamTotalPoints() + " points")
                .collect(Collectors.joining(" and "));

        return AIMessages.AI_POINTS_MATCHES_MESSAGE.getMessage()
                .replaceAll(":teamAlias1", team1)
                .replaceAll(":teamAlias2", team2)
                .replaceAll(":team1WonMatchesHome", String.valueOf(inputMatch.team1().getWinsHome()))
                .replaceAll(":team1WonMatchesAway", String.valueOf(inputMatch.team1().getWinsAway()))
                .replaceAll(":team1LostMatchesHome", String.valueOf(inputMatch.team1().getLossesHome()))
                .replaceAll(":team1LostMatchesAway", String.valueOf(inputMatch.team1().getLossesAway()))
                .replaceAll(":team2WonMatchesHome", String.valueOf(inputMatch.team2().getWinsHome()))
                .replaceAll(":team2WonMatchesAway", String.valueOf(inputMatch.team2().getWinsAway()))
                .replaceAll(":team2LostMatchesHome", String.valueOf(inputMatch.team2().getLossesHome()))
                .replaceAll(":team2LostMatchesAway", String.valueOf(inputMatch.team2().getLossesAway()))
                .replaceAll(":pointsAverageHomeTeam1", String.valueOf(nbaStatisticTeam1.homeAverage()))
                .replaceAll(":pointsAverageAwayTeam1", String.valueOf(nbaStatisticTeam1.awayAverage()))
                .replaceAll(":pointsAverageHomeTeam2", String.valueOf(nbaStatisticTeam2.homeAverage()))
                .replaceAll(":pointsAverageAwayTeam2", String.valueOf(nbaStatisticTeam2.awayAverage()))
                .replaceAll(":numberMatches", String.valueOf(COUNT_LAST_MATCHES))
                .replaceAll(":pointScoredLastMatchesHomeTeam1", team1HomeMatches.stream()
                        .map(NbaStatisticTeamsMatch::homeTeamTotalPoints)
                        .map(String::valueOf)
                        .collect(Collectors.joining(",")))
                .replaceAll(":pointScoredLastMatchesAwayTeam1", team1AwayMatches.stream()
                        .map(NbaStatisticTeamsMatch::awayTeamTotalPoints)
                        .map(String::valueOf)
                        .collect(Collectors.joining(",")))
                .replaceAll(":pointScoredLastMatchesHomeTeam2", team2HomeMatches.stream()
                        .map(NbaStatisticTeamsMatch::homeTeamTotalPoints)
                        .map(String::valueOf)
                        .collect(Collectors.joining(",")))
                .replaceAll(":pointScoredLastMatchesAwayTeam2", team2AwayMatches.stream()
                        .map(NbaStatisticTeamsMatch::awayTeamTotalPoints)
                        .map(String::valueOf)
                        .collect(Collectors.joining(",")))
                .replaceAll(":minPointsHomeTeam1", String.valueOf(nbaStatisticTeam1.homeMinPoints()))
                .replaceAll(":minPointsAwayTeam1", String.valueOf(nbaStatisticTeam1.awayMinPoints()))
                .replaceAll(":maxPointsHomeTeam1", String.valueOf(nbaStatisticTeam1.homeMaxPoints()))
                .replaceAll(":maxPointsAwayTeam1", String.valueOf(nbaStatisticTeam1.awayMaxPoints()))
                .replaceAll(":minPointsHomeTeam2", String.valueOf(nbaStatisticTeam2.homeMinPoints()))
                .replaceAll(":minPointsAwayTeam2", String.valueOf(nbaStatisticTeam2.awayMinPoints()))
                .replaceAll(":maxPointsHomeTeam2", String.valueOf(nbaStatisticTeam2.homeMaxPoints()))
                .replaceAll(":maxPointsAwayTeam2", String.valueOf(nbaStatisticTeam2.awayMaxPoints()))
                .replaceAll(":sizeMatches", String.valueOf(matchesBetweenTwoTeams.size()))
                .replaceAll(":recentMatchesResults", recentMatchesResultString);
    }

}
