package io.gsp26se16.moni.ai.writing.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Loads prompt templates from a versioned filesystem.
 * <p>
 * Resolution order for a prompt "writing/phase3_cc.txt":
 * <ol>
 *   <li>External dir: {@code {baseDir}/{activeVersion}/writing/phase3_cc.txt}</li>
 *   <li>Classpath fallback: {@code prompts/v1/writing/phase3_cc.txt}</li>
 * </ol>
 *
 * The active version per prompt is stored in:
 *   {@code {baseDir}/active_versions.json} (external) or
 *   {@code prompts/active_versions.json} (classpath fallback)
 *
 * Directory layout (external, configurable via app.prompts.base-dir):
 * <pre>
 *   {baseDir}/
 *     active_versions.json        – maps "skill/file.txt" → "vN"
 *     v1/
 *       writing/
 *         phase1_parse.txt  ...
 *       speaking/
 *         phase1_fc.txt     ...
 *     v2/
 *       writing/
 *         phase3_cc.txt     (only files that were edited)
 * </pre>
 */
@Slf4j
@Service
public class PromptLoader {

    /** External prompts directory. If empty/null, falls back to classpath only. */
    @Value("${app.prompts.base-dir:}")
    private String baseDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Cache: resolved classpath/filesystem path → content */
    private final ConcurrentHashMap<String, String> contentCache = new ConcurrentHashMap<>();

    /** Cached active version map. Refreshed on every cache-clear call. */
    private volatile Map<String, String> activeVersionsCache = null;

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Load a prompt by its skill-relative path, e.g. "writing/phase3_cc.txt".
     * Version resolution is applied automatically.
     */
    public String loadPrompt(String skillRelativePath) {
        return contentCache.computeIfAbsent(skillRelativePath, this::resolve);
    }

    /**
     * Load a prompt and substitute placeholders.
     * Each key {@code K} in {@code placeholders} replaces {@code {K}} in the template.
     */
    public String loadPrompt(String skillRelativePath, Map<String, String> placeholders) {
        String result = loadPrompt(skillRelativePath);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            result = result.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return result;
    }

    /**
     * Load a prompt, inject a writing rubric, then apply extra placeholders.
     *
     * @param skillRelativePath e.g. "writing/phase3_cc.txt"
     * @param rubricName        e.g. "CC" → rubrics/writing/CC.txt
     * @param extras            additional placeholder substitutions (may be empty)
     */
    public String loadPromptWithWritingRubric(String skillRelativePath, String rubricName, Map<String, String> extras) {
        Map<String, String> placeholders = new HashMap<>(extras);
        placeholders.put(rubricName + "_RUBRIC", getWritingRubric(rubricName));
        return loadPrompt(skillRelativePath, placeholders);
    }

    /**
     * Load a prompt, inject a speaking rubric, then apply extra placeholders.
     *
     * @param skillRelativePath e.g. "speaking/phase1_fc.txt"
     * @param criterion         e.g. "FC" → rubrics/speaking/FC.txt
     * @param extras            additional placeholder substitutions (may be empty)
     */
    public String loadPromptWithSpeakingRubric(String skillRelativePath, String criterion, Map<String, String> extras) {
        Map<String, String> placeholders = new HashMap<>(extras);
        placeholders.put(criterion + "_RUBRIC", getSpeakingRubric(criterion));
        return loadPrompt(skillRelativePath, placeholders);
    }

    // ─── Rubric loading ───────────────────────────────────────────────────────

    /** @param name TA | CC | LR | GRA | TR */
    public String getWritingRubric(String name) {
        return contentCache.computeIfAbsent(
                "rubric/writing/" + name.toUpperCase(),
                k -> readClasspath("rubrics/writing/" + name.toUpperCase() + ".txt"));
    }

    /** @param criterion FC | GRA | LR | PR */
    public String getSpeakingRubric(String criterion) {
        return contentCache.computeIfAbsent(
                "rubric/speaking/" + criterion.toUpperCase(),
                k -> readClasspath("rubrics/speaking/" + criterion.toUpperCase() + ".txt"));
    }

    // ─── Cache management ─────────────────────────────────────────────────────

    /**
     * Invalidate the cache for a specific prompt path after it has been updated.
     * Called by PromptManagementService after writing a new version.
     *
     * @param skillRelativePath e.g. "writing/phase3_cc.txt"
     */
    public void invalidateCache(String skillRelativePath) {
        contentCache.remove(skillRelativePath);
        activeVersionsCache = null;
        log.info("Cache invalidated for prompt: {}", skillRelativePath);
    }

    /** Invalidate all cached prompts and version info. */
    public void invalidateAll() {
        contentCache.clear();
        activeVersionsCache = null;
        log.info("All prompt caches cleared.");
    }

    // ─── Resolution ───────────────────────────────────────────────────────────

    /**
     * Resolve the content for a skill-relative path.
     * 1. Try external baseDir/{activeVersion}/{skillRelativePath}
     * 2. Fallback to classpath prompts/v1/{skillRelativePath}
     */
    private String resolve(String skillRelativePath) {
        // 1. If external dir is configured, try that first
        if (baseDir != null && !baseDir.isBlank()) {
            String activeVersion = getActiveVersion(skillRelativePath);
            Path externalPath = Paths.get(baseDir, activeVersion, skillRelativePath);
            if (Files.exists(externalPath)) {
                try {
                    log.debug("Loading prompt from external: {}", externalPath);
                    return Files.readString(externalPath, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    log.warn("Failed to read external prompt {}: {}", externalPath, e.getMessage());
                }
            }
        }

        // 2. Fallback to classpath
        String classpathPath = "prompts/v1/" + skillRelativePath;
        log.debug("Loading prompt from classpath: {}", classpathPath);
        return readClasspath(classpathPath);
    }

    /**
     * Get the active version for a given skill-relative path.
     * Reads from external active_versions.json first, then classpath.
     */
    private String getActiveVersion(String skillRelativePath) {
        Map<String, String> versions = loadActiveVersions();
        return versions.getOrDefault(skillRelativePath, "v1");
    }

    private Map<String, String> loadActiveVersions() {
        if (activeVersionsCache != null) return activeVersionsCache;

        // Try external first
        if (baseDir != null && !baseDir.isBlank()) {
            Path externalConfig = Paths.get(baseDir, "active_versions.json");
            if (Files.exists(externalConfig)) {
                try {
                    String json = Files.readString(externalConfig, StandardCharsets.UTF_8);
                    activeVersionsCache = objectMapper.readValue(json, new TypeReference<>() {});
                    return activeVersionsCache;
                } catch (IOException e) {
                    log.warn("Failed to read external active_versions.json: {}", e.getMessage());
                }
            }
        }

        // Fallback to classpath
        try {
            String json = readClasspath("prompts/active_versions.json");
            activeVersionsCache = objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("No active_versions.json found, using v1 for all: {}", e.getMessage());
            activeVersionsCache = new HashMap<>();
        }
        return activeVersionsCache;
    }

    // ─── I/O ──────────────────────────────────────────────────────────────────

    private String readClasspath(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            log.debug("Loading classpath resource: {}", path);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load classpath resource: " + path, e);
        }
    }
}
