package com.healthtimeline;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class DatabaseSchemaInitializer {
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
                    data BYTEA NOT NULL,
                    extracted_text TEXT,
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

            jdbcTemplate.execute("ALTER TABLE public.health_reports ALTER COLUMN data TYPE BYTEA USING data;");
            jdbcTemplate.execute("ALTER TABLE public.health_reports ALTER COLUMN file_name TYPE VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE public.health_reports ALTER COLUMN content_type TYPE VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE public.health_reports ALTER COLUMN report_type TYPE VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE public.health_reports ADD COLUMN IF NOT EXISTS \"values\" TEXT;");
        } catch (Exception ignored) {
            // Best effort schema verification; avoid recursive dropping and preserve the existing production table.
        }
    }
}
