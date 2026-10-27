package com.hoangluongtran0309.releaseflow.change;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

/**
 * The audience-neutral facts of one change, written once by AI in the Organization's
 * output language. {@code migrationStep} is empty when nothing is needed, and
 * {@code migrationKind} says how much it asks (ADR-0035). {@code limitations} is what
 * the change deliberately does not do yet, or empty.
 *
 * <p>A summary stored before ADR-0035 has neither field: its step reads as
 * {@link MigrationKind#UNSPECIFIED}, and it has no limitations.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NeutralSummary(
        String whatChanged,
        String whyChanged,
        String technicalDetail,
        String migrationStep,
        MigrationKind migrationKind,
        String limitations
) {

    public NeutralSummary {
        Objects.requireNonNull(whatChanged, "whatChanged must not be null");
        Objects.requireNonNull(whyChanged, "whyChanged must not be null");
        Objects.requireNonNull(technicalDetail, "technicalDetail must not be null");
        Objects.requireNonNull(migrationStep, "migrationStep must not be null");
        limitations = Objects.requireNonNullElse(limitations, "");
        if (migrationKind == null) {
            migrationKind = MigrationKind.unnamed(migrationStep);
        }
        if ((migrationKind == MigrationKind.NONE) != migrationStep.isBlank()) {
            throw new IllegalArgumentException("A migration step has a kind other than none, and only a step has one.");
        }
    }

    /** A summary whose writer named no migration kind and no limitations. */
    public NeutralSummary(String whatChanged, String whyChanged, String technicalDetail, String migrationStep) {
        this(whatChanged, whyChanged, technicalDetail, migrationStep, null, "");
    }
}
