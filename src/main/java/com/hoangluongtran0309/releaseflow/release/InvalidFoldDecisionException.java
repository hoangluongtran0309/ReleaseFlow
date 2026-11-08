package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** An accepted fold that does not say how the tracker change leaves, or excludes it without a reason. */
public class InvalidFoldDecisionException extends LocalizedException {

    public InvalidFoldDecisionException(String messageKey) {
        super(messageKey);
    }
}
