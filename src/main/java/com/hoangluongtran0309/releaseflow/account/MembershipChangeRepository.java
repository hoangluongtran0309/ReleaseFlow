package com.hoangluongtran0309.releaseflow.account;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface MembershipChangeRepository extends JpaRepository<MembershipChange, UUID> {

    List<MembershipChange> findAllByOrganizationIdOrderByChangedAtDescIdDesc(UUID organizationId, Limit limit);
}
