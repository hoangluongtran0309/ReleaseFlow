package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.translation.GlossaryUse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Revision 2 or later of one published note (ADR-0046). The note as published is revision 1
 * and stays its own row; a revision supersedes the one numbered just below it, and the
 * latest is the note wherever a note is read without a number. The database refuses every
 * update and delete of a revision, and any insert that is not the next number of a
 * published note.
 */
@Entity
@Immutable
@Table(name = "release_note_revisions")
class ReleaseNoteRevision {

    @Id
    private UUID id;

    @Column(name = "note_id", nullable = false, updatable = false)
    private UUID noteId;

    @Column(name = "release_id", nullable = false, updatable = false)
    private UUID releaseId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, updatable = false)
    private int revision;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ReleaseNoteRevisionView.Origin origin;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "author_name", nullable = false, length = 120, updatable = false)
    private String authorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "glossary_status", nullable = false, length = 20, updatable = false)
    private GlossaryUse.Status glossaryStatus;

    @Column(name = "glossary_reason", length = 40, updatable = false)
    private String glossaryReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReleaseNoteRevision() {
    }

    UUID getNoteId() {
        return noteId;
    }

    int getRevision() {
        return revision;
    }

    String getContent() {
        return content;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    GlossaryUse glossary() {
        return new GlossaryUse(glossaryStatus, glossaryReason);
    }

    ReleaseNoteRevisionView view() {
        return new ReleaseNoteRevisionView(revision, origin, content, reason, authorName, createdAt, glossaryStatus,
                glossaryReason);
    }
}
