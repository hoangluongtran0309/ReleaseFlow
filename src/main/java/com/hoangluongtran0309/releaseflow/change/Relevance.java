package com.hoangluongtran0309.releaseflow.change;

import java.util.Locale;
import java.util.Optional;

/**
 * How much one audience needs to hear about a change (ADR-0035). A relevance is not text
 * and is never translated.
 */
public enum Relevance {
    /** This reader would neither notice nor need the change; its note leaves it out unless it is breaking. */
    NONE,
    /** A headline is enough; templates hide their detail lines. */
    BRIEF,
    /** The reader needs the detail. */
    FULL;

    /** The value in the AI contract and templates. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Relevance> fromValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (Relevance relevance : values()) {
            if (relevance.value().equals(value.strip().toLowerCase(Locale.ROOT))) {
                return Optional.of(relevance);
            }
        }
        return Optional.empty();
    }
}
