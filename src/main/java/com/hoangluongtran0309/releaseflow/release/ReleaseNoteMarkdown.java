package com.hoangluongtran0309.releaseflow.release;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.List;
import java.util.TreeSet;

/**
 * Markdown helpers for release notes. Untrusted text such as pull request titles is
 * escaped so it cannot add links, emphasis, or HTML to a note.
 */
final class ReleaseNoteMarkdown {

    private static final Parser PARSER = Parser.builder()
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();
    // Each pass escapes every tag the last one found; one escaped tag can only reveal
    // another in contrived input, so a few passes are plenty.
    private static final int MAX_PASSES = 8;

    private ReleaseNoteMarkdown() {
    }

    static String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (char character : text.toCharArray()) {
            if ("\\`*_[]<>".indexOf(character) >= 0) {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }

    /**
     * An address as a link destination: the characters that would end or break one are
     * percent-encoded, so a parenthesis in a person's URL cannot close the link early.
     */
    static String linkDestination(String url) {
        return url.replace(" ", "%20").replace("(", "%28").replace(")", "%29").replace("<", "%3C").replace(">", "%3E");
    }

    /**
     * Backslash-escapes only what a CommonMark reader takes as raw HTML, so that text
     * such as {@code <target-id>} is shown as written everywhere a note is read rather
     * than dropped as an unknown tag. Code, autolinks, emphasis, links, and a {@code <}
     * that starts no tag are left as written, and escaped text is returned unchanged.
     */
    static String escapeRawHtml(String text) {
        if (text == null || text.indexOf('<') < 0) {
            return text;
        }
        String escaped = text;
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            TreeSet<Integer> tagStarts = tagStarts(escaped);
            if (tagStarts.isEmpty()) {
                return escaped;
            }
            StringBuilder next = new StringBuilder(escaped);
            for (int index : tagStarts.descendingSet()) {
                next.insert(index, '\\');
            }
            escaped = next.toString();
        }
        return escaped;
    }

    /** Where each raw HTML node opens: an inline tag's {@code <}, or every {@code <} of a block. */
    private static TreeSet<Integer> tagStarts(String text) {
        TreeSet<Integer> starts = new TreeSet<>();
        PARSER.parse(text).accept(new AbstractVisitor() {
            @Override
            public void visit(HtmlInline node) {
                List<SourceSpan> spans = node.getSourceSpans();
                if (!spans.isEmpty()) {
                    starts.add(spans.getFirst().getInputIndex());
                }
            }

            @Override
            public void visit(HtmlBlock node) {
                for (SourceSpan span : node.getSourceSpans()) {
                    int end = span.getInputIndex() + span.getLength();
                    for (int index = span.getInputIndex(); index < end; index++) {
                        if (text.charAt(index) == '<' && (index == 0 || text.charAt(index - 1) != '\\')) {
                            starts.add(index);
                        }
                    }
                }
            }
        });
        return starts;
    }
}
