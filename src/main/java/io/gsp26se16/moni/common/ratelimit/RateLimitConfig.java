package io.gsp26se16.moni.common.ratelimit;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Rate limit configuration — Caffeine caches holding TokenBuckets per key.
 * Each cache auto-expires entries after 2 minutes idle to free memory.
 */
@Slf4j
@Getter
@Configuration
public class RateLimitConfig {

    // ── Limits (overridable via env vars / application.yml) ──────────────────

    @Value("${app.rate-limit.ai.capacity:5}")
    private int aiCapacity;

    @Value("${app.rate-limit.ai.refill-per-minute:5}")
    private int aiRefill;

    @Value("${app.rate-limit.auth.capacity:10}")
    private int authCapacity;

    @Value("${app.rate-limit.auth.refill-per-minute:10}")
    private int authRefill;

    @Value("${app.rate-limit.public.capacity:60}")
    private int publicCapacity;

    @Value("${app.rate-limit.public.refill-per-minute:60}")
    private int publicRefill;

    @Value("${app.rate-limit.general.capacity:120}")
    private int generalCapacity;

    @Value("${app.rate-limit.general.refill-per-minute:120}")
    private int generalRefill;

    // ── Caches ────────────────────────────────────────────────────────────────

    @Bean("aiBucketCache")
    public Cache<String, TokenBucket> aiBucketCache() {
        log.info("[RateLimit] AI policy configured: {} req/min", aiCapacity);
        return buildCache(50_000);
    }

    @Bean("authBucketCache")
    public Cache<String, TokenBucket> authBucketCache() {
        log.info("[RateLimit] Auth policy configured: {} req/min", authCapacity);
        return buildCache(50_000);
    }

    @Bean("publicBucketCache")
    public Cache<String, TokenBucket> publicBucketCache() {
        log.info("[RateLimit] Public policy configured: {} req/min", publicCapacity);
        return buildCache(100_000);
    }

    @Bean("generalBucketCache")
    public Cache<String, TokenBucket> generalBucketCache() {
        log.info("[RateLimit] General policy configured: {} req/min", generalCapacity);
        return buildCache(100_000);
    }

    // ── Bucket factories ──────────────────────────────────────────────────────

    public TokenBucket newAiBucket() {
        return new TokenBucket(aiCapacity, aiRefill);
    }

    public TokenBucket newAuthBucket() {
        return new TokenBucket(authCapacity, authRefill);
    }

    public TokenBucket newPublicBucket() {
        return new TokenBucket(publicCapacity, publicRefill);
    }

    public TokenBucket newGeneralBucket() {
        return new TokenBucket(generalCapacity, generalRefill);
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private Cache<String, TokenBucket> buildCache(int maxSize) {
        return Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(2))
                .maximumSize(maxSize)
                .build();
    }
}
