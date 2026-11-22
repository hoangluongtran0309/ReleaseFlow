package com.hoangluongtran0309.releaseflow.automation;

import java.time.Instant;
import java.util.UUID;

/**
 * One delivery of one run, and how it ended. {@code noteRevision} is the revision of its
 * note the delivery carried (ADR-0046), or null when it carried no note; {@code correctsRevision}
 * is the revision the delivery a correction replaces had carried, and null on any other.
 */
public record AutomationActionRunView(
        UUID id,
        int position,
        ActionType actionType,
        String audienceName,
        String language,
        ExecutionStatus status,
        String externalReference,
        String errorCode,
        int attempts,
        Instant startedAt,
        Instant completedAt,
        Integer noteRevision,
        Integer correctsRevision
) {

    /** Whether this is the action a retry would carry out again. */
    public boolean retryable() {
        return status == ExecutionStatus.FAILED || status == ExecutionStatus.UNKNOWN;
    }
}
