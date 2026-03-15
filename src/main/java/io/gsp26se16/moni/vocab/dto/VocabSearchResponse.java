package io.gsp26se16.moni.vocab.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VocabSearchResponse {
    String word;
    String phonetic;
    String pos;
    String definition;
    String example;
    String meaning;
    String audioUrl;
    boolean isSaved;
    Integer dictionaryId;
}
