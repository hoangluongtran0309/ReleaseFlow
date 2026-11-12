package com.hoangluongtran0309.releaseflow.account;

import java.time.Instant;
import java.util.UUID;

/** A person who was removed, with who removed them under the name they had then. */
public record RemovedMemberView(
        UUID id,
        String displayName,
        String email,
        Instant removedAt,
        String removedByName
) {
}
