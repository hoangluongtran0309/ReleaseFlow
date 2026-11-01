package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.account.OutputLanguageService;
import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.audience.AudienceService;
import com.hoangluongtran0309.releaseflow.category.CategoryService;
import com.hoangluongtran0309.releaseflow.change.ChangeExcludedException;
import com.hoangluongtran0309.releaseflow.change.ChangeProcessingException;
import com.hoangluongtran0309.releaseflow.change.ChangeSummaryRequest;
import com.hoangluongtran0309.releaseflow.change.MigrationKind;
import com.hoangluongtran0309.releaseflow.change.Relevance;
import com.hoangluongtran0309.releaseflow.change.InvalidChangeReviewException;
import com.hoangluongtran0309.releaseflow.overview.OverviewService;
import com.hoangluongtran0309.releaseflow.overview.SelectedProject;
import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;
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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

@Controller
public class ReleasePageController {

    private final ProjectService projectService;
    private final ReleaseService releaseService;
    private final AudienceService audienceService;
    private final OutputLanguageService outputLanguageService;
    private final CategoryService categoryService;
    private final SelectedProject selectedProject;
    private final OverviewService overviewService;



    ReleasePageController(
            ProjectService projectService,
            ReleaseService releaseService,
            AudienceService audienceService,
            OutputLanguageService outputLanguageService,
            CategoryService categoryService,
            SelectedProject selectedProject,
            OverviewService overviewService
    ) {
        this.categoryService = categoryService;
        this.projectService = projectService;
        this.releaseService = releaseService;
        this.audienceService = audienceService;
        this.outputLanguageService = outputLanguageService;
        this.selectedProject = selectedProject;
        this.overviewService = overviewService;
    }

    // A URL that names no Project opens on the one this person was last looking at.
    @GetMapping("/releases")
    String releases(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @RequestParam(name = "project", required = false) UUID projectId,
            @RequestParam(name = "status", required = false) String status,
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
        return renderReleases(principal, selected, status, model, response);
    }

    @PostMapping("/projects/{projectId}/releases")
    String create(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @ModelAttribute("releaseRequest") NewReleaseRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return renderReleases(principal, projectId, null, model, response);
        }
        try {
            ReleaseView release = releaseService.createDraft(principal.organizationId(), projectId, request);
            return "redirect:" + releasePath(projectId, release.id());
        } catch (ReleaseVersionTakenException exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            bindingResult.rejectValue("version", exception.messageKey(), exception.arguments(), null);
        } catch (InvalidReleaseScheduleException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            bindingResult.rejectValue("plannedReleaseAt", exception.messageKey(), exception.arguments(), null);
        } catch (InvalidUpgradeGuideUrlException | InvalidPreviousReleaseException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            bindingResult.rejectValue(linkField(exception), exception.messageKey(), exception.arguments(), null);
        }
        return renderReleases(principal, projectId, null, model, response);
    }

    @GetMapping("/projects/{projectId}/releases/{releaseId}")
    String release(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            Model model,
            HttpServletResponse response
    ) {
        return renderRelease(principal, projectId, releaseId, model, response);
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}")
    String edit(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @Valid @ModelAttribute("releaseRequest") ReleaseRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return renderRelease(principal, projectId, releaseId, model, response);
        }
        return act(principal, projectId, releaseId, model, response, bindingResult,
                failure -> failure instanceof ReleaseVersionTakenException ? "version" : linkField(failure),
                () -> releaseService.edit(principal.organizationId(), projectId, releaseId, request));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/schedule")
    String schedule(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @ModelAttribute("scheduleRequest") ReleaseScheduleRequest request,
            BindingResult bindingResult,
            @RequestParam(name = "clear", defaultValue = "false") boolean clear,
            Model model,
            HttpServletResponse response
    ) {
        // A browser sends a date and time typed only in part as nothing at all, so an
        // empty field is an unfinished one; clearing the schedule is a button of its own.
        if (clear) {
            request.setPlannedReleaseAt(null);
        } else if (request.getPlannedReleaseAt() == null || request.getPlannedReleaseAt().isBlank()) {
            bindingResult.rejectValue("plannedReleaseAt", "validation.plannedReleaseAt.required");
        }
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return renderRelease(principal, projectId, releaseId, model, response);
        }
        return act(principal, projectId, releaseId, model, response, bindingResult,
                failure -> failure instanceof InvalidReleaseScheduleException ? "plannedReleaseAt" : null,
                () -> releaseService.schedule(principal.organizationId(), projectId, releaseId, request));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/discard")
    String discard(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, projectId, releaseId, model, response, "/releases?project=" + projectId,
                () -> releaseService.discard(principal.organizationId(), projectId, releaseId));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/changes")
    String addChanges(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @Valid @ModelAttribute("changesRequest") ReleaseChangesRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return renderRelease(principal, projectId, releaseId, model, response);
        }
        return act(principal, projectId, releaseId, model, response,
                () -> releaseService.addChanges(principal.organizationId(), projectId, releaseId, request));
    }

    // Removes a change from a draft, or rejects it while the release is in review.
    @PostMapping("/projects/{projectId}/releases/{releaseId}/changes/{changeId}/remove")
    String removeChange(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @PathVariable UUID changeId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, projectId, releaseId, model, response,
                () -> releaseService.removeChange(principal.organizationId(), projectId, releaseId, changeId));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/request-review")
    String requestReview(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, projectId, releaseId, model, response,
                () -> releaseService.requestReview(principal.organizationId(), projectId, releaseId));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/changes/{changeId}/decision")
    String decide(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @PathVariable UUID changeId,
            @Valid @ModelAttribute("decisionRequest") ReleaseDecisionRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        // Several changes carry this form; only the one sent shows what was wrong with it.
        model.addAttribute("failingChangeId", changeId);
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return renderRelease(principal, projectId, releaseId, model, response);
        }
        return act(principal, projectId, releaseId, model, response, bindingResult,
                failure -> failure instanceof InvalidChangeReviewException ? "category" : null,
                () -> releaseService.decide(principal, projectId, releaseId, changeId, request));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/approve")
    String approve(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, projectId, releaseId, model, response,
                () -> releaseService.approve(principal, projectId, releaseId));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/return-to-draft")
    String returnToDraft(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, projectId, releaseId, model, response,
                () -> releaseService.returnToDraft(principal.organizationId(), projectId, releaseId));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/changes/{changeId}/summary")
    String editSummary(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @PathVariable UUID changeId,
            @Valid @ModelAttribute("summaryRequest") ChangeSummaryRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        model.addAttribute("failingSummaryChangeId", changeId);
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return renderRelease(principal, projectId, releaseId, model, response);
        }
        return act(principal, projectId, releaseId, model, response,
                () -> releaseService.editSummary(principal, projectId, releaseId, changeId, request));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/notes/{noteId}")
    String editNote(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @PathVariable UUID noteId,
            @Valid @ModelAttribute("noteRequest") ReleaseNoteRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        model.addAttribute("failingNoteId", noteId);
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return renderRelease(principal, projectId, releaseId, model, response);
        }
        return act(principal, projectId, releaseId, model, response,
                () -> releaseService.editNote(principal, projectId, releaseId, noteId, request));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/publish")
    String publish(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, projectId, releaseId, model, response,
                () -> releaseService.publish(principal, projectId, releaseId));
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/translations/retry")
    String retryTranslations(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            Model model,
            HttpServletResponse response
    ) {
        return act(principal, projectId, releaseId, model, response,
                releasePath(projectId, releaseId) + "#release-notes",
                () -> releaseService.retryTranslations(principal.organizationId(), projectId, releaseId));
    }

    private String act(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            UUID releaseId,
            Model model,
            HttpServletResponse response,
            Runnable action
    ) {
        return act(principal, projectId, releaseId, model, response, releasePath(projectId, releaseId), action,
                null, failure -> null);
    }

    /**
     * Runs an action a form sent, and shows a refusal that is about one of the form's
     * fields under that field rather than above the page.
     */
    private String act(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            UUID releaseId,
            Model model,
            HttpServletResponse response,
            BindingResult form,
            Function<LocalizedException, String> fieldOf,
            Runnable action
    ) {
        return act(principal, projectId, releaseId, model, response, releasePath(projectId, releaseId), action,
                form, fieldOf);
    }

    // Runs one release action and redirects, or renders the release page with the failure.
    private String act(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            UUID releaseId,
            Model model,
            HttpServletResponse response,
            String successPath,
            Runnable action
    ) {
        return act(principal, projectId, releaseId, model, response, successPath, action, null, failure -> null);
    }

    private String act(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            UUID releaseId,
            Model model,
            HttpServletResponse response,
            String successPath,
            Runnable action,
            BindingResult form,
            Function<LocalizedException, String> fieldOf
    ) {
        try {
            action.run();
        } catch (InvalidReleaseScheduleException
                 | InvalidChangeReviewException
                 | InvalidUpgradeGuideUrlException
                 | InvalidPreviousReleaseException exception) {
            return refuse(principal, projectId, releaseId, HttpStatus.BAD_REQUEST, exception, form, fieldOf, model, response);
        } catch (ReleasePublishedException
                 | ReleaseStatusException
                 | ReleaseEmptyException
                 | ReleaseReviewIncompleteException
                 | ReleaseVersionTakenException
                 | ClassificationChangedException
                 | ChangeNotReleasableException
                 | ChangeExcludedException
                 | ChangeProcessingException
                 | ReleaseNotesMissingException
                 | TranslationsNotReadyException
                 | ReleaseNoteRenderException exception) {
            return refuse(principal, projectId, releaseId, HttpStatus.CONFLICT, exception, form, fieldOf, model, response);
        }
        return "redirect:" + successPath;
    }

    private String refuse(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            UUID releaseId,
            HttpStatus status,
            LocalizedException failure,
            BindingResult form,
            Function<LocalizedException, String> fieldOf,
            Model model,
            HttpServletResponse response
    ) {
        String field = form == null ? null : fieldOf.apply(failure);
        if (field == null) {
            return renderReleaseWithError(principal, projectId, releaseId, status, failure, model, response);
        }
        response.setStatus(status.value());
        form.rejectValue(field, failure.messageKey(), failure.arguments(), null);
        return renderRelease(principal, projectId, releaseId, model, response);
    }

    private String renderReleases(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            String status,
            Model model,
            HttpServletResponse response
    ) {
        List<ProjectView> projects = projectService.list(principal.organizationId());
        UUID selectedProjectId = projectId;
        if (selectedProjectId == null && !projects.isEmpty()) {
            selectedProjectId = projects.getFirst().id();
        }
        // An unknown status shows every release rather than an error.
        ReleaseStatus selectedStatus = ReleaseStatus.fromValue(status).orElse(null);
        model.addAttribute("projects", projects);
        model.addAttribute("selectedProjectId", selectedProjectId);
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("statuses", ReleaseStatus.values());
        model.addAttribute("releases", List.of());
        model.addAttribute("statusCounts", Map.of());
        model.addAttribute("releaseCount", 0);
        model.addAttribute("projectFound", false);
        model.addAttribute("previousReleases", List.of());
        if (selectedProjectId != null) {
            List<ReleaseSummary> releases = releaseService.list(principal.organizationId(), selectedProjectId);
            List<ReleaseOption> previous = releaseService.previousCandidates(principal.organizationId(), selectedProjectId);
            model.addAttribute("previousReleases", previous);
            if (!model.containsAttribute("releaseRequest")) {
                // A new release follows the most recently published one unless a person chooses.
                NewReleaseRequest request = new NewReleaseRequest();
                request.setPreviousReleaseId(previous.isEmpty() ? null : previous.getFirst().id());
                model.addAttribute("releaseRequest", request);
            }
            Map<ReleaseStatus, Long> counts = new EnumMap<>(ReleaseStatus.class);
            for (ReleaseStatus value : ReleaseStatus.values()) {
                counts.put(value, releases.stream().filter(release -> release.status() == value).count());
            }
            model.addAttribute("releases", releases.stream()
                    .filter(release -> selectedStatus == null || release.status() == selectedStatus)
                    .toList());
            model.addAttribute("statusCounts", counts);
            model.addAttribute("releaseCount", releases.size());
            model.addAttribute("projectFound", true);
        }
        if (!model.containsAttribute("releaseRequest")) {
            model.addAttribute("releaseRequest", new NewReleaseRequest());
        }
        return "releases";
    }


    private String renderRelease(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            UUID releaseId,
            Model model,
            HttpServletResponse response
    ) {
        model.addAttribute("projectId", projectId);
        ReleaseView release = releaseService.get(principal.organizationId(), projectId, releaseId);
        model.addAttribute("release", release);
        if (release.status() == ReleaseStatus.PUBLISHED) {
            return "release-note";
        }
        model.addAttribute("categories", categoryService.active(principal.organizationId()).stream()
                .filter(category -> !category.isUnknown())
                .toList());
        model.addAttribute("audiences", audienceService.list(principal.organizationId()));
        model.addAttribute("migrationKinds", MigrationKind.values());
        model.addAttribute("relevances", Relevance.values());
        model.addAttribute("language", outputLanguageService.outputLanguage(principal.organizationId()).tag());
        model.addAttribute("notePreviews", notePreviews(principal, projectId, release, model));
        model.addAttribute("availableChanges", release.status() == ReleaseStatus.DRAFT
                ? releaseService.availableChanges(principal.organizationId(), projectId, releaseId)
                : List.of());
        model.addAttribute("previousReleases", release.status() == ReleaseStatus.DRAFT
                ? releaseService.previousCandidates(principal.organizationId(), projectId).stream()
                .filter(option -> !option.id().equals(releaseId))
                .toList()
                : List.of());
        if (!model.containsAttribute("releaseRequest")) {
            ReleaseRequest request = new ReleaseRequest();
            request.setVersion(release.version());
            request.setSummary(release.summary());
            request.setUpgradeGuideUrl(release.upgradeGuideUrl());
            request.setPreviousReleaseId(release.previousReleaseId());
            model.addAttribute("releaseRequest", request);
        }
        // The forms the page repeats or keeps small read their errors from these.
        for (Map.Entry<String, Supplier<Object>> form : Map.<String, Supplier<Object>>of(
                "scheduleRequest", ReleaseScheduleRequest::new,
                "changesRequest", ReleaseChangesRequest::new,
                "decisionRequest", ReleaseDecisionRequest::new,
                "summaryRequest", ChangeSummaryRequest::new,
                "noteRequest", ReleaseNoteRequest::new
        ).entrySet()) {
            if (!model.containsAttribute(form.getKey())) {
                model.addAttribute(form.getKey(), form.getValue().get());
            }
        }
        return "release";
    }

    // A template that cannot render only hides the preview; approval reports it as an error.
    private List<AudienceNotePreview> notePreviews(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            ReleaseView release,
            Model model
    ) {
        if (release.status() == ReleaseStatus.APPROVED) {
            return List.of();
        }
        try {
            return releaseService.previewNotes(principal.organizationId(), projectId, release.id());
        } catch (ReleaseNoteRenderException exception) {
            if (!model.containsAttribute(UiNotice.PAGE_ATTRIBUTE)) {
                model.addAttribute(UiNotice.PAGE_ATTRIBUTE, UiNotice.error(exception));
            }
            return List.of();
        }
    }


    private String renderReleaseWithError(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            UUID releaseId,
            HttpStatus status,
            LocalizedException exception,
            Model model,
            HttpServletResponse response
    ) {
        response.setStatus(status.value());
        model.addAttribute(UiNotice.PAGE_ATTRIBUTE, UiNotice.error(exception));
        return renderRelease(principal, projectId, releaseId, model, response);
    }

    // The field of the release form a refused link belongs under, or null for another failure.
    private static String linkField(LocalizedException failure) {
        if (failure instanceof InvalidUpgradeGuideUrlException) {
            return "upgradeGuideUrl";
        }
        return failure instanceof InvalidPreviousReleaseException ? "previousReleaseId" : null;
    }

    private static String releasePath(UUID projectId, UUID releaseId) {
        return "/projects/" + projectId + "/releases/" + releaseId;
    }
}
