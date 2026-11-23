package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

import java.util.List;

/**
 * An edit of an approved release that would translate more than its new summary, because the
 * Project context or a glossary changed since approval, sent without the character count it
 * would cost, or with a count that is no longer the cost (ADR-0047, amended 2026-11-23). Nothing
 * was saved; the edit is sent again with {@code retranslationCharacters} set to
 * {@link #characters()}.
 */
public class ReleaseEditTranslationException extends LocalizedException {

    private final int characters;
    private final List<String> languages;

    ReleaseEditTranslationException(int characters, List<String> languages) {
        super("error.release_edit_retranslates", characters, String.join(", ", languages));
        this.characters = characters;
        this.languages = List.copyOf(languages);
    }

    public int characters() {
        return characters;
    }

    public List<String> languages() {
        return languages;
    }
}
