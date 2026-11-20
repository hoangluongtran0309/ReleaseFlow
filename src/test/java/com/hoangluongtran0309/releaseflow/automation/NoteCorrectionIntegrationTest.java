package com.hoangluongtran0309.releaseflow.automation;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0046: an administrator corrects a published note by hand, as its next revision, with a
 * reason; the note as published stays revision 1, and saving delivers nothing.
 */
class NoteCorrectionIntegrationTest extends AutomationIntegrationTestBase {

    private static final String CORRECTED = "# Release 1.4.0\n\nRestore <backup-id> first.\n";

    // A removal is recorded against both members; it goes before the base clears the members.
    @AfterEach
    void clearMembershipChanges() {
        jdbcTemplate.update("DELETE FROM membership_changes");
    }

    @Test
    void correctsAPublishedNoteByHandAsItsNextRevisionAndDeliversNothing() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID noteId = noteId(owner.session(), projectId, releaseId, "operator");
        String published = noteContent(owner.session(), projectId, releaseId, noteId);
        long runs = runCount();

        correct(owner.session(), projectId, releaseId, noteId, 1, CORRECTED, "Placeholders in the right case.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.origin").value("MANUAL"))
                .andExpect(jsonPath("$.content").value(CORRECTED))
                .andExpect(jsonPath("$.reason").value("Placeholders in the right case."))
                .andExpect(jsonPath("$.authorName").value("Mai Tran"))
                .andExpect(jsonPath("$.glossaryStatus").value("NONE"))
                .andExpect(jsonPath("$.createdAt", notNullValue()));

        assertThat(noteContent(owner.session(), projectId, releaseId, noteId)).isEqualTo(CORRECTED);
        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/revisions",
                        projectId, releaseId, noteId).session(owner.session()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value(published))
                .andExpect(jsonPath("$[1].content").value(CORRECTED));
        // The published row itself never changes, and nothing went anywhere.
        assertThat(jdbcTemplate.queryForObject("SELECT content FROM release_audience_notes WHERE id = ?", String.class,
                noteId)).isEqualTo(published);
        assertThat(runCount()).isEqualTo(runs);

        // A second correction names revision 2.
        correct(owner.session(), projectId, releaseId, noteId, 2, CORRECTED + "\nAnd verify it.\n", "One more step.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revision").value(3));
    }

    @Test
    void refusesACorrectionOfAnOlderRevisionOneThatChangesNothingAndOneByAMember() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID approvedId = approvedReleaseWithChange(owner, projectId, "1.5.0");
        UUID noteId = noteId(owner.session(), projectId, releaseId, "operator");
        String published = noteContent(owner.session(), projectId, releaseId, noteId);

        correct(owner.session(), projectId, releaseId, noteId, 1, published, "Nothing.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_note_correction_unchanged"));
        correct(owner.session(), projectId, releaseId, noteId, 1, CORRECTED, "Placeholders.").andExpect(status().isCreated());
        // Somebody corrected revision 1 meanwhile, so it is no longer the one to correct.
        correct(owner.session(), projectId, releaseId, noteId, 1, CORRECTED + "\nMore.\n", "Again.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_note_revision_stale"))
                .andExpect(jsonPath("$.detail", containsString("revision 2 is now the latest")));
        correct(owner.session(), projectId, releaseId, noteId, 2, CORRECTED.replace("\n", "\r\n"), "Same text.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_note_correction_unchanged"));

        // A reason is required and short, and only an administrator corrects.
        correct(owner.session(), projectId, releaseId, noteId, 2, CORRECTED + "x\n", " ").andExpect(status().isBadRequest());
        correct(owner.session(), projectId, releaseId, noteId, 2, CORRECTED + "x\n", "x".repeat(501))
                .andExpect(status().isBadRequest());
        MockHttpSession member = member(owner.organizationId(), "member@example.com");
        correct(member, projectId, releaseId, noteId, 2, CORRECTED + "x\n", "Mine.").andExpect(status().isForbidden());

        // An approved note is edited, not corrected; another Organization finds nothing.
        UUID approvedNote = noteId(owner.session(), projectId, approvedId, "operator");
        correct(owner.session(), projectId, approvedId, approvedNote, 1, CORRECTED, "Too early.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_status_conflict"));
        Owner other = registerAndLogin("other@example.com", "Lan Pham");
        correct(other.session(), projectId, releaseId, noteId, 2, CORRECTED + "x\n", "Not ours.")
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM release_note_revisions", Integer.class)).isOne();
    }

    @Test
    void keepsTheNameARemovedAdministratorHadOnTheRevisionTheyWrote() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID noteId = noteId(owner.session(), projectId, releaseId, "operator");
        UUID adminId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, 'lan@example.com', ?, 'Lan Pham', 'ADMIN', now())
                        """,
                adminId, owner.organizationId(), passwordEncoder.encode("admin-password"));
        MockHttpSession admin = login("lan@example.com", "admin-password");
        correct(admin, projectId, releaseId, noteId, 1, CORRECTED, "Placeholders.").andExpect(status().isCreated());

        mockMvc.perform(delete("/api/members/{memberId}", adminId).session(owner.session()).with(csrf()))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/revisions/2",
                        projectId, releaseId, noteId).session(owner.session()))
                .andExpect(jsonPath("$.authorName").value("Lan Pham"));
        // Their session ends on its next request, so they correct nothing more.
        correct(admin, projectId, releaseId, noteId, 2, CORRECTED + "x\n", "Again.")
                .andExpect(status().is4xxClientError());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM release_note_revisions", Integer.class)).isOne();
    }

    @Test
    void correctsARetiredAudiencesPublishedNoteByHand() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID noteId = noteId(owner.session(), projectId, releaseId, "support");
        mockMvc.perform(post("/api/audiences/{audienceId}/retire", audienceId(owner, "support"))
                        .session(owner.session()).with(csrf()))
                .andExpect(status().isOk());

        correct(owner.session(), projectId, releaseId, noteId, 1, CORRECTED, "Placeholders.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revision").value(2));
    }

    @Test
    void downloadsTheRevisionANumberNamesAndRefusesAnythingElse() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID noteId = noteId(owner.session(), projectId, releaseId, "operator");
        String published = noteContent(owner.session(), projectId, releaseId, noteId);
        correct(owner.session(), projectId, releaseId, noteId, 1, CORRECTED, "Placeholders.").andExpect(status().isCreated());

        download(owner, projectId, releaseId, noteId, null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("1.4.0-operator-en.md")))
                .andExpect(content().string(CORRECTED));
        download(owner, projectId, releaseId, noteId, "1")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("1.4.0-operator-en.r1.md")))
                .andExpect(content().string(published));
        download(owner, projectId, releaseId, noteId, "2")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("1.4.0-operator-en.r2.md")))
                .andExpect(content().string(CORRECTED));
        // A revision the note does not have is not found.
        download(owner, projectId, releaseId, noteId, "3")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("release_note_not_found"));
        // Anything but a positive whole number names no revision at all.
        for (String invalid : List.of("0", "-1", "1.5", "two", "", " 2", "02", "9999999999")) {
            download(owner, projectId, releaseId, noteId, invalid)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("release_note_revision_invalid"));
        }
    }

    private ResultActions correct(MockHttpSession session, UUID projectId, UUID releaseId, UUID noteId, int corrects,
                                  String content, String reason) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/revisions",
                        projectId, releaseId, noteId)
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(new Correction(corrects, content, reason))));
    }

    private ResultActions download(Owner owner, UUID projectId, UUID releaseId, UUID noteId, String revision)
            throws Exception {
        var request = get("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/download", projectId, releaseId,
                noteId).session(owner.session());
        return mockMvc.perform(revision == null ? request : request.param("revision", revision));
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

    private long runCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM automation_runs", Long.class);
    }

    private record Correction(int corrects, String content, String reason) {
    }

    private static final tools.jackson.databind.ObjectMapper JSON = new tools.jackson.databind.ObjectMapper();
}
