package io.gsp26se16.moni.practice.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import io.gsp26se16.moni.common.enumeration.TestSessionStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ExamSessionResponse {
    Integer sessionId;
    Integer testId;
    TestSessionStatus status;
    LocalDateTime startedAt;
    Integer durationSeconds;
    Integer remainingSeconds;
    Integer attemptId;
    List<SavedAnswer> savedAnswers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class SavedAnswer {
        Integer questionId;
        Integer selectedOptionId;
        String answerText;
    }
}
