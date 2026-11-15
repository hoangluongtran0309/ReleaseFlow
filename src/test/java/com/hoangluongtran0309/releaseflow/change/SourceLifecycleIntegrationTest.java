package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.configuration.UiNotice;
import com.hoangluongtran0309.releaseflow.project.SourceAccess;
import com.hoangluongtran0309.releaseflow.support.GitHubStub;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.hoangluongtran0309.releaseflow.support.GitHubStub.closedPullRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Disconnecting, reconnecting, and rotating a GitHub source, and its health (ADR-0042): the
 * three things issue #21 is done by, and what surrounds them.
 */
class SourceLifecycleIntegrationTest extends PostgreSqlIntegrationTest {

    private static final GitHubStub GITHUB = GitHubStub.start();
    private static final String TOKEN = "github_pat_lifecycle-test";
    private static final String BOUND_COLUMNS = "SELECT organization_id, project_id, id, source_type, "
            + "external_project_key, repository_owner, repository_name FROM integration_sources WHERE id = ?";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SourceSyncWorker syncWorker;

    @Autowired
    private SourceAccess sourceAccess;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void gitHubProperties(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.github.api-base-url", GITHUB::baseUrl);
        registry.add("releaseflow.github.timeout", () -> "PT2S");
    }

    @AfterAll
    static void stopStub() {
        GITHUB.close();
    }

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        GITHUB.reset();
        jdbcTemplate.execute("TRUNCATE public_changelog_entries, automation_action_runs, automation_runs, automation_publish_jobs, automation_rule_actions, automation_rules, translation_jobs, release_audience_notes, release_change_reviews, release_notes, release_changes, linked_issue_reads, release_linked_issues, release_fold_decisions, releases");
        jdbcTemplate.update("DELETE FROM change_processing_jobs");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM source_sync_jobs");
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void aDisconnectedSourcesPathAnswersLikeAnUnknownOneAndItsChangesStay() throws Exception {
        Admin admin = connect("owner@example.com");
        deliver(admin, admin.webhookPath(), admin.secret(), 1).andExpect(jsonPath("$.outcome").value("recorded"));
        // An import queued before the disconnect ends with it.
        startImport(admin).andExpect(status().isAccepted());
        Map<String, Object> bound = jdbcTemplate.queryForMap(BOUND_COLUMNS, admin.sourceId());

        mockMvc.perform(post("/api/projects/{projectId}/sources/{sourceId}/disconnect", admin.projectId(), admin.sourceId())
                        .session(admin.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.health.connected").value(false))
                .andExpect(jsonPath("$.health.disconnectedAt").isNotEmpty())
                .andExpect(jsonPath("$.webhookPath").value(nullValue()))
                .andExpect(jsonPath("$.accessTokenConfigured").value(false));

        String known = deliver(admin, admin.webhookPath(), admin.secret(), 2)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("webhook_signature_invalid"))
                .andReturn().getResponse().getContentAsString();
        String unknownPath = "/webhooks/github/" + UUID.randomUUID();
        String unknown = deliver(admin, unknownPath, admin.secret(), 2)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        // The same answer, but for the address each was sent to.
        assertThat(known.replace(admin.webhookPath(), "{path}")).isEqualTo(unknown.replace(unknownPath, "{path}"));

        // Nothing that decrypts to a credential is left, nor any schedule; the identity is.
        assertThat(jdbcTemplate.queryForMap("SELECT webhook_id, secret_ciphertext, token_ciphertext, "
                + "signature_failure_count FROM integration_sources WHERE id = ?", admin.sourceId()))
                .containsEntry("webhook_id", null)
                .containsEntry("secret_ciphertext", null)
                .containsEntry("token_ciphertext", null)
                .containsEntry("signature_failure_count", 0L);
        assertThat(jdbcTemplate.queryForMap(BOUND_COLUMNS, admin.sourceId())).isEqualTo(bound);
        assertThat(sourceAccess.find(admin.organizationId(), admin.projectId(), admin.sourceId())).isEmpty();
        assertThat(jdbcTemplate.queryForMap("SELECT status, last_error FROM source_sync_jobs"))
                .containsEntry("status", "FAILED")
                .containsEntry("last_error", "source_disconnected");
        assertThat(syncWorker.processOne()).isFalse();
        assertThat(GITHUB.listRequests()).isEmpty();

        mockMvc.perform(get("/api/projects/{projectId}/changes", admin.projectId()).session(admin.session()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("feat: change 1"));
        mockMvc.perform(get("/changes").param("project", admin.projectId().toString()).session(admin.session()))
                .andExpect(content().string(containsString("feat: change 1")))
                .andExpect(content().string(containsString("Disconnected")));
        mockMvc.perform(get("/").session(admin.session()))
                .andExpect(content().string(containsString("Disconnected")))
                .andExpect(content().string(containsString("Connect a source")));

        startImport(admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("source_disconnected"));
        mockMvc.perform(put("/api/projects/{projectId}/sources/{sourceId}/token", admin.projectId(), admin.sourceId())
                        .session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"%s\"}".formatted(TOKEN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("source_disconnected"));
        generateSecret(admin).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("source_disconnected"));
        mockMvc.perform(post("/api/projects/{projectId}/sources/{sourceId}/disconnect", admin.projectId(), admin.sourceId())
                        .session(admin.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("source_disconnected"));
    }

    @Test
    void reconnectingTheSameRepositoryKeepsItsIdAndTheImportAddsNoDuplicates() throws Exception {
        Admin admin = connect("owner@example.com");
        deliver(admin, admin.webhookPath(), admin.secret(), 1).andExpect(jsonPath("$.outcome").value("recorded"));
        deliver(admin, admin.webhookPath(), admin.secret(), 2).andExpect(jsonPath("$.outcome").value("recorded"));
        Map<String, Object> bound = jdbcTemplate.queryForMap(BOUND_COLUMNS, admin.sourceId());
        disconnect(admin);

        // Connecting the same repository again would give it a second identity.
        mockMvc.perform(post("/api/projects/{projectId}/sources", admin.projectId())
                        .session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GITHUB\",\"owner\":\"acme\",\"repository\":\"releaseflow\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("source_reconnect_required"));

        String reconnected = reconnect(admin, "{\"token\":\"%s\"}".formatted(TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.id").value(admin.sourceId().toString()))
                .andExpect(jsonPath("$.webhookSecret").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String newPath = JsonPath.read(reconnected, "$.webhookPath");
        String newSecret = JsonPath.read(reconnected, "$.webhookSecret");
        assertThat(newPath).isNotEqualTo(admin.webhookPath());
        assertThat(newSecret).isNotEqualTo(admin.secret());
        // The bound columns were never rewritten, so the new credentials decrypt under them.
        assertThat(jdbcTemplate.queryForMap(BOUND_COLUMNS, admin.sourceId())).isEqualTo(bound);
        assertThat(sourceAccess.find(admin.organizationId(), admin.projectId(), admin.sourceId()).orElseThrow()
                .accessToken()).contains(TOKEN);

        // The old path stays dead; the new one proves itself with the new secret.
        deliver(admin, admin.webhookPath(), newSecret, 3).andExpect(status().isUnauthorized());
        deliver(admin, newPath, newSecret, 2).andExpect(jsonPath("$.outcome").value("duplicate"));

        Instant now = Instant.now();
        GITHUB.respondWithPullRequests(List.of(
                closedPullRequest(3, now.minus(Duration.ofHours(3)), now.minus(Duration.ofHours(3))),
                closedPullRequest(2, now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(2))),
                closedPullRequest(1, now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(2)))
        ));
        startImport(admin).andExpect(status().isAccepted());
        assertThat(syncWorker.processOne()).isTrue();

        mockMvc.perform(get("/api/projects/{projectId}/imports", admin.projectId()).session(admin.session()))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].scannedCount").value(3))
                .andExpect(jsonPath("$[0].importedCount").value(1));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM changes", Long.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT source_id) FROM changes", Long.class)).isEqualTo(1);

        reconnect(admin, "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("source_not_disconnected"));
    }

    @Test
    void anotherProjectMayConnectARepositoryOnceItIsDisconnected() throws Exception {
        Admin admin = connect("owner@example.com");
        disconnect(admin);
        UUID otherProject = createProject(admin.session(), "Other");

        String created = mockMvc.perform(post("/api/projects/{projectId}/sources", otherProject)
                        .session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GITHUB\",\"owner\":\"acme\",\"repository\":\"releaseflow\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(created, "$.id")).isNotEqualTo(admin.sourceId().toString());

        // Now the repository is the other Project's, so the first cannot take it back.
        reconnect(admin, "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("github_repository_already_connected"));
    }

    @Test
    void rotatingTheSecretKeepsThePathAndStopsTheOldOneAtOnce() throws Exception {
        Admin admin = connect("owner@example.com");
        // A delivery signed with something else is counted, never stored.
        deliver(admin, admin.webhookPath(), "not-the-secret", 1).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/projects/{projectId}/sources/{sourceId}", admin.projectId(), admin.sourceId())
                        .session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.health.signatureFailureCount").value(1))
                .andExpect(jsonPath("$.health.lastSignatureFailureAt").isNotEmpty());

        String rotated = generateSecret(admin)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.webhookPath").value(admin.webhookPath()))
                .andReturn().getResponse().getContentAsString();
        String newSecret = JsonPath.read(rotated, "$.webhookSecret");
        assertThat(newSecret).isNotBlank().isNotEqualTo(admin.secret());

        deliver(admin, admin.webhookPath(), admin.secret(), 1).andExpect(status().isUnauthorized());
        deliver(admin, admin.webhookPath(), newSecret, 1).andExpect(jsonPath("$.outcome").value("recorded"));

        // The count restarted with the new secret: only the old secret's one refusal since.
        mockMvc.perform(get("/api/projects/{projectId}/sources/{sourceId}", admin.projectId(), admin.sourceId())
                        .session(admin.session()))
                .andExpect(jsonPath("$.health.signatureFailureCount").value(1))
                .andExpect(jsonPath("$.health.secretUpdatedAt").isNotEmpty())
                .andExpect(content().string(not(containsString(newSecret))));
        mockMvc.perform(get("/api/projects").session(admin.session()))
                .andExpect(content().string(not(containsString(newSecret))));

        // GitHub's secret is only ever generated here.
        mockMvc.perform(put("/api/projects/{projectId}/sources/{sourceId}/webhook-secret", admin.projectId(), admin.sourceId())
                        .session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"webhookSecret\":\"pasted\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("source_secret_generated"));
    }

    @Test
    void aSourceQuietForLongerThanItsOwnRhythmIsFlagged() throws Exception {
        Admin admin = connect("owner@example.com");
        for (int number = 1; number <= 12; number++) {
            deliver(admin, admin.webhookPath(), admin.secret(), number).andExpect(jsonPath("$.outcome").value("recorded"));
        }
        UUID rare = createSource(admin, "releaseflow-docs");
        // Twelve changes an hour apart, the last of them five hours ago: eleven gaps of an hour.
        jdbcTemplate.update("UPDATE changes SET received_at = now() - interval '5 hours' "
                + "- (13 - pull_request_number) * interval '1 hour' WHERE source_id = ?", admin.sourceId());
        jdbcTemplate.update("UPDATE integration_sources SET last_delivery_at = now() - interval '5 hours', "
                + "secret_updated_at = now() - interval '30 days'");

        mockMvc.perform(get("/api/projects/{projectId}/sources", admin.projectId()).session(admin.session()))
                .andExpect(jsonPath("$[0].health.quiet").value(true))
                .andExpect(jsonPath("$[0].health.usualGapHours").value(1))
                .andExpect(jsonPath("$[0].health.quietSince").isNotEmpty())
                // A source with too few deliveries to have a rhythm is never flagged for being rare.
                .andExpect(jsonPath("$[1].id").value(rare.toString()))
                .andExpect(jsonPath("$[1].health.quiet").value(false))
                .andExpect(jsonPath("$[1].health.usualGapHours").value(nullValue()));
        mockMvc.perform(get("/changes").param("project", admin.projectId().toString()).session(admin.session()))
                .andExpect(content().string(containsString("Quiet")))
                .andExpect(content().string(containsString("about 1 hour.")));
        mockMvc.perform(get("/").session(admin.session()))
                .andExpect(content().string(containsString("Quiet")));

        // A delivery ends the silence.
        deliver(admin, admin.webhookPath(), admin.secret(), 13).andExpect(jsonPath("$.outcome").value("recorded"));
        mockMvc.perform(get("/api/projects/{projectId}/sources", admin.projectId()).session(admin.session()))
                .andExpect(jsonPath("$[0].health.quiet").value(false));
    }

    @Test
    void theSourcePageDisconnectsAndReconnectsAfterSayingWhatHappens() throws Exception {
        Admin admin = connect("owner@example.com");
        MockHttpSession member = member(admin.organizationId());
        String page = "/projects/" + admin.projectId() + "/sources/" + admin.sourceId();

        mockMvc.perform(get(page).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Generate a new secret")))
                .andExpect(content().string(containsString("Review and disconnect")));
        mockMvc.perform(get(page).session(member))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Health")))
                .andExpect(content().string(not(containsString("Review and disconnect"))));
        mockMvc.perform(get(page + "/disconnect").session(member)).andExpect(status().isForbidden());
        mockMvc.perform(post(page + "/disconnect").session(member).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post(page + "/webhook-secret/generate").session(member).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post(page + "/reconnect").session(member).with(csrf())).andExpect(status().isForbidden());

        mockMvc.perform(get(page + "/disconnect").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("What happens")))
                .andExpect(content().string(containsString("delete the hook")));
        mockMvc.perform(post(page + "/webhook-secret/generate").session(admin.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(containsString("Recent deliveries")))
                .andExpect(content().string(containsString(admin.webhookPath())));

        mockMvc.perform(post(page + "/disconnect").session(admin.session()).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(page))
                .andExpect(flash().attribute(UiNotice.FLASH_ATTRIBUTE, UiNotice.success("ui.source.disconnectedNotice")));
        mockMvc.perform(get(page).session(admin.session()))
                .andExpect(content().string(containsString("Disconnected")))
                .andExpect(content().string(containsString("Reconnect")))
                .andExpect(content().string(not(containsString("Generate a new secret"))));
        mockMvc.perform(get("/projects").session(admin.session()))
                .andExpect(content().string(containsString("Reconnect source")))
                .andExpect(content().string(containsString("Not connected")));

        // The connect form points at the reconnect rather than giving the repository a second identity.
        mockMvc.perform(post("/projects/{projectId}/sources", admin.projectId()).session(admin.session()).with(csrf())
                        .param("type", "GITHUB").param("owner", "acme").param("repository", "releaseflow"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Reconnect it from its page")));
        // A disconnected source takes no token, even from a stale form.
        mockMvc.perform(post(page + "/token").session(admin.session()).with(csrf()).param("token", TOKEN))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("This source is disconnected.")));

        GITHUB.failAccessCheck(401);
        mockMvc.perform(post(page + "/reconnect").session(admin.session()).with(csrf()).param("token", "github_pat_refused"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("GitHub did not accept this token")))
                .andExpect(content().string(not(containsString("github_pat_refused"))));
        GITHUB.reset();
        mockMvc.perform(post(page + "/reconnect").session(admin.session()).with(csrf()).param("token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(containsString("GitHub reconnected")))
                .andExpect(content().string(containsString("history import")));
    }

    private ResultActions deliver(Admin admin, String path, String secret, int number) throws Exception {
        String body = """
                {"action":"closed","number":%d,"pull_request":{"number":%d,"title":"feat: change %d","body":null,\
                "merged":true,"merged_at":"%s",\
                "merge_commit_sha":"%040x",\
                "html_url":"https://github.com/acme/releaseflow/pull/%d","user":{"login":"mai-dev"},\
                "base":{"ref":"main"},"labels":[]},"repository":{"full_name":"acme/releaseflow"}}"""
                .formatted(number, number, number, Instant.now().minus(Duration.ofDays(2)), number, number);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.getBytes(StandardCharsets.UTF_8))
                .header("X-GitHub-Event", "pull_request")
                .header("X-GitHub-Delivery", UUID.randomUUID().toString())
                .header("X-Hub-Signature-256", signature));
    }

    private ResultActions startImport(Admin admin) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/sources/{sourceId}/imports", admin.projectId(),
                admin.sourceId()).session(admin.session()).with(csrf()));
    }

    private ResultActions generateSecret(Admin admin) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/sources/{sourceId}/webhook-secret", admin.projectId(),
                admin.sourceId()).session(admin.session()).with(csrf()));
    }

    private ResultActions reconnect(Admin admin, String body) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/sources/{sourceId}/reconnect", admin.projectId(),
                        admin.sourceId())
                .session(admin.session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void disconnect(Admin admin) throws Exception {
        mockMvc.perform(post("/api/projects/{projectId}/sources/{sourceId}/disconnect", admin.projectId(),
                        admin.sourceId()).session(admin.session()).with(csrf()))
                .andExpect(status().isOk());
    }

    // Registers an administrator with a Project, repository acme/releaseflow, and an access token.
    private Admin connect(String email) throws Exception {
        RegistrationRequest registration = new RegistrationRequest();
        registration.setOrganizationName(email);
        registration.setDisplayName("Owner");
        registration.setEmail(email);
        registration.setPassword("owner-password");
        RegistrationResult registered = registrationService.register(registration);
        MockHttpSession session = login(email, "owner-password");
        UUID projectId = createProject(session, "ReleaseFlow");
        String source = mockMvc.perform(post("/api/projects/{projectId}/sources", projectId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GITHUB\",\"owner\":\"acme\",\"repository\":\"releaseflow\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Admin admin = new Admin(registered.organizationId(), projectId, UUID.fromString(JsonPath.read(source, "$.id")),
                JsonPath.read(source, "$.webhookPath"), JsonPath.read(source, "$.webhookSecret"), session);
        mockMvc.perform(put("/api/projects/{projectId}/sources/{sourceId}/token", projectId, admin.sourceId())
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(TOKEN)))
                .andExpect(status().isNoContent());
        return admin;
    }

    private UUID createProject(MockHttpSession session, String name) throws Exception {
        MvcResult project = mockMvc.perform(post("/api/projects").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(project.getResponse().getContentAsString(), "$.id"));
    }

    private UUID createSource(Admin admin, String repository) throws Exception {
        String source = mockMvc.perform(post("/api/projects/{projectId}/sources", admin.projectId())
                        .session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"acme\",\"repository\":\"%s\"}".formatted(repository)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(source, "$.id"));
    }

    private MockHttpSession member(UUID organizationId) throws Exception {
        jdbcTemplate.update(
                """
                        INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, 'member@example.com', ?, 'Member', 'MEMBER', now())
                        """,
                UUID.randomUUID(), organizationId, passwordEncoder.encode("member-password")
        );
        return login("member@example.com", "member-password");
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login").with(csrf()).param("email", email).param("password", password))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private record Admin(
            UUID organizationId,
            UUID projectId,
            UUID sourceId,
            String webhookPath,
            String secret,
            MockHttpSession session
    ) {
    }
}
