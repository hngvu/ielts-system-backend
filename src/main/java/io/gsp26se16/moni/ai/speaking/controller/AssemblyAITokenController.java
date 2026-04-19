package io.gsp26se16.moni.ai.speaking.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Proxy endpoint to generate temporary AssemblyAI tokens.
 * Frontend calls this instead of exposing the API key in client bundle.
 */
@Slf4j
@RestController
@PreAuthorize("hasRole('LEARNER')")
@RequestMapping("/api/v1/assemblyai")
public class AssemblyAITokenController {

    @Value("${assemblyai.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> getTemporaryToken() {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("AssemblyAI API key is not configured (empty/null)");
            return ResponseEntity.internalServerError().body(Map.of("error", "AssemblyAI API key not configured"));
        }

        log.info("Requesting AssemblyAI token (key length={})", apiKey.length());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", apiKey);

        Map<String, Object> body = Map.of("expires_in", 3600);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.assemblyai.com/v2/realtime/token", HttpMethod.POST, request, Map.class);

            log.info("AssemblyAI token response status: {}", response.getStatusCode());
            if (response.getBody() != null) {
                return ResponseEntity.ok(response.getBody());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error(
                    "AssemblyAI token HTTP error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode().value())
                    .body(Map.of("error", "AssemblyAI rejected: " + e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("AssemblyAI token unexpected error: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }

        return ResponseEntity.internalServerError().body(Map.of("error", "Failed to generate token"));
    }
}
