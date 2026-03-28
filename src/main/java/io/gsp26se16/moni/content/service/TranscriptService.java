package io.gsp26se16.moni.content.service;

import java.util.List;
import java.util.Map;

public interface TranscriptService {
    List<Map<String, Object>> transcribeAudio(String audioUrl);

    /** Upload raw audio bytes to AssemblyAI, transcribe, return plain text. */
    String transcribeAudioBytes(byte[] audioBytes, String contentType);
}
