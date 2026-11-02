package com.healthtimeline;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class DatabaseSchemaInitializer {
    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaInitializer.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void initialize() {
        ensureHealthReportsTable();
    }

    private void ensureHealthReportsTable() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS public.health_reports (
                    id BIGSERIAL PRIMARY KEY,
                    content_type VARCHAR(255) NOT NULL,
                    file_name VARCHAR(255) NOT NULL,
                    file_size BIGINT,
                    report_date DATE,
                    report_type VARCHAR(255),
                    summary TEXT,
                    uploaded_at TIMESTAMP NOT NULL,
                    user_id BIGINT NOT NULL,
                    values TEXT
                )
            """);

            jdbcTemplate.execute("ALTER TABLE public.health_reports ALTER COLUMN file_name TYPE VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE public.health_reports ALTER COLUMN content_type TYPE VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE public.health_reports ALTER COLUMN report_type TYPE VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE public.health_reports ADD COLUMN IF NOT EXISTS \"values\" TEXT;");
            jdbcTemplate.execute("ALTER TABLE public.health_reports DROP COLUMN IF EXISTS extracted_text;");
            jdbcTemplate.execute("ALTER TABLE public.health_reports DROP COLUMN IF EXISTS data;");
            log.info("Health reports schema verified: extracted_text and data columns removed if present.");
        } catch (Exception e) {
            log.error("Failed to verify or migrate health_reports schema. The database still has the old extracted_text column.", e);
            throw e;
        }
    }
}
