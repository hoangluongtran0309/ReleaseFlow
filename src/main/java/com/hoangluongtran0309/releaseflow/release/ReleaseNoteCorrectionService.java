package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.account.AppUserRole;
import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.audience.AudienceService;
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
    private final ReleaseService releaseService;
    private final AudienceService audienceService;
    private final Clock clock;

    ReleaseNoteCorrectionService(
            ReleaseRepository releaseRepository,
            AudienceReleaseNoteRepository noteRepository,
            ReleaseNoteRevisionRepository revisionRepository,
            ReleaseNoteRevisions noteRevisions,
            ReleaseService releaseService,
            AudienceService audienceService,
            Clock clock
    ) {
        this.releaseService = releaseService;
        this.audienceService = audienceService;
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
        String refusal = audienceService.retiredCodes(organizationId).contains(note.getAudienceCode())
                ? "ui.noteCorrection.renderRetired"
                : noteRevisions.writtenByPerson(organizationId, note) ? "ui.noteCorrection.renderManual" : null;
        return new Draft(release.getVersion(), noteRevisions.latest(organizationId, note), refusal);
    }

    /**
     * What rendering the note again gives now, for a person to read before saving it
     * (ADR-0046). Every member may read it; nothing is stored and no provider is called.
     */
    @Transactional(readOnly = true)
    public CorrectionPreview previewRendered(UUID organizationId, UUID projectId, UUID releaseId, UUID noteId) {
        RenderedNote rendered = releaseService.renderAgain(organizationId, projectId, releaseId, noteId);
        return new CorrectionPreview(ReleaseNoteRevisionView.Origin.RENDERED, rendered.corrects(), rendered.content(),
                rendered.previewHash(), rendered.changed(), rendered.missingTexts(), rendered.missingCharacters());
    }

    /**
     * Saves a correction as the note's next revision, by hand or rendered again. It must name
     * the revision that is the latest, and change its text; one rendered again must also be
     * the text of the preview the person read, with every text the note uses found.
     */
    @Transactional
    public ReleaseNoteRevisionView correct(
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
        ReleaseNoteRevision revision = request.getOrigin() == ReleaseNoteRevisionView.Origin.RENDERED
                ? rendered(person, projectId, release, note, latest, request)
                : manual(person, release, note, latest, request);
        try {
            revisionRepository.saveAndFlush(revision);
        } catch (DataIntegrityViolationException exception) {
            // The database's own check of the next number: someone else's revision won.
            throw new ReleaseNoteRevisionStaleException(latest.revision() + 1);
        }
        return revision.view();
    }

    private ReleaseNoteRevision manual(
            ReleaseFlowPrincipal person,
            Release release,
            AudienceReleaseNote note,
            AudienceNoteView latest,
            NoteCorrectionRequest request
    ) {
        if (request.getContent().equals(latest.content())) {
            throw new ReleaseNoteCorrectionUnchangedException();
        }
        return ReleaseNoteRevision.manual(note.getId(), release, latest.revision() + 1, request.getContent(),
                request.getReason(), person.userId(), person.displayName(), now());
    }

    // Rendered again inside the lock, so what is saved is what the preview showed, or refused.
    private ReleaseNoteRevision rendered(
            ReleaseFlowPrincipal person,
            UUID projectId,
            Release release,
            AudienceReleaseNote note,
            AudienceNoteView latest,
            NoteCorrectionRequest request
    ) {
        RenderedNote rendered = releaseService.renderAgain(person.organizationId(), projectId, release.getId(),
                note.getId());
        if (!rendered.previewHash().equals(request.getPreviewHash())) {
            throw new ReleaseNotePreviewChangedException();
        }
        if (!rendered.changed()) {
            throw new ReleaseNoteCorrectionUnchangedException();
        }
        if (rendered.missingTexts() > 0) {
            throw new ReleaseNoteRenderIncompleteException(rendered.missingTexts());
        }
        return ReleaseNoteRevision.rendered(note.getId(), release, latest.revision() + 1, rendered.content(),
                request.getReason(), rendered.glossary(), person.userId(), person.displayName(), now());
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

    /**
     * What a correction would save: how its text was made, the revision it corrects, the text,
     * the hash saving sends back, whether it differs from that revision, and how many of the
     * note's texts had no answer and how long they are, which is what DeepL would be sent.
     */
    public record CorrectionPreview(
            ReleaseNoteRevisionView.Origin origin,
            int corrects,
            String content,
            String previewHash,
            boolean changed,
            int missingTexts,
            int missingCharacters
    ) {
    }

    /**
     * A correction's starting point: the release's version, the note as it reads now, and why
     * it cannot be rendered again, as a bundle key, or null when it can.
     */
    public record Draft(String version, AudienceNoteView note, String renderRefusal) {

        public boolean renderable() {
            return renderRefusal == null;
        }
    }
}
