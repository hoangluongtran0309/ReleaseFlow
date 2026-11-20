package com.hoangluongtran0309.releaseflow.automation;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0046: a published note reads as its latest revision wherever no number is named, the
 * release's notes, the download, and what a new automation run carries, and every revision
 * reads by its number. With no correction, that is the note exactly as published.
 */
class NoteRevisionReadIntegrationTest extends AutomationIntegrationTestBase {

    private static final String CORRECTED = "# Release 1.4.0\n\nCorrected.\n";

    @Test
    void readsAPublishedNoteAsRevisionOneUntilALaterRevisionIsTheNote() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID ruleId = createdRuleId(createRule(owner, slackRule("By hand", "MANUAL", projectId, endUser, "en"))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        UUID noteId = noteId(owner, projectId, releaseId, "end_user");
        String published = noteContent(owner, projectId, releaseId, noteId);

        revisions(owner.session(), projectId, releaseId, noteId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].revision").value(1))
                .andExpect(jsonPath("$[0].origin").value("PUBLISHED"))
                .andExpect(jsonPath("$[0].content").value(published))
                .andExpect(jsonPath("$[0].reason", nullValue()))
                .andExpect(jsonPath("$[0].authorName").value("Mai Tran"))
                .andExpect(jsonPath("$[0].createdAt", notNullValue()));
        revision(owner.session(), projectId, releaseId, noteId, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(published));
        revision(owner.session(), projectId, releaseId, noteId, 2)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("release_note_not_found"));
        String firstRun = execute(owner, ruleId, releaseId)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.actions[0].noteRevision").value(1))
                .andReturn().getResponse().getContentAsString();

        insertRevision(owner, projectId, releaseId, noteId);

        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/notes", projectId, releaseId)
                        .session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].content".formatted(noteId)).value(CORRECTED))
                .andExpect(jsonPath("$[?(@.id == '%s')].revision".formatted(noteId)).value(2))
                .andExpect(jsonPath("$[?(@.id != '%s')].revision".formatted(noteId)).value(
                        everyItem(is(1))));
        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}", projectId, releaseId)
                        .session(owner.session()))
                .andExpect(jsonPath("$.notes[?(@.id == '%s')].content".formatted(noteId)).value(CORRECTED));
        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/download",
                        projectId, releaseId, noteId).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(CORRECTED));
        revisions(owner.session(), projectId, releaseId, noteId)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value(published))
                .andExpect(jsonPath("$[1].revision").value(2))
                .andExpect(jsonPath("$[1].origin").value("MANUAL"))
                .andExpect(jsonPath("$[1].reason").value("Fix the placeholders"))
                .andExpect(jsonPath("$[1].authorName").value("Mai Tran"));

        // A run created now carries the correction; the earlier run keeps what it carried.
        String secondRun = execute(owner, ruleId, releaseId)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.actions[0].noteRevision").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT note_content_snapshot FROM automation_action_runs WHERE run_id = ?", String.class,
                UUID.fromString(JsonPath.read(secondRun, "$.id")))).isEqualTo(CORRECTED);
        UUID firstRunId = UUID.fromString(JsonPath.read(firstRun, "$.id"));
        mockMvc.perform(get("/api/automation/runs/{runId}", firstRunId).session(owner.session()))
                .andExpect(jsonPath("$.actions[0].noteRevision").value(1));

        // A run written before revisions existed carried revision 1, the only one there was.
        jdbcTemplate.update("UPDATE automation_action_runs SET note_revision = NULL WHERE run_id = ?", firstRunId);
        mockMvc.perform(get("/api/automation/runs/{runId}", firstRunId).session(owner.session()))
                .andExpect(jsonPath("$.actions[0].noteRevision").value(1));
    }

    @Test
    void readsRevisionsOnlyOfAPublishedNoteOfTheReadersOwnOrganization() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID publishedId = publishedRelease(owner, projectId, "1.4.0");
        UUID approvedId = approvedReleaseWithChange(owner, projectId, "1.5.0");
        UUID publishedNote = noteId(owner, projectId, publishedId, "end_user");
        UUID approvedNote = noteId(owner, projectId, approvedId, "end_user");

        // An approved note is edited in place, and its revisions begin at publication.
        revisions(owner.session(), projectId, approvedId, approvedNote)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_status_conflict"));
        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/notes", projectId, approvedId)
                        .session(owner.session()))
                .andExpect(jsonPath("$[0].revision").value(1));
        // Another release's note is not this release's.
        revisions(owner.session(), projectId, publishedId, approvedNote)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("release_note_not_found"));

        // Every member reads revisions; another Organization finds nothing.
        MockHttpSession member = member(owner.organizationId(), "member@example.com");
        revisions(member, projectId, publishedId, publishedNote).andExpect(status().isOk());
        Owner other = registerAndLogin("other@example.com", "Lan Pham");
        revisions(other.session(), projectId, publishedId, publishedNote).andExpect(status().isNotFound());
        revision(other.session(), projectId, publishedId, publishedNote, 1).andExpect(status().isNotFound());
    }

    private ResultActions revisions(MockHttpSession session, UUID projectId, UUID releaseId, UUID noteId)
            throws Exception {
        return mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/revisions",
                projectId, releaseId, noteId).session(session));
    }

    private ResultActions revision(MockHttpSession session, UUID projectId, UUID releaseId, UUID noteId, int number)
            throws Exception {
        return mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/revisions/{n}",
                projectId, releaseId, noteId, number).session(session));
    }

    private ResultActions execute(Owner owner, UUID ruleId, UUID releaseId) throws Exception {
        return mockMvc.perform(post("/api/automation/rules/{ruleId}/execute", ruleId)
                .session(owner.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"releaseId\":\"%s\",\"requestId\":\"%s\"}".formatted(releaseId, UUID.randomUUID())));
    }

    private UUID noteId(Owner owner, UUID projectId, UUID releaseId, String audienceCode) throws Exception {
        String body = mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/notes", projectId, releaseId)
                        .session(owner.session()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body,
                "$[?(@.audienceCode == '%s' && @.language == 'en')].id".formatted(audienceCode));
        return UUID.fromString(ids.getFirst());
    }

    private String noteContent(Owner owner, UUID projectId, UUID releaseId, UUID noteId) throws Exception {
        String body = mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/notes", projectId, releaseId)
                        .session(owner.session()))
                .andReturn().getResponse().getContentAsString();
        List<String> contents = JsonPath.read(body, "$[?(@.id == '%s')].content".formatted(noteId));
        return contents.getFirst();
    }

    // Slice A writes revisions through its own endpoint in a later change; here one is stored directly.
    private void insertRevision(Owner owner, UUID projectId, UUID releaseId, UUID noteId) {
        jdbcTemplate.update(
                """
                        INSERT INTO release_note_revisions (id, note_id, release_id, organization_id, project_id, revision,
                            content, origin, reason, author_id, author_name, glossary_status, created_at)
                        VALUES (?, ?, ?, ?, ?, 2, ?, 'MANUAL', 'Fix the placeholders', ?, 'Mai Tran', 'NONE', now())
                        """,
                UUID.randomUUID(), noteId, releaseId, owner.organizationId(), projectId, CORRECTED, owner.userId());
    }
}
