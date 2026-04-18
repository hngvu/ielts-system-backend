package io.gsp26se16.moni.vocab.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.vocab.dto.VocabSearchResponse;
import io.gsp26se16.moni.vocab.entity.Dictionary;
import io.gsp26se16.moni.vocab.repository.DictionaryRepository;
import io.gsp26se16.moni.vocab.repository.VocabRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class VocabSearchServiceImpl implements VocabSearchService {

    private static final String FREE_DICT_URL = "https://api.dictionaryapi.dev/api/v2/entries/en/";

    private final DictionaryRepository dictionaryRepository;
    private final VocabRepository vocabRepository;
    private final VocabLookupService vocabLookupService;
    private final VocabAuthHelper authHelper;
    private final RestTemplate restTemplate = new RestTemplate();

    public VocabSearchResponse searchWord(String credentialId, String query) {
        String word = query.trim().toLowerCase();
        Users user = credentialId != null ? authHelper.getUser(credentialId) : null;

        // Step 1: Check Dictionary cache
        Dictionary dict = dictionaryRepository.findFirstByWordIgnoreCase(word).orElseGet(() -> {
            Dictionary d = buildFromFreeDictionary(word);
            enrichWithVietnamese(d);
            return dictionaryRepository.save(d);
        });

        // Step 5: Check if user already saved this word
        boolean saved = user != null && vocabRepository.existsByUserIdAndWord(user.getId(), word);

        return VocabSearchResponse.builder()
                .word(dict.getWord())
                .phonetic(dict.getPhonetic())
                .pos(dict.getPos())
                .definition(dict.getDefinition())
                .example(dict.getExample())
                .meaning(dict.getMeaning())
                .audioUrl(dict.getAudioUrl())
                .isSaved(saved)
                .dictionaryId(dict.getId())
                .build();
    }

    // Step 2: Call Free Dictionary API
    @SuppressWarnings("unchecked")
    private Dictionary buildFromFreeDictionary(String word) {
        Dictionary d = new Dictionary();
        d.setWord(word);
        try {
            ResponseEntity<List> response = restTemplate.getForEntity(FREE_DICT_URL + word, List.class);
            if (response.getStatusCode().is2xxSuccessful()
                    && response.getBody() != null
                    && !response.getBody().isEmpty()) {
                Map<String, Object> entry =
                        (Map<String, Object>) response.getBody().get(0);
                d.setPhonetic(getString(entry, "phonetic"));

                // Extract audio from phonetics array
                List<Map<String, Object>> phonetics = (List<Map<String, Object>>) entry.get("phonetics");
                if (phonetics != null) {
                    for (Map<String, Object> p : phonetics) {
                        String audio = getString(p, "audio");
                        if (audio != null && !audio.isBlank()) {
                            d.setAudioUrl(audio);
                            if (d.getPhonetic() == null) d.setPhonetic(getString(p, "text"));
                            break;
                        }
                    }
                }

                // Extract first definition + example from meanings
                List<Map<String, Object>> meanings = (List<Map<String, Object>>) entry.get("meanings");
                if (meanings != null && !meanings.isEmpty()) {
                    Map<String, Object> firstMeaning = meanings.get(0);
                    d.setPos(getString(firstMeaning, "partOfSpeech"));
                    List<Map<String, Object>> definitions = (List<Map<String, Object>>) firstMeaning.get("definitions");
                    if (definitions != null && !definitions.isEmpty()) {
                        Map<String, Object> def = definitions.get(0);
                        d.setDefinition(getString(def, "definition"));
                        d.setExample(getString(def, "example"));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Free Dictionary API failed for '{}': {}", word, e.getMessage());
        }
        return d;
    }

    // Step 3: Enrich with Vietnamese meaning via Gemini
    private void enrichWithVietnamese(Dictionary d) {
        if (d.getMeaning() != null && !d.getMeaning().isBlank()) return;
        try {
            var lookup = vocabLookupService.lookupWord(d.getWord());
            if (d.getMeaning() == null) d.setMeaning(lookup.getMeaning());
            if (d.getPhonetic() == null) d.setPhonetic(lookup.getPhonetic());
            if (d.getPos() == null) d.setPos(lookup.getPos());
        } catch (Exception e) {
            log.warn("Gemini enrichment failed for '{}': {}", d.getWord(), e.getMessage());
        }
    }

    private String getString(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
