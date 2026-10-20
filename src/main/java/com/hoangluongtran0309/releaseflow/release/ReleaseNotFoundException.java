package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.ResourceNotFoundException;

public class ReleaseNotFoundException extends ResourceNotFoundException {

    public ReleaseNotFoundException() {
        super("error.release_not_found");
    }
}
