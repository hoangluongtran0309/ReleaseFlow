package com.hoangluongtran0309.releaseflow.translation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Translates due jobs. A claim and a result are each written in a short transaction; the
 * provider is called between them, only for texts not already in the cache. Transient
 * failures are retried with backoff, at most {@value #MAX_ATTEMPTS} times in all.
 *
 * <p>A job that asked for a glossary DeepL holds is sent with it (ADR-0036); one whose
 * pair is unsupported, or whose sync failed, is sent without it and records why. Every
 * answer for a guarded language is checked before it is stored: a text that came back in
 * its source language is translated once more without the glossary, in the same attempt,
 * and fails the job if it comes back so again. A failing text is never cached.
 */
@Component
class TranslationWorker {

    static final int MAX_ATTEMPTS = 5;
    static final Duration STALE_AFTER = Duration.ofMinutes(10);
    static final String UNEXPECTED_ERROR = "unexpected_error";

    private static final Logger log = LoggerFactory.getLogger(TranslationWorker.class);

    private final TranslationJobRepository jobRepository;
    private final TranslationCacheRepository cacheRepository;
    private final GlossarySyncRepository syncRepository;
    private final TranslationGlossaryRepository glossaryRepository;
    private final GlossaryService glossaryService;
    private final TranslationProvider provider;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final boolean enabled;

    TranslationWorker(
            TranslationJobRepository jobRepository,
            TranslationCacheRepository cacheRepository,
            GlossarySyncRepository syncRepository,
            TranslationGlossaryRepository glossaryRepository,
            GlossaryService glossaryService,
            TranslationProvider provider,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${releaseflow.translation.worker-enabled}") boolean enabled
    ) {
        this.jobRepository = jobRepository;
        this.cacheRepository = cacheRepository;
        this.syncRepository = syncRepository;
        this.glossaryRepository = glossaryRepository;
        this.glossaryService = glossaryService;
        this.provider = provider;
        this.events = events;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "PT1S")
    void processDueJobs() {
        if (!enabled) {
            return;
        }
        try {
            while (processOne()) {
                // Keep draining due jobs before sleeping again.
            }
        } catch (RuntimeException exception) {
            log.error("Translation stopped unexpectedly; it resumes on the next tick.", exception);
        }
    }

    /**
     * Works at most one due job.
     *
     * @return whether a job was claimed
     */
    boolean processOne() {
        Instant now = now();
        transactionTemplate.executeWithoutResult(status -> {
            int recovered = jobRepository.recoverStale(now.minus(STALE_AFTER), now);
            if (recovered > 0) {
                log.warn("Recovered {} translation job(s) whose worker stopped responding.", recovered);
            }
        });
        Optional<Claim> claim = transactionTemplate.execute(status -> jobRepository.lockNextDue(now).map(job -> {
            job.claim(now);
            return new Claim(job.getId(), job.getOrganizationId(), job.getSourceLanguage(), job.getTargetLanguage(),
                    job.getInput(), job.getContext(), job.getGlossaryVersion(), glossary(job),
                    glossaryService.verbatimTerms(job.getOrganizationId(), job.getSourceLanguage(), job.getTargetLanguage()),
                    cached(job), job.getAttempts(), now);
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
            finish(claim, job -> job.fail(TranslationService.DISABLED, now()), true);
            return;
        }
        // The last attempt goes without the glossary, so a broken glossary never fails a
        // translation that would succeed without it.
        String glossaryId = claim.attempt() < MAX_ATTEMPTS ? claim.glossary().deeplGlossaryId() : null;
        String withoutReason = claim.glossary().reasonWithout() != null ? claim.glossary().reasonWithout()
                : GlossaryUse.SYNC_FAILED;
        List<String> missing = new ArrayList<>(new LinkedHashSet<>(claim.input().values().stream()
                .filter(text -> !claim.cached().containsKey(text))
                .toList()));
        UntranslatedGuard guard = new UntranslatedGuard(claim.targetLanguage(), claim.verbatimTerms());
        Map<String, String> fresh = new HashMap<>();
        Set<String> withoutGlossary = new HashSet<>();
        try {
            if (!missing.isEmpty()) {
                List<String> translated = translator.get().translate(
                        missing, claim.sourceLanguage(), claim.targetLanguage(), claim.context(), glossaryId);
                List<String> again = new ArrayList<>();
                for (int index = 0; index < missing.size(); index++) {
                    String text = missing.get(index);
                    if (guard.untranslated(translated.get(index)) && glossaryId != null) {
                        again.add(text);
                    } else {
                        fresh.put(text, translated.get(index));
                    }
                }
                if (glossaryId == null) {
                    withoutGlossary.addAll(missing);
                }
                if (!again.isEmpty()) {
                    log.info("Translation job {} came back untranslated with the glossary; translating {} text(s) without it.",
                            claim.jobId(), again.size());
                    List<String> retranslated = translator.get().translate(
                            again, claim.sourceLanguage(), claim.targetLanguage(), claim.context(), null);
                    for (int index = 0; index < again.size(); index++) {
                        fresh.put(again.get(index), retranslated.get(index));
                    }
                    withoutGlossary.addAll(again);
                    withoutReason = GlossaryUse.ANSWER_UNTRANSLATED;
                }
            }
        } catch (TranslationException exception) {
            if (exception.code().equals(TranslationException.GLOSSARY_REFUSED)) {
                resendGlossary(claim);
            }
            failOrRetry(claim, exception.code(), exception.retryable());
            return;
        } catch (RuntimeException exception) {
            log.error("Translation job {} failed unexpectedly.", claim.jobId(), exception);
            failOrRetry(claim, UNEXPECTED_ERROR, true);
            return;
        }
        String outcome = claim.glossaryVersion().isEmpty() ? null
                : withoutGlossary.isEmpty() ? GlossaryUse.APPLIED_OUTCOME : withoutReason;
        if (fresh.values().stream().anyMatch(guard::untranslated)) {
            // Nothing is cached: a person retries the job or edits the note.
            finish(claim, job -> job.fail(TranslationException.UNTRANSLATED, outcome, now()), true);
            log.warn("Translation job {} failed: an answer came back in its source language.", claim.jobId());
            return;
        }

        Map<String, String> output = new LinkedHashMap<>();
        claim.input().forEach((key, text) -> output.put(key, claim.cached().getOrDefault(text, fresh.get(text))));
        Instant at = now();
        finish(claim, job -> {
            job.succeed(output, outcome, at);
            fresh.forEach((text, translation) -> {
                // A text is cached under the glossary version it was actually translated with.
                String version = withoutGlossary.contains(text) ? "" : claim.glossaryVersion();
                TranslationCacheEntry.Key key = new TranslationCacheEntry.Key(
                        claim.organizationId(), claim.sourceLanguage(), claim.targetLanguage(),
                        TranslationTexts.textHash(text, claim.context(), version)
                );
                if (!cacheRepository.existsById(key)) {
                    cacheRepository.save(new TranslationCacheEntry(key, translation, at));
                }
            });
        }, true);
        log.info("Translated job {} into {} ({} text(s), {} from the cache).", claim.jobId(), claim.targetLanguage(),
                claim.input().size(), claim.input().size() - fresh.size());
    }

    // DeepL refused the glossary: its dictionary is sent again before the job is retried.
    private void resendGlossary(Claim claim) {
        transactionTemplate.executeWithoutResult(status -> syncRepository.lock(claim.organizationId(),
                        TranslationTexts.primary(claim.sourceLanguage()), TranslationTexts.primary(claim.targetLanguage()))
                .ifPresent(sync -> {
                    sync.want(claim.glossaryVersion(), now());
                    sync.resend(now());
                }));
    }
    private void failOrRetry(Claim claim, String error, boolean retryable) {
        if (retryable && claim.attempt() < MAX_ATTEMPTS) {
            Instant retryAt = now().plus(backoff(claim.attempt()));
            finish(claim, job -> job.retryLater(error, retryAt), false);
            log.info("Retrying translation job {} at {} after attempt {} ({}).", claim.jobId(), retryAt, claim.attempt(), error);
        } else {
            finish(claim, job -> job.fail(error, now()), true);
            log.warn("Translation job {} failed after attempt {} ({}).", claim.jobId(), claim.attempt(), error);
        }
    }

    // Applies the result if this worker still holds the claim, and tells listeners when it is final.
    private void finish(Claim claim, Consumer<TranslationJob> result, boolean finished) {
        transactionTemplate.executeWithoutResult(status -> jobRepository.findById(claim.jobId())
                .filter(job -> job.isClaimedAt(claim.claimedAt()))
                .ifPresent(job -> {
                    result.accept(job);
                    jobRepository.flush();
                    if (finished) {
                        events.publishEvent(new TranslationFinished(job.getOrganizationId(), job.getChangeId(), job.getReleaseId(),
                                job.getTargetLanguage()));
                    }
                }));
    }

    /**
     * Whether the job can use its glossary: only when DeepL holds exactly the version it
     * asked for. Otherwise, why not.
     */
    private GlossaryChoice glossary(TranslationJob job) {
        if (job.getGlossaryVersion().isEmpty()) {
            return new GlossaryChoice(null, null);
        }
        Optional<GlossarySync> sync = syncRepository.findById(new GlossarySync.Key(job.getOrganizationId(),
                TranslationTexts.primary(job.getSourceLanguage()), TranslationTexts.primary(job.getTargetLanguage())));
        String glossaryId = glossaryRepository.findById(job.getOrganizationId())
                .map(TranslationGlossary::getDeeplGlossaryId)
                .orElse(null);
        if (sync.isPresent() && sync.get().usable(job.getGlossaryVersion()) && glossaryId != null) {
            return new GlossaryChoice(glossaryId, null);
        }
        boolean unsupported = sync.map(found -> found.getStatus() == GlossarySync.Status.UNSUPPORTED).orElse(false);
        return new GlossaryChoice(null, unsupported ? GlossaryUse.UNSUPPORTED : GlossaryUse.SYNC_FAILED);
    }

    // Text -> translation for every input text already in the cache, under the job's glossary version.
    private Map<String, String> cached(TranslationJob job) {
        Map<String, String> byHash = job.getInput().values().stream()
                .distinct()
                .collect(Collectors.toMap(text -> TranslationTexts.textHash(text, job.getContext(), job.getGlossaryVersion()),
                        text -> text));
        Map<String, String> cached = new HashMap<>();
        cacheRepository.findAllByOrganizationIdAndSourceLanguageAndTargetLanguageAndTextHashIn(
                        job.getOrganizationId(), job.getSourceLanguage(), job.getTargetLanguage(), byHash.keySet())
                .forEach(entry -> cached.put(byHash.get(entry.getTextHash()), entry.getTranslatedText()));
        return cached;
    }

    // 2, 4, 8 … seconds, capped at five minutes.
    static Duration backoff(int attempt) {
        return Duration.ofSeconds(Math.min(300, 1L << Math.min(8, attempt)));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private record Claim(
            UUID jobId,
            UUID organizationId,
            String sourceLanguage,
            String targetLanguage,
            Map<String, String> input,
            String context,
            String glossaryVersion,
            GlossaryChoice glossary,
            List<String> verbatimTerms,
            Map<String, String> cached,
            int attempt,
            Instant claimedAt
    ) {
    }

    // The DeepL glossary to send, or the reason the job goes without the one it asked for.
    private record GlossaryChoice(String deeplGlossaryId, String reasonWithout) {
    }
}
