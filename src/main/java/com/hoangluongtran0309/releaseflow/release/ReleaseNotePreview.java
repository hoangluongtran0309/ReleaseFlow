package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.category.CategoryGroup;
import com.hoangluongtran0309.releaseflow.change.ChangeView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Groups a release's changes into release-note sections. Breaking changes come first
 * and are listed only there, then security changes that are not breaking (ADR-0037); the
 * other sections follow in a fixed group order. Unknown changes are left out; review
 * gives them a category before approval.
 */
final class ReleaseNotePreview {

    private static final Pattern CONVENTIONAL_PREFIX = Pattern.compile(
            "^(feat|fix|perf|docs|refactor|chore|ci|build|test)(\\([^)]*\\))?!?:\\s*",
            Pattern.CASE_INSENSITIVE
    );
    // What a title-only item may not count as its words: issue keys and #12 or !12.
    private static final Pattern REFERENCE = Pattern.compile("\\b[A-Z][A-Z0-9]+-\\d+\\b|[#!]\\d+");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    // A change written from its title alone needs at least this many words of it (ADR-0035).
    static final int TITLE_WORDS = 3;
    // A description shorter than this gives an AI summary of a short title nothing to say.
    static final int DESCRIPTION_WORDS = 10;
    // A pull request template's hints, which nobody wrote about the change.
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Map<CategoryGroup, String> SECTION_TITLES = new LinkedHashMap<>();

    static {
        SECTION_TITLES.put(CategoryGroup.FEATURE, "Features");
        SECTION_TITLES.put(CategoryGroup.FIX, "Fixes");
        SECTION_TITLES.put(CategoryGroup.PERFORMANCE, "Performance");
        SECTION_TITLES.put(CategoryGroup.DOCUMENTATION, "Documentation");
        SECTION_TITLES.put(CategoryGroup.MAINTENANCE, "Maintenance");
        SECTION_TITLES.put(CategoryGroup.OTHER, "Other changes");
    }

    private ReleaseNotePreview() {
    }

    static List<ReleaseNoteSection> sections(List<ChangeView> changes) {
        List<ChangeView> sorted = changes.stream()
                .filter(change -> change.breaking() || !change.unknownCategory())
                .sorted(Comparator.comparing(ChangeView::mergedAt).thenComparing(ChangeView::pullRequestNumber))
                .toList();

        List<ReleaseNoteSection> sections = new ArrayList<>();
        List<ReleaseNoteItem> breaking = sorted.stream().filter(ChangeView::breaking).map(ReleaseNotePreview::item).toList();
        if (!breaking.isEmpty()) {
            sections.add(new ReleaseNoteSection("Breaking changes", breaking));
        }
        List<ReleaseNoteItem> security = sorted.stream()
                .filter(change -> !change.breaking() && change.security())
                .map(ReleaseNotePreview::item)
                .toList();
        if (!security.isEmpty()) {
            sections.add(new ReleaseNoteSection("Security", security));
        }
        SECTION_TITLES.forEach((group, title) -> {
            List<ReleaseNoteItem> section = sorted.stream()
                    .filter(change -> !change.breaking() && !change.security() && change.categoryGroup() == group)
                    .map(ReleaseNotePreview::item)
                    .toList();
            if (!section.isEmpty()) {
                sections.add(new ReleaseNoteSection(title, section));
            }
        });
        return List.copyOf(sections);
    }

    /**
     * Whether a person has to write a change's summary before it reaches a note: its title,
     * such as "fix" or "update deps", is too short to stand alone, and nothing better stands
     * in for it. A summary a person wrote does. One the AI wrote does only when the
     * description gave it something to work from: at least {@value #DESCRIPTION_WORDS}
     * words, and a context the AI did not find insufficient. Otherwise the AI could only
     * restate the title ("A fix was made in the codebase."). It never asks the AI.
     */
    static boolean needsSummary(ChangeView change) {
        if (words(REFERENCE.matcher(displayTitle(change.title())).replaceAll(" ")) >= TITLE_WORDS
                || change.summaryEditedAt() != null) {
            return false;
        }
        if (change.neutralSummary() == null) {
            return true;
        }
        String description = change.description() == null ? "" : HTML_COMMENT.matcher(change.description()).replaceAll(" ");
        return words(description) < DESCRIPTION_WORDS || change.context() != null && change.context().insufficient();
    }

    private static long words(String text) {
        return WORD.matcher(text).results().count();
    }

    static String displayTitle(String title) {
        String stripped = CONVENTIONAL_PREFIX.matcher(title.strip()).replaceFirst("");
        return stripped.isBlank() ? title.strip() : stripped;
    }

    private static ReleaseNoteItem item(ChangeView change) {
        return new ReleaseNoteItem(
                change.id(),
                change.pullRequestNumber(),
                displayTitle(change.title()),
                change.url(),
                change.category(),
                change.breaking(),
                change.categoryName(),
                change.reference()
        );
    }
}
