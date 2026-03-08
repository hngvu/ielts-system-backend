package io.gsp26se16.moni.content.controller;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.content.dto.request.QuestionUpdateRequest;
import io.gsp26se16.moni.content.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    @PutMapping("/{id}")
    @Operation(summary = "Update Question & Tags")
    public ResponseEntity<ApiResponse<Void>> updateQuestion(
            @PathVariable Integer id,
            @RequestBody @Valid QuestionUpdateRequest request) {

        questionService.updateQuestion(id, request);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(1000)
                .message("Update question and Tag successfully")
                .build());
    }
}
