package io.gsp26se16.moni.vocab.dto;

import java.time.LocalDateTime;

import io.gsp26se16.moni.vocab.enumeration.VocabListType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VocabListResponse {
    Integer id;
    String title;
    String icon;
    String description;
    VocabListType type;
    boolean isDefault;
    int wordCount;
    LocalDateTime createdAt;
}
