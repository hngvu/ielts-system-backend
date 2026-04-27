package io.gsp26se16.moni.placement.dto.response;

import java.time.LocalDateTime;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlacementConfigResponse {

    Integer id;
    String name;

    Integer readingTestId;
    String readingTestTitle;

    Integer listeningTestId;
    String listeningTestTitle;

    Integer writingTestId;
    String writingTestTitle;

    Integer speakingTestId;
    String speakingTestTitle;

    Boolean isActive;
    LocalDateTime createdAt;
}
