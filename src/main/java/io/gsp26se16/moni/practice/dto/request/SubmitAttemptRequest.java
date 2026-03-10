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
    @NotNull
    Integer testId;

    @NotNull
    Integer stimulusId;

    @NotNull
    Integer elapsedSeconds;

    @NotNull
    @Size(min = 1)
    @Valid
    List<AnswerRequest> answers;
}
