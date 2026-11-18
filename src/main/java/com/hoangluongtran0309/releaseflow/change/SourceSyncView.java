package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.project.SourceHealth;
import com.hoangluongtran0309.releaseflow.source.SourceType;

import java.time.Instant;
import java.util.UUID;

/**
 * A source and its latest sync job. Every job field is {@code null} or zero when the
 * source was never synced. A polled source is never imported by hand: its job is the
 * poll ReleaseFlow scheduled itself, and {@code nextPollAt} says when the next one is
 * due.
 */
public record SourceSyncView(
        UUID sourceId,
        SourceType type,
        String externalProjectKey,
        String externalTeamKey,
        String deliveryMechanism,
        boolean supportsImport,
        boolean polled,
        Instant nextPollAt,
        boolean accessTokenConfigured,
        String status,
        Instant windowStart,
        Instant windowEnd,
        int scannedCount,
        int importedCount,
        int itemLimit,
        String requesterName,
        Instant startedAt,
        Instant completedAt,
        Instant nextAttemptAt,
        String lastError,
        Instant lastSyncAt,
        String lastErrorCode,
        boolean canResumeImport,
        Instant lastDeliveryAt,
        SourceHealth health
) {

    public boolean connected() {
        return health.connected();
    }

    /** What the panel calls the source; see {@code IntegrationSourceView.label()}. */
    public String label() {
        return externalTeamKey == null || externalTeamKey.isBlank() ? externalProjectKey : externalTeamKey;
    }

    /** Whether a job is queued or running, so another cannot start. */
    public boolean inProgress() {
        return "PENDING".equals(status) || "RUNNING".equals(status) || "RETRY_SCHEDULED".equals(status);
    }
}
