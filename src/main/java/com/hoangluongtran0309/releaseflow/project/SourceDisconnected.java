package com.hoangluongtran0309.releaseflow.project;

import java.util.UUID;

/**
 * Published inside the transaction that disconnects a source, so the work queued for it ends
 * in the same commit (ADR-0042).
 */
public record SourceDisconnected(UUID organizationId, UUID projectId, UUID sourceId) {
}
