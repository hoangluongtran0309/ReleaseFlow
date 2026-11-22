package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.account.AppUserRole;
import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.audience.AudienceService;
import com.hoangluongtran0309.releaseflow.release.AudienceNoteView;
import com.hoangluongtran0309.releaseflow.release.ReleaseAccess;
import com.hoangluongtran0309.releaseflow.release.ReleaseStatus;
import com.hoangluongtran0309.releaseflow.translation.TranslationState;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A correction run (ADR-0046, part 5): an administrator delivers a corrected note to the
 * destinations that already hold an earlier revision of it. Only this service starts one;
 * publication, a schedule, a reminder, and a signed call never do, and saving a revision
 * delivers nothing.
 *
 * <p>Slice A corrects the public changelog alone. Every other action of the rule is named in
 * the offer with the reason it is not offered, so the person sees what still holds the old text.
 */
@Service
public class AutomationCorrectionService {

    private final AutomationRuleService ruleService;
    private final AutomationRuleActionRepository ruleActionRepository;
    private final AutomationActionRunRepository actionRunRepository;
    private final AutomationRunRepository runRepository;
    private final AutomationRunFactory runFactory;
    private final AutomationRunService runService;
    private final ReleaseAccess releaseAccess;
    private final AudienceService audienceService;

    AutomationCorrectionService(
            AutomationRuleService ruleService,
            AutomationRuleActionRepository ruleActionRepository,
            AutomationActionRunRepository actionRunRepository,
            AutomationRunRepository runRepository,
            AutomationRunFactory runFactory,
            AutomationRunService runService,
            ReleaseAccess releaseAccess,
            AudienceService audienceService
    ) {
        this.ruleService = ruleService;
        this.ruleActionRepository = ruleActionRepository;
        this.actionRunRepository = actionRunRepository;
        this.runRepository = runRepository;
        this.runFactory = runFactory;
        this.runService = runService;
        this.releaseAccess = releaseAccess;
        this.audienceService = audienceService;
    }

    /** What a correction run of this rule could deliver for this published release. */
    @Transactional(readOnly = true)
    public CorrectionOffer offer(ReleaseFlowPrincipal principal, UUID ruleId, UUID releaseId) {
        requireAdministrator(principal);
        AutomationRule rule = ruleService.require(principal.organizationId(), ruleId);
        ReleaseAccess.ReleaseSnapshot release = releaseAccess.find(principal.organizationId(), releaseId)
                .orElseThrow(AutomationConflictException::releaseNotPublished);
        requireCorrectable(rule, release);
        return offer(rule, release);
    }

    /**
     * Starts a correction run. The release is locked first, as wherever a run is created for
     * it (#119, #122): a revision is saved under the same lock, so the offer checked here and
     * the revisions the run carries are the same. The same request ID is the same run.
     */
    @Transactional
    public AutomationRunView start(ReleaseFlowPrincipal principal, UUID ruleId, CorrectionRequest request) {
        requireAdministrator(principal);
        if (request.getRequestId() == null) {
            throw AutomationActionInvalidException.requestIdRequired();
        }
        AutomationRule rule = ruleService.require(principal.organizationId(), ruleId);
        Optional<AutomationRun> existing = runRepository.findByRuleIdAndRequestId(ruleId, request.getRequestId());
        if (existing.isPresent()) {
            return runService.view(existing.get());
        }
        ReleaseAccess.ReleaseSnapshot release = releaseAccess
                .lockForRun(principal.organizationId(), request.getReleaseId())
                .orElseThrow(AutomationConflictException::releaseNotPublished);
        // A repeat of this request may have been waiting on the same lock: it is that run.
        existing = runRepository.findByRuleIdAndRequestId(ruleId, request.getRequestId());
        if (existing.isPresent()) {
            return runService.view(existing.get());
        }
        requireCorrectable(rule, release);
        CorrectionOffer offer = offer(rule, release);
        Map<UUID, CorrectionOffer.Item> items = offer.items().stream()
                .collect(Collectors.toMap(CorrectionOffer.Item::ruleActionId, item -> item));
        Set<UUID> picked = new LinkedHashSet<>(request.getRuleActionIds());
        if (picked.stream().anyMatch(ruleActionId -> !items.containsKey(ruleActionId))) {
            throw AutomationActionInvalidException.correctionActionUnknown();
        }
        // The reason is one text, shown by every picked destination as written: one language.
        // That is a question of what was asked, so it is answered before what can be delivered.
        if (picked.stream().map(items::get).map(CorrectionOffer.Item::language).distinct().count() > 1) {
            throw AutomationActionInvalidException.correctionLanguagesMixed();
        }
        for (UUID ruleActionId : picked) {
            CorrectionOffer.Item item = items.get(ruleActionId);
            if (!item.offered()) {
                // Positions count from zero; a person counts actions from one, as the pages do.
                throw AutomationConflictException.correctionNotOffered(item.position() + 1, item.unavailable());
            }
        }
        Map<UUID, List<AutomationActionRun>> histories = histories(principal.organizationId(), release.releaseId(),
                picked);
        Map<UUID, UUID> corrected = new LinkedHashMap<>();
        for (UUID ruleActionId : picked) {
            corrected.put(ruleActionId, lastSuccess(histories.get(ruleActionId)).orElseThrow().getId());
        }
        AutomationRun run = runFactory.createCorrection(
                rule,
                release,
                corrected,
                request.getRequestId(),
                principal.userId(),
                principal.displayName(),
                request.getReason()
        );
        return runService.view(run);
    }

    private CorrectionOffer offer(AutomationRule rule, ReleaseAccess.ReleaseSnapshot release) {
        UUID organizationId = rule.getOrganizationId();
        List<AutomationRuleAction> actions = ruleActionRepository.findAllByRuleIdOrderByPositionAsc(rule.getId());
        Map<UUID, List<AutomationActionRun>> histories = histories(organizationId, release.releaseId(),
                actions.stream().map(AutomationRuleAction::getId).toList());
        Map<UUID, String> audienceNames = audienceService.displayNames(organizationId);
        List<CorrectionOffer.Item> items = actions.stream()
                .map(action -> item(action, release, histories.getOrDefault(action.getId(), List.of()), audienceNames))
                .toList();
        Set<String> reasons = new LinkedHashSet<>();
        for (CorrectionOffer.Item item : items) {
            if (item.picked()) {
                note(release, actions.stream().filter(action -> action.getId().equals(item.ruleActionId()))
                        .findFirst().orElseThrow())
                        .ifPresent(note -> reasons.addAll(
                                releaseAccess.reasonsAfter(organizationId, note.id(), item.carriedRevision())));
            }
        }
        return new CorrectionOffer(rule.getId(), release.releaseId(), release.version(), items, String.join("\n", reasons));
    }

    /** One action of the rule, offered or with the first reason it is not, in a fixed order. */
    private CorrectionOffer.Item item(
            AutomationRuleAction action,
            ReleaseAccess.ReleaseSnapshot release,
            List<AutomationActionRun> history,
            Map<UUID, String> audienceNames
    ) {
        Optional<AudienceNoteView> note = note(release, action);
        Optional<AutomationActionRun> lastSuccess = lastSuccess(history);
        Integer carried = lastSuccess.map(AutomationActionRun::getNoteRevision).orElse(null);
        Integer latest = note.map(AudienceNoteView::revision).orElse(null);
        CorrectionOffer.Unavailable unavailable = unavailable(action, history, lastSuccess, carried, latest);
        return new CorrectionOffer.Item(
                action.getId(),
                action.getPosition(),
                action.getActionType(),
                note.map(AudienceNoteView::audienceName)
                        .orElseGet(() -> audienceNames.getOrDefault(action.getAudienceId(), action.getAudienceId().toString())),
                action.getTargetLanguage(),
                unavailable,
                unavailable == null && action.getActionType() == ActionType.PUBLIC_CHANGELOG,
                carried,
                latest
        );
    }

    private CorrectionOffer.Unavailable unavailable(
            AutomationRuleAction action,
            List<AutomationActionRun> history,
            Optional<AutomationActionRun> lastSuccess,
            Integer carried,
            Integer latest
    ) {
        if (audienceService.isRetired(action.getOrganizationId(), action.getAudienceId())) {
            return CorrectionOffer.Unavailable.AUDIENCE_RETIRED;
        }
        if (action.getActionType() == ActionType.ZENDESK) {
            return CorrectionOffer.Unavailable.NOT_SUPPORTED;
        }
        if (action.getActionType() != ActionType.PUBLIC_CHANGELOG) {
            return CorrectionOffer.Unavailable.LATER;
        }
        if (!history.isEmpty() && history.getLast().getStatus() == ExecutionStatus.UNKNOWN) {
            return CorrectionOffer.Unavailable.UNKNOWN;
        }
        if (history.stream().anyMatch(run -> run.getStatus() == ExecutionStatus.PENDING
                || run.getStatus() == ExecutionStatus.RUNNING)) {
            return CorrectionOffer.Unavailable.IN_PROGRESS;
        }
        if (lastSuccess.isEmpty()) {
            return !history.isEmpty() && history.stream().allMatch(run -> run.getStatus() == ExecutionStatus.SKIPPED)
                    ? CorrectionOffer.Unavailable.ONLY_SKIPPED
                    : CorrectionOffer.Unavailable.NEVER_DELIVERED;
        }
        if (latest == null || carried == null || latest <= carried) {
            return CorrectionOffer.Unavailable.NOTHING_NEWER;
        }
        return null;
    }

    private static Optional<AudienceNoteView> note(ReleaseAccess.ReleaseSnapshot release, AutomationRuleAction action) {
        return release.notes().stream()
                .filter(note -> note.audienceId().equals(action.getAudienceId()))
                .filter(note -> note.language().equals(action.getTargetLanguage()))
                .filter(note -> note.translationStatus() == TranslationState.Status.READY)
                .findFirst();
    }

    // The delivery that holds what the destination shows now: the last that succeeded with a note.
    private static Optional<AutomationActionRun> lastSuccess(List<AutomationActionRun> history) {
        return history == null ? Optional.empty() : history.stream()
                .filter(run -> run.getStatus() == ExecutionStatus.SUCCEEDED && run.getNoteContentSnapshot() != null)
                .max(Comparator.comparing(AutomationActionRun::getCompletedAt));
    }

    private Map<UUID, List<AutomationActionRun>> histories(UUID organizationId, UUID releaseId,
                                                           java.util.Collection<UUID> ruleActionIds) {
        if (ruleActionIds.isEmpty()) {
            return Map.of();
        }
        return actionRunRepository.findAllForRuleActionsAndRelease(organizationId, releaseId, ruleActionIds).stream()
                .collect(Collectors.groupingBy(AutomationActionRun::getRuleActionId, LinkedHashMap::new,
                        Collectors.toList()));
    }

    // A correction goes to a published release, from a rule that can still deliver for it.
    private static void requireCorrectable(AutomationRule rule, ReleaseAccess.ReleaseSnapshot release) {
        if (release.status() != ReleaseStatus.PUBLISHED) {
            throw AutomationConflictException.releaseNotPublished();
        }
        if (!rule.isActive()) {
            throw AutomationConflictException.ruleArchived();
        }
        if (!rule.isEnabled() || (rule.getProjectId() != null && !rule.getProjectId().equals(release.projectId()))) {
            throw AutomationConflictException.ruleUnavailableForCorrection();
        }
    }

    // The URL rule already admits administrators only; a correction says so itself as well.
    private static void requireAdministrator(ReleaseFlowPrincipal principal) {
        if (principal.role() != AppUserRole.ADMIN) {
            throw new AccessDeniedException("Administrator role required.");
        }
    }
}
