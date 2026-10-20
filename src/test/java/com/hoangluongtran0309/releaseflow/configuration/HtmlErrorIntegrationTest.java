package com.hoangluongtran0309.releaseflow.configuration;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * A request that fails outside a controller with its own answer. A browser gets a page; a
 * REST client keeps the Problem Details it would have got before.
 */
class HtmlErrorIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM organization_invitations");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void anUnknownAddressAnswersABrowserWithAPage() throws Exception {
        MockHttpSession session = registerAndLogin();

        mockMvc.perform(get("/nope").session(session).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(content().string(Matchers.containsString("That page does not exist")))
                .andExpect(content().string(Matchers.containsString("/nope")))
                // Never the exception that brought it here.
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("exception"))));
    }

    @Test
    void anUnknownAddressAnswersEverybodyElseWithProblemDetails() throws Exception {
        MockHttpSession session = registerAndLogin();

        mockMvc.perform(get("/nope").session(session).accept(MediaType.ALL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));

        // An address under /api is an API address whatever it says it accepts.
        mockMvc.perform(get("/api/nope").session(session).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void theErrorPageIsWrittenInTheReadersLanguage() throws Exception {
        MockHttpSession session = registerAndLogin();

        mockMvc.perform(get("/nope").session(session)
                        .accept(MediaType.TEXT_HTML)
                        .cookie(new Cookie("releaseflow_lang", "vi")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(Matchers.containsString("Trang này không tồn tại")));

        mockMvc.perform(get("/nope").session(session)
                        .accept(MediaType.TEXT_HTML)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "vi"))
                .andExpect(content().string(Matchers.containsString("Trang này không tồn tại")));
    }

    @Test
    void aPageForSomethingThisOrganizationDoesNotHaveIsTheNotFoundPage() throws Exception {
        MockHttpSession session = registerAndLogin();
        String path = "/projects/" + UUID.randomUUID() + "/releases/" + UUID.randomUUID();

        mockMvc.perform(get(path).session(session).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("detail", UiNotice.error("error.release_not_found")))
                .andExpect(content().string(Matchers.containsString("That page does not exist")))
                .andExpect(content().string(Matchers.containsString("Release was not found.")));

        mockMvc.perform(get(path).session(session)
                        .accept(MediaType.TEXT_HTML)
                        .cookie(new Cookie("releaseflow_lang", "vi")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(Matchers.containsString("Không tìm thấy release.")));
    }

    @Test
    void aFaultIsShownAsAFaultWithoutItsDetails() throws Exception {
        MockHttpSession session = registerAndLogin();

        // What the container's error dispatch hands the error page after an exception.
        mockMvc.perform(get("/error").session(session)
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("database password is hunter2"))
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/projects"))
                .andExpect(content().string(Matchers.containsString("ReleaseFlow could not finish that")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("hunter2"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("IllegalStateException"))));
    }

    @Test
    void springsOwnRefusalsUnderTheApiCarryACodeAndTranslatedWords() throws Exception {
        MockHttpSession session = registerAndLogin();

        mockMvc.perform(patch("/api/projects").session(session).with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("method_not_allowed"))
                .andExpect(jsonPath("$.title").value("Method not allowed"));
        mockMvc.perform(get("/api/projects/not-a-uuid/releases").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_parameter"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("problemDetail."))));
        mockMvc.perform(get("/api/projects/not-a-uuid/releases").session(session)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "vi"))
                .andExpect(jsonPath("$.title").value("Tham số không hợp lệ"));
        mockMvc.perform(post("/api/projects").session(session).with(csrf())
                        .contentType(MediaType.TEXT_PLAIN).content("ReleaseFlow"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("unsupported_media_type"));
        // A browser that follows a wrong link to a page still gets a page.
        mockMvc.perform(get("/projects/not-a-uuid/releases/also-not").session(session).accept(MediaType.TEXT_HTML))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(Matchers.not(Matchers.containsString("problemDetail."))));
    }

    @Test
    void aFaultUnderTheApiIsProblemDetailsWithoutItsDetails() throws Exception {
        MockHttpSession session = registerAndLogin();

        // Even a caller that asks for a page: an address under /api is an API address.
        mockMvc.perform(get("/error").session(session)
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("database password is hunter2"))
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/audiences/42"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.title").value("Internal error"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("hunter2"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("IllegalStateException"))));
    }

    /**
     * The page a member meets is rendered by the container's error dispatch, which MockMvc
     * does not run — {@code ui-smoke.spec.js} checks the page itself in a real browser.
     * What belongs here is that the refusal happens at all, and shows nothing of the page.
     */
    @Test
    void aPageThatIsNotThisRolesIsRefusedWithoutShowingIt() throws Exception {
        MockHttpSession session = registerAndLogin();
        MockHttpSession member = inviteAndJoinAsMember(session);

        mockMvc.perform(get("/automation").session(member).accept(MediaType.TEXT_HTML))
                .andExpect(status().isForbidden())
                .andExpect(content().string(Matchers.not(Matchers.containsString("Run history"))));
    }

    @Test
    void aFormThatOutlivedItsSessionSendsTheBrowserToSignIn() throws Exception {
        MockHttpSession session = registerAndLogin();
        session.invalidate();

        // The form still carries the token of the session that ended.
        mockMvc.perform(post("/projects").with(csrf().useInvalidToken())
                        .accept(MediaType.TEXT_HTML)
                        .param("name", "Checkout"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?expired"));
        org.assertj.core.api.Assertions.assertThat(
                jdbcTemplate.queryForObject("SELECT count(*) FROM projects", Integer.class)).isZero();
    }

    @Test
    void aStaleFormFromSomebodyStillSignedInIsCalledStaleNotSomebodyElses() throws Exception {
        MockHttpSession session = registerAndLogin();

        mockMvc.perform(post("/projects").session(session).with(csrf().useInvalidToken())
                        .accept(MediaType.TEXT_HTML)
                        .param("name", "Checkout"))
                .andExpect(status().isForbidden())
                .andExpect(request().attribute(ApiAccessDeniedHandler.ERROR_REASON, ApiAccessDeniedHandler.FORM_EXPIRED));
    }

    @Test
    void aRoleRefusalIsNotMistakenForAStaleForm() throws Exception {
        MockHttpSession session = registerAndLogin();
        MockHttpSession member = inviteAndJoinAsMember(session);

        mockMvc.perform(get("/automation").session(member).accept(MediaType.TEXT_HTML))
                .andExpect(status().isForbidden())
                .andExpect(request().attribute(ApiAccessDeniedHandler.ERROR_REASON, Matchers.nullValue()));
    }

    @Test
    void anApiCallWithoutAValidTokenStillGetsProblemDetails() throws Exception {
        MockHttpSession session = registerAndLogin();
        session.invalidate();

        mockMvc.perform(post("/api/projects").with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Checkout\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    private MockHttpSession registerAndLogin() throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName("Acme");
        request.setDisplayName("Owner");
        request.setEmail("owner@example.com");
        request.setPassword("owner-password");
        registrationService.register(request);
        return login("owner@example.com", "owner-password");
    }

    private MockHttpSession inviteAndJoinAsMember(MockHttpSession admin) throws Exception {
        MvcResult issued = mockMvc.perform(post("/members/invitations").session(admin).with(csrf())
                        .param("email", "teammate@example.com"))
                .andExpect(status().isOk())
                .andReturn();
        String body = issued.getResponse().getContentAsString();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("accept-invite#token=([A-Za-z0-9_-]+)")
                .matcher(body);
        org.assertj.core.api.Assertions.assertThat(matcher.find()).as("the link is shown once").isTrue();

        mockMvc.perform(post("/accept-invite").with(csrf())
                        .param("token", matcher.group(1))
                        .param("displayName", "Teammate")
                        .param("password", "teammate-password"))
                .andExpect(status().isFound());
        return login("teammate@example.com", "teammate-password");
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login").with(csrf())
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }
}
