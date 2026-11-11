package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The only way other capabilities obtain a source's coordinates and decrypted access
 * token, always scoped by Organization and Project. A disconnected source lends nothing:
 * every lookup here answers as if it were not there (ADR-0042).
 */
@Component
public class SourceAccess {

    private static final Logger log = LoggerFactory.getLogger(SourceAccess.class);

    private final IntegrationSourceRepository sourceRepository;
    private final CredentialCipher credentialCipher;

    SourceAccess(IntegrationSourceRepository sourceRepository, CredentialCipher credentialCipher) {
        this.sourceRepository = sourceRepository;
        this.credentialCipher = credentialCipher;
    }

    @Transactional(readOnly = true)
    public Optional<SourceCredentials> find(UUID organizationId, UUID projectId, UUID sourceId) {
        if (sourceId == null) {
            return Optional.empty();
        }
        return sourceRepository.findByIdAndOrganizationIdAndProjectId(sourceId, organizationId, projectId)
                .filter(IntegrationSource::isConnected)
                .map(this::credentials);
    }

    /**
     * The Project's source of one type, for the one case where a change is enriched from a
     * source that is not its own. A Project may hold more than one; the oldest wins, so the
     * answer is at least stable.
     */
    @Transactional(readOnly = true)
    public Optional<SourceCredentials> findByType(UUID organizationId, UUID projectId, SourceType sourceType) {
        return sourceRepository
                .findAllByOrganizationIdAndProjectIdAndSourceTypeOrderByCreatedAtAscIdAsc(
                        organizationId, projectId, sourceType)
                .stream()
                .filter(IntegrationSource::isConnected)
                .findFirst()
                .map(this::credentials);
    }

    /**
     * The Project's only source of one type. A Project with none, or with more than
     * one, has no single answer, so automation refuses rather than guessing which
     * repository a release belongs in.
     */
    @Transactional(readOnly = true)
    public Optional<SourceCredentials> findSole(UUID organizationId, UUID projectId, SourceType sourceType) {
        List<IntegrationSource> sources = connected(organizationId, projectId, sourceType);
        return sources.size() == 1 ? Optional.of(credentials(sources.getFirst())) : Optional.empty();
    }

    /**
     * The repository key a GitHub Release run records: the Project's one connected source of
     * the type, or, when none is connected, its one disconnected source, so a run created
     * while it is disconnected still says which repository it was for and can be retried
     * once it is reconnected.
     */
    @Transactional(readOnly = true)
    public Optional<String> soleExternalKey(UUID organizationId, UUID projectId, SourceType sourceType) {
        List<IntegrationSource> all = sourceRepository
                .findAllByOrganizationIdAndProjectIdAndSourceTypeOrderByCreatedAtAscIdAsc(
                        organizationId, projectId, sourceType);
        List<IntegrationSource> connected = all.stream().filter(IntegrationSource::isConnected).toList();
        List<IntegrationSource> candidates = connected.isEmpty() ? all : connected;
        return candidates.size() == 1 ? Optional.of(candidates.getFirst().getExternalProjectKey()) : Optional.empty();
    }

    /** Whether the Project has exactly one connected source of the type, without decrypting anything. */
    @Transactional(readOnly = true)
    public boolean hasSole(UUID organizationId, UUID projectId, SourceType sourceType) {
        return connected(organizationId, projectId, sourceType).size() == 1;
    }

    /** Whether the Project holds this key only as a disconnected source. */
    @Transactional(readOnly = true)
    public boolean isDisconnected(UUID organizationId, UUID projectId, SourceType sourceType, String externalKey) {
        return sourceRepository.findByOrganizationIdAndProjectIdAndSourceTypeAndExternalProjectKey(
                        organizationId, projectId, sourceType, externalKey)
                .map(source -> !source.isConnected())
                .orElse(false);
    }

    private List<IntegrationSource> connected(UUID organizationId, UUID projectId, SourceType sourceType) {
        return sourceRepository
                .findAllByOrganizationIdAndProjectIdAndSourceTypeOrderByCreatedAtAscIdAsc(
                        organizationId, projectId, sourceType)
                .stream()
                .filter(IntegrationSource::isConnected)
                .toList();
    }

    /** The polled sources whose next poll is due, oldest cursor first. */
    @Transactional(readOnly = true)
    public List<PolledSource> findDuePolls(SourceType sourceType, Instant now) {
        return sourceRepository.findAllBySourceTypeAndNextPollAtLessThanEqual(sourceType, now).stream()
                .filter(IntegrationSource::isConnected)
                .map(source -> new PolledSource(
                        source.getId(),
                        source.getOrganizationId(),
                        source.getProjectId(),
                        source.getPollCursorAt() == null ? source.getCreatedAt() : source.getPollCursorAt()
                ))
                .toList();
    }

    /**
     * Keeps the key of a Linear team connected before ADR-0041, read at its first
     * enrichment. It is written in a short transaction of its own, after Linear answered.
     */
    @Transactional
    public void rememberTeamKey(UUID organizationId, UUID projectId, UUID sourceId, String teamKey) {
        sourceRepository.findByIdAndOrganizationIdAndProjectId(sourceId, organizationId, projectId)
                .filter(source -> source.getSourceType() == SourceType.LINEAR)
                .ifPresent(source -> source.rememberTeamKey(teamKey));
    }

    private SourceCredentials credentials(IntegrationSource source) {
        return new SourceCredentials(
                source.getId(),
                source.getSourceType(),
                source.getExternalProjectKey(),
                source.getExternalWorkspaceKey(),
                source.getApiBaseUrl(),
                source.getCredentialIdentity(),
                source.getRepositoryOwner(),
                source.getRepositoryName(),
                source.getExternalTeamKey(),
                decryptToken(source)
        );
    }

    // A token that no longer decrypts is treated as missing, which forces review.
    private String decryptToken(IntegrationSource source) {
        if (!source.hasAccessToken()) {
            return null;
        }
        try {
            return credentialCipher.decrypt(
                    source.getToken(),
                    IntegrationSourceService.tokenAuthenticatedData(source)
            );
        } catch (IllegalStateException exception) {
            log.warn("Could not decrypt the access token of source {}.", source.getId());
            return null;
        }
    }
}
