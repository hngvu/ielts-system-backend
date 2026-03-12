package io.gsp26se16.moni.practice.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
public class SubmitAttemptRequest {
    @NotNull(message = "Elapsed seconds is required")
    Integer elapsedSeconds;

    @NotNull
    @Size(min = 1, message = "Answers list cannot be empty")
    @Valid
    List<AnswerRequest> answers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class AnswerRequest {
        @NotNull(message = "Question ID is required")
        Integer questionId;

        Integer selectedOptionId;

        String answerText;
    }
}
