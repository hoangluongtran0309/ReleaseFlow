package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.ResourceNotFoundException;

public class DuplicateCandidateNotFoundException extends ResourceNotFoundException {

    public DuplicateCandidateNotFoundException() {
        super("error.duplicate_candidate_not_found");
    }
}
