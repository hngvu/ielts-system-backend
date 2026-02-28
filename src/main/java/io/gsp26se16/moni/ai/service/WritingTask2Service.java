package io.gsp26se16.moni.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.gsp26se16.moni.ai.model.request.WritingRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WritingTask2Service {

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final PromptLoader promptLoader;
    private final Helper helper;
    // RuleEngine is used exclusively through Helper — no direct dependency here.

    // =========================================================================
    // PUBLIC ENTRY POINT
    // =========================================================================

    public Map<String, Object> score(WritingRequest request) throws JsonProcessingException {

        ChatClient chatClient = chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();

        String question = request.getQuestion();
        String essay    = request.getAnswer();

        // ── Phase 1: Structural parse + task-type detection ───────────────────
        Map<String, Object> parsedEssay = phase1Parse(chatClient, question, essay);

        // ── Phase 2–5: Criterion scoring ──────────────────────────────────────
        Map<String, Object> tr  = phase2TaskResponse(chatClient, question, essay, parsedEssay);
        Map<String, Object> cc  = phase3Coherence(chatClient, essay, parsedEssay);
        Map<String, Object> lr  = phase4Lexical(chatClient, essay);
        Map<String, Object> gra = phase5Grammar(chatClient, essay);

        // ── Phase 6: Rule Engine + band calculation ───────────────────────────
        Map<String, Object> finalResult = helper.calculateBands(tr, cc, lr, gra);

        // ── Phase 7: Feedback ─────────────────────────────────────────────────
        Map<String, Object> feedback = phase7Feedback(chatClient, essay, finalResult);

        Map<String, Object> response = new HashMap<>();
        response.put("assessment",       finalResult);
        response.put("feedback",         feedback);
        response.put("parsed_structure", parsedEssay);
        return response;
    }

    // =========================================================================
    // PHASES
    // =========================================================================

    /**
     * Phase 1 — Structural parse.
     * Prompt vars: {question}, {essay}
     */
    private Map<String, Object> phase1Parse(
            ChatClient chatClient, String question, String essay) {

        String prompt = promptLoader.loadPrompt(
                "phase1_parse_task2.txt",
                Map.of("question", question, "essay", essay));
        return callEvaluation(chatClient, prompt);
    }

    /**
     * Phase 2 — Task Response (strict band-gating model).
     * Prompt vars: {question}, {essay}, {phase2_json}
     * Uses phase2_ta.txt (STRICT BAND GATING VERSION).
     * Violations: no_clear_position, partial_task_addressing,
     * insufficient_development, unclear_position_progression,
     * irrelevant_content, weak_argument_depth.
     */
    private Map<String, Object> phase2TaskResponse(
            ChatClient chatClient,
            String question,
            String essay,
            Map<String, Object> parsedEssay) throws JsonProcessingException {

        String prompt = promptLoader.loadPrompt(
                "phase2_tr.txt",
                Map.of(
                        "question",    question,
                        "essay",       essay,
                        "phase2_json", objectMapper.writeValueAsString(parsedEssay)
                ));
        return callEvaluation(chatClient, prompt);
    }

    /**
     * Phase 3 — Coherence and Cohesion.
     * Prompt vars: {essay}, {phase1_json}
     */
    private Map<String, Object> phase3Coherence(
            ChatClient chatClient,
            String essay,
            Map<String, Object> parsedEssay) throws JsonProcessingException {

        String prompt = promptLoader.loadPrompt(
                "phase3_cc.txt",
                Map.of(
                        "essay",       essay,
                        "phase1_json", objectMapper.writeValueAsString(parsedEssay)
                ));
        return callEvaluation(chatClient, prompt);
    }

    /**
     * Phase 4 — Lexical Resource.
     * Prompt vars: {essay}
     *
     * Note: the LR rubric is now fully inlined in phase4_lr.txt.
     * No {LR_RUBRIC} placeholder exists in the current prompt version.
     */
    private Map<String, Object> phase4Lexical(ChatClient chatClient, String essay) {
        String prompt = promptLoader.loadPrompt(
                "phase4_lr.txt",
                Map.of("essay", essay));
        return callEvaluation(chatClient, prompt);
    }

    /**
     * Phase 5 — Grammatical Range and Accuracy.
     * Prompt vars: {essay}
     */
    private Map<String, Object> phase5Grammar(ChatClient chatClient, String essay) {
        String prompt = promptLoader.loadPrompt(
                "phase5_gra.txt",
                Map.of("essay", essay));
        return callEvaluation(chatClient, prompt);
    }

    /**
     * Phase 7 — Post-marking feedback.
     * Prompt vars: {all_phase_results}, {essay}
     */
    private Map<String, Object> phase7Feedback(
            ChatClient chatClient,
            String essay,
            Map<String, Object> finalResult) throws JsonProcessingException {

        String prompt = promptLoader.loadPrompt(
                "phase7_feedback_task2.txt",
                Map.of(
                        "all_phase_results", objectMapper.writeValueAsString(finalResult),
                        "essay",             essay
                ));
        return callFeedback(chatClient, prompt);
    }

    // =========================================================================
    // LLM CALL HELPERS
    // =========================================================================

    private Map<String, Object> callEvaluation(ChatClient chatClient, String systemPrompt) {
        String response = chatClient.prompt()
                .system(systemPrompt)
                .user("Return ONLY raw JSON.")
                .call()
                .content();
        return helper.parseJson(response);
    }

    private Map<String, Object> callFeedback(ChatClient chatClient, String systemPrompt) {
        String response = chatClient.prompt()
                .system(systemPrompt)
                .user("Explain strictly based on evaluation results. Return ONLY raw JSON.")
                .call()
                .content();
        return helper.parseJson(response);
    }
}