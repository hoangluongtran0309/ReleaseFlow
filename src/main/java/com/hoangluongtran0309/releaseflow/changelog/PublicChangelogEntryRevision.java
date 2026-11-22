package com.hoangluongtran0309.releaseflow.changelog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * A correction of a public entry (ADR-0046, part 4): the corrected text, the reason its
 * correction run gave, and when. Revision 1 is the entry itself, so a row is revision 2 or
 * later. Only a correction run appends one, and the database refuses an update or a delete
 * outright. The entry row never changes, and nothing here names the person who asked for the
 * correction.
 */
@Entity
@Immutable
@Table(name = "public_changelog_entry_revisions")
class PublicChangelogEntryRevision {

    @Id
    private UUID id;

    @Column(name = "entry_id", nullable = false, updatable = false)
    private UUID entryId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, updatable = false)
    private int revision;

    @Column(name = "content_snapshot", nullable = false, updatable = false, columnDefinition = "text")
    private String contentSnapshot;

    @Column(nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "corrected_at", nullable = false, updatable = false)
    private Instant correctedAt;

    @Column(name = "note_revision", nullable = false, updatable = false)
    private int noteRevision;

    @Column(name = "action_run_id", nullable = false, updatable = false)
    private UUID actionRunId;

    protected PublicChangelogEntryRevision() {
    }

    PublicChangelogEntryRevision(
            UUID id,
            PublicChangelogEntry entry,
            int revision,
            int noteRevision,
            UUID actionRunId,
            String content,
            String reason,
            Instant correctedAt
    ) {
        this.id = id;
        this.entryId = entry.getId();
        this.organizationId = entry.getOrganizationId();
        this.revision = revision;
        this.noteRevision = noteRevision;
        this.actionRunId = actionRunId;
        this.contentSnapshot = content;
        this.reason = reason;
        this.correctedAt = correctedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getEntryId() {
        return entryId;
    }

    int getRevision() {
        return revision;
    }

    int getNoteRevision() {
        return noteRevision;
    }

    String getContentSnapshot() {
        return contentSnapshot;
    }

    String getReason() {
        return reason;
    }

    Instant getCorrectedAt() {
        return correctedAt;
    }
}
