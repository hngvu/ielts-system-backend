package io.gsp26se16.moni.expert.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Integrates with Daily.co REST API to create video call rooms.
 */
@Slf4j
@Service
public class DailyCoService {

    @Value("${daily.api-key:}")
    private String apiKey;

    private static final String DAILY_API_URL = "https://api.daily.co/v1/rooms";

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Creates a private Daily.co room with max 2 participants and 30-min expiry.
     *
     * @param roomName unique room identifier
     * @return the room URL (e.g., https://your-domain.daily.co/room-name)
     */
    public String createRoom(String roomName) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Daily.co API key not configured, returning placeholder URL");
            return "https://placeholder.daily.co/" + roomName;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        long expiresAt = System.currentTimeMillis() / 1000 + 1800; // 30 minutes

        Map<String, Object> body = Map.of(
                "name",
                roomName,
                "privacy",
                "private",
                "properties",
                Map.of("max_participants", 2, "exp", expiresAt, "eject_at_room_exp", true));

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(DAILY_API_URL, HttpMethod.POST, request, Map.class);

            if (response.getBody() != null && response.getBody().containsKey("url")) {
                String url = (String) response.getBody().get("url");
                log.info("Daily.co room created: {}", url);
                return url;
            }
        } catch (Exception e) {
            log.error("Failed to create Daily.co room: {}", e.getMessage());
        }

        return "https://placeholder.daily.co/" + roomName;
    }
}
