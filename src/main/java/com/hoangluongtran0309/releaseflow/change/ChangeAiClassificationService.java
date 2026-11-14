package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.OutputLanguage;
import com.hoangluongtran0309.releaseflow.account.OutputLanguageService;
import com.hoangluongtran0309.releaseflow.audience.AudienceBrief;
import com.hoangluongtran0309.releaseflow.audience.AudienceService;
import com.hoangluongtran0309.releaseflow.category.CategoryRef;
import com.hoangluongtran0309.releaseflow.category.CategoryService;
import com.hoangluongtran0309.releaseflow.category.CategorySuggestionService;
import com.hoangluongtran0309.releaseflow.project.ProjectService;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lets a person ask the AI again for a change whose automatic attempt failed, or for
 * an Unknown change recorded before automatic classification. The AI call runs between
 * two short transactions, never inside one.
 */
@Service
class ChangeAiClassificationService {

    private final ChangeRepository changeRepository;
    private final AiClassifiers aiClassifiers;
    private final ProjectSensitivePathService sensitivePaths;
    private final OutputLanguageService outputLanguageService;
    private final AudienceService audienceService;
    private final ProjectService projectService;
    private final CategoryService categoryService;
    private final CategorySuggestionService suggestionService;
    private final ClassificationSettings settings;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    ChangeAiClassificationService(
            ChangeRepository changeRepository,
            AiClassifiers aiClassifiers,
            ProjectSensitivePathService sensitivePaths,
            OutputLanguageService outputLanguageService,
            AudienceService audienceService,
            ProjectService projectService,
            CategoryService categoryService,
            CategorySuggestionService suggestionService,
            ClassificationSettings settings,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.changeRepository = changeRepository;
        this.aiClassifiers = aiClassifiers;
        this.sensitivePaths = sensitivePaths;
        this.outputLanguageService = outputLanguageService;
        this.audienceService = audienceService;
        this.projectService = projectService;
        this.categoryService = categoryService;
        this.suggestionService = suggestionService;
        this.settings = settings;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    boolean isEnabled() {
        return aiClassifiers.isEnabled();
    }

    ChangeView classify(UUID organizationId, UUID projectId, UUID changeId) {
        Snapshot snapshot = transactionTemplate.execute(status -> {
            Change current = find(organizationId, projectId, changeId);
            if (current.isProcessing()) {
                throw new ChangeProcessingException();
            }
            if (!current.isAiEligible()) {
                throw new ChangeNotEligibleForAiException();
            }
            MergedPullRequest pullRequest = current.pullRequest();
            List<CategoryRef> catalog = categoryService.active(organizationId);
            // A retry judges what is already recorded; the tracker is not asked again.
            LinkedContext linked = current.recordedLinkedContext();
            return new Snapshot(
                    pullRequest,
                    current.getSourceType(),
                    linked,
                    ChangeClassifier.classify(pullRequest, current.recordedFiles(), linked,
                            sensitivePaths.forProject(organizationId, projectId), catalog),
                    catalog
            );
        });
        AiChangeClassifier ai = aiClassifiers.active().orElseThrow(AiClassificationUnavailableException::new);
        OutputLanguage language = outputLanguageService.outputLanguage(organizationId);
        List<AudienceBrief> audiences = audienceService.briefs(organizationId);
        String projectContext = projectService.context(organizationId, projectId);

        AiOutcome outcome;
        try {
            outcome = AiOutcome.succeeded(
                    ai,
                    ai.classify(AiClassificationRequest.of(changeId, snapshot.pullRequest(),
                            snapshot.linkedContext().issues(), language,
                            snapshot.rules().category(), snapshot.catalog(), audiences, settings.contextThreshold(),
                            snapshot.sourceType(), projectContext)),
                    language
            );
        } catch (AiClassificationException exception) {
            outcome = AiOutcome.failed(ai, exception.getMessage());
        }

        AiOutcome recorded = outcome;
        ChangeView view = transactionTemplate.execute(status -> {
            Change current = find(organizationId, projectId, changeId);
            // A concurrent request or review may have settled the change meanwhile; its result is kept.
            if (current.isAiEligible()) {
                ChangeAiMerge.ClassifiedChange merged = ChangeAiMerge.merge(
                        snapshot.rules(), recorded, snapshot.pullRequest(), settings.contextThreshold());
                current.applyAiRetry(merged, clock.instant());
                if (merged.suggestion() != null) {
                    suggestionService.propose(organizationId, projectId, changeId, merged.suggestion());
                }
            }
            return ChangeView.from(current);
        });
        if (!outcome.succeeded()) {
            throw new AiClassificationFailedException(outcome.failure());
        }
        return view;
    }

    /**
     * Asks the AI for the narratives a change has none of, for the active audiences that lack
     * one — an audience brought back, or added after the change was classified (ADR-0044).
     * Only a change the AI classified is asked; the request is the same evidence it was
     * classified from, listing only those audiences, and only their narratives are kept:
     * never the category, the flags, the summary, or a narrative that exists, so it never
     * undoes a recorded review or a person's words. Made only when a person asks.
     */
    ChangeView requestMissingNarratives(UUID organizationId, UUID projectId, UUID changeId) {
        Prepared prepared = transactionTemplate.execute(status -> prepareNarratives(organizationId, projectId, changeId));
        if (prepared == null) {
            return transactionTemplate.execute(status -> ChangeView.from(find(organizationId, projectId, changeId)));
        }
        AiChangeClassifier ai = aiClassifiers.active().orElseThrow(AiClassificationUnavailableException::new);
        AiClassification answer;
        try {
            answer = ai.classify(prepared.request());
        } catch (AiClassificationException exception) {
            throw new AiClassificationFailedException(exception.getMessage());
        }
        return transactionTemplate.execute(status -> {
            Change current = find(organizationId, projectId, changeId);
            current.addMissingNarratives(answer.narratives(), prepared.codes());
            return ChangeView.from(current);
        });
    }

    /**
     * The rough size of {@link #requestMissingNarratives}'s request, in tokens: its text
     * length over four. Nothing is sent. Zero when the change lacks no narrative.
     */
    long estimateMissingNarrativeTokens(UUID organizationId, UUID projectId, UUID changeId) {
        Prepared prepared = transactionTemplate.execute(status -> prepareNarratives(organizationId, projectId, changeId));
        if (prepared == null) {
            return 0;
        }
        AiClassificationRequest request = prepared.request();
        long characters = AiClassificationPrompt.system(request).length()
                + AiClassificationPrompt.userMessage(request, objectMapper).length()
                + AiClassificationPrompt.responseSchema(request, objectMapper).toString().length();
        return (characters + 3) / 4;
    }

    /** The active audiences a change the AI classified has no narrative for. */
    List<AudienceBrief> missingNarratives(Change change, List<AudienceBrief> active) {
        if (change.getAiStatus() != AiStatus.SUCCEEDED) {
            return List.of();
        }
        Map<String, AudienceNarrative> existing = change.getAudienceNarratives();
        return active.stream().filter(audience -> !existing.containsKey(audience.code())).toList();
    }

    // Null when there is nothing to ask for.
    private Prepared prepareNarratives(UUID organizationId, UUID projectId, UUID changeId) {
        Change current = find(organizationId, projectId, changeId);
        if (current.isProcessing()) {
            throw new ChangeProcessingException();
        }
        if (current.getAiStatus() != AiStatus.SUCCEEDED) {
            throw new ChangeNotEligibleForAiException();
        }
        List<AudienceBrief> missing = missingNarratives(current, audienceService.briefs(organizationId));
        if (missing.isEmpty()) {
            return null;
        }
        MergedPullRequest pullRequest = current.pullRequest();
        List<CategoryRef> catalog = categoryService.active(organizationId);
        LinkedContext linked = current.recordedLinkedContext();
        ChangeClassification rules = ChangeClassifier.classify(pullRequest, current.recordedFiles(), linked,
                sensitivePaths.forProject(organizationId, projectId), catalog);
        AiClassificationRequest request = AiClassificationRequest.of(changeId, pullRequest, linked.issues(),
                outputLanguageService.outputLanguage(organizationId), rules.category(), catalog, missing,
                settings.contextThreshold(), current.getSourceType(), projectService.context(organizationId, projectId));
        return new Prepared(request, missing.stream().map(AudienceBrief::code).collect(Collectors.toSet()));
    }

    private record Prepared(AiClassificationRequest request, Set<String> codes) {
    }

    private Change find(UUID organizationId, UUID projectId, UUID changeId) {
        return changeRepository.findByIdAndOrganizationIdAndProjectId(changeId, organizationId, projectId)
                .orElseThrow(ChangeNotFoundException::new);
    }

    private record Snapshot(
            MergedPullRequest pullRequest,
            SourceType sourceType,
            LinkedContext linkedContext,
            ChangeClassification rules,
            List<CategoryRef> catalog
    ) {
    }
}
