package com.hoangluongtran0309.releaseflow.audience;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One retirement or return of an audience, with who made it under the name they had then (ADR-0044). */
@Entity
@Table(name = "audience_status_changes")
class AudienceStatusChange {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "audience_id", nullable = false, updatable = false)
    private UUID audienceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Kind kind;

    @Column(name = "changed_by", nullable = false, updatable = false)
    private UUID changedBy;

    @Column(name = "changer_name", nullable = false, length = 120, updatable = false)
    private String changerName;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected AudienceStatusChange() {
    }

    AudienceStatusChange(UUID organizationId, UUID audienceId, Kind kind, UUID changedBy, String changerName, Instant at) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.audienceId = audienceId;
        this.kind = kind;
        this.changedBy = changedBy;
        this.changerName = changerName;
        this.changedAt = at;
    }

    UUID getAudienceId() {
        return audienceId;
    }

    Kind getKind() {
        return kind;
    }

    String getChangerName() {
        return changerName;
    }

    Instant getChangedAt() {
        return changedAt;
    }

    enum Kind {
        RETIRED,
        RESTORED
    }
}
