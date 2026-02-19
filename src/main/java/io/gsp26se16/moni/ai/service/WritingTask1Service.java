package io.gsp26se16.moni.ai.service;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.gsp26se16.moni.ai.model.request.WritingRequest;
import io.gsp26se16.moni.content.entity.QuestionGroup;
import io.gsp26se16.moni.content.repository.QuestionGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WritingTask1Service {

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final GeminiVisionClient visionClient;
    private final QuestionGroupRepository questionGroupRepository;
    private final PromptLoader promptLoader;

    public Map<String, Object> score(WritingRequest request) throws JsonProcessingException {
        ChatClient chatClient =
                chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor()).build();


        // 1. Chart Understanding (if Task 1) - WITH CACHING
//        Map<String, Object> chartData = getOrCacheVisionAnalysis(request.getQuestionGroupId(), request.getChartImage());
        boolean isTask1 = (request.getChartImage() != null && !request.getChartImage().isEmpty());
        Map<String, Object> chartData = null;

        if (isTask1 && request.getQuestionGroupId() != null) {
            chartData = getOrCacheVisionAnalysis(
                    request.getQuestionGroupId(),
                    request.getChartImage());
        }

        Map<String, Object> parsedEssay = phase1Parse(chatClient, request.getAnswer());

        // Phase 2–5: Evaluation
        Map<String, Object> bandTA = phase2TaskAchievement(chatClient, request.getQuestion(), parsedEssay, chartData);
        Map<String, Object> bandCC = phase3Coherence(chatClient, parsedEssay);
        Map<String, Object> bandLR = phase4Lexical(chatClient, parsedEssay);
        Map<String, Object> bandGRA = phase5Grammar(chatClient, parsedEssay);

        // Phase 6: Final Band
        Map<String, Object> finalResult = phase6Calculate(bandTA, bandCC, bandLR, bandGRA);

        // Phase 7: Feedback (STRICT explanation only)
        Map<String, Object> feedback =
                phase7Feedback(chatClient, request.getQuestion(), request.getAnswer(), finalResult);

        return Map.of(
                "assessment", finalResult,
                "feedback", feedback,
                "parsed_structure", parsedEssay);
    }

    // ==============================
    // VISION CACHE
    // ==============================

    private Map<String, Object> getOrCacheVisionAnalysis(Integer questionGroupId, MultipartFile chartImage) {

        QuestionGroup questionGroup = questionGroupRepository
                .findById(questionGroupId)
                .orElseThrow(() -> new RuntimeException("QuestionGroup not found: " + questionGroupId));

        if (questionGroup.getVisonAnalysisResult() != null
                && !questionGroup.getVisonAnalysisResult().isEmpty()) {
            log.info("Using cached vision analysis for QuestionGroup: {}", questionGroupId);
            return questionGroup.getVisonAnalysisResult();
        }

        try {
            byte[] imageBytes = chartImage.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            log.info("Calling Gemini Vision for QuestionGroup: {}", questionGroupId);
            Map<String, Object> analysis = visionClient.analyzeChart(base64Image);

            questionGroup.setVisonAnalysisResult(analysis);
            questionGroupRepository.save(questionGroup);

            return analysis;

        } catch (Exception e) {
            log.error("Failed to process chart image", e);
            throw new RuntimeException("Failed to process chart image", e);
        }
    }

    // ==============================
    // PHASES
    // ==============================

    private Map<String, Object> phase1Parse(ChatClient chatClient, String essay) {
        String systemPrompt = promptLoader.loadPrompt("phase1_parse.txt", Map.of("essay", essay));
        return callEvaluation(chatClient, systemPrompt);
    }

    private Map<String, Object> phase2TaskAchievement(
            ChatClient chatClient,
            String question,
            Map<String, Object> parsed,
            Map<String, Object> chartData) throws JsonProcessingException {

        String systemPrompt = promptLoader.loadPrompt(
                "phase2_ta.txt",
                Map.of(
                        "question", question,
                        "parsed", objectMapper.writeValueAsString(parsed),
                        "chart_data", chartData != null
                                ? objectMapper.writeValueAsString(chartData)
                                : "{}"
                ));

        return callEvaluation(chatClient, systemPrompt);
    }

    private Map<String, Object> phase3Coherence(ChatClient chatClient, Map<String, Object> parsed)
            throws JsonProcessingException {

        String systemPrompt = promptLoader.loadPrompt(
                "phase3_cc.txt",
                Map.of("parsed", objectMapper.writeValueAsString(parsed)));

        return callEvaluation(chatClient, systemPrompt);
    }

    private Map<String, Object> phase4Lexical(ChatClient chatClient, Map<String, Object> parsed)
            throws JsonProcessingException {

        String systemPrompt = promptLoader.loadPrompt(
                "phase4_lr.txt",
                Map.of("parsed", objectMapper.writeValueAsString(parsed)));

        return callEvaluation(chatClient, systemPrompt);
    }

    private Map<String, Object> phase5Grammar(ChatClient chatClient, Map<String, Object> parsed)
            throws JsonProcessingException {

        String systemPrompt = promptLoader.loadPrompt(
                "phase5_gra.txt",
                Map.of("parsed", objectMapper.writeValueAsString(parsed)));

        return callEvaluation(chatClient, systemPrompt);
    }

    private Map<String, Object> phase6Calculate(
            Map<String, Object> ta,
            Map<String, Object> cc,
            Map<String, Object> lr,
            Map<String, Object> gra) {

        double avg = (getBand(ta) + getBand(cc) + getBand(lr) + getBand(gra)) / 4.0;
        double finalBand = Math.round(avg * 2) / 2.0;

        return Map.of(
                "band", finalBand,
                "details", Map.of(
                        "TA", ta,
                        "CC", cc,
                        "LR", lr,
                        "GRA", gra));
    }

    private Map<String, Object> phase7Feedback(
            ChatClient chatClient,
            String question,
            String essay,
            Map<String, Object> finalResult) throws JsonProcessingException {

        Map<String, Object> activeViolations = extractActiveViolations(finalResult);
        String bandValue = String.valueOf(finalResult.get("band"));
        String systemPrompt = promptLoader.loadPrompt(
                "phase7_feedback.txt",
                Map.of(
                        "question", question,
                        "essay", essay,
                        "band", bandValue,
                        "violations", objectMapper.writeValueAsString(activeViolations)
                ));

        return callFeedback(chatClient, systemPrompt);
    }

    // ==============================
    // LLM CALL TYPES
    // ==============================

    private Map<String, Object> callEvaluation(ChatClient chatClient, String systemPrompt) {
        String response = chatClient.prompt()
                .system(systemPrompt)
                .user("Evaluate strictly according to IELTS rubric. Return ONLY raw JSON without markdown formatting or code fences.")
                .call()
                .content();

        return parseJson(response);
    }

    private Map<String, Object> callFeedback(ChatClient chatClient, String systemPrompt) {
        String response = chatClient.prompt()
                .system(systemPrompt)
                .user("Explain strictly based on provided evaluation results. Do NOT re-evaluate. Return ONLY raw JSON without markdown formatting or code fences.")
                .call()
                .content();

        return parseJson(response);
    }

    // ==============================
    // UTILITIES
    // ==============================

    private Map<String, Object> extractActiveViolations(Map<String, Object> finalResult) {

        Map<String, Object> details = (Map<String, Object>) finalResult.get("details");
        Map<String, Object> active = new HashMap<>();

        for (String criterion : details.keySet()) {

            Map<String, Object> criterionData =
                    (Map<String, Object>) details.get(criterion);

            Map<String, Object> violations =
                    (Map<String, Object>) criterionData.get("violations");

            if (violations == null) continue;

            Map<String, Object> activeViolations = new HashMap<>();

            for (String key : violations.keySet()) {

                Map<String, Object> v =
                        (Map<String, Object>) violations.get(key);

                if (Boolean.TRUE.equals(v.get("active"))) {
                    activeViolations.put(key, v);
                }
            }

            if (!activeViolations.isEmpty()) {
                active.put(criterion, activeViolations);
            }
        }

        return active;
    }

    private Map<String, Object> parseJson(String response) {
        try {
            String cleaned = cleanJsonResponse(response);
            return objectMapper.readValue(cleaned, Map.class);
        } catch (Exception e) {
            log.error("Invalid JSON from LLM: {}", response);
            return Map.of(
                    "error", "Invalid JSON",
                    "raw", response);
        }
    }

    private double getBand(Map<String, Object> map) {
        Object val = map.get("band");
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return 5.0;
    }

    private String cleanJsonResponse(String response) {
        if (response == null) {
            return "{}";
        }

        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7); // Remove ```json
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3); // Remove ```
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }
}