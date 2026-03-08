package io.gsp26se16.moni.ai.writing.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.gsp26se16.moni.ai.writing.entity.AiEvaluation;
import io.gsp26se16.moni.ai.writing.entity.WritingSubmission;
import io.gsp26se16.moni.ai.writing.repository.AiEvaluationRepository;
import io.gsp26se16.moni.ai.writing.repository.WritingSubmissionRepository;
import io.gsp26se16.moni.ai.writing.request.WritingRequest;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.common.enumeration.EvaluationStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.enumeration.WritingTaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WritingTask2Service {

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final PromptLoader promptLoader;
    private final Helper helper;
    private final WritingSubmissionRepository writingSubmissionRepository;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final UsersRepository usersRepository;

    // =========================================================================
    // PUBLIC ENTRY POINT
    // =========================================================================

    public Map<String, Object> score(WritingRequest request) throws JsonProcessingException {

        // ── Tạo WritingSubmission (PROCESSING) ────────────────────────────────
        WritingSubmission submission = createSubmission(request, WritingTaskType.TASK_2);

        try {
            ChatClient chatClient = chatClientBuilder
                    .defaultAdvisors(new SimpleLoggerAdvisor()).build();

            String question = request.getQuestion();
            String essay    = request.getAnswer();

            // ── Phase 1: Structural parse ─────────────────────────────────────
            Map<String, Object> parsedEssay = phase1Parse(chatClient, question, essay);

            // ── Phase 2–5: Criterion scoring ──────────────────────────────────
            Map<String, Object> tr  = phase2TaskResponse(chatClient, question, essay, parsedEssay);
            Map<String, Object> cc  = phase3Coherence(chatClient, essay, parsedEssay);
            Map<String, Object> lr  = phase4Lexical(chatClient, essay);
            Map<String, Object> gra = phase5Grammar(chatClient, essay);

            // ── Phase 6: Rule Engine + band calculation ───────────────────────
            Map<String, Object> finalResult = helper.calculateBands(tr, cc, lr, gra);

            // ── Phase 7: Feedback ─────────────────────────────────────────────
            Map<String, Object> feedback = phase7Feedback(chatClient, essay, finalResult);

            // ── Lưu AiEvaluation + cập nhật submission COMPLETED ─────────────
            double finalBand = (double) finalResult.get("final_band");
            persistEvaluation(submission, finalBand, finalResult, feedback);

            Map<String, Object> response = new HashMap<>();
            response.put("assessment", finalResult);
            response.put("feedback", feedback);
            response.put("parsed_structure", parsedEssay);
            return response;

        } catch (Exception e) {
            log.error("WritingTask2 scoring thất bại cho submission {}: {}", submission.getId(), e.getMessage(), e);
            submission.setEvaluationStatus(EvaluationStatus.FAILED);
            writingSubmissionRepository.save(submission);
            throw e;
        }
    }

    // =========================================================================
    // PHASES
    // =========================================================================

    private Map<String, Object> phase1Parse(ChatClient chatClient, String question, String essay) {
        String prompt = promptLoader.loadPrompt("phase1_parse_task2.txt", Map.of("question", question, "essay", essay));
        return callEvaluation(chatClient, prompt);
    }

    private Map<String, Object> phase2TaskResponse(
            ChatClient chatClient, String question, String essay, Map<String, Object> parsedEssay)
            throws JsonProcessingException {
        String prompt = promptLoader.loadPrompt(
                "phase2_tr.txt",
                Map.of(
                        "question",    question,
                        "essay",       essay,
                        "phase2_json", objectMapper.writeValueAsString(parsedEssay)));
        return callEvaluation(chatClient, prompt);
    }

    private Map<String, Object> phase3Coherence(ChatClient chatClient, String essay, Map<String, Object> parsedEssay)
            throws JsonProcessingException {
        String prompt = promptLoader.loadPrompt(
                "phase3_cc.txt", Map.of("essay", essay, "phase1_json", objectMapper.writeValueAsString(parsedEssay)));
        return callEvaluation(chatClient, prompt);
    }

    private Map<String, Object> phase4Lexical(ChatClient chatClient, String essay) {
        String prompt = promptLoader.loadPrompt("phase4_lr.txt", Map.of("essay", essay));
        return callEvaluation(chatClient, prompt);
    }

    private Map<String, Object> phase5Grammar(ChatClient chatClient, String essay) {
        String prompt = promptLoader.loadPrompt("phase5_gra.txt", Map.of("essay", essay));
        return callEvaluation(chatClient, prompt);
    }

    private Map<String, Object> phase7Feedback(ChatClient chatClient, String essay, Map<String, Object> finalResult)
            throws JsonProcessingException {
        String prompt = promptLoader.loadPrompt(
                "phase7_feedback_task2.txt",
                Map.of("all_phase_results", objectMapper.writeValueAsString(finalResult), "essay", essay));
        return callFeedback(chatClient, prompt);
    }

    // =========================================================================
    // DB PERSISTENCE
    // =========================================================================

    /**
     * Tạo WritingSubmission với trạng thái PROCESSING.
     * userId lấy từ JWT trong SecurityContext.
     */
    private WritingSubmission createSubmission(WritingRequest request, WritingTaskType taskType) {
        Users user = resolveCurrentUser();
        int wordCount = countWords(request.getAnswer());

        WritingSubmission submission = WritingSubmission.builder()
                .user(user)
                .taskType(taskType)
                .essayContent(request.getAnswer())
                .wordCount(wordCount)
                .evaluationStatus(EvaluationStatus.PROCESSING)
                .build();

        WritingSubmission saved = writingSubmissionRepository.save(submission);
        log.info("WritingSubmission tạo: id={}, taskType={}, wordCount={}", saved.getId(), taskType, wordCount);
        return saved;
    }

    /**
     * Lưu AiEvaluation và cập nhật trạng thái submission → COMPLETED.
     */
    private void persistEvaluation(
            WritingSubmission submission,
            double finalBand,
            Map<String, Object> analysisResult,
            Map<String, Object> feedbackResponse) {

        AiEvaluation evaluation = AiEvaluation.builder()
                .submissionId(submission.getId())
                .skill(Skill.WRITING)
                .overallScore(finalBand)
                .analysisResult(analysisResult)
                .feedbackResponse(feedbackResponse)
                .createdAt(LocalDateTime.now())
                .build();

        aiEvaluationRepository.save(evaluation);

        submission.setEvaluationStatus(EvaluationStatus.COMPLETED);
        writingSubmissionRepository.save(submission);

        log.info("AiEvaluation lưu thành công: submissionId={}, band={}", submission.getId(), finalBand);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * Lấy Users hiện tại từ JWT SecurityContext.
     * Claim "userId" được inject bởi CustomJwtDecoder.
     */
    private Users resolveCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String userId = jwt.getClaim("userId");
            if (userId != null) {
                return usersRepository.findById(userId).orElse(null);
            }
        }
        log.warn("Không tìm được userId từ SecurityContext, submission sẽ không có user");
        return null;
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

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