package com.hoangluongtran0309.releaseflow.translation;

import com.hoangluongtran0309.releaseflow.configuration.ResourceNotFoundException;

public class GlossaryTermNotFoundException extends ResourceNotFoundException {

    public GlossaryTermNotFoundException() {
        super("error.glossary_term_not_found");
    }
}
