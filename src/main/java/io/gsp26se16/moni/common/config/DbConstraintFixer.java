package io.gsp26se16.moni.common.config;

import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DbConstraintFixer {

    private final DataSource dataSource;

    @PostConstruct
    public void fixConstraints() {
        log.info("[DBFIX] Attempting to drop outdated tag_type constraint...");
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {

            // Drop the specific check constraint that was generated with fewer enum values
            stmt.execute("ALTER TABLE tags DROP CONSTRAINT IF EXISTS tags_tag_type_check;");
            log.info("[DBFIX] Successfully dropped tags_tag_type_check constraint.");

        } catch (Exception e) {
            log.warn("[DBFIX] Failed to drop constraint (maybe it doesn't exist?): {}", e.getMessage());
        }
    }
}
