package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.DeepLStub;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.hoangluongtran0309.releaseflow.support.TestChanges;
import com.hoangluongtran0309.releaseflow.translation.TranslationQueue;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An approved release's summary and upgrade guide link change without a return to draft
 * (ADR-0047, amended 2026-11-23), translated through a stand-in for DeepL.
 */
class ReleaseEditAfterApprovalIntegrationTest extends PostgreSqlIntegrationTest {

    private static final DeepLStub DEEPL = DeepLStub.start();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String NARRATIVES = """
            {"operator":"Watch the export queue.","contributor":"Use ExportJob.","end_user":"Download any table."}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReleaseService releaseService;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
        jdbcTemplate.execute("TRUNCATE release_note_revisions, public_changelog_entry_revisions, public_changelog_entries, automation_action_runs, automation_runs, automation_publish_jobs, automation_rule_actions, automation_rules, translation_jobs, translation_cache, release_audience_notes, release_change_reviews, release_notes, release_changes, linked_issue_reads, release_linked_issues, release_fold_decisions, releases");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM organization_translation_settings");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM app_users");
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void changesTheSummaryAndLinkInReviewAndOnceApprovedButNeverTheVersionOrPreviousRelease() throws Exception {
        Owner owner = registerWithLanguages("owner@example.com", "en");
        UUID projectId = createProject(owner);
        summarizedChange(owner, projectId, 1, "Tables export as CSV.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0", "First summary.");
        action(owner, projectId, releaseId, "request-review").andExpect(status().isOk());

        // In review there is no note yet, so nothing is recorded either.
        edit(owner, projectId, releaseId, Map.of("version", "1.4.0", "summary", "Reviewed summary.",
                "upgradeGuideUrl", "https://docs.example.com/upgrade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Reviewed summary."))
                .andExpect(jsonPath("$.upgradeGuideUrl").value("https://docs.example.com/upgrade"))
                .andExpect(jsonPath("$.summaryChange").doesNotExist())
                .andExpect(jsonPath("$.upgradeGuideChange").doesNotExist());
        edit(owner, projectId, releaseId, Map.of("version", "1.4.1", "summary", "Reviewed summary."))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_status_conflict"));
        Map<String, Object> previous = new LinkedHashMap<>();
        previous.put("version", "1.4.0");
        previous.put("previousReleaseId", UUID.randomUUID().toString());
        edit(owner, projectId, releaseId, previous)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_status_conflict"));
        edit(owner, projectId, releaseId, Map.of("version", "1.4.0", "upgradeGuideUrl", "ftp://docs.example.com/upgrade"))
                .andExpect(status().isBadRequest());

        approveEverything(owner, projectId, releaseId);
        String before = notes(owner, projectId, releaseId);
        edit(owner, projectId, releaseId, Map.of("version", "1.4.0", "summary", "Approved summary.",
                "upgradeGuideUrl", "https://docs.example.com/upgrade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approverName").value("Mai Tran"))
                .andExpect(jsonPath("$.summaryChange.name").value("Mai Tran"))
                .andExpect(jsonPath("$.upgradeGuideChange").doesNotExist())
                .andExpect(jsonPath("$.notes[*].content").value(everyItem(containsString("Approved summary."))));
        assertThat(before).contains("Reviewed summary.");

        // A return to draft takes what changed after approval with the approval.
        action(owner, projectId, releaseId, "return-to-draft")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryChange").doesNotExist());
    }

    @Test
    void recordsWhoChangedItListsHandEditedNotesUntilTheyAreEditedAgainAndKeepsTheRecordOncePublished()
            throws Exception {
        Owner owner = registerWithLanguages("owner@example.com", "en");
        UUID projectId = createProject(owner);
        summarizedChange(owner, projectId, 1, "Tables export as CSV.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0", "First summary.");
        approveEverything(owner, projectId, releaseId);
        UUID operatorNote = noteId(releaseId, "operator", "en");
        editNote(owner, projectId, releaseId, operatorNote, "# Release 1.4.0\n\nWritten by hand.\n");

        // A member may approve, so a member may edit; the record keeps their name then.
        MockHttpSession member = member(owner.organizationId(), "member@example.com", "Linh Pham");
        mockMvc.perform(put(releaseApi(projectId, releaseId)).session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("version", "1.4.0", "summary", "Corrected summary."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryChange.name").value("Linh Pham"));
        assertThat(noteContent(owner, projectId, releaseId, operatorNote)).isEqualTo("# Release 1.4.0\n\nWritten by hand.\n");
        mockMvc.perform(get(releasePage(projectId, releaseId)).session(owner.session()))
                .andExpect(content().string(containsString("Summary changed after approval by Linh Pham on")))
                .andExpect(content().string(containsString("still show the earlier text")))
                .andExpect(content().string(containsString("Operator, en")));

        // Edited again after the change, the note was written from the new text.
        editNote(owner, projectId, releaseId, operatorNote, "# Release 1.4.0\n\nCorrected summary, by hand.\n");
        mockMvc.perform(get(releasePage(projectId, releaseId)).session(owner.session()))
                .andExpect(content().string(not(containsString("still show the earlier text"))));

        // The member leaves; the record keeps the name they had.
        jdbcTemplate.update("UPDATE app_users SET display_name = 'Someone else' WHERE email = 'member@example.com'");
        action(owner, projectId, releaseId, "publish").andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryChange.name").value("Linh Pham"));
        mockMvc.perform(get(releasePage(projectId, releaseId)).session(owner.session()))
                .andExpect(content().string(containsString("Summary changed after approval by Linh Pham on")));
        // Published, it never changes: the service says so, after the lock.
        edit(owner, projectId, releaseId, Map.of("version", "1.4.0", "summary", "Too late."))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_published"));
    }

    @Test
    void sendsTheNewSummaryOnceForEachLanguageAndNothingForTheLink() throws Exception {
        Owner owner = registerWithLanguages("owner@example.com", "en", "vi");
        UUID projectId = createProject(owner);
        summarizedChange(owner, projectId, 1, "Tables export as CSV.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0", "First summary.");
        approveEverything(owner, projectId, releaseId);
        drain();
        int requests = DEEPL.requests().size();

        edit(owner, projectId, releaseId, Map.of("version", "1.4.0", "summary", "Second summary."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes[?(@.language == 'vi')].translationStatus").value(everyItem(is("PENDING"))));
        assertThat(DEEPL.requests()).as("nothing is translated inside the edit").hasSize(requests);
        drain();
        assertThat(DEEPL.requests()).hasSize(requests + 1);
        assertThat(DEEPL.requests().getLast().texts()).containsExactly("Second summary.");
        assertThat(DEEPL.requests().getLast().targetLanguage()).isEqualTo("VI");

        // The same summary, or the link alone, sends nothing.
        edit(owner, projectId, releaseId, Map.of("version", "1.4.0", "summary", "Second summary.")).andExpect(status().isOk());
        edit(owner, projectId, releaseId, Map.of("version", "1.4.0", "summary", "Second summary.",
                "upgradeGuideUrl", "https://docs.example.com/upgrade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upgradeGuideChange.name").value("Mai Tran"));
        drain();
        assertThat(DEEPL.requests()).hasSize(requests + 1);
    }

    @Test
    void holdsAnEditThatWouldTranslateTheReleaseAgainUntilItsCountIsEchoed() throws Exception {
        Owner owner = registerWithLanguages("owner@example.com", "en", "vi");
        UUID projectId = createProject(owner);
        summarizedChange(owner, projectId, 1, "Tables export as CSV.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0", "First summary.");
        approveEverything(owner, projectId, releaseId);
        drain();
        projectContext(owner, projectId, "The console is the web UI.");
        int requests = DEEPL.requests().size();

        String held = edit(owner, projectId, releaseId, Map.of("version", "1.4.0", "summary", "Second summary."))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_edit_retranslates"))
                .andExpect(jsonPath("$.languages[0]").value("vi"))
                .andReturn().getResponse().getContentAsString();
        int characters = JsonPath.read(held, "$.characters");
        assertThat(characters).isGreaterThan("Second summary.".length());
        assertThat(JsonPath.<String>read(releaseJson(owner, projectId, releaseId), "$.summary")).isEqualTo("First summary.");

        // A count other than the cost now is refused with the current one: a wrong number, or
        // the count shown for another edit.
        edit(owner, projectId, releaseId, Map.of("version", "1.4.0", "summary", "Second summary.",
                "retranslationCharacters", characters - 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.characters").value(characters));
        int recounted = JsonPath.read(edit(owner, projectId, releaseId, Map.of("version", "1.4.0",
                        "summary", "Second summary, longer.", "retranslationCharacters", characters))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString(), "$.characters");
        assertThat(recounted).isEqualTo(characters + ", longer".length());
        assertThat(JsonPath.<String>read(releaseJson(owner, projectId, releaseId), "$.summary")).isEqualTo("First summary.");

        edit(owner, projectId, releaseId, Map.of("version", "1.4.0", "summary", "Second summary, longer.",
                "retranslationCharacters", recounted))
                .andExpect(status().isOk());
        drain();
        int sent = DEEPL.requests().subList(requests, DEEPL.requests().size()).stream()
                .flatMap(request -> request.texts().stream())
                .mapToInt(text -> text.codePointCount(0, text.length()))
                .sum();
        assertThat(sent).as("the count shown is what DeepL was sent").isEqualTo(recounted);
    }

    @ParameterizedTest(name = "{0} changes sharing a text")
    @ValueSource(ints = {1, 2})
    void editAndCorrectionPreviewCountExactlyWhatDeepLReceives(int changes) throws Exception {
        Owner owner = registerWithLanguages("owner@example.com", "en", "vi");
        UUID projectId = createProject(owner);
        String shared = "Export finished 🚀.";
        for (int n = 1; n <= changes; n++) {
            UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, n,
                    "feat: add export " + n, "FEATURE", false, false, null);
            // In one case two fields repeat the text. In the other two changes share it.
            String narratives = changes == 1 ? JSON.writeValueAsString(Map.of("operator", shared)) : "{}";
            TestChanges.summarize(jdbcTemplate, change, shared, narratives);
        }
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0", "Original summary.");
        approveEverything(owner, projectId, releaseId);
        drain();
        DEEPL.reset();
        projectContext(owner, projectId, "A new context for this approval.");
        int echo = JsonPath.read(edit(owner, projectId, releaseId, Map.of("version", "1.4.0", "summary", shared))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_edit_retranslates"))
                .andReturn().getResponse().getContentAsString(), "$.characters");
        edit(owner, projectId, releaseId, Map.of("version", "1.4.0", "summary", shared, "retranslationCharacters", echo))
                .andExpect(status().isOk());
        assertThat(DEEPL.translatedTexts()).isEmpty();
        drain();
        assertThat(DEEPL.translatedTexts()).containsExactlyInAnyOrder(shared, "Users asked.", "Streams rows.");
        int sent = DEEPL.translatedTexts().stream().mapToInt(text -> text.codePointCount(0, text.length())).sum();
        assertThat(echo).isEqualTo(sent);
        action(owner, projectId, releaseId, "publish").andExpect(status().isOk());

        // Reproduce missing published answers for the same inputs. The preview still
        // marks every affected field, including the release summary, and calls no provider.
        jdbcTemplate.update("DELETE FROM translation_cache WHERE organization_id = ?", owner.organizationId());
        DEEPL.reset();
        mockMvc.perform(get(releaseApi(projectId, releaseId) + "/notes/{id}/correction-preview",
                        noteId(releaseId, "operator", "vi")).param("origin", "RENDERED").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missingTexts").value(changes == 1 ? 5 : 7))
                .andExpect(jsonPath("$.missingCharacters").value(sent));
        assertThat(DEEPL.requests()).isEmpty();
    }

    @Test
    void movesANotesUpdateTimeOnlyWhenItsTextChanges() throws Exception {
        Owner owner = registerWithLanguages("owner@example.com", "en", "vi");
        UUID projectId = createProject(owner);
        UUID change = summarizedChange(owner, projectId, 1, "Tables export as CSV.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0", "First summary.");
        approveEverything(owner, projectId, releaseId);
        drain();
        Map<String, Snapshot> before = snapshots(releaseId);

        // Finding 33's case: one audience's narrative changes, and every note of the language
        // goes through PENDING and READY.
        mockMvc.perform(put(releaseApi(projectId, releaseId) + "/changes/{changeId}/summary", change)
                        .session(owner.session()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"whatChanged":"Tables export as CSV.","whyChanged":"Users asked.",
                                 "technicalDetail":"Streams rows.","migrationStep":"",
                                 "narratives":{"operator":"Watch the export queue.","contributor":"Use ExportJob.",
                                               "end_user":"Download any table, quicker."}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes[?(@.language == 'vi')].translationStatus").value(everyItem(is("PENDING"))));
        drain();
        Map<String, Snapshot> after = snapshots(releaseId);

        assertThat(after.values()).allSatisfy(note -> assertThat(note.status()).isEqualTo("READY"));
        before.forEach((note, was) -> {
            Snapshot now = after.get(note);
            if (now.content().equals(was.content())) {
                assertThat(now.updatedAt()).as(note + " did not change").isEqualTo(was.updatedAt());
            } else {
                assertThat(now.updatedAt()).as(note + " changed").isAfter(was.updatedAt());
            }
        });
        assertThat(after.get("end_user.en").content()).isNotEqualTo(before.get("end_user.en").content());
        assertThat(after.get("operator.vi").updatedAt()).isEqualTo(before.get("operator.vi").updatedAt());
    }

    @Test
    void anEditAfterAPublicationIsRefusedByTheServiceAndAPublicationAfterAnEditWaitsForItsNotes() throws Exception {
        Owner owner = registerWithLanguages("owner@example.com", "en", "vi");
        UUID projectId = createProject(owner);
        summarizedChange(owner, projectId, 1, "Tables export as CSV.");
        UUID first = draftWithAllChanges(owner, projectId, "1.4.0", "First summary.");
        approveEverything(owner, projectId, first);
        drain();
        ReleaseFlowPrincipal principal = principal(owner.session());

        // The publication holds the lock; the edit waits, then finds the release published.
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch go = new CountDownLatch(1);
        CompletableFuture<Void> publication = holding(locked, go,
                () -> releaseService.publish(principal, projectId, first));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<MvcResult> edit = CompletableFuture.supplyAsync(() -> call(() -> edit(owner, projectId, first,
                Map.of("version", "1.4.0", "summary", "Too late.")).andReturn()));
        Thread.sleep(500);
        assertThat(edit).isNotDone();
        go.countDown();
        publication.get(10, TimeUnit.SECONDS);
        MvcResult refused = edit.get(10, TimeUnit.SECONDS);
        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(JsonPath.<String>read(refused.getResponse().getContentAsString(), "$.code")).isEqualTo("release_published");

        // The edit holds the lock; the publication waits, then finds the notes translating.
        summarizedChange(owner, projectId, 2, "Charts export as PNG.");
        UUID second = draftWithAllChanges(owner, projectId, "1.5.0", "First summary.");
        approveEverything(owner, projectId, second);
        drain();
        ReleaseRequest request = new ReleaseRequest();
        request.setVersion("1.5.0");
        request.setSummary("Second summary.");
        CountDownLatch editing = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CompletableFuture<Void> held = holding(editing, commit,
                () -> releaseService.edit(principal, projectId, second, request));
        assertThat(editing.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<MvcResult> publish = CompletableFuture.supplyAsync(() -> call(() ->
                action(owner, projectId, second, "publish").andReturn()));
        Thread.sleep(500);
        assertThat(publish).isNotDone();
        commit.countDown();
        held.get(10, TimeUnit.SECONDS);
        MvcResult waiting = publish.get(10, TimeUnit.SECONDS);
        assertThat(waiting.getResponse().getStatus()).isEqualTo(409);
        assertThat(JsonPath.<String>read(waiting.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("translations_not_ready");
    }

    @Test
    void thePageEditsAnApprovedReleaseAndShowsWhatATranslationWouldCostBeforeSaving() throws Exception {
        Owner owner = registerWithLanguages("owner@example.com", "en", "vi");
        UUID projectId = createProject(owner);
        summarizedChange(owner, projectId, 1, "Tables export as CSV.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0", "First summary.");
        approveEverything(owner, projectId, releaseId);
        drain();

        mockMvc.perform(get(releasePage(projectId, releaseId)).session(owner.session()))
                .andExpect(content().string(containsString("id=\"release-text\"")))
                .andExpect(content().string(containsString("The approval stays, and the change is recorded beside it.")));
        projectContext(owner, projectId, "The console is the web UI.");
        String held = mockMvc.perform(post(releasePage(projectId, releaseId)).session(owner.session()).with(csrf())
                        .param("version", "1.4.0").param("summary", "Second summary.").param("upgradeGuideUrl", ""))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("saving translates the release again")))
                .andExpect(content().string(containsString("Save and translate again")))
                .andReturn().getResponse().getContentAsString();
        String characters = held.replaceAll("(?s).*name=\"retranslationCharacters\" value=\"(\\d+)\".*", "$1");
        mockMvc.perform(post(releasePage(projectId, releaseId)).session(owner.session()).with(csrf())
                        .param("version", "1.4.0").param("summary", "Second summary.").param("upgradeGuideUrl", "")
                        .param("retranslationCharacters", characters))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get(releasePage(projectId, releaseId)).session(owner.session()).header("Accept-Language", "vi"))
                .andExpect(content().string(containsString("Tóm tắt được Mai Tran đổi sau khi duyệt")));
    }

    private interface Call<T> {
        T run() throws Exception;
    }

    private static <T> T call(Call<T> call) {
        try {
            return call.run();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    // Runs the work in a transaction that holds whatever it locked until told to commit.
    private CompletableFuture<Void> holding(CountDownLatch locked, CountDownLatch commit, Runnable work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return CompletableFuture.runAsync(() -> transaction.executeWithoutResult(status -> {
            work.run();
            locked.countDown();
            try {
                assertThat(commit.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }));
    }

    private record Snapshot(String content, String status, Timestamp updatedAt) {
    }

    private Map<String, Snapshot> snapshots(UUID releaseId) {
        Map<String, Snapshot> snapshots = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT audience_code, language, content, translation_status, updated_at "
                        + "FROM release_audience_notes WHERE release_id = ?",
                row -> {
                    snapshots.put(row.getString(1) + "." + row.getString(2),
                            new Snapshot(row.getString(3), row.getString(4), row.getTimestamp(5)));
                }, releaseId);
        return snapshots;
    }

    private void drain() {
        TranslationQueue.drain(context, jdbcTemplate);
    }

    private void projectContext(Owner owner, UUID projectId, String text) throws Exception {
        mockMvc.perform(put("/api/projects/{projectId}/context", projectId).session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("context", text))))
                .andExpect(status().isOk());
    }

    private ResultActions edit(Owner owner, UUID projectId, UUID releaseId, Map<String, ?> body) throws Exception {
        return mockMvc.perform(put(releaseApi(projectId, releaseId)).session(owner.session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body)));
    }

    private String releaseJson(Owner owner, UUID projectId, UUID releaseId) throws Exception {
        return mockMvc.perform(get(releaseApi(projectId, releaseId)).session(owner.session()))
                .andReturn().getResponse().getContentAsString();
    }

    private String notes(Owner owner, UUID projectId, UUID releaseId) throws Exception {
        return mockMvc.perform(get(releaseApi(projectId, releaseId) + "/notes").session(owner.session()))
                .andReturn().getResponse().getContentAsString();
    }

    private String noteContent(Owner owner, UUID projectId, UUID releaseId, UUID noteId) throws Exception {
        List<String> found = JsonPath.read(notes(owner, projectId, releaseId), "$[?(@.id == '" + noteId + "')].content");
        return found.getFirst();
    }

    private UUID noteId(UUID releaseId, String audienceCode, String language) {
        return jdbcTemplate.queryForObject("SELECT id FROM release_audience_notes WHERE release_id = ? "
                + "AND audience_code = ? AND language = ?", UUID.class, releaseId, audienceCode, language);
    }

    private void editNote(Owner owner, UUID projectId, UUID releaseId, UUID noteId, String content) throws Exception {
        mockMvc.perform(put(releaseApi(projectId, releaseId) + "/notes/{noteId}", noteId).session(owner.session())
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("content", content))))
                .andExpect(status().isOk());
    }

    private ReleaseFlowPrincipal principal(MockHttpSession session) {
        return (ReleaseFlowPrincipal) ((SecurityContext) session
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .getAuthentication().getPrincipal();
    }

    private MockHttpSession member(UUID organizationId, String email, String name) throws Exception {
        jdbcTemplate.update("""
                INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                VALUES (?, ?, ?, (SELECT password_hash FROM app_users WHERE email = 'owner@example.com'), ?, 'MEMBER', now())
                """, UUID.randomUUID(), organizationId, email, name);
        MvcResult login = mockMvc.perform(post("/login").with(csrf()).param("email", email)
                        .param("password", "owner-password"))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
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

    // Asks for review, approves every change as shown, then approves the release.
    private void approveEverything(Owner owner, UUID projectId, UUID releaseId) throws Exception {
        String state = releaseJson(owner, projectId, releaseId);
        if ("DRAFT".equals(JsonPath.read(state, "$.status"))) {
            action(owner, projectId, releaseId, "request-review").andExpect(status().isOk());
        }
        List<Map<String, Object>> changes = JsonPath.read(releaseJson(owner, projectId, releaseId), "$.changes");
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
                        .content(JSON.writeValueAsString(Map.of("version", version, "summary", summary))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID releaseId = UUID.fromString(JsonPath.read(body, "$.id"));
        mockMvc.perform(post(releaseApi(projectId, releaseId) + "/changes")
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allAvailable\":true}"))
                .andExpect(status().isOk());
        return releaseId;
    }

    private UUID createProject(Owner owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ReleaseFlow\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private static String releaseApi(UUID projectId, UUID releaseId) {
        return "/api/projects/" + projectId + "/releases/" + releaseId;
    }

    private static String releasePage(UUID projectId, UUID releaseId) {
        return "/projects/" + projectId + "/releases/" + releaseId;
    }

    private Owner registerWithLanguages(String email, String... languages) throws Exception {
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
        Owner owner = new Owner(
                registration.organizationId(),
                registration.userId(),
                (MockHttpSession) login.getRequest().getSession(false)
        );
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
