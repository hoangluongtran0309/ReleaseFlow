package com.hoangluongtran0309.releaseflow.release;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A release's details. The upgrade guide and the previous release were added later
 * (ADR-0037): a caller that leaves one out keeps the value a release has, or gets the
 * default when creating one; an explicit null clears it.
 */
public class ReleaseRequest {

    @NotBlank(message = "{validation.version.required}")
    @Size(max = 50, message = "{validation.version.tooLong}")
    private String version;

    @Size(max = 2000, message = "{validation.summary.tooLong}")
    private String summary;

    private String upgradeGuideUrl;

    private boolean upgradeGuideUrlGiven;

    private UUID previousReleaseId;

    private boolean previousReleaseIdGiven;

    // The character count a person was shown before an edit that would translate the release
    // again (ADR-0047, amended 2026-11-23); the edit proceeds only when it is still the count.
    private Integer retranslationCharacters;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version == null ? null : version.strip();
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary == null || summary.isBlank() ? null : summary.strip();
    }

    public String getUpgradeGuideUrl() {
        return upgradeGuideUrl;
    }

    public void setUpgradeGuideUrl(String upgradeGuideUrl) {
        this.upgradeGuideUrl = upgradeGuideUrl == null || upgradeGuideUrl.isBlank() ? null : upgradeGuideUrl.strip();
        this.upgradeGuideUrlGiven = true;
    }

    public UUID getPreviousReleaseId() {
        return previousReleaseId;
    }

    public void setPreviousReleaseId(UUID previousReleaseId) {
        this.previousReleaseId = previousReleaseId;
        this.previousReleaseIdGiven = true;
    }

    public Integer getRetranslationCharacters() {
        return retranslationCharacters;
    }

    public void setRetranslationCharacters(Integer retranslationCharacters) {
        this.retranslationCharacters = retranslationCharacters;
    }

    boolean upgradeGuideUrlGiven() {
        return upgradeGuideUrlGiven;
    }

    boolean previousReleaseIdGiven() {
        return previousReleaseIdGiven;
    }
}
