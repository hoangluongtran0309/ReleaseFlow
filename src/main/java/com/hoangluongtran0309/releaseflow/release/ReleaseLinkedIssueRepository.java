package com.hoangluongtran0309.releaseflow.release;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ReleaseLinkedIssueRepository extends JpaRepository<ReleaseLinkedIssue, ReleaseLinkedIssue.Key> {

    List<ReleaseLinkedIssue> findAllByReleaseIdAndOrganizationId(UUID releaseId, UUID organizationId);
}
