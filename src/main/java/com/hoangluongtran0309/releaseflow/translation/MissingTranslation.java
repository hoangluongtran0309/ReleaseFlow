package com.hoangluongtran0309.releaseflow.translation;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** A source text and the inputs that distinguish its cached answers within an Organization. */
public record MissingTranslation(String sourceLanguage, String targetLanguage, String context,
                                 String glossaryVersion, String text) {

    static Set<MissingTranslation> find(Map<String, String> input, Map<String, String> answers, String sourceLanguage,
                                        String targetLanguage, String context, String version) {
        return TranslationTexts.missing(input, answers).stream()
                .map(text -> new MissingTranslation(sourceLanguage, targetLanguage, context, version, text))
                .collect(Collectors.toSet());
    }

    public static int characters(Set<MissingTranslation> missing) {
        return missing.stream().mapToInt(item -> item.text.codePointCount(0, item.text.length())).sum();
    }
}
