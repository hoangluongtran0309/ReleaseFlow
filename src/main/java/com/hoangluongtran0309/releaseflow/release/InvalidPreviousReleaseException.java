package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** A previous release that is not a published release of the same Project. */
public class InvalidPreviousReleaseException extends LocalizedException {

    InvalidPreviousReleaseException() {
        super("error.invalid_previous_release.notPublished");
    }
}
