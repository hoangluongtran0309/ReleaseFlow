package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.configuration.UiNotice;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

// Members see a Project's context; saving is administrator only, by URL rule.
@Controller
public class ProjectContextPageController {

    private final ProjectService projectService;

    ProjectContextPageController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/projects/{projectId}/context")
    String context(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            Model model
    ) {
        ProjectContextView context = projectService.contextView(principal.organizationId(), projectId);
        ProjectContextRequest form = new ProjectContextRequest();
        form.setContext(context.context());
        return render(principal, context, form, model);
    }

    @PostMapping("/projects/{projectId}/context")
    String save(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @ModelAttribute("contextForm") ProjectContextRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirect,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return render(principal, projectService.contextView(principal.organizationId(), projectId), request, model);
        }
        projectService.replaceContext(principal, projectId, request);
        UiNotice.flash(redirect, UiNotice.success("ui.projectContext.saved"));
        return "redirect:/projects/" + projectId + "/context";
    }

    private String render(
            ReleaseFlowPrincipal principal,
            ProjectContextView context,
            ProjectContextRequest form,
            Model model
    ) {
        model.addAttribute("project", projectService.get(principal.organizationId(), context.projectId()));
        model.addAttribute("context", context);
        model.addAttribute("contextForm", form);
        model.addAttribute("contextLimit", ProjectContextRequest.LIMIT);
        return "project-context";
    }
}
