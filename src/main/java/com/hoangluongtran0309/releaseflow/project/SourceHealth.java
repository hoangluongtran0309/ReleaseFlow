package com.hoangluongtran0309.releaseflow.project;

import java.time.Instant;

/**
 * What can be said about a source's health from what ReleaseFlow already holds (ADR-0042).
 * It is evidence on a page: nothing computes it on a schedule, nothing is sent anywhere, and
 * it never stops a source, a release, or a rule.
 *
 * @param signatureFailureCount deliveries refused since the secret was last set
 * @param quiet a delivered source that has heard nothing for longer than its own usual gap
 * @param quietSince the last delivery, or the secret's last setting if nothing came since
 * @param usualGapHours the gap the flag is measured against, in whole hours, when known
 * @param overdue a polled source whose next read is more than one interval late
 */
public record SourceHealth(
        boolean connected,
        Instant disconnectedAt,
        Instant secretUpdatedAt,
        long signatureFailureCount,
        Instant lastSignatureFailureAt,
        boolean quiet,
        Instant quietSince,
        Long usualGapHours,
        boolean overdue
) {

    /** Whether there is anything a person should look at. */
    public boolean needsAttention() {
        return connected && (quiet || overdue || signatureFailureCount > 0);
    }
}
