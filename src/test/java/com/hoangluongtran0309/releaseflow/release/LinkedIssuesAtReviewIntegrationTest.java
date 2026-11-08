package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.JiraStub;
import com.hoangluongtran0309.releaseflow.support.LinearStub;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0041 through the release: each linked issue read again when review is requested, a note
 * that shows an open status only when a person accepts it, and folding a tracker change into a
 * code change of the same release.
 */
class LinkedIssuesAtReviewIntegrationTest extends PostgreSqlIntegrationTest {

    private static final JiraStub JIRA = JiraStub.start();
    private static final LinearStub LINEAR = LinearStub.start();
    private static final String TEAM = "11111111-1111-4111-8111-111111111111";
    private static final String WORKSPACE = "22222222-2222-4222-8222-222222222222";
    // An audience that prints each linked issue, its status only when there is one to show.
    private static final String TEMPLATE = "- {{whatChanged}}{{#linkedIssues}} [{{key}}]{{#status}} ({{status}}){{/status}}{{/linkedIssues}}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LinkedIssueReadWorker readWorker;

    @DynamicPropertySource
    static void providers(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.jira.api-base-url", JIRA::baseUrl);
        registry.add("releaseflow.jira.timeout", () -> "PT2S");
        registry.add("releaseflow.linear.api-base-url", LINEAR::baseUrl);
        registry.add("releaseflow.linear.timeout", () -> "PT2S");
    }

    @AfterAll
    static void stopStubs() {
        JIRA.close();
        LINEAR.close();
    }

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        JIRA.reset();
        LINEAR.reset();
        jdbcTemplate.execute("TRUNCATE public_changelog_entries, automation_action_runs, automation_runs, "
                + "automation_publish_jobs, automation_rule_actions, automation_rules, translation_jobs, "
                + "release_audience_notes, release_change_reviews, release_notes, release_changes, linked_issue_reads, "
                + "release_linked_issues, release_fold_decisions, releases");
        jdbcTemplate.update("DELETE FROM duplicate_candidates");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void eachLinkedIssueIsReadAtReviewAndANoteShowsAnOpenStatusOnlyOnceAccepted() throws Exception {
        Workspace workspace = workspace();
        UUID change = code(workspace, 1, "feat: export tables as CSV files", """
                [{"key":"APP-7","title":"Export tables","type":"Story","status":"In Progress","url":"https://acme.atlassian.net/browse/APP-7","tracker":"JIRA","statusCategory":"open"},
                 {"key":"APP-8","title":"Stream rows","type":"Task","status":"Done","url":"https://acme.atlassian.net/browse/APP-8"}]""");
        String release = draft(workspace, "1.0.0", change);

        send(workspace.admin(), post(release + "/request-review"), null).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM linked_issue_reads", String.class)).isEqualTo("PENDING");
        // As of review, APP-7 is finished and APP-8 has been reopened.
        JIRA.respondWithIssue("APP-7", "Export tables", "Story", "Done", "");
        JIRA.respondWithIssue("APP-8", "Stream rows", "Task", "In Progress", "");
        drainReads();

        send(workspace.admin(), get(release), null)
                .andExpect(jsonPath("$.linkedIssueRead").value("SUCCEEDED"))
                .andExpect(jsonPath("$.linkedIssues[0].key").value("APP-7"))
                .andExpect(jsonPath("$.linkedIssues[0].recordedStatus").value("In Progress"))
                .andExpect(jsonPath("$.linkedIssues[0].statusAtReview").value("Done"))
                .andExpect(jsonPath("$.linkedIssues[0].open").value(false))
                .andExpect(jsonPath("$.linkedIssues[1].key").value("APP-8"))
                .andExpect(jsonPath("$.linkedIssues[1].status").value("In Progress"))
                .andExpect(jsonPath("$.linkedIssues[1].open").value(true))
                .andExpect(jsonPath("$.linkedIssues[1].accepted").value(false));
        mockMvc.perform(get(release.substring("/api".length())).session(workspace.admin()))
                .andExpect(content().string(containsString("id=\"open-linked-issues\"")))
                .andExpect(content().string(containsString("Status: In Progress")))
                .andExpect(content().string(containsString("Show this status")));

        approveAll(workspace, release, change);
        assertThat(note(release)).contains("- export tables as CSV files [APP-7] (Done) [APP-8]\n");

        // A status read since the page was loaded is never accepted on the person's behalf.
        send(workspace.admin(), post(release + "/linked-issues/acceptance"),
                Map.of("tracker", "JIRA", "key", "APP-8", "status", "In Review"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("linked_issue_status_changed"));
        send(workspace.admin(), post(release + "/linked-issues/acceptance"),
                Map.of("tracker", "JIRA", "key", "APP-99", "status", "Done"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("linked_issue_not_found"));
        send(workspace.member(), post(release + "/linked-issues/acceptance"),
                Map.of("tracker", "JIRA", "key", "APP-8", "status", "In Progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedIssues[1].accepted").value(true))
                .andExpect(jsonPath("$.linkedIssues[1].accepterName").value("Member"));
        assertThat(note(release)).as("an approved release's notes follow the acceptance")
                .contains("- export tables as CSV files [APP-7] (Done) [APP-8] (In Progress)\n");

        send(workspace.admin(), post(release + "/publish"), null).andExpect(status().isOk());
        send(workspace.admin(), post(release + "/linked-issues/acceptance"),
                Map.of("tracker", "JIRA", "key", "APP-8", "status", "In Progress"))
                .andExpect(status().isConflict());
    }

    @Test
    void aReadThatFinishesAfterApprovalRendersTheNotesAgainAndAnUnreadIssueKeepsItsRecordedStatus() throws Exception {
        Workspace workspace = workspace();
        UUID change = code(workspace, 2, "feat: preview the retention policy", """
                [{"key":"APP-9","title":"Retention preview","type":"Story","status":"Done","url":"https://acme.atlassian.net/browse/APP-9","tracker":"JIRA","statusCategory":"done"},
                 {"key":"APP-10","title":"Retention docs","type":"Task","status":"In Progress","url":"https://acme.atlassian.net/browse/APP-10","tracker":"JIRA","statusCategory":"open"}]""");
        String release = draft(workspace, "1.1.0", change);
        send(workspace.admin(), post(release + "/request-review"), null).andExpect(status().isOk());
        approveAll(workspace, release, change);
        assertThat(note(release)).as("before the read, the recorded statuses").contains("- preview the retention policy [APP-9] (Done) [APP-10]\n");

        JIRA.respondWithIssue("APP-10", "Retention docs", "Task", "Done", "");
        JIRA.failIssue("APP-9");
        drainReads();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM linked_issue_reads", String.class)).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject("SELECT attempts FROM linked_issue_reads", Integer.class))
                .isEqualTo(LinkedIssueReadWorker.MAX_ATTEMPTS);
        assertThat(JIRA.issueRequests()).filteredOn(request -> request.path().endsWith("/APP-9"))
                .hasSize(LinkedIssueReadWorker.MAX_ATTEMPTS);
        assertThat(note(release)).as("the read after approval renders the notes again")
                .contains("- preview the retention policy [APP-9] (Done) [APP-10] (Done)\n");
        send(workspace.admin(), get(release), null)
                .andExpect(jsonPath("$.linkedIssueRead").value("FAILED"))
                .andExpect(jsonPath("$.linkedIssues[0].read").value(false))
                .andExpect(jsonPath("$.linkedIssues[1].read").value(true));
    }

    @Test
    void aTrackerChangeWhoseKeyACodeChangeHoldsIsSuggestedForFoldingAndRejectedOnAcceptance() throws Exception {
        Workspace workspace = workspace();
        UUID code = code(workspace, 21, "feat(schedule): Quartz backup schedules", """
                [{"key":"APP-12","title":"Quartz schedules","type":"Story","status":"Done","url":"https://acme.atlassian.net/browse/APP-12","tracker":"JIRA","statusCategory":"done"}]""");
        UUID tracker = tracker(workspace, "JIRA", "APP-12", "APP-12: Quartz schedules",
                "https://acme.atlassian.net/browse/APP-12");
        String release = draft(workspace, "0.2.0", code, tracker);
        send(workspace.admin(), post(release + "/request-review"), null)
                .andExpect(jsonPath("$.foldingSuggestions.length()").value(1))
                .andExpect(jsonPath("$.foldingSuggestions[0].trackerReference").value("APP-12"))
                .andExpect(jsonPath("$.foldingSuggestions[0].codeReference").value("#21"))
                .andExpect(jsonPath("$.foldingSuggestions[0].evidence").value("LINKED_KEY"));
        mockMvc.perform(get(release.substring("/api".length())).session(workspace.member()))
                .andExpect(content().string(containsString("id=\"folding-suggestions\"")))
                .andExpect(content().string(containsString("Fold APP-12 into #21")))
                .andExpect(content().string(containsString("#21 names APP-12 as a linked issue.")))
                .andExpect(content().string(not(containsString("Fold, and exclude APP-12 from releases"))));

        send(workspace.member(), put(release + "/folding-suggestions/" + tracker + "/decision"),
                Map.of("codeChangeId", code.toString(), "decision", "ACCEPT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_fold_decision"));
        send(workspace.member(), put(release + "/folding-suggestions/" + tracker + "/decision"),
                Map.of("codeChangeId", code.toString(), "decision", "ACCEPT", "removal", "REJECT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes.length()").value(1))
                .andExpect(jsonPath("$.foldingSuggestions.length()").value(0))
                .andExpect(jsonPath("$.foldDecisions[0].decision").value("ACCEPTED"))
                .andExpect(jsonPath("$.foldDecisions[0].removal").value("REJECTED"))
                .andExpect(jsonPath("$.foldDecisions[0].deciderName").value("Member"));
        assertThat(jdbcTemplate.queryForObject("SELECT excluded_at IS NULL FROM changes WHERE id = ?", Boolean.class, tracker))
                .as("rejected, so available again").isTrue();
        send(workspace.member(), put(release + "/folding-suggestions/" + tracker + "/decision"),
                Map.of("codeChangeId", code.toString(), "decision", "DISMISS"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("folding_suggestion_not_found"));
    }

    @Test
    void aConfirmedDuplicateIsSuggestedAndOnlyAnAdministratorExcludesWithAReason() throws Exception {
        Workspace workspace = workspace();
        UUID code = code(workspace, 30, "ci: rotate S3 credentials used by integration tests", null);
        UUID tracker = tracker(workspace, "LINEAR", "OPS-7", "Rotate S3 credentials",
                "https://linear.app/acme/issue/OPS-7/rotate-s3-credentials");
        UUID other = tracker(workspace, "JIRA", "APP-19", "APP-19: Document S3 profiles",
                "https://acme.atlassian.net/browse/APP-19");
        jdbcTemplate.update("""
                INSERT INTO duplicate_candidates (id, organization_id, project_id, change_id, duplicate_of_id, similarity,
                    evidence, status, decided_by, decider_name, created_at, decided_at)
                VALUES (?, ?, ?, ?, ?, 0.95, '{"title":0.9,"content":0.9,"paths":0}'::jsonb, 'CONFIRMED',
                    (SELECT id FROM app_users WHERE role = 'ADMIN'), 'Owner', now(), now())
                """, UUID.randomUUID(), workspace.organizationId(), workspace.projectId(), tracker, code);
        String release = draft(workspace, "0.2.0", code, tracker, other);
        send(workspace.admin(), post(release + "/request-review"), null)
                .andExpect(jsonPath("$.foldingSuggestions.length()").value(1))
                .andExpect(jsonPath("$.foldingSuggestions[0].evidence").value("CONFIRMED_DUPLICATE"))
                .andExpect(jsonPath("$.foldingSuggestions[0].confirmedBy").value("Owner"));

        Map<String, String> exclude = Map.of("codeChangeId", code.toString(), "decision", "ACCEPT", "removal", "EXCLUDE",
                "reason", "OPS-7 is tracked by #30, which carries the release-note entry.");
        send(workspace.member(), put(release + "/folding-suggestions/" + tracker + "/decision"), exclude)
                .andExpect(status().isForbidden());
        send(workspace.admin(), put(release + "/folding-suggestions/" + tracker + "/decision"),
                Map.of("codeChangeId", code.toString(), "decision", "ACCEPT", "removal", "EXCLUDE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_fold_decision"));
        send(workspace.admin(), put(release + "/folding-suggestions/" + tracker + "/decision"),
                Map.of("codeChangeId", code.toString(), "decision", "ACCEPT", "removal", "EXCLUDE", "reason", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_fold_decision"));
        send(workspace.admin(), put(release + "/folding-suggestions/" + tracker + "/decision"), exclude)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes.length()").value(2))
                .andExpect(jsonPath("$.foldDecisions[0].removal").value("EXCLUDED"));

        assertThat(jdbcTemplate.queryForObject("SELECT exclusion_reason FROM changes WHERE id = ?", String.class, tracker))
                .isEqualTo("OPS-7 is tracked by #30, which carries the release-note entry.");
        String linked = jdbcTemplate.queryForObject("SELECT linked_issues::text FROM changes WHERE id = ?", String.class, code);
        assertThat(JsonPath.<List<String>>read(linked, "$[*].key")).containsExactly("OPS-7");
        assertThat(JsonPath.<List<String>>read(linked, "$[*].tracker")).containsExactly("LINEAR");
        assertThat(JsonPath.<List<String>>read(linked, "$[*].statusCategory")).containsExactly("done");
        assertThat(jdbcTemplate.queryForObject("SELECT linked_context_status FROM changes WHERE id = ?", String.class, code))
                .isEqualTo("COLLECTED");
    }

    @Test
    void aDismissedSuggestionIsNotOfferedAgainAndNothingIsFolded() throws Exception {
        Workspace workspace = workspace();
        UUID code = code(workspace, 61, "feat: sign-in throttling", """
                [{"key":"APP-48","title":"Throttle sign-ins","type":"Story","status":"Done","url":"https://acme.atlassian.net/browse/APP-48","tracker":"JIRA","statusCategory":"done"}]""");
        UUID tracker = tracker(workspace, "JIRA", "APP-48", "APP-48: Throttle sign-ins",
                "https://acme.atlassian.net/browse/APP-48");
        String release = draft(workspace, "1.2.0", code, tracker);
        send(workspace.admin(), post(release + "/request-review"), null).andExpect(status().isOk());

        mockMvc.perform(post(release.substring("/api".length()) + "/folding-suggestions/" + tracker + "/decision")
                        .session(workspace.admin()).with(csrf())
                        .param("codeChangeId", code.toString())
                        .param("decision", "DISMISS"))
                .andExpect(redirectedUrl(release.substring("/api".length())));
        send(workspace.admin(), get(release), null)
                .andExpect(jsonPath("$.changes.length()").value(2))
                .andExpect(jsonPath("$.foldingSuggestions.length()").value(0))
                .andExpect(jsonPath("$.foldDecisions[0].decision").value("DISMISSED"))
                .andExpect(jsonPath("$.foldDecisions[0].removal").doesNotExist());
        mockMvc.perform(get(release.substring("/api".length())).session(workspace.admin()))
                .andExpect(content().string(not(containsString("id=\"folding-suggestions\""))));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM release_fold_decisions", Integer.class)).isEqualTo(1);
    }

    private void drainReads() {
        do {
            jdbcTemplate.update("UPDATE linked_issue_reads SET next_attempt_at = now() - interval '1 second' WHERE status = 'PENDING'");
        } while (readWorker.processOne());
    }

    private void approveAll(Workspace workspace, String release, UUID... changes) throws Exception {
        for (UUID change : changes) {
            send(workspace.admin(), put(release + "/changes/" + change + "/decision"),
                    Map.of("action", "APPROVE", "category", "FEATURE", "breaking", false, "security", false))
                    .andExpect(status().isOk());
        }
        send(workspace.admin(), post(release + "/approve"), null).andExpect(status().isOk());
    }

    // The note of the audience that prints linked issues.
    private String note(String release) {
        return jdbcTemplate.queryForObject("""
                SELECT n.content FROM release_audience_notes n JOIN audience_definitions a ON a.id = n.audience_id
                WHERE a.code = 'tracking' AND n.release_id = ?
                """, String.class, UUID.fromString(release.substring(release.lastIndexOf('/') + 1)));
    }

    private String draft(Workspace workspace, String version, UUID... changes) throws Exception {
        String created = send(workspace.admin(), post("/api/projects/" + workspace.projectId() + "/releases"),
                Map.of("version", version)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String release = "/api/projects/" + workspace.projectId() + "/releases/" + JsonPath.read(created, "$.id");
        send(workspace.admin(), post(release + "/changes"), Map.of("changeIds", List.of(changes))).andExpect(status().isOk());
        return release;
    }

    private UUID code(Workspace workspace, int number, String title, String linkedIssues) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO changes
                    (id, organization_id, project_id, source_id, source_type, external_id, origin, pull_request_number,
                     title, author_login, labels, target_branch, merge_commit_sha, merged_at, url, received_at,
                     category, category_display_name, category_group, breaking, needs_review, classification_reasons,
                     classification_source, ai_status, processing_status, changed_file_status, review_triggers,
                     linked_context_status, linked_issues)
                VALUES (?, ?, ?, ?, 'GITHUB', ?, 'IMPORT', ?, ?, 'mai-dev', '{}', 'main', ?, now(), ?, now(),
                        'FEATURE', 'Feature', 'FEATURE', false, true, '{}', 'RULES', 'NOT_REQUESTED', 'COMPLETED',
                        'UNAVAILABLE', '[]', ?, ?::jsonb)
                """, id, workspace.organizationId(), workspace.projectId(), workspace.github(), "#" + number, number, title,
                "%040d".formatted(number), "https://github.com/acme/app/pull/" + number,
                linkedIssues == null ? "NOT_FOUND" : "COLLECTED", linkedIssues);
        return id;
    }

    private UUID tracker(Workspace workspace, String type, String key, String title, String url) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO changes
                    (id, organization_id, project_id, source_id, source_type, external_id, origin, pull_request_number,
                     title, labels, merged_at, url, received_at, category, category_display_name, category_group,
                     breaking, needs_review, classification_reasons, classification_source, ai_status, processing_status,
                     changed_file_status, review_triggers, linked_context_status)
                VALUES (?, ?, ?, ?, ?, ?, 'IMPORT', ?, ?, '{}', now(), ?, now(), 'FEATURE', 'Feature', 'FEATURE', false,
                        true, '{}', 'RULES', 'NOT_REQUESTED', 'COMPLETED', 'NOT_SUPPORTED', '[]', 'NOT_SUPPORTED')
                """, id, workspace.organizationId(), workspace.projectId(),
                type.equals("JIRA") ? workspace.jira() : workspace.linear(), type, key, "LINEAR".equals(type) ? 7 : Integer.parseInt(key.substring(key.indexOf('-') + 1)),
                title, url);
        return id;
    }

    // An Organization with a GitHub, a Jira, and a Linear source, and an audience that prints linked issues.
    private Workspace workspace() throws Exception {
        RegistrationRequest registration = new RegistrationRequest();
        registration.setOrganizationName("Acme");
        registration.setDisplayName("Owner");
        registration.setEmail("owner@acme.test");
        registration.setPassword("owner-password");
        UUID organizationId = registrationService.register(registration).organizationId();
        MockHttpSession admin = login("owner@acme.test", "owner-password");
        jdbcTemplate.update("""
                INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                VALUES (?, ?, 'member@acme.test', ?, 'Member', 'MEMBER', now())
                """, UUID.randomUUID(), organizationId, passwordEncoder.encode("member-password"));
        MockHttpSession member = login("member@acme.test", "member-password");

        UUID projectId = UUID.fromString(JsonPath.read(send(admin, post("/api/projects"), Map.of("name", "App"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id"));
        String sources = "/api/projects/" + projectId + "/sources";
        UUID github = id(send(admin, post(sources), Map.of("type", "GITHUB", "owner", "acme", "repository", "app")));
        UUID jira = id(send(admin, post(sources), Map.of("type", "JIRA", "siteUrl", JiraStub.SITE, "projectKey", "APP",
                "accountEmail", "bot@acme.test", "apiToken", "jira-review-token")));
        LINEAR.respondWithTeam(TEAM, WORKSPACE, "OPS");
        UUID linear = id(send(admin, post(sources), Map.of("type", "LINEAR", "teamId", TEAM,
                "webhookSecret", "lin_wh_review-test", "apiToken", "lin_api_review-test")));
        send(admin, post("/api/audiences"), Map.of("code", "tracking", "displayName", "Tracking",
                "communicationIntent", "", "templateBody", TEMPLATE)).andExpect(status().isCreated());
        JIRA.reset();
        LINEAR.reset();
        return new Workspace(organizationId, projectId, github, jira, linear, admin, member);
    }

    private static UUID id(ResultActions created) throws Exception {
        return UUID.fromString(JsonPath.read(created.andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.id"));
    }

    private ResultActions send(MockHttpSession session, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.session(session).with(csrf());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(new tools.jackson.databind.ObjectMapper().writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login").with(csrf()).param("email", email).param("password", password))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private record Workspace(UUID organizationId, UUID projectId, UUID github, UUID jira, UUID linear,
                             MockHttpSession admin, MockHttpSession member) {
    }
}
