package com.hoangluongtran0309.releaseflow.release;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ReleaseFoldDecisionRepository extends JpaRepository<ReleaseFoldDecision, ReleaseFoldDecision.Key> {

    List<ReleaseFoldDecision> findAllByReleaseIdAndOrganizationId(UUID releaseId, UUID organizationId);
}
