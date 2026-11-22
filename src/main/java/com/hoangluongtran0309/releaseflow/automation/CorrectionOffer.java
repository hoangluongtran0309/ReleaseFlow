package com.hoangluongtran0309.releaseflow.automation;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * What a correction run of one rule could deliver for one published release (ADR-0046, part
 * 5): each of the rule's actions, offered or not, with the reason it is not. An action is
 * offered when it has a successful delivery for the release and its note has a later revision
 * than that delivery carried. {@code suggestedReason} is the reasons of the revisions the
 * default picks would deliver, for the person to write the run's reason from.
 */
public record CorrectionOffer(
        UUID ruleId,
        UUID releaseId,
        String releaseVersion,
        List<Item> items,
        String suggestedReason
) {

    public CorrectionOffer {
        items = List.copyOf(items);
    }

    public boolean anyOffered() {
        return items.stream().anyMatch(Item::offered);
    }

    /**
     * One action of the rule. {@code carriedRevision} is the revision its last successful
     * delivery carried, and {@code latestRevision} the note's latest; either is null when
     * there is none.
     */
    public record Item(
            UUID ruleActionId,
            int position,
            ActionType actionType,
            String audienceName,
            String language,
            Unavailable unavailable,
            boolean picked,
            Integer carriedRevision,
            Integer latestRevision
    ) {

        public boolean offered() {
            return unavailable == null;
        }
    }

    /**
     * Why an action is not offered, in the order they are asked: a destination that cannot
     * be corrected at all first, since then its state does not matter.
     */
    public enum Unavailable {

        /** Its audience is retired, so nothing is delivered for it (ADR-0044). */
        AUDIENCE_RETIRED,
        /** Zendesk keeps the article it published; a correction is made there by hand. */
        NOT_SUPPORTED,
        /** A destination whose correction comes with Slice B (#88). */
        LATER,
        /** Its latest delivery's outcome is unknown, and a person settles it first. */
        UNKNOWN,
        /** A delivery of it for this release has not finished yet. */
        IN_PROGRESS,
        /** It was only ever skipped, so nothing was delivered there to correct (#135). */
        ONLY_SKIPPED,
        /** It never delivered this release: it failed, or never ran. */
        NEVER_DELIVERED,
        /** The note has no revision later than the one it last delivered. */
        NOTHING_NEWER;

        /** What an API caller matches on. */
        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The bundle key of the sentence a page shows. */
        public String getLabelKey() {
            return "ui.automation.correction.unavailable." + code();
        }
    }
}
