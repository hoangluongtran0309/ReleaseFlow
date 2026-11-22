package com.hoangluongtran0309.releaseflow.changelog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a correction does to a public entry (ADR-0046, parts 4 and 5), case by case, including
 * the ones a correction run reaches only by timing: an older revision arriving after a newer
 * one, a retry of an action that already wrote its revision, and a delivery found through an
 * earlier correction.
 */
class PublicChangelogCorrectionIntegrationTest extends PublicChangelogTestBase {

    private static final String SECOND = "# Release 1.4.0\n\nThe inbox lists every new change.\n";
    private static final String THIRD = "# Release 1.4.0\n\nThe inbox lists each new change.\n";

    @Autowired
    private PublicChangelogService changelog;

    @Test
    void appendsTheNextRevisionWithTheRunsReasonAndTreatsARepeatAsTheOneAlreadyThere() throws Exception {
        Entry entry = entry();
        UUID first = action(entry, 2);

        assertThat(correct(entry, first, entry.delivery(), 2, SECOND).outcome()).isEqualTo(Publication.Outcome.PUBLISHED);
        assertThat(revisions(entry)).containsExactly(Map.of("revision", 2, "note_revision", 2,
                "content_snapshot", SECOND, "reason", "Say what the inbox lists."));

        // The same action again, as a confirmed retry after an unknown outcome: its revision.
        assertThat(correct(entry, first, entry.delivery(), 2, SECOND).outcome()).isEqualTo(Publication.Outcome.PUBLISHED);
        // The same action with other words is not what it wrote.
        assertThat(correct(entry, first, entry.delivery(), 2, THIRD).outcome()).isEqualTo(Publication.Outcome.CONFLICT);
        // Another run delivering what the entry already says: the revision that is already there.
        Publication again = correct(entry, action(entry, 2), entry.delivery(), 2, SECOND);
        assertThat(again.outcome()).isEqualTo(Publication.Outcome.PUBLISHED);
        assertThat(again.url()).endsWith("/releases/" + entry.id());
        assertThat(revisions(entry)).hasSize(1);
    }

    @Test
    void neverReplacesWhatReadersHaveWithAnOlderRevision() throws Exception {
        Entry entry = entry();
        correct(entry, action(entry, 3), entry.delivery(), 3, THIRD);

        // A run carrying revision 2 that reaches the entry after one carrying revision 3.
        assertThat(correct(entry, action(entry, 2), entry.delivery(), 2, SECOND).outcome())
                .isEqualTo(Publication.Outcome.CONFLICT);
        // The same revision in other words.
        assertThat(correct(entry, action(entry, 3), entry.delivery(), 3, SECOND).outcome())
                .isEqualTo(Publication.Outcome.CONFLICT);
        assertThat(revisions(entry)).hasSize(1);
    }

    @Test
    void findsTheEntryThroughTheDeliveryItCorrects() throws Exception {
        Entry entry = entry();
        UUID correction = action(entry, 2);
        correct(entry, correction, entry.delivery(), 2, SECOND);

        // A correction of that correction finds the entry through the revision it wrote.
        assertThat(correct(entry, action(entry, 3), correction, 3, THIRD).outcome())
                .isEqualTo(Publication.Outcome.PUBLISHED);
        assertThat(revisions(entry)).extracting(row -> row.get("revision")).containsExactly(2, 3);

        // A delivery that found the same note already public wrote no entry of its own: its
        // release, its audience as it named it, and its language find the entry.
        UUID matched = action(entry, 1);
        assertThat(changelog.correct(new Publication.Correction(action(entry, 4), entry.organizationId(),
                entry.releaseId(), matched, "End user", "en", 4, "# Release 1.4.0\n\nFourth.\n", "Shorter."))
                .outcome()).isEqualTo(Publication.Outcome.PUBLISHED);
        assertThat(revisions(entry)).hasSize(3);

        // A delivery of another Organization, or none at all, finds nothing.
        assertThat(changelog.correct(new Publication.Correction(action(entry, 5), UUID.randomUUID(),
                entry.releaseId(), entry.delivery(), "End user", "en", 5, "# Fifth\n", "Fifth."))
                .outcome()).isEqualTo(Publication.Outcome.CONFLICT);
        assertThat(revisions(entry)).hasSize(3);
    }

    private Entry entry() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID entryId = publishedEntry(owner, createProject(owner), "1.4.0");
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT organization_id, release_id, action_run_id FROM public_changelog_entries WHERE id = ?", entryId);
        return new Entry(entryId, (UUID) row.get("organization_id"), (UUID) row.get("release_id"),
                (UUID) row.get("action_run_id"), owner.userId());
    }

    private record Entry(UUID id, UUID organizationId, UUID releaseId, UUID delivery, UUID owner) {
    }

    private UUID action(Entry entry, int noteRevision) {
        return correctionActionRun(entry.id(), noteRevision, "Say what the inbox lists.", entry.owner(), "Mai Tran");
    }

    private Publication correct(Entry entry, UUID actionRunId, UUID corrects, int noteRevision, String content) {
        return changelog.correct(new Publication.Correction(actionRunId, entry.organizationId(), entry.releaseId(),
                corrects, "End user", "en", noteRevision, content, "Say what the inbox lists."));
    }

    private List<Map<String, Object>> revisions(Entry entry) {
        return jdbcTemplate.queryForList("""
                SELECT revision, note_revision, content_snapshot, reason FROM public_changelog_entry_revisions
                WHERE entry_id = ? ORDER BY revision
                """, entry.id());
    }
}
