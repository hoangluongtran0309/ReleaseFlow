package com.hoangluongtran0309.releaseflow.changelog;

import com.hoangluongtran0309.releaseflow.audience.MarkdownHtml;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** One public entry as a page or a feed needs it. */
public record EntryView(
        UUID id,
        String projectName,
        String releaseVersion,
        String audienceName,
        String language,
        String content,
        Instant publishedAt,
        String url
) {

    /**
     * The note's language named in that language, such as "English" or "Tiếng Việt", so a
     * reader recognises theirs whatever language the page around it is in.
     */
    public String languageName() {
        Locale locale = Locale.forLanguageTag(language);
        String name = locale.getDisplayName(locale);
        if (name.isEmpty()) {
            return language;
        }
        return name.substring(0, 1).toUpperCase(locale) + name.substring(1);
    }

    /** The note as HTML that is safe to insert into a page or an RSS description. */
    public String html() {
        return MarkdownHtml.render(content);
    }
}
