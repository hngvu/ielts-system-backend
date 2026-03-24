package io.gsp26se16.moni.tag.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.tag.dto.request.TagAssignRequest;
import io.gsp26se16.moni.tag.dto.request.TagRequest;
import io.gsp26se16.moni.tag.dto.response.TagResponse;
import io.gsp26se16.moni.tag.entity.TagType;
import io.gsp26se16.moni.tag.service.TagService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @PostMapping
    public ResponseEntity<ApiResponse<TagResponse>> createTag(@RequestBody @Valid TagRequest request) {
        ApiResponse<TagResponse> apiResponse = ApiResponse.<TagResponse>builder()
                .result(tagService.createTag(request))
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TagResponse>>> getTags(
            @RequestParam(required = false) TagType type, @RequestParam(required = false) String keyword) {
        ApiResponse<List<TagResponse>> apiResponse = ApiResponse.<List<TagResponse>>builder()
                .result(tagService.getTags(type, keyword))
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TagResponse>> getTag(@PathVariable Integer id) {
        ApiResponse<TagResponse> apiResponse = ApiResponse.<TagResponse>builder()
                .result(tagService.getTagById(id))
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TagResponse>> updateTag(
            @PathVariable Integer id, @RequestBody @Valid TagRequest request) {
        ApiResponse<TagResponse> apiResponse = ApiResponse.<TagResponse>builder()
                .result(tagService.updateTag(id, request))
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTag(@PathVariable Integer id) {
        tagService.deleteTag(id);

        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .message("Tag has been deleted successfully")
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping("/questions/{questionId}/tags")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assignToQuestion(
            @PathVariable Integer questionId, @RequestBody @Valid TagAssignRequest request) {

        tagService.assignTagsToQuestion(questionId, request);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Đã cập nhật Tag cho Câu hỏi thành công")
                .result(Map.of("assigned", true, "targetId", questionId, "tagIds", request.getTagIds()))
                .build());
    }

    @PostMapping("/stimulus/{stimulusId}/tags")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assignToStimulus(
            @PathVariable Integer stimulusId, @RequestBody @Valid TagAssignRequest request) {

        tagService.assignTagsToStimulus(stimulusId, request);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Đã cập nhật Tag cho Đoạn văn/Audio thành công")
                .result(Map.of("assigned", true, "targetId", stimulusId, "tagIds", request.getTagIds()))
                .build());
    }

    @PostMapping("/tests/{testId}/tags")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assignToTest(
            @PathVariable Integer testId, @RequestBody @Valid TagAssignRequest request) {

        tagService.assignTagsToTest(testId, request);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Đã cập nhật Tag cho Đề thi thành công")
                .result(Map.of("assigned", true, "targetId", testId, "tagIds", request.getTagIds()))
                .build());
    }
}
