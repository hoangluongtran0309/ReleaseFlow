package com.hoangluongtran0309.releaseflow.automation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AutomationActionRunRepository extends JpaRepository<AutomationActionRun, UUID> {

    List<AutomationActionRun> findAllByRunIdOrderByPositionAsc(UUID runId);

    List<AutomationActionRun> findAllByRunIdInOrderByRunIdAscPositionAsc(Collection<UUID> runIds);

    /**
     * For each rule action that ever delivered a note of this release, its latest delivery that
     * succeeded: what that destination holds now (ADR-0046).
     */
    @Query(value = """
            SELECT DISTINCT ON (action_run.rule_action_id) action_run.*
            FROM automation_action_runs action_run
            JOIN automation_runs run ON run.id = action_run.run_id
            WHERE run.release_id = :releaseId
              AND run.organization_id = :organizationId
              AND action_run.status = 'SUCCEEDED'
              AND action_run.note_content_snapshot IS NOT NULL
            ORDER BY action_run.rule_action_id, action_run.completed_at DESC, action_run.id
            """, nativeQuery = true)
    List<AutomationActionRun> findLatestDeliveries(
            @Param("organizationId") UUID organizationId,
            @Param("releaseId") UUID releaseId
    );

    /**
     * The next Action nobody else holds, from a Run that is neither finished nor being
     * cancelled, and only once every Action before it has succeeded. Locking the Action
     * row alone keeps two workers off the same delivery without blocking the Run.
     */
    @Query(value = """
            SELECT action_run.* FROM automation_action_runs action_run
            JOIN automation_runs run ON run.id = action_run.run_id
            WHERE action_run.status = 'PENDING'
              AND run.status IN ('PENDING', 'RUNNING')
              AND run.cancellation_requested = FALSE
              AND NOT EXISTS (
                  SELECT 1 FROM automation_action_runs previous
                  WHERE previous.run_id = action_run.run_id
                    AND previous.position < action_run.position
                    AND previous.status NOT IN ('SUCCEEDED', 'SKIPPED')
              )
            ORDER BY run.created_at, action_run.position
            LIMIT 1
            FOR UPDATE OF action_run SKIP LOCKED
            """, nativeQuery = true)
    Optional<AutomationActionRun> lockNextPending();

    /**
     * Deliveries cannot be repeated safely, so an Action whose worker stopped is never
     * released back to PENDING: it becomes UNKNOWN and waits for a person.
     */
    @Query(value = """
            SELECT id FROM automation_action_runs
            WHERE status = 'RUNNING' AND claimed_at < :staleBefore
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<UUID> findStaleRunningIds(@Param("staleBefore") Instant staleBefore);
}
