package com.hoangluongtran0309.releaseflow.translation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

interface TranslationCacheRepository extends JpaRepository<TranslationCacheEntry, TranslationCacheEntry.Key> {

    List<TranslationCacheEntry> findAllByOrganizationIdAndSourceLanguageAndTargetLanguageAndTextHashIn(
            UUID organizationId,
            String sourceLanguage,
            String targetLanguage,
            Collection<String> textHashes
    );

    /** Prefer the requested version. A render at publication, or a job that must go
     * without its glossary, may also reuse answers cached under the empty version. */
    default Answers answers(UUID organizationId, String sourceLanguage, String targetLanguage,
                            Collection<String> sources, String context, String version, boolean allowWithoutGlossary) {
        Map<String, String> requested = new HashMap<>();
        Map<String, String> fallback = new HashMap<>();
        for (String text : sources) {
            requested.put(TranslationTexts.textHash(text, context, version), text);
            if (allowWithoutGlossary && !version.isEmpty()) {
                fallback.put(TranslationTexts.textHash(text, context, ""), text);
            }
        }
        Set<String> hashes = new HashSet<>(requested.keySet());
        hashes.addAll(fallback.keySet());
        Map<String, String> answers = new HashMap<>();
        Map<String, String> otherAnswers = new HashMap<>();
        for (TranslationCacheEntry entry : findAllByOrganizationIdAndSourceLanguageAndTargetLanguageAndTextHashIn(
                organizationId, sourceLanguage, targetLanguage, hashes)) {
            String hash = entry.getTextHash();
            if (requested.containsKey(hash)) {
                answers.put(requested.get(hash), entry.getTranslatedText());
            } else {
                otherAnswers.put(fallback.get(hash), entry.getTranslatedText());
            }
        }
        otherAnswers.keySet().removeAll(answers.keySet());
        answers.putAll(otherAnswers);
        return new Answers(answers, otherAnswers.keySet());
    }

    record Answers(Map<String, String> texts, Set<String> withoutGlossary) {
    }
}
