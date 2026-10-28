package com.hoangluongtran0309.releaseflow.translation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface GlossarySyncRepository extends JpaRepository<GlossarySync, GlossarySync.Key> {

    List<GlossarySync> findAllByKeyOrganizationId(UUID organizationId);

    // Creates the row if it is missing, so concurrent approvals never collide on inserting it.
    @Modifying
    @Query(value = """
            INSERT INTO glossary_syncs (organization_id, source_language, target_language, wanted_version, status,
                                        attempts, next_attempt_at, updated_at)
            VALUES (:organizationId, :sourceLanguage, :targetLanguage, :version, 'PENDING', 0, :now, :now)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfMissing(
            @Param("organizationId") UUID organizationId,
            @Param("sourceLanguage") String sourceLanguage,
            @Param("targetLanguage") String targetLanguage,
            @Param("version") String version,
            @Param("now") Instant now
    );

    @Query(value = """
            SELECT * FROM glossary_syncs
            WHERE organization_id = :organizationId AND source_language = :sourceLanguage
              AND target_language = :targetLanguage
            FOR UPDATE
            """, nativeQuery = true)
    Optional<GlossarySync> lock(
            @Param("organizationId") UUID organizationId,
            @Param("sourceLanguage") String sourceLanguage,
            @Param("targetLanguage") String targetLanguage
    );

    // SKIP LOCKED lets several workers claim different syncs; an Organization with a sync
    // under way waits for it, since all its pairs live in one DeepL glossary.
    @Query(value = """
            SELECT * FROM glossary_syncs s
            WHERE s.status = 'PENDING' AND s.next_attempt_at <= :now
              AND NOT EXISTS (SELECT 1 FROM glossary_syncs r
                              WHERE r.organization_id = s.organization_id AND r.status = 'RUNNING')
            ORDER BY s.next_attempt_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<GlossarySync> lockNextDue(@Param("now") Instant now);

    @Query(value = """
            SELECT count(*) FROM glossary_syncs WHERE organization_id = :organizationId AND status = 'RUNNING'
            """, nativeQuery = true)
    long countRunning(@Param("organizationId") UUID organizationId);

    // Sending a dictionary again is harmless, so a claim left by a stopped worker is released.
    @Modifying
    @Query(value = """
            UPDATE glossary_syncs SET status = 'PENDING', next_attempt_at = :now
            WHERE status = 'RUNNING' AND claimed_at < :staleBefore
            """, nativeQuery = true)
    int recoverStale(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now);
}
