package io.gsp26se16.moni.content.controller;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.ai.writing.service.PromptLoader;
import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.content.dto.request.QuestionUpdateRequest;
import io.gsp26se16.moni.content.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/questions")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class QuestionController {

    private final QuestionService questionService;
    private final ChatClient.Builder chatClientBuilder;
    private final PromptLoader promptLoader;

    @PutMapping("/{id}")
    @Operation(summary = "Update Question & Tags")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateQuestion(
            @PathVariable Integer id, @RequestBody @Valid QuestionUpdateRequest request) {

        questionService.updateQuestion(id, request);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Update question and Tag successfully")
                .result(Map.of("id", id))
                .build());
    }

    @PutMapping("/batch")
    @Operation(summary = "Batch Update Questions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchUpdateQuestions(
            @RequestBody Map<Integer, QuestionUpdateRequest> updates) {
        questionService.batchUpdateQuestions(updates);
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Batch update successfully")
                .result(Map.of("updatedCount", updates.size()))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Question (cascades options)")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(@PathVariable Integer id) {
        questionService.deleteQuestion(id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().code(1000).message("Deleted").build());
    }

    @PostMapping("/generate-hint")
    @Operation(summary = "Generate AI hint/suggestion for a speaking question")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateHint(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Map<String, String>>builder()
                            .code(1001)
                            .message("Question text is required")
                            .build());
        }
        try {
            String prompt = promptLoader.loadPrompt("speaking/hint_generator.txt", Map.of("question", question));
            ChatClient client = chatClientBuilder.build();
            String hint = client.prompt().user(prompt).call().content();
            return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                    .code(1000)
                    .message("Hint generated")
                    .result(Map.of("hint", hint != null ? hint : ""))
                    .build());
        } catch (Exception e) {
            log.error("Failed to generate hint for question: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<Map<String, String>>builder()
                            .code(9998)
                            .message("Tạo gợi ý thất bại: " + e.getMessage())
                            .build());
        }
    }
}
