package com.hoangluongtran0309.releaseflow.account;

import java.time.Instant;
import java.util.UUID;

public record MemberView(UUID id, String email, String displayName, AppUserRole role, Instant createdAt) {

    public String roleLabelKey() {
        return role == AppUserRole.ADMIN ? "ui.members.role.admin" : "ui.members.role.member";
    }

    public boolean admin() {
        return role == AppUserRole.ADMIN;
    }
}
