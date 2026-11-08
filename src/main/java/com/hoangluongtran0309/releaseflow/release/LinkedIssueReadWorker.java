package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.change.ChangeInboxService;
import com.hoangluongtran0309.releaseflow.change.ChangeView;
import com.hoangluongtran0309.releaseflow.jira.JiraApiClient;
import com.hoangluongtran0309.releaseflow.jira.LinkedIssue;
import com.hoangluongtran0309.releaseflow.linear.LinearApiClient;
import com.hoangluongtran0309.releaseflow.project.SourceAccess;
import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reads each linked issue of a release in review again, from its own tracker (ADR-0041). A
 * claim and a result are each written in a short transaction; Jira and Linear are called
 * between them. An issue that cannot be read is tried again with backoff, at most
 * {@value #MAX_ATTEMPTS} attempts in all, and then keeps the status recorded with its change.
 */
@Component
class LinkedIssueReadWorker {

    static final int MAX_ATTEMPTS = 5;
    static final Duration STALE_AFTER = Duration.ofMinutes(10);

    private static final Logger log = LoggerFactory.getLogger(LinkedIssueReadWorker.class);

    private final LinkedIssueReadRepository readRepository;
    private final ReleaseLinkedIssueRepository issueRepository;
    private final ReleaseChangeRepository releaseChangeRepository;
    private final ChangeInboxService changeService;
    private final SourceAccess sourceAccess;
    private final JiraApiClient jiraApiClient;
    private final LinearApiClient linearApiClient;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final boolean enabled;

    LinkedIssueReadWorker(
            LinkedIssueReadRepository readRepository,
            ReleaseLinkedIssueRepository issueRepository,
            ReleaseChangeRepository releaseChangeRepository,
            ChangeInboxService changeService,
            SourceAccess sourceAccess,
            JiraApiClient jiraApiClient,
            LinearApiClient linearApiClient,
            TransactionTemplate transactionTemplate,
            ApplicationEventPublisher events,
            Clock clock,
            @Value("${releaseflow.linked-issues.worker-enabled:true}") boolean enabled
    ) {
        this.readRepository = readRepository;
        this.issueRepository = issueRepository;
        this.releaseChangeRepository = releaseChangeRepository;
        this.changeService = changeService;
        this.sourceAccess = sourceAccess;
        this.jiraApiClient = jiraApiClient;
        this.linearApiClient = linearApiClient;
        this.transactionTemplate = transactionTemplate;
        this.events = events;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "PT2S")
    void processDueReads() {
        if (!enabled) {
            return;
        }
        try {
            while (processOne()) {
                // Keep draining due reads before sleeping again.
            }
        } catch (RuntimeException exception) {
            log.error("Reading linked issues stopped unexpectedly; it resumes on the next tick.", exception);
        }
    }

    /**
     * Works at most one due read.
     *
     * @return whether a read was claimed
     */
    boolean processOne() {
        Instant now = now();
        transactionTemplate.executeWithoutResult(status -> {
            int recovered = readRepository.recoverStale(now.minus(STALE_AFTER), now);
            if (recovered > 0) {
                log.warn("Recovered {} linked issue read(s) whose worker stopped responding.", recovered);
            }
        });
        Optional<Claim> claim = transactionTemplate.execute(status -> readRepository.lockNextDue(now).map(read -> {
            read.claim(now);
            return new Claim(read.getReleaseId(), read.getOrganizationId(), read.getProjectId(), read.getAttempts(), now,
                    issues(read));
        }));
        if (claim.isEmpty()) {
            return false;
        }
        work(claim.get());
        return true;
    }

    // Each distinct linked issue of the release's changes, by its tracker and key, that has not
    // been read at this review: a retry, or a fold that added an issue, reads only what is missing.
    private List<LinkedIssue> issues(LinkedIssueRead read) {
        Set<String> alreadyRead = new HashSet<>();
        issueRepository.findAllByReleaseIdAndOrganizationId(read.getReleaseId(), read.getOrganizationId()).stream()
                .filter(row -> row.getReadAt() != null)
                .forEach(row -> alreadyRead.add(IssueStatuses.id(row.getTracker(), row.getIssueKey())));
        List<UUID> ids = releaseChangeRepository
                .findAllByReleaseIdAndOrganizationId(read.getReleaseId(), read.getOrganizationId())
                .stream()
                .map(ReleaseChange::getChangeId)
                .toList();
        Map<String, LinkedIssue> issues = new LinkedHashMap<>();
        for (ChangeView change : changeService.changes(read.getOrganizationId(), read.getProjectId(), ids)) {
            for (LinkedIssue issue : change.linkedIssues()) {
                String id = IssueStatuses.id(issue.trackerOrJira(), issue.key());
                if (!alreadyRead.contains(id)) {
                    issues.putIfAbsent(id, issue);
                }
            }
        }
        return List.copyOf(issues.values());
    }

    private void work(Claim claim) {
        Optional<SourceCredentials> jira = tracker(claim, SourceType.JIRA);
        Optional<SourceCredentials> linear = tracker(claim, SourceType.LINEAR);
        Map<String, LinkedIssue> read = new LinkedHashMap<>();
        int unread = 0;
        for (LinkedIssue issue : claim.issues()) {
            Optional<LinkedIssue> now = LinkedIssue.LINEAR.equals(issue.trackerOrJira())
                    ? linear.flatMap(source -> linearApiClient.linkedIssue(issue.key(), source.externalProjectKey(),
                            source.externalWorkspaceKey(), source.token()))
                    : jira.flatMap(source -> jiraApiClient.issue(source.apiBaseUrl(), issue.key(),
                            source.credentialIdentity(), source.token()));
            if (now.isPresent()) {
                read.put(IssueStatuses.id(issue.trackerOrJira(), issue.key()), now.get());
            } else {
                unread++;
            }
        }
        boolean everyIssueRead = unread == 0;
        int unreadCount = unread;
        transactionTemplate.executeWithoutResult(status -> readRepository.findById(claim.releaseId())
                .filter(job -> job.isClaimedAt(claim.claimedAt()))
                .ifPresent(job -> {
                    Instant at = now();
                    record(claim, read, at);
                    if (!everyIssueRead && claim.attempt() < MAX_ATTEMPTS) {
                        job.retryLater(at.plus(backoff(claim.attempt())));
                        log.info("Could not read {} linked issue(s) of release {}; trying again after attempt {}.",
                                unreadCount, claim.releaseId(), claim.attempt());
                    } else {
                        job.finish(everyIssueRead, at);
                        if (!everyIssueRead) {
                            log.warn("{} linked issue(s) of release {} could not be read and keep their recorded status.",
                                    unreadCount, claim.releaseId());
                        }
                    }
                    readRepository.flush();
                    events.publishEvent(new LinkedIssuesRead(claim.organizationId(), claim.releaseId()));
                }));
    }

    private void record(Claim claim, Map<String, LinkedIssue> read, Instant at) {
        Map<String, ReleaseLinkedIssue> rows = new LinkedHashMap<>();
        issueRepository.findAllByReleaseIdAndOrganizationId(claim.releaseId(), claim.organizationId())
                .forEach(row -> rows.put(IssueStatuses.id(row.getTracker(), row.getIssueKey()), row));
        read.forEach((id, issue) -> {
            ReleaseLinkedIssue row = rows.computeIfAbsent(id, missing -> new ReleaseLinkedIssue(claim.releaseId(),
                    claim.organizationId(), claim.projectId(), issue.trackerOrJira(), issue.key().toUpperCase(Locale.ROOT)));
            row.read(issue.status(), issue.statusCategory(), at);
            issueRepository.save(row);
        });
        issueRepository.flush();
    }

    private Optional<SourceCredentials> tracker(Claim claim, SourceType type) {
        return sourceAccess.findByType(claim.organizationId(), claim.projectId(), type)
                .filter(source -> source.accessToken().isPresent());
    }

    // Microseconds, as PostgreSQL stores them, so a claim compares equal when read back.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    // 2, 4, 8 … seconds, capped at five minutes.
    static Duration backoff(int attempt) {
        return Duration.ofSeconds(Math.min(300, 1L << Math.min(8, attempt)));
    }

    private record Claim(UUID releaseId, UUID organizationId, UUID projectId, int attempt, Instant claimedAt,
                         List<LinkedIssue> issues) {
    }
}
