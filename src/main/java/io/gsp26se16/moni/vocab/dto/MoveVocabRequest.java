package io.gsp26se16.moni.vocab.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MoveVocabRequest {
    Integer vocabListId;
}
