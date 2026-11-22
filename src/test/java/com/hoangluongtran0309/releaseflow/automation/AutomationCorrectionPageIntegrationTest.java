package com.hoangluongtran0309.releaseflow.automation;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * ADR-0046 part 5 on the pages: the correction form names every action of the rule, offered
 * or with its reason, starts the run, and the run history shows it as a correction.
 */
class AutomationCorrectionPageIntegrationTest extends AutomationIntegrationTestBase {

    @Test
    void offersTheCorrectedDestinationAndNamesTheOthersInTheReadersLanguage() throws Exception {
        Fixture fixture = fixture();
        String form = "/automation/" + fixture.ruleId() + "/corrections";

        mockMvc.perform(get("/automation").session(fixture.owner().session()))
                .andExpect(content().string(containsString("href=\"" + form + "\"")))
                .andExpect(content().string(containsString("Deliver a correction")));
        mockMvc.perform(get(form).session(fixture.owner().session()))
                .andExpect(status().isOk())
                .andExpect(view().name("automation-correction"))
                .andExpect(content().string(containsString("Deliver a correction with Publish")))
                .andExpect(content().string(containsString("Show what can be corrected")))
                .andExpect(content().string(not(containsString("id=\"correction-offer\""))));

        mockMvc.perform(get(form).session(fixture.owner().session()).param("releaseId", fixture.releaseId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1. Public changelog — End user, en")))
                .andExpect(content().string(containsString("Delivers revision 2; it holds revision 1.")))
                .andExpect(content().string(containsString("value=\"" + fixture.changelogAction() + "\" class=\"checkbox mt-0.5\" checked")))
                .andExpect(content().string(containsString("2. Slack — End user, en")))
                .andExpect(content().string(containsString("Correcting this kind of destination comes later.")))
                .andExpect(content().string(containsString("issues/88")))
                // The reason starts as the revision's.
                .andExpect(content().string(containsString(">Placeholders keep their case.</textarea>")));

        mockMvc.perform(get(form).session(fixture.owner().session()).header("Accept-Language", "vi")
                        .param("releaseId", fixture.releaseId().toString()))
                .andExpect(content().string(containsString("Gửi bản sửa bằng Publish")))
                .andExpect(content().string(containsString("Gửi bản sửa đổi 2; nơi này đang giữ bản sửa đổi 1.")))
                .andExpect(content().string(containsString("Việc sửa loại nơi nhận này sẽ có sau.")));
    }

    @Test
    void startsTheRunAndShowsItAsACorrection() throws Exception {
        Fixture fixture = fixture();
        String form = "/automation/" + fixture.ruleId() + "/corrections";
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(post(form).session(fixture.owner().session()).with(csrf())
                        .param("releaseId", fixture.releaseId().toString())
                        .param("requestId", requestId.toString())
                        .param("ruleActionIds", fixture.changelogAction().toString())
                        .param("reason", "Placeholders keep their case."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/automation"))
                .andExpect(flash().attributeExists("notice"));
        // The same form sent twice is the same run.
        mockMvc.perform(post(form).session(fixture.owner().session()).with(csrf())
                        .param("releaseId", fixture.releaseId().toString())
                        .param("requestId", requestId.toString())
                        .param("ruleActionIds", fixture.changelogAction().toString())
                        .param("reason", "Placeholders keep their case."))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM automation_runs WHERE trigger_type = 'CORRECTION'", Integer.class)).isOne();
        deliverEverything();

        mockMvc.perform(get("/automation").session(fixture.owner().session()))
                .andExpect(content().string(containsString(">Correction</span>")))
                .andExpect(content().string(containsString("Reason: Placeholders keep their case.")))
                .andExpect(content().string(containsString("revision 2, replaces revision 1")));
    }

    @Test
    void saysWhatIsMissingAndWhyAPickedActionIsNotOffered() throws Exception {
        Fixture fixture = fixture();
        String form = "/automation/" + fixture.ruleId() + "/corrections";

        // The release page points a behind destination at the form.
        mockMvc.perform(get("/projects/{p}/releases/{r}", fixture.projectId(), fixture.releaseId())
                        .session(fixture.owner().session()))
                .andExpect(content().string(containsString(
                        form + "?releaseId=" + fixture.releaseId())))
                .andExpect(content().string(containsString("Deliver the correction")));

        mockMvc.perform(post(form).session(fixture.owner().session()).with(csrf())
                        .param("releaseId", fixture.releaseId().toString())
                        .param("requestId", UUID.randomUUID().toString())
                        .param("reason", " "))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Pick at least one destination.")))
                .andExpect(content().string(containsString("Say why the note was corrected.")));
        mockMvc.perform(post(form).session(fixture.owner().session()).with(csrf())
                        .param("releaseId", fixture.releaseId().toString())
                        .param("requestId", UUID.randomUUID().toString())
                        .param("ruleActionIds", fixture.slackAction().toString())
                        .param("reason", "Placeholders keep their case."))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString(
                        "Action 2 is not offered: correcting this kind of destination comes later.")));

        MockHttpSession member = member(fixture.owner().organizationId(), "member@example.com");
        mockMvc.perform(get(form).session(member)).andExpect(status().isForbidden());
        mockMvc.perform(get("/projects/{p}/releases/{r}", fixture.projectId(), fixture.releaseId()).session(member))
                .andExpect(content().string(not(containsString(form))));
    }

    /** A published release whose end users' note was corrected by hand after a rule delivered it. */
    private Fixture fixture() throws Exception {
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
        UUID noteId = jdbcTemplate.queryForObject("""
                SELECT note.id FROM release_audience_notes note
                JOIN audience_definitions audience ON audience.id = note.audience_id
                WHERE note.release_id = ? AND audience.code = 'end_user' AND note.language = 'en'
                """, UUID.class, releaseId);
        mockMvc.perform(post("/api/projects/{p}/releases/{r}/notes/{n}/revisions", projectId, releaseId, noteId)
                        .session(owner.session()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"corrects\":1,\"content\":\"# Release 1.4.0\\n\\nThe inbox lists every new change.\\n\","
                                + "\"reason\":\"Placeholders keep their case.\"}"))
                .andExpect(status().isCreated());
        var actions = jdbcTemplate.queryForList(
                "SELECT id FROM automation_rule_actions WHERE rule_id = ? ORDER BY position", UUID.class, ruleId);
        return new Fixture(owner, projectId, releaseId, ruleId, actions.get(0), actions.get(1));
    }

    private record Fixture(Owner owner, UUID projectId, UUID releaseId, UUID ruleId, UUID changelogAction,
                           UUID slackAction) {
    }
}
