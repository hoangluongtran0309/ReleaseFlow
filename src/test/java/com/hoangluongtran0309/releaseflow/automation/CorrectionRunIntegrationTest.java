package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.account.AppUserRole;
import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.support.TestChanges;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A correction run (ADR-0046, part 5, as amended on 2026-11-21): an administrator delivers a
 * corrected note to the public changelog entries that hold an earlier revision of it, with a
 * reason those entries show.
 */
class CorrectionRunIntegrationTest extends AutomationIntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String REASON = "Placeholders keep their case.";
    private static final String NOTHING_FOR_ANYBODY = """
            {"end_user":{"relevance":"NONE","headline":"","body":""},
             "support":{"relevance":"NONE","headline":"","body":""},
             "operator":{"relevance":"NONE","headline":"","body":""},
             "contributor":{"relevance":"NONE","headline":"","body":""}}""";

    @Autowired
    private AutomationCorrectionService correctionService;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private int changeNumber = 200;

    @Test
    void offersAnActionWhoseNoteHasALaterRevisionAndNamesEveryOtherWithItsReason() throws Exception {
        Published published = published();
        UUID zendeskRule = createdRuleId(createRule(published.owner(),
                zendeskRule("Help centre", "MANUAL", published.projectId(), published.endUser()))
                .andExpect(status().isCreated()));
        enable(published.owner(), zendeskRule).andExpect(status().isOk());

        // Delivered, and the note has not changed since: there is nothing to correct yet.
        offer(published, published.ruleId())
                .andExpect(jsonPath("$.releaseVersion").value("1.4.0"))
                .andExpect(jsonPath("$.items[0].actionType").value("PUBLIC_CHANGELOG"))
                .andExpect(jsonPath("$.items[0].unavailable").value("NOTHING_NEWER"))
                .andExpect(jsonPath("$.items[0].carriedRevision").value(1))
                .andExpect(jsonPath("$.items[0].latestRevision").value(1))
                .andExpect(jsonPath("$.items[0].picked").value(false))
                // A destination whose correction comes later is named whatever it holds.
                .andExpect(jsonPath("$.items[1].actionType").value("SLACK"))
                .andExpect(jsonPath("$.items[1].unavailable").value("LATER"));

        correctByHand(published, "# Release 1.4.0\n\nThe inbox lists every new change.\n", REASON);

        offer(published, published.ruleId())
                .andExpect(jsonPath("$.items[0].unavailable").doesNotExist())
                .andExpect(jsonPath("$.items[0].picked").value(true))
                .andExpect(jsonPath("$.items[0].carriedRevision").value(1))
                .andExpect(jsonPath("$.items[0].latestRevision").value(2))
                .andExpect(jsonPath("$.items[1].unavailable").value("LATER"))
                // The reason starts as the reasons of the revisions the picked destinations lack.
                .andExpect(jsonPath("$.suggestedReason").value(REASON));

        // Zendesk keeps what it published, so its state does not matter.
        offer(published, zendeskRule)
                .andExpect(jsonPath("$.items[0].unavailable").value("NOT_SUPPORTED"));

        // A retired audience comes first of all.
        mockMvc.perform(post("/api/audiences/{id}/retire", published.endUser()).session(published.owner().session())
                        .with(csrf()))
                .andExpect(status().isOk());
        offer(published, published.ruleId())
                .andExpect(jsonPath("$.items[0].unavailable").value("AUDIENCE_RETIRED"));
        offer(published, zendeskRule)
                .andExpect(jsonPath("$.items[0].unavailable").value("AUDIENCE_RETIRED"));
    }

    @Test
    void namesAnActionThatNeverDeliveredWasOnlySkippedIsUnsettledOrIsStillRunning() throws Exception {
        Published published = published();
        correctByHand(published, "# Release 1.4.0\n\nThe inbox lists every new change.\n", REASON);
        UUID byHand = createdRuleId(createRule(published.owner(), changelogRule("By hand", "MANUAL",
                published.projectId(), published.endUser())).andExpect(status().isCreated()));
        enable(published.owner(), byHand).andExpect(status().isOk());

        // Never run for this release.
        offer(published, byHand).andExpect(jsonPath("$.items[0].unavailable").value("NEVER_DELIVERED"));

        // Run, but not finished.
        UUID running = execute(published, byHand);
        offer(published, byHand).andExpect(jsonPath("$.items[0].unavailable").value("IN_PROGRESS"));

        // Failed only: never delivered, and a retry is what delivers it.
        jdbcTemplate.update("UPDATE automation_action_runs SET status = 'FAILED', completed_at = now(), "
                + "error_code = 'public_changelog_conflict' WHERE run_id = ?", running);
        jdbcTemplate.update("UPDATE automation_runs SET status = 'FAILED', completed_at = now() WHERE id = ?", running);
        offer(published, byHand).andExpect(jsonPath("$.items[0].unavailable").value("NEVER_DELIVERED"));

        // Its outcome unknown: a person settles that first.
        jdbcTemplate.update("UPDATE automation_action_runs SET status = 'UNKNOWN' WHERE run_id = ?", running);
        jdbcTemplate.update("UPDATE automation_runs SET status = 'UNKNOWN' WHERE id = ?", running);
        offer(published, byHand).andExpect(jsonPath("$.items[0].unavailable").value("UNKNOWN"));

        // Only ever skipped: there is nothing there to correct.
        jdbcTemplate.update("UPDATE automation_action_runs SET status = 'SKIPPED', error_code = 'audience_retired' "
                + "WHERE run_id = ?", running);
        jdbcTemplate.update("UPDATE automation_runs SET status = 'SUCCEEDED' WHERE id = ?", running);
        offer(published, byHand).andExpect(jsonPath("$.items[0].unavailable").value("ONLY_SKIPPED"));
    }

    @Test
    void deliversTheLatestRevisionWithTheRunsReasonAndOffersNothingAfterIt() throws Exception {
        Published published = published();
        // Registered when the application starts, as every trigger's series is.
        double before = counter("correction", "succeeded");
        assertThat(registry.find(AutomationMetrics.EXECUTIONS).tag("trigger", "correction").counters()).hasSize(3);
        UUID publicationDelivery = jdbcTemplate.queryForObject(
                "SELECT action_run_id FROM public_changelog_entries", UUID.class);
        correctByHand(published, "# Release 1.4.0\n\nThe inbox lists every new change.\n", REASON);

        UUID runId = start(published, published.ruleId(), "Say what the inbox lists.", UUID.randomUUID(),
                published.changelogAction())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.triggerType").value("CORRECTION"))
                .andExpect(jsonPath("$.reason").value("Say what the inbox lists."))
                .andExpect(jsonPath("$.initiatorName").value("Mai Tran"))
                // Only the picked action is in the run.
                .andExpect(jsonPath("$.actions.length()").value(1))
                .andExpect(jsonPath("$.actions[0].noteRevision").value(2))
                .andExpect(jsonPath("$.actions[0].correctsRevision").value(1))
                .andReturn().getResponse().getContentAsString().transform(body -> UUID.fromString(JsonPath.read(body, "$.id")));
        deliverEverything();

        mockMvc.perform(get("/api/automation/runs/{id}", runId).session(published.owner().session()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.actions[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.actions[0].externalReference").value(containsString("/releases/")));
        Map<String, Object> revision = jdbcTemplate.queryForMap(
                "SELECT revision, note_revision, content_snapshot, reason FROM public_changelog_entry_revisions");
        assertThat(revision).containsEntry("revision", 2).containsEntry("note_revision", 2)
                .containsEntry("content_snapshot", "# Release 1.4.0\n\nThe inbox lists every new change.\n")
                .containsEntry("reason", "Say what the inbox lists.");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT corrects_action_run_id FROM automation_action_runs WHERE run_id = ?", UUID.class, runId))
                .isEqualTo(publicationDelivery);
        assertThat(counter("correction", "succeeded")).isEqualTo(before + 1);

        // The entry now holds the latest revision.
        offer(published, published.ruleId()).andExpect(jsonPath("$.items[0].unavailable").value("NOTHING_NEWER"));
        start(published, published.ruleId(), REASON, UUID.randomUUID(), published.changelogAction())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("automation_correction_not_offered"))
                .andExpect(jsonPath("$.detail").value("Action 1 is not offered: it already holds the note's latest revision."));
    }

    @Test
    void makesARepeatTheSameRunAndRefusesWhatCannotBeCorrected() throws Exception {
        Published published = published();
        correctByHand(published, "# Release 1.4.0\n\nThe inbox lists every new change.\n", REASON);
        UUID requestId = UUID.randomUUID();
        String first = start(published, published.ruleId(), REASON, requestId, published.changelogAction())
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        start(published, published.ruleId(), "Another reason", requestId, published.changelogAction())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(JsonPath.<String>read(first, "$.id")))
                .andExpect(jsonPath("$.reason").value(REASON));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM automation_runs WHERE trigger_type = 'CORRECTION'", Integer.class)).isEqualTo(1);

        // What the request must say.
        startWith(published, published.ruleId(), Map.of("releaseId", published.releaseId(), "reason", REASON,
                "ruleActionIds", List.of(published.changelogAction())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("automation_request_id_required"));
        start(published, published.ruleId(), " ", UUID.randomUUID(), published.changelogAction())
                .andExpect(status().isBadRequest());
        start(published, published.ruleId(), "x".repeat(501), UUID.randomUUID(), published.changelogAction())
                .andExpect(status().isBadRequest());
        startWith(published, published.ruleId(), Map.of("releaseId", published.releaseId(), "reason", REASON,
                "requestId", UUID.randomUUID(), "ruleActionIds", List.of()))
                .andExpect(status().isBadRequest());
        start(published, published.ruleId(), REASON, UUID.randomUUID(), UUID.randomUUID())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("automation_correction_action_unknown"));
        start(published, published.ruleId(), REASON, UUID.randomUUID(), published.slackAction())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("automation_correction_not_offered"));
        // In flight: the first run has not been delivered yet.
        start(published, published.ruleId(), REASON, UUID.randomUUID(), published.changelogAction())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("automation_correction_not_offered"))
                .andExpect(jsonPath("$.detail").value("Action 1 is not offered: a delivery of it has not finished yet."));

        // Not a published release.
        UUID approved = approvedReleaseWithChange(published.owner(), published.projectId(), "1.5.0");
        startWith(published, published.ruleId(), Map.of("releaseId", approved, "reason", REASON,
                "requestId", UUID.randomUUID(), "ruleActionIds", List.of(published.changelogAction())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("automation_release_not_published"));

        // A rule that cannot deliver now.
        mockMvc.perform(post("/api/automation/rules/{id}/disable", published.ruleId())
                        .session(published.owner().session()).with(csrf()))
                .andExpect(status().isOk());
        start(published, published.ruleId(), REASON, UUID.randomUUID(), published.changelogAction())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("automation_rule_unavailable"))
                .andExpect(jsonPath("$.detail").value(
                        "The rule must be enabled and cover the release's project to deliver a correction."));
        // An archived rule is gone, as for every other call about it.
        mockMvc.perform(delete("/api/automation/rules/{id}", published.ruleId())
                        .session(published.owner().session()).with(csrf()))
                .andExpect(status().isNoContent());
        start(published, published.ruleId(), REASON, UUID.randomUUID(), published.changelogAction())
                .andExpect(status().isNotFound());
    }

    @Test
    void refusesAMemberAnotherOrganizationAndOneReasonInTwoLanguages() throws Exception {
        Published published = published();
        MockHttpSession member = member(published.owner().organizationId(), "member@example.com");
        mockMvc.perform(post("/api/automation/rules/{id}/corrections", published.ruleId())
                        .session(member).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("releaseId", published.releaseId(), "reason", REASON,
                                "requestId", UUID.randomUUID(), "ruleActionIds", List.of(published.changelogAction())))))
                .andExpect(status().isForbidden());
        // The service says so itself, whatever the URL rules are.
        ReleaseFlowPrincipal memberPrincipal = (ReleaseFlowPrincipal) ((SecurityContext) member
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .getAuthentication().getPrincipal();
        assertThat(memberPrincipal.role()).isEqualTo(AppUserRole.MEMBER);
        CorrectionRequest request = new CorrectionRequest();
        request.setReleaseId(published.releaseId());
        request.setRequestId(UUID.randomUUID());
        request.setReason(REASON);
        request.setRuleActionIds(List.of(published.changelogAction()));
        assertThatThrownBy(() -> correctionService.start(memberPrincipal, published.ruleId(), request))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> correctionService.offer(memberPrincipal, published.ruleId(), published.releaseId()))
                .isInstanceOf(AccessDeniedException.class);

        Owner other = registerAndLogin("other@example.com", "Linh Pham");
        mockMvc.perform(get("/api/automation/rules/{id}/corrections", published.ruleId())
                        .session(other.session()).param("releaseId", published.releaseId().toString()))
                .andExpect(status().isNotFound());

        // One reason is shown as written, so it is in one language. The languages are put back
        // whatever happens, since the shared cleanup does not remove them.
        try {
            // One reason is shown as written, so it is in one language.
            mockMvc.perform(put("/api/organization/release-languages").session(published.owner().session()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"targetLanguages\":[\"en\",\"vi\"]}"))
                    .andExpect(status().isOk());
            UUID twoLanguages = createdRuleId(createRule(published.owner(), """
                    {"name":"Both","triggerType":"MANUAL","projectId":"%s","actions":[
                      {"actionType":"PUBLIC_CHANGELOG","audienceId":"%s","language":"en"},
                      {"actionType":"PUBLIC_CHANGELOG","audienceId":"%s","language":"vi"}]}
                    """.formatted(published.projectId(), published.endUser(), published.endUser()))
                    .andExpect(status().isCreated()));
            enable(published.owner(), twoLanguages).andExpect(status().isOk());
            List<UUID> actions = ruleActions(twoLanguages);
            startWith(published, twoLanguages, Map.of("releaseId", published.releaseId(), "reason", REASON,
                    "requestId", UUID.randomUUID(), "ruleActionIds", actions))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("automation_correction_languages_mixed"));
        } finally {
            jdbcTemplate.update("DELETE FROM organization_translation_settings");
        }
    }

    @Test
    void neverSkipsACorrectionForHavingNothingToSayButSkipsARetiredAudience() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, changelogRule("Publish", "RELEASE_PUBLISHED", projectId, endUser))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        // Breaking, so the note lists the change whatever its audiences: it is delivered.
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, changeNumber++,
                "feat(ui): add the inbox", "FEATURE", true, false, owner.userId());
        TestChanges.summarize(jdbcTemplate, change, "The inbox lists new changes.", NOTHING_FOR_ANYBODY);
        UUID releaseId = approvedRelease(owner, projectId, "2.0.0");
        mockMvc.perform(post("/api/projects/{p}/releases/{r}/publish", projectId, releaseId)
                        .session(owner.session()).with(csrf()))
                .andExpect(status().isOk());
        deliverEverything();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public_changelog_entries", Integer.class)).isOne();

        // Reviewed again as not breaking, the note rendered again has nothing for end users.
        mockMvc.perform(post("/api/projects/{p}/changes/{c}/review", projectId, change)
                        .session(owner.session()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"feature\",\"breaking\":false}"))
                .andExpect(status().isOk());
        UUID noteId = endUserNote(releaseId);
        String preview = mockMvc.perform(get("/api/projects/{p}/releases/{r}/notes/{n}/correction-preview",
                        projectId, releaseId, noteId).session(owner.session()).param("origin", "RENDERED"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/projects/{p}/releases/{r}/notes/{n}/revisions", projectId, releaseId, noteId)
                        .session(owner.session()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"RENDERED\",\"corrects\":1,\"previewHash\":\"%s\",\"reason\":\"Not breaking.\"}"
                                .formatted(JsonPath.<String>read(preview, "$.previewHash"))))
                .andExpect(status().isCreated());

        // A run that is not a correction skips it; the correction replaces what the entry says.
        UUID byHand = createdRuleId(createRule(owner, changelogRule("By hand", "MANUAL", projectId, endUser))
                .andExpect(status().isCreated()));
        enable(owner, byHand).andExpect(status().isOk());
        UUID manualRun = executeRule(owner, byHand, releaseId);
        Published published = new Published(owner, projectId, releaseId, ruleId, endUser,
                ruleActions(ruleId).getFirst(), null);
        UUID correction = runId(start(published, ruleId, "Not breaking after all.", UUID.randomUUID(),
                published.changelogAction()).andExpect(status().isAccepted()));
        deliverEverything();
        mockMvc.perform(get("/api/automation/runs/{id}", manualRun).session(owner.session()))
                .andExpect(jsonPath("$.actions[0].status").value("SKIPPED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("nothing_for_audience"));
        mockMvc.perform(get("/api/automation/runs/{id}", correction).session(owner.session()))
                .andExpect(jsonPath("$.actions[0].status").value("SUCCEEDED"));
        assertThat(jdbcTemplate.queryForObject("SELECT content_snapshot FROM public_changelog_entry_revisions",
                String.class)).contains("Nothing in this release concerns this audience.");

        // A correction for an audience retired after the run was created is skipped, and the run succeeds.
        correctByHand(published, "# Release 2.0.0\n\nNothing changes for you.\n", "Plainer.");
        UUID retired = runId(start(published, ruleId, "Plainer.", UUID.randomUUID(), published.changelogAction())
                .andExpect(status().isAccepted()));
        mockMvc.perform(post("/api/audiences/{id}/retire", endUser).session(owner.session()).with(csrf()))
                .andExpect(status().isOk());
        deliverEverything();
        mockMvc.perform(get("/api/automation/runs/{id}", retired).session(owner.session()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.actions[0].status").value("SKIPPED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("audience_retired"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public_changelog_entry_revisions", Integer.class))
                .isOne();
    }

    @Test
    void comparesAnyOtherRunWithTheEntrysLatestRevision() throws Exception {
        Published published = published();
        correctByHand(published, "# Release 1.4.0\n\nThe inbox lists every new change.\n", REASON);
        start(published, published.ruleId(), REASON, UUID.randomUUID(), published.changelogAction())
                .andExpect(status().isAccepted());
        deliverEverything();
        UUID byHand = createdRuleId(createRule(published.owner(), changelogRule("By hand", "MANUAL",
                published.projectId(), published.endUser())).andExpect(status().isCreated()));
        enable(published.owner(), byHand).andExpect(status().isOk());

        // The same words as the entry's latest revision: that entry, and nothing appended.
        UUID same = executeRule(published.owner(), byHand, published.releaseId());
        deliverEverything();
        mockMvc.perform(get("/api/automation/runs/{id}", same).session(published.owner().session()))
                .andExpect(jsonPath("$.actions[0].status").value("SUCCEEDED"));

        // Different words: refused, since only a correction appends a revision.
        correctByHand(published, "# Release 1.4.0\n\nThe inbox lists each new change.\n", "Each, not every.", 2);
        UUID different = executeRule(published.owner(), byHand, published.releaseId());
        deliverEverything();
        mockMvc.perform(get("/api/automation/runs/{id}", different).session(published.owner().session()))
                .andExpect(jsonPath("$.actions[0].status").value("FAILED"))
                .andExpect(jsonPath("$.actions[0].errorCode").value("public_changelog_conflict"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public_changelog_entry_revisions", Integer.class))
                .isOne();
    }

    @Test
    void retriesAnUnknownCorrectionOnlyWhenConfirmedAndNeverAppendsTwice() throws Exception {
        Published published = published();
        correctByHand(published, "# Release 1.4.0\n\nThe inbox lists every new change.\n", REASON);
        UUID runId = runId(start(published, published.ruleId(), REASON, UUID.randomUUID(), published.changelogAction())
                .andExpect(status().isAccepted()));
        deliverEverything();
        // The revision was written, and then nobody heard how it ended.
        jdbcTemplate.update("UPDATE automation_action_runs SET status = 'UNKNOWN', error_code = 'execution_outcome_unknown', "
                + "external_reference = NULL WHERE run_id = ?", runId);
        jdbcTemplate.update("UPDATE automation_runs SET status = 'UNKNOWN' WHERE id = ?", runId);

        mockMvc.perform(post("/api/automation/runs/{id}/retry", runId).session(published.owner().session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("automation_unknown_needs_confirmation"));
        mockMvc.perform(post("/api/automation/runs/{id}/retry", runId).session(published.owner().session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmUnknown\":true}"))
                .andExpect(status().isOk());
        deliverEverything();

        mockMvc.perform(get("/api/automation/runs/{id}", runId).session(published.owner().session()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.actions[0].attempts").value(2));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public_changelog_entry_revisions", Integer.class))
                .isOne();
    }

    @Test
    void startsOnlyWhenAPersonAsksAndNeverAsARulesTrigger() throws Exception {
        Published published = published();
        long runsBefore = runs();
        correctByHand(published, "# Release 1.4.0\n\nThe inbox lists every new change.\n", REASON);
        // Saving a revision delivers nothing.
        deliverEverything();
        assertThat(runs()).isEqualTo(runsBefore);

        createRule(published.owner(), changelogRule("Never", "CORRECTION", published.projectId(), published.endUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("automation_trigger_invalid"));
        mockMvc.perform(put("/api/automation/rules/{id}", published.ruleId())
                        .session(published.owner().session()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(changelogRule("Never", "CORRECTION", published.projectId(), published.endUser())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("automation_trigger_invalid"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM automation_runs WHERE trigger_type = 'CORRECTION'", Integer.class)).isZero();
    }

    @Test
    void writesTheRowsThePublicChangelogTestsStandInFor() throws Exception {
        Published published = published();
        correctByHand(published, "# Release 1.4.0\n\nThe inbox lists every new change.\n", REASON);
        UUID runId = runId(start(published, published.ruleId(), REASON, UUID.randomUUID(), published.changelogAction())
                .andExpect(status().isAccepted()));
        deliverEverything();

        // PublicChangelogTestBase.correct(…) writes these columns, and the same ones are empty.
        assertThat(emptyColumns("automation_runs", runId)).containsExactlyInAnyOrder("scheduled_for");
        UUID actionRunId = jdbcTemplate.queryForObject(
                "SELECT id FROM automation_action_runs WHERE run_id = ?", UUID.class, runId);
        assertThat(emptyColumns("automation_action_runs", actionRunId))
                .containsExactlyInAnyOrder("secret_nonce_snapshot", "secret_ciphertext_snapshot", "error_code");
        assertThat(jdbcTemplate.queryForMap("""
                SELECT run.trigger_type, run.reason, run.request_id IS NOT NULL AS asked, run.initiated_by IS NOT NULL AS by_whom,
                       action_run.note_revision, revision.action_run_id = action_run.id AS written_by_it
                FROM automation_runs run
                JOIN automation_action_runs action_run ON action_run.run_id = run.id
                JOIN public_changelog_entry_revisions revision ON revision.action_run_id = action_run.id
                WHERE run.id = ?
                """, runId))
                .containsEntry("trigger_type", "CORRECTION").containsEntry("reason", REASON)
                .containsEntry("asked", true).containsEntry("by_whom", true)
                .containsEntry("note_revision", 2).containsEntry("written_by_it", true);
    }

    @Test
    void startsACorrectionOnlyOnceARevisionSavedMeanwhileIsCommitted() throws Exception {
        Published published = published();
        correctByHand(published, "# Release 1.4.0\n\nThe inbox lists every new change.\n", REASON);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> holder = holdTheReleaseWhileSavingRevision3(published, locked, release);
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<String> correction = CompletableFuture.supplyAsync(() -> call(() -> start(published,
                published.ruleId(), REASON, UUID.randomUUID(), published.changelogAction())
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()));
        Thread.sleep(500);
        assertThat(correction).isNotDone();
        release.countDown();
        holder.get(10, TimeUnit.SECONDS);

        assertThat(JsonPath.<Integer>read(correction.get(10, TimeUnit.SECONDS), "$.actions[0].noteRevision")).isEqualTo(3);
    }

    @Test
    void startsAManualRunOnlyOnceARevisionSavedMeanwhileIsCommitted() throws Exception {
        Published published = published();
        UUID byHand = createdRuleId(createRule(published.owner(), changelogRule("By hand", "MANUAL",
                published.projectId(), published.endUser())).andExpect(status().isCreated()));
        enable(published.owner(), byHand).andExpect(status().isOk());
        correctByHand(published, "# Release 1.4.0\n\nThe inbox lists every new change.\n", REASON);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> holder = holdTheReleaseWhileSavingRevision3(published, locked, release);
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<String> manual = CompletableFuture.supplyAsync(() -> call(() -> mockMvc.perform(
                        post("/api/automation/rules/{id}/execute", byHand).session(published.owner().session()).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(Map.of("releaseId", published.releaseId(), "requestId", UUID.randomUUID()))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()));
        Thread.sleep(500);
        assertThat(manual).isNotDone();
        release.countDown();
        holder.get(10, TimeUnit.SECONDS);

        assertThat(JsonPath.<Integer>read(manual.get(10, TimeUnit.SECONDS), "$.actions[0].noteRevision")).isEqualTo(3);
    }

    /**
     * Another transaction that locks the release, as saving a correction does, writes revision
     * 3 of the end users' note, and commits only when told to.
     */
    private CompletableFuture<Void> holdTheReleaseWhileSavingRevision3(
            Published published,
            CountDownLatch locked,
            CountDownLatch release
    ) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        UUID noteId = endUserNote(published.releaseId());
        return CompletableFuture.runAsync(() -> transaction.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject("SELECT id FROM releases WHERE id = ? FOR NO KEY UPDATE", UUID.class,
                    published.releaseId());
            jdbcTemplate.update("""
                    INSERT INTO release_note_revisions (id, note_id, release_id, organization_id, project_id, revision,
                        content, origin, reason, author_id, author_name, glossary_status, created_at)
                    SELECT gen_random_uuid(), note_id, release_id, organization_id, project_id, 3,
                        '# Release 1.4.0' || chr(10) || chr(10) || 'The inbox lists each new change.' || chr(10),
                        origin, 'Each, not every.', author_id, author_name, glossary_status, now()
                    FROM release_note_revisions WHERE note_id = ? AND revision = 2
                    """, noteId);
            locked.countDown();
            try {
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }));
    }

    /** A rule that publishes end users' English note to the changelog and to Slack, delivered. */
    private Published published() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, """
                {"name":"Publish","triggerType":"RELEASE_PUBLISHED","projectId":"%s","actions":[
                  {"actionType":"PUBLIC_CHANGELOG","audienceId":"%s","language":"en"},
                  {"actionType":"SLACK","audienceId":"%s","language":"en","secret":"%s"}]}
                """.formatted(projectId, endUser, endUser, slackWebhook())).andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        deliverEverything();
        List<UUID> actions = ruleActions(ruleId);
        return new Published(owner, projectId, releaseId, ruleId, endUser, actions.get(0), actions.get(1));
    }

    private record Published(Owner owner, UUID projectId, UUID releaseId, UUID ruleId, UUID endUser,
                             UUID changelogAction, UUID slackAction) {
    }

    private String changelogRule(String name, String trigger, UUID projectId, UUID audienceId) {
        return """
                {"name":"%s","triggerType":"%s","projectId":"%s",
                 "actions":[{"actionType":"PUBLIC_CHANGELOG","audienceId":"%s","language":"en"}]}
                """.formatted(name, trigger, projectId, audienceId);
    }

    private List<UUID> ruleActions(UUID ruleId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM automation_rule_actions WHERE rule_id = ? ORDER BY position", UUID.class, ruleId);
    }

    private UUID endUserNote(UUID releaseId) {
        return jdbcTemplate.queryForObject("""
                SELECT note.id FROM release_audience_notes note
                JOIN audience_definitions audience ON audience.id = note.audience_id
                WHERE note.release_id = ? AND audience.code = 'end_user' AND note.language = 'en'
                """, UUID.class, releaseId);
    }

    private void correctByHand(Published published, String content, String reason) throws Exception {
        correctByHand(published, content, reason, latestRevision(published));
    }

    private void correctByHand(Published published, String content, String reason, int corrects) throws Exception {
        mockMvc.perform(post("/api/projects/{p}/releases/{r}/notes/{n}/revisions", published.projectId(),
                        published.releaseId(), endUserNote(published.releaseId()))
                        .session(published.owner().session()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("corrects", corrects, "content", content, "reason", reason))))
                .andExpect(status().isCreated());
    }

    private int latestRevision(Published published) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(revision), 1) FROM release_note_revisions WHERE note_id = ?",
                Integer.class, endUserNote(published.releaseId()));
    }

    private ResultActions offer(Published published, UUID ruleId) throws Exception {
        return mockMvc.perform(get("/api/automation/rules/{id}/corrections", ruleId)
                        .session(published.owner().session()).param("releaseId", published.releaseId().toString()))
                .andExpect(status().isOk());
    }

    private ResultActions start(Published published, UUID ruleId, String reason, UUID requestId, UUID... actions)
            throws Exception {
        return startWith(published, ruleId, Map.of("releaseId", published.releaseId(), "reason", reason,
                "requestId", requestId, "ruleActionIds", List.of(actions)));
    }

    private ResultActions startWith(Published published, UUID ruleId, Map<String, Object> request) throws Exception {
        return mockMvc.perform(post("/api/automation/rules/{id}/corrections", ruleId)
                .session(published.owner().session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(request)));
    }

    private UUID execute(Published published, UUID ruleId) throws Exception {
        return executeRule(published.owner(), ruleId, published.releaseId());
    }

    private UUID executeRule(Owner owner, UUID ruleId, UUID releaseId) throws Exception {
        return runId(mockMvc.perform(post("/api/automation/rules/{id}/execute", ruleId)
                        .session(owner.session()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("releaseId", releaseId, "requestId", UUID.randomUUID()))))
                .andExpect(status().isAccepted()));
    }

    private static UUID runId(ResultActions result) throws Exception {
        return UUID.fromString(JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id"));
    }

    private long runs() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM automation_runs", Long.class);
    }

    private List<String> emptyColumns(String table, UUID id) {
        return jdbcTemplate.queryForList("""
                SELECT key FROM jsonb_each((SELECT to_jsonb(row) FROM %s row WHERE id = ?)) WHERE value = 'null'::jsonb
                """.formatted(table), String.class, id);
    }

    private double counter(String trigger, String outcome) {
        return registry.get(AutomationMetrics.EXECUTIONS).tag("trigger", trigger).tag("outcome", outcome)
                .counter().count();
    }

    private static String body(Map<String, ?> values) {
        return JSON.writeValueAsString(values);
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
}
