package com.hoangluongtran0309.releaseflow.translation;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * One language pair's dictionary in the Organization's DeepL glossary: the version a
 * translation asked for, the version DeepL holds, and the bounded job that brings them
 * together. It is wanted by an approval and synced by {@link GlossarySyncWorker}.
 */
@Entity
@Table(name = "glossary_syncs")
class GlossarySync {

    @EmbeddedId
    private Key key;

    @Column(name = "wanted_version", nullable = false, length = 64)
    private String wantedVersion;

    @Column(name = "synced_version", length = 64)
    private String syncedVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "last_error", length = 100)
    private String lastError;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GlossarySync() {
    }

    GlossarySync(Key key, String wantedVersion, Instant now) {
        this.key = key;
        this.wantedVersion = wantedVersion;
        this.status = Status.PENDING;
        this.nextAttemptAt = now;
        this.updatedAt = now;
    }

    /**
     * A translation asks for this version. Nothing changes when DeepL holds it, a sync of it
     * is under way, or it has already been found unsupported or failed: only a person's
     * retry starts those again. A sync under way for another version is followed by one of
     * this version.
     */
    void want(String version, Instant now) {
        if (version.equals(wantedVersion) && status != Status.SYNCED) {
            return;
        }
        if (status == Status.SYNCED && version.equals(syncedVersion)) {
            this.wantedVersion = version;
            return;
        }
        this.wantedVersion = version;
        this.updatedAt = now;
        if (status == Status.RUNNING) {
            return;
        }
        this.status = Status.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.lastError = null;
    }

    /** A person's retry: a failed sync starts over with a fresh budget of attempts. */
    void restartIfFailed(Instant now) {
        if (status != Status.FAILED) {
            return;
        }
        this.status = Status.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.updatedAt = now;
    }

    /** DeepL refused a translation with this glossary, so its dictionary is sent again. */
    void resend(Instant now) {
        if (status == Status.RUNNING || status == Status.PENDING) {
            return;
        }
        this.status = Status.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.updatedAt = now;
    }

    /**
     * Claims the sync of the dictionary as the glossary now stands. A glossary edited since
     * the version was wanted can no longer produce it, so the current version is synced
     * instead, and translations that asked for the old one go without it.
     */
    void claim(String currentVersion, Instant now) {
        this.wantedVersion = currentVersion;
        this.status = Status.RUNNING;
        this.attempts++;
        this.claimedAt = now;
        this.updatedAt = now;
    }

    boolean isClaimedAt(Instant claim) {
        return status == Status.RUNNING && claim.equals(claimedAt);
    }

    /** DeepL holds {@code version}; if a later version was wanted meanwhile, it is synced next. */
    void synced(String version, Instant now) {
        this.syncedVersion = version;
        this.lastError = null;
        this.updatedAt = now;
        if (version.equals(wantedVersion)) {
            this.status = Status.SYNCED;
        } else {
            this.status = Status.PENDING;
            this.attempts = 0;
            this.nextAttemptAt = now;
        }
    }

    void unsupported(Instant now) {
        this.status = Status.UNSUPPORTED;
        this.lastError = null;
        this.updatedAt = now;
    }

    void retryLater(String error, Instant nextAttemptAt, Instant now) {
        this.status = Status.PENDING;
        this.lastError = error;
        this.nextAttemptAt = nextAttemptAt;
        this.updatedAt = now;
    }

    void fail(String error, Instant now) {
        this.status = Status.FAILED;
        this.lastError = error;
        this.updatedAt = now;
    }

    /** Whether DeepL holds exactly this version, so a translation can use it. */
    boolean usable(String version) {
        return status == Status.SYNCED && version.equals(syncedVersion);
    }

    Key getKey() {
        return key;
    }

    String getWantedVersion() {
        return wantedVersion;
    }

    String getSyncedVersion() {
        return syncedVersion;
    }

    Status getStatus() {
        return status;
    }

    int getAttempts() {
        return attempts;
    }

    String getLastError() {
        return lastError;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    enum Status {
        PENDING,
        RUNNING,
        SYNCED,
        UNSUPPORTED,
        FAILED
    }

    @Embeddable
    record Key(
            @Column(name = "organization_id") UUID organizationId,
            @Column(name = "source_language", length = 3) String sourceLanguage,
            @Column(name = "target_language", length = 3) String targetLanguage
    ) implements Serializable {
    }
}
