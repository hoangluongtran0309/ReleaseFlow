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

    /**
     * A correction rendered again: the text today's writer gives from the answers the note was
     * published with, claiming the glossary only as those answers used it.
     */
    static ReleaseNoteRevision rendered(
            UUID noteId,
            Release release,
            int revision,
            String content,
            String reason,
            GlossaryUse glossary,
            UUID authorId,
            String authorName,
            Instant createdAt
    ) {
        ReleaseNoteRevision written = manual(noteId, release, revision, content, reason, authorId, authorName,
                createdAt);
        written.origin = ReleaseNoteRevisionView.Origin.RENDERED;
        written.glossaryStatus = glossary.status();
        written.glossaryReason = glossary.reason();
        return written;
    }

    /** A correction a person wrote by hand: it claims no glossary, as an edit does not. */
    static ReleaseNoteRevision manual(
            UUID noteId,
            Release release,
            int revision,
            String content,
            String reason,
            UUID authorId,
            String authorName,
            Instant createdAt
    ) {
        ReleaseNoteRevision written = new ReleaseNoteRevision();
        written.id = UUID.randomUUID();
        written.noteId = noteId;
        written.releaseId = release.getId();
        written.organizationId = release.getOrganizationId();
        written.projectId = release.getProjectId();
        written.revision = revision;
        written.content = content;
        written.origin = ReleaseNoteRevisionView.Origin.MANUAL;
        written.reason = reason;
        written.authorId = authorId;
        written.authorName = authorName;
        written.glossaryStatus = GlossaryUse.Status.NONE;
        written.glossaryReason = null;
        written.createdAt = createdAt;
        return written;
    }

    UUID getNoteId() {
        return noteId;
    }

    int getRevision() {
        return revision;
    }

    ReleaseNoteRevisionView.Origin getOrigin() {
        return origin;
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
