package com.hoangluongtran0309.releaseflow.translation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TranslationJobRepository extends JpaRepository<TranslationJob, UUID> {

    Optional<TranslationJob> findByChangeIdAndTargetLanguageAndInputHash(UUID changeId, String targetLanguage, String inputHash);

    Optional<TranslationJob> findByReleaseIdAndTargetLanguageAndInputHash(UUID releaseId, String targetLanguage, String inputHash);

    List<TranslationJob> findAllByOrganizationIdAndChangeIdInAndStatus(
            UUID organizationId,
            Collection<UUID> changeIds,
            TranslationJob.Status status
    );

    // SKIP LOCKED lets several workers claim different jobs without waiting on each other.
    // A job waits while the glossary version it asked for is being synced (ADR-0036); the
    // sync is bounded, so the wait is too.
    @Query(value = """
            SELECT * FROM translation_jobs j
            WHERE j.status = 'PENDING' AND j.next_attempt_at <= :now
              AND NOT EXISTS (
                  SELECT 1 FROM glossary_syncs s
                  WHERE s.organization_id = j.organization_id
                    AND s.source_language = lower(split_part(j.source_language, '-', 1))
                    AND s.target_language = lower(split_part(j.target_language, '-', 1))
                    AND s.wanted_version = j.glossary_version
                    AND s.status IN ('PENDING', 'RUNNING'))
            ORDER BY j.next_attempt_at, j.created_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<TranslationJob> lockNextDue(@Param("now") Instant now);

    // Translating again is harmless, so a claim left by a stopped worker is simply released.
    @Modifying
    @Query(value = """
            UPDATE translation_jobs SET status = 'PENDING', next_attempt_at = :now
            WHERE status = 'RUNNING' AND claimed_at < :staleBefore
            """, nativeQuery = true)
    int recoverStale(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now);
}
