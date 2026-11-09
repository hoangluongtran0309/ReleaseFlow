package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.jira.JiraApiClient;
import com.hoangluongtran0309.releaseflow.jira.LinkedIssue;
import com.hoangluongtran0309.releaseflow.linear.LinearApiClient;
import com.hoangluongtran0309.releaseflow.linear.LinearTeam;
import com.hoangluongtran0309.releaseflow.project.SourceAccess;
import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Asks the Project's issue trackers what they know about a change from another source: its
 * Jira project for keys such as {@code DBV-12}, and its Linear team for identifiers such as
 * {@code OPS-7} (ADR-0019, ADR-0041). This is the one place a change is enriched from a
 * source that is not its own, so it looks the trackers up by type rather than by ID.
 */
@Component
class LinkedContextEnricher {

    private final SourceAccess sourceAccess;
    private final JiraApiClient jiraApiClient;
    private final LinearApiClient linearApiClient;

    LinkedContextEnricher(SourceAccess sourceAccess, JiraApiClient jiraApiClient, LinearApiClient linearApiClient) {
        this.sourceAccess = sourceAccess;
        this.jiraApiClient = jiraApiClient;
        this.linearApiClient = linearApiClient;
    }

    LinkedContext enrich(
            UUID organizationId,
            UUID projectId,
            MergedPullRequest change,
            SourceEnricher enricher,
            SourceCredentials credentials,
            String token
    ) {
        // A tracker's own change is already the issue; it links to nothing.
        if (!credentials.sourceType().hasWebhook() || credentials.sourceType() == SourceType.LINEAR) {
            return LinkedContext.NOT_SUPPORTED;
        }
        Optional<SourceCredentials> jira = withToken(sourceAccess.findByType(organizationId, projectId, SourceType.JIRA));
        Optional<SourceCredentials> linear = withToken(sourceAccess.findByType(organizationId, projectId, SourceType.LINEAR));
        if (jira.isEmpty() && linear.isEmpty()) {
            return LinkedContext.NOT_CONFIGURED;
        }

        // Most keys live in commit messages, so a list ReleaseFlow could not read may hide one.
        Optional<List<String>> commits = enricher.commitMessages(credentials, token, change);
        IssueKeys.Evidence evidence = new IssueKeys.Evidence(
                change.title(),
                change.description(),
                change.targetBranch(),
                change.headBranch(),
                commits.orElse(List.of())
        );

        Found found = new Found();
        jira.ifPresent(source -> readJira(source, evidence, found));
        linear.ifPresent(source -> readLinear(organizationId, projectId, source, evidence, found));
        if (found.unread) {
            return LinkedContext.retryable(LinkedContextStatus.UNAVAILABLE, found.issues);
        }
        if (found.keys == 0) {
            return commits.isPresent()
                    ? LinkedContext.of(LinkedContextStatus.NOT_FOUND, List.of())
                    : LinkedContext.retryable(LinkedContextStatus.PARTIAL, List.of());
        }
        return commits.isPresent()
                ? LinkedContext.of(LinkedContextStatus.COLLECTED, found.issues)
                : LinkedContext.retryable(LinkedContextStatus.PARTIAL, found.issues);
    }

    private void readJira(SourceCredentials jira, IssueKeys.Evidence evidence, Found found) {
        for (String key : IssueKeys.extract(jira.externalProjectKey(), evidence)) {
            found.keys++;
            Optional<LinkedIssue> issue = jiraApiClient.issue(
                    jira.apiBaseUrl(), key, jira.credentialIdentity(), jira.token());
            issue.ifPresentOrElse(found.issues::add, () -> found.unread = true);
        }
    }

    // A team connected before its key was kept is asked for it once; a team whose key cannot
    // be read links nothing, and says so.
    private void readLinear(UUID organizationId, UUID projectId, SourceCredentials linear, IssueKeys.Evidence evidence,
                            Found found) {
        String teamKey = linear.teamKey();
        if (teamKey == null || teamKey.isBlank()) {
            teamKey = linearApiClient.team(linear.externalProjectKey(), linear.token())
                    .map(LinearTeam::key)
                    .filter(key -> !key.isBlank())
                    .orElse(null);
            if (teamKey == null) {
                found.unread = true;
                return;
            }
            sourceAccess.rememberTeamKey(organizationId, projectId, linear.sourceId(), teamKey);
        }
        for (String identifier : IssueKeys.extract(teamKey, evidence)) {
            found.keys++;
            Optional<LinkedIssue> issue = linearApiClient.linkedIssue(
                    identifier, linear.externalProjectKey(), linear.externalWorkspaceKey(), linear.token());
            issue.ifPresentOrElse(found.issues::add, () -> found.unread = true);
        }
    }

    private static Optional<SourceCredentials> withToken(Optional<SourceCredentials> source) {
        return source.filter(credentials -> credentials.accessToken().isPresent());
    }

    // What the trackers answered for one change.
    private static final class Found {
        private final List<LinkedIssue> issues = new ArrayList<>();
        private int keys;
        private boolean unread;
    }
}
