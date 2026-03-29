package io.gsp26se16.moni.ai.writing.controller;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.gsp26se16.moni.ai.speaking.service.ConversationEngine;
import io.gsp26se16.moni.ai.writing.request.WritingRequest;
import io.gsp26se16.moni.ai.writing.service.WritingTask1Service;
import io.gsp26se16.moni.ai.writing.service.WritingTask2Service;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.service.TranscriptService;
import io.gsp26se16.moni.payment.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final WritingTask1Service task1Service;
    private final WritingTask2Service task2Service;
    private final CreditService creditService;
    private final TranscriptService transcriptService;
    private final ConversationEngine conversationEngine;
    private final io.gsp26se16.moni.ai.writing.repository.WritingSubmissionRepository writingSubmissionRepository;
    private final io.gsp26se16.moni.ai.writing.repository.AiEvaluationRepository aiEvaluationRepository;

    @PostMapping(value = "/writing/score", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> scoreWriting(@ModelAttribute WritingRequest request)
            throws JsonProcessingException {
        String userId = getCurrentUserId();
        if (userId != null) {
            creditService.checkAndDeduct(userId, "AI_WRITING_SCORE");
        }

        boolean isTask1 = (request.getChartImage() != null
                && !request.getChartImage().isEmpty()
                && request.getChartImage().getSize() > 0);

        Map<String, Object> result;
        if (isTask1) {
            result = task1Service.score(request);
        } else {
            result = task2Service.score(request);
        }

        // Update original submission status + re-link AiEvaluation if submissionId provided
        if (request.getSubmissionId() != null) {
            Long origId = request.getSubmissionId();
            writingSubmissionRepository.findById(origId).ifPresent(sub -> {
                sub.setEvaluationStatus(io.gsp26se16.moni.common.enumeration.EvaluationStatus.COMPLETED);
                writingSubmissionRepository.save(sub);
            });
            // Re-link the most recent AiEvaluation to original submission
            var recentEvals = aiEvaluationRepository.findBySubmissionId(origId);
            if (recentEvals.isEmpty()) {
                // Find eval created by scoring (linked to the NEW submission created by Task1/2Service)
                // Get all evals sorted by creation, take latest WRITING one
                var allEvals = aiEvaluationRepository.findAll();
                allEvals.stream()
                        .filter(e -> io.gsp26se16.moni.common.enumeration.Skill.WRITING.equals(e.getSkill()))
                        .max(java.util.Comparator.comparing(
                                e -> e.getCreatedAt() != null ? e.getCreatedAt() : java.time.LocalDateTime.MIN))
                        .ifPresent(e -> {
                            e.setSubmissionId(origId);
                            aiEvaluationRepository.save(e);
                        });
            }
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/speaking/score", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> scoreSpeaking(
            @RequestPart(value = "audio", required = false) MultipartFile audio,
            @RequestPart(value = "question", required = false) String question)
            throws IOException {
        String userId = getCurrentUserId();

        if (audio == null || audio.isEmpty()) {
            log.warn("Speaking score: no audio file received");
            return ResponseEntity.badRequest()
                    .body(Map.of("code", 1085, "message", "Không nhận được file audio. Vui lòng ghi âm và thử lại."));
        }

        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 1086, "message", "Thiếu câu hỏi."));
        }

        try {
            // Step 1: Transcribe audio via AssemblyAI
            log.info(
                    "Speaking practice scoring: audio={} bytes, type={}, question={} chars",
                    audio.getSize(),
                    audio.getContentType(),
                    question.length());
            String transcript = transcriptService.transcribeAudioBytes(audio.getBytes(), audio.getContentType());

            if (transcript == null || transcript.isBlank()) {
                return ResponseEntity.ok(Map.of(
                        "overallScore",
                        0.0,
                        "fluency",
                        0.0,
                        "pronunciation",
                        0.0,
                        "vocabulary",
                        0.0,
                        "grammar",
                        0.0,
                        "comments",
                        "Không phát hiện giọng nói. Vui lòng thử lại."));
            }

            // Step 2: Evaluate with AI
            Map<String, Object> result = conversationEngine.evaluatePractice(userId, question, transcript);

            // Step 3: Deduct credit AFTER successful evaluation
            if (userId != null) {
                creditService.checkAndDeduct(userId, "AI_SPEAKING_SCORE");
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Speaking scoring failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("code", 9998, "message", "Chấm điểm thất bại: " + e.getMessage()));
        }
    }

    private String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        if (auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaim("userId");
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }
}
