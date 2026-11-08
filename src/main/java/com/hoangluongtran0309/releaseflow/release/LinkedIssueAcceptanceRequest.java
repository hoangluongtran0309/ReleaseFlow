package com.hoangluongtran0309.releaseflow.release;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Accepts that a release's notes show a linked issue's status (ADR-0041). It names the
 * status the person saw, so a page loaded before another read never accepts a status it did
 * not show.
 */
public class LinkedIssueAcceptanceRequest {

    @NotBlank(message = "{validation.linkedIssueTracker.required}")
    @Pattern(regexp = "JIRA|LINEAR", message = "{validation.linkedIssueTracker.required}")
    private String tracker;

    @NotBlank(message = "{validation.linkedIssueKey.required}")
    @Size(max = 100, message = "{validation.linkedIssueKey.required}")
    private String key;

    @NotBlank(message = "{validation.linkedIssueStatus.required}")
    @Size(max = 200, message = "{validation.linkedIssueStatus.required}")
    private String status;

    public String getTracker() {
        return tracker;
    }

    public void setTracker(String tracker) {
        this.tracker = tracker == null ? null : tracker.strip();
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key == null ? null : key.strip();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
