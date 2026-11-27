package com.hoangluongtran0309.releaseflow.configuration;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behind a proxy that terminates TLS, on a real Tomcat: every redirect of signing in and out
 * lands on the public address, and every cookie is marked {@code Secure}, although nothing
 * sets {@code RELEASEFLOW_SESSION_COOKIE_SECURE}. The browser resolves a relative
 * {@code Location} against the address it asked for, so that is how it is read here.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.request.header.forwarded.strategy=web-server")
class ForwardedHeaderIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String PUBLIC_ORIGIN = "https://releaseflow.example.com";
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, String> cookies = new LinkedHashMap<>();

    @LocalServerPort
    private int port;

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
    void signingInAndOutBehindAnHttpsProxyStaysOnThePublicAddress() throws Exception {
        register();

        HttpResponse<String> anonymous = send(get("/projects"), true);
        assertThat(anonymous.statusCode()).isEqualTo(302);
        assertThat(landing(anonymous, "/projects")).isEqualTo(PUBLIC_ORIGIN + "/login");
        assertThat(setCookie(anonymous, "JSESSIONID")).hasValueSatisfying(ForwardedHeaderIntegrationTest::isSecure);

        HttpResponse<String> signInPage = send(get("/login?lang=en"), true);
        assertThat(signInPage.statusCode()).isEqualTo(200);
        assertThat(setCookie(signInPage, "releaseflow_lang")).hasValueSatisfying(ForwardedHeaderIntegrationTest::isSecure);

        HttpResponse<String> signedIn = send(form("/login", Map.of(
                "_csrf", csrf(signInPage),
                "email", "owner@example.com",
                "password", "correct-password")), true);
        assertThat(signedIn.statusCode()).isEqualTo(302);
        assertThat(landing(signedIn, "/login")).isEqualTo(PUBLIC_ORIGIN + "/");
        // Signing in replaces the session, and the new one is just as Secure.
        assertThat(setCookie(signedIn, "JSESSIONID")).hasValueSatisfying(ForwardedHeaderIntegrationTest::isSecure);

        HttpResponse<String> overview = send(get("/"), true);
        assertThat(overview.statusCode()).isEqualTo(200);

        HttpResponse<String> signedOut = send(form("/logout", Map.of("_csrf", csrf(overview))), true);
        assertThat(signedOut.statusCode()).isEqualTo(302);
        assertThat(landing(signedOut, "/logout")).isEqualTo(PUBLIC_ORIGIN + "/login?logout");
    }

    @Test
    void withoutTheProxyHeadersNothingClaimsHttps() throws Exception {
        HttpResponse<String> anonymous = send(get("/projects"), false);
        assertThat(anonymous.statusCode()).isEqualTo(302);
        String direct = "http://127.0.0.1:" + port;
        assertThat(URI.create(direct + "/projects").resolve(location(anonymous)).toString())
                .isEqualTo(direct + "/login");
        assertThat(setCookie(anonymous, "JSESSIONID")).hasValueSatisfying(cookie ->
                assertThat(cookie).doesNotContainIgnoringCase("; Secure"));
    }

    private void register() {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName("Acme");
        request.setDisplayName("Owner");
        request.setEmail("owner@example.com");
        request.setPassword("correct-password");
        registrationService.register(request);
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
    }

    private HttpRequest.Builder form(String path, Map<String, String> fields) {
        String body = fields.entrySet().stream()
                .map(field -> field.getKey() + "=" + URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body));
    }

    /** Sends as a browser would through the proxy, keeping the cookies it was given. */
    private HttpResponse<String> send(HttpRequest.Builder request, boolean throughProxy)
            throws IOException, InterruptedException {
        request.header("Accept", "text/html");
        if (throughProxy) {
            request.header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "releaseflow.example.com");
        }
        if (!cookies.isEmpty()) {
            request.header("Cookie", cookies.entrySet().stream()
                    .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                    .collect(Collectors.joining("; ")));
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        for (String header : response.headers().allValues("Set-Cookie")) {
            String pair = header.split(";", 2)[0];
            cookies.put(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1));
        }
        return response;
    }

    /** Where the browser goes next: the {@code Location} resolved against the public page it asked for. */
    private static String landing(HttpResponse<String> response, String requestedPath) {
        return URI.create(PUBLIC_ORIGIN + requestedPath).resolve(location(response)).toString();
    }

    private static String location(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElseThrow();
    }

    private static Optional<String> setCookie(HttpResponse<String> response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst();
    }

    private static void isSecure(String setCookie) {
        assertThat(setCookie).containsIgnoringCase("; Secure");
    }

    private static String csrf(HttpResponse<String> page) {
        Matcher matched = CSRF.matcher(page.body());
        assertThat(matched.find()).as("a CSRF field on the page").isTrue();
        return matched.group(1);
    }
}
