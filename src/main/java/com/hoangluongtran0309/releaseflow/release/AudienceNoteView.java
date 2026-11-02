package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.audience.MarkdownHtml;
import com.hoangluongtran0309.releaseflow.translation.GlossaryUse;
import com.hoangluongtran0309.releaseflow.translation.TranslationState;

import java.time.Instant;
import java.util.UUID;

/**
 * One audience's stored note in one language. {@code autoRerender} is true while the
 * note still follows its template, and false once a person has edited it.
 * {@code translationStatus} is READY once every translation it needs is in; until then
 * it shows the untranslated text. {@code glossaryStatus} says what those translations did
 * with the Organization's glossary, with {@code glossaryReason} when it was not applied.
 */
public record AudienceNoteView(
        UUID id,
        UUID audienceId,
        String audienceCode,
        String audienceName,
        String language,
        String content,
        boolean autoRerender,
        String lastEditorName,
        Instant updatedAt,
        TranslationState.Status translationStatus,
        GlossaryUse.Status glossaryStatus,
        String glossaryReason
) {

    /** The content as HTML that is safe to insert into a page. */
    public String html() {
        return MarkdownHtml.render(content);
    }
}
