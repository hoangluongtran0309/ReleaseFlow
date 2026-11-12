package com.hoangluongtran0309.releaseflow.account;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Changing a member's role and removing a member (ADR-0043). */
class MemberManagementIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String PASSWORD = "member-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM membership_changes");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM organization_invitations");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void removingAMemberEndsEverySessionOnItsNextRequestAndKeepsTheirName() throws Exception {
        Admin admin = register("owner@example.com");
        UUID memberId = insertMember(admin, "linh@example.com", "Linh Pham", AppUserRole.MEMBER);
        MockHttpSession memberApi = login("linh@example.com");
        MockHttpSession memberPages = login("linh@example.com");
        UUID projectId = createProject(memberApi);
        UUID changeId = TestChanges.insert(jdbcTemplate, admin.organizationId(), projectId, 7, "fix: keep the name",
                "FIX", false, true, null);
        mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/review", projectId, changeId)
                        .session(memberApi).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"fix\",\"breaking\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/members/{memberId}", memberId).session(admin.session()).with(csrf()))
                .andExpect(status().isNoContent());

        // Both sessions stop on their next request: the API answers as without a session, a page signs out.
        mockMvc.perform(get("/api/session").session(memberApi))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("authentication_required"));
        assertThat(memberApi.isInvalid()).isTrue();
        mockMvc.perform(get("/changes").session(memberPages))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?ended"));
        mockMvc.perform(get("/login").param("ended", ""))
                .andExpect(content().string(containsString("your access to this Organization changed")));
        // They cannot sign in again.
        mockMvc.perform(post("/login").with(csrf()).param("email", "linh@example.com").param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?error"));

        // The row stays with its name and email, and nothing that could sign in.
        assertThat(jdbcTemplate.queryForMap(
                "SELECT display_name, email, password_hash, removed_by FROM app_users WHERE id = ?", memberId))
                .containsEntry("display_name", "Linh Pham")
                .containsEntry("email", "linh@example.com")
                .containsEntry("password_hash", null)
                .containsEntry("removed_by", admin.userId());
        // What they recorded still names them.
        mockMvc.perform(get("/changes").param("project", projectId.toString()).session(admin.session()))
                .andExpect(content().string(containsString("Linh Pham")));
        mockMvc.perform(get("/api/projects/{projectId}/changes", projectId).session(admin.session()))
                .andExpect(jsonPath("$[0].reviewerName").value("Linh Pham"));

        mockMvc.perform(get("/api/members").session(admin.session()))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/members/changes").session(admin.session()))
                .andExpect(jsonPath("$[0].kind").value("REMOVED"))
                .andExpect(jsonPath("$[0].memberName").value("Linh Pham"))
                .andExpect(jsonPath("$[0].previousRole").value("MEMBER"))
                .andExpect(jsonPath("$[0].newRole").value(nullValue()))
                .andExpect(jsonPath("$[0].changedByName").value("Owner"));
        mockMvc.perform(get("/members").session(admin.session()))
                .andExpect(content().string(containsString("Removed members")))
                .andExpect(content().string(containsString("Owner removed Linh Pham.")));

        // Removed is gone: the same request finds no member.
        mockMvc.perform(delete("/api/members/{memberId}", memberId).session(admin.session()).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("member_not_found"));
    }

    @Test
    void aRoleChangeEndsTheSessionsMadeWithTheOldRole() throws Exception {
        Admin admin = register("owner@example.com");
        UUID memberId = insertMember(admin, "linh@example.com", "Linh Pham", AppUserRole.MEMBER);
        MockHttpSession member = login("linh@example.com");
        mockMvc.perform(get("/api/members").session(member)).andExpect(status().isForbidden());

        mockMvc.perform(put("/api/members/{memberId}/role", memberId).session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // The old session is refused even though the new role would allow more; a new one has it.
        mockMvc.perform(get("/api/session").session(member)).andExpect(status().isUnauthorized());
        MockHttpSession promoted = login("linh@example.com");
        mockMvc.perform(get("/api/members").session(promoted)).andExpect(status().isOk());

        // The same role again changes nothing, records nothing, and ends nothing.
        mockMvc.perform(put("/api/members/{memberId}/role", memberId).session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/session").session(promoted)).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM membership_changes", Long.class)).isEqualTo(1);

        mockMvc.perform(put("/api/members/{memberId}/role", memberId).session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void theLastAdministratorCannotBeDemotedOrRemovedInEitherLanguage() throws Exception {
        Admin admin = register("owner@example.com");

        mockMvc.perform(put("/api/members/{memberId}/role", admin.userId()).session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("member_last_administrator"))
                .andExpect(jsonPath("$.title").value("Last administrator"));
        mockMvc.perform(delete("/api/members/{memberId}", admin.userId()).session(admin.session()).with(csrf())
                        .header("Accept-Language", "vi"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("member_last_administrator"))
                .andExpect(jsonPath("$.title").value("Quản trị viên cuối cùng"));
        mockMvc.perform(post("/members/{memberId}/remove", admin.userId()).session(admin.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("keeps at least one administrator")));

        // The refused change left the session alone.
        mockMvc.perform(get("/api/session").session(admin.session())).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM membership_changes", Long.class)).isZero();
    }

    @Test
    void twoAdministratorsDemotingEachOtherAtOnceLeaveOne() throws Exception {
        Admin first = register("owner@example.com");
        UUID secondId = insertMember(first, "second@example.com", "Second", AppUserRole.ADMIN);
        ReleaseFlowPrincipal firstPrincipal = principal(first.userId());
        ReleaseFlowPrincipal secondPrincipal = principal(secondId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Throwable>> outcomes = new ArrayList<>();
            outcomes.add(executor.submit(() -> attempt(start,
                    () -> memberService.changeRole(firstPrincipal, secondId, AppUserRole.MEMBER))));
            outcomes.add(executor.submit(() -> attempt(start,
                    () -> memberService.changeRole(secondPrincipal, first.userId(), AppUserRole.MEMBER))));
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> outcome : outcomes) {
                Throwable failure = outcome.get(30, TimeUnit.SECONDS);
                if (failure != null) {
                    failures.add(failure);
                }
            }
            assertThat(failures).singleElement().isInstanceOf(LastAdministratorException.class);
        } finally {
            executor.shutdownNow();
        }
        assertThat(administrators(first.organizationId())).isEqualTo(1);
    }

    @Test
    void theDatabaseRefusesTwoRawDemotionsThatEachLeaveTheOther() throws Exception {
        Admin first = register("owner@example.com");
        UUID secondId = insertMember(first, "second@example.com", "Second", AppUserRole.ADMIN);

        try (Connection one = dataSource.getConnection(); Connection two = dataSource.getConnection()) {
            one.setAutoCommit(false);
            two.setAutoCommit(false);
            demote(one, secondId);
            demote(two, first.userId());
            one.commit();
            // The trigger waits for the first commit's lock, then sees it, and refuses.
            assertThatThrownBy(two::commit)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("app_users_keep_an_administrator");
            two.rollback();
        }
        assertThat(administrators(first.organizationId())).isEqualTo(1);
    }

    @Test
    void demotingOrRemovingRevokesTheInvitationsThePersonMadeAndCountsThem() throws Exception {
        Admin admin = register("owner@example.com");
        UUID secondId = insertMember(admin, "second@example.com", "Second", AppUserRole.ADMIN);
        MockHttpSession second = login("second@example.com");
        invite(second, "a@example.com");
        invite(second, "b@example.com");
        invite(admin.session(), "c@example.com");

        mockMvc.perform(put("/api/members/{memberId}/role", secondId).session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForList(
                "SELECT email FROM organization_invitations WHERE status = 'REVOKED' ORDER BY email", String.class))
                .containsExactly("a@example.com", "b@example.com");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM organization_invitations WHERE email = 'c@example.com'", String.class))
                .isEqualTo("PENDING");
        mockMvc.perform(get("/api/members/changes").session(admin.session()))
                .andExpect(jsonPath("$[0].kind").value("ROLE_CHANGED"))
                .andExpect(jsonPath("$[0].previousRole").value("ADMIN"))
                .andExpect(jsonPath("$[0].newRole").value("MEMBER"))
                .andExpect(jsonPath("$[0].revokedInvitations").value(2));

        // The invitation the remover's colleague made goes when that colleague is removed.
        UUID thirdId = insertMember(admin, "third@example.com", "Third", AppUserRole.ADMIN);
        invite(login("third@example.com"), "d@example.com");
        mockMvc.perform(delete("/api/members/{memberId}", thirdId).session(admin.session()).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/members/changes").session(admin.session()))
                .andExpect(jsonPath("$[0].kind").value("REMOVED"))
                .andExpect(jsonPath("$[0].revokedInvitations").value(1));
        mockMvc.perform(get("/members").session(admin.session()))
                .andExpect(content().string(containsString("Owner changed Second from Admin to Member.")))
                .andExpect(content().string(containsString("2 pending invitations revoked.")));
    }

    @Test
    void aPersonInvitedBackIsANewMemberAndTheirOldRecordsStayWithTheOldOne() throws Exception {
        Admin admin = register("owner@example.com");
        UUID oldId = insertMember(admin, "linh@example.com", "Linh Pham", AppUserRole.MEMBER);
        mockMvc.perform(delete("/api/members/{memberId}", oldId).session(admin.session()).with(csrf()))
                .andExpect(status().isNoContent());

        String link = invite(admin.session(), "linh@example.com");
        String token = link.substring(link.indexOf("#token=") + "#token=".length());
        String accepted = mockMvc.perform(post("/api/public/invitations/accept").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"displayName\":\"Linh P.\",\"password\":\"%s\"}".formatted(token, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID newId = UUID.fromString(JsonPath.read(accepted, "$.userId"));

        assertThat(newId).isNotEqualTo(oldId);
        mockMvc.perform(get("/api/members").session(admin.session()))
                .andExpect(jsonPath("$[1].id").value(newId.toString()))
                .andExpect(jsonPath("$[1].displayName").value("Linh P."))
                .andExpect(jsonPath("$[1].role").value("MEMBER"));
        mockMvc.perform(get("/members").session(admin.session()))
                .andExpect(content().string(containsString("Linh Pham")));
        // Signing in reaches the new member, never the removed one.
        MockHttpSession back = login("linh@example.com");
        mockMvc.perform(get("/api/session").session(back)).andExpect(jsonPath("$.userId").value(newId.toString()));
    }

    @Test
    void aRemovedAddressIsFreeToRegisterAnotherOrganization() throws Exception {
        Admin admin = register("owner@example.com");
        UUID memberId = insertMember(admin, "linh@example.com", "Linh Pham", AppUserRole.MEMBER);
        mockMvc.perform(delete("/api/members/{memberId}", memberId).session(admin.session()).with(csrf()))
                .andExpect(status().isNoContent());

        RegistrationRequest registration = new RegistrationRequest();
        registration.setOrganizationName("Linh's own");
        registration.setDisplayName("Linh Pham");
        registration.setEmail("linh@example.com");
        registration.setPassword("owner-password");
        assertThat(registrationService.register(registration).organizationId()).isNotEqualTo(admin.organizationId());
    }

    @Test
    void onlyAdministratorsOfTheSameOrganizationChangeMembers() throws Exception {
        Admin admin = register("owner@example.com");
        Admin other = register("other@example.com");
        UUID memberId = insertMember(admin, "linh@example.com", "Linh Pham", AppUserRole.MEMBER);
        MockHttpSession member = login("linh@example.com");

        mockMvc.perform(delete("/api/members/{memberId}", admin.userId()).session(member).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/members/{memberId}/role", admin.userId()).session(member).with(csrf()).param("role", "MEMBER"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/members/{memberId}", memberId).session(other.session()).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("member_not_found"));
        mockMvc.perform(post("/members/{memberId}/remove", memberId).session(other.session()).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(appUserRepository.findById(memberId).orElseThrow().isRemoved()).isFalse();
    }

    @Test
    void theMembersPageChangesARoleAndRemovesAMember() throws Exception {
        Admin admin = register("owner@example.com");
        UUID memberId = insertMember(admin, "linh@example.com", "Linh Pham", AppUserRole.MEMBER);

        mockMvc.perform(get("/members").session(admin.session()))
                .andExpect(content().string(containsString("Change role")))
                .andExpect(content().string(containsString("Remove Linh Pham?")))
                .andExpect(content().string(containsString("When someone leaves")))
                .andExpect(content().string(not(containsString("Removed members"))));
        mockMvc.perform(post("/members/{memberId}/role", memberId).session(admin.session()).with(csrf()).param("role", "ADMIN"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/members"));
        mockMvc.perform(post("/members/{memberId}/remove", memberId).session(admin.session()).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/members"));
        assertThat(appUserRepository.findById(memberId).orElseThrow().isRemoved()).isTrue();

        // An administrator who removes themselves is signed out on their next page.
        UUID secondId = insertMember(admin, "second@example.com", "Second", AppUserRole.ADMIN);
        MockHttpSession second = login("second@example.com");
        mockMvc.perform(post("/members/{memberId}/remove", secondId).session(second).with(csrf()))
                .andExpect(redirectedUrl("/members"));
        mockMvc.perform(get("/members").session(second)).andExpect(redirectedUrl("/login?ended"));
    }

    private static Throwable attempt(CountDownLatch start, Runnable change) {
        try {
            start.await();
            change.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static void demote(Connection connection, UUID userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE app_users SET role = 'MEMBER', session_version = session_version + 1 WHERE id = ?")) {
            statement.setObject(1, userId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private long administrators(UUID organizationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE organization_id = ? AND role = 'ADMIN' AND removed_at IS NULL",
                Long.class, organizationId);
    }

    private ReleaseFlowPrincipal principal(UUID userId) {
        return new ReleaseFlowPrincipal(appUserRepository.findById(userId).orElseThrow());
    }

    private String invite(MockHttpSession session, String email) throws Exception {
        String issued = mockMvc.perform(post("/api/invitations").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(issued, "$.acceptancePath");
    }

    private UUID createProject(MockHttpSession session) throws Exception {
        String project = mockMvc.perform(post("/api/projects").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ReleaseFlow\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(project, "$.id"));
    }

    private UUID insertMember(Admin admin, String email, String name, AppUserRole role) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, now())
                        """,
                id, admin.organizationId(), email, passwordEncoder.encode(PASSWORD), name, role.name());
        return id;
    }

    private Admin register(String email) throws Exception {
        RegistrationRequest registration = new RegistrationRequest();
        registration.setOrganizationName(email);
        registration.setDisplayName("Owner");
        registration.setEmail(email);
        registration.setPassword("owner-password");
        RegistrationResult registered = registrationService.register(registration);
        MvcResult login = mockMvc.perform(post("/login").with(csrf()).param("email", email).param("password", "owner-password"))
                .andExpect(status().isFound())
                .andReturn();
        return new Admin(registered.organizationId(),
                appUserRepository.findByEmailAndRemovedAtIsNull(email).orElseThrow().getId(),
                (MockHttpSession) login.getRequest().getSession(false));
    }

    private MockHttpSession login(String email) throws Exception {
        ResultActions login = mockMvc.perform(post("/login").with(csrf()).param("email", email).param("password", PASSWORD))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"));
        return (MockHttpSession) login.andReturn().getRequest().getSession(false);
    }

    private record Admin(UUID organizationId, UUID userId, MockHttpSession session) {
    }
}
