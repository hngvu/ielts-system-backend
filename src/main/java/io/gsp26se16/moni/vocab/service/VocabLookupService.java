package io.gsp26se16.moni.vocab.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.gsp26se16.moni.vocab.dto.VocabLookupResponse;
import io.gsp26se16.moni.vocab.entity.Dictionary;
import io.gsp26se16.moni.vocab.repository.DictionaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class VocabLookupService {

    private final DictionaryRepository dictionaryRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String DOL_API =
            "https://apigateway.dolenglish.vn/public/search-transform/api/dictionary/result";
    private static final String DOL_TTS_API =
            "https://apigateway.dolenglish.vn/public/tts/api/generate-with-download-url";

    public VocabLookupResponse lookupWord(String word, String sentence) {
        Optional<Dictionary> cached = dictionaryRepository.findFirstByWordIgnoreCase(word);
        if (cached.isPresent()) {
            return mapToResponse(cached.get());
        }

        VocabLookupResponse result = callDolApi(word);
        if (result != null) {
            saveToDictionary(result);
            return result;
        }

        return VocabLookupResponse.builder()
                .word(word.toLowerCase())
                .meaning("Không tìm thấy từ này")
                .examples(List.of())
                .build();
    }

    @SuppressWarnings("unchecked")
    private VocabLookupResponse callDolApi(String word) {
        try {
            String url = DOL_API + "?query=" + word.trim().toLowerCase() + "&size=1&page=1";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getBody() == null) return null;

            List<Map<String, Object>> data =
                    (List<Map<String, Object>>) response.getBody().get("data");
            if (data == null || data.isEmpty()) return null;

            Map<String, Object> entry = data.get(0);
            String dictWord = str(entry, "word", word.toLowerCase());
            if (!dictWord.equalsIgnoreCase(word.trim())) return null;

            String phonetic = str(entry, "phoneticAm", str(entry, "phoneticBr", ""));
            String pos = str(entry, "wordType", "");
            String meaning = str(entry, "meaningVi", "");
            String explanation = str(entry, "explanationVi", str(entry, "meaningEn", ""));

            List<String> examples = new ArrayList<>();
            Object exObj = entry.get("examples");
            if (exObj instanceof List) {
                for (Object ex : (List<?>) exObj) {
                    if (ex instanceof Map) {
                        Map<String, Object> exMap = (Map<String, Object>) ex;
                        String en = str(exMap, "en", "");
                        String vi = str(exMap, "vi", "");
                        if (!en.isBlank()) examples.add(vi.isBlank() ? en : en + " (" + vi + ")");
                    }
                }
            }

            String collocation = "";
            Object colObj = entry.get("collocations");
            if (colObj instanceof List && !((List<?>) colObj).isEmpty()) {
                Object first = ((List<?>) colObj).get(0);
                if (first instanceof Map) collocation = str((Map<String, Object>) first, "text", "");
            }

            String audioUrl = fetchTtsUrl(dictWord, phonetic);

            return VocabLookupResponse.builder()
                    .word(dictWord)
                    .phonetic(phonetic.isBlank() ? "" : "/" + phonetic + "/")
                    .pos(pos)
                    .meaning(meaning)
                    .explanation(explanation)
                    .collocation(collocation)
                    .examples(examples)
                    .audioUrl(audioUrl)
                    .build();
        } catch (Exception e) {
            log.warn("DOL API failed for '{}': {}", word, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String fetchTtsUrl(String word, String ipa) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = "[{\"word\":\"" + word + "\",\"ipa\":\"" + (ipa != null ? ipa : "") + "\"}]";
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<List> res = restTemplate.exchange(DOL_TTS_API, HttpMethod.POST, entity, List.class);
            if (res.getBody() != null && !res.getBody().isEmpty()) {
                Object first = res.getBody().get(0);
                if (first instanceof Map) return str((Map<String, Object>) first, "downloadUrl", null);
            }
        } catch (Exception e) {
            log.debug("TTS failed for '{}': {}", word, e.getMessage());
        }
        return null;
    }

    private void saveToDictionary(VocabLookupResponse r) {
        try {
            Dictionary entry = new Dictionary();
            entry.setWord(r.getWord());
            entry.setPhonetic(r.getPhonetic());
            entry.setPos(r.getPos());
            entry.setMeaning(r.getMeaning());
            entry.setExplanation(r.getExplanation());
            entry.setCollocation(r.getCollocation());
            entry.setAudioUrl(r.getAudioUrl());
            entry.setExamples(objectMapper.writeValueAsString(r.getExamples()));
            dictionaryRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to cache for '{}': {}", r.getWord(), e.getMessage());
        }
    }

    private VocabLookupResponse mapToResponse(Dictionary dict) {
        List<String> exampleList = new ArrayList<>();
        if (dict.getExamples() != null && !dict.getExamples().isBlank()) {
            try {
                exampleList = objectMapper.readValue(dict.getExamples(), new TypeReference<List<String>>() {});
            } catch (Exception ignored) {
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
                .audioUrl(dict.getAudioUrl())
                .build();
    }

    private String str(Map<String, Object> map, String key, String fallback) {
        Object val = map.get(key);
        return val != null ? val.toString() : fallback;
    }
}
