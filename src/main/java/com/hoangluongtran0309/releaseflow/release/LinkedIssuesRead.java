package com.hoangluongtran0309.releaseflow.release;

import java.util.UUID;

/** A release's linked issues were read as of its review, so its notes may show other statuses. */
record LinkedIssuesRead(UUID organizationId, UUID releaseId) {
}
