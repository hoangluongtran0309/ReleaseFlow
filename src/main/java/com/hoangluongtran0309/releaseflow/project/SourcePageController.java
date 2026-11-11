package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.configuration.UiNotice;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
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

import java.util.List;
import java.util.UUID;

/**
 * One source's page: its health for everyone, and for administrators its lifecycle — a new
 * webhook secret, disconnecting, and reconnecting (ADR-0042). Every step goes through the
 * same {@link IntegrationSourceService} methods as the REST API.
 */
@Controller
public class SourcePageController {

    private final ProjectService projectService;
    private final IntegrationSourceService sourceService;
    private final SourceTokenBorrowers borrowers;

    SourcePageController(
            ProjectService projectService,
            IntegrationSourceService sourceService,
            SourceTokenBorrowers borrowers
    ) {
        this.projectService = projectService;
        this.sourceService = sourceService;
        this.borrowers = borrowers;
    }

    @GetMapping("/projects/{projectId}/sources/{sourceId}")
    String source(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID sourceId,
            Model model
    ) {
        addPageModel(principal, projectId, sourceId, model);
        return "source";
    }

    /** What disconnecting does, and which rules it leaves without a token, before it is done. */
    @GetMapping("/projects/{projectId}/sources/{sourceId}/disconnect")
    String confirmDisconnect(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID sourceId,
            Model model
    ) {
        IntegrationSourceView source = sourceService.get(principal.organizationId(), projectId, sourceId);
        if (!source.connected()) {
            return "redirect:/projects/" + projectId + "/sources/" + sourceId;
        }
        model.addAttribute("project", projectService.get(principal.organizationId(), projectId));
        model.addAttribute("source", source);
        model.addAttribute("borrowingRules", source.type() == SourceType.GITHUB
                ? borrowers.enabledGitHubReleaseRules(principal.organizationId(), projectId)
                : List.of());
        return "source-disconnect";
    }

    @PostMapping("/projects/{projectId}/sources/{sourceId}/disconnect")
    String disconnect(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID sourceId,
            RedirectAttributes redirect
    ) {
        try {
            sourceService.disconnect(principal.organizationId(), principal.userId(), projectId, sourceId);
            UiNotice.flash(redirect, UiNotice.success("ui.source.disconnectedNotice"));
        } catch (SourceLifecycleException exception) {
            UiNotice.flash(redirect, UiNotice.error(exception));
        }
        return "redirect:/projects/" + projectId + "/sources/" + sourceId;
    }

    @PostMapping("/projects/{projectId}/sources/{sourceId}/reconnect")
    String reconnect(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID sourceId,
            @Valid @ModelAttribute("reconnectRequest") SourceReconnectRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (!bindingResult.hasErrors()) {
            try {
                IntegrationSourceCreated reconnected =
                        sourceService.reconnect(principal.organizationId(), projectId, sourceId, request);
                response.setHeader("Cache-Control", CacheControl.noStore().getHeaderValue());
                model.addAttribute("source", reconnected);
                model.addAttribute("reconnected", true);
                return "source-created";
            } catch (SourceCredentialInvalidException exception) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                bindingResult.rejectValue(exception.field(), exception.messageKey(), exception.arguments(), null);
            } catch (SourceTokenRejectedException exception) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                bindingResult.rejectValue("token", exception.messageKey(), exception.arguments(), null);
            } catch (SourceUnavailableException exception) {
                response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
                bindingResult.reject(exception.messageKey(), exception.arguments(), null);
            } catch (SourceAlreadyConnectedException exception) {
                response.setStatus(HttpStatus.CONFLICT.value());
                bindingResult.reject(exception.messageKey(), exception.arguments(), null);
            } catch (SourceLifecycleException exception) {
                response.setStatus(exception.status().value());
                bindingResult.reject(exception.messageKey(), exception.arguments(), null);
            }
        } else {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
        }
        // Nothing submitted is rendered back: every field here is a credential but the email.
        model.addAttribute("reconnectFailing", true);
        addPageModel(principal, projectId, sourceId, model);
        return "source";
    }

    /** GitHub and GitLab: a secret ReleaseFlow generated, shown on the page that follows only. */
    @PostMapping("/projects/{projectId}/sources/{sourceId}/webhook-secret/generate")
    String generateSecret(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID sourceId,
            Model model,
            RedirectAttributes redirect,
            HttpServletResponse response
    ) {
        try {
            IntegrationSourceCreated rotated = sourceService.generateSecret(principal.organizationId(), projectId, sourceId);
            response.setHeader("Cache-Control", CacheControl.noStore().getHeaderValue());
            model.addAttribute("source", rotated);
            return "source-secret";
        } catch (SourceLifecycleException exception) {
            UiNotice.flash(redirect, UiNotice.error(exception));
            return "redirect:/projects/" + projectId + "/sources/" + sourceId;
        }
    }

    /** Linear, or a signed GitLab source: a secret the provider made, pasted in and never shown. */
    @PostMapping("/projects/{projectId}/sources/{sourceId}/webhook-secret")
    String replaceSecret(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID sourceId,
            @Valid @ModelAttribute("webhookSecretRequest") WebhookSecretRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirect,
            Model model,
            HttpServletResponse response
    ) {
        if (!bindingResult.hasErrors()) {
            try {
                sourceService.replaceSecret(principal.organizationId(), projectId, sourceId, request);
                UiNotice.flash(redirect, UiNotice.success("ui.source.secretSaved"));
                return "redirect:/projects/" + projectId + "/sources/" + sourceId;
            } catch (SourceCredentialInvalidException exception) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                bindingResult.rejectValue(exception.field(), exception.messageKey(), exception.arguments(), null);
            } catch (SourceLifecycleException exception) {
                response.setStatus(exception.status().value());
                bindingResult.reject(exception.messageKey(), exception.arguments(), null);
            }
        } else {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
        }
        model.addAttribute("secretFailing", true);
        addPageModel(principal, projectId, sourceId, model);
        return "source";
    }

    private void addPageModel(ReleaseFlowPrincipal principal, UUID projectId, UUID sourceId, Model model) {
        model.addAttribute("project", projectService.get(principal.organizationId(), projectId));
        model.addAttribute("source", sourceService.get(principal.organizationId(), projectId, sourceId));
        if (!model.containsAttribute("reconnectRequest")) {
            model.addAttribute("reconnectRequest", new SourceReconnectRequest());
        }
        if (!model.containsAttribute("webhookSecretRequest")) {
            model.addAttribute("webhookSecretRequest", new WebhookSecretRequest());
        }
    }
}
