package com.hoangluongtran0309.releaseflow.translation;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.hoangluongtran0309.releaseflow.support.TestChanges;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0046, amended 2026-11-20: rendering a published note again reads each text as the release
 * was published with it, from the latest job of its subject that succeeded by publication, in
 * the cache under that job's context and glossary version and then under none, repaired as
 * ADR-0040 repairs any answer. Nothing is written, and no provider is asked.
 */
class PublishedTranslationIntegrationTest extends PostgreSqlIntegrationTest {

    private static final Instant PUBLISHED = Instant.parse("2026-11-02T02:50:37Z");
    private static final String CONTEXT = "dbvault-cli is the client.";
    private static final String VERSION = "a".repeat(64);

    @Autowired
    private TranslationService translationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID organizationId;
    private UUID projectId;
    private UUID changeId;

    @BeforeEach
    void setUp() {
        clearDatabase();
        organizationId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO organizations (id, name, slug, created_at, output_language) VALUES (?, 'dbvault', 'dbvault', now(), 'en')",
                organizationId);
        projectId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO projects (id, organization_id, name, created_at) VALUES (?, ?, 'dbvault', now())",
                projectId, organizationId);
        changeId = TestChanges.insert(jdbcTemplate, organizationId, projectId, 1, "feat: restore by id", "FEATURE",
                false, false, null);
    }

    @AfterEach
    void clearDatabase() {
        jdbcTemplate.execute("TRUNCATE release_note_revisions, public_changelog_entry_revisions, public_changelog_entries, automation_action_runs, automation_runs, automation_publish_jobs, automation_rule_actions, automation_rules, translation_jobs, release_audience_notes, release_change_reviews, release_notes, release_changes, linked_issue_reads, release_linked_issues, release_fold_decisions, releases");
        jdbcTemplate.update("DELETE FROM change_processing_jobs");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void readsEachTextUnderItsJobsGlossaryVersionThenUnderNoneAndRepairsIt() {
        job(PUBLISHED.minusSeconds(600), CONTEXT, VERSION, "applied");
        cache("Restore <backup-id> with dbvault-cli.", CONTEXT, VERSION, "Khôi phục <BACKUP-id> bằng dbvault-CLI.");
        // A text the job translated without its glossary is cached under none.
        cache("Then verify it.", CONTEXT, "", "Sau đó kiểm tra nó.");

        PublishedTranslation found = translationService.published(organizationId, changeId, "en", "vi", Map.of(
                "whatChanged", "Restore <backup-id> with dbvault-cli.",
                "whyChanged", "Then verify it.",
                "migrationStep", ""
        ), PUBLISHED);

        assertThat(found.complete()).isTrue();
        assertThat(found.texts()).containsEntry("whatChanged", "Khôi phục <backup-id> bằng dbvault-cli.")
                .containsEntry("whyChanged", "Sau đó kiểm tra nó.")
                .containsEntry("migrationStep", "");
        assertThat(found.glossary()).isEqualTo(GlossaryUse.APPLIED);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM translation_jobs", Integer.class)).isOne();
    }

    @Test
    void usesTheLatestJobThatSucceededByPublicationAndCountsWhatItCannotFind() {
        job(PUBLISHED.minusSeconds(3600), "", "", null);
        job(PUBLISHED.minusSeconds(60), CONTEXT, "", null);
        // A job that finished after publication was never part of the note.
        job(PUBLISHED.plusSeconds(60), "later context", "", null);
        cache("Restore it.", "", "", "Bản cũ.");
        cache("Restore it.", CONTEXT, "", "Khôi phục nó.");
        cache("Restore it.", "later context", "", "Bản sau.");

        PublishedTranslation found = translationService.published(organizationId, changeId, "en", "vi", Map.of(
                "whatChanged", "Restore it.",
                // A text whose source changed since publication has no answer under that job.
                "whyChanged", "A sentence written after publication."
        ), PUBLISHED);

        assertThat(found.texts()).containsEntry("whatChanged", "Khôi phục nó.")
                .containsEntry("whyChanged", "A sentence written after publication.");
        assertThat(found.missing()).containsExactly("whyChanged");
        assertThat(found.missingCharacters()).isEqualTo("A sentence written after publication.".length());
        assertThat(found.complete()).isFalse();
    }

    @Test
    void findsNothingWithoutAJobAndNothingIsNeededInTheSourceLanguage() {
        cache("Restore it.", "", "", "Khôi phục nó.");

        PublishedTranslation none = translationService.published(organizationId, changeId, "en", "vi",
                Map.of("whatChanged", "Restore it."), PUBLISHED);
        assertThat(none.missing()).containsExactly("whatChanged");
        assertThat(none.texts()).containsEntry("whatChanged", "Restore it.");

        PublishedTranslation english = translationService.published(organizationId, changeId, "en", "en",
                Map.of("whatChanged", "Restore it."), PUBLISHED);
        assertThat(english.complete()).isTrue();
        assertThat(english.texts()).containsEntry("whatChanged", "Restore it.");
        assertThat(english.glossary()).isEqualTo(GlossaryUse.NONE);
    }

    private void job(Instant completedAt, String context, String version, String outcome) {
        jdbcTemplate.update(
                """
                        INSERT INTO translation_jobs (id, organization_id, project_id, change_id, source_language,
                            target_language, input_hash, input, output, status, attempts, next_attempt_at, created_at,
                            completed_at, context, glossary_version, glossary_outcome)
                        VALUES (?, ?, ?, ?, 'en', 'vi', ?, '{}'::jsonb, '{}'::jsonb, 'SUCCEEDED', 1, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(), organizationId, projectId, changeId, UUID.randomUUID().toString().replace("-", ""),
                Timestamp.from(completedAt), Timestamp.from(completedAt), Timestamp.from(completedAt), context, version,
                outcome);
    }

    private void cache(String text, String context, String version, String translation) {
        jdbcTemplate.update(
                """
                        INSERT INTO translation_cache (organization_id, source_language, target_language, text_hash,
                            translated_text, created_at)
                        VALUES (?, 'en', 'vi', ?, ?, now())
                        """,
                organizationId, TranslationTexts.textHash(text, context, version), translation);
    }
}
