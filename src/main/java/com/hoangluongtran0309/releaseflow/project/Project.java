package com.hoangluongtran0309.releaseflow.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
class Project {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // What administrators say about the product, for the AI and translation (ADR-0035).
    @Column(nullable = false, length = ProjectContextRequest.LIMIT)
    private String context = "";

    @Column(name = "context_updated_by")
    private UUID contextUpdatedBy;

    @Column(name = "context_updater_name", length = 120)
    private String contextUpdaterName;

    @Column(name = "context_updated_at")
    private Instant contextUpdatedAt;

    protected Project() {
    }

    Project(UUID id, UUID organizationId, String name, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.name = name;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    String getName() {
        return name;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void replaceContext(String value, UUID editor, String name, Instant at) {
        this.context = value;
        this.contextUpdatedBy = editor;
        this.contextUpdaterName = name;
        this.contextUpdatedAt = at;
    }

    String getContext() {
        return context;
    }

    String getContextUpdaterName() {
        return contextUpdaterName;
    }

    Instant getContextUpdatedAt() {
        return contextUpdatedAt;
    }
}
