package io.gsp26se16.moni.vocab.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.gsp26se16.moni.vocab.dto.VocabLookupResponse;
import io.gsp26se16.moni.vocab.entity.Dictionary;
import io.gsp26se16.moni.vocab.repository.DictionaryRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VocabLookupService {

    private final DictionaryRepository dictionaryRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${GEMINI_API_KEY:}")
    private String apiKey;

    @Value("${GEMINI_MODEL:gemini-2.0-flash}")
    private String model;

    public VocabLookupResponse lookupWord(String word, String sentence) {
        // 1. Check cache first
        Optional<Dictionary> cached = dictionaryRepository.findFirstByWordIgnoreCase(word);
        if (cached.isPresent()) {
            return mapToResponse(cached.get());
        }

        // 2. Call Gemini API
        Map<String, Object> geminiResult = callGeminiForVocab(word, sentence);

        // 3. Save to DB
        Dictionary entry = new Dictionary();
        entry.setWord(getString(geminiResult, "word", word.toLowerCase()));
        entry.setPhonetic(getString(geminiResult, "phonetic", ""));
        entry.setPos(getString(geminiResult, "pos", ""));
        entry.setMeaning(getString(geminiResult, "meaning", ""));
        entry.setExplanation(getString(geminiResult, "explanation", ""));
        entry.setCollocation(getString(geminiResult, "collocation", ""));

        List<String> exampleList = extractExamples(geminiResult);
        try {
            entry.setExamples(objectMapper.writeValueAsString(exampleList));
        } catch (Exception e) {
            entry.setExamples("[]");
        }

        dictionaryRepository.save(entry);

        // 4. Return response
        VocabLookupResponse response = new VocabLookupResponse();
        response.setWord(entry.getWord());
        response.setPhonetic(entry.getPhonetic());
        response.setPos(entry.getPos());
        response.setMeaning(entry.getMeaning());
        response.setExplanation(entry.getExplanation());
        response.setCollocation(entry.getCollocation());
        response.setExamples(exampleList);
        return response;
    }

    private VocabLookupResponse mapToResponse(Dictionary dict) {
        List<String> exampleList = new ArrayList<>();
        if (dict.getExamples() != null && !dict.getExamples().isBlank()) {
            try {
                exampleList = objectMapper.readValue(dict.getExamples(), new TypeReference<List<String>>() {});
            } catch (Exception ignored) {
                // fallback: return empty list
            }
        }

        return VocabLookupResponse.builder()
                .word(dict.getWord())
                .phonetic(dict.getPhonetic())
                .pos(dict.getPos())
                .meaning(dict.getMeaning())
                .explanation(dict.getExplanation())
                .collocation(dict.getCollocation())
                .examples(exampleList)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callGeminiForVocab(String word, String sentence) {
        String ctx = (sentence != null && !sentence.isBlank()) ? sentence : "(no sentence provided)";
        String prompt = String.format(
                """
				You are an English-Vietnamese dictionary for IELTS learners.
				Given the word and the sentence it appears in, return VALID JSON ONLY with these exact keys:
				- word: the word in lowercase
				- phonetic: IPA pronunciation (e.g. "/\\u02cck\\u0252mpj\\u028a\\u02c8te\\u026a\\u0283n/")
				- pos: part of speech (noun, verb, adjective, etc.)
				- meaning: Vietnamese translation (short, 1-3 words)
				- explanation: Vietnamese explanation of how this word is used in the given sentence context (2-3 sentences)
				- collocation: one common collocation with this word
				- examples: array of exactly 2 example sentences using this word, each followed by Vietnamese translation in parentheses

				Word: %s
				Sentence: %s
				""",
                word, ctx);

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
                String responseText = extractTextFromGeminiResponse(response.getBody());
                return parseJsonFromResponse(responseText);
            }

            throw new RuntimeException("Gemini API returned non-OK status");
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("429")) {
                throw new RuntimeException("Đã vượt giới hạn API. Vui lòng thử lại sau 30 giây.", e);
            }
            throw new RuntimeException(
                    "Lỗi khi gọi AI: " + (msg != null ? msg.substring(0, Math.min(msg.length(), 100)) : "unknown"), e);
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonFromResponse(String text) {
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
            return objectMapper.readValue(text, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON from Gemini response: " + text, e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractExamples(Map<String, Object> result) {
        Object raw = result.get("examples");
        if (raw instanceof List) {
            List<String> list = new ArrayList<>();
            for (Object item : (List<?>) raw) {
                if (item != null) list.add(item.toString());
            }
            return list;
        }
        return new ArrayList<>();
    }

    private String getString(Map<String, Object> map, String key, String fallback) {
        Object val = map.get(key);
        return val != null ? val.toString() : fallback;
    }
}
