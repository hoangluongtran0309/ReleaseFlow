package com.hoangluongtran0309.releaseflow.translation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A term an Organization keeps as written in every language it translates into. */
@Entity
@Table(name = "glossary_kept_terms")
class GlossaryKeptTerm {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 100, updatable = false)
    private String term;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "creator_name", nullable = false, length = 120, updatable = false)
    private String creatorName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GlossaryKeptTerm() {
    }

    GlossaryKeptTerm(UUID organizationId, String term, UUID createdBy, String creatorName, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.term = term;
        this.createdBy = createdBy;
        this.creatorName = creatorName;
        this.createdAt = createdAt;
    }

    String getTerm() {
        return term;
    }

    KeptTermView view() {
        return new KeptTermView(id, term, creatorName, createdAt);
    }
}
