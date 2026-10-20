package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceType;
import com.hoangluongtran0309.releaseflow.source.WebhookAuthMode;

import java.time.Instant;
import java.util.UUID;

public final class IntegrationSourceCreated {

    private final UUID id;
    private final UUID projectId;
    private final SourceType type;
    private final String externalProjectKey;
    private final String owner;
    private final String repository;
    private final String apiBaseUrl;
    private final String externalWorkspaceKey;
    private final String credentialIdentity;
    private final WebhookAuthMode webhookAuthMode;
    private final UUID webhookId;
    private final String webhookPath;
    private final String webhookSecret;
    private final Instant createdAt;

    IntegrationSourceCreated(
            UUID id,
            UUID projectId,
            SourceType type,
            String externalProjectKey,
            String owner,
            String repository,
            String apiBaseUrl,
            String externalWorkspaceKey,
            String credentialIdentity,
            WebhookAuthMode webhookAuthMode,
            UUID webhookId,
            String webhookPath,
            String webhookSecret,
            Instant createdAt
    ) {
        this.id = id;
        this.projectId = projectId;
        this.type = type;
        this.externalProjectKey = externalProjectKey;
        this.owner = owner;
        this.repository = repository;
        this.apiBaseUrl = apiBaseUrl;
        this.externalWorkspaceKey = externalWorkspaceKey;
        this.credentialIdentity = credentialIdentity;
        this.webhookAuthMode = webhookAuthMode;
        this.webhookId = webhookId;
        this.webhookPath = webhookPath;
        this.webhookSecret = webhookSecret;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public SourceType getType() {
        return type;
    }

    public String getExternalProjectKey() {
        return externalProjectKey;
    }

    public String getOwner() {
        return owner;
    }

    public String getRepository() {
        return repository;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public String getExternalWorkspaceKey() {
        return externalWorkspaceKey;
    }

    public String getCredentialIdentity() {
        return credentialIdentity;
    }

    public WebhookAuthMode getWebhookAuthMode() {
        return webhookAuthMode;
    }

    public UUID getWebhookId() {
        return webhookId;
    }

    public String getWebhookPath() {
        return webhookPath;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    /**
     * For a GitLab source that checks signed headers, the secret as GitLab's signing token
     * field takes it; {@code null} otherwise. It is the same key, so it is shown only where
     * the secret is, once.
     */
    public String getSigningToken() {
        return webhookSecret != null && webhookAuthMode == WebhookAuthMode.GITLAB_SIGNING_TOKEN
                ? WebhookSignatures.standardWebhookSecret(webhookSecret)
                : null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return ("IntegrationSourceCreated[id=%s, projectId=%s, type=%s, externalProjectKey=%s, webhookId=%s, "
                + "webhookSecret=[REDACTED], signingToken=[REDACTED]]")
                .formatted(id, projectId, type, externalProjectKey, webhookId);
    }
}
