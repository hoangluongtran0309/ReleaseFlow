package com.hoangluongtran0309.releaseflow.release;

import java.util.UUID;

/**
 * A suggestion to fold a tracker change into a code change of the same release (ADR-0041),
 * with its evidence: the code change holds the tracker change's key as a linked issue, or a
 * person confirmed the two as possible duplicates. It is evidence only; a person decides.
 *
 * @param confirmedBy who confirmed the duplicate, for {@code CONFIRMED_DUPLICATE} only
 * @param addsBreaking whether accepting marks the code change breaking, as the tracker change is
 * @param addsSecurity whether accepting marks the code change security, as the tracker change is
 */
public record FoldingSuggestion(
        UUID trackerChangeId,
        String trackerReference,
        String trackerTitle,
        UUID codeChangeId,
        String codeReference,
        String codeTitle,
        ReleaseFoldDecision.Evidence evidence,
        String issueKey,
        String confirmedBy,
        boolean addsBreaking,
        boolean addsSecurity
) {

    /** Whether accepting changes the code change's flags, and so records its review. */
    public boolean addsFlags() {
        return addsBreaking || addsSecurity;
    }
}
