package io.gsp26se16.moni.vocab.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizResponse {
    List<QuizQuestion> questions;
    String source;

    @Builder.Default
    Boolean isHistory = false;

    Integer score;
}
