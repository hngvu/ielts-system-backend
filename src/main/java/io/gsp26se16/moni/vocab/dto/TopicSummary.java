package io.gsp26se16.moni.vocab.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TopicSummary {
    String topic;
    int wordCount;
}
