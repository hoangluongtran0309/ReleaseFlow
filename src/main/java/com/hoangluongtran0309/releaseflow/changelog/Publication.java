package com.hoangluongtran0309.releaseflow.changelog;

import java.util.UUID;

/**
 * What became of one attempt to make a release note public, and the address readers
 * will find it at.
 */
public record Publication(Outcome outcome, String url) {

    public enum Outcome {

        /** The note is public: either this call published it, or it already was. */
        PUBLISHED,

        /**
         * This release, audience, and language are already public with different
         * words. Published material is never overwritten, so the delivery fails and a
         * person decides what to do.
         */
        CONFLICT
    }

    /**
     * A corrected note for the entry an earlier delivery made (ADR-0046, parts 4 and 5).
     * {@code correctsActionRunId} is that delivery, and {@code deliveredAudienceName} the
     * audience's name as it delivered it, which finds an entry the delivery matched rather
     * than wrote. {@code reason} is the one the correction run gives.
     */
    public record Correction(
            UUID actionRunId,
            UUID organizationId,
            UUID releaseId,
            UUID correctsActionRunId,
            String deliveredAudienceName,
            String language,
            int noteRevision,
            String content,
            String reason
    ) {
    }

    /** One note offered to the public changelog. */
    public record Request(
            UUID actionRunId,
            UUID organizationId,
            UUID projectId,
            UUID releaseId,
            String releaseVersion,
            String audienceName,
            String language,
            String content
    ) {
    }
}
