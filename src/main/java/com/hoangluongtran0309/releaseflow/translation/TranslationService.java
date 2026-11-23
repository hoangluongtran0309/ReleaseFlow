package com.hoangluongtran0309.releaseflow.translation;

import com.hoangluongtran0309.releaseflow.project.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
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

    /**
     * A change's texts as a published note of its release was written in the target language
     * (ADR-0046, amended 2026-11-20). The job read is the change's latest that succeeded into
     * the language by {@code publishedAt}; each text is looked up by itself in the cache, under
     * that job's context and glossary version, then under none, and repaired. Nothing is
     * written and no provider is called.
     */
    @Transactional(readOnly = true)
    public PublishedTranslation published(
            UUID organizationId,
            UUID changeId,
            String sourceLanguage,
            String targetLanguage,
            Map<String, String> texts,
            Instant publishedAt
    ) {
        return published(organizationId, sourceLanguage, targetLanguage, texts, () -> jobRepository
                .findFirstByOrganizationIdAndChangeIdAndTargetLanguageAndStatusAndCompletedAtLessThanEqualOrderByCompletedAtDesc(
                        organizationId, changeId, targetLanguage, TranslationJob.Status.SUCCEEDED, publishedAt));
    }

    /** {@link #published} for a release's own texts, its summary. */
    @Transactional(readOnly = true)
    public PublishedTranslation publishedForRelease(
            UUID organizationId,
            UUID releaseId,
            String sourceLanguage,
            String targetLanguage,
            Map<String, String> texts,
            Instant publishedAt
    ) {
        return published(organizationId, sourceLanguage, targetLanguage, texts, () -> jobRepository
                .findFirstByOrganizationIdAndReleaseIdAndTargetLanguageAndStatusAndCompletedAtLessThanEqualOrderByCompletedAtDesc(
                        organizationId, releaseId, targetLanguage, TranslationJob.Status.SUCCEEDED, publishedAt));
    }

    private PublishedTranslation published(
            UUID organizationId,
            String sourceLanguage,
            String targetLanguage,
            Map<String, String> texts,
            Supplier<Optional<TranslationJob>> jobOf
    ) {
        Map<String, String> input = TranslationTexts.nonBlank(texts);
        if (input.isEmpty() || TranslationTexts.sameLanguage(sourceLanguage, targetLanguage)) {
            return new PublishedTranslation(texts, Set.of(), 0, GlossaryUse.NONE);
        }
        Optional<TranslationJob> job = jobOf.get();
        Map<String, String> answers = job.map(found -> cachedAnswers(organizationId, sourceLanguage, targetLanguage,
                input.values(), found.getContext(), found.getGlossaryVersion())).orElse(Map.of());
        Map<String, String> merged = new LinkedHashMap<>(texts);
        Set<String> missing = new LinkedHashSet<>();
        int missingCharacters = 0;
        for (Map.Entry<String, String> text : input.entrySet()) {
            String answer = answers.get(text.getValue());
            if (answer == null) {
                missing.add(text.getKey());
                missingCharacters += text.getValue().codePointCount(0, text.getValue().length());
            } else {
                merged.put(text.getKey(), SourceForms.restore(text.getValue(), answer));
            }
        }
        GlossaryUse glossary = job.filter(found -> !answers.isEmpty()).map(TranslationJob::glossaryUse)
                .orElse(GlossaryUse.NONE);
        return new PublishedTranslation(merged, missing, missingCharacters, glossary);
    }

    /**
     * The characters a change's texts would send to the provider if their translation were
     * ensured now (ADR-0047): none when a job for the same input already exists, otherwise the
     * code points of each distinct text not cached under the Project's context and the pair's
     * glossary version as they are now. Nothing is written and no provider is called.
     */
    @Transactional(readOnly = true)
    public int charactersToSend(
            UUID organizationId,
            UUID projectId,
            UUID changeId,
            String sourceLanguage,
            String targetLanguage,
            Map<String, String> texts
    ) {
        return charactersToSend(new Subject(organizationId, projectId, changeId, null), sourceLanguage, targetLanguage,
                texts);
    }

    /** {@link #charactersToSend} for a release's own texts, its summary. */
    @Transactional(readOnly = true)
    public int charactersToSendForRelease(
            UUID organizationId,
            UUID projectId,
            UUID releaseId,
            String sourceLanguage,
            String targetLanguage,
            Map<String, String> texts
    ) {
        return charactersToSend(new Subject(organizationId, projectId, null, releaseId), sourceLanguage, targetLanguage,
                texts);
    }

    private int charactersToSend(Subject subject, String sourceLanguage, String targetLanguage, Map<String, String> texts) {
        Map<String, String> input = TranslationTexts.nonBlank(texts);
        if (!provider.isEnabled() || input.isEmpty() || TranslationTexts.sameLanguage(sourceLanguage, targetLanguage)) {
            return 0;
        }
        String context = projectService.context(subject.organizationId(), subject.projectId());
        String version = glossaryVersion(subject.organizationId(), sourceLanguage, targetLanguage);
        if (find(subject, targetLanguage, TranslationTexts.inputHash(sourceLanguage, input, context, version)).isPresent()) {
            return 0;
        }
        // The worker sends only what the cache lacks under the job's own glossary version.
        Set<String> distinct = new LinkedHashSet<>(input.values());
        Set<String> cached = new HashSet<>();
        cacheRepository.findAllByOrganizationIdAndSourceLanguageAndTargetLanguageAndTextHashIn(
                        subject.organizationId(), sourceLanguage, targetLanguage,
                        distinct.stream().map(text -> TranslationTexts.textHash(text, context, version))
                                .collect(Collectors.toSet()))
                .forEach(entry -> cached.add(entry.getTextHash()));
        int characters = 0;
        for (String text : distinct) {
            if (!cached.contains(TranslationTexts.textHash(text, context, version))) {
                characters += text.codePointCount(0, text.length());
            }
        }
        return characters;
    }

    // Text -> DeepL's answer, as cached under the job's glossary version or, for a text it
    // translated without the glossary, under none.
    private Map<String, String> cachedAnswers(
            UUID organizationId,
            String sourceLanguage,
            String targetLanguage,
            Collection<String> sources,
            String context,
            String version
    ) {
        Map<String, String> withGlossary = new LinkedHashMap<>();
        Map<String, String> withoutGlossary = new LinkedHashMap<>();
        for (String text : sources) {
            withGlossary.put(TranslationTexts.textHash(text, context, version), text);
            withoutGlossary.put(TranslationTexts.textHash(text, context, ""), text);
        }
        Set<String> hashes = new HashSet<>(withGlossary.keySet());
        hashes.addAll(withoutGlossary.keySet());
        Map<String, String> found = new LinkedHashMap<>();
        Map<String, String> fallback = new LinkedHashMap<>();
        cacheRepository.findAllByOrganizationIdAndSourceLanguageAndTargetLanguageAndTextHashIn(
                        organizationId, sourceLanguage, targetLanguage, hashes)
                .forEach(entry -> {
                    if (withGlossary.containsKey(entry.getTextHash())) {
                        found.put(withGlossary.get(entry.getTextHash()), entry.getTranslatedText());
                    }
                    if (withoutGlossary.containsKey(entry.getTextHash())) {
                        fallback.put(withoutGlossary.get(entry.getTextHash()), entry.getTranslatedText());
                    }
                });
        fallback.forEach(found::putIfAbsent);
        return found;
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
