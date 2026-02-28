package io.gsp26se16.moni.ai.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads prompt templates and rubric files from classpath resources.
 * <p>
 * Directory layout (src/main/resources):
 * 
 * <pre>
 *   prompts/           – prompt template text files
 *   rubrics/writing/   – TA.txt | CC.txt | LR.txt | GRA.txt | TR.txt
 *   rubrics/speaking/  – FC.txt | GRA.txt | LR.txt | PR.txt
 * </pre>
 * 
 * Each speaking rubric file contains all bands (4–8) in a single file.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptLoader {

    /** In-memory cache so each file is read from disk only once. */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    // ─────────────────────────────── Prompt loading ──────────────────────────────

    /**
     * Load a raw prompt template (no placeholder substitution).
     */
    public String loadPrompt(String filename) {
        return cache.computeIfAbsent("prompts/" + filename, this::readClasspath);
    }

    /**
     * Load a prompt template and replace the given placeholders.
     *
     * @param filename     e.g. "phase3_cc.txt"
     * @param placeholders key → value map; each key {@code K} replaces {@code {K}}
     */
    public String loadPrompt(String filename, Map<String, String> placeholders) {
        String result = loadPrompt(filename);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            result = result.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return result;
    }

    /**
     * Load a prompt template, inject a writing rubric, then apply extra
     * placeholders.
     * The rubric is injected by replacing {@code {NAME_RUBRIC}} in the template.
     *
     * @param filename   e.g. "phase3_cc.txt"
     * @param rubricName e.g. "CC" → rubrics/writing/CC.txt
     * @param extras     additional placeholder substitutions (may be empty)
     */
    public String loadPromptWithWritingRubric(String filename, String rubricName, Map<String, String> extras) {
        Map<String, String> placeholders = new HashMap<>(extras);
        placeholders.put(rubricName + "_RUBRIC", getWritingRubric(rubricName));
        return loadPrompt(filename, placeholders);
    }

    /**
     * Load a prompt template, inject a speaking rubric, then apply extra
     * placeholders.
     * The rubric is injected by replacing {@code {NAME_RUBRIC}} in the template.
     *
     * @param filename  e.g. "speaking.txt"
     * @param criterion e.g. "FC" → rubrics/speaking/FC.txt
     * @param extras    additional placeholder substitutions (may be empty)
     */
    public String loadPromptWithSpeakingRubric(String filename, String criterion, Map<String, String> extras) {
        Map<String, String> placeholders = new HashMap<>(extras);
        placeholders.put(criterion + "_RUBRIC", getSpeakingRubric(criterion));
        return loadPrompt(filename, placeholders);
    }

    // ──────────────────────────── Writing rubric loading ─────────────────────────

    /**
     * Get a writing rubric by criterion name.
     *
     * @param name TA | CC | LR | GRA | TR
     */
    public String getWritingRubric(String name) {
        return cache.computeIfAbsent("rubrics/writing/" + name.toUpperCase() + ".txt", this::readClasspath);
    }

    // ─────────────────────────── Speaking rubric loading ─────────────────────────

    /**
     * Get the full speaking rubric for a criterion (all bands 4–8 in one file).
     *
     * @param criterion FC | GRA | LR | PR
     */
    public String getSpeakingRubric(String criterion) {
        return cache.computeIfAbsent("rubrics/speaking/" + criterion.toUpperCase() + ".txt", this::readClasspath);
    }

    // ─────────────────────────────────── I/O ─────────────────────────────────────

    private String readClasspath(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            log.debug("Loading resource: {}", path);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource: " + path, e);
        }
    }
}
