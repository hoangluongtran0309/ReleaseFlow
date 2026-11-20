package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.account.AppUserRole;
import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Corrects a published note by hand (ADR-0046). A correction is a new revision of one
 * audience and language note, with a reason and the person's name as it is now; the note as
 * published stays revision 1, and nothing is delivered until a person asks. Only
 * administrators correct, since a correction changes what people read with no review before
 * it. A retired audience's note is corrected this way too.
 */
@Service
public class ReleaseNoteCorrectionService {

    private final ReleaseRepository releaseRepository;
    private final AudienceReleaseNoteRepository noteRepository;
    private final ReleaseNoteRevisionRepository revisionRepository;
    private final ReleaseNoteRevisions noteRevisions;
    private final Clock clock;

    ReleaseNoteCorrectionService(
            ReleaseRepository releaseRepository,
            AudienceReleaseNoteRepository noteRepository,
            ReleaseNoteRevisionRepository revisionRepository,
            ReleaseNoteRevisions noteRevisions,
            Clock clock
    ) {
        this.releaseRepository = releaseRepository;
        this.noteRepository = noteRepository;
        this.revisionRepository = revisionRepository;
        this.noteRevisions = noteRevisions;
        this.clock = clock;
    }

    /**
     * What a correction starts from: the note as its latest revision reads, and that
     * revision's number, which the correction names.
     */
    @Transactional(readOnly = true)
    public Draft draft(UUID organizationId, UUID projectId, UUID releaseId, UUID noteId) {
        Release release = releaseRepository.findByIdAndOrganizationIdAndProjectId(releaseId, organizationId, projectId)
                .orElseThrow(ReleaseNotFoundException::new);
        AudienceReleaseNote note = note(organizationId, releaseId, noteId);
        requirePublished(release);
        return new Draft(release.getVersion(), noteRevisions.latest(organizationId, note));
    }

    /**
     * Saves a correction written by hand as the note's next revision. It must name the
     * revision that is the latest, and change its text.
     */
    @Transactional
    public ReleaseNoteRevisionView correctByHand(
            ReleaseFlowPrincipal person,
            UUID projectId,
            UUID releaseId,
            UUID noteId,
            NoteCorrectionRequest request
    ) {
        if (person.role() != AppUserRole.ADMIN) {
            throw new AccessDeniedException("Administrator role required.");
        }
        // The release is locked first, as every write to what belongs to it is (#119), so two
        // corrections of the same note serialise and the second reads the first's revision.
        Release release = releaseRepository
                .lockByIdAndOrganizationIdAndProjectId(releaseId, person.organizationId(), projectId)
                .orElseThrow(ReleaseNotFoundException::new);
        AudienceReleaseNote note = note(person.organizationId(), releaseId, noteId);
        requirePublished(release);
        AudienceNoteView latest = noteRevisions.latest(person.organizationId(), note);
        if (request.getCorrects() != latest.revision()) {
            throw new ReleaseNoteRevisionStaleException(latest.revision());
        }
        if (request.getContent().equals(latest.content())) {
            throw new ReleaseNoteCorrectionUnchangedException();
        }
        ReleaseNoteRevision revision = ReleaseNoteRevision.manual(note.getId(), release, latest.revision() + 1,
                request.getContent(), request.getReason(), person.userId(), person.displayName(), now());
        try {
            revisionRepository.saveAndFlush(revision);
        } catch (DataIntegrityViolationException exception) {
            // The database's own check of the next number: someone else's revision won.
            throw new ReleaseNoteRevisionStaleException(latest.revision() + 1);
        }
        return revision.view();
    }

    private AudienceReleaseNote note(UUID organizationId, UUID releaseId, UUID noteId) {
        return noteRepository.findByIdAndReleaseIdAndOrganizationId(noteId, releaseId, organizationId)
                .orElseThrow(ReleaseNoteNotFoundException::new);
    }

    // An approved note is edited in place; only a published one is corrected.
    private static void requirePublished(Release release) {
        if (!release.isPublished()) {
            throw new ReleaseStatusException("error.release_status_conflict.revisionsAfterPublication");
        }
    }

    // PostgreSQL keeps microseconds, so a time must survive a round trip unchanged.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** A correction's starting point: the release's version and the note as it reads now. */
    public record Draft(String version, AudienceNoteView note) {
    }
}
