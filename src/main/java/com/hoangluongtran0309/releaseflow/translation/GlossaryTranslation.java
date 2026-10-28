package com.hoangluongtran0309.releaseflow.translation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A term that always becomes another between two languages, named by their primary
 * languages (ADR-0036). Changing one means deleting it and adding another.
 */
@Entity
@Table(name = "glossary_translations")
class GlossaryTranslation {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "source_language", nullable = false, length = 3, updatable = false)
    private String sourceLanguage;

    @Column(name = "target_language", nullable = false, length = 3, updatable = false)
    private String targetLanguage;

    @Column(name = "source_term", nullable = false, length = 100, updatable = false)
    private String sourceTerm;

    @Column(name = "target_term", nullable = false, length = 100, updatable = false)
    private String targetTerm;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "creator_name", nullable = false, length = 120, updatable = false)
    private String creatorName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GlossaryTranslation() {
    }

    GlossaryTranslation(
            UUID organizationId,
            String sourceLanguage,
            String targetLanguage,
            String sourceTerm,
            String targetTerm,
            UUID createdBy,
            String creatorName,
            Instant createdAt
    ) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.sourceTerm = sourceTerm;
        this.targetTerm = targetTerm;
        this.createdBy = createdBy;
        this.creatorName = creatorName;
        this.createdAt = createdAt;
    }

    String getSourceTerm() {
        return sourceTerm;
    }

    String getTargetTerm() {
        return targetTerm;
    }

    FixedTranslationView view() {
        return new FixedTranslationView(id, sourceLanguage, targetLanguage, sourceTerm, targetTerm, creatorName, createdAt);
    }
}
