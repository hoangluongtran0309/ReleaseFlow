package com.hoangluongtran0309.releaseflow.release;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reads notes as their latest revision says (ADR-0046). Wherever a note is read without a
 * number, the release page, the download, and the content an automation run snapshots, the
 * latest revision is the note. With no correction, that is the note exactly as published.
 */
@Component
class ReleaseNoteRevisions {

    private final ReleaseNoteRevisionRepository revisionRepository;

    ReleaseNoteRevisions(ReleaseNoteRevisionRepository revisionRepository) {
        this.revisionRepository = revisionRepository;
    }

    /** Each of a release's notes as its latest revision reads. */
    List<AudienceNoteView> latest(UUID organizationId, UUID releaseId, List<AudienceReleaseNote> notes) {
        if (notes.isEmpty()) {
            return List.of();
        }
        return AudienceReleaseNote.views(notes,
                revisionRepository.findLatestByReleaseIdAndOrganizationId(releaseId, organizationId));
    }

    /** One note as its latest revision reads. */
    AudienceNoteView latest(UUID organizationId, AudienceReleaseNote note) {
        List<ReleaseNoteRevision> revisions = revisionRepository
                .findAllByNoteIdAndOrganizationIdOrderByRevisionAsc(note.getId(), organizationId);
        return note.view(revisions.isEmpty() ? null : revisions.getLast());
    }

    /**
     * Whether a person wrote the note's text: it was edited before publication, or its latest
     * revision was written by hand. Such a note never follows its template again (ADR-0046),
     * and is delivered as written (ADR-0046, amended 2026-11-20).
     */
    boolean writtenByPerson(UUID organizationId, AudienceReleaseNote note) {
        if (!note.isAutoRerender()) {
            return true;
        }
        List<ReleaseNoteRevision> revisions = revisionRepository
                .findAllByNoteIdAndOrganizationIdOrderByRevisionAsc(note.getId(), organizationId);
        return !revisions.isEmpty() && revisions.getLast().getOrigin() == ReleaseNoteRevisionView.Origin.MANUAL;
    }

    /** The reasons of a note's revisions after {@code after}, oldest first. */
    List<String> reasonsAfter(UUID organizationId, UUID noteId, int after) {
        return revisionRepository.findAllByNoteIdAndOrganizationIdOrderByRevisionAsc(noteId, organizationId).stream()
                .filter(revision -> revision.getRevision() > after)
                .map(ReleaseNoteRevision::getReason)
                .toList();
    }

    /** Every revision of a published note, oldest first: revision 1 is the note as published. */
    List<ReleaseNoteRevisionView> history(Release release, AudienceReleaseNote note) {
        List<ReleaseNoteRevisionView> history = new ArrayList<>();
        history.add(note.publishedRevision(release));
        revisionRepository.findAllByNoteIdAndOrganizationIdOrderByRevisionAsc(note.getId(), release.getOrganizationId())
                .forEach(revision -> history.add(revision.view()));
        return history;
    }
}
