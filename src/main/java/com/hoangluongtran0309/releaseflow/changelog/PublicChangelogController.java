package com.hoangluongtran0309.releaseflow.changelog;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * An Organization's changelog, for anybody at all: no session, no cookie, nothing but
 * what was published. An entry and its revisions never change, but a correction can
 * follow, so an entry's address is cached for five minutes and validated by its latest
 * revision, while an earlier revision, which nothing can change, is cached for a day.
 * Caching headers are set only once the page is found, so no answer that says nothing is
 * there is ever cached: a number that is not an earlier revision yet may become one.
 *
 * <p>A page returns a view name rather than a {@code ResponseEntity} wrapping one:
 * wrapping it would make the model the response body and serve internal identifiers as
 * JSON. Headers belong on the response, which is where the caching contract lives.
 */
@Controller
class PublicChangelogController {

    private static final MediaType RSS = new MediaType("application", "rss+xml", StandardCharsets.UTF_8);
    private static final DateTimeFormatter RSS_DATE =
            DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter PUBLISHED_ON =
            DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneOffset.UTC);
    private static final String LIST_CACHE = CacheControl.maxAge(Duration.ofMinutes(5))
            .cachePublic()
            .getHeaderValue();
    private static final String ENTRY_CACHE = CacheControl.maxAge(Duration.ofMinutes(5))
            .cachePublic()
            .getHeaderValue();
    private static final String REVISION_CACHE = CacheControl.maxAge(Duration.ofDays(1))
            .cachePublic()
            .immutable()
            .getHeaderValue();
    private static final Pattern REVISION_NUMBER = Pattern.compile("[1-9][0-9]{0,9}");
    // The note is the same for everybody; the chrome around it follows the reader, so a
    // shared cache must keep one reader's language out of another's response.
    private static final String CHROME_VARIES_BY = "Accept-Language, Cookie";

    private final PublicChangelogService changelog;
    private final PublicChangelogUrls urls;

    PublicChangelogController(PublicChangelogService changelog, PublicChangelogUrls urls) {
        this.changelog = changelog;
        this.urls = urls;
    }

    @GetMapping("/changelog/{slug}")
    String index(@PathVariable String slug, Model model, HttpServletResponse response) {
        PublicChangelogService.Feed feed = changelog.findFeed(slug).orElseThrow(PublicChangelogController::notFound);
        model.addAttribute("organizationName", feed.organizationName());
        model.addAttribute("cards", feed.cards());
        model.addAttribute("canonicalUrl", urls.rootUrl(feed.slug()));
        model.addAttribute("feedUrl", urls.feedUrl(feed.slug()));
        model.addAttribute("publishedOn", PUBLISHED_ON);
        response.setHeader("Cache-Control", LIST_CACHE);
        response.setHeader("Vary", CHROME_VARIES_BY);
        return "changelog/index";
    }

    @GetMapping("/changelog/{slug}/releases/{entryId}")
    String entry(
            @PathVariable String slug,
            @PathVariable UUID entryId,
            Locale locale,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        PublicChangelogService.Entry found = changelog.findEntry(slug, entryId)
                .orElseThrow(PublicChangelogController::notFound);
        response.setHeader("Cache-Control", ENTRY_CACHE);
        response.setHeader("Vary", CHROME_VARIES_BY);
        if (new ServletWebRequest(request, response).checkNotModified(etag(entryId, found.revision(), locale))) {
            return null;
        }
        model.addAttribute("organizationName", found.organizationName());
        model.addAttribute("entry", found.entry());
        model.addAttribute("correction", found.correction());
        model.addAttribute("earlierRevisions", found.earlierRevisions());
        model.addAttribute("canonicalUrl", urls.entryUrl(found.slug(), found.entry().id()));
        model.addAttribute("changelogUrl", urls.rootUrl(found.slug()));
        model.addAttribute("feedUrl", urls.feedUrl(found.slug()));
        model.addAttribute("publishedOn", PUBLISHED_ON);
        return "changelog/detail";
    }

    /**
     * A revision a later one replaced. It never changes, and it asks search engines not to
     * index it, so a search finds what the entry says now. Anything that is not the number
     * of an earlier revision, however large, is the same not-found as an unknown entry.
     */
    @GetMapping("/changelog/{slug}/releases/{entryId}/revisions/{revision}")
    String revision(
            @PathVariable String slug,
            @PathVariable UUID entryId,
            @PathVariable String revision,
            Locale locale,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        int number = revisionNumber(revision);
        PublicChangelogService.EarlierRevision found = changelog.findEarlierRevision(slug, entryId, number)
                .orElseThrow(PublicChangelogController::notFound);
        response.setHeader("Cache-Control", REVISION_CACHE);
        response.setHeader("Vary", CHROME_VARIES_BY);
        response.setHeader("X-Robots-Tag", "noindex");
        if (new ServletWebRequest(request, response).checkNotModified(etag(entryId, number, locale))) {
            return null;
        }
        model.addAttribute("organizationName", found.organizationName());
        model.addAttribute("revision", found);
        model.addAttribute("entry", found.entry());
        model.addAttribute("correction", found.correction());
        model.addAttribute("canonicalUrl", found.url());
        model.addAttribute("entryUrl", found.entry().url());
        model.addAttribute("changelogUrl", urls.rootUrl(found.slug()));
        model.addAttribute("feedUrl", urls.feedUrl(found.slug()));
        model.addAttribute("publishedOn", PUBLISHED_ON);
        return "changelog/revision";
    }

    /**
     * A page's validator: the entry, the revision it shows, and the reader's language. The
     * note is the same for everybody, but the page around it follows the reader, so a
     * reader who switches language must not be told that the other language's page is
     * still current.
     */
    private static String etag(UUID entryId, int revision, Locale locale) {
        return "\"" + entryId + "-" + revision + "-" + locale.toLanguageTag() + "\"";
    }

    private static int revisionNumber(String value) {
        if (!REVISION_NUMBER.matcher(value).matches()) {
            throw notFound();
        }
        long number = Long.parseLong(value);
        if (number > Integer.MAX_VALUE) {
            throw notFound();
        }
        return (int) number;
    }

    @GetMapping("/changelog/{slug}/rss.xml")
    ResponseEntity<String> feed(@PathVariable String slug) {
        PublicChangelogService.Feed feed = changelog.findFeed(slug).orElseThrow(PublicChangelogController::notFound);
        return ResponseEntity.ok()
                .contentType(RSS)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(rss(feed));
    }

    // RSS 2.0, written with the JDK's own XML writer so every value is escaped by the
    // same code that puts it there. Its own words stay English: a feed is one shared,
    // cached document, and a reader's client says nothing about who subscribed. A
    // correction's line is the note's own, in the note's language.
    private String rss(PublicChangelogService.Feed feed) {
        String root = urls.rootUrl(feed.slug());
        try {
            StringWriter output = new StringWriter();
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("rss");
            xml.writeAttribute("version", "2.0");
            xml.writeStartElement("channel");
            element(xml, "title", feed.organizationName() + " releases");
            element(xml, "link", root);
            element(xml, "description", "Published release notes from " + feed.organizationName());
            for (PublicChangelogService.FeedItem item : feed.items()) {
                if (item.correction() == null) {
                    published(xml, item.entry());
                } else {
                    corrected(xml, item.entry(), item.correction());
                }
            }
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
            return output.toString();
        } catch (XMLStreamException failure) {
            // A fault, not an answer: it is logged and the reader gets the plain error page.
            throw new IllegalStateException("Could not write the RSS feed.", failure);
        }
    }

    private static void published(XMLStreamWriter xml, EntryView entry) throws XMLStreamException {
        xml.writeStartElement("item");
        element(xml, "title", entry.projectName() + " " + entry.releaseVersion());
        element(xml, "link", entry.url());
        xml.writeStartElement("guid");
        xml.writeAttribute("isPermaLink", "true");
        xml.writeCharacters(entry.url());
        xml.writeEndElement();
        element(xml, "pubDate", RSS_DATE.format(entry.publishedAt()));
        // Readers render a description as HTML, so raw Markdown would reach
        // subscribers as literal asterisks. It is the same safe HTML the page
        // shows, and the writer escapes it on the way into the document.
        element(xml, "description", entry.html());
        xml.writeEndElement();
    }

    /**
     * A correction is an item of its own: RSS 2.0 cannot say an item changed, and a reader
     * that has seen a guid does not show it again. Its guid names the correction and is no
     * address; its link is the entry, which shows the correction.
     */
    private static void corrected(XMLStreamWriter xml, EntryView entry, PublicChangelogService.Correction correction)
            throws XMLStreamException {
        xml.writeStartElement("item");
        element(xml, "title", entry.projectName() + " " + entry.releaseVersion() + " (corrected)");
        element(xml, "link", entry.url());
        xml.writeStartElement("guid");
        xml.writeAttribute("isPermaLink", "false");
        xml.writeCharacters(entry.url() + "#revision-" + correction.revision());
        xml.writeEndElement();
        element(xml, "pubDate", RSS_DATE.format(correction.correctedAt()));
        // The line holds a person's reason, so it is escaped into the HTML here, once, and
        // the writer encodes that HTML as it encodes every description.
        String line = HtmlUtils.htmlEscape(correction.line(), StandardCharsets.UTF_8.name()).replace("\n", "<br>");
        element(xml, "description", "<p lang=\"" + HtmlUtils.htmlEscape(entry.language()) + "\">" + line + "</p>\n"
                + entry.withContent(correction.content()).html());
        xml.writeEndElement();
    }

    private static void element(XMLStreamWriter xml, String name, String value) throws XMLStreamException {
        xml.writeStartElement(name);
        xml.writeCharacters(value == null ? "" : value);
        xml.writeEndElement();
    }

    /** An address nobody answers says nothing about who exists. */
    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
