package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.ResourceNotFoundException;

public class LinkedIssueNotFoundException extends ResourceNotFoundException {

    public LinkedIssueNotFoundException() {
        super("error.linked_issue_not_found");
    }
}
