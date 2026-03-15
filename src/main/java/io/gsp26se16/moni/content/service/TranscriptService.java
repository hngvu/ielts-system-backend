package io.gsp26se16.moni.content.service;

import java.util.List;
import java.util.Map;

public interface TranscriptService {
    List<Map<String, Object>> transcribeAudio(String audioUrl);
}
