package io.gsp26se16.moni.vocab.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateVocabListRequest {
    String title;
    String icon;
    String description;
}
