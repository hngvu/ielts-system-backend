package io.gsp26se16.moni.practice.dto.request;

import jakarta.validation.constraints.NotNull;

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
public class AnswerRequest {
    @NotNull
    Integer questionId;

    Integer selectedOptionId; // for MCQ/TFNG/YNNG

    String answerText; // for fill-in-blank/short-answer
}
