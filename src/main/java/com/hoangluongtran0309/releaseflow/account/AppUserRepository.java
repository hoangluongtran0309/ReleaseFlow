package com.hoangluongtran0309.releaseflow.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    // A removed member's address is free again (ADR-0043), so every lookup by address
    // reads members who are not removed.
    boolean existsByEmailAndRemovedAtIsNull(String email);

    Optional<AppUser> findByEmailAndRemovedAtIsNull(String email);

    List<AppUser> findAllByOrganizationIdAndRemovedAtIsNullOrderByCreatedAtAscIdAsc(UUID organizationId);

    List<AppUser> findAllByOrganizationIdAndRemovedAtIsNotNullOrderByRemovedAtDescIdDesc(UUID organizationId);

    long countByOrganizationIdAndRoleAndRemovedAtIsNull(UUID organizationId, AppUserRole role);

    /** What a session is checked against on every request: one indexed read. */
    @Query("""
            select user.sessionVersion as sessionVersion, user.removedAt as removedAt
            from AppUser user
            where user.id = :id and user.organizationId = :organizationId
            """)
    Optional<MembershipState> findMembershipState(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    interface MembershipState {
        int getSessionVersion();

        Instant getRemovedAt();
    }

    Optional<AppUser> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<AppUser> findAllByOrganizationIdOrderByCreatedAtAscIdAsc(UUID organizationId);

    @Query("select user.uiLocale from AppUser user where user.id = :id")
    Optional<String> findUiLocaleById(@Param("id") UUID id);
}
