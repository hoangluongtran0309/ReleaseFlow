package com.hoangluongtran0309.releaseflow.linear;

/**
 * A Linear team as ReleaseFlow needs it: the workspace every delivery is checked against,
 * and the key its issue identifiers start with, such as {@code OPS} in {@code OPS-7}.
 */
public record LinearTeam(String workspaceId, String key) {
}
