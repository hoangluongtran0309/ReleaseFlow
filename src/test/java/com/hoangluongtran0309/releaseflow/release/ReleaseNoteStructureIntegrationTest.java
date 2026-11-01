package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.hoangluongtran0309.releaseflow.support.TestChanges;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADR-0037: a release's own links and the notes' security section and footers. */
class ReleaseNoteStructureIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String GUIDE = "https://docs.example.com/upgrade/1.0";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        // Published releases reject DELETE by design; TRUNCATE bypasses row triggers.
        jdbcTemplate.execute("TRUNCATE public_changelog_entries, automation_action_runs, automation_runs, automation_publish_jobs, automation_rule_actions, automation_rules, translation_jobs, release_audience_notes, release_change_reviews, release_notes, release_changes, releases");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void theOperatorNoteOfAProjectWithOneGitHubSourceEndsWithAWorkingCompareLink() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner, "dbvault");
        connectGitHub(owner, projectId);
        TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1, "feat: add the vault", "FEATURE", false, false,
                null);
        UUID first = publishAll(owner, projectId, "v0.3.0", "{\"version\":\"v0.3.0\"}");
        UUID keyed = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 2,
                "fix: derive a key per target", "FIX", false, true, null);
        TestChanges.markSecurity(jdbcTemplate, keyed);
        TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 3, "feat!: require TLS for every target",
                "FEATURE", true, true, null);

        // Without a previous release named, it follows the most recently published one.
        UUID releaseId = createDraft(owner, projectId, "{\"version\":\"v1.0.0\",\"upgradeGuideUrl\":\"" + GUIDE + "\"}")
                .andExpect(jsonPath("$.previousReleaseId").value(first.toString()))
                .andExpect(jsonPath("$.previousVersion").value("v0.3.0"))
                .andExpect(jsonPath("$.upgradeGuideUrl").value(GUIDE))
                .andReturn().getResponse().getContentAsString().transform(body -> UUID.fromString(JsonPath.read(body, "$.id")));
        addAll(owner, projectId, releaseId);
        String approved = approve(owner, projectId, releaseId).andReturn().getResponse().getContentAsString();

        List<String> notes = JsonPath.read(approved, "$.notes[*].content");
        List<String> codes = JsonPath.read(approved, "$.notes[*].audienceCode");
        assertThat(codes).containsExactly("contributor", "end_user", "operator", "support");
        // A change marked as security is under 🔒 Security in every audience's note, and counted.
        assertThat(notes).allSatisfy(note -> assertThat(note)
                .contains("This release includes 2 changes: 1 breaking change and 1 security change.")
                .contains("> ⚠️ 1 breaking change requires action before upgrading: [read the upgrade guide](" + GUIDE + ").")
                .contains("\n## 🔒 Security\n\n- **derive a key per target**"));
        String compare = "**Compare:** [v0.3.0...v1.0.0](https://github.com/acme/dbvault/compare/v0.3.0...v1.0.0)";
        String upgrade = "**Upgrade guide:** [Read before upgrading](" + GUIDE + ")";
        assertThat(notes.get(2)).endsWith("\n\n" + compare + "\n\n" + upgrade + "\n");
        assertThat(notes.get(0)).endsWith("\n\n" + compare + "\n\n" + upgrade + "\n");
        assertThat(notes.get(1)).doesNotContain("Compare", "Upgrade guide:");
        assertThat(notes.get(3)).doesNotContain("Compare", "Upgrade guide:");
        // No preset links the release itself: the note is often that release's own page.
        assertThat(notes).noneSatisfy(note -> assertThat(note).contains("/releases/tag/"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT footer_template_snapshot FROM release_audience_notes WHERE release_id = ? AND audience_code = 'operator'",
                String.class, releaseId
        )).contains("{{#compareUrl}}");

        action(owner, projectId, releaseId, "publish").andExpect(status().isOk())
                .andExpect(jsonPath("$.notes[2].content", endsWith(upgrade + "\n")));
    }

    @Test
    void aProjectWithoutOneCodeHostGetsNoCompareLinkButKeepsTheGuide() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner, "dbvault");
        TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1, "feat: add the vault", "FEATURE", false, false,
                null);
        publishAll(owner, projectId, "v0.3.0", "{\"version\":\"v0.3.0\"}");
        TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 2, "feat: add the console", "FEATURE", false, false,
                null);
        UUID releaseId = idOf(createDraft(owner, projectId,
                "{\"version\":\"v1.0.0\",\"upgradeGuideUrl\":\"" + GUIDE + "\"}"));
        addAll(owner, projectId, releaseId);

        String operator = JsonPath.read(approve(owner, projectId, releaseId).andReturn().getResponse().getContentAsString(),
                "$.notes[2].content");

        assertThat(operator).endsWith("- **add the console** ([#2](https://github.com/acme/releaseflow/pull/2))\n\n"
                + "**Upgrade guide:** [Read before upgrading](" + GUIDE + ")\n");
    }

    @Test
    void aPersonChoosesOrClearsTheLinksOfADraftAndOlderCallersKeepThem() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner, "dbvault");
        UUID otherProject = createProject(owner, "cli");
        TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1, "feat: add the vault", "FEATURE", false, false,
                null);
        TestChanges.insert(jdbcTemplate, owner.organizationId(), otherProject, 2, "feat: add the client", "FEATURE", false,
                false, null);
        UUID first = publishAll(owner, projectId, "v0.1.0", "{\"version\":\"v0.1.0\"}");
        UUID otherFirst = publishAll(owner, otherProject, "v0.1.0", "{\"version\":\"v0.1.0\"}");
        TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 3, "feat: add the console", "FEATURE", false,
                false, null);
        UUID second = publishAll(owner, projectId, "v0.2.0", "{\"version\":\"v0.2.0\"}");

        UUID none = idOf(createDraft(owner, projectId, "{\"version\":\"v0.3.0\",\"previousReleaseId\":null}")
                .andExpect(jsonPath("$.previousReleaseId", nullValue()))
                .andExpect(jsonPath("$.previousVersion", nullValue()))
                .andExpect(jsonPath("$.upgradeGuideUrl", nullValue())));
        createDraft(owner, projectId, "{\"version\":\"v0.4.0\",\"previousReleaseId\":\"" + first + "\"}")
                .andExpect(jsonPath("$.previousVersion").value("v0.1.0"));
        createDraft(owner, projectId, "{\"version\":\"v0.5.0\",\"previousReleaseId\":\"" + otherFirst + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_previous_release"))
                .andExpect(jsonPath("$.detail").value("Choose a published release of this Project as the previous release."));
        createDraft(owner, projectId, "{\"version\":\"v0.5.0\",\"previousReleaseId\":\"" + none + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_previous_release"));
        for (String url : List.of("docs.example.com/upgrade", "ftp://docs.example.com/up", "https://me:pw@docs.example.com/up")) {
            createDraft(owner, projectId, "{\"version\":\"v0.5.0\",\"upgradeGuideUrl\":\"" + url + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("invalid_upgrade_guide_url"));
        }

        edit(owner, projectId, none, "{\"version\":\"v0.3.0\",\"previousReleaseId\":\"" + second + "\",\"upgradeGuideUrl\":\""
                + GUIDE + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousVersion").value("v0.2.0"))
                .andExpect(jsonPath("$.upgradeGuideUrl").value(GUIDE));
        // A caller written before ADR-0037 leaves both out, and both stay.
        edit(owner, projectId, none, "{\"version\":\"v0.3.1\",\"summary\":\"Smaller.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousReleaseId").value(second.toString()))
                .andExpect(jsonPath("$.upgradeGuideUrl").value(GUIDE));
        edit(owner, projectId, none, "{\"version\":\"v0.3.1\",\"previousReleaseId\":null,\"upgradeGuideUrl\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousReleaseId", nullValue()))
                .andExpect(jsonPath("$.upgradeGuideUrl", nullValue()));

        // The page preselects the most recently published release, and a person may pick none.
        mockMvc.perform(get("/releases").param("project", projectId.toString()).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "<option value=\"" + second + "\" selected=\"selected\">v0.2.0</option>")));
        mockMvc.perform(post("/projects/{projectId}/releases", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .param("version", "v0.6.0")
                        .param("upgradeGuideUrl", "")
                        .param("previousReleaseId", ""))
                .andExpect(status().isFound());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM releases WHERE version = 'v0.6.0' AND previous_release_id IS NULL", Long.class
        )).isEqualTo(1);
        mockMvc.perform(post("/projects/{projectId}/releases", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .param("version", "v0.7.0")
                        .param("upgradeGuideUrl", "docs.example.com")
                        .param("previousReleaseId", second.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(model().attributeHasFieldErrorCode("releaseRequest", "upgradeGuideUrl",
                        "error.invalid_upgrade_guide_url.shape"))
                .andExpect(content().string(not(containsString("Something went wrong"))));
    }

    private UUID publishAll(Owner owner, UUID projectId, String version, String body) throws Exception {
        UUID releaseId = idOf(createDraft(owner, projectId, body));
        addAll(owner, projectId, releaseId);
        approve(owner, projectId, releaseId);
        action(owner, projectId, releaseId, "publish").andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(version));
        return releaseId;
    }

    private ResultActions approve(Owner owner, UUID projectId, UUID releaseId) throws Exception {
        action(owner, projectId, releaseId, "request-review").andExpect(status().isOk());
        String release = mockMvc.perform(get(releaseApi(projectId, releaseId)).session(owner.session()))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> changes = JsonPath.read(release, "$.changes");
        for (Map<String, Object> change : changes) {
            mockMvc.perform(put(releaseApi(projectId, releaseId) + "/changes/{changeId}/decision", change.get("id"))
                            .session(owner.session())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"APPROVE\",\"category\":\"%s\",\"breaking\":%s,\"security\":%s}".formatted(
                                    change.get("category").toString().toLowerCase(Locale.ROOT), change.get("breaking"),
                                    change.get("security"))))
                    .andExpect(status().isOk());
        }
        return action(owner, projectId, releaseId, "approve").andExpect(status().isOk());
    }

    private ResultActions action(Owner owner, UUID projectId, UUID releaseId, String action) throws Exception {
        return mockMvc.perform(post(releaseApi(projectId, releaseId) + "/" + action)
                .session(owner.session())
                .with(csrf()));
    }

    private ResultActions createDraft(Owner owner, UUID projectId, String body) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/releases", projectId)
                .session(owner.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions edit(Owner owner, UUID projectId, UUID releaseId, String body) throws Exception {
        return mockMvc.perform(put(releaseApi(projectId, releaseId))
                .session(owner.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static UUID idOf(ResultActions created) throws Exception {
        String body = created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private void addAll(Owner owner, UUID projectId, UUID releaseId) throws Exception {
        mockMvc.perform(post(releaseApi(projectId, releaseId) + "/changes")
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allAvailable\":true}"))
                .andExpect(status().isOk());
    }

    private void connectGitHub(Owner owner, UUID projectId) throws Exception {
        mockMvc.perform(post("/api/projects/{projectId}/sources", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GITHUB\",\"owner\":\"acme\",\"repository\":\"dbvault\"}"))
                .andExpect(status().isCreated());
    }

    private UUID createProject(Owner owner, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private static String releaseApi(UUID projectId, UUID releaseId) {
        return "/api/projects/" + projectId + "/releases/" + releaseId;
    }

    private Owner registerAndLogin(String email) throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName(email);
        request.setDisplayName("Mai Tran");
        request.setEmail(email);
        request.setPassword("owner-password");
        RegistrationResult registration = registrationService.register(request);
        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "owner-password"))
                .andExpect(status().isFound())
                .andReturn();
        return new Owner(
                registration.organizationId(),
                registration.userId(),
                (MockHttpSession) login.getRequest().getSession(false)
        );
    }

    private record Owner(UUID organizationId, UUID userId, MockHttpSession session) {
    }
}
