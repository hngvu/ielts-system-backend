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
import io.gsp26se16.moni.ai.writing.repository.AiEvaluationRepository;
import io.gsp26se16.moni.ai.writing.repository.WritingSubmissionRepository;
import io.gsp26se16.moni.ai.writing.request.WritingRequest;
import io.gsp26se16.moni.ai.writing.service.Helper;
import io.gsp26se16.moni.ai.writing.service.WritingTask1Service;
import io.gsp26se16.moni.ai.writing.service.WritingTask2Service;
import io.gsp26se16.moni.common.enumeration.EvaluationStatus;
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
    private final Helper writingHelper;
    private final WritingSubmissionRepository writingSubmissionRepository;
    private final AiEvaluationRepository aiEvaluationRepository;

    @PostMapping(value = "/writing/score", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> scoreWriting(@ModelAttribute WritingRequest request)
            throws JsonProcessingException {

        if (request.getAnswer() == null || request.getAnswer().trim().split("\\s+").length < 20) {
            throw new AppException(ErrorCode.SPAM_ESSAY);
        }

        if (!writingHelper.isEnglish(request.getAnswer())) {
            throw new AppException(ErrorCode.NON_ENGLISH_ESSAY);
        }

        String userId = getCurrentUserId();
        if (userId != null) {
            creditService.checkAndDeduct(userId, "AI_WRITING_SCORE");
        }

        // taskType: 1 = Task 1 (chart/diagram), 2 = Task 2 (essay)
        // Default to Task 2 if not specified
        boolean isTask1 = request.getTaskType() != null && request.getTaskType() == 1;

        Map<String, Object> result;
        if (isTask1) {
            result = task1Service.score(request);
        } else {
            result = task2Service.score(request);
        }

        // Update original submission status if it was processed and returned
        if (request.getSubmissionId() != null) {
            Long origId = request.getSubmissionId();
            writingSubmissionRepository.findById(origId).ifPresent(sub -> {
                sub.setEvaluationStatus(EvaluationStatus.COMPLETED);
                writingSubmissionRepository.save(sub);
            });
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

        if (userId != null) {
            creditService.checkAndDeduct(userId, "AI_SPEAKING_SCORE");
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
