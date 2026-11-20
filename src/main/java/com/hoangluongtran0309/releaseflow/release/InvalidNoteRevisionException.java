package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** A revision named by something other than a positive whole number. */
public class InvalidNoteRevisionException extends LocalizedException {

    public InvalidNoteRevisionException() {
        super("error.release_note_revision_invalid");
    }
}
