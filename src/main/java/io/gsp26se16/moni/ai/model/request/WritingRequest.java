package io.gsp26se16.moni.ai.model.request;

import jakarta.validation.constraints.NotBlank;

import org.springframework.web.multipart.MultipartFile;

import lombok.Data;

@Data
public class WritingRequest {
    @NotBlank(message = "Question is required")
    private String question;

    private String answer;

    // For Task 1: Question Group ID to cache vision analysis
    private Integer questionGroupId;

    // Optional: Chart image for Task 1 (only if first submission)
    private MultipartFile chartImage;

    // Deprecated: Use questionGroupId for caching instead
    @Deprecated
    private String visionAnalysis;
}
