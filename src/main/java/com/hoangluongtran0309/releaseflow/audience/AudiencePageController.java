package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

// Administrator only, by URL rule.
@Controller
public class AudiencePageController {

    private static final String FORM = "audienceForm";

    private final AudienceService audienceService;
    private final ReleaseLanguageService releaseLanguageService;

    AudiencePageController(AudienceService audienceService, ReleaseLanguageService releaseLanguageService) {
        this.audienceService = audienceService;
        this.releaseLanguageService = releaseLanguageService;
    }

    @GetMapping("/audiences")
    String audiences(@AuthenticationPrincipal ReleaseFlowPrincipal principal, Model model, HttpServletResponse response) {
        List<AudienceView> audiences = audienceService.list(principal.organizationId());
        return render(principal, audiences.isEmpty() ? null : audiences.getFirst().id(), model, response);
    }

    @GetMapping("/audiences/new")
    String newAudience(@AuthenticationPrincipal ReleaseFlowPrincipal principal, Model model, HttpServletResponse response) {
        return render(principal, null, model, response);
    }

    @GetMapping("/audiences/{audienceId}")
    String audience(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID audienceId,
            Model model,
            HttpServletResponse response
    ) {
        return render(principal, audienceId, model, response);
    }

    @PostMapping("/audiences")
    String create(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @ModelAttribute(FORM) NewAudienceRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirect,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return render(principal, null, model, response);
        }
        try {
            AudienceView audience = audienceService.create(principal.organizationId(), request);
            UiNotice.flash(redirect, UiNotice.success("ui.audiences.saved"));
            return "redirect:/audiences/" + audience.id();
        } catch (InvalidAudienceTemplateException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            bindingResult.rejectValue("templateBody", exception.messageKey(), exception.arguments(), null);
        } catch (AudienceConflictException exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            if ("audience_code_taken".equals(exception.code())) {
                bindingResult.rejectValue("code", exception.messageKey(), exception.arguments(), null);
            } else {
                model.addAttribute(UiNotice.PAGE_ATTRIBUTE, UiNotice.error(exception));
            }
        }
        return render(principal, null, model, response);
    }

    @PostMapping("/audiences/{audienceId}")
    String update(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID audienceId,
            @Valid @ModelAttribute(FORM) AudienceRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirect,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return render(principal, audienceId, model, response);
        }
        try {
            audienceService.update(principal.organizationId(), audienceId, request);
            UiNotice.flash(redirect, UiNotice.success("ui.audiences.saved"));
            return "redirect:/audiences/" + audienceId;
        } catch (InvalidAudienceTemplateException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            bindingResult.rejectValue("templateBody", exception.messageKey(), exception.arguments(), null);
        }
        return render(principal, audienceId, model, response);
    }

    @PostMapping("/audiences/{audienceId}/delete")
    String delete(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID audienceId,
            RedirectAttributes redirect,
            Model model,
            HttpServletResponse response
    ) {
        try {
            audienceService.delete(principal.organizationId(), audienceId);
            UiNotice.flash(redirect, UiNotice.success("ui.audiences.deleted"));
            return "redirect:/audiences";
        } catch (AudienceConflictException exception) {
            return renderWithError(principal, audienceId, HttpStatus.CONFLICT, exception, model, response);
        }
    }

    @PostMapping("/audiences/{audienceId}/reset-to-preset")
    String resetToPreset(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID audienceId,
            RedirectAttributes redirect,
            Model model,
            HttpServletResponse response
    ) {
        try {
            audienceService.resetToPreset(principal.organizationId(), audienceId);
            UiNotice.flash(redirect, UiNotice.success("ui.audiences.reset"));
            return "redirect:/audiences/" + audienceId;
        } catch (AudienceConflictException exception) {
            return renderWithError(principal, audienceId, HttpStatus.CONFLICT, exception, model, response);
        }
    }

    /** Saves an audience's template for one release note language. */
    @PostMapping("/audiences/{audienceId}/templates/{language}")
    String updateTemplate(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID audienceId,
            @PathVariable String language,
            @ModelAttribute AudienceTemplateRequest request,
            RedirectAttributes redirect,
            Model model,
            HttpServletResponse response
    ) {
        try {
            audienceService.updateTemplate(principal.organizationId(), audienceId, language, request.getTemplateBody());
            UiNotice.flash(redirect, UiNotice.success("ui.audiences.templateSaved"));
            return "redirect:/audiences/" + audienceId + "#language-templates";
        } catch (InvalidAudienceTemplateException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("variantLanguage", language);
            model.addAttribute("variantBody", request.getTemplateBody());
            model.addAttribute("variantError", UiNotice.error(exception));
        }
        return render(principal, audienceId, model, response);
    }

    /** Saves the release note languages, entered as tags separated by commas or spaces. */
    @PostMapping("/audiences/release-languages")
    String replaceReleaseLanguages(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @RequestParam(name = "targetLanguages", defaultValue = "") String targetLanguages,
            RedirectAttributes redirect,
            Model model,
            HttpServletResponse response
    ) {
        ReleaseLanguagesRequest request = new ReleaseLanguagesRequest();
        request.setTargetLanguages(Arrays.asList(targetLanguages.split("[,\\s]+")));
        try {
            releaseLanguageService.replace(principal, request);
            UiNotice.flash(redirect, UiNotice.success("ui.audiences.languagesSaved"));
            return "redirect:/audiences";
        } catch (InvalidReleaseLanguagesException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("languagesError", UiNotice.error(exception));
            model.addAttribute("languagesText", targetLanguages);
        }
        List<AudienceView> audiences = audienceService.list(principal.organizationId());
        return render(principal, audiences.isEmpty() ? null : audiences.getFirst().id(), model, response);
    }

    /**
     * Renders the submitted template with sample values and shows the editor again with
     * the values as entered; nothing is saved.
     */
    @PostMapping("/audiences/preview")
    String preview(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @RequestParam(name = "audienceId", required = false) UUID audienceId,
            @ModelAttribute(FORM) NewAudienceRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        try {
            model.addAttribute("preview", audienceService.preview(request.getTemplateBody()));
        } catch (InvalidAudienceTemplateException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            bindingResult.rejectValue("templateBody", exception.messageKey(), exception.arguments(), null);
        }
        return render(principal, audienceId, model, response);
    }

    private String render(ReleaseFlowPrincipal principal, UUID selectedId, Model model, HttpServletResponse response) {
        UUID organizationId = principal.organizationId();
        model.addAttribute("audiences", audienceService.list(organizationId));
        model.addAttribute("variables", AudienceItem.VARIABLES);
        model.addAttribute("maxAudiences", AudienceService.MAX_AUDIENCES);
        ReleaseLanguageSettings languages = releaseLanguageService.settings(organizationId);
        model.addAttribute("releaseLanguages", languages);
        if (!model.containsAttribute("languagesText")) {
            model.addAttribute("languagesText", String.join(", ", languages.targetLanguages()));
        }
        model.addAttribute("selected", null);
        if (selectedId != null) {
            AudienceView selected = audienceService.get(organizationId, selectedId);
            model.addAttribute("selected", selected);
            if (!model.containsAttribute(FORM)) {
                model.addAttribute(FORM, form(selected));
            }
        }
        if (!model.containsAttribute(FORM)) {
            NewAudienceRequest form = new NewAudienceRequest();
            form.setTemplateBody("- **{{whatChanged}}**{{#narrative}} — {{.}}{{/narrative}}\n");
            model.addAttribute(FORM, form);
        }
        return "audiences";
    }

    private String renderWithError(
            ReleaseFlowPrincipal principal,
            UUID audienceId,
            HttpStatus status,
            LocalizedException exception,
            Model model,
            HttpServletResponse response
    ) {
        response.setStatus(status.value());
        model.addAttribute(UiNotice.PAGE_ATTRIBUTE, UiNotice.error(exception));
        return render(principal, audienceId, model, response);
    }

    private static NewAudienceRequest form(AudienceView audience) {
        NewAudienceRequest form = new NewAudienceRequest();
        form.setCode(audience.code());
        form.setDisplayName(audience.displayName());
        form.setCommunicationIntent(audience.communicationIntent());
        form.setTemplateBody(audience.templateBody());
        return form;
    }
}
