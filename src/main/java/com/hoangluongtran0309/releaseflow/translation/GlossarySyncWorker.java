package com.hoangluongtran0309.releaseflow.translation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Brings a language pair's dictionary in the Organization's DeepL glossary to the version a
 * translation asked for (ADR-0036). A claim and a result are each written in a short
 * transaction, and DeepL is called between them. The Organization's glossary row is locked
 * while claiming, so one sync per Organization runs at a time: all its pairs live in one
 * DeepL glossary. {@code 429}, every {@code 5xx}, and network failures are retried with the
 * translation worker's backoff, at most {@value TranslationWorker#MAX_ATTEMPTS} times in all;
 * anything else, a {@code 456} included, fails at once.
 */
@Component
class GlossarySyncWorker {

    static final Duration STALE_AFTER = Duration.ofMinutes(10);

    private static final Logger log = LoggerFactory.getLogger(GlossarySyncWorker.class);

    private final GlossarySyncRepository syncRepository;
    private final TranslationGlossaryRepository glossaryRepository;
    private final GlossaryService glossaryService;
    private final TranslationProvider provider;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final boolean enabled;

    GlossarySyncWorker(
            GlossarySyncRepository syncRepository,
            TranslationGlossaryRepository glossaryRepository,
            GlossaryService glossaryService,
            TranslationProvider provider,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${releaseflow.translation.worker-enabled}") boolean enabled
    ) {
        this.syncRepository = syncRepository;
        this.glossaryRepository = glossaryRepository;
        this.glossaryService = glossaryService;
        this.provider = provider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "PT1S")
    void processDueSyncs() {
        if (!enabled || !provider.isEnabled()) {
            return;
        }
        try {
            while (processOne()) {
                // Keep draining due syncs before sleeping again.
            }
        } catch (RuntimeException exception) {
            log.error("Glossary sync stopped unexpectedly; it resumes on the next tick.", exception);
        }
    }

    /**
     * Works at most one due sync.
     *
     * @return whether a sync was claimed
     */
    boolean processOne() {
        Instant now = now();
        transactionTemplate.executeWithoutResult(status -> {
            int recovered = syncRepository.recoverStale(now.minus(STALE_AFTER), now);
            if (recovered > 0) {
                log.warn("Recovered {} glossary sync(s) whose worker stopped responding.", recovered);
            }
        });
        Optional<Claim> claim = transactionTemplate.execute(status -> syncRepository.lockNextDue(now).flatMap(sync -> {
            UUID organizationId = sync.getKey().organizationId();
            glossaryRepository.insertIfMissing(organizationId, now);
            TranslationGlossary glossary = glossaryRepository.lock(organizationId).orElseThrow();
            // Another worker may have claimed a pair of this Organization before the lock.
            if (syncRepository.countRunning(organizationId) > 0) {
                return Optional.empty();
            }
            GlossaryService.Dictionary dictionary = glossaryService.dictionary(organizationId,
                    sync.getKey().sourceLanguage(), sync.getKey().targetLanguage());
            sync.claim(dictionary.version(), now);
            return Optional.of(new Claim(sync.getKey(), dictionary, glossary.getDeeplGlossaryId(), sync.getAttempts(), now));
        }));
        if (claim.isEmpty()) {
            return false;
        }
        work(claim.get());
        return true;
    }

    private void work(Claim claim) {
        Optional<Translator> translator = provider.active();
        if (translator.isEmpty()) {
            finish(claim, sync -> sync.fail(TranslationService.DISABLED, now()), null);
            return;
        }
        GlossarySync.Key key = claim.key();
        GlossaryService.Dictionary dictionary = claim.dictionary();
        if (dictionary.version().isEmpty()) {
            // Every entry was removed since the pair was wanted; DeepL keeps what it has, unused.
            finish(claim, sync -> sync.synced("", now()), null);
            return;
        }
        String createdId = null;
        try {
            if (!translator.get().glossaryLanguages().supports(key.sourceLanguage(), key.targetLanguage())) {
                finish(claim, sync -> sync.unsupported(now()), null);
                log.info("DeepL serves no glossary from {} to {}.", key.sourceLanguage(), key.targetLanguage());
                return;
            }
            int entries = dictionary.entries().size();
            if (claim.deeplGlossaryId() == null) {
                createdId = create(translator.get(), key, dictionary);
            } else {
                try {
                    translator.get().replaceDictionary(claim.deeplGlossaryId(), key.sourceLanguage(), key.targetLanguage(),
                            dictionary.tsv(), entries);
                } catch (TranslationException exception) {
                    if (!exception.code().equals(TranslationException.GLOSSARY_NOT_FOUND)) {
                        throw exception;
                    }
                    // Gone, as after a change of DeepL account: created again in the same attempt.
                    createdId = create(translator.get(), key, dictionary);
                }
            }
        } catch (TranslationException exception) {
            failOrRetry(claim, exception.code(), exception.retryable());
            return;
        } catch (RuntimeException exception) {
            log.error("Glossary sync for Organization {} failed unexpectedly.", key.organizationId(), exception);
            failOrRetry(claim, TranslationWorker.UNEXPECTED_ERROR, true);
            return;
        }
        finish(claim, sync -> sync.synced(dictionary.version(), now()), createdId);
        log.info("Synced the glossary from {} to {} ({} entries).", key.sourceLanguage(), key.targetLanguage(),
                dictionary.entries().size());
    }

    private static String create(Translator translator, GlossarySync.Key key, GlossaryService.Dictionary dictionary)
            throws TranslationException {
        return translator.createGlossary("releaseflow-" + key.organizationId(), key.sourceLanguage(), key.targetLanguage(),
                dictionary.tsv(), dictionary.entries().size());
    }

    private void failOrRetry(Claim claim, String error, boolean retryable) {
        if (retryable && claim.attempt() < TranslationWorker.MAX_ATTEMPTS) {
            Instant retryAt = now().plus(TranslationWorker.backoff(claim.attempt()));
            finish(claim, sync -> sync.retryLater(error, retryAt, now()), null);
            log.info("Retrying a glossary sync at {} after attempt {} ({}).", retryAt, claim.attempt(), error);
        } else {
            finish(claim, sync -> sync.fail(error, now()), null);
            log.warn("A glossary sync failed after attempt {} ({}).", claim.attempt(), error);
        }
    }

    // Applies the result, and records a newly created DeepL glossary, if this worker still holds the claim.
    private void finish(Claim claim, Consumer<GlossarySync> result, String createdGlossaryId) {
        transactionTemplate.executeWithoutResult(status -> syncRepository.lock(claim.key().organizationId(),
                        claim.key().sourceLanguage(), claim.key().targetLanguage())
                .filter(sync -> sync.isClaimedAt(claim.claimedAt()))
                .ifPresent(sync -> {
                    result.accept(sync);
                    if (createdGlossaryId != null) {
                        glossaryRepository.lock(claim.key().organizationId())
                                .ifPresent(glossary -> glossary.created(createdGlossaryId));
                    }
                }));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private record Claim(
            GlossarySync.Key key,
            GlossaryService.Dictionary dictionary,
            String deeplGlossaryId,
            int attempt,
            Instant claimedAt
    ) {
    }
}
