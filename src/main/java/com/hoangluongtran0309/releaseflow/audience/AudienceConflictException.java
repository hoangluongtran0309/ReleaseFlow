package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** A change to an Organization's audiences that its current audiences do not allow. */
public class AudienceConflictException extends LocalizedException {

    private final String code;

    private AudienceConflictException(String code, Object... arguments) {
        this(code, "error." + code, arguments);
    }

    private AudienceConflictException(String code, String messageKey, Object... arguments) {
        super(messageKey, arguments);
        this.code = code;
    }

    static AudienceConflictException codeTaken() {
        return new AudienceConflictException("audience_code_taken");
    }

    /** The audience has release notes. */
    static AudienceConflictException inUse() {
        return new AudienceConflictException("audience_in_use");
    }

    /** An automation rule that can still be changed delivers to the audience. */
    static AudienceConflictException usedByRule() {
        return new AudienceConflictException("audience_in_use", "error.audience_in_use.rule");
    }

    /** An archived automation rule, which nobody can change any more, names the audience. */
    static AudienceConflictException usedByArchivedRule() {
        return new AudienceConflictException("audience_in_use", "error.audience_in_use.archivedRule");
    }

    /** A rule's run history records a delivery to the audience. */
    static AudienceConflictException usedByRunHistory() {
        return new AudienceConflictException("audience_in_use", "error.audience_in_use.runHistory");
    }

    static AudienceConflictException last() {
        return new AudienceConflictException("audience_last");
    }

    static AudienceConflictException limit() {
        return new AudienceConflictException("audience_limit");
    }

    static AudienceConflictException notPreset() {
        return new AudienceConflictException("audience_not_preset");
    }

    public String code() {
        return code;
    }
}
