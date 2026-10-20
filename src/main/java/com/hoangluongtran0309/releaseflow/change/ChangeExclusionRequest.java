package com.hoangluongtran0309.releaseflow.change;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why a change will never ship; a person reads it later, so it is required. */
public class ChangeExclusionRequest {

    @NotBlank(message = "{validation.exclusionReason.required}")
    @Size(max = 500, message = "{validation.exclusionReason.tooLong}")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason == null ? null : reason.strip();
    }
}
