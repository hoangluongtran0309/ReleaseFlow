package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.audience.AudienceService;
import com.hoangluongtran0309.releaseflow.audience.AudienceView;
import com.hoangluongtran0309.releaseflow.category.CategoryService;
import com.hoangluongtran0309.releaseflow.category.CategorySuggestionService;
import com.hoangluongtran0309.releaseflow.overview.OverviewService;
import com.hoangluongtran0309.releaseflow.overview.SelectedProject;
import com.hoangluongtran0309.releaseflow.configuration.UiNotice;
import com.hoangluongtran0309.releaseflow.project.ProjectService;
import com.hoangluongtran0309.releaseflow.project.ProjectView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class ChangeInboxPageController {

    private final ProjectService projectService;
    private final ChangeInboxService inboxService;
    private final ChangeAiClassificationService aiClassificationService;
    private final ChangeReviewService reviewService;
    private final ChangeExclusionService exclusionService;
    private final CategoryService categoryService;
    private final CategorySuggestionService suggestionService;
    private final DuplicateCandidateService duplicateService;
    private final SourceImportService importService;
    private final SelectedProject selectedProject;
    private final OverviewService overviewService;
    private final AudienceService audienceService;



    ChangeInboxPageController(
            ProjectService projectService,
            ChangeInboxService inboxService,
            ChangeAiClassificationService aiClassificationService,
            ChangeReviewService reviewService,
            ChangeExclusionService exclusionService,
            CategoryService categoryService,
            CategorySuggestionService suggestionService,
            DuplicateCandidateService duplicateService,
            SourceImportService importService,
            SelectedProject selectedProject,
            OverviewService overviewService,
            AudienceService audienceService
    ) {
        this.projectService = projectService;
        this.inboxService = inboxService;
        this.aiClassificationService = aiClassificationService;
        this.reviewService = reviewService;
        this.exclusionService = exclusionService;
        this.categoryService = categoryService;
        this.suggestionService = suggestionService;
        this.duplicateService = duplicateService;
        this.importService = importService;
        this.selectedProject = selectedProject;
        this.overviewService = overviewService;
        this.audienceService = audienceService;
    }

    /** Queues an import of a repository's last 90 days, then returns to the Inbox. */
    @PostMapping("/projects/{projectId}/sources/{sourceId}/imports")
    String startImport(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID sourceId,
            Model model,
            HttpServletResponse response
    ) {
        return importAction(principal, projectId, model, response,
                () -> importService.startImport(principal, projectId, sourceId));
    }

    @PostMapping("/projects/{projectId}/sources/{sourceId}/imports/resume")
    String resumeImport(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID sourceId,
            Model model,
            HttpServletResponse response
    ) {
        return importAction(principal, projectId, model, response,
                () -> importService.resume(principal, projectId, sourceId));
    }

    private String importAction(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            Model model,
            HttpServletResponse response,
            Runnable action
    ) {
        try {
            action.run();
        } catch (SourceTokenMissingException | SourceSyncInProgressException | SourceImportNotResumableException exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            model.addAttribute(UiNotice.PAGE_ATTRIBUTE, UiNotice.error(exception));
            return renderInbox(principal, projectId, null, null, null, null, model, response);
        }
        return "redirect:" + UriComponentsBuilder.fromPath("/changes")
                .queryParam("project", projectId)
                .fragment("source-imports")
                .encode()
                .toUriString();
    }

    // A URL that names no Project opens on the one this person was last looking at.
    @GetMapping("/changes")
    String changes(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @RequestParam(name = "project", required = false) UUID projectId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String context,
            @RequestParam(required = false) String release,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        // A URL that names a Project is answered as it stands: one of another Organization
        // is reported as not found, never swapped for one of this Organization's.
        UUID selected = projectId != null
                ? projectId
                : overviewService.rememberedOrFirstProject(
                        principal.organizationId(),
                        selectedProject.remembered(request).orElse(null)
                ).orElse(null);
        if (overviewService.owns(principal.organizationId(), selected)) {
            selectedProject.remember(request, response, selected);
        }
        return renderInbox(principal, selected, category, status, context, release, model, response);
    }

    // Both card actions carry the inbox filters as returnCategory/returnStatus so the
    // reviewer lands back on the same card afterwards.
    @PostMapping("/projects/{projectId}/changes/{changeId}/review")
    String review(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID changeId,
            @Valid @ModelAttribute("reviewRequest") ChangeReviewRequest request,
            BindingResult bindingResult,
            @RequestParam(required = false) String returnCategory,
            @RequestParam(required = false) String returnStatus,
            @RequestParam(required = false) String returnContext,
            @RequestParam(required = false) String returnRelease,
            Model model,
            HttpServletResponse response
    ) {
        // Every card carries this form; only the one sent shows what was wrong with it.
        model.addAttribute("failingChangeId", changeId);
        if (!bindingResult.hasErrors()) {
            try {
                reviewService.review(principal, projectId, changeId, request);
            } catch (InvalidChangeReviewException exception) {
                bindingResult.rejectValue("category", exception.messageKey(), exception.arguments(), null);
            }
        }
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return renderInbox(principal, projectId, returnCategory, returnStatus, returnContext, returnRelease, model, response);
        }
        return redirectToCard(projectId, changeId, returnCategory, returnStatus, returnContext, returnRelease);
    }

    @PostMapping("/projects/{projectId}/changes/{changeId}/ai-classification")
    String classifyWithAi(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID changeId,
            @RequestParam(required = false) String returnCategory,
            @RequestParam(required = false) String returnStatus,
            @RequestParam(required = false) String returnContext,
            @RequestParam(required = false) String returnRelease,
            Model model,
            HttpServletResponse response
    ) {
        try {
            aiClassificationService.classify(principal.organizationId(), projectId, changeId);
        } catch (AiClassificationFailedException | ChangeNotEligibleForAiException | ChangeProcessingException exception) {
            // The card shows the recorded failure, or the classification that already exists.
        } catch (AiClassificationUnavailableException exception) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            model.addAttribute(UiNotice.PAGE_ATTRIBUTE, UiNotice.error(exception));
            return renderInbox(principal, projectId, returnCategory, returnStatus, returnContext, returnRelease, model, response);
        }
        return redirectToCard(projectId, changeId, returnCategory, returnStatus, returnContext, returnRelease);
    }

    // Records a person's conclusion on a possible duplicate and returns to the change's card.
    @PostMapping("/projects/{projectId}/duplicate-candidates/{candidateId}/decision")
    String decideDuplicate(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID candidateId,
            @RequestParam UUID changeId,
            @Valid @ModelAttribute DuplicateDecisionRequest request,
            BindingResult bindingResult,
            @RequestParam(required = false) String returnCategory,
            @RequestParam(required = false) String returnStatus,
            @RequestParam(required = false) String returnContext,
            @RequestParam(required = false) String returnRelease,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute(UiNotice.PAGE_ATTRIBUTE, UiNotice.error("ui.changes.duplicateUndecided"));
            return renderInbox(principal, projectId, returnCategory, returnStatus, returnContext, returnRelease, model, response);
        }
        try {
            duplicateService.decide(principal, projectId, candidateId, request);
        } catch (DuplicateCandidateDecidedException exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            model.addAttribute(UiNotice.PAGE_ATTRIBUTE, UiNotice.error(exception));
            return renderInbox(principal, projectId, returnCategory, returnStatus, returnContext, returnRelease, model, response);
        }
        return redirectToCard(projectId, changeId, returnCategory, returnStatus, returnContext, returnRelease);
    }

    // An administrator says this change will never ship; the reason is required.
    @PostMapping("/projects/{projectId}/changes/{changeId}/exclusion")
    String exclude(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID changeId,
            @Valid @ModelAttribute("exclusionRequest") ChangeExclusionRequest request,
            BindingResult bindingResult,
            @RequestParam(required = false) String returnCategory,
            @RequestParam(required = false) String returnStatus,
            @RequestParam(required = false) String returnContext,
            @RequestParam(required = false) String returnRelease,
            Model model,
            HttpServletResponse response,
            RedirectAttributes redirect
    ) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("failingExclusionId", changeId);
            return renderInbox(principal, projectId, returnCategory, returnStatus, returnContext, returnRelease, model, response);
        }
        try {
            exclusionService.exclude(principal, projectId, changeId, request);
        } catch (ChangeInReleaseException | ChangeExcludedException | ChangeProcessingException exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            model.addAttribute(UiNotice.PAGE_ATTRIBUTE, UiNotice.error(exception));
            return renderInbox(principal, projectId, returnCategory, returnStatus, returnContext, returnRelease, model, response);
        }
        UiNotice.flash(redirect, UiNotice.success("ui.changes.excludedSaved"));
        return redirectToCard(projectId, changeId, returnCategory, returnStatus, returnContext, returnRelease);
    }

    // Takes the exclusion back: the change is offered to releases again.
    @PostMapping("/projects/{projectId}/changes/{changeId}/exclusion/delete")
    String include(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID changeId,
            @RequestParam(required = false) String returnCategory,
            @RequestParam(required = false) String returnStatus,
            @RequestParam(required = false) String returnContext,
            @RequestParam(required = false) String returnRelease,
            RedirectAttributes redirect
    ) {
        exclusionService.include(principal, projectId, changeId);
        UiNotice.flash(redirect, UiNotice.success("ui.changes.includedSaved"));
        return redirectToCard(projectId, changeId, returnCategory, returnStatus, returnContext, returnRelease);
    }

    private static String redirectToCard(
            UUID projectId,
            UUID changeId,
            String category,
            String status,
            String context,
            String release
    ) {
        return "redirect:" + UriComponentsBuilder.fromPath("/changes")
                .queryParam("project", projectId)
                .queryParamIfPresent("category", Optional.ofNullable(category).filter(value -> !value.isBlank()))
                .queryParamIfPresent("status", Optional.ofNullable(status).filter(value -> !value.isBlank()))
                .queryParamIfPresent("context", Optional.ofNullable(context).filter(value -> !value.isBlank()))
                .queryParamIfPresent("release", Optional.ofNullable(release).filter(value -> !value.isBlank()))
                .fragment("change-" + changeId)
                .encode()
                .toUriString();
    }

    private String renderInbox(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            String category,
            String status,
            String context,
            String release,
            Model model,
            HttpServletResponse response
    ) {
        List<ProjectView> projects = projectService.list(principal.organizationId());
        UUID selectedProjectId = projectId;
        if (selectedProjectId == null && !projects.isEmpty()) {
            selectedProjectId = projects.getFirst().id();
        }
        model.addAttribute("projects", projects);
        // The filter offers every category, archived ones included; a review only active ones.
        model.addAttribute("categories", categoryService.list(principal.organizationId()));
        // A narrative is keyed by its audience's code; people know the audience by its name.
        model.addAttribute("audienceNames", audienceService.list(principal.organizationId()).stream()
                .collect(Collectors.toMap(AudienceView::code, AudienceView::displayName)));
        model.addAttribute("reviewCategories", categoryService.active(principal.organizationId()).stream()
                .filter(value -> !value.isUnknown())
                .toList());
        model.addAttribute("suggestions", Map.of());
        model.addAttribute("statuses", ReviewStatus.values());
        model.addAttribute("selectedProjectId", selectedProjectId);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedContext", context);
        model.addAttribute("selectedRelease", release);
        model.addAttribute("filtered", hasText(category) || hasText(status) || hasText(context) || hasText(release));
        model.addAttribute("duplicates", List.of());
        model.addAttribute("aiEnabled", aiClassificationService.isEnabled());
        model.addAttribute("changes", List.of());
        model.addAttribute("imports", List.of());
        if (!model.containsAttribute("reviewRequest")) {
            model.addAttribute("reviewRequest", new ChangeReviewRequest());
        }
        if (!model.containsAttribute("exclusionRequest")) {
            model.addAttribute("exclusionRequest", new ChangeExclusionRequest());
        }
        if (selectedProjectId == null) {
            return "changes";
        }

        try {
            model.addAttribute("changes", inboxService.list(
                    principal.organizationId(),
                    selectedProjectId,
                    ChangeFilter.parse(category, status, context, release)
            ));
            model.addAttribute("suggestions", suggestionService.byChange(principal.organizationId(), selectedProjectId));
            model.addAttribute("duplicates", duplicateService.list(principal.organizationId(), selectedProjectId, null));
            model.addAttribute("imports", importService.status(principal.organizationId(), selectedProjectId));
        } catch (InvalidChangeFilterException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute(UiNotice.PAGE_ATTRIBUTE, UiNotice.error(exception));
        }
        return "changes";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
