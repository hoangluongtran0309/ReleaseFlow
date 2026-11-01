package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.account.OutputLanguageService;
import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.audience.AudienceService;
import com.hoangluongtran0309.releaseflow.audience.AudienceTemplateRenderException;
import com.hoangluongtran0309.releaseflow.audience.AudienceView;
import com.hoangluongtran0309.releaseflow.audience.ReleaseLanguageService;
import com.hoangluongtran0309.releaseflow.audience.ReleaseLinks;
import com.hoangluongtran0309.releaseflow.change.ChangeExcludedException;
import com.hoangluongtran0309.releaseflow.change.ChangeInboxService;
import com.hoangluongtran0309.releaseflow.change.ChangeNotFoundException;
import com.hoangluongtran0309.releaseflow.change.ChangeReviewService;
import com.hoangluongtran0309.releaseflow.change.ChangeSummaryRequest;
import com.hoangluongtran0309.releaseflow.change.ChangeSummaryService;
import com.hoangluongtran0309.releaseflow.change.ChangeView;
import com.hoangluongtran0309.releaseflow.change.InvalidChangeReviewException;
import com.hoangluongtran0309.releaseflow.project.ProjectService;
import com.hoangluongtran0309.releaseflow.translation.TranslationFinished;
import com.hoangluongtran0309.releaseflow.translation.TranslationState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Releases of a Project's changes, from draft through review and approval to publication.
 * Every operation is scoped by Organization and Project; a change belongs to at most one
 * release, and a published release never changes again. Approval writes one note per
 * audience and release note language; publication waits until every note is ready and
 * freezes them.
 */
@Service
// Public only for the counts the workspace overview reads; everything else stays
// package-private, and the class is still reached through its own controllers.
public class ReleaseService {

    private static final String VERSION_CONSTRAINT = "releases_project_version_unique";
    private static final String CHANGE_UNIQUE_CONSTRAINT = "release_changes_change_unique";
    private static final String CHANGE_NOT_EXCLUDED_TRIGGER = "release_changes_change_not_excluded";
    private static final TypeReference<List<ReleaseNoteSection>> SECTIONS = new TypeReference<>() {
    };

    private final ProjectService projectService;
    private final ChangeInboxService changeService;
    private final ChangeReviewService changeReviewService;
    private final ChangeSummaryService changeSummaryService;
    private final AudienceService audienceService;
    private final ReleaseLanguageService releaseLanguageService;
    private final ReleaseNoteWriter noteWriter;
    private final ReleaseLinkBuilder linkBuilder;
    private final OutputLanguageService outputLanguageService;
    private final ReleaseRepository releaseRepository;
    private final ReleaseChangeRepository releaseChangeRepository;
    private final ReleaseChangeReviewRepository reviewRepository;
    private final AudienceReleaseNoteRepository noteRepository;
    private final LegacyReleaseNoteRepository legacyNoteRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ReleaseService(
            ProjectService projectService,
            ChangeInboxService changeService,
            ChangeReviewService changeReviewService,
            ChangeSummaryService changeSummaryService,
            AudienceService audienceService,
            ReleaseLanguageService releaseLanguageService,
            ReleaseNoteWriter noteWriter,
            ReleaseLinkBuilder linkBuilder,
            OutputLanguageService outputLanguageService,
            ReleaseRepository releaseRepository,
            ReleaseChangeRepository releaseChangeRepository,
            ReleaseChangeReviewRepository reviewRepository,
            AudienceReleaseNoteRepository noteRepository,
            LegacyReleaseNoteRepository legacyNoteRepository,
            ObjectMapper objectMapper,
            ApplicationEventPublisher events,
            Clock clock
    ) {
        this.projectService = projectService;
        this.changeService = changeService;
        this.changeReviewService = changeReviewService;
        this.changeSummaryService = changeSummaryService;
        this.audienceService = audienceService;
        this.releaseLanguageService = releaseLanguageService;
        this.noteWriter = noteWriter;
        this.linkBuilder = linkBuilder;
        this.outputLanguageService = outputLanguageService;
        this.releaseRepository = releaseRepository;
        this.releaseChangeRepository = releaseChangeRepository;
        this.reviewRepository = reviewRepository;
        this.noteRepository = noteRepository;
        this.legacyNoteRepository = legacyNoteRepository;
        this.objectMapper = objectMapper;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    List<ReleaseSummary> list(UUID organizationId, UUID projectId) {
        projectService.get(organizationId, projectId);
        return releaseRepository.findAllByOrganizationIdAndProjectIdOrderByCreatedAtDescIdDesc(organizationId, projectId)
                .stream()
                .map(release -> new ReleaseSummary(
                        release.getId(),
                        release.getVersion(),
                        release.getSummary(),
                        release.getStatus(),
                        releaseChangeRepository.countByReleaseIdAndOrganizationId(release.getId(), organizationId),
                        reviewRepository.countByReleaseIdAndOrganizationId(release.getId(), organizationId),
                        release.getCreatedAt(),
                        release.getUpdatedAt(),
                        release.getPlannedReleaseAt(),
                        release.getApprovedAt(),
                        release.getPublishedAt()
                ))
                .toList();
    }

    /**
     * What the workspace overview says about this Project. The unassigned count is the
     * processed changes that belong to no release yet, which is what the overview offers
     * to do something about.
     */
    @Transactional(readOnly = true)
    public ReleaseCounts counts(UUID organizationId, UUID projectId) {
        ReleaseStatusCounts releases = releaseRepository.countByStatus(organizationId, projectId);
        Set<UUID> inAnyRelease = membership(organizationId, projectId).keySet();
        long unassigned = changeService.releasableChanges(organizationId, projectId).stream()
                .filter(change -> !inAnyRelease.contains(change.id()))
                .count();
        return new ReleaseCounts(
                releases.total(),
                releases.drafts(),
                releases.inReview(),
                releases.approved(),
                releases.published(),
                unassigned
        );
    }

    /**
     * The published releases of a Project, most recently published first: what a release
     * may follow. The first is what a new release follows unless a person chooses.
     */
    @Transactional(readOnly = true)
    List<ReleaseOption> previousCandidates(UUID organizationId, UUID projectId) {
        projectService.get(organizationId, projectId);
        return published(organizationId, projectId).stream()
                .map(release -> new ReleaseOption(release.getId(), release.getVersion()))
                .toList();
    }

    /**
     * Creates a draft. Without a previous release named, it follows the Project's most
     * recently published release, if any; that choice does not move when another release
     * is published later.
     */
    @Transactional
    ReleaseView createDraft(UUID organizationId, UUID projectId, NewReleaseRequest request) {
        projectService.get(organizationId, projectId);
        Instant plannedReleaseAt = request.plannedInstant();
        String upgradeGuideUrl = ReleaseLinkBuilder.validatedUpgradeGuideUrl(request.getUpgradeGuideUrl());
        UUID previousReleaseId = request.previousReleaseIdGiven()
                ? previousRelease(organizationId, projectId, request.getPreviousReleaseId(), null)
                : published(organizationId, projectId).stream().findFirst().map(Release::getId).orElse(null);
        if (releaseRepository.existsByOrganizationIdAndProjectIdAndVersionIgnoreCase(
                organizationId,
                projectId,
                request.getVersion()
        )) {
            throw new ReleaseVersionTakenException();
        }
        Instant now = clock.instant();
        Release release = new Release(
                UUID.randomUUID(),
                organizationId,
                projectId,
                request.getVersion(),
                request.getSummary(),
                now
        );
        release.schedule(plannedReleaseAt, now);
        release.link(upgradeGuideUrl, previousReleaseId, now);
        try {
            releaseRepository.saveAndFlush(release);
        } catch (DataIntegrityViolationException exception) {
            throw translate(exception);
        }
        return view(release, List.of());
    }

    @Transactional(readOnly = true)
    ReleaseView get(UUID organizationId, UUID projectId, UUID releaseId) {
        Release release = find(organizationId, projectId, releaseId);
        return view(release, includedChanges(release));
    }

    @Transactional
    ReleaseView edit(UUID organizationId, UUID projectId, UUID releaseId, ReleaseRequest request) {
        Release release = find(organizationId, projectId, releaseId);
        release.requireDraft();
        if (releaseRepository.existsByOrganizationIdAndProjectIdAndVersionIgnoreCaseAndIdNot(
                organizationId,
                projectId,
                request.getVersion(),
                releaseId
        )) {
            throw new ReleaseVersionTakenException();
        }
        // A field the caller left out keeps its value.
        String upgradeGuideUrl = request.upgradeGuideUrlGiven()
                ? ReleaseLinkBuilder.validatedUpgradeGuideUrl(request.getUpgradeGuideUrl())
                : release.getUpgradeGuideUrl();
        UUID previousReleaseId = request.previousReleaseIdGiven()
                ? previousRelease(organizationId, projectId, request.getPreviousReleaseId(), releaseId)
                : release.getPreviousReleaseId();
        Instant now = clock.instant();
        release.edit(request.getVersion(), request.getSummary(), now);
        release.link(upgradeGuideUrl, previousReleaseId, now);
        try {
            releaseRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translate(exception);
        }
        return view(release, includedChanges(release));
    }

    @Transactional
    ReleaseView schedule(UUID organizationId, UUID projectId, UUID releaseId, ReleaseScheduleRequest request) {
        Release release = find(organizationId, projectId, releaseId);
        release.requireUnpublished();
        release.schedule(request.plannedInstant(), clock.instant());
        return view(release, includedChanges(release));
    }

    // The database cascade removes the release's decisions and returns its changes to the
    // available pool.
    @Transactional
    void discard(UUID organizationId, UUID projectId, UUID releaseId) {
        Release release = find(organizationId, projectId, releaseId);
        release.requireUnpublished();
        releaseRepository.delete(release);
    }

    @Transactional(readOnly = true)
    List<ChangeView> availableChanges(UUID organizationId, UUID projectId, UUID releaseId) {
        find(organizationId, projectId, releaseId);
        Set<UUID> inAnyRelease = membership(organizationId, projectId).keySet();
        return changeService.releasableChanges(organizationId, projectId).stream()
                .filter(change -> !inAnyRelease.contains(change.id()))
                .toList();
    }

    @Transactional
    ReleaseView addChanges(UUID organizationId, UUID projectId, UUID releaseId, ReleaseChangesRequest request) {
        Release release = find(organizationId, projectId, releaseId);
        release.requireDraft();
        Map<UUID, UUID> membership = membership(organizationId, projectId);

        List<ChangeView> candidates;
        if (request.isAllAvailable()) {
            candidates = changeService.releasableChanges(organizationId, projectId);
        } else {
            Set<UUID> ids = new LinkedHashSet<>(request.getChangeIds());
            candidates = changeService.changes(organizationId, projectId, ids);
            if (candidates.size() != ids.size()) {
                throw new ChangeNotFoundException();
            }
            for (ChangeView change : candidates) {
                UUID owner = membership.get(change.id());
                if (change.processing() || (owner != null && !owner.equals(release.getId()))) {
                    throw new ChangeNotReleasableException();
                }
                if (change.excluded() && owner == null) {
                    throw new ChangeExcludedException();
                }
            }
        }

        Instant now = clock.instant();
        List<ReleaseChange> additions = candidates.stream()
                .filter(change -> !membership.containsKey(change.id()))
                .map(change -> new ReleaseChange(release.getId(), change.id(), organizationId, projectId, now))
                .toList();
        try {
            releaseChangeRepository.saveAllAndFlush(additions);
        } catch (DataIntegrityViolationException exception) {
            if (violates(exception, CHANGE_NOT_EXCLUDED_TRIGGER)) {
                // Excluded after this request read it; the database had the last word.
                throw new ChangeExcludedException();
            }
            throw violates(exception, CHANGE_UNIQUE_CONSTRAINT) ? new ChangeNotReleasableException() : exception;
        }
        if (!additions.isEmpty()) {
            release.touch(now);
        }
        return view(release, includedChanges(release));
    }

    /**
     * Removes a change from a draft, or rejects it during review. Its decision goes with it,
     * and the change becomes available for another release.
     */
    @Transactional
    ReleaseView removeChange(UUID organizationId, UUID projectId, UUID releaseId, UUID changeId) {
        Release release = find(organizationId, projectId, releaseId);
        release.requireChangesRemovable();
        if (releaseChangeRepository.deleteByReleaseIdAndChangeIdAndOrganizationId(releaseId, changeId, organizationId) == 0) {
            throw new ChangeNotFoundException();
        }
        release.touch(clock.instant());
        return view(release, includedChanges(release));
    }

    @Transactional
    ReleaseView requestReview(UUID organizationId, UUID projectId, UUID releaseId) {
        Release release = find(organizationId, projectId, releaseId);
        release.requireDraft();
        List<ChangeView> changes = includedChanges(release);
        if (changes.isEmpty()) {
            throw new ReleaseEmptyException();
        }
        release.requestReview(clock.instant());
        return view(release, changes);
    }

    /**
     * Records a decision on one change of a release in review. Approving confirms the
     * classification the reviewer saw; editing corrects it. Either way the change itself is
     * reviewed by the same person, exactly as in the Change Inbox.
     */
    @Transactional
    ReleaseView decide(
            ReleaseFlowPrincipal reviewer,
            UUID projectId,
            UUID releaseId,
            UUID changeId,
            ReleaseDecisionRequest request
    ) {
        UUID organizationId = reviewer.organizationId();
        Release release = find(organizationId, projectId, releaseId);
        release.requireInReview();
        ChangeView change = includedChanges(release).stream()
                .filter(included -> included.id().equals(changeId))
                .findFirst()
                .orElseThrow(ChangeNotFoundException::new);
        if (request.getAction() == ReviewAction.APPROVE) {
            // Approving keeps the classification as it is, and Unknown is no classification.
            if (change.unknownCategory()) {
                throw new InvalidChangeReviewException("error.invalid_change_review.approveUnknown");
            }
            if (!change.category().equalsIgnoreCase(request.getCategory())
                    || change.breaking() != request.getBreaking()
                    || (request.getSecurity() != null && change.security() != request.getSecurity())) {
                throw new ClassificationChangedException();
            }
        }
        changeReviewService.review(reviewer, projectId, changeId, request.changeReview());

        Instant now = clock.instant();
        ReleaseChangeReview decision = reviewRepository.findById(new ReleaseChange.Key(releaseId, changeId))
                .orElseGet(() -> new ReleaseChangeReview(releaseId, changeId, organizationId, projectId));
        decision.record(request.getAction(), reviewer.userId(), reviewer.displayName(), request.getNote(), now);
        reviewRepository.saveAndFlush(decision);
        release.touch(now);
        return view(release, includedChanges(release));
    }

    @Transactional
    ReleaseView approve(ReleaseFlowPrincipal approver, UUID projectId, UUID releaseId) {
        Release release = find(approver.organizationId(), projectId, releaseId);
        release.requireInReview();
        List<ChangeView> changes = includedChanges(release);
        if (changes.isEmpty()) {
            throw new ReleaseEmptyException();
        }
        long decided = reviewRepository.countByReleaseIdAndOrganizationId(releaseId, approver.organizationId());
        if (decided != changes.size() || changes.stream().anyMatch(ChangeView::needsReview)) {
            throw new ReleaseReviewIncompleteException();
        }
        List<String> titleOnly = changes.stream()
                .filter(ReleaseNotePreview::needsSummary)
                .map(ChangeView::reference)
                .toList();
        if (!titleOnly.isEmpty()) {
            throw new ChangeSummaryRequiredException(titleOnly);
        }
        Instant now = clock.instant();
        List<AudienceReleaseNote> notes = writeNotes(release, changes, now);
        release.approve(approver.userId(), approver.displayName(), now);
        // The database accepts notes only for an approved release.
        releaseRepository.flush();
        noteRepository.saveAllAndFlush(notes);
        return view(release, changes);
    }

    /**
     * Reopens a release in review or approved. Its decisions, approval, and notes are
     * dropped; the reviews and summaries recorded on its changes stay.
     */
    @Transactional
    ReleaseView returnToDraft(UUID organizationId, UUID projectId, UUID releaseId) {
        Release release = find(organizationId, projectId, releaseId);
        release.returnToDraft(clock.instant());
        // The database keeps an approved release's decisions fixed, so the release must be
        // a draft again before they are removed.
        releaseRepository.flush();
        reviewRepository.deleteByReleaseIdAndOrganizationId(releaseId, organizationId);
        noteRepository.deleteByReleaseIdAndOrganizationId(releaseId, organizationId);
        reviewRepository.flush();
        return view(release, includedChanges(release));
    }

    /**
     * Freezes an approved release together with its audience notes, recording who
     * published it and when. Every note must be ready first. From then on the database
     * rejects any change to either. Listeners hear about it inside this transaction and
     * may record work to do; none of them may do it here, because anything that throws
     * would undo the publication.
     */
    @Transactional
    ReleaseView publish(ReleaseFlowPrincipal publisher, UUID projectId, UUID releaseId) {
        Release release = find(publisher.organizationId(), projectId, releaseId);
        release.requireUnpublished();
        if (release.getStatus() == ReleaseStatus.APPROVED) {
            List<AudienceReleaseNote> notes = notes(release);
            if (notes.isEmpty()) {
                throw new ReleaseNotesMissingException();
            }
            if (notes.stream().anyMatch(note -> note.getTranslationStatus() != TranslationState.Status.READY)) {
                throw new TranslationsNotReadyException();
            }
        }
        Instant publishedAt = clock.instant();
        release.publish(publisher.userId(), publisher.displayName(), publishedAt);
        try {
            releaseRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translate(exception);
        }
        events.publishEvent(new ReleasePublished(
                publisher.organizationId(),
                projectId,
                releaseId,
                release.getVersion(),
                publisher.userId(),
                publishedAt
        ));
        return view(release, includedChanges(release));
    }

    /**
     * Replaces one audience's note with a person's text while the release is approved.
     * The note never follows its template again.
     */
    @Transactional
    ReleaseView editNote(
            ReleaseFlowPrincipal editor,
            UUID projectId,
            UUID releaseId,
            UUID noteId,
            ReleaseNoteRequest request
    ) {
        Release release = find(editor.organizationId(), projectId, releaseId);
        release.requireUnpublished();
        if (release.getStatus() != ReleaseStatus.APPROVED) {
            throw new ReleaseStatusException("error.release_status_conflict.notesAtApproval");
        }
        AudienceReleaseNote note = noteRepository
                .findByIdAndReleaseIdAndOrganizationId(noteId, releaseId, editor.organizationId())
                .orElseThrow(ReleaseNoteNotFoundException::new);
        Instant now = clock.instant();
        note.edit(request.getContent(), editor.userId(), editor.displayName(), now);
        release.touch(now);
        noteRepository.flush();
        return view(release, includedChanges(release));
    }

    /**
     * Records a person's summary and narratives for one change of a release in review or
     * approved. The notes of an approved release that still follow their templates are
     * rendered again from their stored templates.
     */
    @Transactional
    ReleaseView editSummary(
            ReleaseFlowPrincipal editor,
            UUID projectId,
            UUID releaseId,
            UUID changeId,
            ChangeSummaryRequest request
    ) {
        Release release = find(editor.organizationId(), projectId, releaseId);
        release.requireUnpublished();
        if (release.getStatus() != ReleaseStatus.IN_REVIEW && release.getStatus() != ReleaseStatus.APPROVED) {
            throw new ReleaseStatusException("error.release_status_conflict.summaryStage");
        }
        if (includedChanges(release).stream().noneMatch(change -> change.id().equals(changeId))) {
            throw new ChangeNotFoundException();
        }
        changeSummaryService.edit(editor, projectId, changeId, request);
        Instant now = clock.instant();
        List<ChangeView> changes = includedChanges(release);
        if (release.getStatus() == ReleaseStatus.APPROVED) {
            rerenderNotes(release, changes, null, false, now);
        }
        release.touch(now);
        return view(release, changes);
    }

    /**
     * Starts the failed translations of an approved release over. Its notes that follow
     * their templates wait for them again.
     */
    @Transactional
    ReleaseView retryTranslations(UUID organizationId, UUID projectId, UUID releaseId) {
        Release release = find(organizationId, projectId, releaseId);
        if (release.getStatus() != ReleaseStatus.APPROVED) {
            throw new ReleaseStatusException("error.release_status_conflict.translationStage");
        }
        List<ChangeView> changes = includedChanges(release);
        Instant now = clock.instant();
        rerenderNotes(release, changes, null, true, now);
        release.touch(now);
        return view(release, changes);
    }

    /**
     * A translation finished or failed for good: the notes of the approved release that
     * holds the change, or whose summary it was, in that language, show where it stands.
     * Runs inside the transaction that recorded the result.
     */
    @EventListener
    void onTranslationFinished(TranslationFinished event) {
        Optional<UUID> releaseId = event.releaseId() != null
                ? Optional.of(event.releaseId())
                : releaseChangeRepository.findByChangeIdAndOrganizationId(event.changeId(), event.organizationId())
                .map(ReleaseChange::getReleaseId);
        releaseId.flatMap(releaseRepository::findById)
                .filter(release -> release.getOrganizationId().equals(event.organizationId()))
                .filter(release -> release.getStatus() == ReleaseStatus.APPROVED)
                .ifPresent(release -> rerenderNotes(release, includedChanges(release), event.targetLanguage(), false,
                        clock.instant()));
    }

    @Transactional(readOnly = true)
    List<AudienceNoteView> notes(UUID organizationId, UUID projectId, UUID releaseId) {
        return notes(find(organizationId, projectId, releaseId)).stream().map(AudienceReleaseNote::view).toList();
    }

    /** One audience's note as a Markdown file named {@code <version>-<audience>-<language>.md}. */
    @Transactional(readOnly = true)
    ReleaseNoteFile noteFile(UUID organizationId, UUID projectId, UUID releaseId, UUID noteId) {
        Release release = find(organizationId, projectId, releaseId);
        AudienceReleaseNote note = noteRepository.findByIdAndReleaseIdAndOrganizationId(noteId, releaseId, organizationId)
                .orElseThrow(ReleaseNoteNotFoundException::new);
        // Anything but letters, digits, dots, dashes, and underscores in the version is replaced.
        String name = release.getVersion().replaceAll("[^A-Za-z0-9._-]", "-") + "-" + note.getAudienceCode() + "-"
                + note.getLanguage() + ".md";
        return new ReleaseNoteFile(name, note.view().content());
    }

    /**
     * What each audience's note would say if the release were approved now, from the
     * current changes and templates. Nothing is stored; an empty release has no preview.
     */
    @Transactional(readOnly = true)
    List<AudienceNotePreview> previewNotes(UUID organizationId, UUID projectId, UUID releaseId) {
        Release release = find(organizationId, projectId, releaseId);
        List<ChangeView> changes = includedChanges(release);
        if (changes.isEmpty()) {
            return List.of();
        }
        String language = outputLanguageService.outputLanguage(organizationId).tag();
        ReleaseLinks links = links(release);
        return audienceService.list(organizationId).stream()
                .map(audience -> new AudienceNotePreview(
                        audience.code(),
                        audience.displayName(),
                        render(release.getSummary(), changes, audience.templateBody(), audience.footerTemplateBody(), links,
                                audience.code(), language, audience.displayName())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    List<ReleaseAssignment> assignments(UUID organizationId, UUID projectId) {
        projectService.get(organizationId, projectId);
        Map<UUID, Release> releases = releaseRepository
                .findAllByOrganizationIdAndProjectIdOrderByCreatedAtDescIdDesc(organizationId, projectId)
                .stream()
                .collect(Collectors.toMap(Release::getId, Function.identity()));
        return releaseChangeRepository.findAllByOrganizationIdAndProjectId(organizationId, projectId).stream()
                .map(item -> {
                    Release release = releases.get(item.getReleaseId());
                    return new ReleaseAssignment(item.getChangeId(), release.getId(), release.getVersion(), release.getStatus());
                })
                .toList();
    }

    // One note per current audience and release note language, from a snapshot of the
    // audience's template and footer for that language.
    private List<AudienceReleaseNote> writeNotes(Release release, List<ChangeView> changes, Instant now) {
        List<AudienceView> audiences = audienceService.list(release.getOrganizationId());
        List<AudienceReleaseNote> notes = new ArrayList<>();
        ReleaseLinks links = links(release);
        for (String language : releaseLanguageService.targetLanguages(release.getOrganizationId())) {
            ReleaseNoteWriter.Localized localized = noteWriter.localize(release, changes, language, false);
            for (AudienceView audience : audiences) {
                String template = audience.templateFor(language);
                String footer = audience.footerFor(language);
                notes.add(new AudienceReleaseNote(
                        UUID.randomUUID(),
                        release,
                        audience.id(),
                        audience.code(),
                        audience.displayName(),
                        language,
                        template,
                        footer,
                        render(localized.summary(), localized.changes(), template, footer, links, audience.code(), language,
                                audience.displayName()),
                        localized.status(),
                        localized.glossary(),
                        now
                ));
            }
        }
        return notes;
    }

    /**
     * Renders again the notes that follow their templates, in one language or all of
     * them, from the release's changes as they now read in each language.
     */
    private void rerenderNotes(Release release, List<ChangeView> changes, String onlyLanguage, boolean retry, Instant now) {
        Map<String, ReleaseNoteWriter.Localized> byLanguage = new LinkedHashMap<>();
        ReleaseLinks links = links(release);
        for (AudienceReleaseNote note : notes(release)) {
            if (!note.isAutoRerender() || (onlyLanguage != null && !onlyLanguage.equals(note.getLanguage()))) {
                continue;
            }
            ReleaseNoteWriter.Localized localized = byLanguage.computeIfAbsent(note.getLanguage(),
                    language -> noteWriter.localize(release, changes, language, retry));
            note.rerender(render(localized.summary(), localized.changes(), note.getTemplateBodySnapshot(),
                    note.getFooterTemplateSnapshot(), links, note.getAudienceCode(), note.getLanguage(),
                    note.getAudienceName()), localized.status(), localized.glossary(), now);
        }
        noteRepository.flush();
    }

    private static String render(
            String summary,
            List<ChangeView> changes,
            String templateBody,
            String footerBody,
            ReleaseLinks links,
            String audienceCode,
            String language,
            String audienceName
    ) {
        try {
            return ReleaseNoteDigest.render(summary, changes, templateBody, footerBody, links, audienceCode, language);
        } catch (AudienceTemplateRenderException exception) {
            throw new ReleaseNoteRenderException(audienceName, exception);
        }
    }

    // The release's version, the one it follows, and its links, from the database alone.
    private ReleaseLinks links(Release release) {
        return linkBuilder.links(
                release.getOrganizationId(),
                release.getProjectId(),
                release.getVersion(),
                previousVersion(release),
                release.getUpgradeGuideUrl()
        );
    }

    private String previousVersion(Release release) {
        return release.getPreviousReleaseId() == null
                ? null
                : releaseRepository.findByIdAndOrganizationIdAndProjectId(
                        release.getPreviousReleaseId(), release.getOrganizationId(), release.getProjectId())
                .map(Release::getVersion)
                .orElse(null);
    }

    private List<Release> published(UUID organizationId, UUID projectId) {
        return releaseRepository.findAllByOrganizationIdAndProjectIdAndStatusOrderByPublishedAtDescIdDesc(
                organizationId, projectId, ReleaseStatus.PUBLISHED);
    }

    // A release may follow only a published release of its own Project, never itself.
    private UUID previousRelease(UUID organizationId, UUID projectId, UUID previousReleaseId, UUID releaseId) {
        if (previousReleaseId == null) {
            return null;
        }
        boolean published = !previousReleaseId.equals(releaseId) && releaseRepository
                .findByIdAndOrganizationIdAndProjectId(previousReleaseId, organizationId, projectId)
                .filter(Release::isPublished)
                .isPresent();
        if (!published) {
            throw new InvalidPreviousReleaseException();
        }
        return previousReleaseId;
    }

    private List<AudienceReleaseNote> notes(Release release) {
        return noteRepository.findAllByReleaseIdAndOrganizationIdOrderByAudienceNameAscAudienceCodeAscLanguageAsc(
                release.getId(),
                release.getOrganizationId()
        );
    }

    private Release find(UUID organizationId, UUID projectId, UUID releaseId) {
        return releaseRepository.findByIdAndOrganizationIdAndProjectId(releaseId, organizationId, projectId)
                .orElseThrow(ReleaseNotFoundException::new);
    }

    private List<ChangeView> includedChanges(Release release) {
        List<UUID> ids = releaseChangeRepository
                .findAllByReleaseIdAndOrganizationId(release.getId(), release.getOrganizationId())
                .stream()
                .map(ReleaseChange::getChangeId)
                .toList();
        return changeService.changes(release.getOrganizationId(), release.getProjectId(), ids);
    }

    // Change ID -> release ID for every change of the Project that is already in a release.
    private Map<UUID, UUID> membership(UUID organizationId, UUID projectId) {
        return releaseChangeRepository.findAllByOrganizationIdAndProjectId(organizationId, projectId)
                .stream()
                .collect(Collectors.toMap(ReleaseChange::getChangeId, ReleaseChange::getReleaseId));
    }

    private ReleaseView view(Release release, List<ChangeView> changes) {
        String previousVersion = previousVersion(release);
        List<ReleaseDecisionView> decisions = reviewRepository
                .findAllByReleaseIdAndOrganizationId(release.getId(), release.getOrganizationId())
                .stream()
                .map(ReleaseChangeReview::view)
                .toList();
        List<AudienceNoteView> notes = notes(release).stream().map(AudienceReleaseNote::view).toList();
        if (!release.isPublished()) {
            return view(release, previousVersion, changes, decisions, ReleaseNotePreview.sections(changes), null, notes);
        }
        if (!notes.isEmpty()) {
            return view(release, previousVersion, changes, decisions, List.of(), null, notes);
        }
        // Published before audiences existed: shown from its single legacy note.
        LegacyReleaseNote legacy = legacyNoteRepository
                .findByReleaseIdAndOrganizationIdAndProjectId(
                        release.getId(),
                        release.getOrganizationId(),
                        release.getProjectId()
                )
                .orElseThrow(() -> new IllegalStateException("Published release " + release.getId() + " has no note."));
        return view(
                release,
                previousVersion,
                changes,
                decisions,
                objectMapper.readValue(legacy.getSections(), SECTIONS),
                legacy.getMarkdown(),
                List.of()
        );
    }

    private static ReleaseView view(
            Release release,
            String previousVersion,
            List<ChangeView> changes,
            List<ReleaseDecisionView> decisions,
            List<ReleaseNoteSection> sections,
            String markdown,
            List<AudienceNoteView> notes
    ) {
        return new ReleaseView(
                release.getId(),
                release.getProjectId(),
                release.getVersion(),
                release.getSummary(),
                release.getStatus(),
                release.getCreatedAt(),
                release.getUpdatedAt(),
                release.getPlannedReleaseAt(),
                release.getUpgradeGuideUrl(),
                release.getPreviousReleaseId(),
                previousVersion,
                changes,
                decisions,
                decisions.size(),
                sections,
                release.getApprovedAt(),
                release.getApproverName(),
                release.getPublishedAt(),
                release.getPublisherName(),
                markdown,
                notes,
                ReleaseView.glossaries(notes)
        );
    }

    private static RuntimeException translate(DataIntegrityViolationException exception) {
        if (violates(exception, VERSION_CONSTRAINT)) {
            return new ReleaseVersionTakenException();
        }
        return exception;
    }

    private static boolean violates(DataIntegrityViolationException exception, String constraint) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }
}
