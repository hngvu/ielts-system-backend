package io.gsp26se16.moni.vocab.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewStatsResponse {
    int totalSaved;
    int dueToday;
    int masteredCount;
    int listsCount;
    int learningCount;
    // Virtual notebook counts (new)
    int toLearn; // DRAFT words - Sổ từ biết tuốt
    int reviewing; // ACTIVE words - Sổ tay nhắc lại
    int mastered; // MASTERED words - Sổ tay Master
    int manual; // MANUAL source words - Sổ từ của tôi
}
