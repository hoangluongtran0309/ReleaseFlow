package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** A correction preview names a way of making the text this release does not offer. */
public class InvalidCorrectionOriginException extends LocalizedException {

    public InvalidCorrectionOriginException() {
        super("error.release_note_correction_origin_invalid");
    }
}
