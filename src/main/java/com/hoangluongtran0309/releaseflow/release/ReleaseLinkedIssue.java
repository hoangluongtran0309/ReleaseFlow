package com.hoangluongtran0309.releaseflow.release;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * One linked issue of a release as of its review, and a person's acceptance of a status a
 * note may then show (ADR-0041). A row may hold an acceptance of a status that was never
 * read, the one recorded with the change.
 */
@Entity
@Table(name = "release_linked_issues")
@IdClass(ReleaseLinkedIssue.Key.class)
class ReleaseLinkedIssue {

    @Id
    @Column(name = "release_id")
    private UUID releaseId;

    @Id
    @Column(length = 10)
    private String tracker;

    @Id
    @Column(name = "issue_key", length = 100)
    private String issueKey;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(length = 200)
    private String status;

    @Column(name = "status_category", length = 10)
    private String statusCategory;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "accepted_status", length = 200)
    private String acceptedStatus;

    @Column(name = "accepted_by")
    private UUID acceptedBy;

    @Column(name = "accepter_name", length = 120)
    private String accepterName;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    protected ReleaseLinkedIssue() {
    }

    ReleaseLinkedIssue(UUID releaseId, UUID organizationId, UUID projectId, String tracker, String issueKey) {
        this.releaseId = releaseId;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.tracker = tracker;
        this.issueKey = issueKey;
    }

    void read(String status, String statusCategory, Instant at) {
        this.status = status == null ? "" : truncate(status);
        this.statusCategory = statusCategory;
        this.readAt = at;
    }

    // A new review reads every issue again; until then, nothing is known as of it.
    void forgetRead() {
        this.status = null;
        this.statusCategory = null;
        this.readAt = null;
    }

    void accept(String status, UUID userId, String name, Instant at) {
        this.acceptedStatus = truncate(status);
        this.acceptedBy = userId;
        this.accepterName = name;
        this.acceptedAt = at;
    }

    private static String truncate(String text) {
        return text.length() > 200 ? text.substring(0, 200) : text;
    }

    UUID getReleaseId() {
        return releaseId;
    }

    String getTracker() {
        return tracker;
    }

    String getIssueKey() {
        return issueKey;
    }

    String getStatus() {
        return status;
    }

    String getStatusCategory() {
        return statusCategory;
    }

    Instant getReadAt() {
        return readAt;
    }

    String getAcceptedStatus() {
        return acceptedStatus;
    }

    String getAccepterName() {
        return accepterName;
    }

    Instant getAcceptedAt() {
        return acceptedAt;
    }

    record Key(UUID releaseId, String tracker, String issueKey) implements Serializable {

        // JPA instantiates id classes reflectively.
        Key() {
            this(null, null, null);
        }
    }
}
