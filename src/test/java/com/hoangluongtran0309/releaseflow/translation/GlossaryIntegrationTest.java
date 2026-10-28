package com.hoangluongtran0309.releaseflow.translation;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.DeepLStub;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.hoangluongtran0309.releaseflow.support.TestChanges;
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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An Organization's glossary, through its page and API, a stand-in for DeepL, and the release page (ADR-0036). */
class GlossaryIntegrationTest extends PostgreSqlIntegrationTest {

    private static final DeepLStub DEEPL = DeepLStub.start();
    private static final String CONTEXT = "dbvault is a database backup tool with a web console and a CLI.";
    private static final String NARRATIVES = """
            {"operator":"Watch the console.","contributor":"Use StorageProfile.","end_user":"See the console."}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private TranslationWorker worker;

    @Autowired
    private GlossarySyncWorker syncWorker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void deepLProperties(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.translation.provider", () -> "deepl");
        registry.add("releaseflow.deepl.api-key", () -> "deepl-test-key");
        registry.add("releaseflow.deepl.base-url", DEEPL::baseUrl);
    }

    @AfterAll
    static void stopStub() {
        DEEPL.close();
    }

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        DEEPL.reset();
        jdbcTemplate.execute("TRUNCATE public_changelog_entries, automation_action_runs, automation_runs, automation_publish_jobs, automation_rule_actions, automation_rules, translation_jobs, release_audience_notes, release_change_reviews, release_notes, release_changes, releases");
        jdbcTemplate.update("DELETE FROM glossary_syncs");
        jdbcTemplate.update("DELETE FROM translation_glossaries");
        jdbcTemplate.update("DELETE FROM glossary_kept_terms");
        jdbcTemplate.update("DELETE FROM glossary_translations");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM organization_translation_settings");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void administratorsEditTheGlossaryAndMembersReadIt() throws Exception {
        Owner owner = register("owner@example.com", "en", "vi");
        MockHttpSession member = member(owner.organizationId());

        keep(owner, "console", "CLI").andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(2));
        translate(owner, "en", "vi-VN", "storage", "lưu trữ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetLanguage").value("vi"));

        mockMvc.perform(get("/api/glossary").session(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keptTerms[*].term").value(contains("CLI", "console")))
                .andExpect(jsonPath("$.translations[0].sourceTerm").value("storage"))
                .andExpect(jsonPath("$.translations[0].targetTerm").value("lưu trữ"))
                .andExpect(jsonPath("$.pairs[0].sourceLanguage").value("en"))
                .andExpect(jsonPath("$.pairs[0].targetLanguage").value("vi"))
                .andExpect(jsonPath("$.pairs[0].entryCount").value(3))
                .andExpect(jsonPath("$.pairs[0].status").value("not_synced"))
                .andExpect(jsonPath("$.entryLimit").value(500));
        mockMvc.perform(post("/api/glossary/kept-terms").session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"terms\":[\"image\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
        mockMvc.perform(get("/glossary").session(member))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("lưu trữ")))
                .andExpect(content().string(containsString("Only organization administrators can change it.")))
                .andExpect(content().string(not(containsString("action=\"/glossary/kept-terms\""))));
        mockMvc.perform(post("/glossary/kept-terms").session(member).with(csrf()).param("terms", "image"))
                .andExpect(status().isForbidden());

        // The page writes through the same service.
        mockMvc.perform(post("/glossary/kept-terms").session(owner.session()).with(csrf()).param("terms", "  image "))
                .andExpect(redirectedUrl("/glossary"));
        String glossary = mockMvc.perform(get("/api/glossary").session(owner.session()))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(glossary, "$.keptTerms[*].term")).containsExactly("CLI", "console", "image");
        String imageId = JsonPath.<List<String>>read(glossary, "$.keptTerms[?(@.term == 'image')].id").getFirst();
        mockMvc.perform(delete("/api/glossary/kept-terms/{id}", imageId).session(owner.session()).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/glossary/kept-terms/{id}", imageId).session(owner.session()).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("glossary_term_not_found"));
    }

    @Test
    void invalidEntriesAreRefusedWithTheirCodesInTheReadersLanguage() throws Exception {
        Owner owner = register("owner@example.com", "en", "vi");
        keep(owner, "console").andExpect(status().isCreated());

        mockMvc.perform(post("/api/glossary/kept-terms").session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"terms\":[\" \",\"%s\",\"two\\nlines\"]}".formatted("x".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors['terms[0]']").value("Enter a term."))
                .andExpect(jsonPath("$.errors['terms[1]']").value("A term must not exceed 100 characters."))
                .andExpect(jsonPath("$.errors['terms[2]']").value("Write the term on one line, without control characters."));
        mockMvc.perform(post("/api/glossary/kept-terms").session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"terms\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        keep(owner, "image", "console")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("glossary_term_taken"))
                .andExpect(jsonPath("$.detail").value("The glossary already has \"console\"."));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM glossary_kept_terms", Long.class))
                .as("all or none").isEqualTo(1);
        keep(owner, "Console").andExpect(status().isCreated());

        translate(owner, "en", "en-GB", "storage", "storage")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.differentLanguages").value("Choose a language other than the one it is translated from."));
        translate(owner, "en", "xx", "storage", "lưu trữ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.targetLanguage").value("Enter a language code, such as en or vi."));
        translate(owner, "en", "vi", "storage", "lưu trữ").andExpect(status().isCreated());
        translate(owner, "en", "vi", "storage", "bộ nhớ")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("glossary_term_taken"));
        translate(owner, "en", "de", "storage", "Speicher").andExpect(status().isCreated());

        // Vietnamese readers get the same codes, in their language.
        mockMvc.perform(post("/api/glossary/kept-terms").session(owner.session()).with(csrf())
                        .header("Accept-Language", "vi")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"terms\":[\"console\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("glossary_term_taken"))
                .andExpect(jsonPath("$.title").value("Xung đột bảng thuật ngữ"))
                .andExpect(jsonPath("$.detail").value("Bảng thuật ngữ đã có \"console\"."));

        jdbcTemplate.update("""
                INSERT INTO glossary_kept_terms (id, organization_id, term, created_by, creator_name, created_at)
                SELECT gen_random_uuid(), ?, 'term ' || n, ?, 'Mai Tran', now() FROM generate_series(1, 496) n
                """, owner.organizationId(), owner.userId());
        keep(owner, "one more")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("glossary_limit"))
                .andExpect(jsonPath("$.detail").value("A glossary holds at most 500 entries. Delete one before adding another."));
    }

    @Test
    void suggestionsAreOfferedAndNothingIsSeeded() throws Exception {
        Owner owner = register("owner@example.com", "en", "vi");

        mockMvc.perform(get("/api/glossary").session(owner.session()))
                .andExpect(jsonPath("$.keptTerms.length()").value(0))
                .andExpect(jsonPath("$.translations.length()").value(0))
                .andExpect(jsonPath("$.pairs[0].status").value("empty"));
        mockMvc.perform(get("/api/glossary/suggestions").session(owner.session()))
                .andExpect(jsonPath("$").value(hasItem("console")))
                .andExpect(jsonPath("$.length()").value(GlossaryService.SUGGESTIONS.size()));

        mockMvc.perform(post("/glossary/suggestions").session(owner.session()).with(csrf())
                        .param("terms", "console").param("terms", "pull request"))
                .andExpect(redirectedUrl("/glossary"));
        mockMvc.perform(get("/api/glossary/suggestions").session(owner.session()))
                .andExpect(jsonPath("$").value(not(hasItem("console"))))
                .andExpect(jsonPath("$.length()").value(GlossaryService.SUGGESTIONS.size() - 2));
        mockMvc.perform(post("/glossary/suggestions").session(owner.session()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Choose at least one suggested term.")));
    }

    @Test
    void deepLGetsTheGlossaryAndTheContextAndTheReleaseSaysItWasApplied() throws Exception {
        Owner owner = register("owner@example.com", "en", "vi");
        keep(owner, "console", "CLI").andExpect(status().isCreated());
        translate(owner, "en", "vi", "storage", "lưu trữ").andExpect(status().isCreated());
        UUID projectId = createProject(owner);
        summarizedChange(owner, projectId, 1, "The console now shows storage use.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.1.0", "Console and storage.");

        approve(owner, projectId, releaseId);

        assertThat(DEEPL.requests()).as("approval only writes rows").isEmpty();
        mockMvc.perform(get(releaseApi(projectId, releaseId)).session(owner.session()))
                .andExpect(jsonPath("$.glossaries.length()").value(1))
                .andExpect(jsonPath("$.glossaries[0].language").value("vi"))
                .andExpect(jsonPath("$.glossaries[0].status").value("PENDING"))
                .andExpect(jsonPath("$.notes[?(@.language == 'en')].glossaryStatus").value(everyItem(is("NONE"))));
        mockMvc.perform(get(releasePage(projectId, releaseId)).session(owner.session()))
                .andExpect(content().string(containsString("Waiting for the glossary")));
        assertThat(worker.processOne()).as("translations wait for the sync").isFalse();

        assertThat(syncWorker.processOne()).isTrue();
        assertThat(DEEPL.glossaries().values()).singleElement().satisfies(glossary -> assertThat(glossary.get("en:vi"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("CLI", "CLI", "console", "console", "storage", "lưu trữ")));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM glossary_syncs", String.class)).isEqualTo("SYNCED");
        drainTranslations();

        String glossaryId = DEEPL.glossaries().keySet().iterator().next();
        assertThat(DEEPL.translations()).hasSize(2).allSatisfy(request -> {
            assertThat(request.glossaryId()).isEqualTo(glossaryId);
            assertThat(request.context()).isEqualTo(CONTEXT);
        });
        assertThat(DEEPL.translatedTexts()).as("the release's own summary goes the same way").contains("Console and storage.");
        String release = mockMvc.perform(get(releaseApi(projectId, releaseId)).session(owner.session()))
                .andExpect(jsonPath("$.glossaries[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.notes[?(@.language == 'vi')].glossaryStatus").value(everyItem(is("APPLIED"))))
                .andExpect(jsonPath("$.notes[*].translationStatus").value(everyItem(is("READY"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(release, "$.notes[?(@.language == 'vi')].content"))
                .allSatisfy(note -> assertThat(note).contains("The console now shows lưu trữ use.", "Console and lưu trữ."));
        mockMvc.perform(get(releasePage(projectId, releaseId)).session(owner.session()))
                .andExpect(content().string(containsString("id=\"glossary-vi\"")))
                .andExpect(content().string(containsString("Sent with every translation")))
                .andExpect(content().string(containsString("not that DeepL kept every term")));
        mockMvc.perform(get("/api/glossary").session(owner.session()))
                .andExpect(jsonPath("$.pairs[0].status").value("synced"));

        action(owner, projectId, releaseId, "publish").andExpect(status().isOk());
        mockMvc.perform(get(releaseApi(projectId, releaseId)).session(owner.session()))
                .andExpect(jsonPath("$.glossaries[0].status").value("APPLIED"));
        mockMvc.perform(get(releasePage(projectId, releaseId)).session(owner.session()))
                .andExpect(content().string(containsString("id=\"glossary-vi\"")));
    }

    @Test
    void anOrganizationWithoutAGlossaryTranslatesAsBefore() throws Exception {
        Owner owner = register("owner@example.com", "en", "vi");
        UUID projectId = createProject(owner);
        summarizedChange(owner, projectId, 1, "Tables export as CSV.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.1.0", null);

        approve(owner, projectId, releaseId);
        assertThat(syncWorker.processOne()).isFalse();
        drainTranslations();

        assertThat(DEEPL.translations()).singleElement().satisfies(request -> assertThat(request.glossaryId()).isNull());
        assertThat(jdbcTemplate.queryForObject("SELECT glossary_version FROM translation_jobs", String.class)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT glossary_outcome FROM translation_jobs", String.class)).isNull();
        mockMvc.perform(get(releaseApi(projectId, releaseId)).session(owner.session()))
                .andExpect(jsonPath("$.glossaries.length()").value(0))
                .andExpect(jsonPath("$.notes[*].glossaryStatus").value(everyItem(is("NONE"))));
        mockMvc.perform(get(releasePage(projectId, releaseId)).session(owner.session()))
                .andExpect(content().string(not(containsString("id=\"release-glossary\""))));
    }

    @Test
    void aGlossaryEditTranslatesAgainAtTheNextApprovalAndANoOpEditDoesNot() throws Exception {
        Owner owner = register("owner@example.com", "en", "vi");
        keep(owner, "console", "CLI").andExpect(status().isCreated());
        UUID projectId = createProject(owner);
        summarizedChange(owner, projectId, 1, "The console now shows storage use.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.1.0", null);
        approve(owner, projectId, releaseId);
        syncAndTranslate();
        assertThat(DEEPL.translations()).hasSize(1);

        // Deleting a term and adding it back leaves the dictionary, and its version, as they were.
        String glossary = mockMvc.perform(get("/api/glossary").session(owner.session()))
                .andReturn().getResponse().getContentAsString();
        String cliId = JsonPath.<List<String>>read(glossary, "$.keptTerms[?(@.term == 'CLI')].id").getFirst();
        mockMvc.perform(delete("/api/glossary/kept-terms/{id}", cliId).session(owner.session()).with(csrf()))
                .andExpect(status().isNoContent());
        keep(owner, "CLI").andExpect(status().isCreated());
        reapprove(owner, projectId, releaseId);
        assertThat(syncWorker.processOne()).isFalse();
        assertThat(worker.processOne()).isFalse();
        assertThat(DEEPL.requests()).as("nothing is sent twice").hasSize(3);
        mockMvc.perform(get(releaseApi(projectId, releaseId)).session(owner.session()))
                .andExpect(jsonPath("$.glossaries[0].status").value("APPLIED"));

        // A real edit changes the version: the dictionary is replaced and the texts translated again.
        translate(owner, "en", "vi", "storage", "lưu trữ").andExpect(status().isCreated());
        reapprove(owner, projectId, releaseId);
        mockMvc.perform(get(releaseApi(projectId, releaseId)).session(owner.session()))
                .andExpect(jsonPath("$.glossaries[0].status").value("PENDING"));
        syncAndTranslate();

        assertThat(DEEPL.requests().stream().filter(request -> request.method().equals("PUT")).toList())
                .singleElement().satisfies(request -> assertThat(request.path()).endsWith("/dictionaries"));
        assertThat(DEEPL.translations()).hasSize(2);
        assertThat(DEEPL.translations().getLast().texts()).contains("The console now shows storage use.");
        mockMvc.perform(get(releaseApi(projectId, releaseId)).session(owner.session()))
                .andExpect(jsonPath("$.glossaries[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.notes[?(@.language == 'vi')].content").value(everyItem(containsString("lưu trữ"))));
        assertThat(jdbcTemplate.queryForObject("SELECT count(DISTINCT glossary_version) FROM translation_jobs", Long.class))
                .isEqualTo(2);
    }

    @Test
    void aGlossaryDeepLNoLongerHoldsIsCreatedAgain() throws Exception {
        Owner owner = register("owner@example.com", "en", "vi");
        keep(owner, "console").andExpect(status().isCreated());
        UUID projectId = createProject(owner);
        summarizedChange(owner, projectId, 1, "The console is faster.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.1.0", null);
        approve(owner, projectId, releaseId);
        syncAndTranslate();

        DEEPL.forgetGlossaries();
        keep(owner, "CLI").andExpect(status().isCreated());
        reapprove(owner, projectId, releaseId);
        syncAndTranslate();

        assertThat(DEEPL.glossaries()).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("SELECT deepl_glossary_id FROM translation_glossaries", String.class))
                .isEqualTo(DEEPL.glossaries().keySet().iterator().next());
        mockMvc.perform(get(releaseApi(projectId, releaseId)).session(owner.session()))
                .andExpect(jsonPath("$.glossaries[0].status").value("APPLIED"));
    }

    @Test
    void anUnsupportedPairTranslatesWithoutTheGlossaryAndSaysWhy() throws Exception {
        DEEPL.glossaryLanguages(Set.of("en", "de"));
        Owner owner = register("owner@example.com", "en", "vi");
        keep(owner, "console").andExpect(status().isCreated());
        UUID projectId = createProject(owner);
        summarizedChange(owner, projectId, 1, "The console is faster.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.1.0", null);
        approve(owner, projectId, releaseId);

        syncAndTranslate();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM glossary_syncs", String.class)).isEqualTo("UNSUPPORTED");
        assertThat(DEEPL.glossaries()).as("nothing is sent").isEmpty();
        assertThat(DEEPL.translations()).singleElement().satisfies(request -> assertThat(request.glossaryId()).isNull());
        mockMvc.perform(get(releaseApi(projectId, releaseId)).session(owner.session()))
                .andExpect(jsonPath("$.glossaries[0].status").value("NOT_APPLIED"))
                .andExpect(jsonPath("$.glossaries[0].reason").value("unsupported_language_pair"))
                .andExpect(jsonPath("$.notes[*].translationStatus").value(everyItem(is("READY"))));
        mockMvc.perform(get(releasePage(projectId, releaseId)).session(owner.session()))
                .andExpect(content().string(containsString("DeepL serves no glossary for this language pair.")))
                .andExpect(content().string(not(containsString("Retry translations to try the glossary again."))));
        mockMvc.perform(get("/glossary").session(owner.session()))
                .andExpect(content().string(containsString("Not served by DeepL")));
        action(owner, projectId, releaseId, "publish").andExpect(status().isOk());
    }

    @Test
    void aFailedSyncIsBoundedVisibleAndRetriedByHand() throws Exception {
        Owner owner = register("owner@example.com", "en", "vi");
        keep(owner, "console").andExpect(status().isCreated());
        UUID projectId = createProject(owner);
        summarizedChange(owner, projectId, 1, "The console is faster.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.1.0", null);
        approve(owner, projectId, releaseId);

        DEEPL.fail("/v3", 503, TranslationWorker.MAX_ATTEMPTS);
        for (int attempt = 1; attempt <= TranslationWorker.MAX_ATTEMPTS; attempt++) {
            makeSyncDue();
            assertThat(syncWorker.processOne()).isTrue();
        }
        makeSyncDue();
        assertThat(syncWorker.processOne()).as("bounded").isFalse();
        assertThat(jdbcTemplate.queryForMap("SELECT status, attempts, last_error FROM glossary_syncs"))
                .containsEntry("status", "FAILED")
                .containsEntry("attempts", TranslationWorker.MAX_ATTEMPTS)
                .containsEntry("last_error", TranslationException.UNAVAILABLE);

        drainTranslations();
        assertThat(DEEPL.translations()).as("never blocks translation")
                .singleElement().satisfies(request -> assertThat(request.glossaryId()).isNull());
        mockMvc.perform(get(releaseApi(projectId, releaseId)).session(owner.session()))
                .andExpect(jsonPath("$.glossaries[0].status").value("NOT_APPLIED"))
                .andExpect(jsonPath("$.glossaries[0].reason").value("glossary_sync_failed"));
        mockMvc.perform(get(releasePage(projectId, releaseId)).session(owner.session()))
                .andExpect(content().string(containsString("The glossary could not be synced to DeepL.")))
                .andExpect(content().string(containsString("Retry translations to try the glossary again.")));
        mockMvc.perform(get("/glossary").session(owner.session()))
                .andExpect(content().string(containsString("Sync failed")))
                .andExpect(content().string(containsString(TranslationException.UNAVAILABLE)));

        mockMvc.perform(post(releaseApi(projectId, releaseId) + "/translations/retry").session(owner.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.glossaries[0].status").value("PENDING"));
        syncAndTranslate();
        assertThat(DEEPL.translations().getLast().glossaryId()).isNotNull();
        mockMvc.perform(get(releaseApi(projectId, releaseId)).session(owner.session()))
                .andExpect(jsonPath("$.glossaries[0].status").value("APPLIED"));
    }

    @Test
    void aFreeAccountServesTheGlossaryOfOneOrganization() throws Exception {
        Owner first = register("first@example.com", "en", "vi");
        keep(first, "console").andExpect(status().isCreated());
        UUID firstProject = createProject(first);
        summarizedChange(first, firstProject, 1, "The console is faster.");
        UUID firstRelease = draftWithAllChanges(first, firstProject, "1.1.0", null);
        approve(first, firstProject, firstRelease);
        syncAndTranslate();

        Owner second = register("second@example.com", "en", "vi");
        keep(second, "CLI").andExpect(status().isCreated());
        UUID secondProject = createProject(second);
        summarizedChange(second, secondProject, 2, "The CLI is faster.");
        UUID secondRelease = draftWithAllChanges(second, secondProject, "2.0.0", null);
        approve(second, secondProject, secondRelease);
        syncAndTranslate();

        assertThat(jdbcTemplate.queryForMap("SELECT status, attempts, last_error FROM glossary_syncs WHERE organization_id = ?",
                second.organizationId()))
                .as("456 is a stop, never a retry")
                .containsEntry("status", "FAILED")
                .containsEntry("attempts", 1)
                .containsEntry("last_error", TranslationException.QUOTA_EXCEEDED);
        mockMvc.perform(get(releaseApi(secondProject, secondRelease)).session(second.session()))
                .andExpect(jsonPath("$.glossaries[0].reason").value("glossary_sync_failed"))
                .andExpect(jsonPath("$.notes[*].translationStatus").value(everyItem(is("READY"))));
        mockMvc.perform(get("/glossary").session(second.session()))
                .andExpect(content().string(containsString("A DeepL API Free account holds one")));
        mockMvc.perform(get(releaseApi(firstProject, firstRelease)).session(first.session()))
                .andExpect(jsonPath("$.glossaries[0].status").value("APPLIED"));
    }

    @Test
    void anAnswerLeftInEnglishIsTranslatedAgainWithoutTheGlossaryOrFails() throws Exception {
        Owner owner = register("owner@example.com", "en", "vi");
        keep(owner, "console").andExpect(status().isCreated());
        UUID projectId = createProject(owner);
        summarizedChange(owner, projectId, 1, "The console lists every backup.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.1.0", null);
        approve(owner, projectId, releaseId);

        DEEPL.answerUntranslatedWithGlossary();
        syncAndTranslate();

        assertThat(DEEPL.translations()).hasSize(2);
        assertThat(DEEPL.translations().getFirst().glossaryId()).isNotNull();
        assertThat(DEEPL.translations().getLast().glossaryId()).isNull();
        mockMvc.perform(get(releaseApi(projectId, releaseId)).session(owner.session()))
                .andExpect(jsonPath("$.notes[*].translationStatus").value(everyItem(is("READY"))))
                .andExpect(jsonPath("$.glossaries[0].reason").value("glossary_answer_untranslated"))
                .andExpect(jsonPath("$.notes[?(@.language == 'vi')].content")
                        .value(everyItem(containsString(DeepLStub.VIETNAMESE_MARKER + "The console lists every backup."))));
        mockMvc.perform(get(releasePage(projectId, releaseId)).session(owner.session()))
                .andExpect(content().string(containsString("With the glossary DeepL answered in the source language")));
        // A text of fewer than three words left to translate cannot be judged, so only the long one was refused.
        assertThat(jdbcTemplate.queryForList("SELECT translated_text FROM translation_cache", String.class))
                .as("the refused answer is not cached")
                .contains("[VI] " + DeepLStub.VIETNAMESE_MARKER + "The console lists every backup.")
                .doesNotContain("The console lists every backup.");

        // Left in English without the glossary too, the translation fails like any other.
        summarizedChange(owner, projectId, 2, "Backups now stream to every storage target.");
        reapprove(owner, projectId, releaseId);
        DEEPL.answerUntranslated();
        syncAndTranslate();
        assertThat(jdbcTemplate.queryForList("SELECT last_error FROM translation_jobs WHERE status = 'FAILED'", String.class))
                .containsExactly(TranslationException.UNTRANSLATED);
        mockMvc.perform(get(releaseApi(projectId, releaseId) + "/notes").session(owner.session()))
                .andExpect(jsonPath("$[?(@.language == 'vi')].translationStatus").value(everyItem(is("FAILED"))));
    }

    @Test
    void aRefusedGlossaryIsSentAgainBeforeTheTranslationIsRetried() throws Exception {
        Owner owner = register("owner@example.com", "en", "vi");
        keep(owner, "console").andExpect(status().isCreated());
        UUID projectId = createProject(owner);
        summarizedChange(owner, projectId, 1, "The console is faster.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.1.0", null);
        approve(owner, projectId, releaseId);
        assertThat(syncWorker.processOne()).isTrue();

        DEEPL.refuseGlossary(1);
        assertThat(worker.processOne()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM glossary_syncs", String.class)).isEqualTo("PENDING");
        makeTranslationsDue();
        assertThat(worker.processOne()).as("it waits for the sync").isFalse();
        syncAndTranslate();

        assertThat(DEEPL.translations()).hasSize(2).allSatisfy(request -> assertThat(request.glossaryId()).isNotNull());
        mockMvc.perform(get(releaseApi(projectId, releaseId)).session(owner.session()))
                .andExpect(jsonPath("$.glossaries[0].status").value("APPLIED"));
    }

    @Test
    void theGlossaryPageReadsInBothLanguages() throws Exception {
        Owner owner = register("owner@example.com", "en", "vi");
        keep(owner, "console").andExpect(status().isCreated());

        mockMvc.perform(get("/glossary").session(owner.session()))
                .andExpect(content().string(containsString("<h1 class=\"page-heading\">Glossary</h1>")))
                .andExpect(content().string(containsString("Kept as written")));
        mockMvc.perform(get("/glossary").param("lang", "vi").session(owner.session()))
                .andExpect(content().string(containsString("<h1 class=\"page-heading\">Bảng thuật ngữ</h1>")))
                .andExpect(content().string(containsString("Giữ nguyên")))
                .andExpect(content().string(containsString("Đồng bộ ở lần duyệt tới")));
        mockMvc.perform(post("/glossary/translations").session(owner.session()).with(csrf())
                        .header("Accept-Language", "vi")
                        .param("sourceLanguage", "en").param("targetLanguage", "en").param("sourceTerm", "").param("targetTerm", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Vui lòng nhập thuật ngữ.")))
                .andExpect(content().string(containsString("Vui lòng chọn ngôn ngữ khác với ngôn ngữ nguồn.")));
    }

    private void syncAndTranslate() {
        makeSyncDue();
        while (syncWorker.processOne()) {
            makeSyncDue();
        }
        drainTranslations();
    }

    private void drainTranslations() {
        makeTranslationsDue();
        while (worker.processOne()) {
            makeTranslationsDue();
        }
    }

    private void makeSyncDue() {
        jdbcTemplate.update("UPDATE glossary_syncs SET next_attempt_at = now() - interval '1 second' WHERE status = 'PENDING'");
    }

    private void makeTranslationsDue() {
        jdbcTemplate.update("UPDATE translation_jobs SET next_attempt_at = now() - interval '1 second' WHERE status = 'PENDING'");
    }

    private ResultActions keep(Owner owner, String... terms) throws Exception {
        return mockMvc.perform(post("/api/glossary/kept-terms").session(owner.session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"terms\":[\"" + String.join("\",\"", terms) + "\"]}"));
    }

    private ResultActions translate(Owner owner, String source, String target, String sourceTerm, String targetTerm)
            throws Exception {
        return mockMvc.perform(post("/api/glossary/translations").session(owner.session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceLanguage\":\"%s\",\"targetLanguage\":\"%s\",\"sourceTerm\":\"%s\",\"targetTerm\":\"%s\"}"
                        .formatted(source, target, sourceTerm, targetTerm)));
    }

    private UUID summarizedChange(Owner owner, UUID projectId, int number, String whatChanged) {
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, number,
                "feat: add export " + number, "FEATURE", false, false, null);
        TestChanges.summarize(jdbcTemplate, change, whatChanged, NARRATIVES);
        return change;
    }

    private ResultActions action(Owner owner, UUID projectId, UUID releaseId, String action) throws Exception {
        return mockMvc.perform(post(releaseApi(projectId, releaseId) + "/" + action).session(owner.session()).with(csrf()));
    }

    // Back to draft, every available change added, and approved again.
    private void reapprove(Owner owner, UUID projectId, UUID releaseId) throws Exception {
        action(owner, projectId, releaseId, "return-to-draft").andExpect(status().isOk());
        addAllChanges(owner, projectId, releaseId);
        approve(owner, projectId, releaseId);
    }

    // Asks for review, approves every change as shown, then approves the release.
    private void approve(Owner owner, UUID projectId, UUID releaseId) throws Exception {
        action(owner, projectId, releaseId, "request-review").andExpect(status().isOk());
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
        action(owner, projectId, releaseId, "approve")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    private UUID draftWithAllChanges(Owner owner, UUID projectId, String version, String summary) throws Exception {
        String body = mockMvc.perform(post("/api/projects/{projectId}/releases", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(summary == null
                                ? "{\"version\":\"%s\"}".formatted(version)
                                : "{\"version\":\"%s\",\"summary\":\"%s\"}".formatted(version, summary)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID releaseId = UUID.fromString(JsonPath.read(body, "$.id"));
        addAllChanges(owner, projectId, releaseId);
        return releaseId;
    }

    private void addAllChanges(Owner owner, UUID projectId, UUID releaseId) throws Exception {
        mockMvc.perform(post(releaseApi(projectId, releaseId) + "/changes")
                .session(owner.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"allAvailable\":true}"));
    }

    private UUID createProject(Owner owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"dbvault\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID projectId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
        mockMvc.perform(put("/api/projects/{projectId}/context", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"context\":\"" + CONTEXT + "\"}"))
                .andExpect(status().isOk());
        return projectId;
    }

    private static String releaseApi(UUID projectId, UUID releaseId) {
        return "/api/projects/" + projectId + "/releases/" + releaseId;
    }

    private static String releasePage(UUID projectId, UUID releaseId) {
        return "/projects/" + projectId + "/releases/" + releaseId;
    }

    private MockHttpSession member(UUID organizationId) throws Exception {
        jdbcTemplate.update("""
                INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                VALUES (?, ?, 'member@example.com', ?, 'Member', 'MEMBER', now())
                """, UUID.randomUUID(), organizationId, passwordEncoder.encode("member-password"));
        return login("member@example.com", "member-password");
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login").with(csrf()).param("email", email).param("password", password))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private Owner register(String email, String... languages) throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName(email);
        request.setDisplayName("Mai Tran");
        request.setEmail(email);
        request.setPassword("owner-password");
        RegistrationResult registration = registrationService.register(request);
        Owner owner = new Owner(registration.organizationId(), registration.userId(), login(email, "owner-password"));
        mockMvc.perform(put("/api/organization/release-languages")
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetLanguages\":[\"" + String.join("\",\"", languages) + "\"]}"))
                .andExpect(status().isOk());
        return owner;
    }

    private record Owner(UUID organizationId, UUID userId, MockHttpSession session) {
    }
}
