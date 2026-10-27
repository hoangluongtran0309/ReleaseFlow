package com.hoangluongtran0309.releaseflow.change;

import java.util.Locale;
import java.util.Optional;

/**
 * How much a change's migration step asks of the reader (ADR-0035). Only a step the
 * evidence says installations cannot do without is required.
 */
public enum MigrationKind {
    /** Nothing to do; the step is empty. */
    NONE,
    /** Existing installations stop working, or lose a protection, until the step is done. */
    REQUIRED,
    /** Only some installations need it, and the step names which. */
    CONDITIONAL,
    /** Advisable, but nothing breaks without it. */
    RECOMMENDED,
    /**
     * A step recorded before kinds existed, or by a caller that named none. It is printed
     * under the neutral label and never treated as one of the kinds above. The AI never
     * returns it.
     */
    UNSPECIFIED;

    /** The value in the AI contract, the API, and templates. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<MigrationKind> fromValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (MigrationKind kind : values()) {
            if (kind.value().equals(value.strip().toLowerCase(Locale.ROOT))) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    /** The kind of a step whose writer named none: unspecified when there is a step. */
    static MigrationKind unnamed(String migrationStep) {
        return migrationStep == null || migrationStep.isBlank() ? NONE : UNSPECIFIED;
    }
}
