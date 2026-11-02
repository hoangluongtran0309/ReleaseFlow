package com.hoangluongtran0309.releaseflow.translation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface GlossaryTranslationRepository extends JpaRepository<GlossaryTranslation, UUID> {

    List<GlossaryTranslation> findAllByOrganizationIdOrderBySourceLanguageAscTargetLanguageAscSourceTermAsc(UUID organizationId);

    List<GlossaryTranslation> findAllByOrganizationIdAndSourceLanguageAndTargetLanguage(
            UUID organizationId,
            String sourceLanguage,
            String targetLanguage
    );

    boolean existsByOrganizationIdAndSourceLanguageAndTargetLanguageAndSourceTerm(
            UUID organizationId,
            String sourceLanguage,
            String targetLanguage,
            String sourceTerm
    );

    Optional<GlossaryTranslation> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByOrganizationId(UUID organizationId);
}
