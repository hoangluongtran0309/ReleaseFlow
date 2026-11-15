package com.hoangluongtran0309.releaseflow.project;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts the deliveries a connected source refused because their proof failed, without one
 * write per request (ADR-0042). ReleaseFlow has no inbound rate limit, so a flood of forged
 * deliveries on a known path is bounded here: the first failure on a source writes at once,
 * later ones within {@link #WRITE_INTERVAL} are added to a pending count, and a drain once
 * a second writes each pending count in one {@code UPDATE}. Nothing about the request — its
 * body, headers, address, or why it failed — is kept.
 *
 * <p>A pending count names the secret it was refused under, and its write applies only while
 * that is still the source's secret, so failures against a secret just replaced never land
 * on the new one's count.
 */
@Component
class SignatureFailureRecorder {

    static final Duration WRITE_INTERVAL = Duration.ofSeconds(1);

    private static final Logger log = LoggerFactory.getLogger(SignatureFailureRecorder.class);

    private final IntegrationSourceRepository sourceRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    // One entry per source being refused, never per request, so it is as large as the
    // sources under attack at most.
    private final Map<UUID, Slot> slots = new ConcurrentHashMap<>();

    SignatureFailureRecorder(
            IntegrationSourceRepository sourceRepository,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.sourceRepository = sourceRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Counts one refused delivery to a source the path resolved to. */
    void record(IntegrationSource source) {
        if (!source.isConnected()) {
            return;
        }
        Instant now = clock.instant();
        Slot slot = slots.computeIfAbsent(source.getId(), id -> new Slot());
        Pending write;
        synchronized (slot) {
            if (!slot.started || !Objects.equals(source.getSecretUpdatedAt(), slot.secretUpdatedAt)) {
                // A new secret: whatever was pending belongs to the old one and is dropped
                // by its own write's condition, so start afresh.
                slot.started = true;
                slot.secretUpdatedAt = source.getSecretUpdatedAt();
                slot.pending = 0;
                slot.lastWriteAt = null;
            }
            if (slot.lastWriteAt == null || !now.isBefore(slot.lastWriteAt.plus(WRITE_INTERVAL))) {
                slot.lastWriteAt = now;
                write = new Pending(source.getId(), slot.secretUpdatedAt, slot.pending + 1, now);
                slot.pending = 0;
                slot.lastFailureAt = null;
            } else {
                slot.pending++;
                slot.lastFailureAt = now;
                write = null;
            }
        }
        if (write != null) {
            write(write);
        }
    }

    /** Writes every pending count, one {@code UPDATE} per source. */
    @Scheduled(fixedDelayString = "PT1S")
    void drain() {
        Instant now = clock.instant();
        List<Pending> writes = new ArrayList<>();
        slots.forEach((sourceId, slot) -> {
            synchronized (slot) {
                if (slot.pending > 0) {
                    writes.add(new Pending(sourceId, slot.secretUpdatedAt, slot.pending, slot.lastFailureAt));
                    slot.pending = 0;
                    slot.lastFailureAt = null;
                    slot.lastWriteAt = now;
                } else if (slot.lastWriteAt == null || !now.isBefore(slot.lastWriteAt.plus(WRITE_INTERVAL))) {
                    // Nothing left to write and nothing written lately: forget the source.
                    slots.remove(sourceId, slot);
                }
            }
        });
        writes.forEach(this::write);
    }

    @PreDestroy
    void drainOnShutdown() {
        drain();
    }

    private void write(Pending pending) {
        try {
            transactionTemplate.executeWithoutResult(status -> sourceRepository.addSignatureFailures(
                    pending.sourceId(), pending.secretUpdatedAt(), pending.failures(), pending.failedAt()));
        } catch (RuntimeException exception) {
            // Evidence on a page is never worth failing a webhook answer for.
            log.warn("Could not record {} signature failure(s) of source {}.", pending.failures(), pending.sourceId());
        }
    }

    private static final class Slot {
        private boolean started;
        private Instant secretUpdatedAt;
        private long pending;
        private Instant lastFailureAt;
        private Instant lastWriteAt;
    }

    private record Pending(UUID sourceId, Instant secretUpdatedAt, long failures, Instant failedAt) {
    }
}
