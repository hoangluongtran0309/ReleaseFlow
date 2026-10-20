package com.hoangluongtran0309.releaseflow.automation;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An audience that a rule's action or a run's history names is refused with
 * {@code 409 audience_in_use}, whatever state the rule is in, and the refusal says which
 * of them keeps it. The audience is added after the release's notes were written, so
 * notes are never what holds it.
 */
class AudienceInUseIntegrationTest extends AutomationIntegrationTestBase {

    @Test
    void aRuleKeepsItsAudienceWhetherEnabledDisabledOrArchived() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID audienceId = createAudience(owner, "leadership", "Leadership");
        UUID ruleId = createdRuleId(createRule(owner, slackRule("Announce", "MANUAL", projectId, audienceId, "en"))
                .andExpect(status().isCreated()));

        expectInUse(deleteAudience(owner, audienceId), "Choose another audience");
        enable(owner, ruleId).andExpect(status().isOk());
        expectInUse(deleteAudience(owner, audienceId), "Choose another audience");
        mockMvc.perform(post("/api/automation/rules/{ruleId}/disable", ruleId).session(owner.session()).with(csrf()))
                .andExpect(status().isOk());
        expectInUse(deleteAudience(owner, audienceId), "Choose another audience");

        mockMvc.perform(delete("/api/automation/rules/{ruleId}", ruleId).session(owner.session()).with(csrf()))
                .andExpect(status().isNoContent());
        expectInUse(deleteAudience(owner, audienceId), "archived automation rule");
        assertThat(audienceCount(owner)).isEqualTo(4);
    }

    @Test
    void runHistoryKeepsAnAudienceAfterTheRuleStopsNamingIt() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID releaseId = approvedReleaseWithChange(owner, projectId, "1.0.0");
        UUID audienceId = createAudience(owner, "leadership", "Leadership");
        UUID ruleId = createdRuleId(createRule(owner, slackRule("Announce", "RELEASE_PUBLISHED", projectId, audienceId, "en"))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/publish", projectId, releaseId)
                        .session(owner.session()).with(csrf()))
                .andExpect(status().isOk());
        deliverEverything();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM automation_action_runs WHERE audience_id = ?", Integer.class, audienceId
        )).isEqualTo(1);

        // The rule now delivers to another audience; only its history names this one.
        mockMvc.perform(put("/api/automation/rules/{ruleId}", ruleId)
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slackRule("Announce", "RELEASE_PUBLISHED", projectId, audienceId(owner, "end_user"), "en")))
                .andExpect(status().isOk());
        expectInUse(deleteAudience(owner, audienceId), "its history keeps it");

        mockMvc.perform(delete("/api/automation/rules/{ruleId}", ruleId).session(owner.session()).with(csrf()))
                .andExpect(status().isNoContent());
        expectInUse(deleteAudience(owner, audienceId), "its history keeps it");
        assertThat(audienceCount(owner)).isEqualTo(4);
    }

    @Test
    void aPageDeleteShowsTheSameRefusal() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID audienceId = createAudience(owner, "leadership", "Leadership");
        UUID ruleId = createdRuleId(createRule(owner, slackRule("Announce", "MANUAL", projectId, audienceId, "en"))
                .andExpect(status().isCreated()));
        mockMvc.perform(delete("/api/automation/rules/{ruleId}", ruleId).session(owner.session()).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/audiences/{audienceId}/delete", audienceId).session(owner.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("archived automation rule")));
    }

    private ResultActions deleteAudience(Owner owner, UUID audienceId) throws Exception {
        return mockMvc.perform(delete("/api/audiences/{audienceId}", audienceId)
                .session(owner.session())
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON));
    }

    private static void expectInUse(ResultActions result, String detail) throws Exception {
        result.andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("audience_in_use"))
                .andExpect(jsonPath("$.detail", containsString(detail)));
    }

    private int audienceCount(Owner owner) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audience_definitions WHERE organization_id = ?", Integer.class, owner.organizationId());
    }
}
