package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** A change that belongs to a release, of any status, cannot be excluded from releases. */
public class ChangeInReleaseException extends LocalizedException {

    public ChangeInReleaseException() {
        super("error.change_in_release");
    }
}
