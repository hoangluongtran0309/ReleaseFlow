package com.hoangluongtran0309.releaseflow.automation;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.LineBreakRendering;
import org.commonmark.renderer.text.TextContentRenderer;

/**
 * Turns release-note Markdown into the plain text a Microsoft Teams message is sent as
 * (ADR-0025). Markup is dropped and what it escaped is shown as written, so an escaped
 * {@code \<id>} or {@code \*bold\*} reads {@code <id>} and {@code *bold*} rather than
 * keeping its backslash. A link keeps its address beside its text, and blocks stay
 * separated by a blank line.
 */
final class TeamsText {

    private static final Parser PARSER = Parser.builder().build();
    private static final TextContentRenderer RENDERER = TextContentRenderer.builder()
            .lineBreakRendering(LineBreakRendering.SEPARATE_BLOCKS)
            .build();

    private TeamsText() {
    }

    static String render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        return RENDERER.render(PARSER.parse(markdown)).strip();
    }
}
