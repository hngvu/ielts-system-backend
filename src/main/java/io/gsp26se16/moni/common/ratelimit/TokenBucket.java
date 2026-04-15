package io.gsp26se16.moni.common.ratelimit;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe Token Bucket implementation — pure Java, no external library.
 *
 * <p>Algorithm: Greedy refill.
 * - capacity: max tokens
 * - refillTokens: tokens added per refillPeriodNanos
 * - tokens refill proportionally based on elapsed time since last refill
 */
public class TokenBucket {

    private final long capacity;
    private final long refillTokens;
    private final long refillPeriodNanos;

    private final AtomicLong availableTokens;
    private volatile long lastRefillNanos;

    public TokenBucket(long capacity, long refillTokensPerMinute) {
        this.capacity = capacity;
        this.refillTokens = refillTokensPerMinute;
        this.refillPeriodNanos = 60L * 1_000_000_000L; // 1 minute in nanos
        this.availableTokens = new AtomicLong(capacity);
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * Try to consume 1 token.
     *
     * @return nanoseconds to wait before retry (0 = success, >0 = throttled)
     */
    public long tryConsume() {
        refill();
        long tokens = availableTokens.get();
        if (tokens > 0 && availableTokens.compareAndSet(tokens, tokens - 1)) {
            return 0L; // consumed OK
        }
        // Calculate wait time: time until next token available
        long now = System.nanoTime();
        long elapsed = now - lastRefillNanos;
        long nanosPerToken = refillPeriodNanos / refillTokens;
        return nanosPerToken - (elapsed % nanosPerToken);
    }

    /** Remaining tokens (approximate, for headers). */
    public long getAvailableTokens() {
        refill();
        return Math.max(0, availableTokens.get());
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillNanos;
        if (elapsed <= 0) return;

        long tokensToAdd = (elapsed * refillTokens) / refillPeriodNanos;
        if (tokensToAdd <= 0) return;

        // CAS loop to update tokens + lastRefillNanos atomically-ish
        // Good enough for rate limiting (slight over-counting is acceptable)
        long current = availableTokens.get();
        long newTokens = Math.min(capacity, current + tokensToAdd);
        if (availableTokens.compareAndSet(current, newTokens)) {
            lastRefillNanos = now;
        }
    }
}
