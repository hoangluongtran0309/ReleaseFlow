package com.hoangluongtran0309.releaseflow.audience;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AudienceStatusChangeRepository extends JpaRepository<AudienceStatusChange, UUID> {

    List<AudienceStatusChange> findAllByOrganizationIdOrderByChangedAtDescIdDesc(UUID organizationId);
}
