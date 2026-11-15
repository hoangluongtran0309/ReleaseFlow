package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceType;
import com.hoangluongtran0309.releaseflow.source.WebhookAuthMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Where a Project's changes come from: a GitHub repository, a GitLab project, a Linear
 * team, or a Jira project, with its webhook secret and an access token. A Project may have
 * several sources.
 *
 * <p>Every column a ciphertext is bound to — Organization, Project, ID, type, external key,
 * and GitHub's owner and name — is mapped as not updatable, so no lifecycle step can rewrite
 * one (ADR-0042). A disconnected source keeps its row, and so its changes, but none of its
 * credentials.
 */
@Entity
@Table(name = "integration_sources")
class IntegrationSource {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20, updatable = false)
    private SourceType sourceType;

    @Column(name = "external_project_key", nullable = false, length = 200, updatable = false)
    private String externalProjectKey;

    // Only a GitHub source has these; its ciphertexts are bound to them.
    @Column(name = "repository_owner", length = 39, updatable = false)
    private String repositoryOwner;

    @Column(name = "repository_name", length = 100, updatable = false)
    private String repositoryName;

    // Only a source whose instance the Organization chose has one, so far GitLab.
    @Column(name = "api_base_url", length = 255, updatable = false)
    private String apiBaseUrl;

    // Only a source whose projects live inside a workspace has one, so far Linear.
    @Column(name = "external_workspace_key", length = 100, updatable = false)
    private String externalWorkspaceKey;

    // A Linear team's key, such as OPS, its issue identifiers start with (ADR-0041). Read
    // when the team is connected, or at the first enrichment of a source connected before.
    @Column(name = "external_team_key", length = 100)
    private String externalTeamKey;

    // Only a source whose credential names an account has one, so far Jira. No ciphertext is
    // bound to it, so a new token may come from another account.
    @Column(name = "credential_identity", length = 255)
    private String credentialIdentity;

    // Only a polled source has a schedule; a delivered one is told instead of asking.
    @Column(name = "poll_cursor_at")
    private Instant pollCursorAt;

    @Column(name = "next_poll_at")
    private Instant nextPollAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "webhook_auth_mode", nullable = false, length = 30, updatable = false)
    private WebhookAuthMode webhookAuthMode;

    // A polled source is never delivered to, so it has no identity to be delivered at.
    @Column(name = "webhook_id", unique = true)
    private UUID webhookId;

    @Column(name = "secret_nonce", columnDefinition = "bytea")
    private byte[] secretNonce;

    @Column(name = "secret_ciphertext", columnDefinition = "bytea")
    private byte[] secretCiphertext;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_delivery_at")
    private Instant lastDeliveryAt;

    @Column(name = "token_nonce", columnDefinition = "bytea")
    private byte[] tokenNonce;

    @Column(name = "token_ciphertext", columnDefinition = "bytea")
    private byte[] tokenCiphertext;

    @Column(name = "token_updated_at")
    private Instant tokenUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 10)
    private ConnectionStatus connectionStatus;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "secret_updated_at")
    private Instant secretUpdatedAt;

    @Column(name = "signature_failure_count", nullable = false, insertable = false, updatable = false)
    private long signatureFailureCount;

    @Column(name = "last_signature_failure_at", insertable = false, updatable = false)
    private Instant lastSignatureFailureAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    @Column(name = "disconnected_by")
    private UUID disconnectedBy;

    protected IntegrationSource() {
    }

    private IntegrationSource(
            UUID id,
            UUID organizationId,
            UUID projectId,
            SourceType sourceType,
            String externalProjectKey,
            WebhookAuthMode webhookAuthMode,
            UUID webhookId,
            byte[] secretNonce,
            byte[] secretCiphertext,
            Instant createdAt
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.sourceType = sourceType;
        this.externalProjectKey = externalProjectKey;
        this.webhookAuthMode = webhookAuthMode;
        this.webhookId = webhookId;
        this.secretNonce = secretNonce == null ? null : secretNonce.clone();
        this.secretCiphertext = secretCiphertext == null ? null : secretCiphertext.clone();
        this.createdAt = createdAt;
        this.secretUpdatedAt = secretCiphertext == null ? null : createdAt;
        this.connectionStatus = ConnectionStatus.ACTIVE;
    }

    static IntegrationSource gitHub(
            UUID id,
            UUID organizationId,
            UUID projectId,
            String repositoryOwner,
            String repositoryName,
            UUID webhookId,
            byte[] secretNonce,
            byte[] secretCiphertext,
            Instant createdAt
    ) {
        IntegrationSource source = new IntegrationSource(
                id,
                organizationId,
                projectId,
                SourceType.GITHUB,
                repositoryOwner + "/" + repositoryName,
                WebhookAuthMode.GITHUB_HMAC,
                webhookId,
                secretNonce,
                secretCiphertext,
                createdAt
        );
        source.repositoryOwner = repositoryOwner;
        source.repositoryName = repositoryName;
        return source;
    }

    /**
     * A Jira project. Nothing is delivered here, so there is no webhook identity and no
     * secret; instead the source carries the schedule ReleaseFlow reads it on.
     */
    static IntegrationSource jira(
            UUID id,
            UUID organizationId,
            UUID projectId,
            String projectKey,
            String siteUrl,
            String accountEmail,
            Instant createdAt,
            Instant firstPollAt
    ) {
        IntegrationSource source = new IntegrationSource(
                id,
                organizationId,
                projectId,
                SourceType.JIRA,
                projectKey,
                WebhookAuthMode.NONE,
                null,
                null,
                null,
                createdAt
        );
        source.apiBaseUrl = siteUrl;
        source.credentialIdentity = accountEmail;
        source.pollCursorAt = createdAt;
        source.nextPollAt = firstPollAt;
        return source;
    }

    /** A poll that finished moves the cursor to the window it covered and books the next one. */
    void recordPoll(Instant cursorAt, Instant nextPollAt, Instant at) {
        if (!isConnected()) {
            // A poll that finished after its source was disconnected books nothing.
            return;
        }
        this.pollCursorAt = cursorAt;
        this.nextPollAt = nextPollAt;
        this.lastSyncAt = at;
        this.lastErrorCode = null;
        this.connectionStatus = ConnectionStatus.ACTIVE;
    }

    /** A poll that failed keeps its cursor, so nothing is skipped, and waits before asking again. */
    void recordPollFailure(String errorCode, Instant retryAt, Instant at) {
        if (!isConnected()) {
            return;
        }
        this.lastSyncAt = at;
        this.lastErrorCode = errorCode;
        this.nextPollAt = retryAt;
        this.connectionStatus = ConnectionStatus.ERROR;
    }

    static IntegrationSource linear(
            UUID id,
            UUID organizationId,
            UUID projectId,
            String teamId,
            String workspaceId,
            UUID webhookId,
            byte[] secretNonce,
            byte[] secretCiphertext,
            Instant createdAt
    ) {
        IntegrationSource source = new IntegrationSource(
                id,
                organizationId,
                projectId,
                SourceType.LINEAR,
                teamId,
                WebhookAuthMode.LINEAR_HMAC,
                webhookId,
                secretNonce,
                secretCiphertext,
                createdAt
        );
        source.externalWorkspaceKey = workspaceId;
        return source;
    }

    void rememberTeamKey(String teamKey) {
        if (sourceType != SourceType.LINEAR) {
            throw new IllegalStateException("Only a Linear source has a team key.");
        }
        this.externalTeamKey = teamKey;
    }

    static IntegrationSource gitLab(
            UUID id,
            UUID organizationId,
            UUID projectId,
            String projectPath,
            String apiBaseUrl,
            WebhookAuthMode webhookAuthMode,
            UUID webhookId,
            byte[] secretNonce,
            byte[] secretCiphertext,
            Instant createdAt
    ) {
        IntegrationSource source = new IntegrationSource(
                id,
                organizationId,
                projectId,
                SourceType.GITLAB,
                projectPath,
                webhookAuthMode,
                webhookId,
                secretNonce,
                secretCiphertext,
                createdAt
        );
        source.apiBaseUrl = apiBaseUrl;
        return source;
    }

    UUID getId() {
        return id;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getProjectId() {
        return projectId;
    }

    SourceType getSourceType() {
        return sourceType;
    }

    String getExternalProjectKey() {
        return externalProjectKey;
    }

    String getRepositoryOwner() {
        return repositoryOwner;
    }

    String getRepositoryName() {
        return repositoryName;
    }

    String getApiBaseUrl() {
        return apiBaseUrl;
    }

    String getExternalWorkspaceKey() {
        return externalWorkspaceKey;
    }

    String getExternalTeamKey() {
        return externalTeamKey;
    }

    WebhookAuthMode getWebhookAuthMode() {
        return webhookAuthMode;
    }

    UUID getWebhookId() {
        return webhookId;
    }

    byte[] getSecretNonce() {
        return secretNonce == null ? null : secretNonce.clone();
    }

    byte[] getSecretCiphertext() {
        return secretCiphertext == null ? null : secretCiphertext.clone();
    }

    String getCredentialIdentity() {
        return credentialIdentity;
    }

    Instant getPollCursorAt() {
        return pollCursorAt;
    }

    Instant getNextPollAt() {
        return nextPollAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getLastDeliveryAt() {
        return lastDeliveryAt;
    }

    ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    Instant getLastSyncAt() {
        return lastSyncAt;
    }

    String getLastErrorCode() {
        return lastErrorCode;
    }

    boolean isConnected() {
        return disconnectedAt == null;
    }

    Instant getDisconnectedAt() {
        return disconnectedAt;
    }

    UUID getDisconnectedBy() {
        return disconnectedBy;
    }

    Instant getSecretUpdatedAt() {
        return secretUpdatedAt;
    }

    long getSignatureFailureCount() {
        return signatureFailureCount;
    }

    Instant getLastSignatureFailureAt() {
        return lastSignatureFailureAt;
    }

    /**
     * Destroys what the source could be reached or proven with — its webhook identity, its
     * secret, its token, and its schedule — and keeps everything that identifies it, so its
     * changes keep their source. The poll cursor stays, for a reconnect to resume from.
     */
    void disconnect(UUID administratorId, Instant at) {
        if (!isConnected()) {
            throw new IllegalStateException("The source is already disconnected.");
        }
        this.webhookId = null;
        this.secretNonce = null;
        this.secretCiphertext = null;
        this.tokenNonce = null;
        this.tokenCiphertext = null;
        this.tokenUpdatedAt = null;
        this.nextPollAt = null;
        this.disconnectedAt = at;
        this.disconnectedBy = administratorId;
    }

    /**
     * Brings a delivered source back with a new webhook identity and secret. Nothing that
     * identifies the source changes, so the secret is encrypted under the same authenticated
     * data it always was.
     */
    void reconnectDelivered(UUID newWebhookId, CredentialCipher.EncryptedSecret secret, Instant at) {
        requireDisconnected();
        if (webhookAuthMode == WebhookAuthMode.NONE) {
            throw new IllegalStateException("A polled source has no webhook to reconnect.");
        }
        this.webhookId = newWebhookId;
        reconnected();
        replaceSecret(secret, at);
    }

    /**
     * Brings a polled source back on its schedule. Its cursor stays where its last completed
     * poll left it, but never further back than {@code earliestCursor}.
     */
    void reconnectPolled(Instant earliestCursor, Instant nextPollAt) {
        requireDisconnected();
        if (webhookAuthMode != WebhookAuthMode.NONE) {
            throw new IllegalStateException("A delivered source is not polled.");
        }
        if (pollCursorAt == null || pollCursorAt.isBefore(earliestCursor)) {
            this.pollCursorAt = earliestCursor;
        }
        this.nextPollAt = nextPollAt;
        reconnected();
    }

    private void reconnected() {
        this.disconnectedAt = null;
        this.disconnectedBy = null;
        this.connectionStatus = ConnectionStatus.ACTIVE;
        this.lastErrorCode = null;
    }

    private void requireDisconnected() {
        if (isConnected()) {
            throw new IllegalStateException("The source is connected.");
        }
    }

    /**
     * A new webhook secret under the same authenticated data. The old one stops at once, and
     * the failure count is read from here on as "refused since this secret".
     */
    void replaceSecret(CredentialCipher.EncryptedSecret secret, Instant at) {
        if (!isConnected()) {
            throw new IllegalStateException("A disconnected source has no secret.");
        }
        this.secretNonce = secret.nonce();
        this.secretCiphertext = secret.ciphertext();
        this.secretUpdatedAt = at;
    }

    /** The account a Jira token belongs to; no ciphertext is bound to it. */
    void changeAccount(String accountEmail) {
        if (sourceType != SourceType.JIRA) {
            throw new IllegalStateException("Only a Jira source signs in as an account.");
        }
        this.credentialIdentity = accountEmail;
    }

    // A token the provider accepted puts a source that failed to connect back in order.
    void replaceToken(CredentialCipher.EncryptedSecret token, Instant updatedAt) {
        this.tokenNonce = token.nonce();
        this.tokenCiphertext = token.ciphertext();
        this.tokenUpdatedAt = updatedAt;
        this.connectionStatus = ConnectionStatus.ACTIVE;
        this.lastErrorCode = null;
    }

    /** The outcome of reading the source's history; a refused credential marks the connection as failing. */
    void recordSync(Instant at, String errorCode, boolean credentialRejected) {
        if (!isConnected()) {
            return;
        }
        this.lastSyncAt = at;
        this.lastErrorCode = errorCode;
        if (credentialRejected) {
            this.connectionStatus = ConnectionStatus.ERROR;
        }
    }

    boolean hasAccessToken() {
        return tokenCiphertext != null;
    }

    CredentialCipher.EncryptedSecret getToken() {
        return new CredentialCipher.EncryptedSecret(tokenNonce, tokenCiphertext);
    }

    Instant getTokenUpdatedAt() {
        return tokenUpdatedAt;
    }
}
