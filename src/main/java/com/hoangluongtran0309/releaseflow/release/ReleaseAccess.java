package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.change.ChangeInboxService;
import com.hoangluongtran0309.releaseflow.change.ChangeView;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The only way other capabilities read a release and the notes it froze, always
 * scoped by Organization. It reads; a release is changed through its own service.
 */
@Component
public class ReleaseAccess {

    private final ReleaseRepository releaseRepository;
    private final AudienceReleaseNoteRepository noteRepository;
    private final ReleaseChangeRepository releaseChangeRepository;
    private final ChangeInboxService changeService;
    private final ReleaseNoteRevisions noteRevisions;

    ReleaseAccess(
            ReleaseRepository releaseRepository,
            AudienceReleaseNoteRepository noteRepository,
            ReleaseNoteRevisions noteRevisions,
            ReleaseChangeRepository releaseChangeRepository,
            ChangeInboxService changeService
    ) {
        this.releaseRepository = releaseRepository;
        this.noteRepository = noteRepository;
        this.noteRevisions = noteRevisions;
        this.releaseChangeRepository = releaseChangeRepository;
        this.changeService = changeService;
    }

    @Transactional(readOnly = true)
    public Optional<ReleaseSnapshot> find(UUID organizationId, UUID releaseId) {
        if (releaseId == null) {
            return Optional.empty();
        }
        return releaseRepository.findById(releaseId)
                .filter(release -> release.getOrganizationId().equals(organizationId))
                .map(this::snapshot);
    }

    /**
     * The release for a run about to be created, its row locked first and held until the
     * caller's transaction ends (#119, #122). A published note can change now (ADR-0046), and
     * a correction saves its revision under this same lock, so the notes a run snapshots are
     * the ones as of a moment no revision is half-way through, and a correction's offer and
     * the run it starts read the same revisions. It joins the caller's transaction and refuses
     * to run without one, since a lock released on return would protect nothing.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ReleaseSnapshot> lockForRun(UUID organizationId, UUID releaseId) {
        if (releaseId == null) {
            return Optional.empty();
        }
        return releaseRepository.lockByIdAndOrganizationId(releaseId, organizationId).map(this::snapshot);
    }

    /**
     * The reasons of a note's revisions after {@code after}, oldest first: what a destination
     * holding revision {@code after} has not been told, for a person to write a correction's
     * reason from.
     */
    @Transactional(readOnly = true)
    public List<String> reasonsAfter(UUID organizationId, UUID noteId, int after) {
        return noteRevisions.reasonsAfter(organizationId, noteId, after);
    }

    private ReleaseSnapshot snapshot(Release release) {
        return new ReleaseSnapshot(
                release.getId(),
                release.getProjectId(),
                release.getVersion(),
                release.getStatus(),
                // A note is its latest revision, so a new run delivers the corrected text.
                noteRevisions.latest(release.getOrganizationId(), release.getId(), noteRepository
                        .findAllByReleaseIdAndOrganizationIdOrderByAudienceNameAscAudienceCodeAscLanguageAsc(
                                release.getId(), release.getOrganizationId()))
        );
    }

    /**
     * Whether the release's note for this audience and language has nothing to deliver: it
     * still follows its template, and no change the release includes concerns its audience,
     * by the same rule the digest uses to choose a note's changes (ADR-0035, amended
     * 2026-11-19). The changes are read as they are now, never the note's text. A note a
     * person wrote, or no note at all, is never one with nothing; a note whose latest
     * revision was written by hand is one a person wrote (ADR-0046, amended 2026-11-20).
     */
    @Transactional(readOnly = true)
    public boolean hasNothingFor(UUID organizationId, UUID releaseId, UUID audienceId, String language) {
        Optional<Release> release = releaseRepository.findById(releaseId)
                .filter(candidate -> candidate.getOrganizationId().equals(organizationId));
        if (release.isEmpty()) {
            return false;
        }
        Optional<AudienceReleaseNote> note = noteRepository
                .findAllByReleaseIdAndOrganizationIdOrderByAudienceNameAscAudienceCodeAscLanguageAsc(releaseId, organizationId)
                .stream()
                .filter(candidate -> candidate.view().audienceId().equals(audienceId)
                        && candidate.getLanguage().equals(language))
                .findFirst();
        if (note.isEmpty() || noteRevisions.writtenByPerson(organizationId, note.get())) {
            return false;
        }
        List<UUID> ids = releaseChangeRepository.findAllByReleaseIdAndOrganizationId(releaseId, organizationId)
                .stream()
                .map(ReleaseChange::getChangeId)
                .toList();
        List<ChangeView> changes = changeService.changes(organizationId, release.get().getProjectId(), ids);
        String audienceCode = note.get().getAudienceCode();
        return changes.stream().noneMatch(change -> ReleaseNoteDigest.concerns(change, audienceCode));
    }

    /** The Organization's published releases, newest first, for choosing one to deliver. */
    @Transactional(readOnly = true)
    public List<PublishedRelease> published(UUID organizationId) {
        return releaseRepository
                .findAllByOrganizationIdAndStatusOrderByCreatedAtDescIdDesc(organizationId, ReleaseStatus.PUBLISHED)
                .stream()
                .map(release -> new PublishedRelease(
                        release.getId(), release.getProjectId(), release.getVersion(), release.getPublishedAt()))
                .toList();
    }

    /**
     * The approved releases whose planned time falls in {@code (after, until]}, oldest
     * first, for a rule that reminds people before one goes out. A null project means
     * every project of the Organization. A release with no planned time is not upcoming.
     */
    @Transactional(readOnly = true)
    public List<UpcomingRelease> upcomingApproved(UUID organizationId, UUID projectId, Instant after, Instant until) {
        if (!until.isAfter(after)) {
            return List.of();
        }
        return releaseRepository
                .findAllByOrganizationIdAndStatusAndPlannedReleaseAtGreaterThanAndPlannedReleaseAtLessThanEqualOrderByPlannedReleaseAtAscIdAsc(
                        organizationId, ReleaseStatus.APPROVED, after, until)
                .stream()
                .filter(release -> projectId == null || projectId.equals(release.getProjectId()))
                .map(release -> new UpcomingRelease(
                        release.getId(), release.getProjectId(), release.getVersion(), release.getPlannedReleaseAt()))
                .toList();
    }

    /** One published release, named the way a person picks it. */
    public record PublishedRelease(UUID id, UUID projectId, String version, Instant publishedAt) {
    }

    /** An approved release that has not gone out yet, and the moment it is planned for. */
    public record UpcomingRelease(UUID id, UUID projectId, String version, Instant plannedReleaseAt) {
    }

    /** A release and the notes it holds, at the moment it was read. */
    public record ReleaseSnapshot(
            UUID releaseId,
            UUID projectId,
            String version,
            ReleaseStatus status,
            List<AudienceNoteView> notes
    ) {

        public ReleaseSnapshot {
            notes = List.copyOf(notes);
        }
    }
}
