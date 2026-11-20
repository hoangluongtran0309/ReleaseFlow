package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.translation.GlossaryUse;

import java.time.Instant;

/**
 * One revision of a published note (ADR-0046). Revision 1 is the note as published, made by
 * {@code PUBLISHED}, with no reason, the publisher's name as recorded, and the publication
 * time. A later revision says how its text was made, why, and by whom, under the name the
 * person had then.
 */
public record ReleaseNoteRevisionView(
        int revision,
        Origin origin,
        String content,
        String reason,
        String authorName,
        Instant createdAt,
        GlossaryUse.Status glossaryStatus,
        String glossaryReason
) {

    /** How a revision's text was made. */
    public enum Origin {
        PUBLISHED,
        MANUAL,
        RENDERED,
        TRANSLATED
    }
}
