package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.configuration.UiNotice;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
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
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectContextIntegrationTest extends PostgreSqlIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CONTEXT = "dbvault-cli is a client of the dbvault server.\nThe CLI stays English by design.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void administratorsWriteTheContextAndMembersReadIt() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");
        UUID project = createProject(admin);
        MockHttpSession member = member(admin.organizationId());

        mockMvc.perform(get("/api/projects/{projectId}/context", project).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(project.toString()))
                .andExpect(jsonPath("$.context").value(""))
                .andExpect(jsonPath("$.updatedBy", nullValue()));

        replace(admin.session(), project, "  " + CONTEXT.replace("\n", "\r\n") + "\n")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context").value(CONTEXT))
                .andExpect(jsonPath("$.updatedBy").value("Mai Tran"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        mockMvc.perform(get("/api/projects/{projectId}/context", project).session(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context").value(CONTEXT));
        replace(member, project, "Members cannot write it.").andExpect(status().isForbidden());
        mockMvc.perform(post("/projects/{projectId}/context", project).session(member).with(csrf())
                        .param("context", "Members cannot write it."))
                .andExpect(status().isForbidden());

        replace(admin.session(), project, "x".repeat(ProjectContextRequest.LIMIT + 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        replace(admin.session(), project, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context").value(""));
    }

    @Test
    void anotherOrganizationNeitherReadsNorWritesIt() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");
        Admin other = registerAndLogin("other@example.com");
        UUID project = createProject(admin);
        replace(admin.session(), project, CONTEXT).andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/{projectId}/context", project).session(other.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("project_not_found"));
        replace(other.session(), project, "Taken.")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("project_not_found"));
        mockMvc.perform(get("/projects/{projectId}/context", project).session(other.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects/{projectId}/context", project).session(admin.session()))
                .andExpect(jsonPath("$.context").value(CONTEXT));
    }

    @Test
    void theContextPageSavesItAndShowsItToMembers() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");
        UUID project = createProject(admin);

        mockMvc.perform(get("/projects").session(admin.session()))
                .andExpect(content().string(containsString("href=\"/projects/" + project + "/context\"")));
        mockMvc.perform(get("/projects/{projectId}/context", project).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<textarea")));

        mockMvc.perform(post("/projects/{projectId}/context", project).session(admin.session()).with(csrf())
                        .param("context", CONTEXT.replace("\n", "\r\n")))
                .andExpect(redirectedUrl("/projects/" + project + "/context"))
                .andExpect(flash().attribute(UiNotice.FLASH_ATTRIBUTE, UiNotice.success("ui.projectContext.saved")));
        mockMvc.perform(get("/projects/{projectId}/context", project).session(admin.session()))
                .andExpect(content().string(containsString(CONTEXT + "</textarea>")))
                .andExpect(content().string(containsString("Last changed by")));

        mockMvc.perform(post("/projects/{projectId}/context", project).session(admin.session()).with(csrf())
                        .param("context", "x".repeat(ProjectContextRequest.LIMIT + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("The context must be at most 2000 characters.")))
                .andExpect(content().string(containsString("aria-invalid=\"true\"")));

        mockMvc.perform(get("/projects/{projectId}/context", project).session(member(admin.organizationId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("The CLI stays English by design.")))
                .andExpect(content().string(containsString("Only organization administrators can change it.")))
                .andExpect(content().string(not(containsString("<textarea"))));
    }

    private ResultActions replace(MockHttpSession session, UUID project, String context) throws Exception {
        return mockMvc.perform(put("/api/projects/{projectId}/context", project).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Map.of("context", context))));
    }

    private UUID createProject(Admin admin) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects").session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"dbvault\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private MockHttpSession member(UUID organizationId) throws Exception {
        jdbcTemplate.update(
                """
                        INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, 'member@example.com', ?, 'Member', 'MEMBER', now())
                        ON CONFLICT DO NOTHING
                        """,
                UUID.randomUUID(), organizationId, passwordEncoder.encode("member-password")
        );
        return login("member@example.com", "member-password");
    }

    private Admin registerAndLogin(String email) throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName(email);
        request.setDisplayName("Mai Tran");
        request.setEmail(email);
        request.setPassword("owner-password");
        RegistrationResult registration = registrationService.register(request);
        return new Admin(registration.organizationId(), login(email, "owner-password"));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login").with(csrf()).param("email", email).param("password", password))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private record Admin(UUID organizationId, MockHttpSession session) {
    }
}
