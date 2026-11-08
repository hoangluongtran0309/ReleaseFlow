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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Release-note regressions, caught without the dbvault workspace (#18). The Organization of
 * dbvault's five releases v0.1.0 to v1.1.0 is set up from the workspace's inputs, each release
 * is approved with AI off and DeepL answered from the answers it really gave, and every note,
 * English and Vietnamese, must equal the one ReleaseFlow 1.1.0 wrote, byte for byte. A text
 * DeepL never answered fails it too, so a change to what ReleaseFlow sends DeepL is caught.
 *
 * <p>The Organization's settings, sources, audiences, and releases go through the REST API.
 * The changes are stored as intake left them, with the classification their review confirms:
 * intake reads each provider's API, and classification is not what this test is about. The
 * inputs and snapshots in {@code golden/dbvault/} come from the workspace and are never edited
 * here; see its README.
 */
class GoldenReleaseNotesIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String PACKAGE = "golden/dbvault/1.1.0/";
    private static final String REVIEW_READS = "trackers/review-reads.jsonl";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DeepLStub DEEPL = DeepLStub.start();
    private static final LinearStub LINEAR = LinearStub.start();
    private static final JiraStub JIRA = JiraStub.start();
    private static final String LINEAR_TEAM = "33333333-3333-4333-8333-333333333333";
    private static final String LINEAR_WORKSPACE = "44444444-4444-4444-8444-444444444444";
    private static final int MAX_DIFF_LINES = 200;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private LinkedIssueReadWorker readWorker;

    private MockHttpSession session;
    private UUID organizationId;
    private UUID projectId;
    private final Map<String, UUID> sourceIds = new LinkedHashMap<>();
    private final Map<String, UUID> changeIds = new LinkedHashMap<>();
    private final Map<String, JsonNode> changes = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();
    private Map<String, List<JsonNode>> reviewReads;
    private int notesCompared;

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
        jdbcTemplate.execute("TRUNCATE public_changelog_entries, automation_action_runs, automation_runs, "
                + "automation_publish_jobs, automation_rule_actions, automation_rules, translation_jobs, "
                + "release_audience_notes, release_change_reviews, release_notes, release_changes, linked_issue_reads, release_linked_issues, release_fold_decisions, releases");
        jdbcTemplate.update("DELETE FROM glossary_syncs");
        jdbcTemplate.update("DELETE FROM translation_glossaries");
        jdbcTemplate.update("DELETE FROM glossary_kept_terms");
        jdbcTemplate.update("DELETE FROM glossary_translations");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM organization_translation_settings");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void dbvaultsNotesAreWrittenAsReleaseFlow110WroteThem() throws Exception {
        verifyManifest();
        JsonNode organization = read("inputs/organization.json");
        for (JsonNode change : read("inputs/changes.json").values()) {
            changes.put(key(change), change);
        }
        Map<List<String>, String> answers = recordedAnswers();
        DEEPL.answerFrom(answers);
        reviewReads = reviewReads();

        setUp(organization);
        for (JsonNode event : organization.path("timeline").values()) {
            switch (event.path("event").stringValue()) {
                case "release" -> release(read("inputs/releases/" + event.path("value").stringValue() + ".json"));
                case "release languages" -> setReleaseLanguages(event.path("value"));
                case "audience added" -> addAudience(audience(organization, event.path("value").stringValue()));
                default -> throw new IllegalStateException("Unknown timeline event " + event);
            }
        }

        assertThat(new LinkedHashSet<>(DEEPL.translatedTexts()))
                .as("the texts sent to DeepL are exactly the ones it answered for the 1.1.0 notes")
                .containsExactlyInAnyOrderElementsOf(answers.keySet().stream().map(answer -> answer.get(2)).toList());
        checkDeepLRequests(organization.path("projectContext").stringValue());
        assertThat(failures).withFailMessage(() -> String.join("\n\n", failures)).isEmpty();
        assertThat(notesCompared).as("notes compared byte for byte").isEqualTo(46);
    }

    // The Organization and Project, their sources, context, glossary, categories, and audiences,
    // then every change, as the workspace's render set them up before its first release.
    private void setUp(JsonNode organization) throws Exception {
        RegistrationRequest registration = new RegistrationRequest();
        registration.setOrganizationName(organization.path("organizationName").stringValue());
        registration.setDisplayName("dbvault Lead");
        registration.setEmail("lead@dbvault.test");
        registration.setPassword("golden-release-notes");
        registration.setOutputLanguage(organization.path("outputLanguage").stringValue());
        organizationId = registrationService.register(registration).organizationId();
        session = login("lead@dbvault.test", "golden-release-notes");

        projectId = UUID.fromString(field(send(post("/api/projects"),
                Map.of("name", organization.path("projectName").stringValue()), 201), "id"));
        for (JsonNode source : organization.path("sources").values()) {
            connect(source);
        }
        send(put("/api/projects/{projectId}/context", projectId),
                Map.of("context", organization.path("projectContext").stringValue()), 200);
        JsonNode glossary = organization.path("glossary");
        send(post("/api/glossary/kept-terms"), Map.of("terms", strings(glossary.path("keptTerms"))), 201);
        for (JsonNode translation : glossary.path("translations").values()) {
            send(post("/api/glossary/translations"), translation, 201);
        }
        Set<String> categories = new LinkedHashSet<>();
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
        resetPresets(organization);

        Map<String, JsonNode> decided = new LinkedHashMap<>();
        for (String version : List.of("v0.1.0", "v0.2.0", "v0.3.0", "v1.0.0", "v1.1.0")) {
            for (JsonNode decision : read("inputs/releases/" + version + ".json").path("decisions").values()) {
                decided.put(key(decision), decision);
            }
        }
        for (Map.Entry<String, JsonNode> change : changes.entrySet()) {
            changeIds.put(change.getKey(), store(change.getValue(), decided.get(change.getKey()),
                    organization.path("categories")));
        }
        for (Map.Entry<String, JsonNode> change : changes.entrySet()) {
            JsonNode exclusion = change.getValue().path("exclusion");
            if (exclusion.isObject()) {
                send(post("/api/projects/{projectId}/changes/{changeId}/exclusion", projectId, changeIds.get(change.getKey())),
                        Map.of("reason", exclusion.path("reason").stringValue()), 200);
            }
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
                yield Map.of("type", type, "teamId", LINEAR_TEAM, "webhookSecret", "lin_wh_golden-notes",
                        "apiToken", "lin_api_golden-notes");
            }
            // The site is the stand-in's: a Jira change keeps its own link, and no note shows the source's.
            case "JIRA" -> Map.of("type", type, "siteUrl", JiraStub.SITE, "projectKey", source.path("projectKey").stringValue(),
                    "accountEmail", "release-bot@dbvault.test", "apiToken", "atlassian-golden-notes");
            default -> throw new IllegalStateException("Unknown source type " + type);
        };
        sourceIds.put(type, UUID.fromString(field(send(post("/api/projects/{projectId}/sources", projectId), body, 201), "id")));
    }

    // A change as intake left it, holding the classification its release's review confirms.
    private UUID store(JsonNode change, JsonNode decision, JsonNode categories) {
        JsonNode record = change.path("record");
        String source = change.path("source").stringValue();
        String reference = change.path("reference").stringValue();
        String category = decision != null ? decision.path("category").stringValue() : record.path("category").stringValue();
        JsonNode definition = categoryNamed(categories, category);
        boolean codeHost = source.equals("GITHUB") || source.equals("GITLAB");
        JsonNode linkedIssues = record.path("linkedIssues");
        String linkedStatus = record.path("linkedContextStatus").stringValue();
        boolean keepsIssues = linkedIssues.isArray() && !linkedIssues.isEmpty()
                && Set.of("PARTIAL", "UNAVAILABLE", "COLLECTED").contains(linkedStatus);
        boolean imported = "IMPORT".equals(record.path("origin").stringValue());
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
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ARRAY(SELECT jsonb_array_elements_text(?::jsonb)), ?,
                                ?, ?::timestamptz, ?, ?, now(),
                                ?, ?, ?, ?, ?, true,
                                ARRAY(SELECT jsonb_array_elements_text(?::jsonb)), 'RULES', 'NOT_REQUESTED',
                                'COMPLETED', ?, '[]', ?, ?::jsonb)
                        """,
                id, organizationId, projectId, sourceIds.get(source), source, reference,
                imported ? "IMPORT" : "WEBHOOK",
                number(reference),
                record.path("title").stringValue(),
                text(record.path("description")),
                text(record.path("authorLogin")),
                record.path("labels").toString(),
                codeHost ? record.path("targetBranch").stringValue() : null,
                codeHost ? record.path("mergeCommitSha").stringValue() : null,
                record.path("mergedAt").stringValue(),
                record.path("url").stringValue(),
                imported ? null : UUID.randomUUID(),
                category,
                definition.path("displayName").stringValue(),
                definition.path("group").stringValue(),
                decision != null ? decision.path("breaking").asBoolean() : record.path("breaking").asBoolean(),
                decision != null && decision.path("security").asBoolean(),
                record.path("reasons").toString(),
                codeHost ? "UNAVAILABLE" : "NOT_SUPPORTED",
                linkedStatus,
                keepsIssues ? linkedIssues.toString() : null
        );
        return id;
    }

    // Created, filled, reviewed as the workspace's render did, approved, translated, compared, and published.
    private void release(JsonNode release) throws Exception {
        String version = release.path("version").stringValue();
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("version", version);
        created.put("summary", text(release.path("summary")));
        created.put("upgradeGuideUrl", text(release.path("upgradeGuideUrl")));
        UUID releaseId = UUID.fromString(field(send(post("/api/projects/{projectId}/releases", projectId), created, 201), "id"));
        String path = "/api/projects/" + projectId + "/releases/" + releaseId;

        List<String> members = new ArrayList<>();
        release.path("members").values().forEach(member -> members.add(key(member)));
        send(post(path + "/changes"), Map.of("changeIds", members.stream().map(changeIds::get).toList()), 200);
        send(post(path + "/request-review"), null, 200);
        readAtReview(version, path);

        Set<String> atApproval = new LinkedHashSet<>();
        release.path("summariesWrittenAtApproval").values().forEach(written -> atApproval.add(written.path("reference").stringValue()));
        for (String member : members) {
            JsonNode summary = changes.get(member).path("summaryWritten");
            if (summary.isObject() && !atApproval.contains(reference(member))) {
                send(put(path + "/changes/" + changeIds.get(member) + "/summary"), summary, 200);
            }
        }
        for (JsonNode decision : release.path("decisions").values()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("action", decision.path("action").stringValue());
            body.put("category", decision.path("category").stringValue());
            body.put("breaking", decision.path("breaking").asBoolean());
            body.put("security", decision.path("security").asBoolean());
            body.put("note", text(decision.path("note")));
            send(put(path + "/changes/" + changeIds.get(key(decision)) + "/decision"), body, 200);
        }
        if (!atApproval.isEmpty()) {
            String refused = send(post(path + "/approve"), null, 409);
            assertThat(field(refused, "code")).as(version + " waits for a written summary").isEqualTo("change_summary_required");
            for (String member : members) {
                if (atApproval.contains(reference(member))) {
                    send(put(path + "/changes/" + changeIds.get(member) + "/summary"), changes.get(member).path("summaryWritten"), 200);
                }
            }
        }
        send(post(path + "/approve"), null, 200);
        TranslationQueue.drain(context, jdbcTemplate);
        List<String> unrecorded = DEEPL.unrecorded();
        assertThat(unrecorded).withFailMessage(() -> version + ": DeepL got " + unrecorded.size()
                        + " text(s) it has no recorded answer for, so what ReleaseFlow sends DeepL changed. The first:\n"
                        + String.join("\n", unrecorded.stream().limit(3).map(GoldenReleaseNotesIntegrationTest::line).toList()))
                .isEmpty();

        JsonNode approved = JSON.readTree(send(get(path), null, 200));
        compareAtApproval(version, release, approved);
        compareNotes(version, release, path, approved);
        // Every release is compared before the test fails, so one run shows each note that differs;
        // only a release that cannot be published stops it.
        if (release.path("publish").asBoolean()) {
            MvcResult published = mockMvc.perform(post(path + "/publish").session(session).with(csrf())).andReturn();
            if (published.getResponse().getStatus() != 200) {
                failures.add(version + " could not be published: "
                        + published.getResponse().getContentAsString(StandardCharsets.UTF_8));
                assertThat(failures).withFailMessage(() -> String.join("\n\n", failures)).isEmpty();
            }
        }
    }

    // What each change carried once approved: a difference here explains a note that differs.
    private void compareAtApproval(String version, JsonNode release, JsonNode approved) {
        Map<UUID, JsonNode> byId = new LinkedHashMap<>();
        approved.path("changes").values().forEach(change -> byId.put(UUID.fromString(change.path("id").stringValue()), change));
        for (JsonNode expected : release.path("atApproval").values()) {
            JsonNode actual = byId.get(changeIds.get(key(expected)));
            String member = version + " " + key(expected);
            if (actual == null) {
                failures.add(member + ": not in the release");
                continue;
            }
            for (String flag : List.of("category", "breaking", "security")) {
                if (!expected.path(flag).toString().equals(actual.path(flag).toString())) {
                    failures.add(member + " " + flag + ": expected " + expected.path(flag) + " but was " + actual.path(flag));
                }
            }
            JsonNode summary = expected.path("neutralSummary");
            if (summary.isObject()) {
                for (Map.Entry<String, JsonNode> field : summary.properties()) {
                    JsonNode value = actual.path("neutralSummary").path(field.getKey());
                    if (!field.getValue().equals(value)) {
                        failures.add(member + " summary " + field.getKey() + ": expected " + field.getValue() + " but was " + value);
                    }
                }
            } else if (!actual.path("neutralSummary").isNull() && !actual.path("neutralSummary").isMissingNode()) {
                failures.add(member + " summary: expected none but was " + actual.path("neutralSummary"));
            }
            for (Map.Entry<String, JsonNode> narrative : expected.path("narratives").properties()) {
                JsonNode written = actual.path("audienceNarrativeDetails").path(narrative.getKey());
                for (String part : List.of("relevance", "headline")) {
                    if (!narrative.getValue().path(part).equals(written.path(part))) {
                        failures.add(member + " " + narrative.getKey() + " " + part + ": expected "
                                + narrative.getValue().path(part) + " but was " + written.path(part));
                    }
                }
            }
        }
    }

    private void compareNotes(String version, JsonNode release, String path, JsonNode approved) throws Exception {
        if (!release.path("glossaries").equals(JSON.readTree(JSON.writeValueAsString(approved.path("glossaries"))))) {
            failures.add(version + " glossaries: expected " + release.path("glossaries") + " but was " + approved.path("glossaries"));
        }
        Map<String, UUID> written = new LinkedHashMap<>();
        for (JsonNode note : JSON.readTree(send(get(path + "/notes"), null, 200)).values()) {
            if (!"READY".equals(note.path("translationStatus").stringValue())) {
                failures.add(version + " " + note.path("audienceCode").stringValue() + "." + note.path("language").stringValue()
                        + ": translation " + note.path("translationStatus").stringValue());
            }
            written.put(note.path("audienceCode").stringValue() + "." + note.path("language").stringValue(),
                    UUID.fromString(note.path("id").stringValue()));
        }
        Set<String> expectedNotes = new LinkedHashSet<>();
        for (JsonNode note : release.path("notes").values()) {
            String name = note.path("audience").stringValue() + "." + note.path("language").stringValue();
            expectedNotes.add(name);
            UUID noteId = written.get(name);
            if (noteId == null) {
                failures.add(version + " " + name + ": no such note was written");
                continue;
            }
            byte[] actual = mockMvc.perform(get(path + "/notes/" + noteId + "/download").session(session))
                    .andReturn().getResponse().getContentAsByteArray();
            byte[] snapshot = bytes(note.path("file").stringValue());
            notesCompared++;
            if (!java.util.Arrays.equals(actual, snapshot)) {
                failures.add(version + " " + name + " differs from " + note.path("file").stringValue() + ":\n"
                        + diff(new String(snapshot, StandardCharsets.UTF_8), new String(actual, StandardCharsets.UTF_8)));
            }
        }
        for (String name : written.keySet()) {
            if (!expectedNotes.contains(name)) {
                failures.add(version + " " + name + ": a note the snapshots do not have");
            }
        }
    }

    /**
     * ADR-0041: requesting review reads each linked issue again. The trackers answer from what
     * the workspace captured when each change was recorded, never from the trackers as they are
     * today, so the run stays deterministic; an issue with no recorded answer fails the test. A
     * package from before these answers existed reads nothing, and its notes show no open
     * status, nor any whose category was never recorded.
     */
    private void readAtReview(String version, String path) throws Exception {
        if (reviewReads == null) {
            return;
        }
        JIRA.reset();
        LINEAR.reset();
        for (JsonNode answer : reviewReads.getOrDefault(version, List.of())) {
            String key = answer.path("key").stringValue();
            if ("LINEAR".equals(answer.path("tracker").stringValue())) {
                LINEAR.respondWithIdentifiedIssue(key, answer.path("title").stringValue(), answer.path("status").stringValue(),
                        answer.path("category").stringValue(), LINEAR_TEAM, LINEAR_WORKSPACE);
            } else {
                JIRA.respondWithIssue(key, answer.path("title").stringValue(), answer.path("type").asString(""),
                        answer.path("status").stringValue(), answer.path("category").stringValue(), "");
            }
        }
        do {
            jdbcTemplate.update("UPDATE linked_issue_reads SET next_attempt_at = now() - interval '1 second' WHERE status = 'PENDING'");
        } while (readWorker.processOne());
        List<String> unread = new ArrayList<>();
        for (JsonNode issue : JSON.readTree(send(get(path), null, 200)).path("linkedIssues").values()) {
            if (!issue.path("read").asBoolean()) {
                unread.add(issue.path("tracker").stringValue() + " " + issue.path("key").stringValue());
            }
        }
        assertThat(unread).as(version + ": linked issues with no recorded answer at review").isEmpty();
    }

    // The answers each release's review read, by release; null for a package without them.
    private static Map<String, List<JsonNode>> reviewReads() throws IOException {
        if (GoldenReleaseNotesIntegrationTest.class.getClassLoader().getResource(PACKAGE + REVIEW_READS) == null) {
            return null;
        }
        Map<String, List<JsonNode>> byRelease = new LinkedHashMap<>();
        for (String line : lines(new String(bytes(REVIEW_READS), StandardCharsets.UTF_8))) {
            JsonNode answer = JSON.readTree(line);
            byRelease.computeIfAbsent(answer.path("release").stringValue(), release -> new ArrayList<>()).add(answer);
        }
        return byRelease;
    }

    // Every translation went with the Project's context and the glossary the inputs hold.
    private void checkDeepLRequests(String projectContext) throws IOException {
        List<String> expected = lines(new String(bytes("deepl/glossary-en-vi.tsv"), StandardCharsets.UTF_8));
        Set<String> glossaryIds = new LinkedHashSet<>();
        for (DeepLStub.RecordedRequest request : DEEPL.requests()) {
            if (request.path().startsWith("/v3/glossaries") && request.body() != null && !request.body().isBlank()) {
                JsonNode body = JSON.readTree(request.body());
                JsonNode dictionary = body.has("dictionaries") ? body.path("dictionaries").get(0) : body;
                assertThat(lines(dictionary.path("entries").stringValue()))
                        .as("the glossary dictionary sent to DeepL").isEqualTo(expected);
            }
        }
        glossaryIds.addAll(DEEPL.glossaries().keySet());
        assertThat(DEEPL.translations()).as("translation requests").isNotEmpty().allSatisfy(request -> {
            assertThat(request.context()).as("DeepL's context").isEqualTo(projectContext);
            assertThat(glossaryIds).as("the glossary named").contains(request.glossaryId());
        });
    }

    private void setReleaseLanguages(JsonNode languages) throws Exception {
        send(put("/api/organization/release-languages"), Map.of("targetLanguages", strings(languages)), 200);
        // The render reset the presets again, so their templates in the new language are the shipped ones.
        resetPresets(read("inputs/organization.json"));
    }

    private void resetPresets(JsonNode organization) throws Exception {
        Map<String, UUID> audiences = audiences();
        for (JsonNode audience : organization.path("audiences").values()) {
            if (audience.path("preset").asBoolean()) {
                send(post("/api/audiences/{audienceId}/reset-to-preset", audiences.get(audience.path("code").stringValue())), null, 200);
            }
        }
    }

    private void addAudience(JsonNode audience) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", audience.path("code").stringValue());
        body.put("displayName", audience.path("displayName").stringValue());
        body.put("communicationIntent", audience.path("communicationIntent").stringValue());
        body.put("templateBody", audience.path("templates").path("main").stringValue());
        body.put("footerTemplateBody", audience.path("footers").path("main").stringValue());
        UUID id = UUID.fromString(field(send(post("/api/audiences"), body, 201), "id"));
        send(put("/api/audiences/{audienceId}/templates/vi", id), Map.of(
                "templateBody", audience.path("templates").path("vi").stringValue(),
                "footerTemplateBody", audience.path("footers").path("vi").stringValue()), 200);
    }

    private Map<String, UUID> audiences() throws Exception {
        Map<String, UUID> byCode = new LinkedHashMap<>();
        for (JsonNode audience : JSON.readTree(send(get("/api/audiences"), null, 200)).values()) {
            byCode.put(audience.path("code").stringValue(), UUID.fromString(audience.path("id").stringValue()));
        }
        return byCode;
    }

    private static JsonNode audience(JsonNode organization, String code) {
        for (JsonNode audience : organization.path("audiences").values()) {
            if (audience.path("code").stringValue().equals(code)) {
                return audience;
            }
        }
        throw new IllegalStateException("No audience " + code + " in the inputs");
    }

    private static JsonNode categoryNamed(JsonNode categories, String code) {
        for (JsonNode category : categories.values()) {
            if (category.path("code").stringValue().equals(code)) {
                return category;
            }
        }
        throw new IllegalStateException("No category " + code + " in the inputs");
    }

    // The source text, target language, and text of each answer DeepL gave the render.
    private static Map<List<String>, String> recordedAnswers() throws IOException {
        Map<List<String>, String> answers = new LinkedHashMap<>();
        for (String line : lines(new String(bytes("deepl/answers.jsonl"), StandardCharsets.UTF_8))) {
            JsonNode answer = JSON.readTree(line);
            answers.put(List.of(answer.path("source_lang").stringValue(), answer.path("target_lang").stringValue(),
                    answer.path("text").stringValue()), answer.path("translation").stringValue());
        }
        return answers;
    }

    // The package is the workspace's, unchanged: a file edited here fails before anything runs.
    private static void verifyManifest() throws IOException {
        for (String line : lines(new String(bytes("MANIFEST.sha256"), StandardCharsets.UTF_8))) {
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

    // A readable line diff of a note: "-" the snapshot, "+" what ReleaseFlow wrote, two lines of context.
    static String diff(String expected, String actual) {
        List<String> before = List.of(expected.split("\n", -1));
        List<String> after = List.of(actual.split("\n", -1));
        int[][] common = new int[before.size() + 1][after.size() + 1];
        for (int i = before.size() - 1; i >= 0; i--) {
            for (int j = after.size() - 1; j >= 0; j--) {
                common[i][j] = before.get(i).equals(after.get(j))
                        ? common[i + 1][j + 1] + 1
                        : Math.max(common[i + 1][j], common[i][j + 1]);
            }
        }
        List<String> edits = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < before.size() || j < after.size()) {
            if (i < before.size() && j < after.size() && before.get(i).equals(after.get(j))) {
                edits.add("  " + before.get(i++));
                j++;
            } else if (j < after.size() && (i == before.size() || common[i][j + 1] >= common[i + 1][j])) {
                edits.add("+ " + after.get(j++));
            } else {
                edits.add("- " + before.get(i++));
            }
        }
        StringBuilder shown = new StringBuilder();
        int lines = 0;
        int lastShown = -1;
        for (int index = 0; index < edits.size() && lines < MAX_DIFF_LINES; index++) {
            boolean near = false;
            for (int other = Math.max(0, index - 2); other <= Math.min(edits.size() - 1, index + 2); other++) {
                near |= !edits.get(other).startsWith("  ");
            }
            if (near) {
                if (lastShown >= 0 && index > lastShown + 1) {
                    shown.append("  …\n");
                }
                shown.append("    ").append(line(edits.get(index))).append('\n');
                lastShown = index;
                lines++;
            }
        }
        if (lines == MAX_DIFF_LINES) {
            shown.append("    (diff truncated)\n");
        }
        return shown.toString();
    }

    private static String line(String edit) {
        return edit.length() > 240 ? edit.substring(0, 240) + "…" : edit;
    }

    private static String key(JsonNode change) {
        return change.path("source").stringValue() + " " + change.path("reference").stringValue();
    }

    private static String reference(String key) {
        return key.substring(key.indexOf(' ') + 1);
    }

    // The number a person sees: #12, !7, OPS-5, DBV-35.
    private static int number(String reference) {
        return Integer.parseInt(reference.replaceFirst("^(#|!|[A-Z][A-Z0-9_]*-)", ""));
    }

    private static String text(JsonNode value) {
        return value.isString() ? value.stringValue() : null;
    }

    private static List<String> strings(JsonNode values) {
        List<String> strings = new ArrayList<>();
        values.values().forEach(value -> strings.add(value.stringValue()));
        return strings;
    }

    private static String field(String json, String name) {
        return JSON.readTree(json).path(name).asString();
    }

    private static JsonNode read(String file) throws IOException {
        return JSON.readTree(bytes(file));
    }

    private static List<String> lines(String text) {
        return text.lines().filter(line -> !line.isEmpty()).toList();
    }

    private static byte[] bytes(String file) throws IOException {
        try (InputStream in = GoldenReleaseNotesIntegrationTest.class.getClassLoader().getResourceAsStream(PACKAGE + file)) {
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
