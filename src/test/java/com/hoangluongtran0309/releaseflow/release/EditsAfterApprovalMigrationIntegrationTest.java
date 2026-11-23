package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V42 on data that existed before it, migrated in a schema of its own (ADR-0047). A note a
 * person edited gets the time of that edit, and a ready note the digest of its text, only when
 * its release is approved, since a published release's notes cannot be written; a note of a
 * published release keeps neither.
 */
class EditsAfterApprovalMigrationIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String SCHEMA = "v42_edits_after_approval_check";
    private static final Instant EDITED = Instant.parse("2026-11-20T09:30:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    private TransactionTemplate transactions;

    @Autowired
    void transactions(PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Test
    void givesAnApprovedReleasesHandEditedNoteItsEditTimeAndLeavesAPublishedOneAlone() {
        try {
            migrate("41");
            UUID[] rows = inSchema(() -> {
                UUID organization = organization();
                UUID author = member(organization);
                UUID project = project(organization);
                UUID audience = audience(organization);
                UUID approved = release(organization, project, author, "1.4.0");
                UUID approvedEdited = note(approved, organization, project, audience, author, "en");
                UUID approvedFollowing = note(approved, organization, project, audience, null, "vi");
                UUID approvedPending = note(approved, organization, project, audience, null, "de");
                jdbcTemplate.update("UPDATE " + SCHEMA + ".release_audience_notes SET translation_status = 'PENDING' "
                        + "WHERE id = ?", approvedPending);
                UUID published = release(organization, project, author, "1.3.0");
                UUID publishedEdited = note(published, organization, project, audience, author, "en");
                jdbcTemplate.update("UPDATE " + SCHEMA + ".releases SET status = 'PUBLISHED', published_at = now(), "
                        + "published_by = ?, publisher_name = 'Mai Tran' WHERE id = ?", author, published);
                return new UUID[]{organization, author, project, approved, approvedEdited, approvedFollowing, publishedEdited,
                        approvedPending};
            });
            UUID organization = rows[0];
            UUID author = rows[1];
            UUID project = rows[2];
            UUID approved = rows[3];
            UUID approvedEdited = rows[4];
            UUID approvedFollowing = rows[5];
            UUID publishedEdited = rows[6];
            UUID approvedPending = rows[7];

            migrate("42");

            assertThat(lastEditedAt(approvedEdited)).isEqualTo(Timestamp.from(EDITED));
            assertThat(lastEditedAt(approvedFollowing)).isNull();
            // Its release is published, so the note cannot be written: it keeps no edit time.
            assertThat(lastEditedAt(publishedEdited)).isNull();
            // The digest is the one the application computes, of each ready note's text.
            assertThat(readyContentSha256(approvedEdited)).isEqualTo(RenderedNote.hash("# Release ✓"));
            assertThat(readyContentSha256(approvedFollowing)).isEqualTo(RenderedNote.hash("# Release ✓"));
            assertThat(readyContentSha256(approvedPending)).isNull();
            assertThat(readyContentSha256(publishedEdited)).isNull();
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + SCHEMA + ".releases "
                    + "WHERE summary_changed_by IS NOT NULL OR upgrade_guide_changed_by IS NOT NULL", Integer.class)).isZero();

            // The time goes only with an editor, and a change is recorded only after approval.
            assertThatThrownBy(() -> inSchema(() -> jdbcTemplate.update("UPDATE " + SCHEMA + ".release_audience_notes "
                    + "SET last_edited_at = now() WHERE id = ?", approvedFollowing)))
                    .isInstanceOf(DataIntegrityViolationException.class);
            UUID draft = UUID.randomUUID();
            inSchema(() -> jdbcTemplate.update("INSERT INTO " + SCHEMA + ".releases (id, organization_id, project_id, "
                    + "version, status, created_at, updated_at) VALUES (?, ?, ?, '1.5.0', 'DRAFT', now(), now())",
                    draft, organization, project));
            assertThatThrownBy(() -> inSchema(() -> jdbcTemplate.update("UPDATE " + SCHEMA + ".releases SET "
                    + "summary_changed_by = ?, summary_changer_name = 'Mai Tran', summary_changed_at = now() WHERE id = ?",
                    author, draft)))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> inSchema(() -> jdbcTemplate.update("UPDATE " + SCHEMA + ".releases SET "
                    + "summary_changed_by = ? WHERE id = ?", author, approved)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    // The schema's triggers name their tables without a schema, as Flyway runs them; the test
    // writes in one transaction whose search path is the schema, so they find its rows.
    private <T> T inSchema(java.util.function.Supplier<T> work) {
        return transactions.execute(status -> {
            jdbcTemplate.execute("SET LOCAL search_path TO " + SCHEMA);
            return work.get();
        });
    }

    private Timestamp lastEditedAt(UUID note) {
        return jdbcTemplate.queryForObject("SELECT last_edited_at FROM " + SCHEMA + ".release_audience_notes WHERE id = ?",
                Timestamp.class, note);
    }

    private String readyContentSha256(UUID note) {
        return jdbcTemplate.queryForObject("SELECT ready_content_sha256 FROM " + SCHEMA + ".release_audience_notes "
                + "WHERE id = ?", String.class, note);
    }

    private UUID organization() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + SCHEMA + ".organizations (id, name, created_at, output_language, slug) "
                + "VALUES (?, 'dbvault', now(), 'en', 'dbvault')", id);
        return id;
    }

    private UUID member(UUID organization) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + SCHEMA + ".app_users (id, organization_id, email, password_hash, display_name, "
                + "role, created_at) VALUES (?, ?, 'lead@dbvault.test', 'x', 'Mai Tran', 'ADMIN', now())", id, organization);
        return id;
    }

    private UUID project(UUID organization) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + SCHEMA + ".projects (id, organization_id, name, created_at) "
                + "VALUES (?, ?, 'dbvault', now())", id, organization);
        return id;
    }

    private UUID audience(UUID organization) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + SCHEMA + ".audience_definitions (id, organization_id, code, display_name, "
                + "communication_intent, template_body, preset, created_at, updated_at) "
                + "VALUES (?, ?, 'end_user', 'End user', '', '- {{whatChanged}}', true, now(), now())", id, organization);
        return id;
    }

    private UUID release(UUID organization, UUID project, UUID approver, String version) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + SCHEMA + ".releases (id, organization_id, project_id, version, status, "
                + "created_at, updated_at, approved_at, approved_by, approver_name) "
                + "VALUES (?, ?, ?, ?, 'APPROVED', now(), now(), now(), ?, 'Mai Tran')",
                id, organization, project, version, approver);
        return id;
    }

    // A note as V41 left it: written at approval, and edited by hand when an editor is given.
    private UUID note(UUID release, UUID organization, UUID project, UUID audience, UUID editor, String language) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + SCHEMA + ".release_audience_notes (id, release_id, organization_id, "
                        + "project_id, audience_id, audience_code, audience_name, language, template_body_snapshot, "
                        + "content, auto_rerender, translation_status, last_edited_by, last_editor_name, created_at, "
                        + "updated_at) VALUES (?, ?, ?, ?, ?, 'end_user', 'End user', ?, '- {{whatChanged}}', "
                        + "'# Release ✓', ?, 'READY', ?, ?, now(), ?)",
                id, release, organization, project, audience, language, editor == null, editor,
                editor == null ? null : "Mai Tran", Timestamp.from(EDITED));
        return id;
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
