package com.hoangluongtran0309.releaseflow.release;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface ReleaseNoteRevisionRepository extends JpaRepository<ReleaseNoteRevision, UUID> {

    List<ReleaseNoteRevision> findAllByNoteIdAndOrganizationIdOrderByRevisionAsc(UUID noteId, UUID organizationId);

    /** The latest revision of each of the release's notes that has one. */
    @Query(value = """
            SELECT DISTINCT ON (note_id) *
            FROM release_note_revisions
            WHERE release_id = :releaseId AND organization_id = :organizationId
            ORDER BY note_id, revision DESC
            """, nativeQuery = true)
    List<ReleaseNoteRevision> findLatestByReleaseIdAndOrganizationId(UUID releaseId, UUID organizationId);
}
