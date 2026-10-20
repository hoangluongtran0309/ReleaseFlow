package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.ResourceNotFoundException;

public class ReleaseNoteNotFoundException extends ResourceNotFoundException {

    public ReleaseNoteNotFoundException() {
        super("error.release_note_not_found");
    }
}
