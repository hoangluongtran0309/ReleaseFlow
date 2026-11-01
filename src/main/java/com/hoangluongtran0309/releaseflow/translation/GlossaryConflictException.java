package com.hoangluongtran0309.releaseflow.translation;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** A glossary edit the glossary's current entries do not allow. */
public class GlossaryConflictException extends LocalizedException {

    private final String code;

    private GlossaryConflictException(String code, Object... arguments) {
        super("error." + code, arguments);
        this.code = code;
    }

    static GlossaryConflictException taken(String term) {
        return new GlossaryConflictException("glossary_term_taken", term);
    }

    static GlossaryConflictException limit(int limit) {
        return new GlossaryConflictException("glossary_limit", limit);
    }

    public String code() {
        return code;
    }
}
