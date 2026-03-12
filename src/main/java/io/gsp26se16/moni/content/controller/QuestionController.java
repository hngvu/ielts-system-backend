package io.gsp26se16.moni.content.controller;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.content.dto.request.QuestionUpdateRequest;
import io.gsp26se16.moni.content.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/questions")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class QuestionController {

    private final QuestionService questionService;

    @PutMapping("/{id}")
    @Operation(summary = "Update Question & Tags")
    public ResponseEntity<ApiResponse<Void>> updateQuestion(
            @PathVariable Integer id, @RequestBody @Valid QuestionUpdateRequest request) {

        questionService.updateQuestion(id, request);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(1000)
                .message("Update question and Tag successfully")
                .build());
    }

    @PutMapping("/batch")
    @Operation(summary = "Batch Update Questions")
    public ResponseEntity<ApiResponse<Void>> batchUpdateQuestions(
            @RequestBody Map<Integer, QuestionUpdateRequest> updates) {
        questionService.batchUpdateQuestions(updates);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(1000)
                .message("Batch update successfully")
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Question (cascades options)")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(@PathVariable Integer id) {
        questionService.deleteQuestion(id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().code(1000).message("Deleted").build());
    }
}
