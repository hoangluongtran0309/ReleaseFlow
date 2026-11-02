package com.hoangluongtran0309.releaseflow.translation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The Organization's one glossary in DeepL, once created. A sync locks this row, so one
 * sync per Organization runs at a time.
 */
@Entity
@Table(name = "translation_glossaries")
class TranslationGlossary {

    @Id
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "deepl_glossary_id", length = 100)
    private String deeplGlossaryId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TranslationGlossary() {
    }

    String getDeeplGlossaryId() {
        return deeplGlossaryId;
    }

    void created(String deeplGlossaryId) {
        this.deeplGlossaryId = deeplGlossaryId;
    }
}
