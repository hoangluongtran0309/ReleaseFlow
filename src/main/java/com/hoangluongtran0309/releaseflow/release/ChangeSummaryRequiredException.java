package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

import java.util.List;

/**
 * A change with no summary whose title is too short to stand alone in a note (ADR-0035).
 * A person writes its summary first.
 */
public class ChangeSummaryRequiredException extends LocalizedException {

    public ChangeSummaryRequiredException(List<String> references) {
        super("error.change_summary_required", String.join(", ", references));
    }
}
