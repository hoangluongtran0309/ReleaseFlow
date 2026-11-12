package com.hoangluongtran0309.releaseflow.release;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ReleaseFoldDecisionRepository extends JpaRepository<ReleaseFoldDecision, ReleaseFoldDecision.Key> {

    /**
     * A release's fold decisions in a fixed order: by when each was decided, then by the
     * tracker change, which identifies a decision within its release. Without an order the
     * database returns them however it reads them, so the same release could list them
     * differently from one request to the next.
     */
    List<ReleaseFoldDecision> findAllByReleaseIdAndOrganizationIdOrderByDecidedAtAscTrackerChangeIdAsc(
            UUID releaseId,
            UUID organizationId
    );
}
