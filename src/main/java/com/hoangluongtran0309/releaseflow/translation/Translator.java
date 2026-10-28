package com.hoangluongtran0309.releaseflow.translation;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;

/** A machine translation provider. It is always called outside a database transaction. */
interface Translator {

    /**
     * @param context    what the Project's administrators say about the product, which
     *                   guides the translation and is not itself translated; empty for none
     * @param glossaryId the provider's glossary holding a dictionary for this language pair,
     *                   or {@code null} for none
     * @return one translation per text, in the same order
     */
    List<String> translate(List<String> texts, String sourceLanguage, String targetLanguage, String context,
                           String glossaryId) throws TranslationException;

    /** The primary languages a glossary can translate from and into. */
    GlossaryLanguages glossaryLanguages() throws TranslationException;

    /**
     * Creates a glossary holding one dictionary.
     *
     * @return the provider's ID for it
     */
    String createGlossary(String name, String sourceLanguage, String targetLanguage, String tsv, int entries)
            throws TranslationException;

    /**
     * Replaces one dictionary of a glossary, creating it if the glossary has none for the
     * pair.
     *
     * @throws TranslationException {@link TranslationException#GLOSSARY_NOT_FOUND} when the
     *                              glossary is gone
     */
    void replaceDictionary(String glossaryId, String sourceLanguage, String targetLanguage, String tsv, int entries)
            throws TranslationException;

    static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("A translation provider must not be called inside a database transaction.");
        }
    }

    record GlossaryLanguages(Set<String> sources, Set<String> targets) {

        public GlossaryLanguages {
            sources = Set.copyOf(sources);
            targets = Set.copyOf(targets);
        }

        boolean supports(String sourceLanguage, String targetLanguage) {
            return sources.contains(sourceLanguage) && targets.contains(targetLanguage);
        }
    }
}
