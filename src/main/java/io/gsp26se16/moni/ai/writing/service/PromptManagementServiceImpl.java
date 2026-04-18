package io.gsp26se16.moni.ai.writing.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Admin service for managing prompt files.
 * <p>
 * Supports listing, reading, updating (creating a new version), and rollback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptManagementServiceImpl implements PromptManagementService {

    @Value("${app.prompts.base-dir:}")
    private String baseDir;

    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;

    /** Supported skills */
    private static final List<String> SKILLS = List.of("writing", "speaking", "vision", "vocab");

    /** Known prompt files per skill */
    private static final Map<String, List<String>> KNOWN_FILES = Map.of(
            "writing",
                    List.of(
                            "phase1_parse.txt",
                            "phase1_parse_task2.txt",
                            "phase2_ta.txt",
                            "phase2_tr.txt",
                            "phase3_cc.txt",
                            "phase4_lr.txt",
                            "phase5_gra.txt",
                            "phase7_feedback.txt",
                            "phase7_feedback_task2.txt"),
            "speaking",
                    List.of("phase1_fc.txt", "phase2_lr.txt", "phase3_gra.txt", "phase4_pr.txt", "phase5_feedback.txt"),
            "vision", List.of("analyze_chart.txt"),
            "vocab", List.of("quiz_generation.txt"));

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * List all prompts with their active version.
     */
    public List<Map<String, Object>> listAllPrompts() {
        Map<String, String> versions = loadActiveVersions();
        List<Map<String, Object>> result = new ArrayList<>();

        for (String skill : SKILLS) {
            List<String> files = KNOWN_FILES.getOrDefault(skill, List.of());
            for (String filename : files) {
                String key = skill + "/" + filename;
                String activeVersion = versions.getOrDefault(key, "v1");
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("skill", skill);
                item.put("filename", filename);
                item.put("path", key);
                item.put("activeVersion", activeVersion);
                item.put("availableVersions", getVersionList(skill, filename));
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Get the content of the currently active version of a prompt.
     */
    public String getActiveContent(String skill, String filename) {
        return promptLoader.loadPrompt(skill + "/" + filename);
    }

    /**
     * Get the content of a specific version of a prompt.
     */
    public String getVersionContent(String skill, String filename, String version) {
        // Try external baseDir first
        if (baseDir != null && !baseDir.isBlank()) {
            Path path = Paths.get(baseDir, version, skill, filename);
            if (Files.exists(path)) {
                try {
                    return Files.readString(path, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    log.warn("Failed to read {}: {}", path, e.getMessage());
                }
            }
        }
        // Fallback to classpath
        try {
            ClassPathResource resource = new ClassPathResource("prompts/" + version + "/" + skill + "/" + filename);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Version " + version + " not found for " + skill + "/" + filename);
        }
    }

    /**
     * Update a prompt: writes a new version file and optionally activates it.
     * Returns the new version string (e.g. "v2").
     */
    public String updatePrompt(String skill, String filename, String content, boolean activateImmediately) {
        ensureBaseDirConfigured();

        // Determine next version number
        List<String> existing = getVersionList(skill, filename);
        int nextNum = existing.stream()
                        .filter(v -> v.matches("v\\d+"))
                        .mapToInt(v -> Integer.parseInt(v.substring(1)))
                        .max()
                        .orElse(0)
                + 1;
        String newVersion = "v" + nextNum;

        // Write new version file
        Path newFilePath = Paths.get(baseDir, newVersion, skill, filename);
        try {
            Files.createDirectories(newFilePath.getParent());
            Files.writeString(newFilePath, content, StandardCharsets.UTF_8);
            log.info("Wrote new prompt version {} for {}/{}", newVersion, skill, filename);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write new prompt version: " + e.getMessage(), e);
        }

        if (activateImmediately) {
            activateVersion(skill, filename, newVersion);
        }

        return newVersion;
    }

    /**
     * Set the active version for a specific prompt and invalidate the cache.
     */
    public void activateVersion(String skill, String filename, String version) {
        ensureBaseDirConfigured();
        String key = skill + "/" + filename;
        Map<String, String> versions = loadActiveVersions();
        versions.put(key, version);
        saveActiveVersions(versions);
        promptLoader.invalidateCache(key);
        log.info("Activated {} for {}", version, key);
    }

    /**
     * List all available versions for a specific prompt.
     */
    public List<String> getVersionList(String skill, String filename) {
        List<String> versions = new ArrayList<>();

        // Check classpath (v1 always exists)
        versions.add("v1");

        // Check external dir
        if (baseDir != null && !baseDir.isBlank()) {
            Path basePath = Paths.get(baseDir);
            if (Files.isDirectory(basePath)) {
                try {
                    Files.list(basePath)
                            .filter(Files::isDirectory)
                            .filter(p -> p.getFileName().toString().matches("v\\d+"))
                            .filter(p -> Files.exists(p.resolve(skill).resolve(filename)))
                            .map(p -> p.getFileName().toString())
                            .sorted()
                            .forEach(v -> {
                                if (!versions.contains(v)) versions.add(v);
                            });
                } catch (IOException e) {
                    log.warn("Error listing versions in {}: {}", basePath, e.getMessage());
                }
            }
        }

        versions.sort((a, b) -> {
            int na = Integer.parseInt(a.substring(1));
            int nb = Integer.parseInt(b.substring(1));
            return Integer.compare(na, nb);
        });
        return versions;
    }

    // ─── Active versions I/O ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Map<String, String> loadActiveVersions() {
        // Try external active_versions.json
        if (baseDir != null && !baseDir.isBlank()) {
            Path configPath = Paths.get(baseDir, "active_versions.json");
            if (Files.exists(configPath)) {
                try {
                    return objectMapper.readValue(
                            Files.readString(configPath, StandardCharsets.UTF_8), new TypeReference<>() {});
                } catch (IOException e) {
                    log.warn("Failed to read external active_versions.json: {}", e.getMessage());
                }
            }
        }

        // Fallback to classpath
        try {
            ClassPathResource resource = new ClassPathResource("prompts/active_versions.json");
            return objectMapper.readValue(
                    resource.getContentAsString(StandardCharsets.UTF_8), new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("No active_versions.json in classpath: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private void saveActiveVersions(Map<String, String> versions) {
        Path configPath = Paths.get(baseDir, "active_versions.json");
        try {
            Files.createDirectories(configPath.getParent());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(versions);
            Files.writeString(configPath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save active_versions.json: " + e.getMessage(), e);
        }
    }

    private void ensureBaseDirConfigured() {
        if (baseDir == null || baseDir.isBlank()) {
            throw new IllegalStateException("app.prompts.base-dir is not configured. Cannot write prompt files. "
                    + "Set this in application.yml or as environment variable APP_PROMPTS_BASE_DIR.");
        }
    }
}
