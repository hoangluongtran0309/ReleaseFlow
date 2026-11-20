package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/**
 * A correction named a revision that is no longer the note's latest (ADR-0046): someone
 * corrected it meanwhile, so the latest has to be read before it is corrected again.
 */
public class ReleaseNoteRevisionStaleException extends LocalizedException {

    public ReleaseNoteRevisionStaleException(int latest) {
        super("error.release_note_revision_stale", latest);
    }
}
