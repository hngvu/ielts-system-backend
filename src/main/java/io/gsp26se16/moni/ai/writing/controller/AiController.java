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

import io.gsp26se16.moni.ai.writing.request.WritingRequest;
import io.gsp26se16.moni.ai.writing.service.WritingTask1Service;
import io.gsp26se16.moni.ai.writing.service.WritingTask2Service;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.payment.service.CreditService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final WritingTask1Service task1Service;
    private final WritingTask2Service task2Service;
    private final CreditService creditService;

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

        if (isTask1) {
            return ResponseEntity.ok(task1Service.score(request));
        } else {
            return ResponseEntity.ok(task2Service.score(request));
        }
    }

    @PostMapping(value = "/speaking/score", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> scoreSpeaking(
            @RequestPart("audio") MultipartFile audio, @RequestPart("question") String question) throws IOException {
        // TODO: Integrate actual Speaking scoring service
        // Credit deduction disabled until scoring is implemented
        return ResponseEntity.ok(Map.of("message", "Speaking scoring is pending implementation"));
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
