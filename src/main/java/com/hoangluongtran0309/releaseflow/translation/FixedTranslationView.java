package com.hoangluongtran0309.releaseflow.translation;

import java.time.Instant;
import java.util.UUID;

/** A term that always becomes {@code targetTerm} from {@code sourceLanguage} to {@code targetLanguage}. */
public record FixedTranslationView(
        UUID id,
        String sourceLanguage,
        String targetLanguage,
        String sourceTerm,
        String targetTerm,
        String creatorName,
        Instant createdAt
) {
}
