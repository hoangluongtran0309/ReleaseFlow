package com.hoangluongtran0309.releaseflow.audience;

import java.time.Instant;

/** Who retired an audience or brought it back, under the name they had then, and when. */
public record AudienceStatusChangeView(String changerName, Instant changedAt) {
}
