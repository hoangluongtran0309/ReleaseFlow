package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.configuration.ResourceNotFoundException;

/** No automation Run with that ID belongs to the current Organization. */
public class AutomationRunNotFoundException extends ResourceNotFoundException {

    AutomationRunNotFoundException() {
        super("error.automation_run_not_found");
    }
}
