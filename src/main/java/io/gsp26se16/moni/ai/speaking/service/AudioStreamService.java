package io.gsp26se16.moni.ai.speaking.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import io.gsp26se16.moni.ai.speaking.model.ActiveSpeakingSession;
import io.gsp26se16.moni.ai.speaking.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Processes the incoming audio stream for a speaking session.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Append audio chunks to the session buffer.</li>
 *   <li>Detect speech boundaries using silence analysis on raw PCM data.</li>
 *   <li>When a boundary is detected, extract the segment, send it to STT,
 *       and forward the transcript to {@link ConversationEngine}.</li>
 * </ul>
 *
 * <p><strong>Silence detection algorithm:</strong>
 * Audio is assumed to be 16-bit signed PCM, mono, 16 kHz.
 * The RMS amplitude of each incoming chunk is calculated. If the RMS falls
 * below {@link #SILENCE_THRESHOLD} for an accumulated duration exceeding
 * {@link #SILENCE_TRIGGER_BYTES}, the current buffer is treated as a
 * completed speech segment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudioStreamService {

    private final SessionManager sessionManager;
    private final SpeechToTextService speechToTextService;
    private final ConversationEngine conversationEngine;

    // ── Silence detection constants ───────────────────────────────────────────

    /**
     * RMS amplitude threshold below which a chunk is considered silent.
     * For 16-bit PCM the theoretical max RMS is 32768.
     * A value of 400 corresponds to very quiet audio (~−38 dBFS).
     */
    private static final double SILENCE_THRESHOLD = 400.0;

    /**
     * Minimum accumulated silent byte count before triggering a segment boundary.
     * 16 kHz × 16-bit = 32000 bytes/sec → 700 ms of silence ≈ 22400 bytes.
     */
    private static final int SILENCE_TRIGGER_BYTES = 22_400;

    /**
     * Minimum buffer size to bother transcribing (avoid sending near-empty segments).
     * ~500 ms of audio ≈ 16000 bytes.
     */
    private static final int MIN_SEGMENT_BYTES = 16_000;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Handles an incoming audio chunk.
     *
     * @param sessionId  the active speaking session ID
     * @param audioChunk raw 16-bit PCM bytes
     * @return a transcript string if a segment was completed, {@code null} otherwise
     */
    public String onAudioChunk(String sessionId, byte[] audioChunk) {
        ActiveSpeakingSession session = sessionManager.getSession(sessionId);
        session.appendChunk(audioChunk);

        if (isSilent(audioChunk)) {
            session.setSilentByteCount(session.getSilentByteCount() + audioChunk.length);
        } else {
            session.setSilentByteCount(0);
        }

        if (session.getSilentByteCount() >= SILENCE_TRIGGER_BYTES) {
            return extractAndTranscribe(session);
        }

        return null;
    }

    /**
     * Called when the client signals end of speaking ("stop" message).
     * Drains any remaining audio, transcribes it, and triggers a final evaluation.
     *
     * @return the final evaluation result map
     */
    public Map<String, Object> finalise(String sessionId) {
        ActiveSpeakingSession session = sessionManager.getSession(sessionId);

        // Transcribe any remaining audio
        extractAndTranscribe(session);

        // Run full evaluation on accumulated transcripts
        return conversationEngine.evaluate(sessionId);
    }

    // ── Internal logic ────────────────────────────────────────────────────────

    /**
     * Drains the buffer, sends it to STT, and passes the transcript to
     * {@link ConversationEngine} for incremental processing.
     *
     * @return the transcript if produced, {@code null} if segment was too short
     */
    private String extractAndTranscribe(ActiveSpeakingSession session) {
        int bufferedBytes =
                session.getAudioBuffer().stream().mapToInt(b -> b.length).sum();

        if (bufferedBytes < MIN_SEGMENT_BYTES) {
            log.debug("Segment too short ({} bytes), skipping transcription", bufferedBytes);
            session.setSilentByteCount(0);
            return null;
        }

        byte[] segment = session.drainBuffer();

        try {
            String transcript = speechToTextService.transcribe(segment);
            if (transcript != null && !transcript.isBlank()) {
                session.addTranscript(transcript);
                log.info("Transcript segment for session {}: \"{}\"", session.getSessionId(), transcript);
                return transcript;
            }
        } catch (Exception e) {
            log.error("STT failed for session {}: {}", session.getSessionId(), e.getMessage());
        }

        return null;
    }

    /**
     * Determines whether a PCM audio chunk is silent by computing its RMS amplitude.
     * Assumes 16-bit little-endian signed PCM.
     */
    private boolean isSilent(byte[] pcmChunk) {
        if (pcmChunk.length < 2) return true;

        long sumOfSquares = 0;
        int sampleCount = pcmChunk.length / 2;

        for (int i = 0; i < sampleCount * 2; i += 2) {
            // Combine two bytes into a signed 16-bit sample (little-endian)
            int sample = (pcmChunk[i] & 0xFF) | (pcmChunk[i + 1] << 8);
            sumOfSquares += (long) sample * sample;
        }

        double rms = Math.sqrt((double) sumOfSquares / sampleCount);
        return rms < SILENCE_THRESHOLD;
    }
}
