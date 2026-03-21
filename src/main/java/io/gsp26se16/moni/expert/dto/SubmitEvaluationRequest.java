package io.gsp26se16.moni.expert.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubmitEvaluationRequest {
    Double overallScore;
    Double fluency;
    Double vocabulary;
    Double grammar;
    Double pronunciation;
    Double taskResponse;
    Double coherence;
    Double lexicalResource;
    Double grammaticalRange;
    String feedback;
    String strengths;
    String areasForImprovement;
}
