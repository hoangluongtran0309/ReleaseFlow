package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.GitLabStub;
import com.hoangluongtran0309.releaseflow.support.JiraStub;
import com.hoangluongtran0309.releaseflow.support.LinearStub;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A new webhook secret, a reconnect, and a new token for the providers other than GitHub,
 * the counting of refused deliveries under a flood (ADR-0042), and the proof a refused
 * Linear delivery names in the log (ADR-0018, amended 2026-11-19).
 */
@ExtendWith(OutputCaptureExtension.class)
class SourceRotationIntegrationTest extends PostgreSqlIntegrationTest {

    private static final LinearStub LINEAR = LinearStub.start();
    private static final GitLabStub GITLAB = GitLabStub.start();
    private static final JiraStub JIRA = JiraStub.start();
    private static final String TEAM = "11111111-1111-4111-8111-111111111111";
    private static final String WORKSPACE = "22222222-2222-4222-8222-222222222222";
    private static final String LINEAR_SECRET = "lin_wh_first-secret";
    private static final String LINEAR_TOKEN = "lin_api_rotation-test";
    private static final String JIRA_TOKEN = "jira-token-rotation";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private LinearWebhookVerifier linearVerifier;

    @Autowired
    private GitLabWebhookVerifier gitLabVerifier;

    @Autowired
    private GitHubWebhookVerifier gitHubVerifier;

    @Autowired
    private SignatureFailureRecorder failures;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.linear.api-base-url", LINEAR::baseUrl);
        registry.add("releaseflow.linear.timeout", () -> "PT2S");
        registry.add("releaseflow.gitlab.allowed-hosts", () -> "gitlab.com," + GITLAB.allowedOrigin());
        registry.add("releaseflow.gitlab.timeout", () -> "PT2S");
        registry.add("releaseflow.jira.api-base-url", JIRA::baseUrl);
        registry.add("releaseflow.jira.timeout", () -> "PT2S");
    }

    @AfterAll
    static void stopStubs() {
        LINEAR.close();
        GITLAB.close();
        JIRA.close();
    }

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        LINEAR.reset();
        GITLAB.reset();
        JIRA.reset();
        failures.drain();
        jdbcTemplate.update("DELETE FROM source_sync_jobs");
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void aLinearSecretIsPastedInAndNeverEchoed() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        Connected linear = connectLinear(owner);
        String next = "lin_wh_second-secret";

        generateSecret(owner, linear.id())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("source_secret_pasted"));
        pasteSecret(owner, linear.id(), next)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(verifyLinear(linear.webhookId(), LINEAR_SECRET, "d-1"))
                .isEmpty();
        assertThat(verifyLinear(linear.webhookId(), next, "d-2"))
                .isPresent();
        failures.drain();
        assertThat(failureCount(linear.id())).isEqualTo(1);
    }

    @Test
    void aLinearTeamIsReconnectedWithItsSecretAndAKeyThatStillReachesItsWorkspace() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        Connected linear = connectLinear(owner);
        disconnect(owner, linear.id());

        reconnect(owner, linear.id(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("source_credential_required"))
                .andExpect(jsonPath("$.field").value("webhookSecret"));
        reconnect(owner, linear.id(), "{\"webhookSecret\":\"lin_wh_new\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("token"));
        assertThat(LINEAR.requests()).isEmpty();

        LINEAR.respondWithTeam(TEAM, "33333333-3333-4333-8333-333333333333");
        reconnect(owner, linear.id(), "{\"webhookSecret\":\"lin_wh_new\",\"token\":\"%s\"}".formatted(LINEAR_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("linear_token_rejected"));

        LINEAR.respondWithTeam(TEAM, WORKSPACE);
        String reconnected = reconnect(owner, linear.id(),
                "{\"webhookSecret\":\"lin_wh_new\",\"token\":\"%s\"}".formatted(LINEAR_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(linear.id().toString()))
                .andExpect(jsonPath("$.webhookSecret").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        String webhookId = ((String) JsonPath.read(reconnected, "$.webhookPath")).substring("/webhooks/linear/".length());
        assertThat(webhookId).isNotEqualTo(linear.webhookId());
        assertThat(verifyLinear(webhookId, "lin_wh_new", "d-3")).isPresent();
        assertThat(verifyLinear(linear.webhookId(), "lin_wh_new", "d-4"))
                .isEmpty();
    }

    @Test
    void aSignedGitLabSourceTakesEitherReleaseFlowsTokenOrOneGitLabGenerated() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        Connected gitLab = connectGitLab(owner, "acme/app", "GITLAB_SIGNING_TOKEN");

        String rotated = generateSecret(owner, gitLab.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signingToken").value(startsWith("whsec_")))
                .andReturn().getResponse().getContentAsString();
        String generated = JsonPath.read(rotated, "$.signingToken");
        assertThat(gitLabVerifier.verify(gitLab.webhookId(), signed(generated), "{}".getBytes(StandardCharsets.UTF_8)))
                .isPresent();

        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        String fromGitLab = "whsec_" + Base64.getEncoder().encodeToString(key);
        pasteSecret(owner, gitLab.id(), fromGitLab).andExpect(status().isNoContent());
        assertThat(gitLabVerifier.verify(gitLab.webhookId(), signed(fromGitLab), "{}".getBytes(StandardCharsets.UTF_8)))
                .isPresent();
        assertThat(gitLabVerifier.verify(gitLab.webhookId(), signed(generated), "{}".getBytes(StandardCharsets.UTF_8)))
                .isEmpty();

        pasteSecret(owner, gitLab.id(), "not-a-signing-token")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("source_signing_token_invalid"))
                .andExpect(jsonPath("$.field").value("webhookSecret"));

        // The token header compares ReleaseFlow's own secret, so that one is only generated.
        Connected tokenHeader = connectGitLab(owner, "acme/other", "GITLAB_SECRET_TOKEN");
        pasteSecret(owner, tokenHeader.id(), fromGitLab)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("source_secret_generated"));
        generateSecret(owner, tokenHeader.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signingToken").value(nullValue()))
                .andExpect(jsonPath("$.webhookSecret").isNotEmpty());
    }

    @Test
    void aJiraSourceChangesAccountAndResumesNoFurtherBackThanAnImport() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID jira = connectJira(owner);

        generateSecret(owner, jira).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("source_has_no_webhook"));
        pasteSecret(owner, jira, "anything").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("source_has_no_webhook"));

        // A new token from another account, confirmed together with it.
        mockMvc.perform(put("/api/projects/{projectId}/sources/{sourceId}/token", owner.projectId(), jira)
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"jira-token-2\",\"accountEmail\":\"bot@acme.test\"}"))
                .andExpect(status().isNoContent());
        assertThat(JIRA.requests()).singleElement()
                .satisfies(request -> assertThat(request.authorization()).isEqualTo("Basic "
                        + Base64.getEncoder().encodeToString("bot@acme.test:jira-token-2".getBytes(StandardCharsets.UTF_8))));
        assertThat(jdbcTemplate.queryForObject("SELECT credential_identity FROM integration_sources WHERE id = ?",
                String.class, jira)).isEqualTo("bot@acme.test");

        disconnect(owner, jira);
        assertThat(jdbcTemplate.queryForMap("SELECT next_poll_at, poll_cursor_at FROM integration_sources WHERE id = ?",
                jira)).containsEntry("next_poll_at", null).extractingByKey("poll_cursor_at").isNotNull();
        jdbcTemplate.update("UPDATE integration_sources SET poll_cursor_at = now() - interval '200 days' WHERE id = ?", jira);

        reconnect(owner, jira, "{\"token\":\"%s\"}".formatted(JIRA_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("accountEmail"));
        reconnect(owner, jira, "{\"accountEmail\":\"ops@acme.test\",\"token\":\"%s\"}".formatted(JIRA_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webhookPath").value(nullValue()))
                .andExpect(jsonPath("$.credentialIdentity").value("ops@acme.test"));

        Map<String, Object> schedule = jdbcTemplate.queryForMap(
                "SELECT poll_cursor_at, next_poll_at FROM integration_sources WHERE id = ?", jira);
        Instant cursor = ((Timestamp) schedule.get("poll_cursor_at")).toInstant();
        assertThat(cursor).isCloseTo(Instant.now().minus(Duration.ofDays(90)), within(Duration.ofMinutes(5)));
        assertThat(((Timestamp) schedule.get("next_poll_at")).toInstant()).isAfter(Instant.now());

        // A read more than one interval late is flagged.
        jdbcTemplate.update("UPDATE integration_sources SET next_poll_at = now() - interval '1 day' WHERE id = ?", jira);
        mockMvc.perform(get("/api/projects/{projectId}/sources/{sourceId}", owner.projectId(), jira).session(owner.session()))
                .andExpect(jsonPath("$.health.overdue").value(true))
                .andExpect(jsonPath("$.health.quiet").value(false));
    }

    @Test
    void aFloodOfForgedDeliveriesIsCountedExactlyAndNeverLandsOnANewSecret() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        String created = mockMvc.perform(post("/api/projects/{projectId}/sources", owner.projectId())
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GITHUB\",\"owner\":\"acme\",\"repository\":\"app\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID sourceId = UUID.fromString(JsonPath.read(created, "$.id"));
        String webhookId = ((String) JsonPath.read(created, "$.webhookPath")).substring("/webhooks/github/".length());
        String forged = "sha256=" + "0".repeat(64);

        for (int attempt = 0; attempt < 50; attempt++) {
            assertThat(gitHubVerifier.verify(webhookId, forged, "{}".getBytes(StandardCharsets.UTF_8))).isEmpty();
        }
        // A missing signature is a failed proof too; an unknown path has nothing to count on.
        assertThat(gitHubVerifier.verify(webhookId, null, "{}".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(gitHubVerifier.verify(UUID.randomUUID().toString(), forged, "{}".getBytes(StandardCharsets.UTF_8)))
                .isEmpty();
        failures.drain();
        assertThat(failureCount(sourceId)).isEqualTo(51);

        // Failures still pending when the secret is replaced belong to the old secret.
        for (int attempt = 0; attempt < 5; attempt++) {
            gitHubVerifier.verify(webhookId, forged, "{}".getBytes(StandardCharsets.UTF_8));
        }
        generateSecret(owner, sourceId).andExpect(status().isOk());
        failures.drain();
        assertThat(failureCount(sourceId)).isZero();
    }

    @Test
    void aLinearDeliveryIsFreshByWhenLinearSentItNotWhenTheIssueChanged() throws Exception {
        Owner owner = registerAndLogin("fresh@example.com");
        Connected linear = connectLinear(owner);
        Instant now = Instant.now();

        // Sent now about a change ten minutes old: fresh. createdAt is not a proof.
        assertThat(verifyLinear(linear.webhookId(), linearBody(now.minusSeconds(600), stamp(now)))).isPresent();
        // The stamp is read leniently, since the secret signed it: seconds and ISO-8601 too.
        assertThat(verifyLinear(linear.webhookId(), linearBody(now, Long.toString(now.getEpochSecond()))))
                .isPresent();
        assertThat(verifyLinear(linear.webhookId(), linearBody(now, "\"" + now + "\""))).isPresent();
        failures.drain();
        assertThat(failureCount(linear.id())).isZero();
    }

    @Test
    void aStaleMissingOrAlteredWebhookTimestampIsRefusedCountedAndNamed(CapturedOutput output) throws Exception {
        Owner owner = registerAndLogin("stamp@example.com");
        Connected linear = connectLinear(owner);
        Instant now = Instant.now();

        // Well outside and well inside the minute, so a slow machine cannot move a stamp
        // across it while the check runs. A fresh createdAt does not save a stale send.
        assertThat(verifyLinear(linear.webhookId(), linearBody(now, stamp(now.minusSeconds(120))))).isEmpty();
        assertThat(verifyLinear(linear.webhookId(), linearBody(now, stamp(now.plusSeconds(120))))).isEmpty();
        assertThat(verifyLinear(linear.webhookId(), linearBody(now, null))).isEmpty();
        assertThat(verifyLinear(linear.webhookId(), linearBody(now, "\"not a time\""))).isEmpty();
        // Signed when stale, then given a fresh stamp: the signature no longer matches.
        byte[] signed = linearBody(now, stamp(now.minusSeconds(120)));
        byte[] altered = new String(signed, StandardCharsets.UTF_8)
                .replace(stamp(now.minusSeconds(120)), stamp(now)).getBytes(StandardCharsets.UTF_8);
        assertThat(linearVerifier.verify(linear.webhookId(), sign(LINEAR_SECRET, signed), "d-altered", altered))
                .isEmpty();
        failures.drain();
        assertThat(failureCount(linear.id())).isEqualTo(5);

        String refusal = "Refused a Linear delivery to source " + linear.id() + ": ";
        // The offset in whole seconds, so a skewed clock shows by how much.
        assertThat(output).containsPattern(Pattern.quote(refusal)
                + "webhookTimestamp is -12[01] s from this server's clock, beyond the allowed 60 s\\.");
        // The other way off is the same reason inside the same minute, so it is only counted.
        assertThat(output).doesNotContainPattern("webhookTimestamp is \\+1(19|20) s");
        assertThat(output).contains(refusal + "no webhookTimestamp that can be read.");
        assertThat(output).contains(refusal + "Linear-Signature does not match the body.");
        // Nothing of the body or the secret is written.
        assertThat(output).doesNotContain(LINEAR_SECRET).doesNotContain(WORKSPACE).doesNotContain("not a time");
    }

    @Test
    void eachRefusedLinearDeliveryNamesTheProofThatFailed(CapturedOutput output) throws Exception {
        Owner owner = registerAndLogin("proofs@example.com");
        Connected linear = connectLinear(owner);
        byte[] body = linearBody(Instant.now(), stamp(Instant.now()));
        String signature = sign(LINEAR_SECRET, body);

        assertThat(linearVerifier.verify(linear.webhookId(), signature, null, body)).isEmpty();
        assertThat(linearVerifier.verify(linear.webhookId(), null, "d-1", body)).isEmpty();
        assertThat(linearVerifier.verify(linear.webhookId(), sign("lin_wh_another-secret", body), "d-2", body))
                .isEmpty();
        byte[] otherWorkspace = new String(body, StandardCharsets.UTF_8)
                .replace(WORKSPACE, "99999999-9999-4999-8999-999999999999").getBytes(StandardCharsets.UTF_8);
        assertThat(linearVerifier.verify(linear.webhookId(), sign(LINEAR_SECRET, otherWorkspace), "d-3",
                otherWorkspace)).isEmpty();
        byte[] notJson = "not json".getBytes(StandardCharsets.UTF_8);
        assertThat(linearVerifier.verify(linear.webhookId(), sign(LINEAR_SECRET, notJson), "d-4", notJson)).isEmpty();
        failures.drain();
        assertThat(failureCount(linear.id())).isEqualTo(5);

        String refusal = "Refused a Linear delivery to source " + linear.id() + ": ";
        assertThat(output).contains(refusal + "no Linear-Delivery header.");
        assertThat(output).contains(refusal + "no Linear-Signature header.");
        assertThat(output).contains(refusal + "Linear-Signature does not match the body.");
        assertThat(output).contains(refusal + "organizationId is not this source's workspace.");
        assertThat(output).contains(refusal + "the body is not JSON.");
        assertThat(output).doesNotContain(LINEAR_SECRET).doesNotContain(signature).doesNotContain("99999999-9999");
    }

    private Optional<VerifiedWebhook> verifyLinear(String webhookId, byte[] body) {
        return linearVerifier.verify(webhookId, sign(LINEAR_SECRET, body), "d-" + UUID.randomUUID(), body);
    }

    private static String stamp(Instant instant) {
        return Long.toString(instant.toEpochMilli());
    }

    private long failureCount(UUID sourceId) {
        return jdbcTemplate.queryForObject("SELECT signature_failure_count FROM integration_sources WHERE id = ?",
                Long.class, sourceId);
    }

    private Optional<VerifiedWebhook> verifyLinear(String webhookId, String secret, String deliveryId) {
        byte[] body = linearBody();
        return linearVerifier.verify(webhookId, sign(secret, body), deliveryId, body);
    }

    private static byte[] linearBody() {
        return linearBody(Instant.now(), stamp(Instant.now()));
    }

    /** @param webhookTimestamp the JSON value of the stamp, or null to leave it out */
    private static byte[] linearBody(Instant createdAt, String webhookTimestamp) {
        String stamp = webhookTimestamp == null ? "" : ",\"webhookTimestamp\":" + webhookTimestamp;
        return """
                {"type":"Issue","action":"create","createdAt":"%s","organizationId":"%s","data":{}%s}"""
                .formatted(createdAt, WORKSPACE, stamp).getBytes(StandardCharsets.UTF_8);
    }

    private static String sign(String secret, byte[] body) {
        return HexFormat.of().formatHex(WebhookSignatures.hmacSha256(secret.getBytes(StandardCharsets.UTF_8), body));
    }

    // Standard Webhooks headers for an empty object, signed with a whsec_ token.
    private static GitLabWebhookVerifier.GitLabDeliveryHeaders signed(String signingToken) {
        String id = UUID.randomUUID().toString();
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        byte[] key = Base64.getDecoder().decode(signingToken.substring("whsec_".length()));
        String signature = "v1," + Base64.getEncoder().encodeToString(WebhookSignatures.hmacSha256(key,
                (id + "." + timestamp + ".{}").getBytes(StandardCharsets.UTF_8)));
        return new GitLabWebhookVerifier.GitLabDeliveryHeaders(id, timestamp, signature, null);
    }

    private ResultActions generateSecret(Owner owner, UUID sourceId) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/sources/{sourceId}/webhook-secret", owner.projectId(),
                sourceId).session(owner.session()).with(csrf()));
    }

    private ResultActions pasteSecret(Owner owner, UUID sourceId, String secret) throws Exception {
        return mockMvc.perform(put("/api/projects/{projectId}/sources/{sourceId}/webhook-secret", owner.projectId(),
                        sourceId)
                .session(owner.session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"webhookSecret\":\"%s\"}".formatted(secret)));
    }

    private ResultActions reconnect(Owner owner, UUID sourceId, String body) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/sources/{sourceId}/reconnect", owner.projectId(),
                        sourceId)
                .session(owner.session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void disconnect(Owner owner, UUID sourceId) throws Exception {
        mockMvc.perform(post("/api/projects/{projectId}/sources/{sourceId}/disconnect", owner.projectId(), sourceId)
                        .session(owner.session()).with(csrf()))
                .andExpect(status().isOk());
    }

    private Connected connectLinear(Owner owner) throws Exception {
        LINEAR.respondWithTeam(TEAM, WORKSPACE);
        String created = mockMvc.perform(post("/api/projects/{projectId}/sources", owner.projectId())
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"LINEAR","teamId":"%s","webhookSecret":"%s","apiToken":"%s"}"""
                                .formatted(TEAM, LINEAR_SECRET, LINEAR_TOKEN)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        LINEAR.reset();
        return connected(created, "/webhooks/linear/");
    }

    private Connected connectGitLab(Owner owner, String path, String mode) throws Exception {
        String created = mockMvc.perform(post("/api/projects/{projectId}/sources", owner.projectId())
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"GITLAB","apiBaseUrl":"%s","projectPath":"%s","webhookAuthMode":"%s"}"""
                                .formatted(GITLAB.baseUrl(), path, mode)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return connected(created, "/webhooks/gitlab/");
    }

    private UUID connectJira(Owner owner) throws Exception {
        String created = mockMvc.perform(post("/api/projects/{projectId}/sources", owner.projectId())
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"JIRA","siteUrl":"%s","projectKey":"APP","accountEmail":"ops@acme.test","apiToken":"%s"}"""
                                .formatted(JiraStub.SITE, JIRA_TOKEN)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JIRA.reset();
        return UUID.fromString(JsonPath.read(created, "$.id"));
    }

    private static Connected connected(String created, String prefix) {
        return new Connected(UUID.fromString(JsonPath.read(created, "$.id")),
                ((String) JsonPath.read(created, "$.webhookPath")).substring(prefix.length()));
    }

    private Owner registerAndLogin(String email) throws Exception {
        RegistrationRequest registration = new RegistrationRequest();
        registration.setOrganizationName(email);
        registration.setDisplayName("Owner");
        registration.setEmail(email);
        registration.setPassword("owner-password");
        RegistrationResult registered = registrationService.register(registration);
        MvcResult login = mockMvc.perform(post("/login").with(csrf()).param("email", email).param("password", "owner-password"))
                .andExpect(status().isFound())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        MvcResult project = mockMvc.perform(post("/api/projects").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ReleaseFlow\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new Owner(registered.organizationId(),
                UUID.fromString(JsonPath.read(project.getResponse().getContentAsString(), "$.id")), session);
    }

    private record Owner(UUID organizationId, UUID projectId, MockHttpSession session) {
    }

    private record Connected(UUID id, String webhookId) {
    }
}
