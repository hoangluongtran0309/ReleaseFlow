package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.configuration.ResourceNotFoundException;

public class AudienceNotFoundException extends ResourceNotFoundException {

    public AudienceNotFoundException() {
        super("error.audience_not_found");
    }
}
