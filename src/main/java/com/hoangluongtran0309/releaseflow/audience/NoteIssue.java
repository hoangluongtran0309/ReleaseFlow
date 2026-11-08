package com.hoangluongtran0309.releaseflow.audience;

import java.util.List;
import java.util.Objects;

/**
 * A linked issue as a note template sees it (ADR-0041). Every value is text and never null.
 * Any status that is open, or whose category is unknown, is empty unless a person accepted
 * showing it, so a published note never shows an open status nobody chose.
 *
 * @param status the status as of the release's review when it was read, otherwise the one
 *     recorded with the change
 * @param recordedStatus the status read when the change was recorded
 * @param statusAtReview the status read when the release's review was requested, empty
 *     when it was not read
 */
public record NoteIssue(
        String key,
        String title,
        String type,
        String status,
        String recordedStatus,
        String statusAtReview,
        String url
) {

    /** The values a template may print for each of a change's {@code linkedIssues}. */
    public static final List<String> VARIABLES =
            List.of("key", "title", "type", "status", "recordedStatus", "statusAtReview", "url");

    public NoteIssue {
        key = Objects.requireNonNullElse(key, "");
        title = Objects.requireNonNullElse(title, "");
        type = Objects.requireNonNullElse(type, "");
        status = Objects.requireNonNullElse(status, "");
        recordedStatus = Objects.requireNonNullElse(recordedStatus, "");
        statusAtReview = Objects.requireNonNullElse(statusAtReview, "");
        url = Objects.requireNonNullElse(url, "");
    }
}
