package com.hoangluongtran0309.releaseflow.changelog;

import com.hoangluongtran0309.releaseflow.account.OrganizationSlugService;
import com.hoangluongtran0309.releaseflow.project.ProjectService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes a published release note into the public changelog, and reads it back for
 * anybody at all. Publishing is idempotent: the same note delivered twice is the entry
 * that is already there, and a different note for the same release, audience, and
 * language is refused rather than allowed to overwrite what people have already read.
 */
@Service
public class PublicChangelogService {

    /** An RSS reader is given the newest entries, not an Organization's whole history. */
    static final int FEED_SIZE = 50;

    private final PublicChangelogEntryRepository entryRepository;
    private final OrganizationSlugService organizations;
    private final ProjectService projects;
    private final PublicChangelogUrls urls;
    private final Clock clock;

    PublicChangelogService(
            PublicChangelogEntryRepository entryRepository,
            OrganizationSlugService organizations,
            ProjectService projects,
            PublicChangelogUrls urls,
            Clock clock
    ) {
        this.entryRepository = entryRepository;
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

    /** The newest entries of the Organization that answers this address. */
    @Transactional(readOnly = true)
    Optional<Feed> findFeed(String slug) {
        return organizations.findBySlug(slug).map(organization -> {
            List<PublicChangelogEntry> newestFirst = entryRepository.findAllByOrganizationIdOrderByPublishedAtDescIdDesc(
                    organization.id(), PageRequest.ofSize(FEED_SIZE));
            return new Feed(
                    organization.name(),
                    organization.slug(),
                    newestFirst.stream().map(this::view).toList(),
                    cards(newestFirst)
            );
        });
    }

    /**
     * One card per release and audience, newest first, holding that note in every language
     * it was published in, in the order they were published. Two languages of one release
     * would otherwise be two cards nobody can tell apart.
     */
    private List<Card> cards(List<PublicChangelogEntry> newestFirst) {
        Map<List<Object>, List<PublicChangelogEntry>> groups = new LinkedHashMap<>();
        for (PublicChangelogEntry entry : newestFirst) {
            groups.computeIfAbsent(List.of(entry.getReleaseId(), entry.getAudienceNameSnapshot()), ignored -> new ArrayList<>())
                    .add(entry);
        }
        return groups.values().stream()
                .map(group -> new Card(group.stream()
                        .sorted(Comparator.comparing(PublicChangelogEntry::getPublishedAt)
                                .thenComparing(PublicChangelogEntry::getId))
                        .map(this::view)
                        .toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    Optional<Entry> findEntry(String slug, UUID entryId) {
        return organizations.findBySlug(slug)
                .flatMap(organization -> entryRepository.findByIdAndOrganizationId(entryId, organization.id())
                        .map(entry -> new Entry(organization.name(), organization.slug(), view(entry))));
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

    private EntryView view(PublicChangelogEntry entry) {
        return new EntryView(
                entry.getId(),
                entry.getProjectNameSnapshot(),
                entry.getReleaseVersionSnapshot(),
                entry.getAudienceNameSnapshot(),
                entry.getLanguage(),
                entry.getContentSnapshot(),
                entry.getPublishedAt(),
                urls.entryUrl(entry.getOrganizationSlugSnapshot(), entry.getId())
        );
    }

    /** An Organization's public changelog, as a reader sees it. */
    record Feed(String organizationName, String slug, List<EntryView> entries, List<Card> cards) {

        Feed {
            entries = List.copyOf(entries);
            cards = List.copyOf(cards);
        }
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

    record Entry(String organizationName, String slug, EntryView entry) {
    }
}
