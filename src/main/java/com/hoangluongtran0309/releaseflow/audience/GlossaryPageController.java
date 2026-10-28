package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.account.OutputLanguageService;
import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.configuration.UiNotice;
import com.hoangluongtran0309.releaseflow.translation.FixedTranslationRequest;
import com.hoangluongtran0309.releaseflow.translation.GlossaryConflictException;
import com.hoangluongtran0309.releaseflow.translation.GlossaryService;
import com.hoangluongtran0309.releaseflow.translation.KeptTermsRequest;
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

import java.util.Locale;
import java.util.UUID;

// Members see the glossary; every change is administrator only, by URL rule.
@Controller
public class GlossaryPageController {

    private static final String KEPT_FORM = "keptTermForm";
    private static final String SUGGESTIONS_FORM = "suggestionsForm";
    private static final String TRANSLATION_FORM = "translationForm";

    private final GlossaryService glossaryService;
    private final OutputLanguageService outputLanguageService;
    private final ReleaseLanguageService releaseLanguageService;

    GlossaryPageController(
            GlossaryService glossaryService,
            OutputLanguageService outputLanguageService,
            ReleaseLanguageService releaseLanguageService
    ) {
        this.glossaryService = glossaryService;
        this.outputLanguageService = outputLanguageService;
        this.releaseLanguageService = releaseLanguageService;
    }

    @GetMapping("/glossary")
    String glossary(@AuthenticationPrincipal ReleaseFlowPrincipal principal, Model model) {
        return render(principal, model);
    }

    @PostMapping("/glossary/kept-terms")
    String addKeptTerm(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @ModelAttribute(KEPT_FORM) KeptTermsRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirect,
            Model model,
            HttpServletResponse response
    ) {
        return addKeptTerms(principal, request, bindingResult, redirect, model, response, "ui.glossary.termAdded");
    }

    @PostMapping("/glossary/suggestions")
    String acceptSuggestions(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @ModelAttribute(SUGGESTIONS_FORM) KeptTermsRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirect,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasFieldErrors("terms")) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute(UiNotice.PAGE_ATTRIBUTE, UiNotice.error("validation.glossarySuggestions.choose"));
            return render(principal, model);
        }
        return addKeptTerms(principal, request, bindingResult, redirect, model, response, "ui.glossary.suggestionsAdded");
    }

    @PostMapping("/glossary/kept-terms/{termId}/delete")
    String deleteKeptTerm(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID termId,
            RedirectAttributes redirect
    ) {
        glossaryService.deleteKeptTerm(principal.organizationId(), termId);
        UiNotice.flash(redirect, UiNotice.success("ui.glossary.deleted"));
        return "redirect:/glossary";
    }

    @PostMapping("/glossary/translations")
    String addTranslation(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @ModelAttribute(TRANSLATION_FORM) FixedTranslationRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirect,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return render(principal, model);
        }
        try {
            glossaryService.addTranslation(principal.organizationId(), principal.userId(),
                    principal.displayName(), request);
        } catch (GlossaryConflictException exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            if (exception.code().equals("glossary_limit")) {
                bindingResult.reject(exception.messageKey(), exception.arguments(), null);
            } else {
                bindingResult.rejectValue("sourceTerm", exception.messageKey(), exception.arguments(), null);
            }
            return render(principal, model);
        }
        UiNotice.flash(redirect, UiNotice.success("ui.glossary.translationAdded"));
        return "redirect:/glossary";
    }

    @PostMapping("/glossary/translations/{translationId}/delete")
    String deleteTranslation(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID translationId,
            RedirectAttributes redirect
    ) {
        glossaryService.deleteTranslation(principal.organizationId(), translationId);
        UiNotice.flash(redirect, UiNotice.success("ui.glossary.deleted"));
        return "redirect:/glossary";
    }

    private String addKeptTerms(
            ReleaseFlowPrincipal principal,
            KeptTermsRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirect,
            Model model,
            HttpServletResponse response,
            String noticeKey
    ) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return render(principal, model);
        }
        try {
            glossaryService.addKeptTerms(principal.organizationId(), principal.userId(), principal.displayName(),
                    request);
        } catch (GlossaryConflictException exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            model.addAttribute(UiNotice.PAGE_ATTRIBUTE, UiNotice.error(exception));
            return render(principal, model);
        }
        UiNotice.flash(redirect, UiNotice.success(noticeKey));
        return "redirect:/glossary";
    }

    private String render(ReleaseFlowPrincipal principal, Model model) {
        UUID organizationId = principal.organizationId();
        String outputLanguage = outputLanguageService.outputLanguage(organizationId).tag();
        model.addAttribute("glossary", glossaryService.view(organizationId, outputLanguage,
                releaseLanguageService.targetLanguages(organizationId)));
        model.addAttribute("suggestions", glossaryService.suggestions(organizationId));
        model.addAttribute("termLimit", GlossaryService.MAX_TERM_LENGTH);
                if (!model.containsAttribute(KEPT_FORM)) {
            model.addAttribute(KEPT_FORM, new KeptTermsRequest());
        }
        if (!model.containsAttribute(SUGGESTIONS_FORM)) {
            model.addAttribute(SUGGESTIONS_FORM, new KeptTermsRequest());
        }
        if (!model.containsAttribute(TRANSLATION_FORM)) {
            FixedTranslationRequest form = new FixedTranslationRequest();
            form.setSourceLanguage(outputLanguage);
            releaseLanguageService.targetLanguages(organizationId).stream()
                    .map(GlossaryPageController::primary)
                    .filter(language -> !language.equals(form.getSourceLanguage()))
                    .findFirst()
                    .ifPresent(form::setTargetLanguage);
            model.addAttribute(TRANSLATION_FORM, form);
        }
        return "glossary";
    }

    private static String primary(String languageTag) {
        return Locale.forLanguageTag(languageTag).getLanguage();
    }
}
