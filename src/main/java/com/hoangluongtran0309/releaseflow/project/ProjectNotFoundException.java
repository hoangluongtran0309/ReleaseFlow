package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.configuration.ResourceNotFoundException;

public class ProjectNotFoundException extends ResourceNotFoundException {

    public ProjectNotFoundException() {
        super("error.project_not_found");
    }
}
