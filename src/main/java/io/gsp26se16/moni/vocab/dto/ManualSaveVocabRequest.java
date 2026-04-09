package io.gsp26se16.moni.vocab.dto;

import io.gsp26se16.moni.vocab.enumeration.VocabSourceType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ManualSaveVocabRequest {
    String word;
    Integer vocabListId;
    String meaning;
    String phonetic;
    String pos;
    String definition;
    String example;
    VocabSourceType sourceType; // where this word was saved from
}
