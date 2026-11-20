package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.ResourceNotFoundException;

public class ReleaseNoteNotFoundException extends ResourceNotFoundException {

    public ReleaseNoteNotFoundException() {
        super("error.release_note_not_found");
    }

    /** Not found, said in the words of the key: a revision the note does not have. */
    public ReleaseNoteNotFoundException(String messageKey) {
        super(messageKey);
    }
}
