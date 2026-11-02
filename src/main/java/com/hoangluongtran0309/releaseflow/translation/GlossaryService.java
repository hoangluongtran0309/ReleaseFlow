package com.hoangluongtran0309.releaseflow.translation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An Organization's glossary (ADR-0036): terms kept as written in every language, and
 * fixed translations for one language pair. Administrators edit it, members read it, and
 * it reaches DeepL as one dictionary per language pair, whose version is the hash of what
 * is sent. Nothing is seeded: the suggestions are only offered.
 */
@Service
public class GlossaryService {

    public static final int MAX_ENTRIES = 500;
    public static final int MAX_TERM_LENGTH = 100;

    /** Terms common in software release notes, offered and never added on their own. */
    static final List<String> SUGGESTIONS = List.of(
            "API", "CLI", "console", "image", "container image", "digest", "pull request", "merge request",
            "commit", "branch", "tag", "repository", "endpoint", "exit code", "webhook", "access token",
            "storage", "flash message"
    );

    private final GlossaryKeptTermRepository keptTermRepository;
    private final GlossaryTranslationRepository translationRepository;
    private final GlossarySyncRepository syncRepository;
    private final Clock clock;

    GlossaryService(
            GlossaryKeptTermRepository keptTermRepository,
            GlossaryTranslationRepository translationRepository,
            GlossarySyncRepository syncRepository,
            Clock clock
    ) {
        this.keptTermRepository = keptTermRepository;
        this.translationRepository = translationRepository;
        this.syncRepository = syncRepository;
        this.clock = clock;
    }

    /**
     * The glossary, and where each pair from the source language to a target language
     * stands with DeepL.
     */
    @Transactional(readOnly = true)
    public GlossaryView view(UUID organizationId, String sourceLanguage, Collection<String> targetLanguages) {
        Map<GlossarySync.Key, GlossarySync> syncs = syncRepository.findAllByKeyOrganizationId(organizationId).stream()
                .collect(Collectors.toMap(GlossarySync::getKey, Function.identity()));
        String source = TranslationTexts.primary(sourceLanguage);
        List<GlossaryPairView> pairs = new ArrayList<>();
        for (String target : new LinkedHashSet<>(targetLanguages.stream().map(TranslationTexts::primary).toList())) {
            if (target.equals(source)) {
                continue;
            }
            Dictionary dictionary = dictionary(organizationId, source, target);
            GlossarySync sync = syncs.get(new GlossarySync.Key(organizationId, source, target));
            pairs.add(pair(source, target, dictionary, sync));
        }
        return new GlossaryView(
                keptTermRepository.findAllByOrganizationIdOrderByTerm(organizationId).stream()
                        .map(GlossaryKeptTerm::view).toList(),
                translationRepository.findAllByOrganizationIdOrderBySourceLanguageAscTargetLanguageAscSourceTermAsc(
                        organizationId).stream().map(GlossaryTranslation::view).toList(),
                pairs,
                MAX_ENTRIES
        );
    }

    /** The suggested kept terms that are not in the glossary yet. */
    @Transactional(readOnly = true)
    public List<String> suggestions(UUID organizationId) {
        Set<String> kept = keptTermRepository.findAllByOrganizationIdAndTermIn(organizationId, SUGGESTIONS).stream()
                .map(GlossaryKeptTerm::getTerm)
                .collect(Collectors.toSet());
        return SUGGESTIONS.stream().filter(term -> !kept.contains(term)).toList();
    }

    /**
     * Adds terms to keep as written, all of them or none.
     *
     * @throws GlossaryConflictException when a term is already kept, or the glossary would
     *                                   pass {@value #MAX_ENTRIES} entries
     */
    @Transactional
    public List<KeptTermView> addKeptTerms(UUID organizationId, UUID administratorId, String administratorName,
                                           KeptTermsRequest request) {
        List<String> terms = List.copyOf(new LinkedHashSet<>(request.getTerms()));
        keptTermRepository.findAllByOrganizationIdAndTermIn(organizationId, terms).stream()
                .findFirst()
                .ifPresent(taken -> {
                    throw GlossaryConflictException.taken(taken.getTerm());
                });
        requireRoom(organizationId, terms.size());
        Instant now = clock.instant();
        return keptTermRepository.saveAllAndFlush(terms.stream()
                        .map(term -> new GlossaryKeptTerm(organizationId, term, administratorId, administratorName, now))
                        .toList())
                .stream()
                .map(GlossaryKeptTerm::view)
                .toList();
    }

    @Transactional
    public void deleteKeptTerm(UUID organizationId, UUID termId) {
        keptTermRepository.delete(keptTermRepository.findByIdAndOrganizationId(termId, organizationId)
                .orElseThrow(GlossaryTermNotFoundException::new));
    }

    /**
     * Adds a fixed translation for one language pair.
     *
     * @throws GlossaryConflictException when the pair already translates the term, or the
     *                                   glossary is full
     */
    @Transactional
    public FixedTranslationView addTranslation(UUID organizationId, UUID administratorId, String administratorName,
                                               FixedTranslationRequest request) {
        if (translationRepository.existsByOrganizationIdAndSourceLanguageAndTargetLanguageAndSourceTerm(
                organizationId, request.getSourceLanguage(), request.getTargetLanguage(), request.getSourceTerm())) {
            throw GlossaryConflictException.taken(request.getSourceTerm());
        }
        requireRoom(organizationId, 1);
        return translationRepository.saveAndFlush(new GlossaryTranslation(
                organizationId,
                request.getSourceLanguage(),
                request.getTargetLanguage(),
                request.getSourceTerm(),
                request.getTargetTerm(),
                administratorId,
                administratorName,
                clock.instant()
        )).view();
    }

    @Transactional
    public void deleteTranslation(UUID organizationId, UUID translationId) {
        translationRepository.delete(translationRepository.findByIdAndOrganizationId(translationId, organizationId)
                .orElseThrow(GlossaryTermNotFoundException::new));
    }

    /**
     * What DeepL is sent for one language pair: every kept term mapped to itself, and the
     * pair's fixed translations, which win over a kept term with the same text. Sorted, so
     * the same glossary always gives the same dictionary and the same version.
     */
    @Transactional(readOnly = true)
    Dictionary dictionary(UUID organizationId, String sourceLanguage, String targetLanguage) {
        Map<String, String> entries = new TreeMap<>();
        keptTermRepository.findAllByOrganizationIdOrderByTerm(organizationId)
                .forEach(term -> entries.put(term.getTerm(), term.getTerm()));
        translationRepository.findAllByOrganizationIdAndSourceLanguageAndTargetLanguage(
                        organizationId, TranslationTexts.primary(sourceLanguage), TranslationTexts.primary(targetLanguage))
                .forEach(translation -> entries.put(translation.getSourceTerm(), translation.getTargetTerm()));
        return Dictionary.of(entries);
    }

    /** Terms a correct translation may hold as written: the kept terms and the pair's fixed targets. */
    @Transactional(readOnly = true)
    List<String> verbatimTerms(UUID organizationId, String sourceLanguage, String targetLanguage) {
        return List.copyOf(dictionary(organizationId, sourceLanguage, targetLanguage).entries().values());
    }

    private void requireRoom(UUID organizationId, int adding) {
        long entries = keptTermRepository.countByOrganizationId(organizationId)
                + translationRepository.countByOrganizationId(organizationId);
        if (entries + adding > MAX_ENTRIES) {
            throw GlossaryConflictException.limit(MAX_ENTRIES);
        }
    }

    private static GlossaryPairView pair(String source, String target, Dictionary dictionary, GlossarySync sync) {
        int entries = dictionary.entries().size();
        if (dictionary.version().isEmpty()) {
            return new GlossaryPairView(source, target, 0, "empty", null, null);
        }
        if (sync == null) {
            return new GlossaryPairView(source, target, entries, "not_synced", null, null);
        }
        String status = switch (sync.getStatus()) {
            case PENDING, RUNNING -> "pending";
            case SYNCED -> dictionary.version().equals(sync.getSyncedVersion()) ? "synced" : "outdated";
            case UNSUPPORTED -> "unsupported";
            case FAILED -> "failed";
        };
        return new GlossaryPairView(source, target, entries, status,
                sync.getStatus() == GlossarySync.Status.FAILED ? sync.getLastError() : null, sync.getUpdatedAt());
    }

    /** One pair's entries, the TSV DeepL is sent, and its version: the SHA-256 of that TSV, or empty for none. */
    record Dictionary(Map<String, String> entries, String tsv, String version) {

        static Dictionary of(Map<String, String> sorted) {
            String tsv = sorted.entrySet().stream()
                    .map(entry -> entry.getKey() + "\t" + entry.getValue())
                    .collect(Collectors.joining("\n"));
            return new Dictionary(Map.copyOf(sorted), tsv, sorted.isEmpty() ? "" : TranslationTexts.sha256Hex(tsv));
        }
    }
}
