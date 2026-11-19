package com.hoangluongtran0309.releaseflow.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The log of refused Linear deliveries (ADR-0018, amended 2026-11-19): one line per source
 * per reason per {@link #WINDOW} at most, so a flood on a known path cannot fill the log. A
 * refusal inside the window is only counted, and the next line for that source and reason
 * says how many were left out since the line before. When refusals stop, {@link #sweep()}
 * writes that line once the window is over, so a count is never held back for long, and
 * then forgets the entry. Memory is therefore one entry per source and reason refused in
 * the last two windows, at most, never one per request; a source that stops being refused,
 * because it was disconnected or its sender fixed, leaves nothing behind.
 *
 * <p>A line names the source's ID and the reason, and never the body, a header's value, the
 * secret, or the caller.
 */
final class LinearRefusalLog {

    static final Duration WINDOW = Duration.ofMinutes(1);

    // The verifier's own name, so the lines read as they did before the cap.
    private static final Logger log = LoggerFactory.getLogger(LinearWebhookVerifier.class);

    /** Why a delivery was refused: a finite set, so one window per source and reason. */
    enum Reason {
        NO_DELIVERY_HEADER("no Linear-Delivery header"),
        NO_SIGNATURE_HEADER("no Linear-Signature header"),
        SECRET_UNREADABLE("its webhook secret could not be decrypted"),
        SIGNATURE_MISMATCH("Linear-Signature does not match the body"),
        NOT_JSON("the body is not JSON"),
        NOT_AN_OBJECT("the body is not a JSON object"),
        NO_TIMESTAMP("no webhookTimestamp that can be read"),
        STALE_TIMESTAMP("webhookTimestamp outside the allowed skew"),
        OTHER_WORKSPACE("organizationId is not this source's workspace");

        private final String text;

        Reason(String text) {
            this.text = text;
        }

        String text() {
            return text;
        }
    }

    /**
     * One refusal: its reason, and what the line says about it, which is the reason's own
     * text unless the refusal has a detail of its own, such as a stale stamp's offset.
     */
    record Refusal(Reason reason, String detail) {

        static Refusal of(Reason reason) {
            return new Refusal(reason, reason.text());
        }
    }

    private record Key(UUID sourceId, Reason reason) {
    }

    private static final class Window {
        private Instant loggedAt;
        private long leftOut;

        private Window(Instant loggedAt) {
            this.loggedAt = loggedAt;
        }
    }

    // What to write once the map's lock is released, so no line is logged while holding it.
    private record Line(Instant since, long leftOut) {
    }

    private final Clock clock;
    private final Map<Key, Window> windows = new ConcurrentHashMap<>();

    LinearRefusalLog(Clock clock) {
        this.clock = clock;
    }

    /** Logs a refusal, or counts it when this source and reason were logged within the window. */
    void refused(UUID sourceId, Refusal refusal) {
        Instant now = clock.instant();
        Line[] line = new Line[1];
        windows.compute(new Key(sourceId, refusal.reason()), (key, window) -> {
            if (window == null) {
                line[0] = new Line(null, 0);
                return new Window(now);
            }
            if (!now.isBefore(window.loggedAt.plus(WINDOW))) {
                line[0] = new Line(window.loggedAt, window.leftOut);
                window.loggedAt = now;
                window.leftOut = 0;
            } else {
                window.leftOut++;
            }
            return window;
        });
        if (line[0] == null) {
            return;
        }
        if (line[0].leftOut() == 0) {
            log.warn("Refused a Linear delivery to source {}: {}.", sourceId, refusal.detail());
        } else {
            log.warn("Refused a Linear delivery to source {}: {}. Left out {} more for the same reason since {}.",
                    sourceId, refusal.detail(), line[0].leftOut(), line[0].since());
        }
    }

    /**
     * Writes the count of every window that is over with refusals left out, and forgets every
     * window that is over with none, so the map holds only what was refused lately.
     */
    void sweep() {
        Instant now = clock.instant();
        windows.forEach((key, current) -> {
            Line[] line = new Line[1];
            windows.computeIfPresent(key, (ignored, window) -> {
                if (now.isBefore(window.loggedAt.plus(WINDOW))) {
                    return window;
                }
                if (window.leftOut == 0) {
                    return null;
                }
                line[0] = new Line(window.loggedAt, window.leftOut);
                window.loggedAt = now;
                window.leftOut = 0;
                return window;
            });
            if (line[0] != null) {
                log.warn("Left out {} more refused Linear deliveries to source {} since {}: {}.",
                        line[0].leftOut(), key.sourceId(), line[0].since(), key.reason().text());
            }
        });
    }

    /** How many source and reason pairs are remembered, for tests of the bound. */
    int size() {
        return windows.size();
    }
}
