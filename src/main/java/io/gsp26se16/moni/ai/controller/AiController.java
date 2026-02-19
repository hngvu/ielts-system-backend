package io.gsp26se16.moni.ai.controller;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.gsp26se16.moni.ai.model.request.WritingRequest;
import io.gsp26se16.moni.ai.service.WritingTask1Service;
import io.gsp26se16.moni.ai.service.WritingTask2Service;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final WritingTask1Service task1Service;
    private final WritingTask2Service task2Service;

    @PostMapping(value = "/writing/score", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> scoreWriting(@ModelAttribute WritingRequest request) throws JsonProcessingException {
        boolean isTask1 =
                (request.getChartImage() != null && !request.getChartImage().isEmpty());

        if (isTask1) {
            return ResponseEntity.ok(task1Service.score(request));
        } else {
            return ResponseEntity.ok(task2Service.score(request));
        }
    }

    @PostMapping(value = "/speaking/score", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> scoreSpeaking(
            @RequestPart("audio") MultipartFile audio, @RequestPart("question") String question) throws IOException {
        // Placeholder for Speaking Service integration
        return ResponseEntity.ok(Map.of("message", "Speaking scoring is pending implementation"));
    }
}
