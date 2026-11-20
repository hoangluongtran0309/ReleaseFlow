package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** A note a person wrote never follows its template again, so it is corrected by hand only (ADR-0046). */
public class ReleaseNoteManualException extends LocalizedException {

    public ReleaseNoteManualException() {
        super("error.release_note_manual");
    }
}
