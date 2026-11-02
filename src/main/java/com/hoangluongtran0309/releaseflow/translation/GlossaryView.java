package com.hoangluongtran0309.releaseflow.translation;

import java.util.List;

/** An Organization's glossary, and where each language pair it translates stands with DeepL. */
public record GlossaryView(
        List<KeptTermView> keptTerms,
        List<FixedTranslationView> translations,
        List<GlossaryPairView> pairs,
        int entryLimit
) {

    public GlossaryView {
        keptTerms = List.copyOf(keptTerms);
        translations = List.copyOf(translations);
        pairs = List.copyOf(pairs);
    }

    public int entryCount() {
        return keptTerms.size() + translations.size();
    }
}
