package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.configuration.UiNotice;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * ADR-0046 on the pages: an administrator corrects a published note after reading the
 * difference, and the release page lists each note's revisions and what each destination
 * last received.
 */
class NoteCorrectionPageIntegrationTest extends AutomationIntegrationTestBase {

    private static final Pattern PREVIEWED = Pattern.compile("name=\"previewed\" value=\"([0-9a-f]{64})\"");

    @Test
    void correctsANoteOnlyAfterItsDifferenceWasShown() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID noteId = noteId(owner, projectId, releaseId, "operator");
        String releasePath = "/projects/" + projectId + "/releases/" + releaseId;
        String correctPath = releasePath + "/notes/" + noteId + "/correct";
        String published = noteContent(owner, projectId, releaseId, noteId);
        String corrected = published + "\nRestore <backup-id> first.\n";

        mockMvc.perform(get(releasePath).session(owner.session()))
                .andExpect(view().name("release-note"))
                .andExpect(content().string(containsString("Revision 1")))
                .andExpect(content().string(containsString("As published")))
                .andExpect(content().string(containsString("Correct this note")))
                .andExpect(content().string(containsString("No automation has delivered this note.")));
        mockMvc.perform(get(correctPath).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(view().name("note-correction"))
                .andExpect(content().string(containsString("Correct the Operator note in en")))
                .andExpect(content().string(containsString("name=\"corrects\" value=\"1\"")))
                .andExpect(content().string(containsString("Show the difference")))
                .andExpect(content().string(not(containsString("Save as revision 2"))));

        MvcResult preview = mockMvc.perform(post(correctPath).session(owner.session()).with(csrf())
                        .param("corrects", "1").param("content", corrected).param("reason", "Placeholders.")
                        .param("intent", "preview"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Difference from revision 1")))
                .andExpect(content().string(containsString("Restore &lt;backup-id&gt; first.")))
                .andExpect(content().string(containsString("Save as revision 2")))
                .andReturn();
        String previewed = previewed(preview);

        // Text other than what was shown is shown again before anything is saved.
        mockMvc.perform(post(correctPath).session(owner.session()).with(csrf())
                        .param("corrects", "1").param("content", corrected + "More.\n").param("reason", "Placeholders.")
                        .param("intent", "save").param("previewed", previewed))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("The text changed after its difference was shown.")));
        assertThat(revisionCount()).isZero();

        // A missing reason is said under the field.
        mockMvc.perform(post(correctPath).session(owner.session()).with(csrf())
                        .param("corrects", "1").param("content", corrected).param("reason", " ")
                        .param("intent", "save").param("previewed", previewed))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Say why the note is corrected.")));

        mockMvc.perform(post(correctPath).session(owner.session()).with(csrf())
                        .param("corrects", "1").param("content", corrected.replace("\n", "\r\n"))
                        .param("reason", "Placeholders.").param("intent", "save").param("previewed", previewed))
                .andExpect(redirectedUrl(releasePath))
                .andExpect(flash().attribute(UiNotice.FLASH_ATTRIBUTE,
                        UiNotice.success("ui.noteCorrection.saved", 2, "Operator", "en")));
        assertThat(revisionCount()).isOne();

        mockMvc.perform(get(releasePath).session(owner.session()))
                .andExpect(content().string(containsString("Revision 2")))
                .andExpect(content().string(containsString("Corrected</span>")))
                .andExpect(content().string(containsString("By hand")))
                .andExpect(content().string(containsString("Placeholders.")))
                .andExpect(content().string(containsString("Difference from revision 1")))
                .andExpect(content().string(containsString("revision=1")))
                .andExpect(content().string(containsString("revision=2")));

        // Revision 1 is no longer the one to correct: the page says so and compares with revision 2.
        MvcResult stalePreview = mockMvc.perform(post(correctPath).session(owner.session()).with(csrf())
                        .param("corrects", "1").param("content", corrected + "Again.\n").param("reason", "Again.")
                        .param("intent", "preview"))
                .andReturn();
        mockMvc.perform(post(correctPath).session(owner.session()).with(csrf())
                        .param("corrects", "1").param("content", corrected + "Again.\n").param("reason", "Again.")
                        .param("intent", "save").param("previewed", previewed(stalePreview)))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("revision 2 is now the latest")))
                .andExpect(content().string(containsString("name=\"corrects\" value=\"2\"")))
                .andExpect(content().string(containsString("Difference from revision 2")));
        assertThat(revisionCount()).isOne();
    }

    @Test
    void letsOnlyAnAdministratorCorrectAndOnlyAPublishedNote() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID approvedId = approvedReleaseWithChange(owner, projectId, "1.5.0");
        UUID noteId = noteId(owner, projectId, releaseId, "operator");
        UUID approvedNote = noteId(owner, projectId, approvedId, "operator");
        String releasePath = "/projects/" + projectId + "/releases/" + releaseId;
        MockHttpSession member = member(owner.organizationId(), "member@example.com");

        mockMvc.perform(get(releasePath).session(member))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Revisions")))
                .andExpect(content().string(not(containsString("Correct this note"))));
        mockMvc.perform(get(releasePath + "/notes/" + noteId + "/correct").session(member))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(releasePath + "/notes/" + noteId + "/correct").session(member).with(csrf())
                        .param("corrects", "1").param("content", "# x").param("reason", "Mine.").param("intent", "save"))
                .andExpect(status().isForbidden());

        String approvedPath = "/projects/" + projectId + "/releases/" + approvedId;
        mockMvc.perform(get(approvedPath + "/notes/" + approvedNote + "/correct").session(owner.session()))
                .andExpect(redirectedUrl(approvedPath))
                .andExpect(flash().attribute(UiNotice.FLASH_ATTRIBUTE,
                        UiNotice.error("error.release_status_conflict.revisionsAfterPublication")));
        assertThat(revisionCount()).isZero();
    }

    @Test
    void showsTheRevisionEachDestinationLastReceived() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID operator = audienceId(owner, "operator");
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID ruleId = createdRuleId(createRule(owner, slackRule("Tell operators", "MANUAL", projectId, operator, "en"))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        mockMvc.perform(post("/api/automation/rules/{ruleId}/execute", ruleId)
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseId\":\"%s\",\"requestId\":\"%s\"}".formatted(releaseId, UUID.randomUUID())))
                .andExpect(status().isAccepted());
        deliverEverything();
        String releasePath = "/projects/" + projectId + "/releases/" + releaseId;

        mockMvc.perform(get(releasePath).session(owner.session()))
                .andExpect(content().string(containsString("Tell operators")))
                .andExpect(content().string(not(containsString(">Behind<"))));

        UUID noteId = noteId(owner, projectId, releaseId, "operator");
        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/revisions",
                        projectId, releaseId, noteId)
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"corrects\":1,\"content\":\"# Corrected\",\"reason\":\"Placeholders.\"}"))
                .andExpect(status().isCreated());

        // Slack still holds revision 1: the note says that destination is behind.
        String page = mockMvc.perform(get(releasePath).session(owner.session()))
                .andReturn().getResponse().getContentAsString();
        String operatorTab = page.substring(page.indexOf("id=\"note-operator-en\""));
        operatorTab = operatorTab.substring(0, operatorTab.indexOf("class=\"tab\"") > 0
                ? operatorTab.indexOf("class=\"tab\"") : operatorTab.length());
        assertThat(operatorTab).contains("Tell operators").contains(">Behind<").contains("Revision 2");
    }

    @Test
    void neverPutsTheMarkupOfACorrectionOnAPage() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID noteId = noteId(owner, projectId, releaseId, "operator");
        String releasePath = "/projects/" + projectId + "/releases/" + releaseId;
        String hostile = "# Release 1.4.0\n\n<script>alert(1)</script>\n\n[open](javascript:alert(2))\n";
        String reason = "<b>bold</b> <img src=x onerror=alert(3)>";

        // The difference shows the hostile lines as text, before anything is saved.
        mockMvc.perform(post(releasePath + "/notes/" + noteId + "/correct").session(owner.session()).with(csrf())
                        .param("corrects", "1").param("content", hostile).param("reason", reason)
                        .param("intent", "preview"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<img src=x"))));

        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/revisions",
                        projectId, releaseId, noteId)
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(
                                Map.of("corrects", 1, "content", hostile, "reason", reason))))
                .andExpect(status().isCreated());

        // The note renders through MarkdownHtml; its revisions, reason, and difference are text.
        mockMvc.perform(get(releasePath).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(not(containsString("href=\"javascript:"))))
                .andExpect(content().string(not(containsString("<b>bold</b>"))))
                .andExpect(content().string(not(containsString("<img src=x"))))
                .andExpect(content().string(containsString("&lt;b&gt;bold&lt;/b&gt;")))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
    }

    @Test
    void rendersANoteAgainOnThePageAndRecordsAnyEditAsWrittenByHand() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID releaseId = publishedRelease(owner, projectId, "1.4.0");
        UUID noteId = noteId(owner, projectId, releaseId, "operator");
        String correctPath = "/projects/" + projectId + "/releases/" + releaseId + "/notes/" + noteId + "/correct";
        UUID changeId = jdbcTemplate.queryForObject(
                "SELECT change_id FROM release_changes WHERE release_id = ?", UUID.class, releaseId);
        mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/review", projectId, changeId)
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"fix\",\"breaking\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(correctPath).session(owner.session()))
                .andExpect(content().string(containsString("Render again")));
        MvcResult rendered = mockMvc.perform(post(correctPath).session(owner.session()).with(csrf())
                        .param("corrects", "1").param("content", "").param("reason", "")
                        .param("intent", "render"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("rendered again by today")))
                .andExpect(content().string(containsString("Difference from revision 1")))
                .andExpect(content().string(containsString("Save as revision 2")))
                .andReturn();
        String page = rendered.getResponse().getContentAsString();
        String renderedHash = hidden(page, "rendered");
        String renderedForm = hidden(page, "renderedForm");
        String text = textarea(page);

        // Saved as rendering gave it, it is a rendered revision.
        mockMvc.perform(post(correctPath).session(owner.session()).with(csrf())
                        .param("corrects", "1").param("content", text).param("reason", "Fixes are fixes.")
                        .param("intent", "save").param("previewed", previewed(rendered))
                        .param("rendered", renderedHash).param("renderedForm", renderedForm))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbcTemplate.queryForObject("SELECT origin FROM release_note_revisions WHERE revision = 2",
                String.class)).isEqualTo("RENDERED");

        // Edited after rendering, the same text is a person's: it is recorded as written by hand.
        mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/review", projectId, changeId)
                        .session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"documentation\",\"breaking\":false}"))
                .andExpect(status().isOk());
        MvcResult again = mockMvc.perform(post(correctPath).session(owner.session()).with(csrf())
                        .param("corrects", "2").param("content", "").param("reason", "").param("intent", "render"))
                .andExpect(status().isOk())
                .andReturn();
        String edited = textarea(again.getResponse().getContentAsString()) + "\nOne line of my own.\n";
        MvcResult editedPreview = mockMvc.perform(post(correctPath).session(owner.session()).with(csrf())
                        .param("corrects", "2").param("content", edited).param("reason", "Mine.")
                        .param("intent", "preview")
                        .param("rendered", hidden(again.getResponse().getContentAsString(), "rendered"))
                        .param("renderedForm", hidden(again.getResponse().getContentAsString(), "renderedForm")))
                .andReturn();
        mockMvc.perform(post(correctPath).session(owner.session()).with(csrf())
                        .param("corrects", "2").param("content", edited).param("reason", "Mine.")
                        .param("intent", "save").param("previewed", previewed(editedPreview))
                        .param("rendered", hidden(again.getResponse().getContentAsString(), "rendered"))
                        .param("renderedForm", hidden(again.getResponse().getContentAsString(), "renderedForm")))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbcTemplate.queryForObject("SELECT origin FROM release_note_revisions WHERE revision = 3",
                String.class)).isEqualTo("MANUAL");

        // Now a person wrote it: the page offers no rendering, and says why.
        mockMvc.perform(get(correctPath).session(owner.session()))
                .andExpect(content().string(not(containsString(">Render again<"))))
                .andExpect(content().string(containsString("A person wrote this note, so it is corrected by hand only.")));
    }

    private static String hidden(String page, String name) {
        Matcher matcher = Pattern.compile("name=\"" + name + "\" value=\"([^\"]*)\"").matcher(page);
        assertThat(matcher.find()).as("hidden " + name).isTrue();
        return matcher.group(1);
    }

    private static String textarea(String page) {
        Matcher matcher = Pattern.compile("(?s)<textarea[^>]*name=\"content\"[^>]*>(.*?)</textarea>").matcher(page);
        assertThat(matcher.find()).as("the content field").isTrue();
        return org.springframework.web.util.HtmlUtils.htmlUnescape(matcher.group(1));
    }

    private static String previewed(MvcResult result) throws Exception {
        Matcher matcher = PREVIEWED.matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private long revisionCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM release_note_revisions", Long.class);
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
}
