package com.hoangluongtran0309.releaseflow.translation;

import java.time.Instant;
import java.util.UUID;

/** A term kept as written in every language. */
public record KeptTermView(UUID id, String term, String creatorName, Instant createdAt) {
}
