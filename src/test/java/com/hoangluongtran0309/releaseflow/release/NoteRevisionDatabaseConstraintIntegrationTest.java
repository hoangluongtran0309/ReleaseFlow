package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V40 (ADR-0046): a published note's revisions and a public entry's revisions are only ever
 * inserted, each as the next number, and nothing that could remove a note, its release, or
 * an entry takes a revision with it.
 */
class NoteRevisionDatabaseConstraintIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        // Revisions and published releases reject DELETE by design; TRUNCATE bypasses row triggers.
        jdbcTemplate.execute("TRUNCATE release_note_revisions, public_changelog_entry_revisions, public_changelog_entries, automation_action_runs, automation_runs, automation_publish_jobs, automation_rule_actions, automation_rules, translation_jobs, release_audience_notes, release_change_reviews, release_notes, release_changes, linked_issue_reads, release_linked_issues, release_fold_decisions, releases");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void correctsOnlyAPublishedNoteAndOnlyWithTheNextNumber() {
        Fixture fixture = fixture();
        UUID note = insertNote(fixture);

        // An approved release's note is edited in place; it has no revisions yet.
        assertThatThrownBy(() -> insertRevision(fixture, note, 2, "MANUAL"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("Only a published release note can be corrected");

        publish(fixture);
        assertThatThrownBy(() -> insertRevision(fixture, note, 1, "MANUAL"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRevision(fixture, note, 3, "MANUAL"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("is not the next one");
        assertThatCode(() -> insertRevision(fixture, note, 2, "MANUAL")).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertRevision(fixture, note, 2, "RENDERED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertRevision(fixture, note, 3, "RENDERED")).doesNotThrowAnyException();

        assertThat(jdbcTemplate.queryForList(
                "SELECT revision FROM release_note_revisions WHERE note_id = ? ORDER BY revision", Integer.class, note))
                .containsExactly(2, 3);
        assertThat(jdbcTemplate.queryForObject("SELECT content FROM release_audience_notes WHERE id = ?", String.class,
                note)).isEqualTo("# Published");
    }

    @Test
    void neverChangesOrRemovesARevisionNorWhatItCorrects() {
        Fixture fixture = fixture();
        UUID note = insertNote(fixture);
        publish(fixture);
        UUID revision = insertRevision(fixture, note, 2, "MANUAL");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE release_note_revisions SET content = 'changed' WHERE id = ?", revision))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("Release note revisions are immutable");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE release_note_revisions SET reason = 'other' WHERE id = ?", revision))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM release_note_revisions WHERE id = ?", revision))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("Release note revisions are immutable");
        // Nothing above a revision can be removed either, so no cascade can reach one.
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM release_audience_notes WHERE id = ?", note))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM releases WHERE id = ?", fixture.release()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM app_users WHERE id = ?", fixture.author()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM projects WHERE id = ?", fixture.project()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM organizations WHERE id = ?", fixture.organization()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM release_note_revisions", Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT content FROM release_note_revisions", String.class))
                .isEqualTo("# Corrected 2");
    }

    @Test
    void keepsARevisionToItsOwnNoteAndTenantWithAReasonAndAnHonestGlossary() {
        Fixture fixture = fixture();
        UUID note = insertNote(fixture);
        publish(fixture);
        Fixture other = fixture();
        UUID otherNote = insertNote(other);
        publish(other);

        // A note of another release, or an author of another Organization, is not this one's.
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        INSERT INTO release_note_revisions (id, note_id, release_id, organization_id, project_id, revision,
                            content, origin, reason, author_id, author_name, glossary_status, created_at)
                        VALUES (?, ?, ?, ?, ?, 2, '# Wrong', 'MANUAL', 'Fix', ?, 'Mai Tran', 'NONE', now())
                        """,
                UUID.randomUUID(), otherNote, fixture.release(), fixture.organization(), fixture.project(),
                fixture.author()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        INSERT INTO release_note_revisions (id, note_id, release_id, organization_id, project_id, revision,
                            content, origin, reason, author_id, author_name, glossary_status, created_at)
                        VALUES (?, ?, ?, ?, ?, 2, '# Wrong', 'MANUAL', 'Fix', ?, 'Mai Tran', 'NONE', now())
                        """,
                UUID.randomUUID(), note, fixture.release(), fixture.organization(), fixture.project(), other.author()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // A reason is required and short; a text a person wrote claims no glossary.
        assertThatThrownBy(() -> insertRevision(fixture, note, 2, "MANUAL", " ", "NONE", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRevision(fixture, note, 2, "MANUAL", "x".repeat(501), "NONE", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRevision(fixture, note, 2, "MANUAL", "Fix", "APPLIED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRevision(fixture, note, 2, "RENDERED", "Fix", "PENDING", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRevision(fixture, note, 2, "RENDERED", "Fix", "NOT_APPLIED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRevision(fixture, note, 2, "PUBLISHED", "Fix", "NONE", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertRevision(fixture, note, 2, "RENDERED", "x".repeat(500), "NOT_APPLIED",
                "glossary_sync_failed")).doesNotThrowAnyException();
    }

    @Test
    void appendsAPublicRevisionOnlyAsTheNextNumberAndNeverChangesIt() {
        Fixture fixture = fixture();
        insertNote(fixture);
        publish(fixture);
        UUID audience = fixture.audience();
        UUID rule = insertRule(fixture.organization());
        UUID action = insertAction(rule, fixture.organization(), audience);
        UUID run = insertRun(fixture, rule, "RELEASE_PUBLISHED", null, null);
        UUID published = insertActionRun(run, fixture.organization(), action, audience, 0, 1);
        UUID entry = insertEntry(fixture, published);
        UUID correction = insertRun(fixture, rule, "CORRECTION", UUID.randomUUID(), fixture.author());
        UUID delivery = insertActionRun(correction, fixture.organization(), action, audience, 0, 2);
        UUID second = insertActionRun(correction, fixture.organization(), action, audience, 1, 3);

        assertThatThrownBy(() -> insertPublicRevision(entry, fixture.organization(), 3, delivery))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("is not the next one");
        UUID revision = insertPublicRevision(entry, fixture.organization(), 2, delivery);
        // One revision per delivery, so a retry cannot quietly append a second copy.
        assertThatThrownBy(() -> insertPublicRevision(entry, fixture.organization(), 3, delivery))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertPublicRevision(entry, fixture.organization(), 3, second)).doesNotThrowAnyException();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE public_changelog_entry_revisions SET content_snapshot = 'changed' WHERE id = ?", revision))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("Public changelog entries are immutable");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM public_changelog_entry_revisions WHERE id = ?",
                revision))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM public_changelog_entries WHERE id = ?", entry))
                .isInstanceOf(DataIntegrityViolationException.class);
        // An action run a public revision points at outlives its run.
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM automation_runs WHERE id = ?", correction))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT content_snapshot FROM public_changelog_entries WHERE id = ?",
                String.class, entry)).isEqualTo("# Published");
    }

    @Test
    void recordsTheRevisionARunCarriedAndWhoAskedForACorrection() {
        Fixture fixture = fixture();
        insertNote(fixture);
        publish(fixture);
        UUID rule = insertRule(fixture.organization());
        UUID action = insertAction(rule, fixture.organization(), fixture.audience());
        UUID run = insertRun(fixture, rule, "RELEASE_PUBLISHED", null, null);

        // A run that carried no note carried no revision of one, and revision 1 is the first.
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        INSERT INTO automation_action_runs (id, run_id, organization_id, rule_action_id, position,
                            action_type, audience_id, audience_name_snapshot, language_snapshot, note_revision, status)
                        VALUES (?, ?, ?, ?, 0, 'PUBLIC_CHANGELOG', ?, 'Operator', 'en', 1, 'PENDING')
                        """,
                UUID.randomUUID(), run, fixture.organization(), action, fixture.audience()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertActionRun(run, fixture.organization(), action, fixture.audience(), 0, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertActionRun(run, fixture.organization(), action, fixture.audience(), 0, null))
                .doesNotThrowAnyException();

        // A correction is asked for by a person, once per request.
        assertThatThrownBy(() -> insertRun(fixture, rule, "CORRECTION", null, fixture.author()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRun(fixture, rule, "CORRECTION", UUID.randomUUID(), null))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID request = UUID.randomUUID();
        assertThatCode(() -> insertRun(fixture, rule, "CORRECTION", request, fixture.author()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> insertRun(fixture, rule, "CORRECTION", request, fixture.author()))
                .isInstanceOf(DataIntegrityViolationException.class);
        // A publication still names nobody, and no rule is ever written with the trigger.
        assertThatThrownBy(() -> insertRun(fixture, rule, "RELEASE_PUBLISHED", null, fixture.author()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE automation_rules SET trigger_type = 'CORRECTION' WHERE id = ?", rule))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private record Fixture(UUID organization, UUID project, UUID release, UUID audience, UUID author) {
    }

    // An approved release with one audience and an administrator.
    private Fixture fixture() {
        UUID organization = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO organizations (id, name, slug, created_at, output_language) VALUES (?, 'Organization', 'org-' || SUBSTRING(gen_random_uuid()::text, 1, 8), now(), 'en')", organization);
        UUID project = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO projects (id, organization_id, name, created_at) VALUES (?, ?, 'Project', now())",
                project, organization);
        UUID author = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, ?, 'hash', 'Mai Tran', 'ADMIN', now())
                        """,
                author, organization, author + "@example.com");
        UUID audience = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO audience_definitions (id, organization_id, code, display_name, communication_intent,
                            template_body, preset, created_at, updated_at)
                        VALUES (?, ?, 'operator', 'Operator', '', '{{whatChanged}}', false, now(), now())
                        """,
                audience, organization);
        UUID release = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO releases (id, organization_id, project_id, version, status, approved_at, approved_by,
                            approver_name, created_at, updated_at)
                        VALUES (?, ?, ?, '1.2.0', 'APPROVED', now(), ?, 'Mai Tran', now(), now())
                        """,
                release, organization, project, author);
        return new Fixture(organization, project, release, audience, author);
    }

    private UUID insertNote(Fixture fixture) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO release_audience_notes (id, release_id, organization_id, project_id, audience_id,
                            audience_code, audience_name, language, template_body_snapshot, content, auto_rerender,
                            translation_status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, 'operator', 'Operator', 'en', '{{whatChanged}}', '# Published', true,
                            'READY', now(), now())
                        """,
                id, fixture.release(), fixture.organization(), fixture.project(), fixture.audience());
        return id;
    }

    private void publish(Fixture fixture) {
        jdbcTemplate.update(
                """
                        UPDATE releases
                        SET status = 'PUBLISHED', published_at = now(), published_by = ?, publisher_name = 'Mai Tran'
                        WHERE id = ?
                        """,
                fixture.author(), fixture.release());
    }

    private UUID insertRevision(Fixture fixture, UUID note, int revision, String origin) {
        return insertRevision(fixture, note, revision, origin, "Fix the placeholders", "NONE", null);
    }

    private UUID insertRevision(Fixture fixture, UUID note, int revision, String origin, String reason,
                                String glossaryStatus, String glossaryReason) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO release_note_revisions (id, note_id, release_id, organization_id, project_id, revision,
                            content, origin, reason, author_id, author_name, glossary_status, glossary_reason, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Mai Tran', ?, ?, now())
                        """,
                id, note, fixture.release(), fixture.organization(), fixture.project(), revision,
                "# Corrected " + revision, origin, reason, fixture.author(), glossaryStatus, glossaryReason);
        return id;
    }

    private UUID insertRule(UUID organization) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO automation_rules
                            (id, organization_id, name, trigger_type, enabled, active, created_at, updated_at)
                        VALUES (?, ?, 'Announce', 'RELEASE_PUBLISHED', FALSE, TRUE, now(), now())
                        """,
                id, organization);
        return id;
    }

    private UUID insertAction(UUID rule, UUID organization, UUID audience) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO automation_rule_actions
                            (id, rule_id, organization_id, position, action_type, audience_id, target_language)
                        VALUES (?, ?, ?, 0, 'PUBLIC_CHANGELOG', ?, 'en')
                        """,
                id, rule, organization, audience);
        return id;
    }

    private UUID insertRun(Fixture fixture, UUID rule, String trigger, UUID request, UUID initiator) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO automation_runs
                            (id, rule_id, organization_id, project_id, release_id, rule_name_snapshot,
                             release_version_snapshot, trigger_type, request_id, initiated_by, initiator_name, status,
                             created_at)
                        VALUES (?, ?, ?, ?, ?, 'Announce', '1.2.0', ?, ?, ?, ?, 'PENDING', now())
                        """,
                id, rule, fixture.organization(), fixture.project(), fixture.release(), trigger, request, initiator,
                initiator == null ? null : "Mai Tran");
        return id;
    }

    private UUID insertActionRun(UUID run, UUID organization, UUID action, UUID audience, int position,
                                 Integer noteRevision) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO automation_action_runs
                            (id, run_id, organization_id, rule_action_id, position, action_type, audience_id,
                             audience_name_snapshot, language_snapshot, note_content_snapshot, note_revision, status)
                        VALUES (?, ?, ?, ?, ?, 'PUBLIC_CHANGELOG', ?, 'Operator', 'en', '# Published', ?, 'PENDING')
                        """,
                id, run, organization, action, position, audience, noteRevision);
        return id;
    }

    private UUID insertEntry(Fixture fixture, UUID actionRun) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO public_changelog_entries (id, organization_id, project_id, release_id, action_run_id,
                            organization_slug_snapshot, organization_name_snapshot, project_name_snapshot,
                            release_version_snapshot, audience_name_snapshot, language, content_snapshot, published_at)
                        VALUES (?, ?, ?, ?, ?, 'dbvault', 'dbvault', 'Project', '1.2.0', 'Operator', 'en', '# Published',
                            now())
                        """,
                id, fixture.organization(), fixture.project(), fixture.release(), actionRun);
        return id;
    }

    private UUID insertPublicRevision(UUID entry, UUID organization, int revision, UUID actionRun) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO public_changelog_entry_revisions (id, entry_id, organization_id, revision,
                            note_revision, action_run_id, content_snapshot, reason, corrected_at)
                        VALUES (?, ?, ?, ?, 2, ?, '# Corrected', 'Fix the placeholders', now())
                        """,
                id, entry, organization, revision, actionRun);
        return id;
    }
}
