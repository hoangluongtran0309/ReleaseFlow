package com.hoangluongtran0309.releaseflow.release;

import java.util.UUID;

/** A published release another release of its Project may follow. */
public record ReleaseOption(UUID id, String version) {
}
