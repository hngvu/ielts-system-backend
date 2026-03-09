package io.gsp26se16.moni.ai.speaking.ws;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import io.gsp26se16.moni.authentication.config.CustomJwtDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Validates the JWT token passed as a query parameter during the WebSocket handshake.
 *
 * <p>Client connects to: {@code ws://host/ws/speaking?token=<JWT>}
 *
 * <p>On success, injects the resolved {@code userId} into the WebSocket session attributes
 * so that {@link SpeakingSocketHandler} can retrieve it without re-parsing the token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final CustomJwtDecoder jwtDecoder;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        String query = request.getURI().getQuery();
        String token = extractToken(query);

        if (token == null) {
            log.warn("WebSocket handshake rejected — no token provided");
            return false;
        }

        try {
            var jwt = jwtDecoder.decode(token);
            String userId = jwt.getClaim("userId");
            if (userId == null) {
                log.warn("WebSocket handshake rejected — JWT has no userId claim");
                return false;
            }
            // Inject userId so the handler can read it from session attributes
            attributes.put("userId", userId);
            log.info("WebSocket handshake accepted for userId: {}", userId);
            return true;
        } catch (Exception e) {
            log.warn("WebSocket handshake rejected — invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // No post-handshake action needed
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractToken(String query) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
        }
        return null;
    }
}
