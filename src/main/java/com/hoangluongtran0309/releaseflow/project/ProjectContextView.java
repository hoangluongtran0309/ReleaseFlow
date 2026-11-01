package com.hoangluongtran0309.releaseflow.project;

import java.time.Instant;
import java.util.UUID;

/**
 * A Project's context, and who changed it last. It applies to changes classified
 * afterwards.
 */
public record ProjectContextView(UUID projectId, String context, String updatedBy, Instant updatedAt) {
}
