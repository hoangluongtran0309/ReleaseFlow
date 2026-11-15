package com.hoangluongtran0309.releaseflow.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    // Unique among members who are not removed (ADR-0043).
    @Column(nullable = false, length = 254)
    private String email;

    // Null exactly when the member was removed: nothing that could sign in is kept.
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppUserRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** The UI language this person chose. NULL means their browser decides. */
    @Column(name = "ui_locale", length = 16)
    private String uiLocale;

    // Raised by every role change and by removal; a session made with another is refused.
    @Column(name = "session_version", nullable = false)
    private int sessionVersion;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_by")
    private UUID removedBy;

    protected AppUser() {
    }

    AppUser(
            UUID id,
            UUID organizationId,
            String email,
            String passwordHash,
            String displayName,
            AppUserRole role,
            Instant createdAt
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getEmail() {
        return email;
    }

    String passwordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AppUserRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getUiLocale() {
        return uiLocale;
    }

    void chooseUiLocale(String uiLocale) {
        this.uiLocale = uiLocale;
    }

    int getSessionVersion() {
        return sessionVersion;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    UUID getRemovedBy() {
        return removedBy;
    }

    boolean isRemoved() {
        return removedAt != null;
    }

    /** A new role; every session made with the old one stops on its next request. */
    void changeRole(AppUserRole role) {
        if (isRemoved()) {
            throw new IllegalStateException("A removed member has no role to change.");
        }
        this.role = role;
        this.sessionVersion++;
    }

    /**
     * Ends the membership and keeps the row, so every name and key in the history still
     * resolves. Nothing that could sign in is left, and every session stops.
     */
    void remove(UUID administratorId, Instant at) {
        if (isRemoved()) {
            throw new IllegalStateException("The member was already removed.");
        }
        this.removedAt = at;
        this.removedBy = administratorId;
        this.passwordHash = null;
        this.sessionVersion++;
    }
}
