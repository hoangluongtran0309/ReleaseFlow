package com.hoangluongtran0309.releaseflow.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

/**
 * An issue a change mentions, as the tracker describes it. It is evidence about the
 * change, never a judgement of it.
 *
 * <p>{@code tracker} is {@code JIRA} or {@code LINEAR}, and {@code statusCategory} is
 * {@code done} for an issue its tracker counts as finished and {@code open} otherwise
 * (ADR-0041). An issue recorded before either existed has neither: it came from Jira, and
 * its category is unknown, which counts as not done.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LinkedIssue(
        String key,
        String title,
        String description,
        String type,
        String status,
        String url,
        String tracker,
        String statusCategory
) {

    public static final String JIRA = "JIRA";
    public static final String LINEAR = "LINEAR";
    public static final String DONE = "done";
    public static final String OPEN = "open";

    public LinkedIssue {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(title, "title must not be null");
    }

    /** An issue as it was recorded before trackers and categories were, from Jira. */
    public LinkedIssue(String key, String title, String description, String type, String status, String url) {
        this(key, title, description, type, status, url, null, null);
    }

    /** The tracker it came from; an issue recorded before trackers were named came from Jira. */
    public String trackerOrJira() {
        return tracker == null ? JIRA : tracker;
    }

    /** Whether its tracker counted it finished when it was read. */
    public boolean done() {
        return DONE.equals(statusCategory);
    }
}
