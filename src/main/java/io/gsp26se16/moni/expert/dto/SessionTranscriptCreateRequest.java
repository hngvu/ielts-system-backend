package io.gsp26se16.moni.expert.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.*;
import lombok.experimental.FieldDefaults;

/** Batch upload of finalized captions during/after a video call. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SessionTranscriptCreateRequest {

    List<Entry> entries;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Entry {
        String text;
        String language;
        LocalDateTime spokenAt;
    }
}
