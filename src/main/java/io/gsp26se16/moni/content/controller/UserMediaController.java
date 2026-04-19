package io.gsp26se16.moni.content.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.content.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@PreAuthorize("hasRole('LEARNER')")
@RequestMapping("/api/v1/user/media")
@RequiredArgsConstructor
@Tag(name = "User Media Management", description = "Upload file Audio/Image for regular users")
public class UserMediaController {

    private final StorageService storageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload Audio or Image for User")
    public ResponseEntity<ApiResponse<String>> upload(@RequestPart("file") MultipartFile file) {
        String fileUrl = storageService.uploadFile(file);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .result(fileUrl)
                .message("Upload successful")
                .build());
    }
}
