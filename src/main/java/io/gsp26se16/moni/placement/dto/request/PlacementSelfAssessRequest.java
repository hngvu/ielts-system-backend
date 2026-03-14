package io.gsp26se16.moni.placement.dto.request;

import jakarta.validation.constraints.NotNull;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlacementSelfAssessRequest {

    @NotNull
    Double readingBand;

    @NotNull
    Double listeningBand;

    @NotNull
    Double writingBand;

    @NotNull
    Double speakingBand;

    @NotNull
    Double targetBand;
}
