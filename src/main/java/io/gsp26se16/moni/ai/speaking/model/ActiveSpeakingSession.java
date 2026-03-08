package io.gsp26se16.moni.ai.speaking.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * In-memory representation of a live speaking session.
 * Holds audio buffer, transcripts, and evaluation state.
 * All session state lives here — services must remain stateless.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ActiveSpeakingSession {

    /** Matches the persisted SpeakingSession.id */
    String sessionId;

    String userId;

    String currentQuestion;

    /** Raw PCM audio buffer — appended per incoming chunk */
    @Builder.Default
    List<byte[]> audioBuffer = new ArrayList<>();

    /** Transcribed text segments collected so far */
    @Builder.Default
    List<String> transcripts = new CopyOnWriteArrayList<>();

    /**
     * Tracks how many consecutive bytes of silence have been seen.
     * Used by AudioStreamService for silence-boundary detection.
     */
    @Builder.Default
    int silentByteCount = 0;

    /** Latest evaluation result (JSON-like map stored as string for simplicity) */
    @Builder.Default
    String evaluationState = "PENDING";

    // ── Convenience helpers ───────────────────────────────────────────────────

    public void appendChunk(byte[] chunk) {
        audioBuffer.add(chunk);
    }

    public void addTranscript(String text) {
        if (text != null && !text.isBlank()) {
            transcripts.add(text.trim());
        }
    }

    /** Returns all buffered audio as a single flat byte array, then clears the buffer. */
    public byte[] drainBuffer() {
        int total = audioBuffer.stream().mapToInt(b -> b.length).sum();
        byte[] merged = new byte[total];
        int offset = 0;
        for (byte[] chunk : audioBuffer) {
            System.arraycopy(chunk, 0, merged, offset, chunk.length);
            offset += chunk.length;
        }
        audioBuffer.clear();
        silentByteCount = 0;
        return merged;
    }

    /** Full transcript as a single joined string for LLM evaluation. */
    public String getFullTranscript() {
        return String.join(" ", transcripts);
    }
}
