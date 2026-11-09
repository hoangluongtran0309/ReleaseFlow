package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** The linked issue's status is no longer the one the person accepted. */
public class LinkedIssueStatusChangedException extends LocalizedException {

    public LinkedIssueStatusChangedException() {
        super("error.linked_issue_status_changed");
    }
}
