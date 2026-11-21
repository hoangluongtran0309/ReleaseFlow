package com.hoangluongtran0309.releaseflow.changelog;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import tools.jackson.databind.json.JsonMapper;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A corrected public entry (ADR-0046, part 4): its address shows the latest revision,
 * an earlier one keeps an address nothing can change, and the feed announces each
 * correction beside the item it already had. The revisions are made by the rows a
 * correction run leaves, since that run is the next part.
 */
class PublicChangelogRevisionIntegrationTest extends PublicChangelogTestBase {

    // Later than the entry a rule publishes while the test runs, so the feed's order is the test's.
    private static final Instant PUBLISHED = Instant.parse("2031-11-02T08:00:00Z");
    // Late in the day in UTC, and already the next day in Vietnam: the line keeps UTC's.
    private static final Instant SECOND = Instant.parse("2031-11-15T23:30:00Z");
    private static final Instant THIRD = Instant.parse("2031-11-18T09:00:00Z");
    private static final String ORIGINAL = "# Bản phát hành 1.2.0\n\n- Dùng `dbvault-CLI` để khôi phục.\n";
    private static final String REVISION_2 = "# Bản phát hành 1.2.0\n\n- Dùng `dbvault-cli` để khôi phục.\n";
    private static final String REVISION_3 = "# Bản phát hành 1.2.0\n\n- Dùng `dbvault-cli` để khôi phục endpoint.\n";
    private static final String CORRECTOR = "Linh Pham";
    private static final String CORRECTOR_EMAIL = "linh.pham@example.com";

    @Test
    void showsTheLatestRevisionUnderItsCorrectionLineInTheEntrysLanguage() throws Exception {
        Fixture fixture = fixture();
        correct(fixture.vietnamese(), REVISION_2, "Trả `dbvault-cli` về đúng chữ.", SECOND, 2,
                fixture.corrector(), CORRECTOR);

        MockHttpServletResponse english = page(entry(fixture, fixture.vietnamese()));
        assertThat(english.getHeader("Cache-Control")).isEqualTo("max-age=300, public");
        assertThat(english.getHeader("ETag")).isEqualTo("\"" + fixture.vietnamese() + "-2-en\"");
        assertThat(english.getHeader("Vary")).isEqualTo("Accept-Language, Cookie");
        assertThat(english.getHeader("X-Robots-Tag")).isNull();
        String body = english.getContentAsString(StandardCharsets.UTF_8);
        // The line is the note's, in the note's language; the page around it is the reader's.
        assertThat(body).contains("<p class=\"correction-line mt-8 max-w-3xl\" lang=\"vi\">"
                + "Đã sửa vào ngày 15 tháng 11 năm 2031: Trả `dbvault-cli` về đúng chữ.</p>");
        assertThat(article(body)).contains("<code>dbvault-cli</code>").doesNotContain("dbvault-CLI");
        assertThat(body).contains("Earlier revisions",
                "href=\"http://localhost:8080/changelog/" + fixture.slug() + "/releases/" + fixture.vietnamese()
                        + "/revisions/1\">Revision 1, as published on 2 Nov 2031</a>");
        assertThat(body).doesNotContain(CORRECTOR, CORRECTOR_EMAIL, "robots");

        String vietnameseReader = page(entry(fixture, fixture.vietnamese()).header("Accept-Language", "vi"))
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(vietnameseReader).contains("Các bản sửa đổi trước", "Đã sửa vào ngày 15 tháng 11 năm 2031");
    }

    @Test
    void validatesEachPageByItsRevisionAndItsReadersLanguage() throws Exception {
        Fixture fixture = fixture();
        UUID entryId = fixture.vietnamese();
        String english = "\"" + entryId + "-1-en\"";
        String vietnamese = "\"" + entryId + "-1-vi\"";

        // An entry nobody corrected: the same note, two pages, two validators.
        assertThat(page(entry(fixture, entryId)).getHeader("ETag")).isEqualTo(english);
        assertThat(page(entry(fixture, entryId).header("Accept-Language", "vi")).getHeader("ETag")).isEqualTo(vietnamese);
        assertThat(statusOf(entry(fixture, entryId).header("If-None-Match", english))).isEqualTo(304);
        assertThat(statusOf(entry(fixture, entryId).header("If-None-Match", vietnamese))).isEqualTo(200);
        assertThat(statusOf(entry(fixture, entryId).header("Accept-Language", "vi").header("If-None-Match", vietnamese)))
                .isEqualTo(304);
        assertThat(statusOf(entry(fixture, entryId).header("Accept-Language", "vi").header("If-None-Match", english)))
                .isEqualTo(200);
        // A reader who chose a language keeps it in a cookie, which outranks the header.
        assertThat(page(entry(fixture, entryId).cookie(new Cookie("releaseflow_lang", "vi")))
                .getHeader("ETag")).isEqualTo(vietnamese);
        // A ?lang= changes only the page it is on, and its validator with it; nothing is kept.
        MockHttpServletResponse chosen = page(entry(fixture, entryId).param("lang", "vi"));
        assertThat(chosen.getHeader("ETag")).isEqualTo(vietnamese);
        assertThat(chosen.getHeader("Set-Cookie")).isNull();

        // A correction makes every copy cached before it stale.
        correct(entryId, REVISION_2, "Sửa chữ.", SECOND, 2, fixture.corrector(), CORRECTOR);
        assertThat(statusOf(entry(fixture, entryId).header("If-None-Match", english))).isEqualTo(200);
        assertThat(page(entry(fixture, entryId)).getHeader("ETag")).isEqualTo("\"" + entryId + "-2-en\"");
        assertThat(statusOf(entry(fixture, entryId).header("If-None-Match", "\"" + entryId + "-2-en\""))).isEqualTo(304);

        // An earlier revision is validated the same way.
        MockHttpServletResponse revision = page(revision(fixture, entryId, "1").header("Accept-Language", "vi"));
        assertThat(revision.getHeader("ETag")).isEqualTo(vietnamese);
        MockHttpServletResponse notModified = mockMvc.perform(revision(fixture, entryId, "1")
                        .header("Accept-Language", "vi")
                        .header("If-None-Match", vietnamese))
                .andReturn().getResponse();
        assertThat(notModified.getStatus()).isEqualTo(304);
        assertThat(notModified.getHeader("X-Robots-Tag")).isEqualTo("noindex");
        assertThat(notModified.getHeader("Cache-Control")).isEqualTo("max-age=86400, public, immutable");
        assertThat(statusOf(revision(fixture, entryId, "1").header("If-None-Match", vietnamese))).isEqualTo(200);
    }

    @Test
    void servesAnEarlierRevisionThatNeverChangesAndIsNotIndexed() throws Exception {
        Fixture fixture = fixture();
        UUID entryId = fixture.vietnamese();
        String original = article(page(entry(fixture, entryId)).getContentAsString(StandardCharsets.UTF_8));
        correct(entryId, REVISION_2, "Trả `dbvault-cli` về đúng chữ.", SECOND, 2, fixture.corrector(), CORRECTOR);
        correct(entryId, REVISION_3, "Thêm \"endpoint\".", THIRD, 3, fixture.corrector(), CORRECTOR);

        MockHttpServletResponse first = page(revision(fixture, entryId, "1"));
        assertThat(first.getHeader("Cache-Control")).isEqualTo("max-age=86400, public, immutable");
        assertThat(first.getHeader("X-Robots-Tag")).isEqualTo("noindex");
        assertThat(first.getHeader("Vary")).isEqualTo("Accept-Language, Cookie");
        String firstBody = first.getContentAsString(StandardCharsets.UTF_8);
        assertThat(firstBody).contains("<meta name=\"robots\" content=\"noindex\">",
                "This is revision 1 of this note. Revision 2 replaced it on 15 Nov 2031.",
                "<link rel=\"canonical\" href=\"http://localhost:8080/changelog/" + fixture.slug() + "/releases/"
                        + entryId + "/revisions/1\">");
        // What was first published, as it was published.
        assertThat(article(firstBody)).isEqualTo(original);
        assertThat(firstBody).doesNotContain("correction-line", "revision 3", "Revision 3", CORRECTOR, CORRECTOR_EMAIL);

        String second = page(revision(fixture, entryId, "2")).getContentAsString(StandardCharsets.UTF_8);
        assertThat(second).contains("This is revision 2 of this note. Revision 3 replaced it on 18 Nov 2031.",
                "lang=\"vi\">Đã sửa vào ngày 15 tháng 11 năm 2031: Trả `dbvault-cli` về đúng chữ.</p>");
        assertThat(article(second)).contains("<code>dbvault-cli</code> để khôi phục.");

        String latest = page(entry(fixture, entryId)).getContentAsString(StandardCharsets.UTF_8);
        assertThat(latest).contains("Revision 2, corrected on 15 Nov 2031", "Đã sửa vào ngày 18 tháng 11 năm 2031: Thêm &quot;endpoint&quot;.");
        assertThat(latest).doesNotContain("/revisions/3\"");
        assertThat(article(latest)).contains("khôi phục endpoint.");
    }

    @Test
    void answersEveryOtherNumberWithAnUncachedNotFound() throws Exception {
        Fixture fixture = fixture();
        UUID entryId = fixture.vietnamese();
        correct(entryId, REVISION_2, "Sửa chữ.", SECOND, 2, fixture.corrector(), CORRECTOR);

        for (String number : List.of("2", "3", "0", "-1", "01", "abc", "1.0", "2147483647", "2147483648",
                "99999999999999999999")) {
            MockHttpServletResponse response = mockMvc.perform(revision(fixture, entryId, number))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).as(number).isEqualTo(404);
            assertThat(response.getHeader("Cache-Control")).as(number).doesNotContain("public", "immutable");
            assertThat(response.getHeader("ETag")).as(number).isNull();
        }
        // Another Organization's address, or an entry it does not hold, is no answer either.
        Owner other = registerAndLogin("other@example.com", "Other Owner");
        assertThat(statusOf(get("/changelog/{slug}/releases/{id}/revisions/1", slug(other), entryId))).isEqualTo(404);
        assertThat(statusOf(get("/changelog/{slug}/releases/{id}/revisions/1", fixture.slug(), UUID.randomUUID())))
                .isEqualTo(404);
        // An entry nobody corrected has no earlier revision.
        assertThat(statusOf(revision(fixture, fixture.english(), "1"))).isEqualTo(404);

        // The latest has no number of its own until a correction replaces it.
        correct(entryId, REVISION_3, "Thêm endpoint.", THIRD, 3, fixture.corrector(), CORRECTOR);
        assertThat(statusOf(revision(fixture, entryId, "2"))).isEqualTo(200);
        assertThat(statusOf(revision(fixture, entryId, "3"))).isEqualTo(404);
    }

    @Test
    void addsAnItemForEachCorrectionAndLeavesThePublishedOneAsItWas() throws Exception {
        Fixture fixture = fixture();
        UUID entryId = fixture.vietnamese();
        String before = feed(fixture);
        String publishedItem = item(before, entryUrl(fixture, entryId));
        String englishItem = item(before, entryUrl(fixture, fixture.english()));

        correct(entryId, REVISION_2, "Trả `dbvault-cli` về đúng chữ.", SECOND, 2, fixture.corrector(), CORRECTOR);
        correct(entryId, REVISION_3, "Thêm endpoint.", THIRD, 3, fixture.corrector(), CORRECTOR);
        String after = feed(fixture);

        assertThat(item(after, entryUrl(fixture, entryId))).isEqualTo(publishedItem);
        assertThat(item(after, entryUrl(fixture, fixture.english()))).isEqualTo(englishItem);
        assertThat(after).doesNotContain(CORRECTOR, CORRECTOR_EMAIL);

        List<Element> items = items(after);
        assertThat(items).hasSize(4);
        // Newest first: both corrections, then the entries as they were published.
        assertThat(items.stream().map(item -> text(item, "guid")).toList()).containsExactly(
                entryUrl(fixture, entryId) + "#revision-3",
                entryUrl(fixture, entryId) + "#revision-2",
                entryUrl(fixture, entryId),
                entryUrl(fixture, fixture.english()));
        Element third = items.getFirst();
        assertThat(((Element) third.getElementsByTagName("guid").item(0)).getAttribute("isPermaLink")).isEqualTo("false");
        assertThat(text(third, "title")).isEqualTo("ReleaseFlow 1.2.0 (corrected)");
        assertThat(text(third, "link")).isEqualTo(entryUrl(fixture, entryId));
        assertThat(text(third, "pubDate")).isEqualTo("Tue, 18 Nov 2031 09:00:00 GMT");
        assertThat(text(third, "description")).startsWith(
                "<p lang=\"vi\">Đã sửa vào ngày 18 tháng 11 năm 2031: Thêm endpoint.</p>\n<h1>Bản phát hành 1.2.0</h1>");
        assertThat(text(third, "description")).contains("khôi phục endpoint.");
        assertThat(text(items.get(1), "description")).contains("<code>dbvault-cli</code> để khôi phục.</li>");
    }

    @Test
    void escapesAPersonsReasonOnThePageAndExactlyOnceInTheFeed() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID entryId = publishedEntry(owner, projectId, "1.4.0");
        String reason = "<script>alert(1)</script> & \"R&D\"";
        // The note's own correction, made by hand, and the entry's revision copied from it.
        Map<String, Object> noteRevision = correctByHand(owner, projectId, "Fixed **wording**.", reason);
        correct(entryId, (String) noteRevision.get("content"), (String) noteRevision.get("reason"), SECOND,
                (Integer) noteRevision.get("revision"), owner.userId(), "Mai Tran");
        String slug = slug(owner);

        String page = page(get("/changelog/{slug}/releases/{id}", slug, entryId)).getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("Corrected on 15 November 2031: &lt;script&gt;alert(1)&lt;/script&gt; &amp; "
                + "&quot;R&amp;D&quot;</p>");
        assertThat(page).doesNotContain("<script>alert(1)", "Mai Tran");
        assertThat(article(page)).isEqualTo("<p>Fixed <strong>wording</strong>.</p>\n");

        String feed = page(get("/changelog/{slug}/rss.xml", slug)).getContentAsString(StandardCharsets.UTF_8);
        Element correction = items(feed).getFirst();
        String description = text(correction, "description");
        // A subscriber's reader decodes the XML once and reads HTML: the reason is text in it.
        assertThat(description).startsWith("<p lang=\"en\">Corrected on 15 November 2031: &lt;script&gt;alert(1)"
                + "&lt;/script&gt; &amp; &quot;R&amp;D&quot;</p>\n<p>Fixed <strong>wording</strong>.</p>");
        assertThat(description).doesNotContain("<script>", "&amp;lt;", "&amp;amp;");
        // In the document itself that HTML is encoded once more, as every description is.
        assertThat(feed).contains("&amp;lt;script&amp;gt;alert(1)&amp;lt;/script&amp;gt; &amp;amp; &amp;quot;R&amp;amp;D")
                .doesNotContain("&amp;amp;lt;", "&lt;script&gt;alert(1)");
        assertThat(feed).doesNotContain("Mai Tran");
    }

    @Test
    void marksACorrectedLanguageOnTheList() throws Exception {
        Fixture fixture = fixture();
        correct(fixture.vietnamese(), REVISION_2, "Sửa chữ.", SECOND, 2, fixture.corrector(), CORRECTOR);

        String list = page(get("/changelog/{slug}", fixture.slug())).getContentAsString(StandardCharsets.UTF_8);
        assertThat(list).contains(">Tiếng Việt</a><span class=\"badge badge-sm ms-1\">Corrected</span>");
        assertThat(list).contains(">English</a>\n");
        assertThat(page(get("/changelog/{slug}", fixture.slug()).header("Accept-Language", "vi"))
                .getContentAsString(StandardCharsets.UTF_8)).contains(">Đã sửa</span>");
    }

    /**
     * An English entry a rule published, and the same release's note in Vietnamese beside
     * it, each named, dated, and worded by the test. The member who corrects is not the
     * Organization's owner, whose address names the Organization on every page.
     */
    private Fixture fixture() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID english = publishedEntry(owner, projectId, "1.2.0");
        UUID vietnamese = UUID.randomUUID();
        copyEntry(english, vietnamese, 99, Map.of(
                "language", "vi",
                "content_snapshot", ORIGINAL,
                "published_at", at(PUBLISHED)));
        member(owner.organizationId(), CORRECTOR_EMAIL);
        UUID corrector = jdbcTemplate.queryForObject(
                "SELECT id FROM app_users WHERE email = ?", UUID.class, CORRECTOR_EMAIL);
        return new Fixture(slug(owner), english, vietnamese, corrector);
    }

    private record Fixture(String slug, UUID english, UUID vietnamese, UUID corrector) {
    }

    private Map<String, Object> correctByHand(Owner owner, UUID projectId, String content, String reason)
            throws Exception {
        UUID releaseId = jdbcTemplate.queryForObject(
                "SELECT id FROM releases WHERE organization_id = ? ORDER BY created_at DESC LIMIT 1",
                UUID.class, owner.organizationId());
        UUID noteId = jdbcTemplate.queryForObject("""
                SELECT note.id FROM release_audience_notes note
                JOIN audience_definitions audience ON audience.id = note.audience_id
                WHERE note.release_id = ? AND audience.code = 'end_user' AND note.language = 'en'
                """, UUID.class, releaseId);
        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/revisions",
                        projectId, releaseId, noteId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper().writeValueAsString(Map.of(
                                "corrects", 1, "origin", "MANUAL", "content", content, "reason", reason))))
                .andExpect(status().isCreated());
        return jdbcTemplate.queryForMap(
                "SELECT revision, content, reason FROM release_note_revisions WHERE note_id = ? ORDER BY revision DESC LIMIT 1",
                noteId);
    }

    private static JsonMapper objectMapper() {
        return JsonMapper.builder().build();
    }

    private MockHttpServletRequestBuilder entry(Fixture fixture, UUID entryId) {
        return get("/changelog/{slug}/releases/{id}", fixture.slug(), entryId);
    }

    private MockHttpServletRequestBuilder revision(Fixture fixture, UUID entryId, String number) {
        return get("/changelog/{slug}/releases/{id}/revisions/" + number, fixture.slug(), entryId);
    }

    private String entryUrl(Fixture fixture, UUID entryId) {
        return "http://localhost:8080/changelog/" + fixture.slug() + "/releases/" + entryId;
    }

    private MockHttpServletResponse page(MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return response;
    }

    private int statusOf(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    private String feed(Fixture fixture) throws Exception {
        return page(get("/changelog/{slug}/rss.xml", fixture.slug())).getContentAsString(StandardCharsets.UTF_8);
    }

    private static String article(String page) {
        Matcher article = Pattern.compile("<article class=\"release-note-body[^>]*>(.*?)</article>", Pattern.DOTALL)
                .matcher(page);
        assertThat(article.find()).isTrue();
        return article.group(1);
    }

    /** One item of the feed exactly as written, found by the permalink guid it was published with. */
    private static String item(String feed, String permalink) {
        Matcher item = Pattern.compile("<item>(?:(?!<item>).)*?<guid isPermaLink=\"true\">" + Pattern.quote(permalink)
                + "</guid>.*?</item>", Pattern.DOTALL).matcher(feed);
        assertThat(item.find()).as(permalink).isTrue();
        return item.group();
    }

    private static List<Element> items(String feed) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(feed.getBytes(StandardCharsets.UTF_8)));
        Element channel = (Element) document.getDocumentElement().getElementsByTagName("channel").item(0);
        NodeList children = channel.getChildNodes();
        List<Element> items = new ArrayList<>();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element element && element.getTagName().equals("item")) {
                items.add(element);
            }
        }
        return items;
    }

    private static String text(Element item, String name) {
        return item.getElementsByTagName(name).item(0).getTextContent();
    }
}
