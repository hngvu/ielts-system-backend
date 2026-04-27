package io.gsp26se16.moni.placement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlacementConfigRequest {

    @NotBlank
    String name;

    @NotNull
    Integer readingTestId;

    @NotNull
    Integer listeningTestId;

    @NotNull
    Integer writingTestId;

    @NotNull
    Integer speakingTestId;
}
