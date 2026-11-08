package com.hoangluongtran0309.releaseflow.release;

import java.time.Instant;
import java.util.UUID;

/** A recorded decision on a folding suggestion (ADR-0041). */
public record FoldDecisionView(
        UUID trackerChangeId,
        UUID codeChangeId,
        ReleaseFoldDecision.Evidence evidence,
        String issueKey,
        ReleaseFoldDecision.Decision decision,
        ReleaseFoldDecision.Removal removal,
        String reason,
        String deciderName,
        Instant decidedAt
) {
}
