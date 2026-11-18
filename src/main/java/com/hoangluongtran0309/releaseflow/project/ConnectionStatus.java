package com.hoangluongtran0309.releaseflow.project;

/**
 * Whether a source can be used: {@code ACTIVE} when its credentials worked the last time
 * they were used, {@code ERROR} when the provider refused them, and {@code DISCONNECTED}
 * once the source is disconnected. Only the first two are stored; {@code DISCONNECTED} is
 * read from the source's disconnection, so reconnecting brings back the stored status.
 */
public enum ConnectionStatus {
    ACTIVE,
    ERROR,
    DISCONNECTED
}
