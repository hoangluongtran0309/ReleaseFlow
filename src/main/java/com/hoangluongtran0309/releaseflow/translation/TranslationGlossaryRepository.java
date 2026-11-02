package com.hoangluongtran0309.releaseflow.translation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface TranslationGlossaryRepository extends JpaRepository<TranslationGlossary, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO translation_glossaries (organization_id, created_at) VALUES (:organizationId, :now)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfMissing(@Param("organizationId") UUID organizationId, @Param("now") Instant now);

    @Query(value = "SELECT * FROM translation_glossaries WHERE organization_id = :organizationId FOR UPDATE",
            nativeQuery = true)
    Optional<TranslationGlossary> lock(@Param("organizationId") UUID organizationId);
}
