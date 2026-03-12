package io.gsp26se16.moni.practice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TestSessionResultResponse {
    private Integer sessionId;
    private Double overallBand;
    private Integer totalCorrect;
    private Integer totalQuestions;
    private LocalDateTime endedAt;
    private String message;
}
