package com.hoangluongtran0309.releaseflow.release;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A person's decision on a folding suggestion (ADR-0041). Accepting names how the tracker
 * change leaves the note: rejected from the release, or excluded from releases with a reason.
 */
public class FoldDecisionRequest {

    public enum Decision {
        ACCEPT, DISMISS
    }

    public enum Removal {
        REJECT, EXCLUDE
    }

    @NotNull(message = "{validation.foldCodeChange.required}")
    private UUID codeChangeId;

    @NotNull(message = "{validation.foldDecision.required}")
    private Decision decision;

    private Removal removal;

    @Size(max = 500, message = "{validation.exclusionReason.tooLong}")
    private String reason;

    public UUID getCodeChangeId() {
        return codeChangeId;
    }

    public void setCodeChangeId(UUID codeChangeId) {
        this.codeChangeId = codeChangeId;
    }

    public Decision getDecision() {
        return decision;
    }

    public void setDecision(Decision decision) {
        this.decision = decision;
    }

    public Removal getRemoval() {
        return removal;
    }

    public void setRemoval(Removal removal) {
        this.removal = removal;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason == null || reason.isBlank() ? null : reason.strip();
    }
}
