package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** A text the note uses has no answer it was published with, so rendering again cannot complete it. */
public class ReleaseNoteRenderIncompleteException extends LocalizedException {

    public ReleaseNoteRenderIncompleteException(int missingTexts) {
        super("error.release_note_render_incomplete", missingTexts);
    }
}
