package com.hoangluongtran0309.releaseflow.translation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
 *
 * <p>Code spans are the third kind (ADR-0040, amended 2026-11-19). DeepL translates the words
 * of a span such as {@code `under 1 s`}, so the spans it translated get their source text
 * back, paired in order with the source's spans the translation no longer holds, and only
 * when there are as many of each.
 */
final class SourceForms {

    static final Pattern PLACEHOLDER = Pattern.compile("<\\p{L}[\\p{L}\\p{N}_-]*>");
    // As UntranslatedGuard reads a code span: a backtick, anything but one, and the next one.
    static final Pattern CODE_SPAN = Pattern.compile("`([^`]*)`");
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
        // Spans first: a placeholder inside a restored span comes back with it.
        String restored = spans(source, translation).restored();
        restored = restore(PLACEHOLDER, sourceForms(PLACEHOLDER, source, false), restored);
        return restore(HYPHENATED, sourceForms(HYPHENATED, source, true), restored);
    }

    /**
     * The translated code spans and the source's lost ones, when their numbers differ, so
     * none was restored; empty when there was nothing to pair or every span was paired.
     */
    static Optional<UnpairedSpans> unpairedSpans(String source, String translation) {
        Spans spans = spans(source, translation);
        return spans.translated() == spans.lost()
                ? Optional.empty()
                : Optional.of(new UnpairedSpans(spans.translated(), spans.lost()));
    }

    /** How many spans DeepL translated, and how many of the source's it no longer holds. */
    record UnpairedSpans(int translated, int lost) {
    }

    private record Spans(String restored, int translated, int lost) {
    }

    private record Span(int start, int end, String text) {
    }

    // A translation's spans whose text the source writes are kept. The source's spans are
    // matched to kept spans of the same text, occurrence by occurrence; the translation's
    // other spans are translated, and the source's unmatched ones are lost. Translated spans
    // get the lost spans' text, in order, only when there are as many of each.
    private static Spans spans(String source, String translation) {
        if (source == null || translation == null || !readable(source) || !readable(translation)) {
            return new Spans(translation, 0, 0);
        }
        List<Span> sourceSpans = codeSpans(source);
        List<Span> answerSpans = codeSpans(translation);
        boolean[] kept = new boolean[answerSpans.size()];
        boolean[] used = new boolean[answerSpans.size()];
        for (int index = 0; index < answerSpans.size(); index++) {
            kept[index] = source.contains(answerSpans.get(index).text());
        }
        List<Span> lost = new ArrayList<>();
        for (Span span : sourceSpans) {
            int held = -1;
            for (int index = 0; index < answerSpans.size() && held < 0; index++) {
                if (kept[index] && !used[index] && answerSpans.get(index).text().equals(span.text())) {
                    held = index;
                }
            }
            if (held >= 0) {
                used[held] = true;
            } else {
                lost.add(span);
            }
        }
        List<Span> translated = new ArrayList<>();
        for (int index = 0; index < answerSpans.size(); index++) {
            if (!kept[index]) {
                translated.add(answerSpans.get(index));
            }
        }
        if (translated.isEmpty() || translated.size() != lost.size()) {
            return new Spans(translation, translated.size(), lost.size());
        }
        StringBuilder restored = new StringBuilder(translation.length());
        int last = 0;
        for (int index = 0; index < translated.size(); index++) {
            Span span = translated.get(index);
            restored.append(translation, last, span.start()).append(lost.get(index).text());
            last = span.end();
        }
        return new Spans(restored.append(translation, last, translation.length()).toString(), translated.size(),
                lost.size());
    }

    // A fenced code block, a double-backtick span, or an odd number of backticks would pair
    // backticks that do not belong together, so such a text keeps its spans as written.
    private static boolean readable(String text) {
        return !text.contains("``") && text.chars().filter(character -> character == '`').count() % 2 == 0;
    }

    // Each span's text, and where that text starts and ends, inside its backticks.
    private static List<Span> codeSpans(String text) {
        List<Span> spans = new ArrayList<>();
        Matcher matcher = CODE_SPAN.matcher(text);
        while (matcher.find()) {
            spans.add(new Span(matcher.start(1), matcher.end(1), matcher.group(1)));
        }
        return spans;
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
