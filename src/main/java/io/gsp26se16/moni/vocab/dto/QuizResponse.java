package io.gsp26se16.moni.vocab.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuizResponse {
    List<QuizQuestion> questions;
    String source;
}
