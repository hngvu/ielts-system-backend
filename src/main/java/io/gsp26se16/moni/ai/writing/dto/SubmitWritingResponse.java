package io.gsp26se16.moni.ai.writing.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/** DTO trả về sau khi nộp bài Writing */
@Data
@Builder
public class SubmitWritingResponse {
    private Long submissionId;
    private String evaluationStatus;
    private LocalDateTime submittedAt;
}
