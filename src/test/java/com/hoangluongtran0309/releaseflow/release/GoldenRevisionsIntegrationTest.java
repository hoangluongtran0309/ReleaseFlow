package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.DeepLStub;
import com.hoangluongtran0309.releaseflow.support.JiraStub;
import com.hoangluongtran0309.releaseflow.support.LinearStub;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.hoangluongtran0309.releaseflow.translation.TranslationQueue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Rendering a published note again, against the dbvault workspace's second golden package
 * (ADR-0046 part 7, amended 2026-11-20). dbvault v1.2.0 is approved as the workspace's render
 * replay approved it, every summary and narrative written as captured and DeepL answered only
 * from the answers it really gave; each note is then put back as 1.1.0-rc.2 published it,
 * revision 1, and the release is published. Rendering each Vietnamese note again must give
 * the package's revision 2 byte for byte, with no DeepL request: the same answers, repaired
 * as ADR-0040 repairs any answer. The package in {@code golden/dbvault/1.4.0-revisions/}
 * comes from the workspace and is never edited here; see its README.
 */
class GoldenRevisionsIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String PACKAGE = "golden/dbvault/1.4.0-revisions/";
    private static final String VERSION = "v1.2.0";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DeepLStub DEEPL = DeepLStub.start();
    private static final LinearStub LINEAR = LinearStub.start();
    private static final JiraStub JIRA = JiraStub.start();
    private static final String LINEAR_TEAM = "33333333-3333-4333-8333-333333333333";
    private static final String LINEAR_WORKSPACE = "44444444-4444-4444-8444-444444444444";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext context;

    private UUID organizationId;
    private UUID projectId;
    private MockHttpSession session;
    private final Map<String, UUID> sourceIds = new LinkedHashMap<>();
    private final Map<String, UUID> changeIds = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();

    @DynamicPropertySource
    static void providers(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.translation.provider", () -> "deepl");
        registry.add("releaseflow.deepl.api-key", () -> "deepl-golden-key:fx");
        registry.add("releaseflow.deepl.base-url", DEEPL::baseUrl);
        registry.add("releaseflow.linear.api-base-url", LINEAR::baseUrl);
        registry.add("releaseflow.jira.api-base-url", JIRA::baseUrl);
    }

    @AfterAll
    static void stopStubs() {
        DEEPL.close();
        LINEAR.close();
        JIRA.close();
    }

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        DEEPL.reset();
        LINEAR.reset();
        JIRA.reset();
        jdbcTemplate.execute("TRUNCATE release_note_revisions, public_changelog_entry_revisions, public_changelog_entries, automation_action_runs, automation_runs, "
                + "automation_publish_jobs, automation_rule_actions, automation_rules, translation_jobs, "
                + "release_audience_notes, release_change_reviews, release_notes, release_changes, linked_issue_reads, release_linked_issues, release_fold_decisions, releases");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM organization_translation_settings");
        jdbcTemplate.update("DELETE FROM audience_status_changes");
        // A retired audience names who retired it, so the audiences go before the members.
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM app_users");
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void rendersDbvaultsVietnameseNotesAgainAsThePackagesRevisionTwo() throws Exception {
        verifyManifest();
        JsonNode organization = read("inputs/organization.json");
        JsonNode release = read("inputs/releases/" + VERSION + ".json");
        DEEPL.answerFrom(recordedAnswers());
        setUp(organization);
        String path = approve(release);

        // Approval writes today's notes: the English ones as rc.2 published them, the
        // Vietnamese ones already repaired, which is the package's revision 2.
        Map<String, UUID> notes = notes(path);
        assertThat(notes).hasSize(12);
        for (Map.Entry<String, UUID> note : notes.entrySet()) {
            String expected = note.getKey().endsWith(".en") ? "revision-1/" : "revision-2/";
            compare("approved " + note.getKey(), download(path, note.getValue()), expected + VERSION + "/" + note.getKey() + ".md");
        }
        assertThat(failures).withFailMessage(() -> String.join("\n\n", failures)).isEmpty();

        // Each Vietnamese note goes back to what rc.2 published, before the release is published.
        for (Map.Entry<String, UUID> note : notes.entrySet()) {
            if (note.getKey().endsWith(".vi")) {
                jdbcTemplate.update("UPDATE release_audience_notes SET content = ? WHERE id = ?",
                        new String(bytes("revision-1/" + VERSION + "/" + note.getKey() + ".md"), StandardCharsets.UTF_8),
                        note.getValue());
            }
        }
        send(post(path + "/publish"), null, 200);
        int requestsBefore = DEEPL.requests().size();

        JsonNode revisions = read("revision-2/" + VERSION + "/revisions.json").path("revisions");
        assertThat(revisions.size()).as("revisions the package expects").isEqualTo(6);
        for (JsonNode revision : revisions.values()) {
            String name = revision.path("audience").stringValue() + "." + revision.path("language").stringValue();
            UUID noteId = notes.get(name);
            assertThat(sha256(download(path, noteId))).as(name + " is revision 1 as published")
                    .isEqualTo(revision.path("revision1Sha256").stringValue());
            JsonNode preview = JSON.readTree(send(get(path + "/notes/" + noteId + "/correction-preview")
                    .param("origin", "RENDERED"), null, 200));
            assertThat(preview.path("corrects").asInt()).as(name + " corrects revision 1").isEqualTo(1);
            assertThat(preview.path("missingTexts").asInt()).as(name + " finds every answer it was published with")
                    .isZero();
            assertThat(preview.path("changed").asBoolean()).as(name + " changes").isTrue();
            byte[] rendered = preview.path("content").stringValue().getBytes(StandardCharsets.UTF_8);
            compare("rendered again " + name, rendered, "revision-2/" + VERSION + "/" + name + ".md");
            assertThat(sha256(rendered)).as(name + " matches revisions.json").isEqualTo(revision.path("sha256").stringValue());
        }
        // An English note was published as today's writer writes it.
        for (String name : notes.keySet()) {
            if (name.endsWith(".en")) {
                JsonNode preview = JSON.readTree(send(get(path + "/notes/" + notes.get(name) + "/correction-preview")
                        .param("origin", "RENDERED"), null, 200));
                assertThat(preview.path("changed").asBoolean()).as(name + " renders as published").isFalse();
            }
        }
        assertThat(failures).withFailMessage(() -> String.join("\n\n", failures)).isEmpty();
        assertThat(DEEPL.requests()).as("rendering again asks DeepL nothing").hasSize(requestsBefore);

        // Saving one keeps exactly what the preview showed, as revision 2.
        UUID operator = notes.get("operator.vi");
        JsonNode preview = JSON.readTree(send(get(path + "/notes/" + operator + "/correction-preview")
                .param("origin", "RENDERED"), null, 200));
        JsonNode saved = JSON.readTree(send(post(path + "/notes/" + operator + "/revisions"), Map.of(
                "origin", "RENDERED", "corrects", 1, "previewHash", preview.path("previewHash").stringValue(),
                "reason", "Placeholders và dbvault-cli được viết như trong nguồn."), 201));
        assertThat(saved.path("origin").stringValue()).isEqualTo("RENDERED");
        compare("saved operator.vi", download(path, operator), "revision-2/" + VERSION + "/operator.vi.md");
        compare("revision 1 of operator.vi", bytes(send(get(path + "/notes/" + operator + "/revisions/1"), null, 200), "content"),
                "revision-1/" + VERSION + "/operator.vi.md");

        // A retired audience's note is corrected by hand only; the package's text may be saved so.
        UUID probe = notes.get("aut_r4_probe.vi");
        send(post("/api/audiences/{audienceId}/retire", audiences().get("aut_r4_probe")), null, 200);
        assertThat(field(send(get(path + "/notes/" + probe + "/correction-preview").param("origin", "RENDERED"), null, 409),
                "code")).isEqualTo("audience_retired");
        send(post(path + "/notes/" + probe + "/revisions"), Map.of("corrects", 1,
                "content", new String(bytes("revision-2/" + VERSION + "/aut_r4_probe.vi.md"), StandardCharsets.UTF_8),
                "reason", "dbvault-cli được viết như trong nguồn."), 201);
        compare("aut_r4_probe.vi by hand", download(path, probe), "revision-2/" + VERSION + "/aut_r4_probe.vi.md");
        assertThat(failures).withFailMessage(() -> String.join("\n\n", failures)).isEmpty();
    }

    // The Organization, its release languages, sources, categories, and audiences, then every
    // change as captured.
    private void setUp(JsonNode organization) throws Exception {
        RegistrationRequest registration = new RegistrationRequest();
        registration.setOrganizationName("dbvault");
        registration.setDisplayName("dbvault Lead");
        registration.setEmail("lead@dbvault.test");
        registration.setPassword("golden-revisions");
        registration.setOutputLanguage(organization.path("outputLanguage").stringValue());
        organizationId = registrationService.register(registration).organizationId();
        session = login("lead@dbvault.test", "golden-revisions");
        projectId = UUID.fromString(field(send(post("/api/projects"), Map.of("name", "dbvault"), 201), "id"));
        for (JsonNode source : organization.path("sources").values()) {
            connect(source);
        }
        List<String> targets = new ArrayList<>();
        organization.path("releaseLanguages").values().forEach(language -> targets.add(language.stringValue()));
        send(put("/api/organization/release-languages"), Map.of("targetLanguages", targets), 200);

        List<String> categories = new ArrayList<>();
        for (JsonNode category : JSON.readTree(send(get("/api/categories"), null, 200)).values()) {
            categories.add(category.path("code").stringValue());
        }
        for (JsonNode category : organization.path("categories").values()) {
            if (!categories.contains(category.path("code").stringValue())) {
                send(post("/api/categories"), Map.of("code", category.path("code").stringValue(),
                        "displayName", category.path("displayName").stringValue(),
                        "group", category.path("group").stringValue()), 201);
            }
        }

        Map<String, UUID> existing = audiences();
        for (JsonNode audience : organization.path("audiences").values()) {
            String code = audience.path("code").stringValue();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("displayName", audience.path("displayName").stringValue());
            body.put("communicationIntent", audience.path("communicationIntent").stringValue());
            body.put("templateBody", audience.path("templateBody").stringValue());
            body.put("footerTemplateBody", audience.path("footerTemplateBody").stringValue());
            UUID id = existing.get(code);
            if (id == null) {
                body.put("code", code);
                id = UUID.fromString(field(send(post("/api/audiences"), body, 201), "id"));
            } else {
                send(put("/api/audiences/{audienceId}", id), body, 200);
            }
            send(put("/api/audiences/{audienceId}/templates/vi", id), Map.of(
                    "templateBody", audience.path("templateVariants").path("vi").stringValue(),
                    "footerTemplateBody", audience.path("footerTemplateVariants").path("vi").stringValue()), 200);
        }

        for (JsonNode change : read("inputs/changes.json").values()) {
            changeIds.put(key(change), store(change, organization.path("categories")));
        }
    }

    private void connect(JsonNode source) throws Exception {
        String type = source.path("type").stringValue();
        Map<String, Object> body = switch (type) {
            case "GITHUB" -> {
                String[] repository = source.path("repository").stringValue().split("/");
                yield Map.of("type", type, "owner", repository[0], "repository", repository[1]);
            }
            case "GITLAB" -> Map.of("type", type, "apiBaseUrl", source.path("apiBaseUrl").stringValue(),
                    "projectPath", source.path("projectPath").stringValue(), "webhookAuthMode", "GITLAB_SIGNING_TOKEN");
            case "LINEAR" -> {
                LINEAR.respondWithTeam(LINEAR_TEAM, LINEAR_WORKSPACE);
                yield Map.of("type", type, "teamId", LINEAR_TEAM, "webhookSecret", "lin_wh_golden-revisions",
                        "apiToken", "lin_api_golden-revisions");
            }
            // The site is the stand-in's: a Jira change keeps its own link, and no note shows the source's.
            case "JIRA" -> Map.of("type", type, "siteUrl", JiraStub.SITE, "projectKey", source.path("projectKey").stringValue(),
                    "accountEmail", "release-bot@dbvault.test", "apiToken", "atlassian-golden-revisions");
            default -> throw new IllegalStateException("Unknown source type " + type);
        };
        sourceIds.put(type, UUID.fromString(field(send(post("/api/projects/{projectId}/sources", projectId), body, 201), "id")));
    }

    // A change as intake left it, with the classification its captured record holds: the
    // replay's edits restored exactly that, so a change inserted as captured needs none.
    private UUID store(JsonNode change, JsonNode categories) {
        JsonNode record = change.path("record");
        String source = change.path("source").stringValue();
        String reference = change.path("reference").stringValue();
        String category = record.path("category").stringValue();
        JsonNode definition = categoryNamed(categories, category);
        boolean codeHost = source.equals("GITHUB") || source.equals("GITLAB");
        JsonNode linkedIssues = record.path("linkedIssues");
        boolean keepsIssues = linkedIssues.isArray() && !linkedIssues.isEmpty();
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO changes
                            (id, organization_id, project_id, source_id, source_type, external_id, origin,
                             pull_request_number, title, description, author_login, labels, target_branch,
                             merge_commit_sha, merged_at, url, delivery_id, received_at,
                             category, category_display_name, category_group, breaking, security, needs_review,
                             classification_reasons, classification_source, ai_status,
                             processing_status, changed_file_status, review_triggers,
                             linked_context_status, linked_issues)
                        VALUES (?, ?, ?, ?, ?, ?, 'WEBHOOK', ?, ?, NULL, NULL, ARRAY[]::text[], ?,
                                ?, ?::timestamptz, ?, ?, now(),
                                ?, ?, ?, ?, ?, true,
                                ARRAY(SELECT jsonb_array_elements_text(?::jsonb)), 'RULES', 'NOT_REQUESTED',
                                'COMPLETED', ?, '[]', ?, ?::jsonb)
                        """,
                id, organizationId, projectId, sourceIds.get(source), source, reference, number(reference),
                record.path("title").stringValue(),
                codeHost ? "main" : null,
                codeHost ? sha256(reference.getBytes(StandardCharsets.UTF_8)).substring(0, 40) : null,
                record.path("mergedAt").stringValue(),
                record.path("url").stringValue(),
                UUID.randomUUID(),
                category,
                definition.path("displayName").stringValue(),
                definition.path("group").stringValue(),
                record.path("breaking").asBoolean(),
                record.path("security").asBoolean(),
                record.path("reasons").toString(),
                codeHost ? "UNAVAILABLE" : "NOT_SUPPORTED",
                keepsIssues ? "COLLECTED" : "NOT_FOUND",
                keepsIssues ? linkedIssues.toString() : null
        );
        return id;
    }

    // Created, filled, every summary and narrative written as captured, each change approved as
    // captured, approved, and translated from the recorded answers alone.
    private String approve(JsonNode release) throws Exception {
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("version", VERSION);
        created.put("summary", release.path("summary").stringValue());
        created.put("upgradeGuideUrl", release.path("upgradeGuideUrl").stringValue());
        UUID releaseId = UUID.fromString(field(send(post("/api/projects/{projectId}/releases", projectId), created, 201), "id"));
        String path = "/api/projects/" + projectId + "/releases/" + releaseId;
        List<UUID> members = new ArrayList<>();
        release.path("changes").values().forEach(member -> members.add(changeIds.get(key(member))));
        send(post(path + "/changes"), Map.of("changeIds", members), 200);
        send(post(path + "/request-review"), null, 200);

        Map<String, JsonNode> records = new LinkedHashMap<>();
        for (JsonNode change : read("inputs/changes.json").values()) {
            records.put(key(change), change.path("record"));
        }
        for (JsonNode member : release.path("changes").values()) {
            JsonNode record = records.get(key(member));
            Map<String, Object> summary = new LinkedHashMap<>();
            JsonNode neutral = record.path("neutralSummary");
            for (String field : List.of("whatChanged", "whyChanged", "technicalDetail", "migrationStep", "limitations")) {
                summary.put(field, neutral.path(field).stringValue());
            }
            summary.put("migrationKind", neutral.path("migrationKind").stringValue());
            summary.put("narratives", record.path("audienceNarrativeDetails"));
            send(put(path + "/changes/" + changeIds.get(key(member)) + "/summary"), summary, 200);
        }
        for (JsonNode member : release.path("changes").values()) {
            Map<String, Object> decision = new LinkedHashMap<>();
            decision.put("action", "APPROVE");
            decision.put("category", member.path("category").stringValue());
            decision.put("breaking", member.path("breaking").asBoolean());
            decision.put("security", member.path("security").asBoolean());
            send(put(path + "/changes/" + changeIds.get(key(member)) + "/decision"), decision, 200);
        }
        send(post(path + "/approve"), null, 200);
        TranslationQueue.drain(context, jdbcTemplate);
        assertThat(DEEPL.unrecorded()).as("every text sent to DeepL has a recorded answer").isEmpty();
        return path;
    }

    private Map<String, UUID> notes(String path) throws Exception {
        Map<String, UUID> notes = new LinkedHashMap<>();
        for (JsonNode note : JSON.readTree(send(get(path + "/notes"), null, 200)).values()) {
            assertThat(note.path("translationStatus").stringValue()).as("translation").isEqualTo("READY");
            notes.put(note.path("audienceCode").stringValue() + "." + note.path("language").stringValue(),
                    UUID.fromString(note.path("id").stringValue()));
        }
        return notes;
    }

    private byte[] download(String path, UUID noteId) throws Exception {
        return mockMvc.perform(get(path + "/notes/" + noteId + "/download").session(session))
                .andReturn().getResponse().getContentAsByteArray();
    }

    private void compare(String what, byte[] actual, String file) throws IOException {
        byte[] expected = bytes(file);
        if (!java.util.Arrays.equals(actual, expected)) {
            failures.add(what + " differs from " + file + ":\n" + GoldenReleaseNotesIntegrationTest.diff(
                    new String(expected, StandardCharsets.UTF_8), new String(actual, StandardCharsets.UTF_8)));
        }
    }

    private Map<String, UUID> audiences() throws Exception {
        Map<String, UUID> byCode = new LinkedHashMap<>();
        for (JsonNode audience : JSON.readTree(send(get("/api/audiences"), null, 200)).values()) {
            byCode.put(audience.path("code").stringValue(), UUID.fromString(audience.path("id").stringValue()));
        }
        return byCode;
    }

    private static JsonNode categoryNamed(JsonNode categories, String code) {
        for (JsonNode category : categories.values()) {
            if (category.path("code").stringValue().equals(code)) {
                return category;
            }
        }
        throw new IllegalStateException("No category " + code + " in the inputs");
    }

    // The source text, target language, and text of each answer DeepL gave the replay.
    private static Map<List<String>, String> recordedAnswers() throws IOException {
        Map<List<String>, String> answers = new LinkedHashMap<>();
        for (String line : new String(bytes("deepl/answers.jsonl"), StandardCharsets.UTF_8).lines()
                .filter(line -> !line.isEmpty()).toList()) {
            JsonNode answer = JSON.readTree(line);
            answers.put(List.of(answer.path("source_lang").stringValue(), answer.path("target_lang").stringValue(),
                    answer.path("text").stringValue()), answer.path("translation").stringValue());
        }
        return answers;
    }

    // The package is the workspace's, unchanged: a file edited here fails before anything runs.
    private static void verifyManifest() throws IOException {
        for (String line : new String(bytes("MANIFEST.sha256"), StandardCharsets.UTF_8).lines()
                .filter(line -> !line.isEmpty()).toList()) {
            String expected = line.substring(0, 64);
            String file = line.substring(64).strip().replaceFirst("^\\*", "");
            assertThat(sha256(bytes(file))).as(PACKAGE + file + " matches MANIFEST.sha256").isEqualTo(expected);
        }
    }

    private String send(MockHttpServletRequestBuilder request, Object body, int status) throws Exception {
        request.session(session).with(csrf());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        String response = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus())
                .as(result.getRequest().getMethod() + " " + result.getRequest().getRequestURI() + " answered " + response)
                .isEqualTo(status);
        return response;
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login").with(csrf()).param("email", email).param("password", password))
                .andReturn();
        assertThat(login.getResponse().getStatus()).as("sign-in").isEqualTo(302);
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private static String key(JsonNode change) {
        return change.path("source").stringValue() + " " + change.path("reference").stringValue();
    }

    // The number a person sees: #12, !7, OPS-5.
    private static int number(String reference) {
        return Integer.parseInt(reference.replaceFirst("^(#|!|[A-Z][A-Z0-9_]*-)", ""));
    }

    private static String field(String json, String name) {
        return JSON.readTree(json).path(name).asString();
    }

    private static byte[] bytes(String json, String name) {
        return JSON.readTree(json).path(name).stringValue().getBytes(StandardCharsets.UTF_8);
    }

    private static JsonNode read(String file) throws IOException {
        return JSON.readTree(bytes(file));
    }

    private static byte[] bytes(String file) throws IOException {
        try (InputStream in = GoldenRevisionsIntegrationTest.class.getClassLoader().getResourceAsStream(PACKAGE + file)) {
            if (in == null) {
                throw new IOException("No " + PACKAGE + file + " on the test classpath");
            }
            return in.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
