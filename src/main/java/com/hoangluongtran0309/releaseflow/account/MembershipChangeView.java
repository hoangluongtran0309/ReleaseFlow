package com.hoangluongtran0309.releaseflow.account;

import java.time.Instant;
import java.util.UUID;

/**
 * A role change or a removal as the Members page and the API show it. The member's name is
 * the one their row keeps; the changer's is the one they had when they made the change.
 */
public record MembershipChangeView(
        UUID id,
        UUID memberId,
        String memberName,
        String memberEmail,
        String kind,
        AppUserRole previousRole,
        AppUserRole newRole,
        int revokedInvitations,
        UUID changedById,
        String changedByName,
        Instant changedAt
) {

    public boolean removal() {
        return "REMOVED".equals(kind);
    }

    public String previousRoleLabelKey() {
        return MemberView.roleLabelKey(previousRole);
    }

    public String newRoleLabelKey() {
        return newRole == null ? null : MemberView.roleLabelKey(newRole);
    }
}
