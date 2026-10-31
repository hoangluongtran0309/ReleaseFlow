package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** V34 on data that existed before it, migrated in a schema of its own (ADR-0037). */
class NoteStructureMigrationIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String SCHEMA = "v34_note_structure_check";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    void givesTheShippedUpgraderAudiencesTheirFooterInEveryLanguageAndNothingElseOne() {
        try {
            migrate("33");
            UUID english = organization("en");
            UUID vietnamese = organization("vi-VN");
            UUID operator = audience(english, "operator", true);
            UUID endUser = audience(english, "end_user", true);
            UUID custom = audience(english, "dba", false);
            UUID contributor = audience(vietnamese, "contributor", true);
            UUID support = audience(vietnamese, "support", true);
            variant(english, operator, "vi");
            variant(english, operator, "fr");
            variant(english, custom, "vi");
            variant(vietnamese, contributor, "en");
            UUID change = change(english);

            migrate("34");

            assertThat(footer(operator)).isEqualTo(shipped(AudiencePresets.OPERATOR, "en")).contains("**Compare:**");
            assertThat(variantFooter(operator, "vi")).isEqualTo(shipped(AudiencePresets.OPERATOR, "vi")).contains("**So sánh:**");
            assertThat(variantFooter(operator, "fr")).isEqualTo(shipped(AudiencePresets.OPERATOR, "fr"))
                    .isEqualTo(shipped(AudiencePresets.OPERATOR, "en"));
            assertThat(footer(contributor)).isEqualTo(shipped(AudiencePresets.CONTRIBUTOR, "vi-VN"))
                    .contains("**Hướng dẫn nâng cấp:**");
            assertThat(variantFooter(contributor, "en")).isEqualTo(shipped(AudiencePresets.CONTRIBUTOR, "en"));
            assertThat(footer(endUser)).isEmpty();
            assertThat(footer(support)).isEmpty();
            assertThat(footer(custom)).isEmpty();
            assertThat(variantFooter(custom, "vi")).isEmpty();
            assertThat(jdbcTemplate.queryForObject("SELECT security FROM " + SCHEMA + ".changes WHERE id = ?", Boolean.class,
                    change)).isFalse();
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    private static String shipped(String code, String language) {
        return AudiencePresets.find(code, language).orElseThrow().footerBody();
    }

    private UUID organization(String language) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + SCHEMA + ".organizations (id, name, created_at, output_language, slug) "
                + "VALUES (?, ?, now(), ?, ?)", id, "Org " + language, language, "org-" + language.toLowerCase());
        return id;
    }

    private UUID audience(UUID organization, String code, boolean preset) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + SCHEMA + ".audience_definitions (id, organization_id, code, display_name, "
                        + "communication_intent, template_body, preset, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, '', '- {{whatChanged}}', ?, now(), now())",
                id, organization, code, code, preset);
        return id;
    }

    private void variant(UUID organization, UUID audience, String language) {
        jdbcTemplate.update("INSERT INTO " + SCHEMA + ".audience_template_variants (id, organization_id, audience_id, "
                        + "language, template_body, created_at, updated_at) VALUES (?, ?, ?, ?, '- {{whatChanged}}', now(), now())",
                UUID.randomUUID(), organization, audience, language);
    }

    private UUID change(UUID organization) {
        UUID project = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + SCHEMA + ".projects (id, organization_id, name, created_at) "
                + "VALUES (?, ?, 'Old', now())", project, organization);
        UUID change = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO " + SCHEMA + """
                        .changes (id, organization_id, project_id, pull_request_number, title, author_login, labels,
                            target_branch, merge_commit_sha, merged_at, url, delivery_id, received_at,
                            category, category_display_name, category_group, breaking, needs_review,
                            classification_reasons, classification_source, ai_status, processing_status,
                            changed_file_status, changed_files, review_triggers)
                        VALUES (?, ?, ?, 1, 'fix: derive a key per target', 'octocat', '{"security"}', 'main', ?, now(),
                            'https://github.com/acme/releaseflow/pull/1', ?, now(), 'FIX', 'Fix', 'FIX',
                            false, false, '{"Seeded"}', 'RULES', 'NOT_REQUESTED', 'COMPLETED', 'COLLECTED', '[]', '[]')
                        """,
                change, organization, project, "0".repeat(40), UUID.randomUUID()
        );
        return change;
    }

    private String footer(UUID audience) {
        return jdbcTemplate.queryForObject("SELECT footer_template_body FROM " + SCHEMA + ".audience_definitions WHERE id = ?",
                String.class, audience);
    }

    private String variantFooter(UUID audience, String language) {
        return jdbcTemplate.queryForObject("SELECT footer_template_body FROM " + SCHEMA
                + ".audience_template_variants WHERE audience_id = ? AND language = ?", String.class, audience, language);
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
