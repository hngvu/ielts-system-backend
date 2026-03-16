package io.gsp26se16.moni.vocab.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.gsp26se16.moni.vocab.dto.SentenceTranslateResponse;
import lombok.RequiredArgsConstructor;

/**
 * Translates an English sentence to Vietnamese using Gemini API.
 * Returns translation, explanation, and key grammar/vocabulary points.
 */
@Service
@RequiredArgsConstructor
public class SentenceTranslateService {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${GEMINI_API_KEY:}")
    private String apiKey;

    @Value("${GEMINI_MODEL:gemini-2.0-flash}")
    private String model;

    public SentenceTranslateResponse translate(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text không được để trống");
        }

        String prompt = String.format(
                """
				You are an English-Vietnamese language tutor for IELTS learners.
				Given the English sentence below, return VALID JSON ONLY with these exact keys:
				- translation: Vietnamese translation of the sentence (natural, fluent)
				- explanation: Brief Vietnamese explanation of the sentence meaning and usage (2-3 sentences)
				- keyPoints: Key grammar structures or vocabulary in this sentence, in Vietnamese (1-2 sentences)

				Sentence: %s
				""",
                text);

        try {
            String url = String.format(
                    "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", model, apiKey);

            Map<String, Object> requestBody =
                    Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String responseText = extractText(response.getBody());
                return parseResponse(responseText);
            }

            throw new RuntimeException("Gemini API trả về lỗi");
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("429")) {
                throw new RuntimeException("Đã vượt giới hạn API. Vui lòng thử lại sau 30 giây.", e);
            }
            throw new RuntimeException(
                    "Lỗi khi dịch câu: " + (msg != null ? msg.substring(0, Math.min(msg.length(), 100)) : "unknown"),
                    e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> content =
                        (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    return (String) parts.get(0).get("text");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Không thể đọc kết quả từ Gemini", e);
        }
        throw new RuntimeException("Không có text trong kết quả Gemini");
    }

    @SuppressWarnings("unchecked")
    private SentenceTranslateResponse parseResponse(String text) {
        try {
            // Strip markdown code fences if present
            if (text.contains("```")) {
                int start = text.indexOf("{");
                int end = text.lastIndexOf("}");
                if (start >= 0 && end > start) {
                    text = text.substring(start, end + 1);
                }
            } else if (text.contains("{")) {
                text = text.substring(text.indexOf("{"), text.lastIndexOf("}") + 1);
            }

            Map<String, Object> map = objectMapper.readValue(text, Map.class);
            return SentenceTranslateResponse.builder()
                    .translation(getString(map, "translation"))
                    .explanation(getString(map, "explanation"))
                    .keyPoints(getString(map, "keyPoints"))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Không thể phân tích kết quả dịch: " + text, e);
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }
}
