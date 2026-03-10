package io.gsp26se16.moni.common.config;

import javax.sql.DataSource;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps Neon serverless DB connection warm to avoid cold start latency.
 * Runs a lightweight query every 4 minutes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseWarmupScheduler {

    private final DataSource dataSource;

    @Scheduled(fixedRate = 240_000)
    public void warmup() {
        try (var conn = dataSource.getConnection();
                var stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
        } catch (Exception e) {
            log.warn("DB warmup failed: {}", e.getMessage());
        }
    }
}
