package com.hoangluongtran0309.releaseflow.translation;

import java.util.UUID;

/**
 * A change's content, or a release's own summary, was translated into a language, or its
 * translation failed for good. Exactly one of {@code changeId} and {@code releaseId} is
 * set. Published inside the transaction that recorded the result.
 */
public record TranslationFinished(UUID organizationId, UUID changeId, UUID releaseId, String targetLanguage) {
}
