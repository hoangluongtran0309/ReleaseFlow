package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.category.CategoryRef;

/**
 * Optional Change Inbox filters. A null category, status, or context means "all". The
 * category is a code of the catalog, compared with the code each change recorded; the
 * context filter keeps changes whose context was found insufficient, and the release
 * filter keeps changes excluded from releases.
 */
record ChangeFilter(String category, ReviewStatus status, boolean insufficientContext, boolean excluded) {

    static final String INSUFFICIENT_CONTEXT = "insufficient";
    static final String EXCLUDED = "excluded";

    static ChangeFilter parse(String category, String status) {
        return parse(category, status, null, null);
    }

    static ChangeFilter parse(String category, String status, String context) {
        return parse(category, status, context, null);
    }

    static ChangeFilter parse(String category, String status, String context, String release) {
        String parsedCategory = null;
        if (category != null && !category.isBlank()) {
            parsedCategory = CategoryRef.normalize(category).orElseThrow(() -> new InvalidChangeFilterException(
                    "error.invalid_change_filter.category"
            ));
        }
        ReviewStatus parsedStatus = null;
        if (status != null && !status.isBlank()) {
            parsedStatus = ReviewStatus.fromValue(status).orElseThrow(() -> new InvalidChangeFilterException(
                    "error.invalid_change_filter.status"
            ));
        }
        boolean insufficientContext = false;
        if (context != null && !context.isBlank()) {
            if (!INSUFFICIENT_CONTEXT.equals(context.strip())) {
                throw new InvalidChangeFilterException("error.invalid_change_filter.context");
            }
            insufficientContext = true;
        }
        boolean excluded = false;
        if (release != null && !release.isBlank()) {
            if (!EXCLUDED.equals(release.strip())) {
                throw new InvalidChangeFilterException("error.invalid_change_filter.release");
            }
            excluded = true;
        }
        return new ChangeFilter(parsedCategory, parsedStatus, insufficientContext, excluded);
    }

    Boolean needsReview() {
        return status == null ? null : status == ReviewStatus.NEEDS_REVIEW;
    }

    // "Classified" means settled by rules or AI without a person; "reviewed" means a person settled it.
    Boolean reviewed() {
        if (status == null || status == ReviewStatus.NEEDS_REVIEW) {
            return null;
        }
        return status == ReviewStatus.REVIEWED;
    }
}
