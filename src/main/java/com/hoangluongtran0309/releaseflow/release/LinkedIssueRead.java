package com.hoangluongtran0309.releaseflow.release;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The read of a release's linked issues that requesting its review queues (ADR-0041). One row
 * per release: requesting review again queues it again.
 */
@Entity
@Table(name = "linked_issue_reads")
class LinkedIssueRead {

    enum Status {
        PENDING, RUNNING, SUCCEEDED, FAILED
    }

    static final String UNREAD = "linked_issues_unread";

    @Id
    @Column(name = "release_id")
    private UUID releaseId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "last_error", length = 100)
    private String lastError;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected LinkedIssueRead() {
    }

    LinkedIssueRead(UUID releaseId, UUID organizationId, UUID projectId, Instant now) {
        this.releaseId = releaseId;
        this.organizationId = organizationId;
        this.projectId = projectId;
        queue(now);
    }

    void queue(Instant now) {
        this.status = Status.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.claimedAt = null;
        this.lastError = null;
        this.requestedAt = now;
        this.completedAt = null;
    }

    void claim(Instant now) {
        this.status = Status.RUNNING;
        this.attempts++;
        this.claimedAt = now;
    }

    boolean isClaimedAt(Instant claim) {
        return status == Status.RUNNING && claim.equals(claimedAt);
    }

    void retryLater(Instant at) {
        this.status = Status.PENDING;
        this.lastError = UNREAD;
        this.nextAttemptAt = at;
    }

    // Every issue was read, or the attempts ran out and the unread ones keep their recorded status.
    void finish(boolean everyIssueRead, Instant now) {
        this.status = everyIssueRead ? Status.SUCCEEDED : Status.FAILED;
        this.lastError = everyIssueRead ? null : UNREAD;
        this.completedAt = now;
    }

    UUID getReleaseId() {
        return releaseId;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getProjectId() {
        return projectId;
    }

    Status getStatus() {
        return status;
    }

    int getAttempts() {
        return attempts;
    }
}
