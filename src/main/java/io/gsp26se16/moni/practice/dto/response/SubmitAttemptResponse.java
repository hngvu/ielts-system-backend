package io.gsp26se16.moni.practice.dto.response;

import java.util.List;

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
public class SubmitAttemptResponse {
    Integer attemptId;
    Integer testSessionId;
    int score;
    int totalQuestions;
    int elapsedSeconds;
    List<AnswerResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class AnswerResult {
        Integer questionId;
        String questionContent;
        Integer selectedOptionId;
        String answerText;
        boolean isCorrect;
        Integer correctOptionId;
        String correctOptionLabel;
        String correctOptionContent;
        String explanation;
        String evidence;
    }
}
