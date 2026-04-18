package io.gsp26se16.moni.vocab.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuizQuestion {
    Integer id;
    String type;
    String prompt;
    List<String> options;
    int correctIndex;
    String word;
    String explanation;
    String vocabStatus; // "DRAFT" (Sổ biết tuốt) or "ACTIVE" (Sổ nhắc lại)
    Integer userSelected; // Populated during history review
}
