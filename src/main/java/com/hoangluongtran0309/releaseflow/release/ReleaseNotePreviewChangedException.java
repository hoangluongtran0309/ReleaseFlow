package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** Rendering again now gives another text than the preview a person read, so it is shown again (ADR-0046). */
public class ReleaseNotePreviewChangedException extends LocalizedException {

    public ReleaseNotePreviewChangedException() {
        super("error.release_note_preview_changed");
    }
}
