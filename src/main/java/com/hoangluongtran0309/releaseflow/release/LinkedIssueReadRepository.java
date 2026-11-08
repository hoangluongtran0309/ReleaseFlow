package com.hoangluongtran0309.releaseflow.release;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface LinkedIssueReadRepository extends JpaRepository<LinkedIssueRead, UUID> {

    Optional<LinkedIssueRead> findByReleaseIdAndOrganizationId(UUID releaseId, UUID organizationId);

    // SKIP LOCKED lets several workers claim different releases.
    @Query(value = """
            SELECT * FROM linked_issue_reads
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<LinkedIssueRead> lockNextDue(@Param("now") Instant now);

    // Reading an issue again is harmless, so a claim left by a stopped worker is released.
    @Modifying
    @Query(value = """
            UPDATE linked_issue_reads SET status = 'PENDING', next_attempt_at = :now
            WHERE status = 'RUNNING' AND claimed_at < :staleBefore
            """, nativeQuery = true)
    int recoverStale(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now);
}
