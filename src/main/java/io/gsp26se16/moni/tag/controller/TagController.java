package io.gsp26se16.moni.tag.controller;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.tag.dto.request.TagRequest;
import io.gsp26se16.moni.tag.dto.response.TagResponse;
import io.gsp26se16.moni.tag.entity.TagType;
import io.gsp26se16.moni.tag.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            @RequestParam(required = false) TagType type,
            @RequestParam(required = false) String keyword
    ) {
        ApiResponse<List<TagResponse>> apiResponse = ApiResponse.<List<TagResponse>>builder()
                .result(tagService.getTags(type, keyword))
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TagResponse>> getTag(@PathVariable Long id) {
        ApiResponse<TagResponse> apiResponse = ApiResponse.<TagResponse>builder()
                .result(tagService.getTagById(id))
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TagResponse>> updateTag(
            @PathVariable Long id,
            @RequestBody @Valid TagRequest request) {
        ApiResponse<TagResponse> apiResponse = ApiResponse.<TagResponse>builder()
                .result(tagService.updateTag(id, request))
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);

        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .message("Tag has been deleted successfully")
                .build();

        return ResponseEntity.ok(apiResponse);
    }
}
