package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.account.OutputLanguageService;
import com.hoangluongtran0309.releaseflow.change.ChangeView;
import com.hoangluongtran0309.releaseflow.change.NeutralSummary;
import com.hoangluongtran0309.releaseflow.translation.TranslationService;
import com.hoangluongtran0309.releaseflow.translation.TranslationState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Puts a release's changes, and its own summary, into one note language. A change whose
 * summary is in another language uses its translation once that is ready, and its own
 * text until then; so does the release's summary, which a person wrote in the
 * Organization's output language. The note is only as ready as the least ready of them.
 * Only the database is touched, so this is safe inside the transaction that approves a
 * release.
 */
@Component
class ReleaseNoteWriter {

    static final String WHAT_CHANGED = "whatChanged";
    static final String WHY_CHANGED = "whyChanged";
    static final String TECHNICAL_DETAIL = "technicalDetail";
    static final String MIGRATION_STEP = "migrationStep";
    static final String LIMITATIONS = "limitations";
    static final String NARRATIVE = "narrative.";
    static final String RELEASE_SUMMARY = "releaseSummary";

    private final TranslationService translationService;
    private final OutputLanguageService outputLanguageService;

    ReleaseNoteWriter(TranslationService translationService, OutputLanguageService outputLanguageService) {
        this.translationService = translationService;
        this.outputLanguageService = outputLanguageService;
    }

    /**
     * @param retry whether to start failed translations over first
     */
    Localized localize(Release release, List<ChangeView> changes, String language, boolean retry) {
        List<ChangeView> localized = new ArrayList<>(changes.size());
        TranslationState.Status status = TranslationState.Status.READY;
        for (ChangeView change : changes) {
            NeutralSummary summary = change.neutralSummary();
            if (summary == null || change.contentLanguage() == null) {
                // Without a summary a change reads as its pull request title, which is not translated.
                localized.add(change);
                continue;
            }
            Map<String, String> texts = texts(change);
            TranslationState state = retry
                    ? translationService.retry(release.getOrganizationId(), release.getProjectId(), change.id(),
                    change.contentLanguage(), language, texts)
                    : translationService.ensure(release.getOrganizationId(), release.getProjectId(), change.id(),
                    change.contentLanguage(), language, texts);
            if (state.ready()) {
                localized.add(translated(change, state.texts(), language));
            } else {
                localized.add(change);
                status = worse(status, state.status());
            }
        }
        String summary = release.getSummary();
        if (summary != null && !summary.isBlank()) {
            String sourceLanguage = outputLanguageService.outputLanguage(release.getOrganizationId()).tag();
            Map<String, String> texts = Map.of(RELEASE_SUMMARY, summary);
            TranslationState state = retry
                    ? translationService.retryForRelease(release.getOrganizationId(), release.getProjectId(),
                    release.getId(), sourceLanguage, language, texts)
                    : translationService.ensureForRelease(release.getOrganizationId(), release.getProjectId(),
                    release.getId(), sourceLanguage, language, texts);
            if (state.ready()) {
                summary = state.texts().get(RELEASE_SUMMARY);
            } else {
                status = worse(status, state.status());
            }
        }
        return new Localized(localized, summary, status);
    }

    /** The texts of a change that are translated: its summary fields and each audience's narrative. */
    static Map<String, String> texts(ChangeView change) {
        NeutralSummary summary = change.neutralSummary();
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put(WHAT_CHANGED, summary.whatChanged());
        texts.put(WHY_CHANGED, summary.whyChanged());
        texts.put(TECHNICAL_DETAIL, summary.technicalDetail());
        texts.put(MIGRATION_STEP, summary.migrationStep());
        // Blank texts are never sent, so a summary without limitations hashes as it did before.
        texts.put(LIMITATIONS, summary.limitations());
        change.audienceNarratives().forEach((code, narrative) -> texts.put(NARRATIVE + code, narrative == null ? "" : narrative));
        return texts;
    }

    // The migration kind is not text: the translation keeps the source's.
    static ChangeView translated(ChangeView change, Map<String, String> texts, String language) {
        NeutralSummary summary = new NeutralSummary(
                texts.get(WHAT_CHANGED),
                texts.get(WHY_CHANGED),
                texts.get(TECHNICAL_DETAIL),
                texts.get(MIGRATION_STEP),
                change.neutralSummary().migrationKind(),
                texts.getOrDefault(LIMITATIONS, "")
        );
        Map<String, String> narratives = new LinkedHashMap<>();
        texts.forEach((key, text) -> {
            if (key.startsWith(NARRATIVE)) {
                narratives.put(key.substring(NARRATIVE.length()), text);
            }
        });
        return change.withContent(summary, narratives, language);
    }

    // A failure needs a person, so it outranks a translation still on its way.
    static TranslationState.Status worse(TranslationState.Status first, TranslationState.Status second) {
        if (first == TranslationState.Status.FAILED || second == TranslationState.Status.FAILED) {
            return TranslationState.Status.FAILED;
        }
        if (first == TranslationState.Status.PENDING || second == TranslationState.Status.PENDING) {
            return TranslationState.Status.PENDING;
        }
        return TranslationState.Status.READY;
    }

    /** A release's changes and summary in one note language, and how ready that language is. */
    record Localized(List<ChangeView> changes, String summary, TranslationState.Status status) {
    }
}
