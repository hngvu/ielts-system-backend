package io.gsp26se16.moni.ai.writing.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.ai.writing.service.PromptManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Admin REST controller for managing AI prompt files.
 * All endpoints require ADMIN role.
 *
 * <pre>
 * GET    /api/v1/admin/prompts                              – list all prompts with version info
 * GET    /api/v1/admin/prompts/{skill}/{filename}           – get active content
 * GET    /api/v1/admin/prompts/{skill}/{filename}/versions  – list versions
 * GET    /api/v1/admin/prompts/{skill}/{filename}/versions/{version} – get specific version content
 * PUT    /api/v1/admin/prompts/{skill}/{filename}           – create new version (+ optionally activate)
 * PUT    /api/v1/admin/prompts/{skill}/{filename}/activate/{version} – activate a version
 * GET    /api/v1/admin/prompts/active-versions              – full active_versions map
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/prompts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PromptManagementController {

    private final PromptManagementService promptManagementService;

    /** List all prompts with their active version. */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAllPrompts() {
        return ResponseEntity.ok(promptManagementService.listAllPrompts());
    }

    /** Get active content for a specific prompt. */
    @GetMapping("/{skill}/{filename}")
    public ResponseEntity<Map<String, Object>> getActivePrompt(
            @PathVariable String skill, @PathVariable String filename) {
        String content = promptManagementService.getActiveContent(skill, filename);
        Map<String, String> versions = promptManagementService.loadActiveVersions();
        String key = skill + "/" + filename;
        return ResponseEntity.ok(Map.of(
                "skill", skill,
                "filename", filename,
                "activeVersion", versions.getOrDefault(key, "v1"),
                "content", content));
    }

    /** List all available versions for a prompt. */
    @GetMapping("/{skill}/{filename}/versions")
    public ResponseEntity<Map<String, Object>> getVersions(@PathVariable String skill, @PathVariable String filename) {
        List<String> versionList = promptManagementService.getVersionList(skill, filename);
        Map<String, String> activeVersions = promptManagementService.loadActiveVersions();
        String key = skill + "/" + filename;
        return ResponseEntity.ok(Map.of(
                "skill", skill,
                "filename", filename,
                "activeVersion", activeVersions.getOrDefault(key, "v1"),
                "versions", versionList));
    }

    /** Get content of a specific version. */
    @GetMapping("/{skill}/{filename}/versions/{version}")
    public ResponseEntity<Map<String, Object>> getVersionContent(
            @PathVariable String skill, @PathVariable String filename, @PathVariable String version) {
        String content = promptManagementService.getVersionContent(skill, filename, version);
        return ResponseEntity.ok(Map.of(
                "skill", skill,
                "filename", filename,
                "version", version,
                "content", content));
    }

    /**
     * Update (create new version of) a prompt.
     * Body: { "content": "...", "activateImmediately": true }
     */
    @PutMapping("/{skill}/{filename}")
    public ResponseEntity<Map<String, Object>> updatePrompt(
            @PathVariable String skill, @PathVariable String filename, @RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        boolean activate = Boolean.TRUE.equals(body.get("activateImmediately"));

        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "content is required"));
        }

        String newVersion = promptManagementService.updatePrompt(skill, filename, content, activate);
        log.info("Admin updated prompt {}/{}, new version={}, activated={}", skill, filename, newVersion, activate);

        return ResponseEntity.ok(Map.of(
                "skill", skill,
                "filename", filename,
                "newVersion", newVersion,
                "activated", activate,
                "message", "Prompt saved as " + newVersion + (activate ? " and activated." : ". Not yet activated.")));
    }

    /**
     * Activate a specific version for a prompt (rollback support).
     */
    @PutMapping("/{skill}/{filename}/activate/{version}")
    public ResponseEntity<Map<String, Object>> activateVersion(
            @PathVariable String skill, @PathVariable String filename, @PathVariable String version) {
        promptManagementService.activateVersion(skill, filename, version);
        log.info("Admin activated {} for {}/{}", version, skill, filename);
        return ResponseEntity.ok(Map.of(
                "skill", skill,
                "filename", filename,
                "activeVersion", version,
                "message", version + " is now active for " + skill + "/" + filename));
    }

    /** Get the full active_versions map. */
    @GetMapping("/active-versions")
    public ResponseEntity<Map<String, String>> getActiveVersions() {
        return ResponseEntity.ok(promptManagementService.loadActiveVersions());
    }
}
