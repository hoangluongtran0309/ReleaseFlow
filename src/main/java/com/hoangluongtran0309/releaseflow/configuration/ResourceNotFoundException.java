package com.hoangluongtran0309.releaseflow.configuration;

import java.io.Serial;

/**
 * Something a request named that this Organization does not have: it never existed, it is
 * gone, or it belongs to another Organization, which is the same answer on purpose. A
 * page answers it with the not-found page; the API answers each kind with its own code.
 */
public abstract class ResourceNotFoundException extends LocalizedException {

    @Serial
    private static final long serialVersionUID = 1L;

    protected ResourceNotFoundException(String messageKey, Object... arguments) {
        super(messageKey, arguments);
    }
}
