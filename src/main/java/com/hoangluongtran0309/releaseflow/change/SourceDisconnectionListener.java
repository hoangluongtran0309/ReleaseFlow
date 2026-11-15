package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.project.SourceDisconnected;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Ends the import or poll a disconnected source had queued, inside the transaction that
 * disconnects it (ADR-0042). A job already running is left to the worker, which reads the
 * source's credentials again before each page and stops when there are none.
 */
@Component
class SourceDisconnectionListener {

    private final SourceSyncJobRepository jobRepository;
    private final Clock clock;

    SourceDisconnectionListener(SourceSyncJobRepository jobRepository, Clock clock) {
        this.jobRepository = jobRepository;
        this.clock = clock;
    }

    @EventListener
    void onDisconnected(SourceDisconnected disconnected) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        jobRepository.findAllByOrganizationIdAndProjectIdOrderByCreatedAtDescIdDesc(
                        disconnected.organizationId(), disconnected.projectId())
                .stream()
                .filter(job -> job.getSourceId().equals(disconnected.sourceId()))
                .filter(SourceSyncJob::isWaiting)
                .forEach(job -> job.fail(SourceSyncWorker.SOURCE_DISCONNECTED, now));
    }
}
