package com.hoangluongtran0309.releaseflow.translation;

import java.util.Map;
import java.util.Set;

/**
 * Texts as a published note was written in its language (ADR-0046, amended 2026-11-20): each
 * found answer, repaired as ADR-0040 repairs any answer, and each text no answer was found for,
 * kept as written in its own language and counted. Nothing was asked of a provider.
 *
 * @param texts      the given texts, each found one replaced by its translation
 * @param missing    the keys of the non-blank texts no answer was found for
 * @param missingCharacters how long those texts are, in characters
 * @param glossary   what the translations that were found did with the glossary
 */
public record PublishedTranslation(Map<String, String> texts, Set<String> missing, int missingCharacters,
                                   GlossaryUse glossary) {

    public PublishedTranslation {
        texts = Map.copyOf(texts);
        missing = Set.copyOf(missing);
    }

    public boolean complete() {
        return missing.isEmpty();
    }
}
