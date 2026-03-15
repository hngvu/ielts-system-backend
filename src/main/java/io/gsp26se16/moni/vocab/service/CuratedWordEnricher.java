package io.gsp26se16.moni.vocab.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import io.gsp26se16.moni.vocab.entity.CuratedWord;
import io.gsp26se16.moni.vocab.entity.Dictionary;
import io.gsp26se16.moni.vocab.repository.CuratedWordRepository;
import io.gsp26se16.moni.vocab.repository.DictionaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CuratedWordEnricher {

    private static final String FREE_DICT_URL = "https://api.dictionaryapi.dev/api/v2/entries/en/";
    private static final String MYMEMORY_URL = "https://api.mymemory.translated.net/get?q=%s&langpair=en|vi";

    private final CuratedWordRepository curatedWordRepository;
    private final DictionaryRepository dictionaryRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger enrichedCount = new AtomicInteger(0);
    private int totalToEnrich = 0;

    public Map<String, Object> getStatus() {
        long total = curatedWordRepository.count();
        long enriched = curatedWordRepository.countByMeaningIsNotNull();
        return Map.of(
                "running",
                running.get(),
                "total",
                total,
                "enriched",
                enriched,
                "remaining",
                total - enriched,
                "currentBatchProgress",
                enrichedCount.get() + "/" + totalToEnrich);
    }

    @Async
    public void startEnrichment() {
        if (!running.compareAndSet(false, true)) {
            log.info("Enrichment already running, skipping.");
            return;
        }

        try {
            List<CuratedWord> words = curatedWordRepository.findUnenriched();
            totalToEnrich = words.size();
            enrichedCount.set(0);
            log.info("Starting enrichment for {} words", totalToEnrich);

            for (CuratedWord cw : words) {
                try {
                    enrichSingleWord(cw);
                    curatedWordRepository.save(cw);
                    int count = enrichedCount.incrementAndGet();
                    if (count % 100 == 0) {
                        log.info("Enriched {}/{}", count, totalToEnrich);
                    }
                } catch (Exception e) {
                    log.warn("Failed to enrich '{}': {}", cw.getWord(), e.getMessage());
                }
                // Rate limit: ~4 req/sec to be safe with MyMemory
                Thread.sleep(250);
            }

            log.info("Enrichment complete: {}/{} words enriched", enrichedCount.get(), totalToEnrich);
        } catch (Exception e) {
            log.error("Enrichment failed: {}", e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }

    private void enrichSingleWord(CuratedWord cw) {
        // Step 1: Check Dictionary cache first
        Optional<Dictionary> cached = dictionaryRepository.findFirstByWordIgnoreCase(cw.getWord());
        if (cached.isPresent()) {
            copyFromDictionary(cw, cached.get());
            return;
        }

        // Step 2: Call Free Dictionary API for English data
        fetchFreeDictionary(cw);

        // Step 3: Call MyMemory for Vietnamese meaning
        if (cw.getDefinition() != null && !cw.getDefinition().isBlank()) {
            fetchVietnameseMeaning(cw);
        }
    }

    @SuppressWarnings("unchecked")
    private void fetchFreeDictionary(CuratedWord cw) {
        try {
            ResponseEntity<List> response = restTemplate.getForEntity(FREE_DICT_URL + cw.getWord(), List.class);
            if (!response.getStatusCode().is2xxSuccessful()
                    || response.getBody() == null
                    || response.getBody().isEmpty()) {
                return;
            }

            Map<String, Object> entry = (Map<String, Object>) response.getBody().get(0);

            // Phonetic
            if (cw.getPhonetic() == null) {
                cw.setPhonetic(getString(entry, "phonetic"));
            }

            // Audio from phonetics array
            List<Map<String, Object>> phonetics = (List<Map<String, Object>>) entry.get("phonetics");
            if (phonetics != null) {
                for (Map<String, Object> p : phonetics) {
                    String audio = getString(p, "audio");
                    if (audio != null && !audio.isBlank()) {
                        cw.setAudioUrl(audio);
                        if (cw.getPhonetic() == null) {
                            cw.setPhonetic(getString(p, "text"));
                        }
                        break;
                    }
                }
            }

            // Definition + example from meanings
            List<Map<String, Object>> meanings = (List<Map<String, Object>>) entry.get("meanings");
            if (meanings != null && !meanings.isEmpty()) {
                Map<String, Object> firstMeaning = meanings.get(0);
                if (cw.getPos() == null) {
                    cw.setPos(getString(firstMeaning, "partOfSpeech"));
                }
                List<Map<String, Object>> definitions = (List<Map<String, Object>>) firstMeaning.get("definitions");
                if (definitions != null && !definitions.isEmpty()) {
                    Map<String, Object> def = definitions.get(0);
                    cw.setDefinition(getString(def, "definition"));
                    cw.setExample(getString(def, "example"));
                }
            }
        } catch (Exception e) {
            log.debug("Free Dictionary miss for '{}': {}", cw.getWord(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void fetchVietnameseMeaning(CuratedWord cw) {
        try {
            // Translate the definition to Vietnamese
            String textToTranslate = cw.getDefinition();
            if (textToTranslate.length() > 200) {
                textToTranslate = textToTranslate.substring(0, 200);
            }
            String url = String.format(MYMEMORY_URL, java.net.URLEncoder.encode(textToTranslate, "UTF-8"));
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Map<String, Object> responseData = (Map<String, Object>) body.get("responseData");
                if (responseData != null) {
                    String translated = getString(responseData, "translatedText");
                    if (translated != null && !translated.isBlank() && !translated.equalsIgnoreCase(textToTranslate)) {
                        cw.setMeaning(translated);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("MyMemory translation failed for '{}': {}", cw.getWord(), e.getMessage());
        }
    }

    private void copyFromDictionary(CuratedWord cw, Dictionary dict) {
        if (cw.getPhonetic() == null && dict.getPhonetic() != null) cw.setPhonetic(dict.getPhonetic());
        if (cw.getDefinition() == null && dict.getDefinition() != null) cw.setDefinition(dict.getDefinition());
        if (cw.getExample() == null && dict.getExample() != null) cw.setExample(dict.getExample());
        if (cw.getMeaning() == null && dict.getMeaning() != null) cw.setMeaning(dict.getMeaning());
        if (cw.getAudioUrl() == null && dict.getAudioUrl() != null) cw.setAudioUrl(dict.getAudioUrl());
        if (cw.getPos() == null && dict.getPos() != null) cw.setPos(dict.getPos());
    }

    private String getString(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
