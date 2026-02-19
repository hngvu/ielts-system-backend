package io.gsp26se16.moni.ai.service;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.gsp26se16.moni.ai.model.request.WritingRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WritingTask2Service {

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final PromptLoader promptLoader;
    private final RuleEngine ruleEngine;

    public Map<String, Object> score(WritingRequest request) {
        ChatClient chatClient =
                chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor()).build();

        // 1. Parse Essay with Task Type Detection
        Map<String, Object> parsedEssay = phase1ParseTask2(chatClient, request.getQuestion(), request.getAnswer());
        String taskType = (String) parsedEssay.get("task_type");

        // 2. Task Response Evaluation
        Map<String, Object> trOutput = phase2TaskResponse(chatClient, request.getQuestion(), parsedEssay, taskType);
        double trBand = ((Number) trOutput.get("band")).doubleValue();
        Map<String, RuleEngine.Violation> violations = extractViolations(trOutput);

        // 3-5. Parallel Scoring (CC, LR, GRA)
        Map<String, Object> cc = phase3Coherence(chatClient, parsedEssay);
        Map<String, Object> lr = phase4Lexical(chatClient, parsedEssay);
        Map<String, Object> gra = phase5Grammar(chatClient, parsedEssay);

        // 6. Raw Bands
        Map<String, Double> rawBands = Map.of(
                "TR", trBand,
                "CC", ((Number) cc.get("band")).doubleValue(),
                "LR", ((Number) lr.get("band")).doubleValue(),
                "GRA", ((Number) gra.get("band")).doubleValue());

        // 7. Apply Rule Engine
        RuleEngine.RuleResult ruleResult = ruleEngine.applyAllRules(rawBands, violations);
        Map<String, Double> cappedBands = ruleResult.cappedBands();

        // 8. Apply GRA Ceiling
        @SuppressWarnings("unchecked")
        List<String> graViolations = (List<String>) gra.getOrDefault("violations", List.of());
        double graCeiledBand = ruleEngine.applyGraCeiling(cappedBands.get("GRA"), graViolations);

        // 9. Calculate Final Band
        double avgBand = (cappedBands.get("TR") + cappedBands.get("CC") + cappedBands.get("LR") + graCeiledBand) / 4.0;

        // Apply TR Overall Ceiling
        double cappedAvg = avgBand;
        String note = "";
        if (cappedBands.get("TR") < 6.0) {
            cappedAvg = Math.min(avgBand, 6.5);
            note = "TR < 6.0 ceiling";
        } else if (cappedBands.get("TR") <= 6.5) {
            cappedAvg = Math.min(avgBand, 7.0);
            note = "TR ceiling at 7.0";
        }

        if (ruleResult.overallCap() != null) {
            cappedAvg = Math.min(cappedAvg, ruleResult.overallCap());
            note += " + HARD overall cap";
        }

        double finalBand = ruleEngine.ieltsRounding(cappedAvg);

        // 10. Generate Feedback
        Map<String, Object> feedback =
                phase7FeedbackTask2(chatClient, request.getQuestion(), request.getAnswer(), cappedBands, finalBand);

        return Map.of(
                "task",
                "IELTS Writing Task 2",
                "overall",
                Map.of("band", finalBand, "note", note, "hard_caps", ruleResult.appliedHard()),
                "bands",
                Map.of(
                        "TR", Map.of("band", cappedBands.get("TR"), "base_band", trBand),
                        "CC", Map.of("band", cappedBands.get("CC")),
                        "LR", Map.of("band", cappedBands.get("LR")),
                        "GRA", Map.of("band", graCeiledBand)),
                "feedback",
                feedback);
    }

    private Map<String, Object> phase1ParseTask2(ChatClient chatClient, String question, String essay) {
        String prompt = promptLoader.loadPrompt(
                "phase1_parse_task2.txt",
                Map.of(
                        "question", question,
                        "essay", essay));
        return callLlmAndParseJson(chatClient, prompt, "");
    }

    private Map<String, Object> phase2TaskResponse(
            ChatClient chatClient, String question, Map<String, Object> parsed, String taskType) {
        String prompt = promptLoader.loadPrompt(
                "phase2_tr.txt",
                Map.of(
                        "question", question,
                        "task_type", taskType,
                        "essay_text", parsed.get("sentences").toString()));
        return callLlmAndParseJson(chatClient, prompt, "");
    }

    private Map<String, Object> phase3Coherence(ChatClient chatClient, Map<String, Object> parsed) {
        String prompt = promptLoader.loadPrompt("phase3_cc.txt");
        return callLlmAndParseJson(chatClient, prompt, parsed.toString());
    }

    private Map<String, Object> phase4Lexical(ChatClient chatClient, Map<String, Object> parsed) {
        String prompt = promptLoader.loadPrompt("phase4_lr.txt");
        return callLlmAndParseJson(chatClient, prompt, parsed.toString());
    }

    private Map<String, Object> phase5Grammar(ChatClient chatClient, Map<String, Object> parsed) {
        String prompt = promptLoader.loadPrompt("phase5_gra.txt");
        return callLlmAndParseJson(chatClient, prompt, parsed.toString());
    }

    private Map<String, Object> phase7FeedbackTask2(
            ChatClient chatClient, String question, String essay, Map<String, Double> bands, double finalBand) {
        String prompt = promptLoader.loadPrompt(
                "phase7_feedback_task2.txt",
                Map.of(
                        "question", question,
                        "essay", essay,
                        "bands", bands.toString()));
        Map<String, Object> result = callLlmAndParseJson(chatClient, prompt, "");
        result.put("type", "tutor_feedback");
        return result;
    }

    private Map<String, Object> callLlmAndParseJson(ChatClient chatClient, String system, String user) {
        String response = chatClient
                .prompt()
                .system(system)
                .user(user.isEmpty() ? system : user)
                .call()
                .content();

        try {
            if (response.contains("{")) {
                response = response.substring(response.indexOf("{"), response.lastIndexOf("}") + 1);
            }
            return objectMapper.readValue(response, Map.class);
        } catch (Exception e) {
            return Map.of("error", "Failed to parse JSON", "raw", response, "band", 5.0);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, RuleEngine.Violation> extractViolations(Map<String, Object> trOutput) {
        Map<String, Object> violationsMap = (Map<String, Object>) trOutput.getOrDefault("violations", Map.of());
        var result = new java.util.HashMap<String, RuleEngine.Violation>();

        for (Map.Entry<String, Object> entry : violationsMap.entrySet()) {
            Map<String, Object> v = (Map<String, Object>) entry.getValue();
            result.put(
                    entry.getKey(),
                    new RuleEngine.Violation(
                            (Boolean) v.getOrDefault("active", false),
                            (String) v.getOrDefault("location", ""),
                            (String) v.getOrDefault("evidence", ""),
                            (String) v.getOrDefault("reason", "")));
        }

        return result;
    }
}
