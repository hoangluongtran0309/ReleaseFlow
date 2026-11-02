package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.translation.GlossaryUse;

/**
 * What the translations behind one language's notes did with the Organization's glossary
 * (ADR-0036): {@code PENDING}, {@code APPLIED}, or {@code NOT_APPLIED} with a
 * {@code reason}. A language whose pair has no glossary, or whose notes a person wrote, is
 * not listed.
 */
public record ReleaseGlossaryView(String language, GlossaryUse.Status status, String reason) {

    public boolean notApplied() {
        return status == GlossaryUse.Status.NOT_APPLIED;
    }
}
