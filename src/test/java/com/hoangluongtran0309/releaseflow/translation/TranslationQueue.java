package com.hoangluongtran0309.releaseflow.translation;

import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs the glossary sync and translation queues to the end, for a test outside this package
 * that approves releases with the workers' schedules turned off. Every row is made due first,
 * so a retry's backoff never makes a test wait.
 */
public final class TranslationQueue {

    private TranslationQueue() {
    }

    public static void drain(ApplicationContext context, JdbcTemplate jdbcTemplate) {
        GlossarySyncWorker syncWorker = context.getBean(GlossarySyncWorker.class);
        TranslationWorker worker = context.getBean(TranslationWorker.class);
        do {
            makeDue(jdbcTemplate);
        } while (syncWorker.processOne());
        do {
            makeDue(jdbcTemplate);
        } while (worker.processOne());
    }

    private static void makeDue(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("UPDATE glossary_syncs SET next_attempt_at = now() - interval '1 second' WHERE status = 'PENDING'");
        jdbcTemplate.update("UPDATE translation_jobs SET next_attempt_at = now() - interval '1 second' WHERE status = 'PENDING'");
    }
}
