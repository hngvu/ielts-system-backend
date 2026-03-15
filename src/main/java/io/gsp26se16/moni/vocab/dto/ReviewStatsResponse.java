package io.gsp26se16.moni.vocab.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewStatsResponse {
    int totalSaved;
    int dueToday;
    int masteredCount;
    int reviewedToday;
}
