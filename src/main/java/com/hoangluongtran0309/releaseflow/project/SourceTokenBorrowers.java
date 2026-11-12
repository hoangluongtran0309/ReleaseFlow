package com.hoangluongtran0309.releaseflow.project;

import java.util.List;
import java.util.UUID;

/**
 * The enabled automation rules of a Project that borrow its GitHub source's token, so the
 * page that disconnects one can name them (ADR-0042). Automation answers it; a Project never
 * reaches into automation itself.
 */
public interface SourceTokenBorrowers {

    List<String> enabledGitHubReleaseRules(UUID organizationId, UUID projectId);
}
