package com.hoangluongtran0309.releaseflow.translation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Whether an answer came back in its source language (ADR-0036). Each target language has
 * the rule its script allows, and a language with none is not checked:
 * <ul>
 *     <li>Vietnamese: a sentence with at least three words left to translate, none of them
 *     carrying a Vietnamese diacritic;</li>
 *     <li>a language written in another script: a sentence with at least three words left
 *     to translate and no character of that script.</li>
 * </ul>
 * Words left to translate leave out kept terms, fixed target terms, code spans, URLs,
 * paths, file names, and words written in capitals or with a capital inside (CLI,
 * MongoDB), which stay as they are in any language.
 */
final class UntranslatedGuard {

    static final int MIN_WORDS = 3;

    private static final String VIETNAMESE_DIACRITICS =
            "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ";
    private static final Map<String, Set<Character.UnicodeScript>> SCRIPTS = Map.ofEntries(
            Map.entry("ru", Set.of(Character.UnicodeScript.CYRILLIC)),
            Map.entry("uk", Set.of(Character.UnicodeScript.CYRILLIC)),
            Map.entry("bg", Set.of(Character.UnicodeScript.CYRILLIC)),
            Map.entry("el", Set.of(Character.UnicodeScript.GREEK)),
            Map.entry("ar", Set.of(Character.UnicodeScript.ARABIC)),
            Map.entry("he", Set.of(Character.UnicodeScript.HEBREW)),
            Map.entry("th", Set.of(Character.UnicodeScript.THAI)),
            Map.entry("zh", Set.of(Character.UnicodeScript.HAN)),
            Map.entry("ja", Set.of(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA,
                    Character.UnicodeScript.KATAKANA)),
            Map.entry("ko", Set.of(Character.UnicodeScript.HANGUL))
    );
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?;:])\\s+|(?<=[。！？])|\\R+");
    private static final Pattern CODE_SPAN = Pattern.compile("`[^`]*`");
    private static final Pattern URL = Pattern.compile("\\bhttps?://\\S+");
    // A path, a file name, a flag, or an identifier: anything a translation keeps as written.
    private static final Pattern VERBATIM_TOKEN = Pattern.compile("\\S*[/\\\\_=~]\\S*|\\S+\\.\\p{Alnum}\\S*|(?<!\\S)--?\\S+");
    private static final Pattern WORD = Pattern.compile("\\p{L}+");

    private final String language;
    private final List<Pattern> verbatimTerms;

    /**
     * @param targetLanguage the language the answer should be in
     * @param verbatimTerms  terms a correct answer may hold as written: kept terms and fixed target terms
     */
    UntranslatedGuard(String targetLanguage, Collection<String> verbatimTerms) {
        this.language = Locale.forLanguageTag(targetLanguage).getLanguage();
        this.verbatimTerms = verbatimTerms.stream()
                .filter(term -> !term.isBlank())
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(term -> Pattern.compile("(?<![\\p{L}\\p{N}-])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}-])",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
                .toList();
    }

    /** Whether this target language has a rule at all. */
    boolean guards() {
        return language.equals("vi") || SCRIPTS.containsKey(language);
    }

    /** Whether the answer reads as left in its source language. */
    boolean untranslated(String answer) {
        if (!guards() || answer == null) {
            return false;
        }
        for (String sentence : SENTENCE_END.split(answer)) {
            if (untranslatedSentence(sentence)) {
                return true;
            }
        }
        return false;
    }

    private boolean untranslatedSentence(String sentence) {
        if (language.equals("vi")) {
            List<String> words = wordsToTranslate(sentence);
            return words.size() >= MIN_WORDS && words.stream().noneMatch(UntranslatedGuard::vietnamese);
        }
        Set<Character.UnicodeScript> scripts = SCRIPTS.get(language);
        return wordsToTranslate(sentence).size() >= MIN_WORDS
                && sentence.codePoints().noneMatch(codePoint -> scripts.contains(Character.UnicodeScript.of(codePoint)));
    }

    // Latin words a translation would have rewritten.
    private List<String> wordsToTranslate(String sentence) {
        String rest = CODE_SPAN.matcher(sentence).replaceAll(" ");
        rest = URL.matcher(rest).replaceAll(" ");
        rest = VERBATIM_TOKEN.matcher(rest).replaceAll(" ");
        for (Pattern term : verbatimTerms) {
            rest = term.matcher(rest).replaceAll(" ");
        }
        List<String> words = new ArrayList<>();
        var matcher = WORD.matcher(rest);
        while (matcher.find()) {
            String word = matcher.group();
            if (latin(word) && !verbatimWord(word)) {
                words.add(word);
            }
        }
        return words;
    }

    // CLI, HTTP, MongoDB, dbVault: written in capitals, or with a capital after the first letter.
    private static boolean verbatimWord(String word) {
        return word.length() > 1 && word.substring(1).chars().anyMatch(Character::isUpperCase);
    }

    private static boolean latin(String word) {
        return word.codePoints().allMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN);
    }

    private static boolean vietnamese(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        return lower.chars().anyMatch(character -> VIETNAMESE_DIACRITICS.indexOf(character) >= 0);
    }
}
