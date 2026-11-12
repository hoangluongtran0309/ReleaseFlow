package com.hoangluongtran0309.releaseflow.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One role change or removal, with who made it under the name they had then. Rows are only
 * ever inserted (ADR-0043).
 */
@Entity
@Table(name = "membership_changes")
class MembershipChange {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_role", nullable = false, length = 20, updatable = false)
    private AppUserRole previousRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_role", length = 20, updatable = false)
    private AppUserRole newRole;

    @Column(name = "revoked_invitations", nullable = false, updatable = false)
    private int revokedInvitations;

    @Column(name = "changed_by", nullable = false, updatable = false)
    private UUID changedBy;

    @Column(name = "changer_name", nullable = false, length = 120, updatable = false)
    private String changerName;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected MembershipChange() {
    }

    private MembershipChange(
            AppUser member,
            Kind kind,
            AppUserRole previousRole,
            AppUserRole newRole,
            int revokedInvitations,
            ReleaseFlowPrincipal administrator,
            Instant at
    ) {
        this.id = UUID.randomUUID();
        this.organizationId = member.getOrganizationId();
        this.memberId = member.getId();
        this.kind = kind;
        this.previousRole = previousRole;
        this.newRole = newRole;
        this.revokedInvitations = revokedInvitations;
        this.changedBy = administrator.userId();
        this.changerName = administrator.displayName();
        this.changedAt = at;
    }

    static MembershipChange roleChanged(
            AppUser member, AppUserRole previousRole, int revokedInvitations, ReleaseFlowPrincipal administrator, Instant at
    ) {
        return new MembershipChange(member, Kind.ROLE_CHANGED, previousRole, member.getRole(), revokedInvitations,
                administrator, at);
    }

    static MembershipChange removed(AppUser member, int revokedInvitations, ReleaseFlowPrincipal administrator, Instant at) {
        return new MembershipChange(member, Kind.REMOVED, member.getRole(), null, revokedInvitations, administrator, at);
    }

    UUID getId() {
        return id;
    }

    UUID getMemberId() {
        return memberId;
    }

    Kind getKind() {
        return kind;
    }

    AppUserRole getPreviousRole() {
        return previousRole;
    }

    AppUserRole getNewRole() {
        return newRole;
    }

    int getRevokedInvitations() {
        return revokedInvitations;
    }

    UUID getChangedBy() {
        return changedBy;
    }

    String getChangerName() {
        return changerName;
    }

    Instant getChangedAt() {
        return changedAt;
    }

    enum Kind {
        ROLE_CHANGED,
        REMOVED
    }
}
