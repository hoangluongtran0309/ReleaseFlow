package com.hoangluongtran0309.releaseflow.release;

import java.time.Instant;
import java.util.List;

/**
 * One linked issue of a release's changes, as of its review (ADR-0041).
 *
 * @param status the status as of review when it was read, otherwise the recorded one
 * @param open whether that status is not known to be finished
 * @param accepted whether a person accepted showing that very status in the notes
 */
public record LinkedIssueStatusView(
        String tracker,
        String key,
        String title,
        String url,
        List<String> changeReferences,
        String recordedStatus,
        String statusAtReview,
        boolean read,
        String status,
        boolean open,
        boolean accepted,
        String accepterName,
        Instant acceptedAt
) {

    public LinkedIssueStatusView {
        changeReferences = List.copyOf(changeReferences);
    }

    /** An issue still open while its change ships, whose status nobody accepted. */
    public boolean warning() {
        return open && !accepted;
    }
}
