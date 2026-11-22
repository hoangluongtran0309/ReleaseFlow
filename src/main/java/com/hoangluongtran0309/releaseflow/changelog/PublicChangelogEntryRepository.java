package com.hoangluongtran0309.releaseflow.changelog;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PublicChangelogEntryRepository extends JpaRepository<PublicChangelogEntry, UUID> {

    List<PublicChangelogEntry> findAllByOrganizationIdOrderByPublishedAtDescIdDesc(
            UUID organizationId,
            Pageable pageable
    );

    Optional<PublicChangelogEntry> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** The entry a second delivery of the same note would be. */
    Optional<PublicChangelogEntry> findByOrganizationIdAndReleaseIdAndAudienceNameSnapshotAndLanguage(
            UUID organizationId,
            UUID releaseId,
            String audienceNameSnapshot,
            String language
    );

    Optional<PublicChangelogEntry> findByActionRunId(UUID actionRunId);

    /** The entry, locked, so two corrections of it decide one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT entry FROM PublicChangelogEntry entry WHERE entry.id = :id AND entry.organizationId = :organizationId")
    Optional<PublicChangelogEntry> lockByIdAndOrganizationId(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId
    );
}
