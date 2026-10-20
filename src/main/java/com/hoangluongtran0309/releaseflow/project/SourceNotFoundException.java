package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.configuration.ResourceNotFoundException;

public class SourceNotFoundException extends ResourceNotFoundException {

    public SourceNotFoundException() {
        super("error.source_not_found");
    }
}
