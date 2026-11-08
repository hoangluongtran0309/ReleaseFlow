package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.audience.NoteIssue;
import com.hoangluongtran0309.releaseflow.jira.LinkedIssue;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Each linked issue of a release as of its review, and the statuses a person accepted
 * (ADR-0041). It decides what a note may show: the status as of review when it was read,
 * otherwise the recorded one, and nothing for a status that is open, or whose category is
 * unknown, unless a person accepted showing that very status.
 */
final class IssueStatuses {

    static final IssueStatuses NONE = new IssueStatuses(Map.of());

    /** One issue's status as of review, if it was read, and the status a person accepted, if any. */
    record Entry(String status, String category, boolean read, String acceptedStatus) {
    }

    private final Map<String, Entry> entries;

    private IssueStatuses(Map<String, Entry> entries) {
        this.entries = entries;
    }

    static IssueStatuses of(List<ReleaseLinkedIssue> rows) {
        Map<String, Entry> entries = new HashMap<>();
        for (ReleaseLinkedIssue row : rows) {
            entries.put(id(row.getTracker(), row.getIssueKey()), new Entry(row.getStatus(), row.getStatusCategory(),
                    row.getReadAt() != null, row.getAcceptedStatus()));
        }
        return new IssueStatuses(entries);
    }

    /** The issue as a template may print it, unescaped. */
    NoteIssue noteIssue(LinkedIssue issue) {
        Entry entry = entry(issue);
        String recorded = shown(issue.status(), issue.done(), entry);
        String atReview = entry != null && entry.read() ? shown(entry.status(), LinkedIssue.DONE.equals(entry.category()), entry) : "";
        return new NoteIssue(
                issue.key(),
                issue.title(),
                issue.type(),
                entry != null && entry.read() ? atReview : recorded,
                recorded,
                atReview,
                issue.url()
        );
    }

    /** The status the issue has as of review if it was read, otherwise the recorded one. */
    String status(LinkedIssue issue) {
        Entry entry = entry(issue);
        return entry != null && entry.read() ? nullToEmpty(entry.status()) : nullToEmpty(issue.status());
    }

    /** Whether that status is not known to be finished. */
    boolean open(LinkedIssue issue) {
        Entry entry = entry(issue);
        return entry != null && entry.read() ? !LinkedIssue.DONE.equals(entry.category()) : !issue.done();
    }

    /** Whether it was read when the review was requested. */
    boolean read(LinkedIssue issue) {
        Entry entry = entry(issue);
        return entry != null && entry.read();
    }

    /** Whether a person accepted showing the status it now has. */
    boolean accepted(LinkedIssue issue) {
        Entry entry = entry(issue);
        String status = status(issue);
        return entry != null && !status.isEmpty() && status.equals(entry.acceptedStatus());
    }

    private Entry entry(LinkedIssue issue) {
        return entries.get(id(issue.trackerOrJira(), issue.key()));
    }

    // A status shows when its tracker counts it finished, or when a person accepted it.
    private static String shown(String status, boolean done, Entry entry) {
        String text = nullToEmpty(status);
        if (text.isEmpty()) {
            return "";
        }
        return done || (entry != null && text.equals(entry.acceptedStatus())) ? text : "";
    }

    static String id(String tracker, String key) {
        return tracker + ":" + key.toUpperCase(Locale.ROOT);
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
