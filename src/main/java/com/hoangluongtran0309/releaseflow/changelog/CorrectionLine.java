package com.hoangluongtran0309.releaseflow.changelog;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * "Corrected on 15 November 2026: {reason}", in the note's language rather than the
 * reader's (ADR-0046, part 4). It comes from the bundle a note's own labels come from,
 * so a language with no translation of it reads it in English, as it reads those labels.
 * The day is the one in UTC, as a published date is. The reason is a person's text and
 * is returned as written: whoever puts the line into a page or a feed escapes it.
 */
final class CorrectionLine {

    private static final String BUNDLE = "messages/release-note";

    private CorrectionLine() {
    }

    static String of(String language, Instant correctedAt, String reason) {
        Locale locale = Locale.forLanguageTag(language);
        ResourceBundle labels = ResourceBundle.getBundle(
                BUNDLE,
                locale,
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES)
        );
        String day = DateTimeFormatter.ofPattern(labels.getString("correctedOn.date"), locale)
                .withZone(ZoneOffset.UTC)
                .format(correctedAt);
        return new MessageFormat(labels.getString("correctedOn"), locale).format(new Object[]{day, reason});
    }
}
