package io.gsp26se16.moni.practice.dto.response;

import io.gsp26se16.moni.common.enumeration.Skill;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TestSessionResponse {
    private Integer sessionId;
    private Integer testId;
    private String testTitle;
    private LocalDateTime startedAt;
    private List<AttemptDetail> attempts;

    @Data
    @Builder
    public static class AttemptDetail {
        private Integer attemptId;
        private Integer stimulusId;
        private Integer section;
        private Skill skill; // R, L, W, hoặc S
    }
}
