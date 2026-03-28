package io.gsp26se16.moni.ai.writing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

/** DTO nhận request nộp bài Writing từ client */
@Data
public class SubmitWritingRequest {

    @NotNull(message = "testId is required")
    private Integer testId;

    @NotNull(message = "stimulusId is required")
    private Integer stimulusId;

    /** 1 = Task 1, 2 = Task 2 */
    @NotNull(message = "taskType is required")
    private Integer taskType;

    @NotBlank(message = "essayContent is required")
    private String essayContent;

    private Integer wordCount;
}
