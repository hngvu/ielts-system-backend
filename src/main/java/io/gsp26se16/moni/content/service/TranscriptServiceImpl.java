package io.gsp26se16.moni.content.service;

import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TranscriptServiceImpl implements TranscriptService {

    @Value("${assemblyai.api-key}")
    private String apiKey;

    @Value("${assemblyai.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> transcribeAudio(String audioUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Step 1: Submit transcription request with language detection + speaker labels
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("audio_url", audioUrl);
        requestBody.put("speaker_labels", true);
        requestBody.put("language_detection", true);
        requestBody.put("speech_models", List.of("universal-3-pro", "universal-2"));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map<String, Object>> submitResponse = restTemplate.exchange(
                baseUrl + "/transcript", HttpMethod.POST, entity, (Class<Map<String, Object>>) (Class<?>) Map.class);

        Map<String, Object> submitBody = submitResponse.getBody();
        if (submitBody == null) {
            throw new RuntimeException("AssemblyAI returned empty response");
        }
        String transcriptId = (String) submitBody.get("id");
        log.info("AssemblyAI transcript submitted: {}", transcriptId);

        // Step 2: Poll for completion (3s intervals, max 5 minutes)
        HttpEntity<Void> pollEntity = new HttpEntity<>(headers);
        int maxRetries = 100;
        for (int i = 0; i < maxRetries; i++) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Transcript polling interrupted");
            }

            ResponseEntity<Map<String, Object>> pollResponse = restTemplate.exchange(
                    baseUrl + "/transcript/" + transcriptId, HttpMethod.GET, pollEntity, (Class<Map<String, Object>>)
                            (Class<?>) Map.class);
            Map<String, Object> result = pollResponse.getBody();
            if (result == null) continue;

            String status = (String) result.get("status");
            if ("completed".equals(status)) {
                log.info("AssemblyAI transcript completed: {}", transcriptId);
                return fetchSentences(transcriptId, headers, result);
            }
            if ("error".equals(status)) {
                String error = (String) result.get("error");
                throw new RuntimeException("AssemblyAI transcription failed: " + error);
            }
            log.debug("Transcript status: {} (attempt {}/{})", status, i + 1, maxRetries);
        }
        throw new RuntimeException("AssemblyAI transcription timed out after 5 minutes");
    }

    /**
     * Fetch sentence-level timestamps from AssemblyAI /sentences endpoint.
     * Falls back to utterance parsing if sentences API fails.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchSentences(
            String transcriptId, HttpHeaders headers, Map<String, Object> transcriptResult) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl + "/transcript/" + transcriptId + "/sentences", HttpMethod.GET, entity, (Class<
                                    Map<String, Object>>)
                            (Class<?>) Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null) {
                List<Map<String, Object>> sentences = (List<Map<String, Object>>) body.get("sentences");
                if (sentences != null && !sentences.isEmpty()) {
                    log.info("Using sentence-level timestamps ({} sentences)", sentences.size());
                    return parseSentences(sentences);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch sentences, falling back to utterances: {}", e.getMessage());
        }
        return parseUtterances(transcriptResult);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseSentences(List<Map<String, Object>> sentences) {
        List<Map<String, Object>> segments = new ArrayList<>();
        int idx = 0;
        for (Map<String, Object> sent : sentences) {
            Map<String, Object> segment = new HashMap<>();
            int startMs = ((Number) sent.get("start")).intValue();
            int endMs = ((Number) sent.get("end")).intValue();
            segment.put("id", "sent_" + idx);
            segment.put("startTime", startMs / 1000.0);
            segment.put("endTime", endMs / 1000.0);
            segment.put("text", sent.get("text"));
            // sentences API includes speaker if speaker_labels was enabled
            Object speaker = sent.get("speaker");
            if (speaker != null) {
                segment.put("speaker", "Speaker " + speaker);
            }
            segments.add(segment);
            idx++;
        }
        return segments;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseUtterances(Map<String, Object> result) {
        List<Map<String, Object>> utterances = (List<Map<String, Object>>) result.get("utterances");
        if (utterances == null || utterances.isEmpty()) {
            // Fallback to words if no utterances
            return List.of(Map.of(
                    "startTime",
                    0.0,
                    "endTime",
                    0.0,
                    "text",
                    (String) result.getOrDefault("text", ""),
                    "speaker",
                    "Speaker"));
        }

        List<Map<String, Object>> segments = new ArrayList<>();
        int idx = 0;
        for (Map<String, Object> utt : utterances) {
            Map<String, Object> segment = new HashMap<>();
            // AssemblyAI returns milliseconds, convert to seconds
            int startMs = (int) utt.get("start");
            int endMs = (int) utt.get("end");
            segment.put("id", "seg_" + idx);
            segment.put("startTime", startMs / 1000.0);
            segment.put("endTime", endMs / 1000.0);
            segment.put("text", utt.get("text"));
            segment.put("speaker", "Speaker " + utt.get("speaker"));
            segments.add(segment);
            idx++;
        }
        return segments;
    }
}
