package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.account.OutputLanguageService;
import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.translation.FixedTranslationRequest;
import com.hoangluongtran0309.releaseflow.translation.FixedTranslationView;
import com.hoangluongtran0309.releaseflow.translation.GlossaryService;
import com.hoangluongtran0309.releaseflow.translation.GlossaryView;
import com.hoangluongtran0309.releaseflow.translation.KeptTermView;
import com.hoangluongtran0309.releaseflow.translation.KeptTermsRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Reading the glossary is open to members; every write is administrator only, by URL rule.
@RestController
public class GlossaryApiController {

    private final GlossaryService glossaryService;
    private final OutputLanguageService outputLanguageService;
    private final ReleaseLanguageService releaseLanguageService;

    GlossaryApiController(
            GlossaryService glossaryService,
            OutputLanguageService outputLanguageService,
            ReleaseLanguageService releaseLanguageService
    ) {
        this.glossaryService = glossaryService;
        this.outputLanguageService = outputLanguageService;
        this.releaseLanguageService = releaseLanguageService;
    }

    @GetMapping("/api/glossary")
    GlossaryView glossary(@AuthenticationPrincipal ReleaseFlowPrincipal principal) {
        UUID organizationId = principal.organizationId();
        return glossaryService.view(organizationId, outputLanguageService.outputLanguage(organizationId).tag(),
                releaseLanguageService.targetLanguages(organizationId));
    }

    @GetMapping("/api/glossary/suggestions")
    List<String> suggestions(@AuthenticationPrincipal ReleaseFlowPrincipal principal) {
        return glossaryService.suggestions(principal.organizationId());
    }

    @PostMapping("/api/glossary/kept-terms")
    ResponseEntity<List<KeptTermView>> addKeptTerms(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @RequestBody KeptTermsRequest request
    ) {
        List<KeptTermView> added = glossaryService.addKeptTerms(principal.organizationId(), principal.userId(),
                principal.displayName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(added);
    }

    @DeleteMapping("/api/glossary/kept-terms/{termId}")
    ResponseEntity<Void> deleteKeptTerm(@AuthenticationPrincipal ReleaseFlowPrincipal principal, @PathVariable UUID termId) {
        glossaryService.deleteKeptTerm(principal.organizationId(), termId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/glossary/translations")
    ResponseEntity<FixedTranslationView> addTranslation(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @RequestBody FixedTranslationRequest request
    ) {
        FixedTranslationView added = glossaryService.addTranslation(principal.organizationId(), principal.userId(),
                principal.displayName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(added);
    }

    @DeleteMapping("/api/glossary/translations/{translationId}")
    ResponseEntity<Void> deleteTranslation(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID translationId
    ) {
        glossaryService.deleteTranslation(principal.organizationId(), translationId);
        return ResponseEntity.noContent().build();
    }
}
