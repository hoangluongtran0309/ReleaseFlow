package com.hoangluongtran0309.releaseflow.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Asks for one correction run (ADR-0046, part 5): one rule, one published release, the
 * actions picked, and the reason every picked destination shows. The request ID makes a
 * repeat the same run, as a manual run's does.
 */
public class CorrectionRequest {

    static final int MAX_REASON_LENGTH = 500;

    @NotNull(message = "{validation.release.required}")
    private UUID releaseId;

    private UUID requestId;

    @NotEmpty(message = "{validation.correction.actionsRequired}")
    private List<UUID> ruleActionIds = new ArrayList<>();

    @NotBlank(message = "{validation.correction.reasonRequired}")
    @Size(max = MAX_REASON_LENGTH, message = "{validation.correction.reasonTooLong}")
    private String reason;

    public UUID getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(UUID releaseId) {
        this.releaseId = releaseId;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public List<UUID> getRuleActionIds() {
        return ruleActionIds;
    }

    public void setRuleActionIds(List<UUID> ruleActionIds) {
        this.ruleActionIds = ruleActionIds == null ? new ArrayList<>() : new ArrayList<>(ruleActionIds);
    }

    public String getReason() {
        return reason;
    }

    // A reason is published as written, so only line endings and the ends are tidied.
    public void setReason(String reason) {
        this.reason = reason == null ? null : reason.replace("\r\n", "\n").strip();
    }
}
