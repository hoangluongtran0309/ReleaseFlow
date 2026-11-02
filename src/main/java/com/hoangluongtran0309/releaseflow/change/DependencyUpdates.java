package com.hoangluongtran0309.releaseflow.change;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes a dependency update and keeps only its own words for the AI (ADR-0035). A bot
 * that bumps a dependency quotes that dependency's release notes, changelog, and commits,
 * and the AI read them as this project's own steps; a prompt rule alone did not stop it.
 * What the update says itself — what is bumped, and from which version to which — stays.
 */
final class DependencyUpdates {

    private static final Set<String> BOT_AUTHORS = Set.of("dependabot[bot]", "renovate[bot]");
    private static final List<String> BOT_BRANCHES = List.of("dependabot/", "renovate/");
    private static final Set<String> QUOTED_SECTIONS = Set.of("release notes", "changelog", "commits");
    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})\\s+(.*?)[\\s#]*$");
    private static final Pattern BLANK_RUN = Pattern.compile("\\n{3,}");

    private DependencyUpdates() {
    }

    /** Whether a bot opened the change to bump a dependency, by its author or its branch. */
    static boolean isDependencyUpdate(MergedPullRequest change) {
        String author = change.authorLogin();
        if (author != null && BOT_AUTHORS.contains(author.toLowerCase(Locale.ROOT))) {
            return true;
        }
        String branch = change.headBranch();
        return branch != null && BOT_BRANCHES.stream().anyMatch(branch::startsWith);
    }

    /**
     * The description without what it quotes from upstream: {@code <details>} and
     * {@code <blockquote>} elements, Markdown blockquotes, and the sections headed
     * "Release notes", "Changelog", or "Commits".
     */
    static String ownText(String description) {
        if (description == null) {
            return null;
        }
        String text = withoutElement(withoutElement(description.replace("\r\n", "\n"), "details"), "blockquote");
        List<String> kept = new ArrayList<>();
        int skippingBelow = 0;
        for (String line : text.split("\n", -1)) {
            Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                int level = heading.group(1).length();
                if (skippingBelow > 0 && level > skippingBelow) {
                    continue;
                }
                skippingBelow = QUOTED_SECTIONS.contains(title(heading.group(2))) ? level : 0;
                if (skippingBelow > 0) {
                    continue;
                }
            } else if (skippingBelow > 0 || line.stripLeading().startsWith(">")) {
                continue;
            }
            kept.add(line);
        }
        String own = BLANK_RUN.matcher(String.join("\n", kept)).replaceAll("\n\n").strip();
        return own.isEmpty() ? null : own;
    }

    // An element and everything inside it, nested ones included. An element left open
    // runs to the end, since what follows its start is still the quoted material.
    private static String withoutElement(String text, String tag) {
        Matcher tags = Pattern.compile("<(/?)" + tag + "\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(text);
        StringBuilder kept = new StringBuilder();
        int depth = 0;
        int from = 0;
        while (tags.find()) {
            boolean closing = !tags.group(1).isEmpty();
            if (depth == 0) {
                kept.append(text, from, tags.start());
            }
            depth = closing ? Math.max(0, depth - 1) : depth + 1;
            from = tags.end();
        }
        if (depth == 0) {
            kept.append(text.substring(from));
        }
        return kept.toString();
    }

    // "**Release Notes:**" reads as "release notes".
    private static String title(String heading) {
        return heading.replaceAll("[*_`:]", "").strip().toLowerCase(Locale.ROOT);
    }
}
