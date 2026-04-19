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
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/media")
@RequiredArgsConstructor
@Tag(name = "Media Management", description = "Upload file Audio/Image")
public class MediaController {

    private final StorageService storageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload File")
    public ResponseEntity<ApiResponse<String>> upload(@RequestPart("file") MultipartFile file) {
        String fileUrl = storageService.uploadFile(file);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .result(fileUrl)
                .message("Upload successful")
                .build());
    }

    @DeleteMapping
    @Operation(summary = "Delete File", description = "Xóa file trên Cloudinary bằng URL")
    public ResponseEntity<ApiResponse<Void>> delete(@RequestParam("url") String fileUrl) {

        storageService.deleteFile(fileUrl);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder().message("File deleted successfully").build());
    }
}
