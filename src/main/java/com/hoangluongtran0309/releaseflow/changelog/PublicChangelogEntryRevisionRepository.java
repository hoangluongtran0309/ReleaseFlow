package com.hoangluongtran0309.releaseflow.changelog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

interface PublicChangelogEntryRevisionRepository extends JpaRepository<PublicChangelogEntryRevision, UUID> {

    List<PublicChangelogEntryRevision> findAllByEntryIdAndOrganizationIdOrderByRevisionAsc(
            UUID entryId,
            UUID organizationId
    );

    /** The newest corrections of an Organization, as the feed lists them. */
    List<PublicChangelogEntryRevision> findAllByOrganizationIdOrderByCorrectedAtDescIdDesc(
            UUID organizationId,
            Pageable pageable
    );

    @Query("""
            SELECT DISTINCT revision.entryId FROM PublicChangelogEntryRevision revision
            WHERE revision.organizationId = :organizationId AND revision.entryId IN :entryIds
            """)
    Set<UUID> findCorrectedEntryIds(
            @Param("organizationId") UUID organizationId,
            @Param("entryIds") Collection<UUID> entryIds
    );
}
