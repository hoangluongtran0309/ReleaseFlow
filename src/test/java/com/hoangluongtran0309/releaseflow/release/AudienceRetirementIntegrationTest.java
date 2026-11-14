package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.audience.AudienceBrief;
import com.hoangluongtran0309.releaseflow.audience.AudienceService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Retiring an audience and bringing it back (ADR-0044). */
class AudienceRetirementIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String NARRATIVES = """
            {"operator":"Watch the export queue.","contributor":"Use ExportJob instead of TableExporter.",
             "end_user":"Download any table.","support":"Tell customers where the button is."}""";
    private static final String NARRATIVES_WITHOUT_SUPPORT = """
            {"operator":"Watch the export queue.","contributor":"Use ExportJob instead of TableExporter.",
             "end_user":"Download any table."}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private AudienceService audienceService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.execute("TRUNCATE public_changelog_entries, automation_action_runs, automation_runs, automation_publish_jobs, automation_rule_actions, automation_rules, translation_jobs, release_audience_notes, release_change_reviews, release_notes, release_changes, linked_issue_reads, release_linked_issues, release_fold_decisions, releases");
        jdbcTemplate.update("DELETE FROM audience_status_changes");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM projects");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM app_users");
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void aRetiredAudienceGetsNoNarrativeNoteOrPreviewAndItsPublishedNotesStay() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner);
        UUID first = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add table export", "FEATURE", false, false, null);
        TestChanges.summarize(jdbcTemplate, first, "Tables export as CSV.", NARRATIVES);
        UUID published = draftWithAllChanges(owner, projectId, "1.0.0");
        approve(owner, projectId, published).andExpect(jsonPath("$.notes.length()").value(4));
        action(owner, projectId, published, "publish").andExpect(status().isOk());
        String supportNote = jdbcTemplate.queryForObject(
                "SELECT content FROM release_audience_notes WHERE release_id = ? AND audience_code = 'support'",
                String.class, published);

        UUID support = audienceId(owner, "support");
        retire(owner, support)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retiredAt").isNotEmpty())
                .andExpect(jsonPath("$.retirerName").value("Mai Tran"));

        // The AI is asked for the active audiences only.
        assertThat(audienceService.briefs(owner.organizationId())).extracting(AudienceBrief::code)
                .containsExactly("contributor", "end_user", "operator");

        UUID second = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 2,
                "feat: add table import", "FEATURE", false, false, null);
        TestChanges.summarize(jdbcTemplate, second, "Tables import from CSV.", NARRATIVES_WITHOUT_SUPPORT);
        UUID next = draftWithAllChanges(owner, projectId, "1.1.0");
        mockMvc.perform(get(releaseApi(projectId, next) + "/note-previews").session(owner.session()))
                .andExpect(jsonPath("$[*].audienceCode").value(contains("contributor", "end_user", "operator")));
        approve(owner, projectId, next)
                .andExpect(jsonPath("$.notes.length()").value(3))
                .andExpect(jsonPath("$.notes[*].audienceCode").value(not(org.hamcrest.Matchers.hasItem("support"))));

        // Its published note is unchanged, readable, and downloadable.
        mockMvc.perform(get(releaseApi(projectId, published) + "/notes").session(owner.session()))
                .andExpect(jsonPath("$.length()").value(4));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT content FROM release_audience_notes WHERE release_id = ? AND audience_code = 'support'",
                String.class, published)).isEqualTo(supportNote);
        String noteId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM release_audience_notes WHERE release_id = ? AND audience_code = 'support'",
                String.class, published);
        mockMvc.perform(get(releaseApi(projectId, published) + "/notes/{noteId}/download", noteId).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(supportNote));
    }

    @Test
    void anApprovedReleaseKeepsTheNoteOfAnAudienceRetiredAfterItsApproval() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner);
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add table export", "FEATURE", false, false, null);
        TestChanges.summarize(jdbcTemplate, change, "Tables export as CSV.", NARRATIVES);
        UUID release = draftWithAllChanges(owner, projectId, "1.0.0");
        approve(owner, projectId, release);

        retire(owner, audienceId(owner, "support")).andExpect(status().isOk());

        // The approved release keeps the note it was approved with, and a summary edit keeps
        // the retired audience's narrative, so that note still reads as before.
        editSummary(owner, projectId, release, change, "Tables export as CSV files.",
                Map.of("operator", "Watch the export queue.", "contributor", "Use ExportJob.", "end_user", "Download any table."))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT audience_narratives -> 'support' ->> 'body' FROM changes WHERE id = ?", String.class, change))
                .isEqualTo("Tell customers where the button is.");
        mockMvc.perform(get(releaseApi(projectId, release)).session(owner.session()))
                .andExpect(jsonPath("$.notes.length()").value(4));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT content FROM release_audience_notes WHERE release_id = ? AND audience_code = 'support'",
                String.class, release))
                .contains("Tables export as CSV files.")
                .contains("Tell customers where the button is.");
    }

    @Test
    void anAudienceBroughtBackListsTheChangesWithoutItsNarrativeAndItsNoteStillIncludesThem() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner);
        UUID support = audienceId(owner, "support");
        retire(owner, support).andExpect(status().isOk());
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add table export", "FEATURE", false, false, null);
        TestChanges.summarize(jdbcTemplate, change, "Tables export as CSV.", NARRATIVES_WITHOUT_SUPPORT);

        restore(owner, support).andExpect(status().isOk()).andExpect(jsonPath("$.retiredAt").doesNotExist());
        UUID release = draftWithAllChanges(owner, projectId, "1.0.0");
        action(owner, projectId, release, "request-review")
                .andExpect(jsonPath("$.missingNarratives.length()").value(1))
                .andExpect(jsonPath("$.missingNarratives[0].changeId").value(change.toString()))
                .andExpect(jsonPath("$.missingNarratives[0].audienceNames[0]").value("Support"))
                // No AI provider is configured here, so nothing is offered to send.
                .andExpect(jsonPath("$.missingNarratives[0].estimatedTokens").value(0));
        mockMvc.perform(get("/projects/{projectId}/releases/{releaseId}", projectId, release).session(owner.session()))
                .andExpect(content().string(containsString("Changes with no narrative for an audience")))
                .andExpect(content().string(containsString("No narrative for Support")))
                .andExpect(content().string(not(containsString("Ask the AI ("))));

        approveRemaining(owner, projectId, release).andExpect(jsonPath("$.notes.length()").value(4));
        // Never dropped: the change is in Support's note under its headline, with no narrative.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT content FROM release_audience_notes WHERE release_id = ? AND audience_code = 'support'",
                String.class, release))
                .contains("Tables export as CSV.")
                .doesNotContain("Watch the export queue.");
        assertThat(jdbcTemplate.queryForList(
                "SELECT kind FROM audience_status_changes WHERE audience_id = ? ORDER BY changed_at", String.class, support))
                .containsExactly("RETIRED", "RESTORED");
    }

    @Test
    void theLastActiveAudienceCannotBeRetiredOrDeletedInEitherLanguage() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        for (String code : List.of("contributor", "end_user", "operator")) {
            retire(owner, audienceId(owner, code)).andExpect(status().isOk());
        }
        UUID support = audienceId(owner, "support");

        retire(owner, support)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("audience_last_active"))
                .andExpect(jsonPath("$.title").value("Last active audience"));
        mockMvc.perform(post("/api/audiences/{id}/retire", support).session(owner.session()).with(csrf())
                        .header("Accept-Language", "vi"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Audience đang dùng cuối cùng"));
        mockMvc.perform(delete(support, owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("audience_last"));

        // A retired one can still be deleted while nothing names it, and stepping twice is refused.
        retire(owner, audienceId(owner, "operator"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("audience_already_retired"));
        restore(owner, support)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("audience_not_retired"));
    }

    @Test
    void theDatabaseRefusesTwoRawRetirementsThatEachLeaveTheOther() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        for (String code : List.of("contributor", "end_user")) {
            retire(owner, audienceId(owner, code)).andExpect(status().isOk());
        }
        UUID operator = audienceId(owner, "operator");
        UUID support = audienceId(owner, "support");

        try (Connection one = dataSource.getConnection(); Connection two = dataSource.getConnection()) {
            one.setAutoCommit(false);
            two.setAutoCommit(false);
            retireRaw(one, operator, owner);
            retireRaw(two, support, owner);
            one.commit();
            assertThatThrownBy(two::commit)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("audience_definitions_keep_an_active_audience");
            two.rollback();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audience_definitions WHERE organization_id = ? AND retired_at IS NULL",
                Long.class, owner.organizationId())).isEqualTo(1);
    }

    @Test
    void onlyAdministratorsRetireAndBringBack() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID support = audienceId(owner, "support");
        jdbcTemplate.update("""
                INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                VALUES (?, ?, 'member@example.com', ?, 'Member', 'MEMBER', now())""",
                UUID.randomUUID(), owner.organizationId(), passwordEncoder.encode("member-password"));
        MockHttpSession member = login("member@example.com", "member-password");

        mockMvc.perform(post("/api/audiences/{id}/retire", support).session(member).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/audiences/{id}/retire", support).session(member).with(csrf()))
                .andExpect(status().isForbidden());

        // The page asks first, then retires and offers to bring it back.
        mockMvc.perform(get("/audiences/{id}", support).session(owner.session()))
                .andExpect(content().string(containsString("Retire audience")));
        mockMvc.perform(post("/audiences/{id}/retire", support).session(owner.session()).with(csrf()))
                .andExpect(status().isFound());
        mockMvc.perform(get("/audiences/{id}", support).session(owner.session()))
                .andExpect(content().string(containsString("Retired by Mai Tran")))
                .andExpect(content().string(containsString("Bring back")));
        mockMvc.perform(post("/audiences/{id}/restore", support).session(owner.session()).with(csrf()))
                .andExpect(status().isFound());
        mockMvc.perform(get("/audiences/{id}", support).session(owner.session()))
                .andExpect(content().string(containsString("Brought back by Mai Tran")));
    }

    private static void retireRaw(Connection connection, UUID audienceId, Owner owner) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE audience_definitions SET retired_at = now(), retired_by = ?, retirer_name = 'Raw' WHERE id = ?")) {
            statement.setObject(1, owner.userId());
            statement.setObject(2, audienceId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder delete(UUID audienceId, Owner owner) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/audiences/{id}", audienceId)
                .session(owner.session()).with(csrf());
    }

    private ResultActions retire(Owner owner, UUID audienceId) throws Exception {
        return mockMvc.perform(post("/api/audiences/{id}/retire", audienceId).session(owner.session()).with(csrf()));
    }

    private ResultActions restore(Owner owner, UUID audienceId) throws Exception {
        return mockMvc.perform(post("/api/audiences/{id}/restore", audienceId).session(owner.session()).with(csrf()));
    }

    private UUID audienceId(Owner owner, String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM audience_definitions WHERE organization_id = ? AND code = ?",
                UUID.class, owner.organizationId(), code);
    }

    private ResultActions editSummary(
            Owner owner, UUID projectId, UUID releaseId, UUID changeId, String whatChanged, Map<String, String> narratives
    ) throws Exception {
        StringBuilder json = new StringBuilder("{");
        narratives.forEach((code, text) -> json.append(json.length() > 1 ? "," : "")
                .append('"').append(code).append("\":\"").append(text).append('"'));
        json.append('}');
        return mockMvc.perform(put(releaseApi(projectId, releaseId) + "/changes/{changeId}/summary", changeId)
                .session(owner.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"whatChanged":"%s","whyChanged":"Users asked.","technicalDetail":"","migrationStep":"",
                         "narratives":%s}""".formatted(whatChanged, json)));
    }

    private ResultActions action(Owner owner, UUID projectId, UUID releaseId, String action) throws Exception {
        return mockMvc.perform(post(releaseApi(projectId, releaseId) + "/" + action).session(owner.session()).with(csrf()));
    }

    private ResultActions approve(Owner owner, UUID projectId, UUID releaseId) throws Exception {
        action(owner, projectId, releaseId, "request-review").andExpect(status().isOk());
        return approveRemaining(owner, projectId, releaseId);
    }

    private ResultActions approveRemaining(Owner owner, UUID projectId, UUID releaseId) throws Exception {
        String release = mockMvc.perform(get(releaseApi(projectId, releaseId)).session(owner.session()))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> changes = JsonPath.read(release, "$.changes");
        for (Map<String, Object> change : changes) {
            mockMvc.perform(put(releaseApi(projectId, releaseId) + "/changes/{changeId}/decision", change.get("id"))
                            .session(owner.session())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"APPROVE\",\"category\":\"%s\",\"breaking\":%s}".formatted(
                                    change.get("category").toString().toLowerCase(Locale.ROOT), change.get("breaking"))))
                    .andExpect(status().isOk());
        }
        return action(owner, projectId, releaseId, "approve")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    private UUID draftWithAllChanges(Owner owner, UUID projectId, String version) throws Exception {
        String body = mockMvc.perform(post("/api/projects/{projectId}/releases", projectId)
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"%s\"}".formatted(version)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID releaseId = UUID.fromString(JsonPath.read(body, "$.id"));
        mockMvc.perform(post(releaseApi(projectId, releaseId) + "/changes").session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allAvailable\":true}"))
                .andExpect(status().isOk());
        return releaseId;
    }

    private UUID createProject(Owner owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects").session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ReleaseFlow\"}"))
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
        return new Owner(registration.organizationId(), registration.userId(), login(email, "owner-password"));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login").with(csrf()).param("email", email).param("password", password))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private record Owner(UUID organizationId, UUID userId, MockHttpSession session) {
    }
}
