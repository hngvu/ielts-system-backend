package io.gsp26se16.moni.ai.writing.request;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

@Data
public class WritingRequest {
    @NotBlank(message = "Question is required")
    private String question;

    private String answer;

    /**
     * 1 = Task 1 (chart/diagram), 2 = Task 2 (essay).
     */
    private Integer taskType;

    // For Task 1: Stimulus ID to look up cached vision analysis result
    private Integer stimulusId;

    // Optional: link to existing WritingSubmission to update status after scoring
    private Long submissionId;
}
