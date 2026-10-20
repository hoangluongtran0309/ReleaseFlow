package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.configuration.UiNotice;
import com.hoangluongtran0309.releaseflow.project.ProjectService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.UUID;

// Members see a Project's sensitive paths; saving is administrator only, by URL rule.
@Controller
public class SensitivePathPageController {

    private final ProjectService projectService;
    private final ProjectSensitivePathService sensitivePathService;

    SensitivePathPageController(
            ProjectService projectService,
            ProjectSensitivePathService sensitivePathService
    ) {
        this.projectService = projectService;
        this.sensitivePathService = sensitivePathService;
    }

    @GetMapping("/projects/{projectId}/sensitive-paths")
    String sensitivePaths(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            Model model
    ) {
        return render(principal, projectId, null, model);
    }

    // One pattern per line; blank lines and repeats are dropped.
    @PostMapping("/projects/{projectId}/sensitive-paths")
    String save(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @RequestParam(name = "additions", defaultValue = "") String additions,
            RedirectAttributes redirect,
            Model model,
            HttpServletResponse response
    ) {
        SensitivePathsRequest request = new SensitivePathsRequest();
        request.setAdditions(Arrays.asList(additions.split("\\R")));
        try {
            sensitivePathService.replace(principal, projectId, request);
            UiNotice.flash(redirect, UiNotice.success("ui.sensitivePaths.saved"));
            return "redirect:/projects/" + projectId + "/sensitive-paths";
        } catch (InvalidSensitivePathsException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("additionsError", UiNotice.error(exception));
            return render(principal, projectId, additions, model);
        }
    }

    private String render(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            String submitted,
            Model model
    ) {
        model.addAttribute("project", projectService.get(principal.organizationId(), projectId));
        SensitivePathsView paths = sensitivePathService.view(principal.organizationId(), projectId);
        model.addAttribute("paths", paths);
        model.addAttribute("additionsText", submitted != null ? submitted : String.join("\n", paths.additions()));
        return "sensitive-paths";
    }
}
