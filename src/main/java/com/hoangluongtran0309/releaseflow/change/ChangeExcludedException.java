package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** The change was excluded from releases, so it cannot be added to one or excluded again. */
public class ChangeExcludedException extends LocalizedException {

    public ChangeExcludedException() {
        super("error.change_excluded");
    }

    public ChangeExcludedException(String messageKey) {
        super(messageKey);
    }
}
