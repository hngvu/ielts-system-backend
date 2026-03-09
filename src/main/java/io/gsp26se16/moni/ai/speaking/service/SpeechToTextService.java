package io.gsp26se16.moni.ai.speaking.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends raw PCM/WAV audio to the NVIDIA Whisper Large V3 API and returns the transcript.
 *
 * <p>API: {@code POST https://integrate.api.nvidia.com/v1/audio/transcriptions}
 * (OpenAI-compatible multipart endpoint)
 *
 * <p>Input:  raw PCM bytes (16-bit, mono, 16 kHz)
 * <p>Output: transcribed text string
 */
@Slf4j
@Service
public class SpeechToTextService {

    private static final String WHISPER_URL = "https://integrate.api.nvidia.com/v1/audio/transcriptions";

    private static final String WHISPER_MODEL = "nvidia/whisper-large-v3";

    private final String apiKey;
    private final RestTemplate restTemplate;

    public SpeechToTextService(@Value("${spring.ai.openai.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restTemplate = new RestTemplate();
    }

    // ─────────────────────────────── Public API ───────────────────────────────

    /**
     * Transcribes a raw PCM audio segment.
     *
     * <p>The bytes are sent as a WAV file (with a minimal WAV header) to ensure
     * compatibility with the Whisper endpoint.
     *
     * @param audioBytes raw 16-bit PCM mono 16kHz bytes
     * @return the transcribed text, or {@code null} if transcription fails
     */
    public String transcribe(byte[] audioBytes) {
        try {
            byte[] wavBytes = addWavHeader(audioBytes, 16_000, 1, 16);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(apiKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("model", WHISPER_MODEL);
            body.add("file", new NamedByteArrayResource(wavBytes, "audio.wav"));

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(WHISPER_URL, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object text = response.getBody().get("text");
                return text != null ? text.toString().trim() : null;
            }

            log.warn("Whisper API returned non-2xx status: {}", response.getStatusCode());
            return null;

        } catch (Exception e) {
            log.error("SpeechToTextService error: {}", e.getMessage(), e);
            return null;
        }
    }

    // ─────────────────────────────── Helpers ─────────────────────────────────

    /**
     * Builds a minimal 44-byte WAV header and prepends it to the raw PCM bytes.
     *
     * @param pcm        raw PCM audio data
     * @param sampleRate e.g. 16000
     * @param channels   1 for mono
     * @param bitDepth   16
     */
    private byte[] addWavHeader(byte[] pcm, int sampleRate, int channels, int bitDepth) {
        int dataSize = pcm.length;
        int byteRate = sampleRate * channels * (bitDepth / 8);
        int blockAlign = channels * (bitDepth / 8);

        byte[] header = new byte[44];

        // RIFF chunk
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        writeInt32LE(header, 4, 36 + dataSize);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';

        // fmt sub-chunk
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        writeInt32LE(header, 16, 16); // sub-chunk size
        writeInt16LE(header, 20, 1); // PCM format
        writeInt16LE(header, 22, channels);
        writeInt32LE(header, 24, sampleRate);
        writeInt32LE(header, 28, byteRate);
        writeInt16LE(header, 32, blockAlign);
        writeInt16LE(header, 34, bitDepth);

        // data sub-chunk
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        writeInt32LE(header, 40, dataSize);

        byte[] wav = new byte[44 + dataSize];
        System.arraycopy(header, 0, wav, 0, 44);
        System.arraycopy(pcm, 0, wav, 44, dataSize);
        return wav;
    }

    private void writeInt32LE(byte[] buf, int offset, int value) {
        buf[offset] = (byte) value;
        buf[offset + 1] = (byte) (value >> 8);
        buf[offset + 2] = (byte) (value >> 16);
        buf[offset + 3] = (byte) (value >> 24);
    }

    private void writeInt16LE(byte[] buf, int offset, int value) {
        buf[offset] = (byte) value;
        buf[offset + 1] = (byte) (value >> 8);
    }

    /**
     * ByteArrayResource with a custom filename — required for multipart file uploads.
     */
    private static class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
