package com.hoangluongtran0309.releaseflow.changelog;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * An entry nobody corrected is served exactly as it was before revisions existed. The
 * files under {@code changelog/as-today/} were captured from the code before ADR-0046's
 * part 4, from this same fixture, and are never regenerated from the code they test:
 * only the cache headers of an entry may differ from them.
 */
class PublicChangelogAsTodayIntegrationTest extends PublicChangelogTestBase {

    static final String SLUG = "as-today";
    static final Instant FIRST = Instant.parse("2031-01-01T00:00:00Z");
    static final String RICH_NOTE = """
            # Release 2.0.25

            ## ✨ New Features

            - **Export** a vault with `dbvault-cli export <backup-id>`.
            - A [link](https://example.com/docs) & an <b>inline</b> tag, "quoted".

            <script>alert('x')</script>
            """;
    static final String VIETNAMESE_NOTE = """
            # Bản phát hành 2.0.25

            - Sửa lỗi của `dbvault-cli` khi khôi phục `<backup-id>`.
            """;

    @Test
    void servesAnEntryNobodyCorrectedExactlyAsBefore() throws Exception {
        asToday(registerAndLogin("owner@example.com", "Mai Tran"));

        assertThat(body(get("/changelog/{slug}/releases/{id}", SLUG, entryId(49)))).isEqualTo(captured("entry-en.html"));
        assertThat(body(get("/changelog/{slug}/releases/{id}", SLUG, entryId(49)).header("Accept-Language", "vi")))
                .isEqualTo(captured("entry-en-vi-reader.html"));
        assertThat(body(get("/changelog/{slug}/releases/{id}", SLUG, entryId(50)))).isEqualTo(captured("entry-vi.html"));
        assertThat(body(get("/changelog/{slug}", SLUG))).isEqualTo(captured("index.html"));
        assertThat(body(get("/changelog/{slug}", SLUG).header("Accept-Language", "vi")))
                .isEqualTo(captured("index-vi-reader.html"));
        // Today's feed left every item open, so each was nested in the one before. The
        // only difference allowed is that each item now closes after its own description.
        String feed = body(get("/changelog/{slug}/rss.xml", SLUG));
        assertThat(feed.replace("</item>", "")).isEqualTo(captured("rss.xml").replace("</item>", ""));
        assertThat(feed).doesNotContain("</item></item>");
        // Fifty items, each closed straight after its description, and only the channel's
        // own description followed by an item that opens.
        assertThat(feed.split("</description></item>", -1)).hasSize(51);
        assertThat(feed.split("</description><item>", -1)).hasSize(2);
        assertThat(feed).endsWith("</description></item></channel></rss>");
        assertEveryItemIsAChildOfTheChannel(feed, 50);
        // The check would have caught the feed as it was.
        assertThatThrownBy(() -> assertEveryItemIsAChildOfTheChannel(captured("rss.xml"), 50))
                .isInstanceOf(AssertionError.class);
    }

    /**
     * 1.3.0 and 1.3.1 opened an item per entry and never closed one, so with two entries or
     * more each item sat inside the one before it, and most readers showed only the newest.
     * Read as a reader reads it, every item is a child of the channel, one per entry.
     */
    @Test
    void writesEachEntryAsAnItemOfTheChannel() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID first = publishedEntry(owner, projectId, "1.4.0");
        changeSlug(owner, SLUG);
        copyEntry(first, entryId(1), 101, Map.of("language", "vi", "published_at", at(FIRST)));
        copyEntry(first, entryId(2), 102, Map.of("language", "de", "published_at", at(FIRST.plusSeconds(60))));

        assertEveryItemIsAChildOfTheChannel(body(get("/changelog/{slug}/rss.xml", SLUG)), 3);
    }

    private static void assertEveryItemIsAChildOfTheChannel(String feed, int entries) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(feed.getBytes(StandardCharsets.UTF_8)));
        NodeList channels = document.getDocumentElement().getElementsByTagName("channel");
        assertThat(channels.getLength()).isEqualTo(1);
        Node channel = channels.item(0);
        NodeList items = document.getElementsByTagName("item");
        assertThat(items.getLength()).isEqualTo(entries);
        for (int index = 0; index < items.getLength(); index++) {
            Element item = (Element) items.item(index);
            assertThat(item.getParentNode()).as("item %d", index).isSameAs(channel);
            assertThat(item.getElementsByTagName("item").getLength()).as("items inside item %d", index).isZero();
            assertThat(item.getElementsByTagName("guid").getLength()).as("guids of item %d", index).isEqualTo(1);
        }
    }

    /**
     * One real entry, pushed out of the feed by fifty copies that name, date, and word
     * themselves, so nothing the test does not choose reaches a page. Pairs of copies are
     * one release for one audience in English and Vietnamese.
     */
    protected void asToday(Owner owner) throws Exception {
        UUID entryId = publishedEntry(owner, createProject(owner), "1.4.0");
        changeSlug(owner, SLUG);
        for (int n = 1; n <= 50; n++) {
            int pair = (n + 1) / 2;
            boolean english = n % 2 == 1;
            String content = n == 49 ? RICH_NOTE : n == 50 ? VIETNAMESE_NOTE : "Note " + n + ".";
            copyEntry(entryId, entryId(n), 100 + n, Map.of(
                    "organization_slug_snapshot", SLUG,
                    "project_name_snapshot", "ReleaseFlow",
                    "release_version_snapshot", "2.0." + pair,
                    "audience_name_snapshot", "Audience " + pair,
                    "language", english ? "en" : "vi",
                    "content_snapshot", content,
                    "published_at", at(FIRST.plus(Duration.ofHours(n)))));
        }
    }

    static UUID entryId(int n) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(n));
    }

    protected String body(MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    static String captured(String name) throws IOException {
        try (InputStream file = PublicChangelogAsTodayIntegrationTest.class
                .getResourceAsStream("/changelog/as-today/" + name)) {
            assertThat(file).as(name).isNotNull();
            return new String(file.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
