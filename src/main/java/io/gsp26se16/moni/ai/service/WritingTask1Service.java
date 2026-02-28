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
    private final RuleEngine ruleEngine;
    private final Helper helper;


    // =====================================================
    // MAIN ENTRY
    // =====================================================

    public Map<String, Object> score(WritingRequest request) throws JsonProcessingException {

        ChatClient chatClient =
                chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor()).build();

        boolean isTask1 = request.getChartImage() != null && !request.getChartImage().isEmpty();
        Map<String, Object> chartData = null;

        if (isTask1 && request.getQuestionGroupId() != null) {
            chartData = getOrCacheVisionAnalysis(
                    request.getQuestionGroupId(),
                    request.getChartImage());
        }

        // ================= PHASE 1 =================
        Map<String, Object> parsedEssay =
                phase1Parse(chatClient, request.getAnswer());

        // ================= PHASE 2–5 =================
        Map<String, Object> ta = phase2TaskAchievement(chatClient, request.getAnswer(), parsedEssay, chartData);


        Map<String, Object> cc =
                phase3Coherence(chatClient,request.getAnswer(),parsedEssay);

        Map<String, Object> lr =
                phase4Lexical(chatClient, request.getAnswer());

        Map<String, Object> gra =
                phase5Grammar(chatClient, request.getAnswer());

        // ================= RULE ENGINE =================
        Map<String, Object> finalResult =
                phase6Calculate(ta, cc, lr, gra);

        // ================= FEEDBACK =================
        Map<String, Object> feedback =
                phase7Feedback(
                        chatClient,
                        request.getQuestion(),
                        request.getAnswer(),
                        finalResult
                );

        Map<String, Object> response = new HashMap<>();
        response.put("assessment", finalResult);
        response.put("feedback", feedback);
        response.put("parsed_structure", parsedEssay);
        return response;
    }

    // =====================================================
    // PHASES
    // =====================================================

    private Map<String, Object> phase1Parse(ChatClient chatClient, String essay) {
        String prompt = promptLoader.loadPrompt(
                "phase1_parse.txt",
                Map.of("essay", essay));
        return callEvaluation(chatClient, prompt);
    }

    private Map<String, Object> phase2TaskAchievement(
            ChatClient chatClient,
            String essay,                           // rename: question → essay
            Map<String, Object> parsed,
            Map<String, Object> chartData
    ) throws JsonProcessingException {

        String prompt = promptLoader.loadPrompt(
                "phase2_ta.txt",
                Map.of(
                        "essay", essay,
                        "phase1_json", objectMapper.writeValueAsString(parsed),
                        "chart_entities", chartData != null
                                ? objectMapper.writeValueAsString(chartData)
                                : "[]"
                )
        );
        return callEvaluation(chatClient, prompt);
    }

    private Map<String, Object> phase3Coherence(ChatClient chatClient,
                                                String essay,              // add essay param
                                                Map<String, Object> parsed)
            throws JsonProcessingException {
        String prompt = promptLoader.loadPrompt(
                "phase3_cc.txt",
                Map.of(
                        "essay", essay,
                        "phase1_json", objectMapper.writeValueAsString(parsed)
                ));
        return callEvaluation(chatClient, prompt);
    }

    private Map<String, Object> phase4Lexical(ChatClient chatClient,
                                              String essay) {

        String prompt = promptLoader.loadPrompt(
                "phase4_lr.txt",
                Map.of("essay", essay));

        return callEvaluation(chatClient, prompt);
    }

    private Map<String, Object> phase5Grammar(ChatClient chatClient,
                                              String essay) {

        String prompt = promptLoader.loadPrompt(
                "phase5_gra.txt",
                Map.of("essay", essay));

        return callEvaluation(chatClient, prompt);
    }

    // =====================================================
    // FINAL CALCULATION
    // =====================================================

    private Map<String, Object> phase6Calculate(
            Map<String, Object> ta,
            Map<String, Object> cc,
            Map<String, Object> lr,
            Map<String, Object> gra
    ) {

        // ================= RAW BANDS =================
        Map<String, Double> rawBands = Map.of(
                "TA", getBand(ta),
                "CC", getBand(cc),
                "LR", getBand(lr),
                "GRA", getBand(gra)
        );

        // ================= COLLECT VIOLATIONS =================
        Map<String, RuleEngine.Violation> violations =
                helper.collectViolations(ta, cc, lr, gra);

        // ================= APPLY RULE ENGINE =================
        RuleEngine.RuleResult ruleResult =
                ruleEngine.applyAllRules(rawBands, violations);

        double finalBand =
                ruleEngine.calculateFinalBand(
                        ruleResult.adjustedBands(),
                        ruleResult.overallCap()
                );

        Map<String, Object> result = new HashMap<>();
        result.put("final_band", finalBand);
        result.put("overall_cap", ruleResult.overallCap());        // null-safe
        result.put("applied_hard_rules", ruleResult.appliedHardRules());
        result.put("criteria", Map.of(
                "TA", helper.mergeCriterion(ta, ruleResult.adjustedBands()),
                "CC", helper.mergeCriterion(cc, ruleResult.adjustedBands()),
                "LR", helper.mergeCriterion(lr, ruleResult.adjustedBands()),
                "GRA", helper.mergeCriterion(gra, ruleResult.adjustedBands())
        ));
        return result;

    }
    // =====================================================
    // FEEDBACK
    // =====================================================

    private Map<String, Object> phase7Feedback(
            ChatClient chatClient,
            String question,
            String essay,
            Map<String, Object> finalResult)
            throws JsonProcessingException {

        String prompt = promptLoader.loadPrompt(
                "phase7_feedback.txt",
                Map.of(
                        "question", question,
                        "essay", essay,
                        "all_phase_results",
                        objectMapper.writeValueAsString(finalResult)
                )
        );

        return callFeedback(chatClient, prompt);
    }

    // =====================================================
    // LLM CALLS
    // =====================================================

    private Map<String, Object> callEvaluation(
            ChatClient chatClient,
            String systemPrompt) {

        String response = chatClient.prompt()
                .system(systemPrompt)
                .user("Return ONLY raw JSON.")
                .call()
                .content();

        return helper.parseJson(response);
    }

    private Map<String, Object> callFeedback(
            ChatClient chatClient,
            String systemPrompt) {

        String response = chatClient.prompt()
                .system(systemPrompt)
                .user("Explain strictly based on evaluation results. Return ONLY raw JSON.")
                .call()
                .content();

        return helper.parseJson(response);
    }

    // =====================================================
    // UTILITIES
    // =====================================================

    private double getBand(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            // Handle error case first
            if (map.containsKey("error")) {
                log.warn("Criterion contains error, defaulting to band 5.0: {}", map.get("error"));
                return 5.0;
            }

            Object val = map.get("band");
            if (val instanceof Number n) {
                return n.doubleValue();
            }

            // Try to parse as string (in case LLM returns "7" instead of 7)
            if (val instanceof String) {
                try {
                    return Double.parseDouble((String) val);
                } catch (NumberFormatException e) {
                    log.warn("Could not parse band value as number: {}", val);
                }
            }
        }

        log.warn("Invalid band value, defaulting to 5.0: {}", obj);
        return 5.0;
    }
    // =====================================================
    // VISION CACHE (unchanged)
    // =====================================================

    private Map<String, Object> getOrCacheVisionAnalysis(
            Integer questionGroupId,
            MultipartFile chartImage) {

        QuestionGroup questionGroup =
                questionGroupRepository
                        .findById(questionGroupId)
                        .orElseThrow(() ->
                                new RuntimeException("QuestionGroup not found: " + questionGroupId));

        if (questionGroup.getVisonAnalysisResult() != null &&
                !questionGroup.getVisonAnalysisResult().isEmpty()) {
            log.info("Using cached vision analysis for QuestionGroup: {}", questionGroupId);
            return questionGroup.getVisonAnalysisResult();
        }

        try {
            if (chartImage == null || chartImage.isEmpty() || chartImage.getSize() <= 0) {
                log.warn("Chart image is empty or unreadable for QuestionGroup: {}", questionGroupId);
                return Map.of();
            }

            byte[] imageBytes = chartImage.getBytes();

            // ✅ Double-check bytes actually loaded
            if (imageBytes == null || imageBytes.length == 0) {
                log.warn("Chart image bytes are empty for QuestionGroup: {}", questionGroupId);
                return Map.of();
            }

            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            Map<String, Object> analysis = visionClient.analyzeChart(base64Image);

            questionGroup.setVisonAnalysisResult(analysis);
            questionGroupRepository.save(questionGroup);

            return analysis;

        } catch (Exception e) {
            throw new RuntimeException("Failed to process chart image", e);
        }
    }
}