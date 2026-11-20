package com.hoangluongtran0309.releaseflow.release;

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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * The page an administrator corrects a published note on, by hand (ADR-0046). It starts
 * from the note's latest revision; the person edits it, reads the difference, and saves it
 * with a reason. Saving is refused until the exact text saved is the text whose difference
 * was shown, so nobody saves a change they have not read.
 */
@Controller
class NoteCorrectionPageController {

    private final ReleaseNoteCorrectionService correctionService;

    NoteCorrectionPageController(ReleaseNoteCorrectionService correctionService) {
        this.correctionService = correctionService;
    }

    @GetMapping("/projects/{projectId}/releases/{releaseId}/notes/{noteId}/correct")
    String correction(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @PathVariable UUID noteId,
            Model model,
            RedirectAttributes redirect
    ) {
        ReleaseNoteCorrectionService.Draft draft;
        try {
            draft = correctionService.draft(principal.organizationId(), projectId, releaseId, noteId);
        } catch (ReleaseStatusException exception) {
            // Only a published note is corrected; an approved one is edited on its release page.
            UiNotice.flash(redirect, UiNotice.error(exception));
            return "redirect:" + releasePath(projectId, releaseId);
        }
        NoteCorrectionRequest request = new NoteCorrectionRequest();
        request.setCorrects(draft.note().revision());
        request.setContent(draft.note().content());
        model.addAttribute("correctionRequest", request);
        return render(projectId, releaseId, draft, null, model);
    }

    @PostMapping("/projects/{projectId}/releases/{releaseId}/notes/{noteId}/correct")
    String correct(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @PathVariable UUID noteId,
            @Valid @ModelAttribute("correctionRequest") NoteCorrectionRequest request,
            BindingResult bindingResult,
            @RequestParam(defaultValue = "preview") String intent,
            @RequestParam(required = false) String previewed,
            @RequestParam(required = false) String rendered,
            @RequestParam(required = false) String renderedForm,
            Model model,
            HttpServletResponse response,
            RedirectAttributes redirect
    ) {
        ReleaseNoteCorrectionService.Draft draft =
                correctionService.draft(principal.organizationId(), projectId, releaseId, noteId);
        if ("render".equals(intent)) {
            return renderAgain(principal, projectId, releaseId, noteId, draft, request, model, response);
        }
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return render(projectId, releaseId, draft, null, model);
        }
        // The text rendering gave, saved as it was shown, is a rendered revision; anything a
        // person changed in it is theirs, written by hand.
        if (rendered != null && renderedForm != null && renderedForm.equals(hash(request.getContent()))) {
            request.setOrigin(ReleaseNoteRevisionView.Origin.RENDERED);
            request.setPreviewHash(rendered);
            model.addAttribute("rendered", rendered);
            model.addAttribute("renderedForm", renderedForm);
        }
        if (!"save".equals(intent)) {
            return preview(projectId, releaseId, draft, request, model);
        }
        if (!hash(request.getContent()).equals(previewed)) {
            model.addAttribute(UiNotice.PAGE_ATTRIBUTE,
                    new UiNotice(UiNotice.Severity.WARNING, "ui.noteCorrection.previewAgain", List.of()));
            return preview(projectId, releaseId, draft, request, model);
        }
        ReleaseNoteRevisionView saved;
        try {
            saved = correctionService.correct(principal, projectId, releaseId, noteId, request);
        } catch (ReleaseNoteRevisionStaleException exception) {
            // Someone corrected it meanwhile: the person's text is kept and compared with theirs.
            response.setStatus(HttpStatus.CONFLICT.value());
            ReleaseNoteCorrectionService.Draft latest =
                    correctionService.draft(principal.organizationId(), projectId, releaseId, noteId);
            request.setCorrects(latest.note().revision());
            return refuse(projectId, releaseId, latest, request, exception, model);
        } catch (ReleaseNoteCorrectionUnchangedException
                 | ReleaseNoteRenderIncompleteException
                 | ReleaseNoteManualException
                 | NoteAudienceRetiredException exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            return refuse(projectId, releaseId, draft, request, exception, model);
        } catch (ReleaseNotePreviewChangedException exception) {
            // Rendering gives another text now: that one is shown, to be read before saving.
            response.setStatus(HttpStatus.CONFLICT.value());
            model.addAttribute(UiNotice.PAGE_ATTRIBUTE, UiNotice.error(exception));
            return renderAgain(principal, projectId, releaseId, noteId, draft, request, model, response);
        }
        UiNotice.flash(redirect, UiNotice.success("ui.noteCorrection.saved", saved.revision(),
                draft.note().audienceName(), draft.note().language()));
        return "redirect:" + releasePath(projectId, releaseId);
    }

    // Fills the form with the text rendering gives now, and shows its difference.
    private String renderAgain(
            ReleaseFlowPrincipal principal,
            UUID projectId,
            UUID releaseId,
            UUID noteId,
            ReleaseNoteCorrectionService.Draft draft,
            NoteCorrectionRequest request,
            Model model,
            HttpServletResponse response
    ) {
        ReleaseNoteCorrectionService.CorrectionPreview rendered;
        try {
            rendered = correctionService.previewRendered(principal.organizationId(), projectId, releaseId, noteId);
        } catch (ReleaseNoteManualException | NoteAudienceRetiredException exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            model.addAttribute(UiNotice.PAGE_ATTRIBUTE, UiNotice.error(exception));
            return render(projectId, releaseId, draft, null, model);
        }
        request.setCorrects(rendered.corrects());
        request.setContent(rendered.content());
        // The preview's own hash is what saving sends back; the form's text is the same text
        // as the form holds it, so a person's edit can be told from it.
        model.addAttribute("rendered", rendered.previewHash());
        model.addAttribute("renderedForm", hash(request.getContent()));
        model.addAttribute("missingTexts", rendered.missingTexts());
        return preview(projectId, releaseId, draft, request, model);
    }

    private String refuse(
            UUID projectId,
            UUID releaseId,
            ReleaseNoteCorrectionService.Draft draft,
            NoteCorrectionRequest request,
            LocalizedException failure,
            Model model
    ) {
        model.addAttribute(UiNotice.PAGE_ATTRIBUTE, UiNotice.error(failure));
        return preview(projectId, releaseId, draft, request, model);
    }

    private String preview(
            UUID projectId,
            UUID releaseId,
            ReleaseNoteCorrectionService.Draft draft,
            NoteCorrectionRequest request,
            Model model
    ) {
        List<LineDiff.Line> difference = LineDiff.between(draft.note().content(), request.getContent());
        model.addAttribute("previewed", hash(request.getContent()));
        return render(projectId, releaseId, draft, difference, model);
    }

    private String render(
            UUID projectId,
            UUID releaseId,
            ReleaseNoteCorrectionService.Draft draft,
            List<LineDiff.Line> difference,
            Model model
    ) {
        model.addAttribute("projectId", projectId);
        model.addAttribute("releaseId", releaseId);
        model.addAttribute("releasePath", releasePath(projectId, releaseId));
        model.addAttribute("version", draft.version());
        model.addAttribute("note", draft.note());
        model.addAttribute("renderRefusal", draft.renderRefusal());
        model.addAttribute("difference", difference);
        model.addAttribute("changed", difference != null && LineDiff.changes(difference));
        return "note-correction";
    }

    // The text a preview showed, so saving can tell it is the text being saved.
    private static String hash(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is always available.", exception);
        }
    }

    private static String releasePath(UUID projectId, UUID releaseId) {
        return "/projects/" + projectId + "/releases/" + releaseId;
    }
}
