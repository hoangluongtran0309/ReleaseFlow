package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.project.LinearRefusalLog.Reason;
import com.hoangluongtran0309.releaseflow.project.LinearRefusalLog.Refusal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cap on the log of refused Linear deliveries: one line per source and reason a minute,
 * the next one counting what was left out, and memory only for what was refused lately
 * (ADR-0018, amended 2026-11-19).
 */
@ExtendWith(OutputCaptureExtension.class)
class LinearRefusalLogTest {

    private static final Instant START = Instant.parse("2026-11-19T04:10:02Z");

    private final MovableClock clock = new MovableClock(START);
    private final LinearRefusalLog refusals = new LinearRefusalLog(clock);
    private final UUID source = UUID.fromString("6f1c2a4e-0000-4000-8000-000000000001");

    @Test
    void writesOneLinePerSourceAndReasonAMinuteAndCountsTheRestInTheNext(CapturedOutput output) {
        refusals.refused(source, Refusal.of(Reason.SIGNATURE_MISMATCH));
        for (int second = 1; second <= 41; second++) {
            clock.set(START.plusSeconds(second));
            refusals.refused(source, Refusal.of(Reason.SIGNATURE_MISMATCH));
        }
        assertThat(lines(output)).containsExactly(
                "Refused a Linear delivery to source " + source + ": Linear-Signature does not match the body.");

        // A minute after the line, the next refusal is written, with the count since then.
        clock.set(START.plus(LinearRefusalLog.WINDOW));
        refusals.refused(source, Refusal.of(Reason.SIGNATURE_MISMATCH));

        assertThat(lines(output)).containsExactly(
                "Refused a Linear delivery to source " + source + ": Linear-Signature does not match the body.",
                "Refused a Linear delivery to source " + source + ": Linear-Signature does not match the body."
                        + " Left out 41 more for the same reason since 2026-11-19T04:10:02Z.");
    }

    @Test
    void eachReasonAndEachSourceHasAWindowOfItsOwn(CapturedOutput output) {
        UUID other = UUID.fromString("6f1c2a4e-0000-4000-8000-000000000002");

        refusals.refused(source, Refusal.of(Reason.SIGNATURE_MISMATCH));
        refusals.refused(source, Refusal.of(Reason.NO_TIMESTAMP));
        refusals.refused(other, Refusal.of(Reason.SIGNATURE_MISMATCH));
        // A stale stamp's line carries its offset, but every offset shares one window.
        refusals.refused(source, stale(-75));
        refusals.refused(source, stale(-76));

        assertThat(lines(output)).containsExactly(
                "Refused a Linear delivery to source " + source + ": Linear-Signature does not match the body.",
                "Refused a Linear delivery to source " + source + ": no webhookTimestamp that can be read.",
                "Refused a Linear delivery to source " + other + ": Linear-Signature does not match the body.",
                "Refused a Linear delivery to source " + source
                        + ": webhookTimestamp is -75 s from this server's clock, beyond the allowed 60 s.");
    }

    @Test
    void theSweepWritesWhatWasLeftOutOnceTheMinuteIsOverAndThenForgetsTheEntry(CapturedOutput output) {
        refusals.refused(source, stale(-75));
        clock.set(START.plusSeconds(5));
        refusals.refused(source, stale(-80));
        refusals.refused(source, stale(-81));

        // Inside the minute nothing more is written, and the entry is kept.
        clock.set(START.plusSeconds(59));
        refusals.sweep();
        assertThat(lines(output)).hasSize(1);
        assertThat(refusals.size()).isEqualTo(1);

        // Once it is over, the count is written without waiting for another refusal.
        clock.set(START.plus(LinearRefusalLog.WINDOW).plusSeconds(3));
        refusals.sweep();
        assertThat(lines(output)).last().isEqualTo("Left out 2 more refused Linear deliveries to source " + source
                + " since 2026-11-19T04:10:02Z: webhookTimestamp outside the allowed skew.");

        // A quiet minute after that line, nothing is left to say and the entry goes.
        clock.set(START.plus(LinearRefusalLog.WINDOW.multipliedBy(2)).plusSeconds(3));
        refusals.sweep();
        assertThat(lines(output)).hasSize(2);
        assertThat(refusals.size()).isZero();
    }

    @Test
    void memoryHoldsOnlyWhatWasRefusedLately(CapturedOutput output) {
        // A thousand sources refused once each, as after a deploy that broke every secret.
        for (int index = 0; index < 1000; index++) {
            refusals.refused(UUID.randomUUID(), Refusal.of(Reason.SIGNATURE_MISMATCH));
        }
        assertThat(refusals.size()).isEqualTo(1000);

        clock.set(START.plus(LinearRefusalLog.WINDOW));
        refusals.sweep();

        // None had anything left out, so each is forgotten without another line.
        assertThat(refusals.size()).isZero();
        assertThat(lines(output)).hasSize(1000);
    }

    @Test
    void countsExactlyWhatConcurrentRefusalsLeftOut(CapturedOutput output) throws Exception {
        int threads = 8;
        int perThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> running = new ArrayList<>();
            for (int thread = 0; thread < threads; thread++) {
                running.add(executor.submit(() -> {
                    start.await();
                    for (int attempt = 0; attempt < perThread; attempt++) {
                        refusals.refused(source, Refusal.of(Reason.SIGNATURE_MISMATCH));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> each : running) {
                each.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        clock.set(START.plus(LinearRefusalLog.WINDOW));
        refusals.sweep();

        assertThat(lines(output)).containsExactly(
                "Refused a Linear delivery to source " + source + ": Linear-Signature does not match the body.",
                "Left out " + (threads * perThread - 1) + " more refused Linear deliveries to source " + source
                        + " since 2026-11-19T04:10:02Z: Linear-Signature does not match the body.");
    }

    private static Refusal stale(long offset) {
        return new Refusal(Reason.STALE_TIMESTAMP,
                "webhookTimestamp is %+d s from this server's clock, beyond the allowed 60 s".formatted(offset));
    }

    // The message of every line the log wrote, without the logger's prefix.
    private static List<String> lines(CapturedOutput output) {
        List<String> lines = new ArrayList<>();
        for (String line : output.getAll().split("\\R")) {
            int start = line.indexOf("Refused a Linear delivery");
            if (start < 0) {
                start = line.indexOf("Left out ");
            }
            if (start >= 0 && line.contains("Linear deliver")) {
                lines.add(line.substring(start));
            }
        }
        return lines;
    }

    private static final class MovableClock extends Clock {

        private volatile Instant now;

        private MovableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
