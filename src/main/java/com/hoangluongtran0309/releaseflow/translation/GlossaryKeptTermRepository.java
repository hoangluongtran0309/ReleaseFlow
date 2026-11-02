package com.hoangluongtran0309.releaseflow.translation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface GlossaryKeptTermRepository extends JpaRepository<GlossaryKeptTerm, UUID> {

    List<GlossaryKeptTerm> findAllByOrganizationIdOrderByTerm(UUID organizationId);

    List<GlossaryKeptTerm> findAllByOrganizationIdAndTermIn(UUID organizationId, Collection<String> terms);

    Optional<GlossaryKeptTerm> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByOrganizationId(UUID organizationId);
}
