package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface IntegrationSourceRepository extends JpaRepository<IntegrationSource, UUID> {

    List<IntegrationSource> findAllByOrganizationIdAndProjectIdInOrderByCreatedAtAscIdAsc(
            UUID organizationId,
            Collection<UUID> projectIds
    );

    List<IntegrationSource> findAllByOrganizationIdAndProjectIdOrderByCreatedAtAscIdAsc(UUID organizationId, UUID projectId);

    Optional<IntegrationSource> findByIdAndOrganizationIdAndProjectId(UUID id, UUID organizationId, UUID projectId);

    Optional<IntegrationSource> findByIdAndOrganizationId(UUID id, UUID organizationId);

    // The only lookup without an Organization ID: a webhook has no principal, so the
    // untrusted path routes to one source whose secret must then verify the delivery
    // before its tenant is trusted (ADR-0002).
    Optional<IntegrationSource> findByWebhookId(UUID webhookId);

    // A change is enriched from the Project's tracker, which is found by type, not by ID.
    List<IntegrationSource> findAllByOrganizationIdAndProjectIdAndSourceTypeOrderByCreatedAtAscIdAsc(
            UUID organizationId,
            UUID projectId,
            SourceType sourceType
    );

    /** The polled sources whose schedule has come round. */
    List<IntegrationSource> findAllBySourceTypeAndNextPollAtLessThanEqual(SourceType sourceType, Instant now);

    /** Whether the key is connected anywhere in the Organization; a disconnected source holds it nowhere. */
    boolean existsByOrganizationIdAndSourceTypeAndExternalProjectKeyAndDisconnectedAtIsNull(
            UUID organizationId,
            SourceType sourceType,
            String externalProjectKey
    );

    /** The Project's own source for a key, connected or not; at most one exists. */
    Optional<IntegrationSource> findByOrganizationIdAndProjectIdAndSourceTypeAndExternalProjectKey(
            UUID organizationId,
            UUID projectId,
            SourceType sourceType,
            String externalProjectKey
    );

    /** A new secret is refused from here on, so the count starts again. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE integration_sources
            SET signature_failure_count = 0, last_signature_failure_at = NULL
            WHERE id = :id AND organization_id = :organizationId
            """, nativeQuery = true)
    int resetSignatureFailures(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    /**
     * Adds refused deliveries to a connected source's count, only while the secret they were
     * refused under is still the source's secret.
     */
    @Modifying
    @Query(value = """
            UPDATE integration_sources
            SET signature_failure_count = signature_failure_count + :failures,
                last_signature_failure_at = GREATEST(COALESCE(last_signature_failure_at, :failedAt), :failedAt)
            WHERE id = :id
              AND disconnected_at IS NULL
              AND secret_updated_at IS NOT DISTINCT FROM CAST(:secretUpdatedAt AS TIMESTAMP WITH TIME ZONE)
            """, nativeQuery = true)
    int addSignatureFailures(
            @Param("id") UUID id,
            @Param("secretUpdatedAt") Instant secretUpdatedAt,
            @Param("failures") long failures,
            @Param("failedAt") Instant failedAt
    );

    /**
     * The gaps between the changes each source was delivered by webhook since {@code since}:
     * how many there are and their 90th percentile, in seconds. A source with fewer than two
     * delivered changes has no row.
     */
    @Query(value = """
            SELECT gaps.source_id AS sourceId,
                   COUNT(*) AS gapCount,
                   PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY gaps.gap_seconds) AS usualGapSeconds
            FROM (
                SELECT c.source_id,
                       EXTRACT(EPOCH FROM c.received_at - LAG(c.received_at)
                               OVER (PARTITION BY c.source_id ORDER BY c.received_at)) AS gap_seconds
                FROM changes c
                WHERE c.organization_id = :organizationId
                  AND c.source_id IN (:sourceIds)
                  AND c.origin = 'WEBHOOK'
                  AND c.received_at >= :since
            ) gaps
            WHERE gaps.gap_seconds IS NOT NULL
            GROUP BY gaps.source_id
            """, nativeQuery = true)
    List<DeliveryRhythm> deliveryRhythms(
            @Param("organizationId") UUID organizationId,
            @Param("sourceIds") Collection<UUID> sourceIds,
            @Param("since") Instant since
    );

    interface DeliveryRhythm {
        UUID getSourceId();

        long getGapCount();

        double getUsualGapSeconds();
    }

    @Modifying
    @Query("""
            update IntegrationSource source
            set source.lastDeliveryAt = :deliveredAt
            where source.id = :id
              and source.organizationId = :organizationId
              and (source.lastDeliveryAt is null or source.lastDeliveryAt < :deliveredAt)
            """)
    int recordDelivery(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId,
            @Param("deliveredAt") Instant deliveredAt
    );
}
