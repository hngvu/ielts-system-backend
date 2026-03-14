package io.gsp26se16.moni.placement.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import io.gsp26se16.moni.practice.dto.request.AnswerRequest;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlacementSubmitRequest {

    @NotNull
    Integer readingTestId;

    @NotNull
    Integer listeningTestId;

    @NotNull
    @Valid
    List<AnswerRequest> readingAnswers;

    @NotNull
    @Valid
    List<AnswerRequest> listeningAnswers;

    @NotNull
    Double writingBand;

    @NotNull
    Double speakingBand;

    @NotNull
    Double targetBand;

    Integer elapsedSeconds;
}
