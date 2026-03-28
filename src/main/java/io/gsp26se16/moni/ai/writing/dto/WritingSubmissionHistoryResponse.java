package io.gsp26se16.moni.ai.writing.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/** DTO cho lịch sử nộp bài Writing của user */
@Data
@Builder
public class WritingSubmissionHistoryResponse {
    private Long submissionId;
    private Integer testId;
    private Integer stimulusId;
    private String taskType;
    private Integer wordCount;
    private String evaluationStatus;
    private LocalDateTime submittedAt;
}
