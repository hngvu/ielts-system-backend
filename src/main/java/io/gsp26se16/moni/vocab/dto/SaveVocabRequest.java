package io.gsp26se16.moni.vocab.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SaveVocabRequest {
    String word;
    Integer vocabListId;
    String sentence; // optional context sentence for better AI meaning
}
