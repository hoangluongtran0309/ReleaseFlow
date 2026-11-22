package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.audience.AudienceService;
import com.hoangluongtran0309.releaseflow.release.ReleaseAccess;
import com.hoangluongtran0309.releaseflow.release.ReleaseStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads the run history and carries out what a person asks of it: running a rule by
 * hand, sending a failed action again, or calling a run off. Nothing here talks to a
 * provider; the worker does that.
 */
@Service
public class AutomationRunService {

    private final AutomationRunRepository runRepository;
    private final AutomationActionRunRepository actionRunRepository;
    private final AutomationRuleService ruleService;
    private final AutomationRunFactory runFactory;
    private final ReleaseAccess releaseAccess;
    private final AudienceService audienceService;
    private final Clock clock;

    AutomationRunService(
            AutomationRunRepository runRepository,
            AutomationActionRunRepository actionRunRepository,
            AutomationRuleService ruleService,
            AutomationRunFactory runFactory,
            ReleaseAccess releaseAccess,
            AudienceService audienceService,
            Clock clock
    ) {
        this.runRepository = runRepository;
        this.actionRunRepository = actionRunRepository;
        this.ruleService = ruleService;
        this.runFactory = runFactory;
        this.releaseAccess = releaseAccess;
        this.audienceService = audienceService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AutomationRunPage list(UUID organizationId, int page, int size) {
        AutomationRunPage.requireValid(page, size);
        List<AutomationRun> runs = runRepository.findAllByOrganizationIdOrderByCreatedAtDescIdDesc(
                organizationId, PageRequest.of(page, size));
        return new AutomationRunPage(
                views(runs),
                page,
                size,
                runRepository.countByOrganizationId(organizationId)
        );
    }

    @Transactional(readOnly = true)
    public AutomationRunView get(UUID organizationId, UUID runId) {
        return views(List.of(find(organizationId, runId))).getFirst();
    }

    /**
     * Runs a rule against one published release. The request ID is the promise that a
     * repeat is the same run, so a retried click never delivers the note twice.
     */
    @Transactional
    public AutomationRunView execute(ReleaseFlowPrincipal principal, UUID ruleId, ExecuteRuleRequest request) {
        if (request.getRequestId() == null) {
            throw AutomationActionInvalidException.requestIdRequired();
        }
        AutomationRule rule = ruleService.require(principal.organizationId(), ruleId);
        Optional<AutomationRun> existing = runRepository.findByRuleIdAndRequestId(ruleId, request.getRequestId());
        if (existing.isPresent()) {
            return views(List.of(existing.get())).getFirst();
        }
        // The release is locked first, as wherever a run is created for it (#119, #122), so
        // the run carries each note as of a moment no correction is half-way through.
        ReleaseAccess.ReleaseSnapshot release = releaseAccess
                .lockForRun(principal.organizationId(), request.getReleaseId())
                .orElseThrow(AutomationConflictException::releaseNotPublished);
        // A repeat of this request may have been waiting on the same lock: it is that run.
        existing = runRepository.findByRuleIdAndRequestId(ruleId, request.getRequestId());
        if (existing.isPresent()) {
            return views(List.of(existing.get())).getFirst();
        }
        if (release.status() != ReleaseStatus.PUBLISHED) {
            throw AutomationConflictException.releaseNotPublished();
        }
        if (!rule.matches(release.projectId(), TriggerType.MANUAL)) {
            throw AutomationConflictException.ruleUnavailable();
        }
        AutomationRun run = runFactory.create(
                rule,
                release,
                TriggerType.MANUAL,
                request.getRequestId(),
                principal.userId(),
                principal.displayName(),
                null
        );
        return views(List.of(run)).getFirst();
    }

    /** One run as the API and the pages show it. */
    @Transactional(readOnly = true)
    AutomationRunView view(AutomationRun run) {
        return views(List.of(run)).getFirst();
    }

    /**
     * Sends the first action that failed, or whose outcome nobody knows, once more.
     * Actions that already succeeded are never repeated.
     */
    @Transactional
    public AutomationRunView retry(UUID organizationId, UUID runId, boolean confirmUnknown) {
        AutomationRun run = find(organizationId, runId);
        if (run.getStatus() == ExecutionStatus.UNKNOWN && !confirmUnknown) {
            throw AutomationConflictException.unknownNeedsConfirmation();
        }
        if (run.getStatus() != ExecutionStatus.FAILED && run.getStatus() != ExecutionStatus.UNKNOWN) {
            throw AutomationConflictException.runNotRetryable();
        }
        List<AutomationActionRun> actions = actionRunRepository.findAllByRunIdOrderByPositionAsc(runId);
        actions.stream()
                .filter(action -> action.getStatus() == ExecutionStatus.FAILED
                        || action.getStatus() == ExecutionStatus.UNKNOWN)
                .min(Comparator.comparingInt(AutomationActionRun::getPosition))
                .orElseThrow(AutomationConflictException::runNotRetryable)
                .retry();
        // Everything the stopped action held up waits again with it.
        actions.stream()
                .filter(action -> action.getStatus() == ExecutionStatus.CANCELLED)
                .forEach(AutomationActionRun::retry);
        run.reopen();
        runRepository.flush();
        actionRunRepository.flush();
        return views(List.of(run)).getFirst();
    }

    /**
     * Calls a run off. An action already on its way finishes and records its result;
     * everything behind it is cancelled.
     */
    @Transactional
    public AutomationRunView cancel(UUID organizationId, UUID runId) {
        AutomationRun run = find(organizationId, runId);
        if (run.getStatus() == ExecutionStatus.SUCCEEDED || run.getStatus() == ExecutionStatus.CANCELLED) {
            return views(List.of(run)).getFirst();
        }
        Instant now = now();
        run.requestCancellation();
        List<AutomationActionRun> actions = actionRunRepository.findAllByRunIdOrderByPositionAsc(runId);
        if (actions.stream().noneMatch(action -> action.getStatus() == ExecutionStatus.RUNNING)) {
            actions.forEach(action -> action.cancelPending(now));
            run.settle(actions, now);
        }
        runRepository.flush();
        actionRunRepository.flush();
        return views(List.of(run)).getFirst();
    }

    private AutomationRun find(UUID organizationId, UUID runId) {
        return runRepository.findByIdAndOrganizationId(runId, organizationId)
                .orElseThrow(AutomationRunNotFoundException::new);
    }

    private List<AutomationRunView> views(List<AutomationRun> runs) {
        if (runs.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<AutomationActionRun>> byRun = actionRunRepository
                .findAllByRunIdInOrderByRunIdAscPositionAsc(runs.stream().map(AutomationRun::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(AutomationActionRun::getRunId));
        // Looked up once per Organization, and only when a row needs it.
        Map<UUID, Map<UUID, String>> audienceNames = new HashMap<>();
        // What each correction replaced, so a page can say "revision 3, replaces revision 1".
        List<UUID> corrected = byRun.values().stream().flatMap(List::stream)
                .map(AutomationActionRun::getCorrectsActionRunId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<UUID, Integer> correctedRevisions = corrected.isEmpty() ? Map.of() : actionRunRepository.findAllById(corrected)
                .stream()
                .filter(action -> action.getNoteRevision() != null)
                .collect(Collectors.toMap(AutomationActionRun::getId, AutomationActionRun::getNoteRevision));
        return runs.stream()
                .map(run -> new AutomationRunView(
                        run.getId(),
                        run.getRuleId(),
                        run.getRuleNameSnapshot(),
                        run.getReleaseId(),
                        run.getReleaseVersionSnapshot(),
                        run.getTriggerType(),
                        run.getRequestId(),
                        run.getInitiatorName(),
                        run.getReason(),
                        run.getStatus(),
                        run.isCancellationRequested(),
                        run.getCreatedAt(),
                        run.getStartedAt(),
                        run.getCompletedAt(),
                        byRun.getOrDefault(run.getId(), List.of()).stream()
                                .map(action -> view(action, namedByItsId(action)
                                        ? audienceNames.computeIfAbsent(action.getOrganizationId(), audienceService::displayNames)
                                        : Map.of(), correctedRevisions))
                                .toList()
                ))
                .toList();
    }

    // Before 1.3.0, an action whose release had no note for its audience stored the
    // audience's ID as its name. The stored row stays as it is; the page shows the name.
    private static boolean namedByItsId(AutomationActionRun action) {
        return action.getAudienceNameSnapshot().equals(action.getAudienceId().toString());
    }

    private static AutomationActionRunView view(
            AutomationActionRun action,
            Map<UUID, String> audienceNames,
            Map<UUID, Integer> correctedRevisions
    ) {
        String audienceName = namedByItsId(action)
                ? audienceNames.getOrDefault(action.getAudienceId(), action.getAudienceNameSnapshot())
                : action.getAudienceNameSnapshot();
        return new AutomationActionRunView(
                action.getId(),
                action.getPosition(),
                action.getActionType(),
                audienceName,
                action.getLanguageSnapshot(),
                action.getStatus(),
                action.getExternalReference(),
                action.getErrorCode(),
                action.getAttempts(),
                action.getStartedAt(),
                action.getCompletedAt(),
                action.getNoteRevision(),
                action.getCorrectsActionRunId() == null ? null : correctedRevisions.get(action.getCorrectsActionRunId())
        );
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
