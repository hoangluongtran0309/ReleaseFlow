package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** V32 on narratives stored as plain strings, migrated in a schema of its own. */
class AudienceNarrativeMigrationIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String SCHEMA = "v32_narratives_check";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    void rewritesEveryStoredStringAsAFullNarrativeWithoutAHeadline() {
        try {
            migrate("17");
            UUID organization = UUID.randomUUID();
            UUID project = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO " + SCHEMA + ".organizations (id, name, created_at, output_language) "
                    + "VALUES (?, 'Old', now(), 'en')", organization);
            jdbcTemplate.update("INSERT INTO " + SCHEMA + ".projects (id, organization_id, name, created_at) "
                    + "VALUES (?, ?, 'Old', now())", project, organization);
            jdbcTemplate.update("INSERT INTO " + SCHEMA + ".github_integrations (id, organization_id, project_id, "
                            + "repository_owner, repository_name, webhook_id, secret_nonce, secret_ciphertext, created_at) "
                            + "VALUES (?, ?, ?, 'acme', 'releaseflow', ?, ?, ?, now())",
                    UUID.randomUUID(), organization, project, UUID.randomUUID(), new byte[12], "a secret sealed with AAD".getBytes());
            UUID written = change(organization, project, 1,
                    "{\"operator\":\"Watch the export queue.\",\"end_user\":\"Download any table.\"}");
            UUID none = change(organization, project, 2, "{}");
            UUID withoutNarratives = change(organization, project, 3, null);

            migrate("32");

            JsonNode narratives = narratives(written);
            assertThat(narratives.size()).isEqualTo(2);
            assertThat(narratives.path("operator")).isEqualTo(OBJECT_MAPPER.readTree(
                    "{\"relevance\":\"FULL\",\"headline\":\"\",\"body\":\"Watch the export queue.\"}"));
            assertThat(narratives.path("end_user").path("body").stringValue()).isEqualTo("Download any table.");
            assertThat(narratives(none)).isEqualTo(OBJECT_MAPPER.readTree("{}"));
            assertThat(narratives(withoutNarratives).isNull()).isTrue();
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    private UUID change(UUID organization, UUID project, int number, String narratives) {
        UUID change = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO " + SCHEMA + """
                        .changes (id, organization_id, project_id, pull_request_number, title, author_login, labels,
                            target_branch, merge_commit_sha, merged_at, url, delivery_id, received_at,
                            category, category_display_name, category_group, breaking, needs_review,
                            classification_reasons, classification_source, ai_status, processing_status,
                            changed_file_status, changed_files, review_triggers,
                            ai_provider, ai_model, ai_attempted_at, neutral_summary, content_language, audience_narratives)
                        VALUES (?, ?, ?, ?, 'feat: export', 'octocat', '{}', 'main', ?, now(),
                            'https://github.com/acme/releaseflow/pull/1', ?, now(), 'FEATURE', 'Feature', 'FEATURE',
                            false, false, '{"Seeded"}', 'RULES', 'SUCCEEDED', 'COMPLETED', 'COLLECTED', '[]', '[]',
                            'openai', 'gpt-test', now(), '{"whatChanged":"Tables export.","whyChanged":"","technicalDetail":"","migrationStep":""}'::jsonb,
                            'en', ?::jsonb)
                        """,
                change, organization, project, number, "0".repeat(40), UUID.randomUUID(), narratives
        );
        return change;
    }

    private JsonNode narratives(UUID change) {
        String stored = jdbcTemplate.queryForObject(
                "SELECT audience_narratives::text FROM " + SCHEMA + ".changes WHERE id = ?", String.class, change);
        return stored == null ? OBJECT_MAPPER.nullNode() : OBJECT_MAPPER.readTree(stored);
    }

    private void migrate(String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }
}
