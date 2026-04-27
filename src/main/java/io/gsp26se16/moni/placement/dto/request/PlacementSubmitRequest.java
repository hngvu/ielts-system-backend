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

    // Writing
    @NotNull
    Integer writingTestId;

    @NotNull
    String writingEssay;

    @NotNull
    Integer writingTaskType; // 1 = Task 1, 2 = Task 2

    Integer writingStimulusId; // For Task 1 chart analysis

    // Speaking
    @NotNull
    Integer speakingTestId;

    @NotNull
    String speakingAudioBase64;

    @NotNull
    Double targetBand;

    Integer elapsedSeconds;
}
