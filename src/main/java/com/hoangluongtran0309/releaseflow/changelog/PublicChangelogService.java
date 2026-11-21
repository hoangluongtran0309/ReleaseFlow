package com.hoangluongtran0309.releaseflow.changelog;

import com.hoangluongtran0309.releaseflow.account.OrganizationSlugService;
import com.hoangluongtran0309.releaseflow.project.ProjectService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Writes a published release note into the public changelog, and reads it back for
 * anybody at all. Publishing is idempotent: the same note delivered twice is the entry
 * that is already there, and a different note for the same release, audience, and
 * language is refused rather than allowed to overwrite what people have already read.
 *
 * <p>A correction is a revision beside the entry, never a change to it (ADR-0046, part
 * 4). The entry's address reads the latest revision; an earlier one keeps an address of
 * its own; and the feed adds an item for each correction beside the one it already has.
 */
@Service
public class PublicChangelogService {

    /** An RSS reader is given the newest entries, not an Organization's whole history. */
    static final int FEED_SIZE = 50;

    private final PublicChangelogEntryRepository entryRepository;
    private final PublicChangelogEntryRevisionRepository revisionRepository;
    private final OrganizationSlugService organizations;
    private final ProjectService projects;
    private final PublicChangelogUrls urls;
    private final Clock clock;

    PublicChangelogService(
            PublicChangelogEntryRepository entryRepository,
            PublicChangelogEntryRevisionRepository revisionRepository,
            OrganizationSlugService organizations,
            ProjectService projects,
            PublicChangelogUrls urls,
            Clock clock
    ) {
        this.entryRepository = entryRepository;
        this.revisionRepository = revisionRepository;
        this.organizations = organizations;
        this.projects = projects;
        this.urls = urls;
        this.clock = clock;
    }

    /**
     * Makes one note public. The Organization's name and address, and the Project's
     * name, are copied in as they read now; nothing later changes them.
     */
    @Transactional
    public Publication publish(Publication.Request request) {
        Optional<PublicChangelogEntry> existing = existing(request);
        if (existing.isPresent()) {
            return answer(existing.get(), request);
        }
        OrganizationSlugService.PublicOrganization organization =
                organizations.publicOrganization(request.organizationId());
        PublicChangelogEntry entry = new PublicChangelogEntry(
                UUID.randomUUID(),
                request.organizationId(),
                request.projectId(),
                request.releaseId(),
                request.actionRunId(),
                organization.slug(),
                organization.name(),
                projects.get(request.organizationId(), request.projectId()).name(),
                request.releaseVersion(),
                request.audienceName(),
                request.language(),
                request.content(),
                clock.instant().truncatedTo(ChronoUnit.MICROS)
        );
        try {
            entryRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException raced) {
            // Another worker published the same note first; theirs is the entry.
            return answer(existing(request).orElseThrow(() -> raced), request);
        }
        return new Publication(Publication.Outcome.PUBLISHED, urls.entryUrl(organization.slug(), entry.getId()));
    }

    /**
     * The newest entries of the Organization that answers this address, and the newest
     * items of its feed: each entry as it was published, and each correction beside it.
     */
    @Transactional(readOnly = true)
    Optional<Feed> findFeed(String slug) {
        return organizations.findBySlug(slug).map(organization -> {
            List<PublicChangelogEntry> newestFirst = entryRepository.findAllByOrganizationIdOrderByPublishedAtDescIdDesc(
                    organization.id(), PageRequest.ofSize(FEED_SIZE));
            Set<UUID> corrected = newestFirst.isEmpty()
                    ? Set.of()
                    : revisionRepository.findCorrectedEntryIds(
                            organization.id(), newestFirst.stream().map(PublicChangelogEntry::getId).toList());
            return new Feed(
                    organization.name(),
                    organization.slug(),
                    items(organization.id(), newestFirst),
                    cards(newestFirst, corrected)
            );
        });
    }

    /**
     * Entries and corrections, newest first, at most {@link #FEED_SIZE} of them. Without a
     * correction this is the entries alone, in the order the feed always listed them.
     */
    private List<FeedItem> items(UUID organizationId, List<PublicChangelogEntry> newestEntries) {
        List<PublicChangelogEntryRevision> newestCorrections = revisionRepository
                .findAllByOrganizationIdOrderByCorrectedAtDescIdDesc(organizationId, PageRequest.ofSize(FEED_SIZE));
        Map<UUID, PublicChangelogEntry> corrected = entryRepository
                .findAllById(newestCorrections.stream().map(PublicChangelogEntryRevision::getEntryId).distinct().toList())
                .stream()
                .filter(entry -> entry.getOrganizationId().equals(organizationId))
                .collect(Collectors.toMap(PublicChangelogEntry::getId, Function.identity()));
        List<FeedItem> items = new ArrayList<>(newestEntries.size() + newestCorrections.size());
        int entry = 0;
        int correction = 0;
        while (items.size() < FEED_SIZE && (entry < newestEntries.size() || correction < newestCorrections.size())) {
            PublicChangelogEntryRevision nextCorrection =
                    correction < newestCorrections.size() ? newestCorrections.get(correction) : null;
            PublicChangelogEntry nextEntry = entry < newestEntries.size() ? newestEntries.get(entry) : null;
            if (nextCorrection != null
                    && (nextEntry == null || nextCorrection.getCorrectedAt().isAfter(nextEntry.getPublishedAt()))) {
                PublicChangelogEntry of = corrected.get(nextCorrection.getEntryId());
                items.add(new FeedItem(view(of, true), correction(of, nextCorrection)));
                correction++;
            } else {
                items.add(new FeedItem(view(nextEntry, false), null));
                entry++;
            }
        }
        return items;
    }

    /**
     * One card per release and audience, newest first, holding that note in every language
     * it was published in, in the order they were published. Two languages of one release
     * would otherwise be two cards nobody can tell apart.
     */
    private List<Card> cards(List<PublicChangelogEntry> newestFirst, Set<UUID> corrected) {
        Map<List<Object>, List<PublicChangelogEntry>> groups = new LinkedHashMap<>();
        for (PublicChangelogEntry entry : newestFirst) {
            groups.computeIfAbsent(List.of(entry.getReleaseId(), entry.getAudienceNameSnapshot()), ignored -> new ArrayList<>())
                    .add(entry);
        }
        return groups.values().stream()
                .map(group -> new Card(group.stream()
                        .sorted(Comparator.comparing(PublicChangelogEntry::getPublishedAt)
                                .thenComparing(PublicChangelogEntry::getId))
                        .map(entry -> view(entry, corrected.contains(entry.getId())))
                        .toList()))
                .toList();
    }

    /** An entry at its own address: its latest revision, and a link to each earlier one. */
    @Transactional(readOnly = true)
    Optional<Entry> findEntry(String slug, UUID entryId) {
        return organizations.findBySlug(slug)
                .flatMap(organization -> entryRepository.findByIdAndOrganizationId(entryId, organization.id())
                        .map(entry -> {
                            List<PublicChangelogEntryRevision> revisions = revisions(entry);
                            if (revisions.isEmpty()) {
                                return new Entry(organization.name(), organization.slug(), view(entry, false), 1,
                                        null, List.of());
                            }
                            PublicChangelogEntryRevision latest = revisions.getLast();
                            List<RevisionLink> earlier = new ArrayList<>();
                            earlier.add(new RevisionLink(1, entry.getPublishedAt(), revisionUrl(entry, 1)));
                            for (PublicChangelogEntryRevision revision : revisions.subList(0, revisions.size() - 1)) {
                                earlier.add(new RevisionLink(revision.getRevision(), revision.getCorrectedAt(),
                                        revisionUrl(entry, revision.getRevision())));
                            }
                            return new Entry(
                                    organization.name(),
                                    organization.slug(),
                                    view(entry, true).withContent(latest.getContentSnapshot()),
                                    latest.getRevision(),
                                    correction(entry, latest),
                                    earlier
                            );
                        }));
    }

    /**
     * A revision some later one replaced. The latest has no number of its own to be read
     * at, because what it says about its successor would change: it is read at the entry's
     * address, and any number that is not an earlier revision is no answer at all.
     */
    @Transactional(readOnly = true)
    Optional<EarlierRevision> findEarlierRevision(String slug, UUID entryId, int number) {
        return organizations.findBySlug(slug)
                .flatMap(organization -> entryRepository.findByIdAndOrganizationId(entryId, organization.id())
                        .flatMap(entry -> {
                            Map<Integer, PublicChangelogEntryRevision> revisions = revisions(entry).stream()
                                    .collect(Collectors.toMap(PublicChangelogEntryRevision::getRevision,
                                            Function.identity()));
                            PublicChangelogEntryRevision replacement = revisions.get(number + 1);
                            if (number < 1 || replacement == null) {
                                return Optional.empty();
                            }
                            PublicChangelogEntryRevision revision = revisions.get(number);
                            EntryView view = view(entry, true);
                            return Optional.of(new EarlierRevision(
                                    organization.name(),
                                    organization.slug(),
                                    revision == null ? view : view.withContent(revision.getContentSnapshot()),
                                    number,
                                    revision == null ? null : correction(entry, revision),
                                    replacement.getRevision(),
                                    replacement.getCorrectedAt(),
                                    revisionUrl(entry, number)
                            ));
                        }));
    }

    private List<PublicChangelogEntryRevision> revisions(PublicChangelogEntry entry) {
        return revisionRepository.findAllByEntryIdAndOrganizationIdOrderByRevisionAsc(
                entry.getId(), entry.getOrganizationId());
    }

    private Correction correction(PublicChangelogEntry entry, PublicChangelogEntryRevision revision) {
        return new Correction(
                revision.getId(),
                revision.getRevision(),
                revision.getCorrectedAt(),
                CorrectionLine.of(entry.getLanguage(), revision.getCorrectedAt(), revision.getReason()),
                revision.getContentSnapshot()
        );
    }

    private String revisionUrl(PublicChangelogEntry entry, int number) {
        return urls.revisionUrl(entry.getOrganizationSlugSnapshot(), entry.getId(), number);
    }

    private Optional<PublicChangelogEntry> existing(Publication.Request request) {
        return entryRepository.findByActionRunId(request.actionRunId())
                .or(() -> entryRepository.findByOrganizationIdAndReleaseIdAndAudienceNameSnapshotAndLanguage(
                        request.organizationId(),
                        request.releaseId(),
                        request.audienceName(),
                        request.language()
                ));
    }

    private Publication answer(PublicChangelogEntry entry, Publication.Request request) {
        if (!entry.says(request.releaseVersion(), request.content())) {
            return new Publication(Publication.Outcome.CONFLICT, null);
        }
        return new Publication(
                Publication.Outcome.PUBLISHED,
                urls.entryUrl(entry.getOrganizationSlugSnapshot(), entry.getId())
        );
    }

    private EntryView view(PublicChangelogEntry entry, boolean corrected) {
        return new EntryView(
                entry.getId(),
                entry.getProjectNameSnapshot(),
                entry.getReleaseVersionSnapshot(),
                entry.getAudienceNameSnapshot(),
                entry.getLanguage(),
                entry.getContentSnapshot(),
                entry.getPublishedAt(),
                urls.entryUrl(entry.getOrganizationSlugSnapshot(), entry.getId()),
                corrected
        );
    }

    /** An Organization's public changelog, as a reader sees it. */
    record Feed(String organizationName, String slug, List<FeedItem> items, List<Card> cards) {

        Feed {
            items = List.copyOf(items);
            cards = List.copyOf(cards);
        }
    }

    /** An entry as it was published, or, with a correction, the item that announces it. */
    record FeedItem(EntryView entry, Correction correction) {
    }

    /** One release for one audience; the first language published is the one it opens on. */
    record Card(List<EntryView> languages) {

        Card {
            languages = List.copyOf(languages);
        }

        public EntryView first() {
            return languages.getFirst();
        }
    }

    /**
     * An entry as its address shows it: {@code entry} holds the latest revision's text,
     * and {@code correction} is null until there is one.
     */
    record Entry(
            String organizationName,
            String slug,
            EntryView entry,
            int revision,
            Correction correction,
            List<RevisionLink> earlierRevisions
    ) {

        Entry {
            earlierRevisions = List.copyOf(earlierRevisions);
        }
    }

    /** An earlier revision, with the one that replaced it; revision 1 has no correction. */
    record EarlierRevision(
            String organizationName,
            String slug,
            EntryView entry,
            int revision,
            Correction correction,
            int replacedBy,
            Instant replacedAt,
            String url
    ) {
    }

    /** One correction of an entry; {@code line} is in the entry's language. */
    record Correction(UUID id, int revision, Instant correctedAt, String line, String content) {
    }

    record RevisionLink(int revision, Instant at, String url) {
    }
}
