package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.configuration.ResourceNotFoundException;

/** No active automation Rule with that ID belongs to the current Organization. */
public class AutomationRuleNotFoundException extends ResourceNotFoundException {

    AutomationRuleNotFoundException() {
        super("error.automation_rule_not_found");
    }
}
