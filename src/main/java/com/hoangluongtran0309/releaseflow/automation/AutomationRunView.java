package com.hoangluongtran0309.releaseflow.automation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One firing of one rule, with the deliveries it made. {@code reason} is the one a correction
 * run gives (ADR-0046), and null on any other run.
 */
public record AutomationRunView(
        UUID id,
        UUID ruleId,
        String ruleName,
        UUID releaseId,
        String releaseVersion,
        TriggerType triggerType,
        UUID requestId,
        String initiatorName,
        String reason,
        ExecutionStatus status,
        boolean cancellationRequested,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        List<AutomationActionRunView> actions
) {

    public AutomationRunView {
        actions = List.copyOf(actions);
    }

    public boolean retryable() {
        return status == ExecutionStatus.FAILED || status == ExecutionStatus.UNKNOWN;
    }

    /** Repeating a delivery that may already have happened takes a confirmation. */
    public boolean needsUnknownConfirmation() {
        return status == ExecutionStatus.UNKNOWN;
    }

    public boolean cancellable() {
        return status != ExecutionStatus.SUCCEEDED && status != ExecutionStatus.CANCELLED;
    }
}
