package io.gsp26se16.moni.ai.writing.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GeminiVisionClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final PromptLoader promptLoader;

    @Value("${GEMINI_API_KEY:}")
    private String apiKey;

    @Value("${GEMINI_MODEL:gemini-3-flash-preview}")
    private String model;

    /**
     * Analyzes a chart image using Google Gemini Vision
     *
     * @param imageBase64 Base64-encoded image
     * @return Chart analysis in JSON format
     */
    public Map<String, Object> analyzeChart(String imageBase64) {
        String prompt = promptLoader.loadPrompt("vision/analyze_chart.txt");

        try {
            String url = String.format(
                    "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", model, apiKey);

            Map<String, Object> requestBody = Map.of(
                    "contents",
                    List.of(Map.of(
                            "parts",
                            List.of(
                                    Map.of("text", prompt),
                                    Map.of("inline_data", Map.of("mime_type", "image/png", "data", imageBase64))))));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // Extract text from Gemini response
                String responseText = extractTextFromGeminiResponse(response.getBody());

                // Parse JSON from response
                return parseJsonFromResponse(responseText);
            }

            throw new RuntimeException("Failed to analyze chart with Gemini Vision");

        } catch (Exception e) {
            throw new RuntimeException("Gemini Vision API call failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromGeminiResponse(Map<String, Object> response) {
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
            throw new RuntimeException("Failed to extract text from Gemini response", e);
        }
        throw new RuntimeException("No text found in Gemini response");
    }

    private Map<String, Object> parseJsonFromResponse(String text) {
        try {
            // Find JSON in response (Gemini sometimes adds markdown)
            if (text.contains("{")) {
                text = text.substring(text.indexOf("{"), text.lastIndexOf("}") + 1);
            }
            return objectMapper.readValue(text, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON from Gemini response: " + text, e);
        }
    }
}
