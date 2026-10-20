package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.ResourceNotFoundException;

public class ChangeNotFoundException extends ResourceNotFoundException {

    public ChangeNotFoundException() {
        super("error.change_not_found");
    }
}
