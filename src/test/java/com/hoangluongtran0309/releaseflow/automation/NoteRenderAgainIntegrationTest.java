package com.hoangluongtran0309.releaseflow.automation;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0046: a published note rendered again, from today's writer, its own template and footer,
 * and the release's changes as they read now, is a preview a person reads; saving it sends
 * back the preview's hash. A note a person wrote and a retired audience's note are corrected
 * by hand only.
 */
class NoteRenderAgainIntegrationTest extends AutomationIntegrationTestBase {

    @Test
    void rendersAPublishedNoteAgainAndSavesWhatThePreviewShowed() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID noteId = noteId(owner.session(), projectId, releaseId, "operator");
        String published = noteContent(owner.session(), projectId, releaseId, noteId);

        // Nothing has changed since publication, so rendering again gives the same note.
        preview(owner.session(), projectId, releaseId, noteId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("RENDERED"))
                .andExpect(jsonPath("$.corrects").value(1))
                .andExpect(jsonPath("$.content").value(published))
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.missingTexts").value(0))
                .andExpect(jsonPath("$.missingCharacters").value(0));
        String unchanged = previewHash(owner.session(), projectId, releaseId, noteId);
        saveRendered(owner.session(), projectId, releaseId, noteId, 1, unchanged)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_note_correction_unchanged"));

        // A review after publication moves the change to another section; rendering again shows it.
        review(owner, projectId, releaseId, "fix");
        String stale = previewHash(owner.session(), projectId, releaseId, noteId);
        review(owner, projectId, releaseId, "documentation");
        saveRendered(owner.session(), projectId, releaseId, noteId, 1, stale)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_note_preview_changed"));

        String body = preview(owner.session(), projectId, releaseId, noteId)
                .andExpect(jsonPath("$.changed").value(true))
                .andReturn().getResponse().getContentAsString();
        String rendered = JsonPath.read(body, "$.content");
        assertThat(rendered).isNotEqualTo(published);
        // Every member reads the preview; only an administrator saves it.
        MockHttpSession member = member(owner.organizationId(), "member@example.com");
        preview(member, projectId, releaseId, noteId).andExpect(status().isOk());
        saveRendered(member, projectId, releaseId, noteId, 1, JsonPath.read(body, "$.previewHash"))
                .andExpect(status().isForbidden());

        saveRendered(owner.session(), projectId, releaseId, noteId, 1, JsonPath.read(body, "$.previewHash"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.origin").value("RENDERED"))
                .andExpect(jsonPath("$.content").value(rendered))
                .andExpect(jsonPath("$.glossaryStatus").value("NONE"));
        assertThat(noteContent(owner.session(), projectId, releaseId, noteId)).isEqualTo(rendered);
        // A rendered revision still follows its template, so it can be rendered again.
        preview(owner.session(), projectId, releaseId, noteId)
                .andExpect(jsonPath("$.corrects").value(2))
                .andExpect(jsonPath("$.changed").value(false));
    }

    @Test
    void correctsANoteAPersonWroteOrARetiredAudiencesNoteByHandOnly() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);

        // A note edited before publication never follows its template again.
        UUID edited = approvedReleaseWithChange(owner, projectId, "1.4.0");
        UUID editedNote = noteId(owner.session(), projectId, edited, "operator");
        mockMvc.perform(put("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}", projectId, edited, editedNote)
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"# Release 1.4.0\\n\\nWritten by a person.\\n\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/publish", projectId, edited)
                        .session(owner.session()).with(csrf()))
                .andExpect(status().isOk());
        preview(owner.session(), projectId, edited, editedNote)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_note_manual"));

        // Nor does a note whose latest revision was written by hand.
        UUID releaseId = publishedRelease(owner, projectId, "1.5.0");
        UUID noteId = noteId(owner.session(), projectId, releaseId, "operator");
        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/revisions",
                        projectId, releaseId, noteId)
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"corrects\":1,\"content\":\"# Release 1.5.0\\n\\nBy hand.\\n\",\"reason\":\"Wording.\"}"))
                .andExpect(status().isCreated());
        preview(owner.session(), projectId, releaseId, noteId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_note_manual"));
        saveRendered(owner.session(), projectId, releaseId, noteId, 2, "0".repeat(64))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_note_manual"));

        // A retired audience is never rendered again, and says so before anything else.
        UUID supportNote = noteId(owner.session(), projectId, releaseId, "support");
        mockMvc.perform(post("/api/audiences/{audienceId}/retire", audienceId(owner, "support"))
                        .session(owner.session()).with(csrf()))
                .andExpect(status().isOk());
        preview(owner.session(), projectId, releaseId, supportNote)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("audience_retired"))
                .andExpect(jsonPath("$.detail", containsString("retired")));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM release_note_revisions", Integer.class)).isOne();
    }

    @Test
    void offersRenderingAgainOnlyForAPublishedNoteAndRefusesAnyOtherOrigin() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID approvedId = approvedReleaseWithChange(owner, projectId, "1.5.0");
        UUID noteId = noteId(owner.session(), projectId, releaseId, "operator");

        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/correction-preview",
                        projectId, releaseId, noteId).session(owner.session()).param("origin", "TRANSLATED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("release_note_correction_origin_invalid"));
        correctWith(owner.session(), projectId, releaseId, noteId,
                "{\"origin\":\"TRANSLATED\",\"corrects\":1,\"previewHash\":\"x\",\"reason\":\"Glossary.\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        correctWith(owner.session(), projectId, releaseId, noteId,
                "{\"origin\":\"RENDERED\",\"corrects\":1,\"reason\":\"No preview.\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        preview(owner.session(), projectId, approvedId, noteId(owner.session(), projectId, approvedId, "operator"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_status_conflict"));
        Owner other = registerAndLogin("other@example.com", "Lan Pham");
        preview(other.session(), projectId, releaseId, noteId).andExpect(status().isNotFound());
    }

    private void review(Owner owner, UUID projectId, UUID releaseId, String category) throws Exception {
        UUID changeId = jdbcTemplate.queryForObject(
                "SELECT change_id FROM release_changes WHERE release_id = ?", UUID.class, releaseId);
        mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/review", projectId, changeId)
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"%s\",\"breaking\":false}".formatted(category)))
                .andExpect(status().isOk());
    }

    private ResultActions preview(MockHttpSession session, UUID projectId, UUID releaseId, UUID noteId) throws Exception {
        return mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/correction-preview",
                projectId, releaseId, noteId).session(session).param("origin", "RENDERED"));
    }

    private String previewHash(MockHttpSession session, UUID projectId, UUID releaseId, UUID noteId) throws Exception {
        return JsonPath.read(preview(session, projectId, releaseId, noteId)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.previewHash");
    }

    private ResultActions saveRendered(MockHttpSession session, UUID projectId, UUID releaseId, UUID noteId,
                                       int corrects, String previewHash) throws Exception {
        return correctWith(session, projectId, releaseId, noteId,
                "{\"origin\":\"RENDERED\",\"corrects\":%d,\"previewHash\":\"%s\",\"reason\":\"Rendered again.\"}"
                        .formatted(corrects, previewHash));
    }

    private ResultActions correctWith(MockHttpSession session, UUID projectId, UUID releaseId, UUID noteId, String body)
            throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/revisions",
                        projectId, releaseId, noteId)
                .session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private UUID noteId(MockHttpSession session, UUID projectId, UUID releaseId, String audienceCode) throws Exception {
        String body = mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/notes", projectId, releaseId)
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body,
                "$[?(@.audienceCode == '%s' && @.language == 'en')].id".formatted(audienceCode));
        return UUID.fromString(ids.getFirst());
    }

    private String noteContent(MockHttpSession session, UUID projectId, UUID releaseId, UUID noteId) throws Exception {
        String body = mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/notes", projectId, releaseId)
                        .session(session))
                .andReturn().getResponse().getContentAsString();
        List<String> contents = JsonPath.read(body, "$[?(@.id == '%s')].content".formatted(noteId));
        return contents.getFirst();
    }
}
