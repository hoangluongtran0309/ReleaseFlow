package com.hoangluongtran0309.releaseflow.release;

import java.util.UUID;

/**
 * A suggestion to fold a tracker change into a code change of the same release (ADR-0041),
 * with its evidence: the code change holds the tracker change's key as a linked issue, or a
 * person confirmed the two as possible duplicates. It is evidence only; a person decides.
 *
 * @param confirmedBy who confirmed the duplicate, for {@code CONFIRMED_DUPLICATE} only
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
        String confirmedBy
) {
}
