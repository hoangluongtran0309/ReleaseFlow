package com.hoangluongtran0309.releaseflow.translation;

import com.hoangluongtran0309.releaseflow.project.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Translations of change content, and of a release's own summary, one job per change or
 * release, target language, and input. The Project's context (ADR-0035) and the version of
 * the pair's glossary (ADR-0036) are part of the input, so editing either translates again
 * at the next approval, and unchanged input is never sent twice. A job that asks for a
 * glossary DeepL does not hold yet wants it synced. It only reads and writes the database
 * and never calls a provider, so it is safe inside the transaction that approves a release:
 * the workers pick new jobs and syncs up after commit.
 */
@Service
public class TranslationService {

    static final String DISABLED = "translation_disabled";

    private final TranslationJobRepository jobRepository;
    private final TranslationCacheRepository cacheRepository;
    private final TranslationProvider provider;
    private final ProjectService projectService;
    private final GlossaryService glossaryService;
    private final GlossarySyncRepository syncRepository;
    private final Clock clock;

    TranslationService(
            TranslationJobRepository jobRepository,
            TranslationCacheRepository cacheRepository,
            TranslationProvider provider,
            ProjectService projectService,
            GlossaryService glossaryService,
            GlossarySyncRepository syncRepository,
            Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.cacheRepository = cacheRepository;
        this.provider = provider;
        this.projectService = projectService;
        this.glossaryService = glossaryService;
        this.syncRepository = syncRepository;
        this.clock = clock;
    }

    public boolean isEnabled() {
        return provider.isEnabled();
    }

    /**
     * The change's texts in the target language, creating the job that translates them
     * when there is none yet. Texts already in the target language, or all blank, are
     * ready at once; texts all found in the cache make a job that has already succeeded.
     */
    @Transactional
    public TranslationState ensure(
            UUID organizationId,
            UUID projectId,
            UUID changeId,
            String sourceLanguage,
            String targetLanguage,
            Map<String, String> texts
    ) {
        return ensure(new Subject(organizationId, projectId, changeId, null), sourceLanguage, targetLanguage, texts);
    }

    /**
     * A release's own texts, its summary, in the target language: the same queue, cache,
     * and retries as a change's content.
     */
    @Transactional
    public TranslationState ensureForRelease(
            UUID organizationId,
            UUID projectId,
            UUID releaseId,
            String sourceLanguage,
            String targetLanguage,
            Map<String, String> texts
    ) {
        return ensure(new Subject(organizationId, projectId, null, releaseId), sourceLanguage, targetLanguage, texts);
    }

    /**
     * Starts the failed translation of these texts over, then reports where it stands. A
     * translation made without the glossary it asked for is made again, and a failed sync
     * of the pair's glossary starts over. Anything else is left alone, and so is everything
     * while no provider is configured to translate.
     */
    @Transactional
    public TranslationState retry(
            UUID organizationId,
            UUID projectId,
            UUID changeId,
            String sourceLanguage,
            String targetLanguage,
            Map<String, String> texts
    ) {
        return retry(new Subject(organizationId, projectId, changeId, null), sourceLanguage, targetLanguage, texts);
    }

    /** {@link #retry} for a release's own texts. */
    @Transactional
    public TranslationState retryForRelease(
            UUID organizationId,
            UUID projectId,
            UUID releaseId,
            String sourceLanguage,
            String targetLanguage,
            Map<String, String> texts
    ) {
        return retry(new Subject(organizationId, projectId, null, releaseId), sourceLanguage, targetLanguage, texts);
    }

    private TranslationState ensure(Subject subject, String sourceLanguage, String targetLanguage, Map<String, String> texts) {
        Map<String, String> input = TranslationTexts.nonBlank(texts);
        if (input.isEmpty() || TranslationTexts.sameLanguage(sourceLanguage, targetLanguage)) {
            return TranslationState.ready(texts);
        }
        String context = projectService.context(subject.organizationId(), subject.projectId());
        String version = glossaryVersion(subject.organizationId(), sourceLanguage, targetLanguage);
        String hash = TranslationTexts.inputHash(sourceLanguage, input, context, version);
        TranslationJob job = find(subject, targetLanguage, hash)
                .orElseGet(() -> jobRepository.saveAndFlush(
                        newJob(subject, sourceLanguage, targetLanguage, hash, input, context, version)));
        if (!version.isEmpty() && job.getStatus() == TranslationJob.Status.PENDING) {
            want(subject.organizationId(), sourceLanguage, targetLanguage, version);
        }
        return merge(texts, job.state());
    }

    // Without a provider nothing is translated, so nothing asks for a glossary either.
    private String glossaryVersion(UUID organizationId, String sourceLanguage, String targetLanguage) {
        if (!provider.isEnabled()) {
            return "";
        }
        return glossaryService.dictionary(organizationId, sourceLanguage, targetLanguage).version();
    }

    // The pair's sync row is locked, so a sync finishing meanwhile sees what was wanted.
    private void want(UUID organizationId, String sourceLanguage, String targetLanguage, String version) {
        String source = TranslationTexts.primary(sourceLanguage);
        String target = TranslationTexts.primary(targetLanguage);
        Instant now = now();
        syncRepository.insertIfMissing(organizationId, source, target, version, now);
        syncRepository.lock(organizationId, source, target).ifPresent(sync -> {
            sync.want(version, now);
            syncRepository.flush();
        });
    }

    private TranslationState retry(Subject subject, String sourceLanguage, String targetLanguage, Map<String, String> texts) {
        Map<String, String> input = TranslationTexts.nonBlank(texts);
        if (provider.isEnabled() && !input.isEmpty() && !TranslationTexts.sameLanguage(sourceLanguage, targetLanguage)) {
            String context = projectService.context(subject.organizationId(), subject.projectId());
            String version = glossaryVersion(subject.organizationId(), sourceLanguage, targetLanguage);
            if (!version.isEmpty()) {
                syncRepository.lock(subject.organizationId(), TranslationTexts.primary(sourceLanguage),
                        TranslationTexts.primary(targetLanguage)).ifPresent(sync -> sync.restartIfFailed(now()));
            }
            find(subject, targetLanguage, TranslationTexts.inputHash(sourceLanguage, input, context, version))
                    .filter(job -> job.getOrganizationId().equals(subject.organizationId()))
                    .ifPresent(job -> {
                        job.restart(now());
                        job.restartForGlossary(now());
                        jobRepository.flush();
                    });
        }
        return ensure(subject, sourceLanguage, targetLanguage, texts);
    }

    private Optional<TranslationJob> find(Subject subject, String targetLanguage, String hash) {
        return subject.changeId() != null
                ? jobRepository.findByChangeIdAndTargetLanguageAndInputHash(subject.changeId(), targetLanguage, hash)
                : jobRepository.findByReleaseIdAndTargetLanguageAndInputHash(subject.releaseId(), targetLanguage, hash);
    }

    private TranslationJob newJob(
            Subject subject,
            String sourceLanguage,
            String targetLanguage,
            String hash,
            Map<String, String> input,
            String context,
            String version
    ) {
        Instant now = now();
        TranslationJob job = new TranslationJob(
                subject.organizationId(), subject.projectId(), subject.changeId(), subject.releaseId(),
                sourceLanguage, targetLanguage, hash, input, context, version, now
        );
        Optional<Map<String, String>> cached = cached(subject.organizationId(), sourceLanguage, targetLanguage, input,
                context, version);
        if (cached.isPresent()) {
            // Texts are cached under the glossary version they were translated with.
            job.succeed(cached.get(), version.isEmpty() ? null : GlossaryUse.APPLIED_OUTCOME, now);
        } else if (!provider.isEnabled()) {
            job.fail(DISABLED, now);
        }
        return job;
    }

    // Every text translated before, or nothing.
    private Optional<Map<String, String>> cached(
            UUID organizationId,
            String sourceLanguage,
            String targetLanguage,
            Map<String, String> input,
            String context,
            String version
    ) {
        Map<String, String> byHash = cacheRepository
                .findAllByOrganizationIdAndSourceLanguageAndTargetLanguageAndTextHashIn(
                        organizationId,
                        sourceLanguage,
                        targetLanguage,
                        input.values().stream().map(text -> TranslationTexts.textHash(text, context, version))
                                .collect(Collectors.toSet())
                )
                .stream()
                .collect(Collectors.toMap(TranslationCacheEntry::getTextHash, TranslationCacheEntry::getTranslatedText));
        Map<String, String> translated = new LinkedHashMap<>();
        for (Map.Entry<String, String> text : input.entrySet()) {
            String hit = byHash.get(TranslationTexts.textHash(text.getValue(), context, version));
            if (hit == null) {
                return Optional.empty();
            }
            translated.put(text.getKey(), hit);
        }
        return Optional.of(translated);
    }

    // The given texts with each translated one replaced; blank texts stay as they were.
    private static TranslationState merge(Map<String, String> texts, TranslationState state) {
        if (!state.ready()) {
            return state;
        }
        Map<String, String> merged = new LinkedHashMap<>(texts);
        merged.replaceAll((key, value) -> state.texts().getOrDefault(key, value));
        return TranslationState.ready(merged, state.glossary());
    }

    // What a job translates: one change's content, or one release's own texts.
    private record Subject(UUID organizationId, UUID projectId, UUID changeId, UUID releaseId) {
    }

    // PostgreSQL keeps microseconds, so a time must survive a round trip unchanged.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
