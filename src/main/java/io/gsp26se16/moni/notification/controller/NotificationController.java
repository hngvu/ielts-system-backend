package io.gsp26se16.moni.notification.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.notification.dto.response.NotificationResponse;
import io.gsp26se16.moni.notification.service.NotificationService;
import io.gsp26se16.moni.notification.service.NotificationSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSseService sseService;
    private final UserCredentialsRepository userCredentialsRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> list(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "20") int limit) {
        String userId = resolveUserId(jwt.getClaim("userId"));
        return ResponseEntity.ok(ApiResponse.<List<NotificationResponse>>builder()
                .result(notificationService.listForUser(userId, limit))
                .build());
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        String userId = resolveUserId(jwt.getClaim("userId"));
        return ResponseEntity.ok(ApiResponse.<Map<String, Long>>builder()
                .result(Map.of("count", notificationService.unreadCount(userId)))
                .build());
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        String userId = resolveUserId(jwt.getClaim("userId"));
        notificationService.markAsRead(userId, id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().build());
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        String userId = resolveUserId(jwt.getClaim("userId"));
        int updated = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.<Map<String, Integer>>builder()
                .result(Map.of("updated", updated))
                .build());
    }

    /**
     * SSE endpoint for realtime notifications.
     * EventSource doesn't support custom headers, so token comes via query param
     * (same pattern as /payments/subscribe).
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String token) {
        try {
            var signedJWT = com.nimbusds.jwt.SignedJWT.parse(token);
            String credentialId = signedJWT.getJWTClaimsSet().getStringClaim("userId");
            if (credentialId == null) throw new AppException(ErrorCode.UNAUTHENTICATED);

            // Bypass JPA to release DB connection immediately during long-lived stream
            String userId = jdbcTemplate.queryForObject(
                    "SELECT user_id FROM user_credentials WHERE id = ?", String.class, credentialId);
            if (userId == null) throw new AppException(ErrorCode.UNAUTHENTICATED);

            return sseService.subscribe(userId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Notification SSE auth failed: {}", e.getMessage());
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    private String resolveUserId(String credentialId) {
        if (credentialId == null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        return userCredentialsRepository
                .findById(credentialId)
                .map(c -> c.getUser().getId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }
}
