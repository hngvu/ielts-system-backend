package io.gsp26se16.moni.content.controller;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.content.dto.request.QuestionCreateRequest;
import io.gsp26se16.moni.content.dto.request.QuestionGroupCreateRequest;
import io.gsp26se16.moni.content.service.QuestionGroupService;
import io.gsp26se16.moni.content.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class QuestionGroupController {

    private final QuestionGroupService questionGroupService;
    private final QuestionService questionService;

    @PostMapping("/stimuli/{stimulusId}/question-groups")
    @Operation(summary = "Create QuestionGroup under a Stimulus")
    public ResponseEntity<ApiResponse<Integer>> createQuestionGroup(
            @PathVariable Integer stimulusId, @RequestBody @Valid QuestionGroupCreateRequest request) {

        Integer groupId = questionGroupService.createForStimulus(stimulusId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Integer>builder().code(1000).result(groupId).build());
    }

    @DeleteMapping("/question-groups/{id}")
    @Operation(summary = "Delete QuestionGroup (cascades questions + options)")
    public ResponseEntity<ApiResponse<Void>> deleteQuestionGroup(@PathVariable Integer id) {
        questionGroupService.deleteQuestionGroup(id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().code(1000).message("Deleted").build());
    }

    @PatchMapping("/question-groups/{id}/image-url")
    @Operation(summary = "Update QuestionGroup imageUrl")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateImageUrl(
            @PathVariable Integer id, @RequestBody Map<String, String> body) {
        String imageUrl = body.get("imageUrl");
        questionGroupService.updateImageUrl(id, imageUrl);
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Updated")
                .result(Map.of("id", id, "imageUrl", imageUrl != null ? imageUrl : ""))
                .build());
    }

    @PatchMapping("/question-groups/{id}/group-content")
    @Operation(summary = "Update QuestionGroup groupContent (gap-fill paragraph)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateGroupContent(
            @PathVariable Integer id, @RequestBody Map<String, String> body) {
        String groupContent = body.get("groupContent");
        questionGroupService.updateGroupContent(id, groupContent);
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Updated")
                .result(Map.of("id", id, "groupContent", groupContent != null ? groupContent : ""))
                .build());
    }

    @PatchMapping("/question-groups/{id}/question-type-code")
    @Operation(summary = "Update QuestionGroup questionTypeCode")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateQuestionTypeCode(
            @PathVariable Integer id, @RequestBody Map<String, String> body) {
        String questionTypeCode = body.get("questionTypeCode");
        questionGroupService.updateQuestionTypeCode(id, questionTypeCode);
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Updated")
                .result(Map.of("id", id, "questionTypeCode", questionTypeCode != null ? questionTypeCode : ""))
                .build());
    }

    @PostMapping("/question-groups/{groupId}/questions")
    @Operation(summary = "Create Question under a QuestionGroup")
    public ResponseEntity<ApiResponse<Integer>> createQuestion(
            @PathVariable Integer groupId, @RequestBody @Valid QuestionCreateRequest request) {

        Integer questionId = questionService.createForGroup(groupId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Integer>builder()
                        .code(1000)
                        .result(questionId)
                        .build());
    }
}
