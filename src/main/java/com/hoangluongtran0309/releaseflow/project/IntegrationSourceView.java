package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceType;
import com.hoangluongtran0309.releaseflow.source.WebhookAuthMode;

import java.time.Instant;
import java.util.UUID;

// The access token itself is write-only; the view only says whether one is set. A
// webhook source has a webhook identity and a path; a polled source has nextPollAt
// instead, and never both. A disconnected source has neither, and says so in its health.
public record IntegrationSourceView(
        UUID id,
        SourceType type,
        String deliveryMechanism,
        String externalProjectKey,
        String externalTeamKey,
        String owner,
        String repository,
        String apiBaseUrl,
        String externalWorkspaceKey,
        String credentialIdentity,
        WebhookAuthMode webhookAuthMode,
        UUID webhookId,
        String webhookPath,
        Instant createdAt,
        Instant lastDeliveryAt,
        boolean accessTokenConfigured,
        Instant accessTokenUpdatedAt,
        ConnectionStatus connectionStatus,
        Instant lastSyncAt,
        String lastErrorCode,
        Instant nextPollAt,
        SourceHealth health
) {

    public boolean connected() {
        return health.connected();
    }

    /**
     * What a page calls the source: its repository or project key, and for a Linear source
     * its team's key, such as OPS, rather than the team's ID. A team whose key has not been
     * read yet is called by its ID.
     */
    public String label() {
        return externalTeamKey == null || externalTeamKey.isBlank() ? externalProjectKey : externalTeamKey;
    }
}
