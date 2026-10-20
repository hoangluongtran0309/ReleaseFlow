package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.automation.AutomationIntegrationTestBase;
import com.hoangluongtran0309.releaseflow.support.TestChanges;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A change an administrator says will never ship stays in the Inbox with its review, and
 * no release offers it until somebody takes that back (ADR-0033).
 */
class ChangeExclusionIntegrationTest extends AutomationIntegrationTestBase {

    @Test
    void anExcludedChangeIsNoLongerOfferedUntilItIsIncludedAgain() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID kept = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add export", "FEATURE", false, false, null);
        UUID duplicate = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 2,
                "DBV-20: add export", "UNKNOWN", false, true, null);

        exclude(owner, projectId, duplicate, "  Duplicates its pull request.  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exclusion.excluderName").value("Mai Tran"))
                .andExpect(jsonPath("$.exclusion.reason").value("Duplicates its pull request."))
                .andExpect(jsonPath("$.exclusion.excludedAt").exists())
                // Excluding settles nothing: the change still needs its review.
                .andExpect(jsonPath("$.needsReview").value(true))
                .andExpect(jsonPath("$.category").value("UNKNOWN"));
        exclude(owner, projectId, duplicate, "Again.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("change_excluded"));

        UUID releaseId = draft(owner, projectId, "1.0.0");
        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/available-changes", projectId, releaseId)
                        .session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(kept.toString())));
        add(owner, projectId, releaseId, "{\"changeIds\":[\"" + duplicate + "\"]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("change_excluded"));
        add(owner, projectId, releaseId, "{\"allAvailable\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[*].id").value(contains(kept.toString())));

        // The Inbox keeps it, and can show only what was excluded.
        mockMvc.perform(get("/api/projects/{projectId}/changes", projectId).param("release", "excluded")
                        .session(owner.session()))
                .andExpect(jsonPath("$[*].id").value(contains(duplicate.toString())));
        mockMvc.perform(get("/api/projects/{projectId}/changes", projectId).param("release", "elsewhere")
                        .session(owner.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_change_filter"));

        mockMvc.perform(delete("/api/projects/{projectId}/changes/{changeId}/exclusion", projectId, duplicate)
                        .session(owner.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exclusion").doesNotExist());
        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/available-changes", projectId, releaseId)
                        .session(owner.session()))
                .andExpect(jsonPath("$[*].id").value(contains(duplicate.toString())));
    }

    @Test
    void aChangeInAnyReleaseIsRemovedFromItBeforeItIsExcluded() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add export", "FEATURE", false, false, null);
        UUID releaseId = draft(owner, projectId, "1.0.0");
        add(owner, projectId, releaseId, "{\"allAvailable\":true}").andExpect(status().isOk());

        exclude(owner, projectId, change, "Reverted.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("change_in_release"));

        mockMvc.perform(delete("/api/projects/{projectId}/releases/{releaseId}/changes/{changeId}", projectId, releaseId, change)
                        .session(owner.session()).with(csrf()))
                .andExpect(status().isOk());
        exclude(owner, projectId, change, "Reverted.").andExpect(status().isOk());

        // The database refuses it too, whatever a caller read before.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO release_changes (release_id, change_id, organization_id, project_id, added_at) VALUES (?, ?, ?, ?, now())",
                releaseId, change, owner.organizationId(), projectId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("release_changes_change_not_excluded");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE changes SET exclusion_reason = NULL WHERE id = ?", change))
                .as("an exclusion is recorded whole")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyAnAdministratorOfTheOrganizationExcludes() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        Owner other = registerAndLogin("other@example.com", "Other Owner");
        UUID projectId = createProject(owner);
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add export", "FEATURE", false, false, null);
        MockHttpSession member = member(owner.organizationId(), "member@example.com");

        mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/exclusion", projectId, change)
                        .session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"No.\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/projects/{projectId}/changes/{changeId}/exclusion", projectId, change)
                        .session(member).with(csrf()).param("reason", "No."))
                .andExpect(status().isForbidden());
        exclude(other, projectId, change, "Not mine.")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("change_not_found"));
        exclude(owner, projectId, change, " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        exclude(owner, projectId, change, "x".repeat(501))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        assertThat(jdbcTemplate.queryForObject("SELECT excluded_at IS NULL FROM changes WHERE id = ?", Boolean.class, change))
                .isTrue();

        // A member still sees what was decided, without the controls to change it.
        exclude(owner, projectId, change, "Reverted by #2.").andExpect(status().isOk());
        mockMvc.perform(get("/changes").param("project", projectId.toString()).session(member))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Excluded from releases by")))
                .andExpect(content().string(containsString("Reverted by #2.")))
                .andExpect(content().string(not(containsString("Include in releases again"))));
    }

    @Test
    void theInboxExcludesAndIncludesThroughItsForms() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add export", "FEATURE", false, false, null);

        mockMvc.perform(post("/projects/{projectId}/changes/{changeId}/exclusion", projectId, change)
                        .session(owner.session()).with(csrf()).param("reason", ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Say why this change will never ship.")));
        mockMvc.perform(post("/projects/{projectId}/changes/{changeId}/exclusion", projectId, change)
                        .session(owner.session()).with(csrf())
                        .param("reason", "Duplicates its pull request.")
                        .param("returnRelease", "excluded"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/changes?project=" + projectId + "&release=excluded#change-" + change));
        mockMvc.perform(get("/changes").param("project", projectId.toString()).param("release", "excluded")
                        .session(owner.session()))
                .andExpect(content().string(containsString("Duplicates its pull request.")))
                .andExpect(content().string(containsString("Include in releases again")));

        mockMvc.perform(post("/projects/{projectId}/changes/{changeId}/exclusion/delete", projectId, change)
                        .session(owner.session()).with(csrf()))
                .andExpect(status().isFound());
        assertThat(jdbcTemplate.queryForObject("SELECT excluded_at IS NULL FROM changes WHERE id = ?", Boolean.class, change))
                .isTrue();
    }

    private ResultActions exclude(Owner owner, UUID projectId, UUID changeId, String reason) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/exclusion", projectId, changeId)
                .session(owner.session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"%s\"}".formatted(reason)));
    }

    private UUID draft(Owner owner, UUID projectId, String version) throws Exception {
        String body = mockMvc.perform(post("/api/projects/{projectId}/releases", projectId)
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"%s\"}".formatted(version)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private ResultActions add(Owner owner, UUID projectId, UUID releaseId, String body) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/changes", projectId, releaseId)
                .session(owner.session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
