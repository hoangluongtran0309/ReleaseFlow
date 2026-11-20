package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.support.TestChanges;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An action whose note has nothing for its audience is skipped, decided from the release's
 * changes when the action runs, unless a person wrote the note (ADR-0035 and ADR-0044,
 * amended 2026-11-19).
 */
class NothingForAudienceIntegrationTest extends AutomationIntegrationTestBase {

    /** Nothing concerns end users or support; operators and contributors read it in full. */
    private static final String NOTHING_FOR_END_USERS = """
            {"end_user":{"relevance":"NONE","headline":"","body":""},
             "support":{"relevance":"NONE","headline":"","body":""},
             "operator":{"relevance":"FULL","headline":"","body":"Exports queue in the background."},
             "contributor":{"relevance":"FULL","headline":"","body":"Call ExportJob instead."}}""";

    /** Nothing concerns anybody. */
    private static final String NOTHING_FOR_ANYBODY = """
            {"end_user":{"relevance":"NONE","headline":"","body":""},
             "support":{"relevance":"NONE","headline":"","body":""},
             "operator":{"relevance":"NONE","headline":"","body":""},
             "contributor":{"relevance":"NONE","headline":"","body":""}}""";

    @Autowired
    private AutomationTriggerWorker triggerWorker;

    @Autowired
    private MeterRegistry registry;

    private int changeNumber = 100;

    @Test
    void skipsAnActionWhoseNoteHasNothingForItsAudienceAndDeliversTheOneBehindIt() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID operator = audienceId(owner, "operator");
        UUID ruleId = createdRuleId(createRule(owner, """
                {"name":"Announce","triggerType":"RELEASE_PUBLISHED","projectId":"%s","actions":[
                  {"actionType":"PUBLIC_CHANGELOG","audienceId":"%s","language":"en"},
                  {"actionType":"SLACK","audienceId":"%s","language":"en","secret":"%s"}]}
                """.formatted(projectId, endUser, operator, slackWebhook())).andExpect(status().isCreated()));
        // Nothing about a rule says whether a release will have something for its audiences.
        enable(owner, ruleId).andExpect(status().isOk());
        double deliveriesBefore = deliveries();

        UUID releaseId = publish(owner, projectId, approvedReleaseWith(owner, projectId, "1.4.1", false, NOTHING_FOR_END_USERS));
        deliverEverything();

        mockMvc.perform(get("/api/automation/runs").session(owner.session()))
                .andExpect(jsonPath("$.items[0].releaseId").value(releaseId.toString()))
                .andExpect(jsonPath("$.items[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items[0].actions[0].status").value("SKIPPED"))
                .andExpect(jsonPath("$.items[0].actions[0].errorCode").value("nothing_for_audience"))
                .andExpect(jsonPath("$.items[0].actions[0].externalReference").doesNotExist())
                .andExpect(jsonPath("$.items[0].actions[1].status").value("SUCCEEDED"));
        // No public entry that only says nothing concerns its reader, and only the one
        // delivery that was made is counted.
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public_changelog_entries", Integer.class))
                .isZero();
        assertThat(SLACK.messages()).hasSize(1);
        assertThat(SLACK.messages().getFirst()).contains("Exports queue in the background.");
        assertThat(deliveries()).isEqualTo(deliveriesBefore + 1);

        // The run page says why, in both interface languages.
        mockMvc.perform(get("/automation").session(owner.session()))
                .andExpect(content().string(containsString("nothing_for_audience")))
                .andExpect(content().string(containsString(
                        "Nothing in this release concerns its audience, so nothing was sent; the actions after it still ran.")));
        mockMvc.perform(get("/automation").param("lang", "vi").session(owner.session()))
                .andExpect(content().string(containsString(
                        "Release này không có thay đổi nào liên quan tới audience của nó, nên không gửi gì;"
                                + " các Action sau nó vẫn chạy.")));
    }

    @Test
    void skipsTheSameWayWhenAPersonRunsTheRuleAndWhenItsScheduleFires() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID releaseId = publish(owner, projectId, approvedReleaseWith(owner, projectId, "1.4.1", false, NOTHING_FOR_END_USERS));
        UUID byHand = createdRuleId(createRule(owner, slackRule("Tell users", "MANUAL", projectId, endUser, "en"))
                .andExpect(status().isCreated()));
        enable(owner, byHand).andExpect(status().isOk());
        UUID scheduled = createdRuleId(createRule(owner,
                cronRule("Weekly digest", projectId, endUser, releaseId, "0 0 9 * * *", "Europe/Berlin"))
                .andExpect(status().isCreated()));
        enable(owner, scheduled).andExpect(status().isOk());
        double deliveriesBefore = deliveries();

        UUID manualRun = run(owner, byHand, releaseId);
        deliverEverything();
        setNextFireAt(scheduled, Instant.now().minusSeconds(30).truncatedTo(ChronoUnit.MICROS));
        assertThat(triggerWorker.processOne()).isTrue();
        deliverEverything();

        // Every action of each run was skipped, and each run succeeded with nothing delivered.
        mockMvc.perform(get("/api/automation/runs/{runId}", manualRun).session(owner.session()))
                .andExpect(jsonPath("$.triggerType").value("MANUAL"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.actions[0].status").value("SKIPPED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("nothing_for_audience"));
        mockMvc.perform(get("/api/automation/runs").session(owner.session()))
                .andExpect(jsonPath("$.items[0].triggerType").value("SCHEDULED_CRON"))
                .andExpect(jsonPath("$.items[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items[0].actions[0].status").value("SKIPPED"))
                .andExpect(jsonPath("$.items[0].actions[0].errorCode").value("nothing_for_audience"));
        assertThat(SLACK.messages()).isEmpty();
        assertThat(deliveries()).isEqualTo(deliveriesBefore);
        // A skipped action attempted nothing, so there is nothing to retry.
        mockMvc.perform(post("/api/automation/runs/{runId}/retry", manualRun).session(owner.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("automation_run_not_retryable"));
    }

    @Test
    void deliversANoteAPersonWroteAsWrittenThoughNothingConcernsItsAudience() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, slackRule("Tell users", "MANUAL", projectId, endUser, "en"))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        UUID releaseId = approvedReleaseWith(owner, projectId, "1.4.1", false, NOTHING_FOR_END_USERS);

        String notes = mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/notes", projectId, releaseId)
                        .session(owner.session()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> endUserNote = JsonPath.read(notes, "$[?(@.audienceCode == 'end_user')].id");
        mockMvc.perform(put("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}",
                                projectId, releaseId, endUserNote.getFirst())
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"# Release 1.4.1\\n\\nExports are quicker; nothing changes for you.\\n\"}"))
                .andExpect(status().isOk());
        publish(owner, projectId, releaseId);

        UUID runId = run(owner, ruleId, releaseId);
        deliverEverything();

        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.actions[0].status").value("SUCCEEDED"));
        assertThat(SLACK.messages()).hasSize(1);
        assertThat(SLACK.messages().getFirst()).contains("Exports are quicker; nothing changes for you.");
    }

    @Test
    void deliversABreakingChangeASecurityChangeForThoseItAlwaysConcernsAndAChangeWithoutANarrative() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID operator = audienceId(owner, "operator");
        UUID ruleId = createdRuleId(createRule(owner, """
                {"name":"Announce","triggerType":"MANUAL","projectId":"%s","actions":[
                  {"actionType":"SLACK","audienceId":"%s","language":"en","secret":"%s"},
                  {"actionType":"SLACK","audienceId":"%s","language":"en","secret":"%s"}]}
                """.formatted(projectId, endUser, slackWebhook(), operator, slackWebhook())).andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());

        // A breaking change is in every note, whatever its relevance.
        UUID breaking = publish(owner, projectId, approvedReleaseWith(owner, projectId, "2.0.0", true, NOTHING_FOR_ANYBODY));
        UUID breakingRun = run(owner, ruleId, breaking);
        // A security change concerns operators even when it is none for them, and not end users.
        UUID security = publish(owner, projectId, approvedSecurityReleaseWith(owner, projectId, "2.0.1", NOTHING_FOR_ANYBODY));
        UUID securityRun = run(owner, ruleId, security);
        // A change with no narrative for the audience concerns it.
        UUID unnarrated = publish(owner, projectId, approvedReleaseWith(owner, projectId, "2.0.2", false, null));
        UUID unnarratedRun = run(owner, ruleId, unnarrated);
        deliverEverything();

        mockMvc.perform(get("/api/automation/runs/{runId}", breakingRun).session(owner.session()))
                .andExpect(jsonPath("$.actions[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.actions[1].status").value("SUCCEEDED"));
        mockMvc.perform(get("/api/automation/runs/{runId}", securityRun).session(owner.session()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.actions[0].status").value("SKIPPED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("nothing_for_audience"))
                .andExpect(jsonPath("$.actions[1].status").value("SUCCEEDED"));
        mockMvc.perform(get("/api/automation/runs/{runId}", unnarratedRun).session(owner.session()))
                .andExpect(jsonPath("$.actions[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.actions[1].status").value("SUCCEEDED"));
        assertThat(SLACK.messages()).hasSize(5);
    }

    @Test
    void readsTheChangesAsTheyAreWhenTheActionRunsSoAReviewAfterPublicationCanSkipANoteThatListsAChange()
            throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, slackRule("Tell users", "MANUAL", projectId, endUser, "en"))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        UUID releaseId = publish(owner, projectId, approvedReleaseWith(owner, projectId, "2.0.0", true, NOTHING_FOR_ANYBODY));
        UUID changeId = jdbcTemplate.queryForObject(
                "SELECT change_id FROM release_changes WHERE release_id = ?", UUID.class, releaseId);

        // The known limit of ADR-0035's amendment: the published note keeps the breaking
        // change it was written with, but a review clears the flag afterwards, so nothing
        // concerns end users by the time the action runs.
        mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/review", projectId, changeId)
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"feature\",\"breaking\":false}"))
                .andExpect(status().isOk());
        UUID runId = run(owner, ruleId, releaseId);
        deliverEverything();

        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/notes", projectId, releaseId)
                        .session(owner.session()))
                .andExpect(jsonPath("$[?(@.audienceCode == 'end_user')].content",
                        hasItem(containsString("The inbox lists new changes."))));
        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.actions[0].status").value("SKIPPED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("nothing_for_audience"));
        assertThat(SLACK.messages()).isEmpty();
    }

    @Test
    void aRetiredAudienceKeepsItsOwnReasonAndAMissingNoteStillFails() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID releaseId = approvedReleaseWith(owner, projectId, "1.4.1", false, NOTHING_FOR_END_USERS);
        // Added after approval, so the release has no note for it, though the change says
        // it concerns nobody there either.
        UUID partners = createAudience(owner, "partners", "Partners");
        UUID changeId = jdbcTemplate.queryForObject(
                "SELECT change_id FROM release_changes WHERE release_id = ?", UUID.class, releaseId);
        TestChanges.summarize(jdbcTemplate, changeId, "Tables export as CSV.", NOTHING_FOR_END_USERS.replace(
                "{\"end_user\"", "{\"partners\":{\"relevance\":\"NONE\",\"headline\":\"\",\"body\":\"\"},\"end_user\""));
        publish(owner, projectId, releaseId);
        UUID ruleId = createdRuleId(createRule(owner, """
                {"name":"Announce","triggerType":"MANUAL","projectId":"%s","actions":[
                  {"actionType":"SLACK","audienceId":"%s","language":"en","secret":"%s"},
                  {"actionType":"SLACK","audienceId":"%s","language":"en","secret":"%s"}]}
                """.formatted(projectId, endUser, slackWebhook(), partners, slackWebhook())).andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        mockMvc.perform(post("/api/audiences/{id}/retire", endUser).session(owner.session()).with(csrf()))
                .andExpect(status().isOk());

        UUID runId = run(owner, ruleId, releaseId);
        deliverEverything();

        mockMvc.perform(get("/api/automation/runs/{runId}", runId).session(owner.session()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.actions[0].status").value("SKIPPED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("audience_retired"))
                .andExpect(jsonPath("$.actions[1].status").value("FAILED"))
                .andExpect(jsonPath("$.actions[1].errorCode").value("release_note_missing"));
        assertThat(SLACK.messages()).isEmpty();
    }

    /**
     * An approved release of one change, with the narratives given, or none when null. A
     * breaking change needs review, which approving the release records.
     */
    @Test
    void deliversARevisionAPersonWroteAndSkipsOneRenderedFromChangesThatConcernNobody() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, slackRule("Tell users", "MANUAL", projectId, endUser, "en"))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());

        // ADR-0046, amended 2026-11-20: a revision written by hand is a note a person wrote.
        UUID handReleaseId = publish(owner, projectId,
                approvedReleaseWith(owner, projectId, "1.4.1", false, NOTHING_FOR_END_USERS));
        UUID handNote = endUserNote(owner, projectId, handReleaseId);
        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/revisions",
                        projectId, handReleaseId, handNote)
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"corrects\":1,\"content\":\"# Release 1.4.1\\n\\nExports are quicker for you.\\n\","
                                + "\"reason\":\"Say what users notice.\"}"))
                .andExpect(status().isCreated());
        UUID handRun = run(owner, ruleId, handReleaseId);

        // A revision rendered again from changes that now concern nobody says so, and is skipped.
        UUID renderedReleaseId = publish(owner, projectId,
                approvedReleaseWith(owner, projectId, "2.0.0", true, NOTHING_FOR_ANYBODY));
        UUID changeId = jdbcTemplate.queryForObject(
                "SELECT change_id FROM release_changes WHERE release_id = ?", UUID.class, renderedReleaseId);
        mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/review", projectId, changeId)
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"feature\",\"breaking\":false}"))
                .andExpect(status().isOk());
        UUID renderedNote = endUserNote(owner, projectId, renderedReleaseId);
        String preview = mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/correction-preview",
                        projectId, renderedReleaseId, renderedNote)
                        .session(owner.session()).param("origin", "RENDERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/revisions",
                        projectId, renderedReleaseId, renderedNote)
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"RENDERED\",\"corrects\":1,\"previewHash\":\"%s\",\"reason\":\"Not breaking.\"}"
                                .formatted(JsonPath.<String>read(preview, "$.previewHash"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("RENDERED"));
        UUID renderedRun = run(owner, ruleId, renderedReleaseId);
        deliverEverything();

        mockMvc.perform(get("/api/automation/runs/{runId}", handRun).session(owner.session()))
                .andExpect(jsonPath("$.actions[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.actions[0].noteRevision").value(2));
        mockMvc.perform(get("/api/automation/runs/{runId}", renderedRun).session(owner.session()))
                .andExpect(jsonPath("$.actions[0].status").value("SKIPPED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("nothing_for_audience"));
        assertThat(SLACK.messages()).hasSize(1);
        assertThat(SLACK.messages().getFirst()).contains("Exports are quicker for you.");
    }

    private UUID endUserNote(Owner owner, UUID projectId, UUID releaseId) throws Exception {
        String notes = mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/notes", projectId, releaseId)
                        .session(owner.session()))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(notes, "$[?(@.audienceCode == 'end_user')].id");
        return UUID.fromString(ids.getFirst());
    }

    private UUID approvedReleaseWith(Owner owner, UUID projectId, String version, boolean breaking, String narratives)
            throws Exception {
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, changeNumber++,
                "feat(ui): add the inbox", "FEATURE", breaking, breaking, null);
        if (narratives != null) {
            TestChanges.summarize(jdbcTemplate, change, "The inbox lists new changes.", narratives);
        }
        return approvedRelease(owner, projectId, version);
    }

    private UUID approvedSecurityReleaseWith(Owner owner, UUID projectId, String version, String narratives)
            throws Exception {
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, changeNumber++,
                "fix(auth): expire stale sessions", "FIX", false, true, null);
        TestChanges.markSecurity(jdbcTemplate, change);
        TestChanges.summarize(jdbcTemplate, change, "Stale sessions expire.", narratives);
        return approvedRelease(owner, projectId, version);
    }

    private UUID publish(Owner owner, UUID projectId, UUID releaseId) throws Exception {
        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/publish", projectId, releaseId)
                        .session(owner.session())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        return releaseId;
    }

    private UUID run(Owner owner, UUID ruleId, UUID releaseId) throws Exception {
        String body = mockMvc.perform(post("/api/automation/rules/{ruleId}/execute", ruleId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseId\":\"%s\",\"requestId\":\"%s\"}".formatted(releaseId, UUID.randomUUID())))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    // Every delivery counted, whatever set it off and however it ended. Counters live as
    // long as the process, so a test compares against what it read first.
    private double deliveries() {
        return registry.find(AutomationMetrics.EXECUTIONS).counters().stream().mapToDouble(Counter::count).sum();
    }
}
