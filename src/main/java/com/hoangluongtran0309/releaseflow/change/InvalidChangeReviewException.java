package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class InvalidChangeReviewException extends LocalizedException {

    public InvalidChangeReviewException() {
        super("error.invalid_change_review");
    }

    /** The same refusal, explained by one of the {@code error.invalid_change_review.*} sentences. */
    public InvalidChangeReviewException(String messageKey) {
        super(messageKey);
    }
}
