package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** The change would leave the Organization without an administrator (ADR-0043). */
public class LastAdministratorException extends LocalizedException {

    public LastAdministratorException() {
        super("error.member_last_administrator");
    }
}
