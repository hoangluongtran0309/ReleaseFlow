package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.github.GitHubApiClient;
import com.hoangluongtran0309.releaseflow.gitlab.GitLabApiClient;
import com.hoangluongtran0309.releaseflow.gitlab.GitLabBaseUrl;
import com.hoangluongtran0309.releaseflow.jira.JiraApiClient;
import com.hoangluongtran0309.releaseflow.jira.JiraSiteUrl;
import com.hoangluongtran0309.releaseflow.linear.LinearApiClient;
import com.hoangluongtran0309.releaseflow.linear.LinearTeam;
import com.hoangluongtran0309.releaseflow.source.ProviderAccess;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import com.hoangluongtran0309.releaseflow.source.WebhookAuthMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A Project's integration sources. Each source gets its own webhook ID and secret,
 * revealed once, and an optional write-only access token. Secrets and tokens are
 * encrypted with authenticated data that binds them to their source.
 */
@Service
public class IntegrationSourceService {

    private static final int WEBHOOK_SECRET_BYTES = 32;
    private static final String EXTERNAL_UNIQUE_CONSTRAINT = "integration_sources_external_unique";
    private static final String EXTERNAL_PROJECT_UNIQUE_CONSTRAINT = "integration_sources_external_project_unique";
    // A reconnected Jira source reads no further back than an import would (ADR-0042).
    private static final Duration RECONNECT_WINDOW = Duration.ofDays(90);
    private static final String STANDARD_WEBHOOK_PREFIX = "whsec_";
    private static final String GITHUB_TOKEN_PURPOSE = "github-access-token";
    private static final String GITLAB_TOKEN_PURPOSE = "gitlab-access-token";
    private static final String LINEAR_TOKEN_PURPOSE = "linear-access-token";
    private static final String JIRA_TOKEN_PURPOSE = "jira-access-token";

    private final ProjectRepository projectRepository;
    private final IntegrationSourceRepository sourceRepository;
    private final CredentialCipher credentialCipher;
    private final GitHubApiClient gitHubApiClient;
    private final GitLabApiClient gitLabApiClient;
    private final GitLabBaseUrl gitLabBaseUrl;
    private final LinearApiClient linearApiClient;
    private final JiraApiClient jiraApiClient;
    private final JiraSiteUrl jiraSiteUrl;
    private final Duration pollInterval;
    private final SourceViews sourceViews;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    IntegrationSourceService(
            ProjectRepository projectRepository,
            IntegrationSourceRepository sourceRepository,
            CredentialCipher credentialCipher,
            GitHubApiClient gitHubApiClient,
            GitLabApiClient gitLabApiClient,
            GitLabBaseUrl gitLabBaseUrl,
            LinearApiClient linearApiClient,
            JiraApiClient jiraApiClient,
            JiraSiteUrl jiraSiteUrl,
            @Value("${releaseflow.jira.poll-interval}") Duration pollInterval,
            SourceViews sourceViews,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this(
                projectRepository,
                sourceRepository,
                credentialCipher,
                gitHubApiClient,
                gitLabApiClient,
                gitLabBaseUrl,
                linearApiClient,
                jiraApiClient,
                jiraSiteUrl,
                pollInterval,
                sourceViews,
                events,
                transactionManager,
                clock,
                new SecureRandom()
        );
    }

    IntegrationSourceService(
            ProjectRepository projectRepository,
            IntegrationSourceRepository sourceRepository,
            CredentialCipher credentialCipher,
            GitHubApiClient gitHubApiClient,
            GitLabApiClient gitLabApiClient,
            GitLabBaseUrl gitLabBaseUrl,
            LinearApiClient linearApiClient,
            JiraApiClient jiraApiClient,
            JiraSiteUrl jiraSiteUrl,
            Duration pollInterval,
            SourceViews sourceViews,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager,
            Clock clock,
            SecureRandom secureRandom
    ) {
        this.projectRepository = projectRepository;
        this.sourceRepository = sourceRepository;
        this.credentialCipher = credentialCipher;
        this.gitHubApiClient = gitHubApiClient;
        this.gitLabApiClient = gitLabApiClient;
        this.gitLabBaseUrl = gitLabBaseUrl;
        this.linearApiClient = linearApiClient;
        this.jiraApiClient = jiraApiClient;
        this.jiraSiteUrl = jiraSiteUrl;
        this.pollInterval = pollInterval;
        this.sourceViews = sourceViews;
        this.events = events;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    /**
     * Connects a repository, project, or team to a Project. A given one belongs to at most
     * one Project of the Organization; a Project may have several sources. Connecting
     * Linear asks Linear to confirm the token first, so this method holds no transaction
     * of its own; each branch writes in one short transaction of its own.
     */
    public IntegrationSourceCreated create(UUID organizationId, UUID projectId, IntegrationSourceRequest request) {
        transactionTemplate.executeWithoutResult(status -> requireProject(organizationId, projectId));
        return switch (request.getType()) {
            case GITHUB -> createGitHub(organizationId, projectId, request);
            case GITLAB -> createGitLab(organizationId, projectId, request);
            case LINEAR -> createLinear(organizationId, projectId, request);
            case JIRA -> createJira(organizationId, projectId, request);
        };
    }

    private IntegrationSourceCreated createGitHub(
            UUID organizationId,
            UUID projectId,
            IntegrationSourceRequest request
    ) {
        String owner = canonicalize(request.getOwner());
        String repository = canonicalize(request.getRepository());
        String projectKey = owner + "/" + repository;
        requireNotHeld(organizationId, projectId, SourceType.GITHUB, projectKey);

        UUID sourceId = UUID.randomUUID();
        UUID webhookId = UUID.randomUUID();
        Instant createdAt = clock.instant();
        String webhookSecret = generateWebhookSecret();
        CredentialCipher.EncryptedSecret encryptedSecret = credentialCipher.encrypt(
                webhookSecret,
                gitHubAuthenticatedData(organizationId, projectId, sourceId, owner, repository)
        );

        save(
                () -> IntegrationSource.gitHub(
                        sourceId,
                        organizationId,
                        projectId,
                        owner,
                        repository,
                        webhookId,
                        encryptedSecret.nonce(),
                        encryptedSecret.ciphertext(),
                        createdAt
                ),
                SourceType.GITHUB,
                projectKey
        );

        return new IntegrationSourceCreated(
                sourceId,
                projectId,
                SourceType.GITHUB,
                projectKey,
                owner,
                repository,
                null,
                null,
                null,
                WebhookAuthMode.GITHUB_HMAC,
                webhookId,
                webhookPath(SourceType.GITHUB, webhookId),
                webhookSecret,
                createdAt
        );
    }

    private IntegrationSourceCreated createGitLab(
            UUID organizationId,
            UUID projectId,
            IntegrationSourceRequest request
    ) {
        // The only address a request may choose, so the deployment's allowlist decides it.
        String apiBaseUrl = gitLabBaseUrl.validated(request.getApiBaseUrl());
        String projectPath = canonicalize(request.getProjectPath());
        requireNotHeld(organizationId, projectId, SourceType.GITLAB, projectPath);

        UUID sourceId = UUID.randomUUID();
        UUID webhookId = UUID.randomUUID();
        Instant createdAt = clock.instant();
        String webhookSecret = generateWebhookSecret();
        CredentialCipher.EncryptedSecret encryptedSecret = credentialCipher.encrypt(
                webhookSecret,
                gitLabAuthenticatedData(organizationId, projectId, sourceId, projectPath)
        );

        save(
                () -> IntegrationSource.gitLab(
                        sourceId,
                        organizationId,
                        projectId,
                        projectPath,
                        apiBaseUrl,
                        request.getWebhookAuthMode(),
                        webhookId,
                        encryptedSecret.nonce(),
                        encryptedSecret.ciphertext(),
                        createdAt
                ),
                SourceType.GITLAB,
                projectPath
        );

        return new IntegrationSourceCreated(
                sourceId,
                projectId,
                SourceType.GITLAB,
                projectPath,
                null,
                null,
                apiBaseUrl,
                null,
                null,
                request.getWebhookAuthMode(),
                webhookId,
                webhookPath(SourceType.GITLAB, webhookId),
                webhookSecret,
                createdAt
        );
    }

    /**
     * Connects a Linear team. Linear mints the webhook secret itself, so the administrator
     * pastes it in, and the token is required because confirming the team is also how
     * ReleaseFlow learns the workspace every delivery is checked against. Linear is asked
     * before anything is written, and never inside a transaction.
     */
    private IntegrationSourceCreated createLinear(
            UUID organizationId,
            UUID projectId,
            IntegrationSourceRequest request
    ) {
        // Linear names a team by a UUID, so the same canonical form as the other types works.
        String teamId = canonicalize(request.getTeamId());
        requireNotHeld(organizationId, projectId, SourceType.LINEAR, teamId);
        // The same request names the team's key, which links its identifiers (ADR-0041).
        LinearTeam team = linearApiClient.team(teamId, request.getApiToken())
                .orElseThrow(() -> new SourceTokenRejectedException(SourceType.LINEAR));
        String workspaceId = team.workspaceId();

        UUID sourceId = UUID.randomUUID();
        UUID webhookId = UUID.randomUUID();
        Instant createdAt = clock.instant();
        String webhookSecret = request.getWebhookSecret();
        CredentialCipher.EncryptedSecret encryptedSecret = credentialCipher.encrypt(
                webhookSecret,
                linearAuthenticatedData(organizationId, projectId, sourceId, teamId)
        );

        save(
                () -> {
                    IntegrationSource source = IntegrationSource.linear(
                            sourceId,
                            organizationId,
                            projectId,
                            teamId,
                            workspaceId,
                            webhookId,
                            encryptedSecret.nonce(),
                            encryptedSecret.ciphertext(),
                            createdAt
                    );
                    source.replaceToken(
                            credentialCipher.encrypt(request.getApiToken(), tokenAuthenticatedData(source)),
                            createdAt
                    );
                    if (!team.key().isBlank()) {
                        source.rememberTeamKey(team.key());
                    }
                    return source;
                },
                SourceType.LINEAR,
                teamId
        );

        // The secret came from Linear, so it is never echoed back.
        return new IntegrationSourceCreated(
                sourceId,
                projectId,
                SourceType.LINEAR,
                teamId,
                null,
                null,
                null,
                workspaceId,
                null,
                WebhookAuthMode.LINEAR_HMAC,
                webhookId,
                webhookPath(SourceType.LINEAR, webhookId),
                null,
                createdAt
        );
    }

    /**
     * Connects a Jira project. Nothing will ever be delivered here, so no webhook identity
     * or secret is made; instead the source is given the schedule ReleaseFlow will read it
     * on, starting one interval from now. Jira is asked to confirm the account and token
     * before anything is written, and never inside a transaction.
     */
    private IntegrationSourceCreated createJira(
            UUID organizationId,
            UUID projectId,
            IntegrationSourceRequest request
    ) {
        // The only Jira address a request may choose, so the Cloud rules decide it.
        String siteUrl = jiraSiteUrl.validated(request.getSiteUrl());
        String projectKey = request.getProjectKey().strip().toUpperCase(Locale.ROOT);
        String accountEmail = request.getAccountEmail();
        requireNotHeld(organizationId, projectId, SourceType.JIRA, projectKey);

        ProviderAccess access = jiraApiClient.checkProjectAccess(
                siteUrl, projectKey, accountEmail, request.getApiToken());
        if (access == ProviderAccess.REJECTED) {
            throw new SourceTokenRejectedException(SourceType.JIRA);
        }
        if (access == ProviderAccess.UNAVAILABLE) {
            throw new SourceUnavailableException(SourceType.JIRA);
        }

        UUID sourceId = UUID.randomUUID();
        Instant createdAt = clock.instant();
        save(
                () -> {
                    IntegrationSource source = IntegrationSource.jira(
                            sourceId,
                            organizationId,
                            projectId,
                            projectKey,
                            siteUrl,
                            accountEmail,
                            createdAt,
                            createdAt.plus(pollInterval)
                    );
                    source.replaceToken(
                            credentialCipher.encrypt(request.getApiToken(), tokenAuthenticatedData(source)),
                            createdAt
                    );
                    return source;
                },
                SourceType.JIRA,
                projectKey
        );

        // Nothing is delivered to a Jira source, so there is no path and no secret.
        return new IntegrationSourceCreated(
                sourceId,
                projectId,
                SourceType.JIRA,
                projectKey,
                null,
                null,
                siteUrl,
                null,
                accountEmail,
                WebhookAuthMode.NONE,
                null,
                null,
                null,
                createdAt
        );
    }

    @Transactional(readOnly = true)
    public List<IntegrationSourceView> list(UUID organizationId, UUID projectId) {
        requireProject(organizationId, projectId);
        return sourceViews.views(
                organizationId,
                sourceRepository.findAllByOrganizationIdAndProjectIdOrderByCreatedAtAscIdAsc(organizationId, projectId)
        );
    }

    @Transactional(readOnly = true)
    public IntegrationSourceView get(UUID organizationId, UUID projectId, UUID sourceId) {
        return sourceViews.view(find(organizationId, projectId, sourceId));
    }

    /**
     * Stores a new access token after the provider confirms it can read the project. The
     * provider call runs between two short transactions, never inside one.
     */
    public void replaceToken(UUID organizationId, UUID projectId, UUID sourceId, SourceTokenRequest request) {
        String token = request.getToken();
        Coordinates stored = transactionTemplate.execute(status -> {
            IntegrationSource source = find(organizationId, projectId, sourceId);
            if (!source.isConnected()) {
                throw SourceLifecycleException.disconnected();
            }
            return coordinates(source);
        });
        // A Jira token belongs to its account, and that person may leave: a new token may come
        // with a new account, confirmed together. No other type names an account.
        String accountEmail = stored.sourceType() == SourceType.JIRA && request.getAccountEmail() != null
                ? request.getAccountEmail()
                : null;
        Coordinates coordinates = accountEmail == null ? stored : stored.withIdentity(accountEmail);

        requireAccess(coordinates, token);

        transactionTemplate.executeWithoutResult(status -> {
            IntegrationSource current = find(organizationId, projectId, sourceId);
            if (!current.isConnected()) {
                throw SourceLifecycleException.disconnected();
            }
            if (accountEmail != null) {
                current.changeAccount(accountEmail);
            }
            current.replaceToken(
                    credentialCipher.encrypt(token, tokenAuthenticatedData(current)),
                    clock.instant()
            );
        });
    }

    /**
     * Disconnects a source (ADR-0042). Its row, its identity, and every change it recorded
     * stay; its webhook identity, secret, token, and schedule are destroyed in this one
     * transaction, and the work queued for it ends in the same commit. Nothing is called at
     * the provider: the hook and the token are the provider's to delete.
     */
    @Transactional
    public IntegrationSourceView disconnect(UUID organizationId, UUID administratorId, UUID projectId, UUID sourceId) {
        IntegrationSource source = find(organizationId, projectId, sourceId);
        if (!source.isConnected()) {
            throw SourceLifecycleException.disconnected();
        }
        source.disconnect(administratorId, now());
        sourceRepository.flush();
        events.publishEvent(new SourceDisconnected(organizationId, projectId, sourceId));
        return sourceViews.view(source);
    }

    /**
     * Brings a disconnected source back under its own ID, so intake keeps recognising every
     * change it recorded. Nothing that identifies the source changes; a delivered source gets
     * a new webhook path and secret, and a polled one resumes from its cursor. Any provider
     * call happens between two short transactions, never inside one.
     */
    public IntegrationSourceCreated reconnect(
            UUID organizationId,
            UUID projectId,
            UUID sourceId,
            SourceReconnectRequest request
    ) {
        Coordinates stored = transactionTemplate.execute(status -> {
            IntegrationSource source = find(organizationId, projectId, sourceId);
            if (source.isConnected()) {
                throw SourceLifecycleException.notDisconnected();
            }
            return coordinates(source);
        });
        if (sourceRepository.existsByOrganizationIdAndSourceTypeAndExternalProjectKeyAndDisconnectedAtIsNull(
                organizationId, stored.sourceType(), stored.externalProjectKey())) {
            // Another Project connected the same key since; it is that Project's now.
            throw new SourceAlreadyConnectedException(stored.sourceType());
        }
        String pastedSecret = request.getWebhookSecret();
        String token = request.getToken();
        Coordinates coordinates = stored;
        switch (stored.sourceType()) {
            case GITHUB -> {
                if (pastedSecret != null) {
                    throw SourceLifecycleException.secretGenerated();
                }
            }
            case GITLAB -> {
                if (pastedSecret != null) {
                    requirePastable(stored.webhookAuthMode());
                    requireSigningToken(pastedSecret);
                }
            }
            case LINEAR -> {
                if (pastedSecret == null) {
                    throw SourceCredentialInvalidException.required("webhookSecret");
                }
                if (token == null) {
                    throw SourceCredentialInvalidException.required("token");
                }
            }
            case JIRA -> {
                if (pastedSecret != null) {
                    throw SourceLifecycleException.noWebhook();
                }
                if (request.getAccountEmail() == null) {
                    throw SourceCredentialInvalidException.required("accountEmail");
                }
                if (token == null) {
                    throw SourceCredentialInvalidException.required("token");
                }
                coordinates = stored.withIdentity(request.getAccountEmail());
            }
        }
        if (token != null) {
            requireAccess(coordinates, token);
        }

        boolean delivered = !stored.sourceType().isPolled();
        String generatedSecret = delivered && pastedSecret == null ? generateWebhookSecret() : null;
        UUID webhookId = delivered ? UUID.randomUUID() : null;
        Instant at = now();
        IntegrationSource reconnected;
        try {
            reconnected = transactionTemplate.execute(status -> {
                IntegrationSource source = find(organizationId, projectId, sourceId);
                if (source.isConnected()) {
                    throw SourceLifecycleException.notDisconnected();
                }
                if (delivered) {
                    String secret = generatedSecret != null ? generatedSecret : pastedSecret;
                    source.reconnectDelivered(
                            webhookId,
                            credentialCipher.encrypt(secret, secretAuthenticatedData(source)),
                            at
                    );
                } else {
                    source.reconnectPolled(at.minus(RECONNECT_WINDOW), at.plus(pollInterval));
                    source.changeAccount(request.getAccountEmail());
                }
                if (token != null) {
                    source.replaceToken(credentialCipher.encrypt(token, tokenAuthenticatedData(source)), at);
                }
                sourceRepository.saveAndFlush(source);
                sourceRepository.resetSignatureFailures(sourceId, organizationId);
                return source;
            });
        } catch (DataIntegrityViolationException exception) {
            throw violates(exception, EXTERNAL_UNIQUE_CONSTRAINT)
                    ? new SourceAlreadyConnectedException(stored.sourceType())
                    : exception;
        }
        return created(reconnected, generatedSecret);
    }

    /**
     * A new webhook secret ReleaseFlow generated, for GitHub and GitLab, shown once. The path
     * stays, and the old secret stops at once: a secret is rotated because someone else may
     * know it (ADR-0042). No provider is called.
     */
    @Transactional
    public IntegrationSourceCreated generateSecret(UUID organizationId, UUID projectId, UUID sourceId) {
        IntegrationSource source = connected(organizationId, projectId, sourceId);
        if (source.getSourceType() == SourceType.LINEAR) {
            throw SourceLifecycleException.secretPasted();
        }
        String secret = generateWebhookSecret();
        storeSecret(source, secret);
        return created(source, secret);
    }

    /**
     * A new webhook secret the provider made: Linear's signing secret, or a signing token
     * GitLab generated for a signed source. It is never echoed back.
     */
    @Transactional
    public void replaceSecret(UUID organizationId, UUID projectId, UUID sourceId, WebhookSecretRequest request) {
        IntegrationSource source = connected(organizationId, projectId, sourceId);
        switch (source.getSourceType()) {
            case GITHUB -> throw SourceLifecycleException.secretGenerated();
            case GITLAB -> {
                requirePastable(source.getWebhookAuthMode());
                requireSigningToken(request.getWebhookSecret());
            }
            case LINEAR -> {
                // Linear's secret is whatever Linear shows; only the next delivery can confirm it.
            }
            case JIRA -> throw SourceLifecycleException.noWebhook();
        }
        storeSecret(source, request.getWebhookSecret());
    }

    private IntegrationSource connected(UUID organizationId, UUID projectId, UUID sourceId) {
        IntegrationSource source = find(organizationId, projectId, sourceId);
        if (!source.isConnected()) {
            throw SourceLifecycleException.disconnected();
        }
        if (source.getSourceType().isPolled()) {
            throw SourceLifecycleException.noWebhook();
        }
        return source;
    }

    private void storeSecret(IntegrationSource source, String secret) {
        source.replaceSecret(credentialCipher.encrypt(secret, secretAuthenticatedData(source)), now());
        sourceRepository.saveAndFlush(source);
        sourceRepository.resetSignatureFailures(source.getId(), source.getOrganizationId());
    }

    // Only a signed GitLab source takes a token GitLab generated; the token header compares
    // ReleaseFlow's own secret verbatim, so that one is only ever generated.
    private static void requirePastable(WebhookAuthMode mode) {
        if (mode != WebhookAuthMode.GITLAB_SIGNING_TOKEN) {
            throw SourceLifecycleException.secretGenerated();
        }
    }

    private static void requireSigningToken(String value) {
        if (value == null || !value.startsWith(STANDARD_WEBHOOK_PREFIX)) {
            throw SourceCredentialInvalidException.signingToken("webhookSecret");
        }
        try {
            byte[] key = Base64.getDecoder().decode(value.substring(STANDARD_WEBHOOK_PREFIX.length()));
            if (key.length < 16) {
                throw SourceCredentialInvalidException.signingToken("webhookSecret");
            }
        } catch (IllegalArgumentException exception) {
            throw SourceCredentialInvalidException.signingToken("webhookSecret");
        }
    }

    private IntegrationSourceCreated created(IntegrationSource source, String revealedSecret) {
        return new IntegrationSourceCreated(
                source.getId(),
                source.getProjectId(),
                source.getSourceType(),
                source.getExternalProjectKey(),
                source.getRepositoryOwner(),
                source.getRepositoryName(),
                source.getApiBaseUrl(),
                source.getExternalWorkspaceKey(),
                source.getCredentialIdentity(),
                source.getWebhookAuthMode(),
                source.getWebhookId(),
                webhookPath(source.getSourceType(), source.getWebhookId()),
                revealedSecret,
                source.getCreatedAt()
        );
    }

    private static Coordinates coordinates(IntegrationSource source) {
        return new Coordinates(
                source.getSourceType(),
                source.getExternalProjectKey(),
                source.getExternalWorkspaceKey(),
                source.getApiBaseUrl(),
                source.getCredentialIdentity(),
                source.getRepositoryOwner(),
                source.getRepositoryName(),
                source.getWebhookAuthMode()
        );
    }

    // PostgreSQL keeps microseconds, so a time compared after a round trip must have no more.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** Asks the provider to confirm a token before anything is written; never inside a transaction. */
    private void requireAccess(Coordinates coordinates, String token) {
        ProviderAccess access = switch (coordinates.sourceType()) {
            case GITHUB -> gitHubApiClient.checkPullRequestAccess(
                    coordinates.repositoryOwner(),
                    coordinates.repositoryName(),
                    token
            );
            case GITLAB -> gitLabApiClient.checkProjectAccess(
                    coordinates.apiBaseUrl(),
                    coordinates.externalProjectKey(),
                    token
            );
            case JIRA -> jiraApiClient.checkProjectAccess(
                    coordinates.apiBaseUrl(),
                    coordinates.externalProjectKey(),
                    coordinates.credentialIdentity(),
                    token
            );
            // A Linear token is only accepted while it still reaches the same workspace.
            case LINEAR -> linearApiClient.workspaceOf(coordinates.externalProjectKey(), token)
                    .filter(workspace -> workspace.equals(coordinates.externalWorkspaceKey()))
                    .map(workspace -> ProviderAccess.GRANTED)
                    .orElse(ProviderAccess.REJECTED);
        };
        if (access == ProviderAccess.REJECTED) {
            throw new SourceTokenRejectedException(coordinates.sourceType());
        }
        if (access == ProviderAccess.UNAVAILABLE) {
            throw new SourceUnavailableException(coordinates.sourceType());
        }
    }

    /**
     * Records how reading a source's history ended. A refused credential marks the
     * source as failing until a new token is saved.
     */
    @Transactional
    public void recordSync(UUID organizationId, UUID sourceId, Instant at, String errorCode, boolean credentialRejected) {
        sourceRepository.findByIdAndOrganizationId(sourceId, organizationId)
                .ifPresent(source -> source.recordSync(at, errorCode, credentialRejected));
    }

    private void save(Supplier<IntegrationSource> source, SourceType sourceType, String externalProjectKey) {
        try {
            transactionTemplate.executeWithoutResult(status -> sourceRepository.saveAndFlush(source.get()));
        } catch (DataIntegrityViolationException exception) {
            if (violates(exception, EXTERNAL_PROJECT_UNIQUE_CONSTRAINT)) {
                throw SourceLifecycleException.reconnectRequired(externalProjectKey);
            }
            throw violates(exception, EXTERNAL_UNIQUE_CONSTRAINT)
                    ? new SourceAlreadyConnectedException(sourceType)
                    : exception;
        }
    }

    /**
     * A key is connected in at most one Project, and a Project that holds it disconnected
     * reconnects it rather than taking a second identity for it, which would let an import
     * record every change again (ADR-0042).
     */
    private void requireNotHeld(UUID organizationId, UUID projectId, SourceType sourceType, String externalProjectKey) {
        sourceRepository.findByOrganizationIdAndProjectIdAndSourceTypeAndExternalProjectKey(
                        organizationId, projectId, sourceType, externalProjectKey)
                .filter(source -> !source.isConnected())
                .ifPresent(source -> {
                    throw SourceLifecycleException.reconnectRequired(externalProjectKey);
                });
        if (sourceRepository.existsByOrganizationIdAndSourceTypeAndExternalProjectKeyAndDisconnectedAtIsNull(
                organizationId,
                sourceType,
                externalProjectKey
        )) {
            throw new SourceAlreadyConnectedException(sourceType);
        }
    }

    private IntegrationSource find(UUID organizationId, UUID projectId, UUID sourceId) {
        requireProject(organizationId, projectId);
        return sourceRepository.findByIdAndOrganizationIdAndProjectId(sourceId, organizationId, projectId)
                .orElseThrow(SourceNotFoundException::new);
    }

    private void requireProject(UUID organizationId, UUID projectId) {
        projectRepository.findByIdAndOrganizationId(projectId, organizationId)
                .orElseThrow(ProjectNotFoundException::new);
    }

    /** A poll that finished: the cursor covers the window it read, and the next one is booked. */
    @Transactional
    public void recordPoll(UUID organizationId, UUID sourceId, Instant cursorAt, Instant at) {
        sourceRepository.findByIdAndOrganizationId(sourceId, organizationId)
                .ifPresent(source -> source.recordPoll(cursorAt, at.plus(pollInterval), at));
    }

    /** A poll that failed: the cursor stays where it was, so the next attempt misses nothing. */
    @Transactional
    public void recordPollFailure(UUID organizationId, UUID sourceId, String errorCode, Instant retryAt, Instant at) {
        sourceRepository.findByIdAndOrganizationId(sourceId, organizationId)
                .ifPresent(source -> source.recordPollFailure(errorCode, retryAt, at));
    }

    static String webhookPath(SourceType sourceType, UUID webhookId) {
        if (webhookId == null) {
            // A polled source, or a disconnected one: nothing is delivered to it.
            return null;
        }
        return switch (sourceType) {
            case GITHUB -> "/webhooks/github/" + webhookId;
            case GITLAB -> "/webhooks/gitlab/" + webhookId;
            case LINEAR -> "/webhooks/linear/" + webhookId;
            // Nothing is delivered to a polled source, so it is reachable at no path.
            case JIRA -> null;
        };
    }

    /** The authenticated data of a source's webhook secret, which binds it to that source. */
    static byte[] secretAuthenticatedData(IntegrationSource source) {
        return switch (source.getSourceType()) {
            case GITHUB -> gitHubAuthenticatedData(
                    source.getOrganizationId(),
                    source.getProjectId(),
                    source.getId(),
                    source.getRepositoryOwner(),
                    source.getRepositoryName()
            );
            case GITLAB -> gitLabAuthenticatedData(
                    source.getOrganizationId(),
                    source.getProjectId(),
                    source.getId(),
                    source.getExternalProjectKey()
            );
            case LINEAR -> linearAuthenticatedData(
                    source.getOrganizationId(),
                    source.getProjectId(),
                    source.getId(),
                    source.getExternalProjectKey()
            );
            case JIRA -> typedAuthenticatedData(
                    source.getOrganizationId(),
                    source.getProjectId(),
                    source.getId(),
                    SourceType.JIRA,
                    source.getExternalProjectKey()
            );
        };
    }

    // Unchanged since sources were GitHub integrations, so existing ciphertexts still decrypt.
    static byte[] gitHubAuthenticatedData(
            UUID organizationId,
            UUID projectId,
            UUID sourceId,
            String owner,
            String repository
    ) {
        return String.join(
                        "\n",
                        organizationId.toString(),
                        projectId.toString(),
                        sourceId.toString(),
                        owner,
                        repository
                )
                .getBytes(StandardCharsets.UTF_8);
    }

    // The type line keeps a GitLab ciphertext from being read as a GitHub one.
    static byte[] gitLabAuthenticatedData(UUID organizationId, UUID projectId, UUID sourceId, String projectPath) {
        return String.join(
                        "\n",
                        organizationId.toString(),
                        projectId.toString(),
                        sourceId.toString(),
                        SourceType.GITLAB.name(),
                        projectPath
                )
                .getBytes(StandardCharsets.UTF_8);
    }

    // The type line keeps a Linear ciphertext from being read as another provider's.
    static byte[] linearAuthenticatedData(UUID organizationId, UUID projectId, UUID sourceId, String teamId) {
        return typedAuthenticatedData(organizationId, projectId, sourceId, SourceType.LINEAR, teamId);
    }

    static byte[] typedAuthenticatedData(
            UUID organizationId,
            UUID projectId,
            UUID sourceId,
            SourceType sourceType,
            String externalProjectKey
    ) {
        return String.join(
                        "\n",
                        organizationId.toString(),
                        projectId.toString(),
                        sourceId.toString(),
                        sourceType.name(),
                        externalProjectKey
                )
                .getBytes(StandardCharsets.UTF_8);
    }

    // The purpose line keeps a token ciphertext from being accepted as a webhook secret.
    static byte[] tokenAuthenticatedData(IntegrationSource source) {
        String purpose = switch (source.getSourceType()) {
            case GITHUB -> GITHUB_TOKEN_PURPOSE;
            case GITLAB -> GITLAB_TOKEN_PURPOSE;
            case LINEAR -> LINEAR_TOKEN_PURPOSE;
            case JIRA -> JIRA_TOKEN_PURPOSE;
        };
        return String.join(
                        "\n",
                        new String(secretAuthenticatedData(source), StandardCharsets.UTF_8),
                        purpose
                )
                .getBytes(StandardCharsets.UTF_8);
    }

    private String generateWebhookSecret() {
        byte[] secret = new byte[WEBHOOK_SECRET_BYTES];
        secureRandom.nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    private static String canonicalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean violates(DataIntegrityViolationException exception, String constraint) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }

    private record Coordinates(
            SourceType sourceType,
            String externalProjectKey,
            String externalWorkspaceKey,
            String apiBaseUrl,
            String credentialIdentity,
            String repositoryOwner,
            String repositoryName,
            WebhookAuthMode webhookAuthMode
    ) {

        Coordinates withIdentity(String identity) {
            return new Coordinates(sourceType, externalProjectKey, externalWorkspaceKey, apiBaseUrl, identity,
                    repositoryOwner, repositoryName, webhookAuthMode);
        }
    }
}
