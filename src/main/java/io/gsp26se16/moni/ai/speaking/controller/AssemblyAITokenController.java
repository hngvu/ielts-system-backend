package io.gsp26se16.moni.ai.speaking.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
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
@RequestMapping("/api/v1/assemblyai")
public class AssemblyAITokenController {

    @Value("${assemblyai.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> getTemporaryToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", apiKey);

        Map<String, Object> body = Map.of("expires_in", 3600);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.assemblyai.com/v2/realtime/token", HttpMethod.POST, request, Map.class);

            if (response.getBody() != null) {
                return ResponseEntity.ok(response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to get AssemblyAI token: {}", e.getMessage());
        }

        return ResponseEntity.internalServerError().body(Map.of("error", "Failed to generate token"));
    }
}
