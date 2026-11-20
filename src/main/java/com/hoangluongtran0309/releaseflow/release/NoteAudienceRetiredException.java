package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** A retired audience's note is never rendered or translated again; it is corrected by hand only (ADR-0046). */
public class NoteAudienceRetiredException extends LocalizedException {

    public NoteAudienceRetiredException() {
        super("error.audience_retired");
    }
}
