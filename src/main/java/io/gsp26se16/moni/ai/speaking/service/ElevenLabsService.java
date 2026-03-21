package io.gsp26se16.moni.ai.speaking.service;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gọi ElevenLabs TTS và stream audio về client qua WebSocket.
 *
 * Protocol gửi về client:
 *   { "type": "audio_chunk", "data": "<base64 mp3>" }  — nhiều lần
 *   { "type": "audio_end" }                             — báo xong
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElevenLabsService {

    private static final String TTS_URL = "https://api.elevenlabs.io/v1/text-to-speech/%s/stream";
    private static final int CHUNK_SIZE = 4096;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${elevenlabs.api-key}")
    private String apiKey;

    @Value("${elevenlabs.voice-id}")
    private String voiceId;

    @Value("${elevenlabs.model-id:eleven_multilingual_v2}")
    private String modelId;

    /**
     * Convert text → MP3 và stream về client qua WebSocket.
     * Nếu TTS lỗi thì log và bỏ qua — exam vẫn tiếp tục.
     */
    public void streamToClient(String text, WebSocketSession wsSession) {
        if (text == null || text.isBlank() || !wsSession.isOpen()) return;

        try {
            byte[] audioBytes = callElevenLabs(text);
            sendChunks(audioBytes, wsSession);
            sendAudioEnd(wsSession);
        } catch (Exception e) {
            log.error("ElevenLabs TTS lỗi: {}", e.getMessage());
        }
    }

    // ─────────────────────────────── Private ─────────────────────────────────

    private byte[] callElevenLabs(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("xi-api-key", apiKey);
        headers.set("Accept", "audio/mpeg");

        Map<String, Object> body = Map.of(
                "text", text,
                "model_id", modelId,
                "voice_settings",
                        Map.of(
                                "stability", 0.5,
                                "similarity_boost", 0.75));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<byte[]> response =
                restTemplate.exchange(String.format(TTS_URL, voiceId), HttpMethod.POST, entity, byte[].class);

        byte[] audio = response.getBody();
        if (audio == null || audio.length == 0) throw new RuntimeException("ElevenLabs trả về audio rỗng");
        log.info("ElevenLabs: {} chars → {} bytes", text.length(), audio.length);
        return audio;
    }

    private void sendChunks(byte[] audio, WebSocketSession ws) throws IOException {
        int offset = 0;
        while (offset < audio.length && ws.isOpen()) {
            int end = Math.min(offset + CHUNK_SIZE, audio.length);
            byte[] chunk = new byte[end - offset];
            System.arraycopy(audio, offset, chunk, 0, chunk.length);

            String msg = objectMapper.writeValueAsString(
                    Map.of("type", "audio_chunk", "data", Base64.getEncoder().encodeToString(chunk)));
            ws.sendMessage(new TextMessage(msg));
            offset += CHUNK_SIZE;
        }
    }

    private void sendAudioEnd(WebSocketSession ws) throws IOException {
        if (!ws.isOpen()) return;
        ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type", "audio_end"))));
    }
}
