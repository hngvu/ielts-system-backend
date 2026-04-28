package io.gsp26se16.moni.common.ratelimit;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;

import lombok.extern.slf4j.Slf4j;

/**
 * Rate Limiting Filter — runs once per request, before Spring Security.
 *
 * <p>4 tiers:
 * <ul>
 *   <li>AI endpoints ({@code /api/v1/ai/**}) — 5 req/min per user/IP</li>
 *   <li>Auth ({@code /auth/**, /credentials/**, /users/**}) — 10 req/min per IP</li>
 *   <li>Public vocab/tests — 60 req/min per IP</li>
 *   <li>Everything else (authenticated) — 120 req/min per user/IP</li>
 * </ul>
 *
 * <p>Returns {@code HTTP 429} with header {@code X-Rate-Limit-Retry-After-Seconds} when throttled.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Cache<String, TokenBucket> aiBucketCache;
    private final Cache<String, TokenBucket> authBucketCache;
    private final Cache<String, TokenBucket> publicBucketCache;
    private final Cache<String, TokenBucket> generalBucketCache;
    private final RateLimitConfig config;

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    public RateLimitFilter(
            @Qualifier("aiBucketCache") Cache<String, TokenBucket> aiBucketCache,
            @Qualifier("authBucketCache") Cache<String, TokenBucket> authBucketCache,
            @Qualifier("publicBucketCache") Cache<String, TokenBucket> publicBucketCache,
            @Qualifier("generalBucketCache") Cache<String, TokenBucket> generalBucketCache,
            RateLimitConfig config) {
        this.aiBucketCache = aiBucketCache;
        this.authBucketCache = authBucketCache;
        this.publicBucketCache = publicBucketCache;
        this.generalBucketCache = generalBucketCache;
        this.config = config;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        String method = request.getMethod();

        // Skip OPTIONS preflight and WebSocket upgrade
        if ("OPTIONS".equalsIgnoreCase(method) || uri.startsWith("/ws/")) {
            filterChain.doFilter(request, response);
            return;
        }

        Policy policy = resolvePolicy(uri);
        String key = resolveKey(request, policy);
        TokenBucket bucket = getBucket(policy, key);

        long waitNanos = bucket.tryConsume();

        if (waitNanos == 0L) {
            // Consumed OK — add remaining tokens header
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = Math.max(1L, waitNanos / 1_000_000_000L);
            log.warn("[RateLimit] 429 — policy={} key={} uri={} retryAfter={}s", policy, key, uri, retryAfterSeconds);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(retryAfterSeconds));
            response.getWriter()
                    .write("{\"code\":429,\"message\":\"Too many requests. Please retry after " + retryAfterSeconds
                            + " seconds.\"}");
        }
    }

    // ── Policy ────────────────────────────────────────────────────────────────

    private enum Policy {
        AI,
        AUTH,
        PUBLIC,
        GENERAL
    }

    private Policy resolvePolicy(String uri) {
        if (uri.startsWith("/api/v1/ai/")) return Policy.AI;
        // AUTH chỉ cho login/register/credentials. /users/* đều đã require auth (PreAuthorize)
        // nên dùng GENERAL (per-token bucket) — tránh trang admin user-detail bắn 7 request
        // song song bị siết bởi AUTH bucket 10/phút theo IP.
        if (uri.startsWith("/auth/") || uri.startsWith("/credentials/")) return Policy.AUTH;
        if (uri.startsWith("/api/v1/vocab/") || uri.startsWith("/api/v1/tests") || uri.startsWith("/api/v1/experts"))
            return Policy.PUBLIC;
        return Policy.GENERAL;
    }

    /**
     * Key resolution:
     * - AUTH always uses IP (caller not authenticated yet)
     * - Others use last-32-chars of Bearer token if present, else IP
     */
    private String resolveKey(HttpServletRequest request, Policy policy) {
        if (policy != Policy.AUTH) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                String suffix = token.length() > 32 ? token.substring(token.length() - 32) : token;
                return policy.name() + ":token:" + suffix;
            }
        }
        return policy.name() + ":ip:" + getClientIp(request);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return request.getRemoteAddr();
    }

    // ── Bucket lookup (create if absent) ─────────────────────────────────────

    private TokenBucket getBucket(Policy policy, String key) {
        return switch (policy) {
            case AI -> aiBucketCache.get(key, k -> config.newAiBucket());
            case AUTH -> authBucketCache.get(key, k -> config.newAuthBucket());
            case PUBLIC -> publicBucketCache.get(key, k -> config.newPublicBucket());
            case GENERAL -> generalBucketCache.get(key, k -> config.newGeneralBucket());
        };
    }
}
