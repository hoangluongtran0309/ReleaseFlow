package com.hoangluongtran0309.releaseflow.translation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tokens a translation keeps exactly as its source wrote them (ADR-0040): angle-bracket
 * placeholders such as {@code <backup-id>}, and hyphenated identifiers the source writes in
 * lowercase, such as {@code dbvault-cli}. With a glossary, DeepL changes their letter case
 * ({@code <BACKUP-id>}, {@code dbvault-CLI}), so a translation gets the source's form back
 * wherever it differs from it in case alone. Only the answer is read: what is sent, hashed,
 * and cached never changes.
 */
final class SourceForms {

    static final Pattern PLACEHOLDER = Pattern.compile("<\\p{L}[\\p{L}\\p{N}_-]*>");
    // Letters and digits joined by hyphens, standing alone: dbvault-cli, but neither a part of
    // a longer token nor a hyphen at an end.
    static final Pattern HYPHENATED = Pattern.compile(
            "(?<![\\p{L}\\p{N}_-])\\p{L}[\\p{L}\\p{N}]*(?:-[\\p{L}\\p{N}]+)+(?![\\p{L}\\p{N}_-])");

    private SourceForms() {
    }

    /**
     * The translation with every placeholder and lowercase hyphenated identifier of the
     * source written as in the source, wherever the translation has it in another letter
     * case. A token the source writes in more than one case is left as the translation has
     * it, and nothing else changes: a translation with nothing to restore is returned as it is.
     */
    static String restore(String source, String translation) {
        if (source == null || translation == null) {
            return translation;
        }
        String restored = restore(PLACEHOLDER, sourceForms(PLACEHOLDER, source, false), translation);
        return restore(HYPHENATED, sourceForms(HYPHENATED, source, true), restored);
    }

    /** The source's placeholders the translation no longer holds in any letter case, in source order. */
    static List<String> lostPlaceholders(String source, String translation) {
        Set<String> sourcePlaceholders = all(PLACEHOLDER, source);
        if (sourcePlaceholders.isEmpty()) {
            return List.of();
        }
        Set<String> held = new HashSet<>();
        all(PLACEHOLDER, translation).forEach(placeholder -> held.add(key(placeholder)));
        return sourcePlaceholders.stream().filter(placeholder -> !held.contains(key(placeholder))).toList();
    }

    private static String restore(Pattern pattern, Map<String, String> forms, String translation) {
        if (forms.isEmpty()) {
            return translation;
        }
        Matcher matcher = pattern.matcher(translation);
        StringBuilder restored = null;
        int last = 0;
        while (matcher.find()) {
            String form = forms.get(key(matcher.group()));
            if (form != null && !form.equals(matcher.group())) {
                if (restored == null) {
                    restored = new StringBuilder(translation.length());
                }
                restored.append(translation, last, matcher.start()).append(form);
                last = matcher.end();
            }
        }
        return restored == null ? translation : restored.append(translation, last, translation.length()).toString();
    }

    // Each token of the source by its case-folded form, kept only when the source writes it in
    // one form, and for identifiers only when that form is lowercase.
    private static Map<String, String> sourceForms(Pattern pattern, String source, boolean lowercaseOnly) {
        Map<String, String> forms = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (String token : all(pattern, source)) {
            String previous = forms.putIfAbsent(key(token), token);
            if (previous != null && !previous.equals(token)) {
                ambiguous.add(key(token));
            }
        }
        ambiguous.forEach(forms::remove);
        if (lowercaseOnly) {
            forms.values().removeIf(form -> !form.equals(key(form)));
        }
        return forms;
    }

    private static Set<String> all(Pattern pattern, String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null) {
            return found;
        }
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }

    private static String key(String token) {
        return token.toLowerCase(Locale.ROOT);
    }
}
