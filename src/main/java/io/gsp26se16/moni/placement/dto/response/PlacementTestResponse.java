package io.gsp26se16.moni.placement.dto.response;

import io.gsp26se16.moni.content.dto.response.TestDetailResponse;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlacementTestResponse {
    TestDetailResponse readingTest;
    TestDetailResponse listeningTest;
    TestDetailResponse writingTest;
    TestDetailResponse speakingTest;
}
