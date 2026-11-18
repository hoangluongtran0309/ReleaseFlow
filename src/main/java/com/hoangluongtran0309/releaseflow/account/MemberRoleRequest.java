package com.hoangluongtran0309.releaseflow.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** The role an administrator gives a member: {@code ADMIN} or {@code MEMBER}. */
public class MemberRoleRequest {

    @NotBlank(message = "{validation.memberRole.required}")
    @Pattern(regexp = "ADMIN|MEMBER", message = "{validation.memberRole.known}")
    private String role;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role == null ? null : role.strip();
    }

    AppUserRole appUserRole() {
        return AppUserRole.valueOf(role);
    }
}
