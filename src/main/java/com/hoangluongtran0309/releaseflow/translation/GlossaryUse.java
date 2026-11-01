package com.hoangluongtran0309.releaseflow.translation;

import java.util.Collection;

/**
 * What translations did with the Organization's glossary (ADR-0036): {@code NONE} when the
 * pair has no glossary, {@code PENDING} while they wait, {@code APPLIED} when every one used
 * the version it asked for, and {@code NOT_APPLIED} with a reason otherwise:
 * {@code unsupported_language_pair}, {@code glossary_sync_failed}, or
 * {@code glossary_answer_untranslated}. Nothing claims a glossary it did not use.
 */
public record GlossaryUse(Status status, String reason) {

    public static final GlossaryUse NONE = new GlossaryUse(Status.NONE, null);
    public static final GlossaryUse PENDING = new GlossaryUse(Status.PENDING, null);
    public static final GlossaryUse APPLIED = new GlossaryUse(Status.APPLIED, null);

    static final String APPLIED_OUTCOME = "applied";
    static final String UNSUPPORTED = "unsupported_language_pair";
    static final String SYNC_FAILED = "glossary_sync_failed";
    static final String ANSWER_UNTRANSLATED = "glossary_answer_untranslated";

    public enum Status {
        NONE,
        PENDING,
        APPLIED,
        NOT_APPLIED
    }

    public GlossaryUse {
        if ((status == Status.NOT_APPLIED) != (reason != null)) {
            throw new IllegalArgumentException("A reason is given exactly when the glossary was not applied.");
        }
    }

    public static GlossaryUse notApplied(String reason) {
        return new GlossaryUse(Status.NOT_APPLIED, reason);
    }

    /** A job's recorded outcome: {@code applied}, a reason, or nothing. */
    static GlossaryUse fromOutcome(String outcome) {
        if (outcome == null) {
            return NONE;
        }
        return outcome.equals(APPLIED_OUTCOME) ? APPLIED : notApplied(outcome);
    }

    /**
     * Several translations together: not applied if any one was not, then pending, then
     * applied. Translations that had no glossary to use say nothing about the others.
     */
    public static GlossaryUse combine(Collection<GlossaryUse> uses) {
        GlossaryUse combined = NONE;
        for (GlossaryUse use : uses) {
            if (use.status().ordinal() > combined.status().ordinal()) {
                combined = use;
            }
        }
        return combined;
    }
}
