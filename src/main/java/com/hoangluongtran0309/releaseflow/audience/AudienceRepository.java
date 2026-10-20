package com.hoangluongtran0309.releaseflow.audience;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AudienceRepository extends JpaRepository<AudienceDefinition, UUID> {

    List<AudienceDefinition> findAllByOrganizationIdOrderByDisplayNameAscCodeAsc(UUID organizationId);

    List<AudienceDefinition> findAllByOrganizationIdOrderByCodeAsc(UUID organizationId);

    Optional<AudienceDefinition> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);

    // Serializes changes to an Organization's set of audiences, so the count checks
    // (at least one, at most the limit) hold under concurrent requests.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AudienceDefinition a where a.organizationId = :organizationId order by a.code")
    List<AudienceDefinition> lockAllByOrganizationId(UUID organizationId);

    /**
     * What still names an audience, so it cannot be deleted: {@code notes}, {@code rule}
     * (an active rule's action, enabled or not), {@code archivedRule}, or {@code runHistory};
     * {@code null} when nothing does. Release notes are checked first, then rules, then the
     * runs that outlive them.
     */
    @Query(value = """
            SELECT CASE
                WHEN EXISTS (SELECT 1 FROM release_audience_notes n
                             WHERE n.audience_id = :audienceId AND n.organization_id = :organizationId)
                    THEN 'notes'
                WHEN EXISTS (SELECT 1 FROM automation_rule_actions a
                             JOIN automation_rules r ON r.id = a.rule_id AND r.organization_id = a.organization_id
                             WHERE a.audience_id = :audienceId AND a.organization_id = :organizationId AND r.active)
                    THEN 'rule'
                WHEN EXISTS (SELECT 1 FROM automation_rule_actions a
                             JOIN automation_rules r ON r.id = a.rule_id AND r.organization_id = a.organization_id
                             WHERE a.audience_id = :audienceId AND a.organization_id = :organizationId AND NOT r.active)
                    THEN 'archivedRule'
                WHEN EXISTS (SELECT 1 FROM automation_action_runs ar
                             WHERE ar.audience_id = :audienceId AND ar.organization_id = :organizationId)
                    THEN 'runHistory'
            END
            """, nativeQuery = true)
    String use(UUID organizationId, UUID audienceId);
}
