package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.configuration.ResourceNotFoundException;

/** No member with that ID in the caller's Organization: unknown, another's, or removed. */
public class MemberNotFoundException extends ResourceNotFoundException {

    public MemberNotFoundException() {
        super("error.member_not_found");
    }
}
