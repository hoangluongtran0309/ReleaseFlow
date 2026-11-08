package com.hoangluongtran0309.releaseflow.release;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * A person's decision on a suggestion to fold a tracker change into a code change of the same
 * release (ADR-0041), recorded once per tracker change and release.
 */
@Entity
@Table(name = "release_fold_decisions")
@IdClass(ReleaseFoldDecision.Key.class)
class ReleaseFoldDecision {

    enum Evidence {
        LINKED_KEY, CONFIRMED_DUPLICATE
    }

    enum Decision {
        ACCEPTED, DISMISSED
    }

    enum Removal {
        REJECTED, EXCLUDED
    }

    @Id
    @Column(name = "release_id")
    private UUID releaseId;

    @Id
    @Column(name = "tracker_change_id")
    private UUID trackerChangeId;

    @Column(name = "code_change_id", nullable = false)
    private UUID codeChangeId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Evidence evidence;

    @Column(name = "issue_key", nullable = false, length = 100)
    private String issueKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Decision decision;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Removal removal;

    @Column(length = 500)
    private String reason;

    @Column(name = "decided_by", nullable = false)
    private UUID decidedBy;

    @Column(name = "decider_name", nullable = false, length = 120)
    private String deciderName;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected ReleaseFoldDecision() {
    }

    ReleaseFoldDecision(
            UUID releaseId,
            FoldingSuggestion suggestion,
            UUID organizationId,
            UUID projectId,
            Decision decision,
            Removal removal,
            String reason,
            UUID decidedBy,
            String deciderName,
            Instant decidedAt
    ) {
        this.releaseId = releaseId;
        this.trackerChangeId = suggestion.trackerChangeId();
        this.codeChangeId = suggestion.codeChangeId();
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.evidence = suggestion.evidence();
        this.issueKey = suggestion.issueKey();
        this.decision = decision;
        this.removal = removal;
        this.reason = reason;
        this.decidedBy = decidedBy;
        this.deciderName = deciderName;
        this.decidedAt = decidedAt;
    }

    FoldDecisionView view() {
        return new FoldDecisionView(trackerChangeId, codeChangeId, evidence, issueKey, decision, removal, reason,
                deciderName, decidedAt);
    }

    UUID getTrackerChangeId() {
        return trackerChangeId;
    }

    record Key(UUID releaseId, UUID trackerChangeId) implements Serializable {

        // JPA instantiates id classes reflectively.
        Key() {
            this(null, null);
        }
    }
}
