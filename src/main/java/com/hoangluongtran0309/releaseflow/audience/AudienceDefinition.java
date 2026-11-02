package com.hoangluongtran0309.releaseflow.audience;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One kind of reader an Organization writes release notes for. The code never changes
 * after creation, because stored narratives are keyed by it.
 */
@Entity
@Table(name = "audience_definitions")
class AudienceDefinition {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 64, updatable = false)
    private String code;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "communication_intent", nullable = false, length = 1000)
    private String communicationIntent;

    @Column(name = "template_body", nullable = false, columnDefinition = "text")
    private String templateBody;

    // Rendered once per note after its changes; blank adds nothing (ADR-0037, V34).
    @Column(name = "footer_template_body", nullable = false, columnDefinition = "text")
    private String footerTemplateBody;

    // Set at registration, or when an audience whose code has a shipped preset is reset to it.
    @Column(nullable = false)
    private boolean preset;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AudienceDefinition() {
    }

    AudienceDefinition(
            UUID id,
            UUID organizationId,
            String code,
            String displayName,
            String communicationIntent,
            String templateBody,
            String footerTemplateBody,
            boolean preset,
            Instant createdAt
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.code = code;
        this.displayName = displayName;
        this.communicationIntent = communicationIntent;
        this.templateBody = templateBody;
        this.footerTemplateBody = footerTemplateBody;
        this.preset = preset;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    void update(String displayName, String communicationIntent, String templateBody, String footerTemplateBody, Instant at) {
        this.displayName = displayName;
        this.communicationIntent = communicationIntent;
        this.templateBody = templateBody;
        this.footerTemplateBody = footerTemplateBody;
        this.updatedAt = at;
    }

    /** Takes a shipped preset's values; from then on the audience follows that preset. */
    void resetTo(AudiencePresets.Preset shipped, Instant at) {
        update(shipped.displayName(), shipped.communicationIntent(), shipped.templateBody(), shipped.footerBody(), at);
        this.preset = true;
    }

    AudienceView view(Map<String, String> templateVariants, Map<String, String> footerVariants) {
        return new AudienceView(id, code, displayName, communicationIntent, templateBody, footerTemplateBody, preset,
                createdAt, updatedAt, templateVariants, footerVariants);
    }

    UUID getId() {
        return id;
    }

    String getCode() {
        return code;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    String getTemplateBody() {
        return templateBody;
    }

    String getFooterTemplateBody() {
        return footerTemplateBody;
    }

    String getCommunicationIntent() {
        return communicationIntent;
    }

    boolean isPreset() {
        return preset;
    }
}
