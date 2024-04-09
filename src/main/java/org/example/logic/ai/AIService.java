package org.example.logic.ai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel;
import org.example.constant.*;
import org.example.db.ai.AIDao;
import org.example.logic.basketball.NbaStatisticsService;
import org.example.logic.basketball.NbaTeamsService;
import org.example.model.*;
import org.example.util.PropertiesLoaderUtil;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AIService {
    private static final Properties properties;
    private static final OpenAIModels DEFAULT_MODEL_NAME = OpenAIModels.GPT_3_5_TURBO;
    private static final Integer MAX_AI_RUNS = 4;
    private static final Integer COUNT_LAST_MATCHES = 7;
    private static final Integer MAX_TOKENS_COUNT = 400;

    private static String OPEN_API_KEY;
    private static String GCP_PROJECT_ID;
    private static final String GCP_REGION = "us-central1";
    private final ChatLanguageModel openAI4Turbo, openAI3Turbo, googleGemini;

    private final AIDao aiDao;
    private final NbaStatisticsService nbaStatisticsService;


    static {
        properties = PropertiesLoaderUtil.loadProperties("credentials.properties");

        String openApiKey = properties.getProperty("OPEN_AI_KEY");
        String gcpProjectId = properties.getProperty("GCP_PROJECT_ID");
        if (openApiKey == null || openApiKey.isEmpty() || gcpProjectId == null || gcpProjectId.isEmpty()) {
            throw new RuntimeException("OPEN_AI_KEY and GCP_PROJECT_ID can not be empty in credentials.properties");
        }

        AIService.OPEN_API_KEY = openApiKey;
        AIService.GCP_PROJECT_ID = gcpProjectId;
    }


    public AIService(AIDao aiDao, NbaStatisticsService nbaStatisticsService) {
        this.aiDao = aiDao;
        this.nbaStatisticsService = nbaStatisticsService;

        this.openAI4Turbo = OpenAiChatModel.builder()
                .apiKey(OPEN_API_KEY)
                .maxTokens(AIService.MAX_TOKENS_COUNT)
                .modelName(OpenAiChatModelName.GPT_4_TURBO_PREVIEW)
                .logRequests(true)
                .logResponses(true)
                .build();

        this.openAI3Turbo = OpenAiChatModel.builder()
                .apiKey(OPEN_API_KEY)
                .maxTokens(AIService.MAX_TOKENS_COUNT)
                .modelName(OpenAiChatModelName.GPT_3_5_TURBO)
                .logRequests(true)
                .logResponses(true)
                .build();

        this.googleGemini =  VertexAiGeminiChatModel.builder()
                .project(GCP_PROJECT_ID)
                .location(GCP_REGION)
                .modelName(GoogleAIModels.GEMINI_PRO.getName())
                .build();
    }

    private String sendQuestionOpenAI(String question, OpenAIModels model) {
        if (model == OpenAIModels.GPT_3_5_TURBO) {
            return openAI3Turbo.generate(question);
        } else {
            return openAI4Turbo.generate(question);
        }
    }

    private String sendQuestionGoogle(String question, GoogleAIModels modelName) {
        return googleGemini.generate(question);
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

        AIResponse aiResponseOpenAI = invokeAIAPI(matchName, AIQuestion, countPreviousAIRuns, AIProvider.OPEN_AI);
        AIResponse aiResponseGoogle = invokeAIAPI(matchName, AIQuestion, countPreviousAIRuns, AIProvider.GOOGLE);

        persistAIResponse(aiResponseOpenAI);
        persistAIResponse(aiResponseGoogle);

        System.out.println("Finished AI for " + inputMatch.matchMame());
    }

    @NotNull
    private AIResponse invokeAIAPI(String matchName, String AIQuestion, int countPreviousAIRuns, AIProvider aiProvider) {
        String response = "";
        String modelName = "";

        if (aiProvider == AIProvider.OPEN_AI) {
            OpenAIModels openAIModel = countPreviousAIRuns == 0 ? OpenAIModels.GPT_4_TURBO_PREVIEW : DEFAULT_MODEL_NAME;
            modelName = openAIModel.getName();
            response = sendQuestionOpenAI(AIQuestion, openAIModel);
        } else if (aiProvider == AIProvider.GOOGLE) {
            GoogleAIModels googleAIModels = GoogleAIModels.GEMINI_PRO;
            modelName = googleAIModels.getName();
            response = sendQuestionGoogle(AIQuestion, googleAIModels);
        }

        String value = findPointsValueInResponse(response);

        return new AIResponse(null, matchName, AIQuestion, response, aiProvider.toString(), modelName, value, LocalDateTime.now());
    }

    private String findPointsValueInResponse(String response) {
        String regexPoints = "(?<=points: )[0-9]+";
        String value = "XXX";

        Matcher matcher1 = Pattern.compile(regexPoints, Pattern.CASE_INSENSITIVE).matcher(response);
        if (matcher1.find()) {
            value = matcher1.group();
        } else {
            System.out.println("Could not find points value in " + response);
        }

        return value;
    }

    @NotNull
    private static String formatAiQuestion(EventNbaPoints inputMatch, String team1Alias, String team2Alias,
                                           NbaStatisticTeamHomeAway nbaStatisticTeam1, NbaStatisticTeamHomeAway nbaStatisticTeam2,
                                           List<NbaStatisticTeamsMatch> team1HomeMatches, List<NbaStatisticTeamsMatch> team1AwayMatches,
                                           List<NbaStatisticTeamsMatch> team2HomeMatches, List<NbaStatisticTeamsMatch> team2AwayMatches,
                                           List<NbaStatisticTeamsMatch> matchesBetweenTwoTeams) {
        String recentMatchesResultString = matchesBetweenTwoTeams.stream()
                .map(match -> match.homeTeamAlias() + " at home with " + match.homeTeamTotalPoints() + " and " + match.awayTeamAlias() + " away with " + match.awayTeamTotalPoints() + " points")
                .collect(Collectors.joining(", "));
        Map<String, String> team1OtherStatistics = NbaTeamsService.teamStandingsOtherStatisticsMap.get(team1Alias).getOtherStatistics();
        Map<String, String> team2OtherStatistics = NbaTeamsService.teamStandingsOtherStatisticsMap.get(team2Alias).getOtherStatistics();
        NbaTeam team1 = inputMatch.team1();
        NbaTeam team2 = inputMatch.team2();

        return AIMessages.AI_POINTS_MATCHES_MESSAGE.getMessage()
                .replaceAll(":teamAlias1", team1Alias)
                .replaceAll(":teamAlias2", team2Alias)
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
                .replaceAll(":team1HomeRecord", team1OtherStatistics.get(NbaOtherStatistics.HOME_RECORD.getValue()))
                .replaceAll(":team1Streak", team1OtherStatistics.get(NbaOtherStatistics.CURRENT_STREAK.getValue()))
                .replaceAll(":team2AwayRecord", team2OtherStatistics.get(NbaOtherStatistics.AWAY_RECORD.getValue()))
                .replaceAll(":team2Streak", team2OtherStatistics.get(NbaOtherStatistics.CURRENT_STREAK.getValue()))
                .replaceAll(":team1OverallPointDiff", team1OtherStatistics.get(NbaOtherStatistics.POINT_DIFFERENTIAL.getValue()))
                .replaceAll(":team1AveragePointDiff", team1OtherStatistics.get(NbaOtherStatistics.AVERAGE_POINT_DIFFERENTIAL.getValue()))
                .replaceAll(":team2OverallPointDiff", team2OtherStatistics.get(NbaOtherStatistics.POINT_DIFFERENTIAL.getValue()))
                .replaceAll(":team2AveragePointDiff", team2OtherStatistics.get(NbaOtherStatistics.AVERAGE_POINT_DIFFERENTIAL.getValue()))
                .replaceAll(":team1OverallStanding", team1.getStandingOverall().toString())
                .replaceAll(":team1ConferenceStanding", team1.getStandingConference().toString())
                .replaceAll(":team2OverallStanding", team2.getStandingOverall().toString())
                .replaceAll(":team2ConferenceStanding", team2.getStandingConference().toString())

                .replaceAll(":team1TwoPointsRatio", team1OtherStatistics.get(NbaOtherStatistics.TWO_POINT_RATIO.getValue()))
                .replaceAll(":team1ThreePointsRatio", team1OtherStatistics.get(NbaOtherStatistics.THREE_POINT_RATIO.getValue()))
                .replaceAll(":team1FreePointsRatio", team1OtherStatistics.get(NbaOtherStatistics.FREE_THROWS_RATIO.getValue()))
                .replaceAll(":team1OffensiveRebounds", team1OtherStatistics.get(NbaOtherStatistics.OFFENSIVE_REBOUNDS_PER_GAME.getValue()))
                .replaceAll(":team1DefensiveRebounds", team1OtherStatistics.get(NbaOtherStatistics.DEFENSIVE_REBOUNDS_PER_GAME.getValue()))
                .replaceAll(":team1Steals", team1OtherStatistics.get(NbaOtherStatistics.STEALS_PER_GAME.getValue()))
                .replaceAll(":team1Blocks", team1OtherStatistics.get(NbaOtherStatistics.BLOCKS_PER_GAME.getValue()))

                .replaceAll(":team2TwoPointsRatio", team2OtherStatistics.get(NbaOtherStatistics.TWO_POINT_RATIO.getValue()))
                .replaceAll(":team2ThreePointsRatio", team2OtherStatistics.get(NbaOtherStatistics.THREE_POINT_RATIO.getValue()))
                .replaceAll(":team2FreePointsRatio", team2OtherStatistics.get(NbaOtherStatistics.FREE_THROWS_RATIO.getValue()))
                .replaceAll(":team2OffensiveRebounds", team2OtherStatistics.get(NbaOtherStatistics.OFFENSIVE_REBOUNDS_PER_GAME.getValue()))
                .replaceAll(":team2DefensiveRebounds", team2OtherStatistics.get(NbaOtherStatistics.DEFENSIVE_REBOUNDS_PER_GAME.getValue()))
                .replaceAll(":team2Steals", team2OtherStatistics.get(NbaOtherStatistics.STEALS_PER_GAME.getValue()))
                .replaceAll(":team2Blocks", team2OtherStatistics.get(NbaOtherStatistics.BLOCKS_PER_GAME.getValue()))

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
