package com.hoangluongtran0309.releaseflow.change;

import java.time.Instant;
import java.util.UUID;

/** A person's decision that a change will never ship: who made it, when, and why. */
public record ChangeExclusion(UUID excludedBy, String excluderName, Instant excludedAt, String reason) {
}
