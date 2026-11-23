package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.account.OutputLanguageService;
import com.hoangluongtran0309.releaseflow.change.AudienceNarrative;
import com.hoangluongtran0309.releaseflow.change.ChangeView;
import com.hoangluongtran0309.releaseflow.change.NeutralSummary;
import com.hoangluongtran0309.releaseflow.translation.GlossaryUse;
import com.hoangluongtran0309.releaseflow.translation.PublishedTranslation;
import com.hoangluongtran0309.releaseflow.translation.TranslationService;
import com.hoangluongtran0309.releaseflow.translation.TranslationState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Puts a release's changes, and its own summary, into one note language. A change whose
 * summary is in another language uses its translation once that is ready, and its own
 * text until then; so does the release's summary, which a person wrote in the
 * Organization's output language. The note is only as ready as the least ready of them,
 * and says what their translations did with the glossary only as all of them did it.
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
    static final String HEADLINE = "headline.";
    static final String RELEASE_SUMMARY = "releaseSummary";

    private final TranslationService translationService;
    private final OutputLanguageService outputLanguageService;

    ReleaseNoteWriter(TranslationService translationService, OutputLanguageService outputLanguageService) {
        this.translationService = translationService;
        this.outputLanguageService = outputLanguageService;
    }

    /**
     * @param retry whether to start failed translations over first
     * @param untranslated the audience codes whose narratives no note of this release needs:
     *                     the retired audiences it has no note for (ADR-0044); empty when none
     *                     is retired, so the texts are what they always were
     */
    /**
     * What rendering a release's notes again into this language would send to the translation
     * provider, with {@code summary} as the release's summary (ADR-0047): the characters of its
     * changes' texts and of the summary, each counted as {@link #localize} would ensure them,
     * without writing anything or calling the provider.
     */
    Characters charactersToSend(Release release, String summary, List<ChangeView> changes, String language,
                                Set<String> untranslated) {
        int changeCharacters = 0;
        for (ChangeView change : changes) {
            if (change.neutralSummary() == null || change.contentLanguage() == null) {
                continue;
            }
            changeCharacters += translationService.charactersToSend(release.getOrganizationId(), release.getProjectId(),
                    change.id(), change.contentLanguage(), language, texts(change, untranslated));
        }
        int summaryCharacters = 0;
        if (summary != null && !summary.isBlank()) {
            String sourceLanguage = outputLanguageService.outputLanguage(release.getOrganizationId()).tag();
            summaryCharacters = translationService.charactersToSendForRelease(release.getOrganizationId(),
                    release.getProjectId(), release.getId(), sourceLanguage, language, Map.of(RELEASE_SUMMARY, summary));
        }
        return new Characters(changeCharacters, summaryCharacters);
    }

    /** The characters one language would send: its changes' texts, and the release's summary. */
    record Characters(int changes, int summary) {

        int total() {
            return changes + summary;
        }
    }

    Localized localize(Release release, List<ChangeView> changes, String language, boolean retry,
                       Set<String> untranslated) {
        List<ChangeView> localized = new ArrayList<>(changes.size());
        TranslationState.Status status = TranslationState.Status.READY;
        List<GlossaryUse> glossary = new ArrayList<>();
        for (ChangeView change : changes) {
            NeutralSummary summary = change.neutralSummary();
            if (summary == null || change.contentLanguage() == null) {
                // Without a summary a change reads as its pull request title, which is not translated.
                localized.add(change);
                continue;
            }
            Map<String, String> texts = texts(change, untranslated);
            TranslationState state = retry
                    ? translationService.retry(release.getOrganizationId(), release.getProjectId(), change.id(),
                    change.contentLanguage(), language, texts)
                    : translationService.ensure(release.getOrganizationId(), release.getProjectId(), change.id(),
                    change.contentLanguage(), language, texts);
            glossary.add(state.glossary());
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
            glossary.add(state.glossary());
            if (state.ready()) {
                summary = state.texts().get(RELEASE_SUMMARY);
            } else {
                status = worse(status, state.status());
            }
        }
        return new Localized(localized, summary, status, GlossaryUse.combine(glossary));
    }

    /**
     * A published release's changes and summary in one note's language, for rendering that note
     * again (ADR-0046, amended 2026-11-20): each text the note uses, its summary fields and its
     * own audience's headline and narrative, as the release was published with it. Nothing is
     * asked of a provider and no job is created; a text with no answer stays in its own
     * language and is counted.
     */
    Published localizePublished(Release release, List<ChangeView> changes, String language, String audienceCode) {
        List<ChangeView> localized = new ArrayList<>(changes.size());
        List<GlossaryUse> glossary = new ArrayList<>();
        int missingTexts = 0;
        int missingCharacters = 0;
        for (ChangeView change : changes) {
            NeutralSummary summary = change.neutralSummary();
            if (summary == null || change.contentLanguage() == null) {
                localized.add(change);
                continue;
            }
            PublishedTranslation found = translationService.published(release.getOrganizationId(), change.id(),
                    change.contentLanguage(), language, textsFor(change, audienceCode), release.getPublishedAt());
            glossary.add(found.glossary());
            missingTexts += found.missing().size();
            missingCharacters += found.missingCharacters();
            localized.add(translated(change, found.texts(), language));
        }
        String summary = release.getSummary();
        if (summary != null && !summary.isBlank()) {
            String sourceLanguage = outputLanguageService.outputLanguage(release.getOrganizationId()).tag();
            PublishedTranslation found = translationService.publishedForRelease(release.getOrganizationId(),
                    release.getId(), sourceLanguage, language, Map.of(RELEASE_SUMMARY, summary), release.getPublishedAt());
            glossary.add(found.glossary());
            missingTexts += found.missing().size();
            missingCharacters += found.missingCharacters();
            summary = found.texts().get(RELEASE_SUMMARY);
        }
        return new Published(localized, summary, missingTexts, missingCharacters, GlossaryUse.combine(glossary));
    }

    // The texts one audience's note uses: the summary fields, and that audience's own headline
    // and narrative. Another audience's are not this note's, so none of them is looked for.
    private static Map<String, String> textsFor(ChangeView change, String audienceCode) {
        Map<String, String> texts = texts(change);
        texts.keySet().removeIf(key -> (key.startsWith(NARRATIVE) || key.startsWith(HEADLINE))
                && !key.equals(NARRATIVE + audienceCode) && !key.equals(HEADLINE + audienceCode));
        return texts;
    }

    static Map<String, String> texts(ChangeView change) {
        return texts(change, Set.of());
    }

    /**
     * The texts of a change that are translated: its summary fields and each active
     * audience's headline and body. With no audience retired, these are every audience's, so
     * the request and its input hash are what they always were.
     */
    static Map<String, String> texts(ChangeView change, Set<String> retired) {
        NeutralSummary summary = change.neutralSummary();
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put(WHAT_CHANGED, summary.whatChanged());
        texts.put(WHY_CHANGED, summary.whyChanged());
        texts.put(TECHNICAL_DETAIL, summary.technicalDetail());
        texts.put(MIGRATION_STEP, summary.migrationStep());
        // Blank texts are never sent, so a summary without limitations hashes as it did before.
        texts.put(LIMITATIONS, summary.limitations());
        change.audienceNarrativeDetails().forEach((code, narrative) -> {
            if (retired.contains(code)) {
                return;
            }
            texts.put(NARRATIVE + code, narrative.body());
            // Blank, so not sent, for a narrative without one: its translation hashes as before.
            texts.put(HEADLINE + code, narrative.headline());
        });
        return texts;
    }

    // Neither the migration kind nor a relevance is text: the translation keeps the source's.
    static ChangeView translated(ChangeView change, Map<String, String> texts, String language) {
        NeutralSummary summary = new NeutralSummary(
                texts.get(WHAT_CHANGED),
                texts.get(WHY_CHANGED),
                texts.get(TECHNICAL_DETAIL),
                texts.get(MIGRATION_STEP),
                change.neutralSummary().migrationKind(),
                texts.getOrDefault(LIMITATIONS, "")
        );
        Map<String, AudienceNarrative> narratives = new LinkedHashMap<>();
        change.audienceNarrativeDetails().forEach((code, narrative) -> narratives.put(code, new AudienceNarrative(
                narrative.relevance(),
                texts.getOrDefault(HEADLINE + code, ""),
                texts.getOrDefault(NARRATIVE + code, "")
        )));
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

    /**
     * A release's changes and summary in one note language, how ready that language is,
     * and what its translations did with the glossary.
     */
    record Localized(List<ChangeView> changes, String summary, TranslationState.Status status, GlossaryUse glossary) {
    }

    /**
     * A published release's changes and summary in one note's language, how many of the
     * note's texts had no answer and how long they are, and what the answers found did with
     * the glossary.
     */
    record Published(List<ChangeView> changes, String summary, int missingTexts, int missingCharacters,
                     GlossaryUse glossary) {
    }
}
