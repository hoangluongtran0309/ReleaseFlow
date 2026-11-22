package com.hoangluongtran0309.releaseflow.automation;

import java.util.Map;
import java.util.UUID;

/**
 * Everything one delivery needs, copied out of the database when the Action was
 * claimed, so the provider is called with no transaction open. Never log this: it
 * carries the decrypted secret.
 */
record ActionCommand(
        UUID actionRunId,
        UUID organizationId,
        UUID projectId,
        UUID releaseId,
        String releaseVersion,
        String audienceName,
        String language,
        String noteContent,
        Map<String, String> configuration,
        String rawSecret,
        Correction correction
) {

    ActionCommand {
        configuration = Map.copyOf(configuration);
    }

    /** A delivery that corrects nothing: every run but a correction's. */
    ActionCommand(
            UUID actionRunId,
            UUID organizationId,
            UUID projectId,
            UUID releaseId,
            String releaseVersion,
            String audienceName,
            String language,
            String noteContent,
            Map<String, String> configuration,
            String rawSecret
    ) {
        this(actionRunId, organizationId, projectId, releaseId, releaseVersion, audienceName, language, noteContent,
                configuration, rawSecret, null);
    }

    /**
     * What a correction delivers beyond the note (ADR-0046, part 5): the successful delivery
     * it replaces and the audience's name as that delivery gave it, the revision of the note
     * it carries, and the reason its run gives.
     */
    record Correction(UUID correctsActionRunId, String deliveredAudienceName, int noteRevision, String reason) {
    }

    @Override
    public String toString() {
        return "ActionCommand[actionRunId=%s, release=%s, secret=***]".formatted(actionRunId, releaseId);
    }
}
