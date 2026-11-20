package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** A correction whose text is the latest revision's exactly: there is nothing to correct. */
public class ReleaseNoteCorrectionUnchangedException extends LocalizedException {

    public ReleaseNoteCorrectionUnchangedException() {
        super("error.release_note_correction_unchanged");
    }
}
