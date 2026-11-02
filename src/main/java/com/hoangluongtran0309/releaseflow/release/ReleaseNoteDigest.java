package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.audience.AudienceItem;
import com.hoangluongtran0309.releaseflow.audience.AudienceTemplate;
import com.hoangluongtran0309.releaseflow.audience.ReleaseLinks;
import com.hoangluongtran0309.releaseflow.change.AudienceNarrative;
import com.hoangluongtran0309.releaseflow.change.ChangeView;
import com.hoangluongtran0309.releaseflow.change.MigrationKind;
import com.hoangluongtran0309.releaseflow.change.NeutralSummary;
import com.hoangluongtran0309.releaseflow.change.Relevance;
import com.hoangluongtran0309.releaseflow.jira.LinkedIssue;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders the release note one audience reads: a title, a "What's New" overview with
 * counts, a breaking-change warning, one section per kind of change, each item written
 * by the audience's template, and the audience's footer once at the end. It is a pure
 * function of its input and never calls AI or the network. Labels follow the note's
 * language (English or Vietnamese, otherwise English).
 *
 * <p>The note shows only the changes that concern its audience (ADR-0035): one whose
 * relevance for it is none is left out, unless it is breaking, and then it reads as
 * brief. Counts and the warning describe what the note shows. An audience left with
 * nothing still gets a note, which says so.
 *
 * <p>Each change is in one section (ADR-0037): a breaking one in Breaking, a security
 * one that is not breaking in Security, and any other in its category's. A change that
 * is both is marked as security in Breaking, whatever the audience's template prints. A
 * security change concerns operators, contributors, and support even when its relevance
 * for them is none, and then it reads as brief.
 */
final class ReleaseNoteDigest {

    enum Section {
        BREAKING, SECURITY, FEATURES, FIXES, PERFORMANCE, DOCUMENTATION, MAINTENANCE, OTHER;

        static Section of(ChangeView change) {
            if (change.breaking()) {
                return BREAKING;
            }
            if (change.security()) {
                return SECURITY;
            }
            return switch (change.categoryGroup()) {
                case FEATURE -> FEATURES;
                case FIX -> FIXES;
                case PERFORMANCE -> PERFORMANCE;
                case DOCUMENTATION -> DOCUMENTATION;
                case MAINTENANCE -> MAINTENANCE;
                case OTHER -> OTHER;
            };
        }
    }

    private static final String BUNDLE = "messages/release-note";
    // The shipped audiences that upgrade or answer for an upgrade: a security change is always theirs.
    private static final Set<String> SECURITY_AUDIENCES = Set.of("operator", "contributor", "support");
    // A list marker or an ordered one, after which the security marker goes.
    private static final Pattern LIST_ITEM = Pattern.compile("^(\\s*(?:[-*+]|\\d{1,9}[.)])\\s+)");
    private static final String ITEM_SEPARATOR = "\n\n";
    private static final int MAX_HEADING = 6;
    private static final int HEADING_DEMOTION = 2;

    private ReleaseNoteDigest() {
    }

    /** A note with no footer and no release links. */
    static String render(
            String version,
            String summary,
            List<ChangeView> changes,
            String templateBody,
            String audienceCode,
            String languageTag
    ) {
        return render(summary, changes, templateBody, "", new ReleaseLinks(version, "", "", "", ""), audienceCode, languageTag);
    }

    /**
     * @param footerBody the audience's footer for this language; blank adds nothing
     * @param links the release's version and its release-wide links, unescaped
     */
    static String render(
            String summary,
            List<ChangeView> changes,
            String templateBody,
            String footerBody,
            ReleaseLinks links,
            String audienceCode,
            String languageTag
    ) {
        if (changes.isEmpty()) {
            throw new IllegalArgumentException("A release note needs at least one change.");
        }
        Labels labels = new Labels(languageTag);
        Map<Section, List<ChangeView>> sections = sections(changes.stream()
                .filter(change -> concerns(change, audienceCode))
                .toList());

        StringBuilder note = new StringBuilder("# ")
                .append(labels.format("title", ReleaseNoteMarkdown.escape(links.version())))
                .append("\n\n");
        if (summary != null && !summary.isBlank()) {
            note.append(ReleaseNoteMarkdown.escape(summary.strip())).append("\n\n");
        }
        if (sections.isEmpty()) {
            note.append(labels.raw("nothingForAudience")).append('\n');
        } else {
            note.append("## ").append(labels.raw("whatsNew")).append("\n\n")
                    .append(overview(sections, links.upgradeGuideUrl(), labels)).append('\n');
            sections.forEach((section, items) -> {
                note.append("\n## ").append(labels.raw("section." + section.name())).append("\n\n");
                note.append(String.join(ITEM_SEPARATOR, items.stream()
                        .map(change -> item(change, templateBody, audienceCode, labels))
                        .toList()));
                note.append('\n');
            });
        }
        String footer = AudienceTemplate.renderFooter(footerBody, footerLinks(links));
        if (!footer.isEmpty()) {
            note.append('\n').append(footer).append('\n');
        }
        return note.toString().strip() + "\n";
    }

    // One change as the audience's template writes it. A change that is both breaking and
    // security says so in Breaking, after its list marker, whatever the template prints.
    private static String item(ChangeView change, String templateBody, String audienceCode, Labels labels) {
        String rendered = demoteHeadings(AudienceTemplate.render(templateBody, item(change, audienceCode)).strip());
        if (!change.breaking() || !change.security()) {
            return rendered;
        }
        String marker = labels.raw("securityMarker") + " ";
        Matcher listItem = LIST_ITEM.matcher(rendered);
        return listItem.find()
                ? listItem.group(1) + marker + rendered.substring(listItem.end())
                : marker + rendered;
    }

    // The values a footer prints: versions are text a person wrote, so they are escaped,
    // and the upgrade guide cannot close the link it is put in.
    private static ReleaseLinks footerLinks(ReleaseLinks links) {
        return new ReleaseLinks(
                ReleaseNoteMarkdown.escape(links.version()),
                ReleaseNoteMarkdown.escape(links.previousVersion()),
                links.compareUrl(),
                ReleaseNoteMarkdown.linkDestination(links.upgradeGuideUrl()),
                links.releaseUrl()
        );
    }

    /**
     * Whether a change is in the audience's note: a breaking change always is, a security
     * change always is for operators, contributors, and support, and any other unless it is
     * none for it.
     */
    static boolean concerns(ChangeView change, String audienceCode) {
        return change.breaking() || alwaysConcerns(change, audienceCode) || relevance(change, audienceCode) != Relevance.NONE;
    }

    // A change with no narrative for the audience (no AI, a failure, an audience added
    // later) is full, and a breaking change, or a security change for an upgrader, that is
    // none for it reads as brief.
    static Relevance relevance(ChangeView change, String audienceCode) {
        AudienceNarrative narrative = change.audienceNarrative(audienceCode);
        Relevance relevance = narrative == null ? Relevance.FULL : narrative.relevance();
        return relevance == Relevance.NONE && (change.breaking() || alwaysConcerns(change, audienceCode))
                ? Relevance.BRIEF
                : relevance;
    }

    private static boolean alwaysConcerns(ChangeView change, String audienceCode) {
        return change.security() && SECURITY_AUDIENCES.contains(audienceCode);
    }

    // Breaking changes first, then security ones, then the other kinds in a fixed order;
    // empty sections are left out.
    static Map<Section, List<ChangeView>> sections(List<ChangeView> changes) {
        Map<Section, List<ChangeView>> sections = new EnumMap<>(Section.class);
        changes.stream()
                .sorted(Comparator.comparing(ChangeView::mergedAt).thenComparing(ChangeView::pullRequestNumber))
                .forEach(change -> sections.computeIfAbsent(Section.of(change), ignored -> new ArrayList<>()).add(change));
        return sections;
    }

    /**
     * What one change says to one audience. Without a summary, what changed is the pull
     * request title, escaped because it is untrusted text. Summary and narrative text keep
     * their Markdown, but any raw HTML in it is escaped here, as the note is written, so a
     * placeholder such as {@code <id>} reads the same on GitHub as on the release page.
     * The stored text, and what is sent for translation, stay as they were written.
     */
    static AudienceItem item(ChangeView change, String audienceCode) {
        NeutralSummary summary = change.neutralSummary();
        AudienceNarrative written = change.audienceNarrative(audienceCode);
        String narrative = ReleaseNoteMarkdown.escapeRawHtml(change.narrative(audienceCode));
        String headline = written == null ? "" : ReleaseNoteMarkdown.escapeRawHtml(written.headline());
        String relevance = relevance(change, audienceCode) == Relevance.BRIEF ? AudienceItem.BRIEF : AudienceItem.FULL;
        if (summary == null) {
            return new AudienceItem(
                    ReleaseNoteMarkdown.escape(ReleaseNotePreview.displayTitle(change.title())),
                    "",
                    "",
                    "",
                    "",
                    "",
                    narrative,
                    headline,
                    relevance,
                    change.reference(),
                    change.pullRequestNumber(),
                    change.url(),
                    linkedIssues(change)
            );
        }
        return new AudienceItem(
                ReleaseNoteMarkdown.escapeRawHtml(summary.whatChanged()),
                ReleaseNoteMarkdown.escapeRawHtml(summary.whyChanged()),
                ReleaseNoteMarkdown.escapeRawHtml(summary.technicalDetail()),
                ReleaseNoteMarkdown.escapeRawHtml(summary.migrationStep()),
                summary.migrationKind() == MigrationKind.NONE ? "" : summary.migrationKind().value(),
                ReleaseNoteMarkdown.escapeRawHtml(summary.limitations()),
                narrative,
                headline,
                relevance,
                change.reference(),
                change.pullRequestNumber(),
                change.url(),
                linkedIssues(change)
        );
    }

    /**
     * The issues a change mentions, as a template may print them. Every value but the
     * URL ReleaseFlow built itself is the tracker's own text, so it is escaped.
     */
    private static List<LinkedIssue> linkedIssues(ChangeView change) {
        return change.linkedIssues().stream()
                .map(issue -> new LinkedIssue(
                        issue.key(),
                        ReleaseNoteMarkdown.escape(issue.title()),
                        "",
                        issue.type() == null ? null : ReleaseNoteMarkdown.escape(issue.type()),
                        issue.status() == null ? null : ReleaseNoteMarkdown.escape(issue.status()),
                        issue.url()
                ))
                .toList();
    }

    // Every change is in one section, so the counts add up to the total. The warning links
    // its call to action to the upgrade guide when the release has one.
    private static String overview(Map<Section, List<ChangeView>> sections, String upgradeGuideUrl, Labels labels) {
        int total = sections.values().stream().mapToInt(List::size).sum();
        List<String> counts = new ArrayList<>();
        sections.forEach((section, items) -> counts.add(labels.format("count." + section.name(), items.size())));
        StringBuilder overview = new StringBuilder(labels.format("overview", total, labels.join(counts)));
        List<ChangeView> breaking = sections.get(Section.BREAKING);
        if (breaking != null) {
            overview.append("\n\n> ").append(upgradeGuideUrl.isEmpty()
                    ? labels.format("breakingWarning", breaking.size())
                    : labels.format("breakingWarning.guide", breaking.size(),
                            ReleaseNoteMarkdown.linkDestination(upgradeGuideUrl)));
        }
        return overview.toString();
    }

    /**
     * Moves every Markdown heading of an item down two levels, at most to level six, so
     * an item never competes with the note's own headings. Fenced code is left alone.
     */
    static String demoteHeadings(String body) {
        StringBuilder demoted = new StringBuilder();
        boolean insideFence = false;
        String[] lines = body.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                insideFence = !insideFence;
            } else if (!insideFence) {
                int hashes = 0;
                while (hashes < trimmed.length() && trimmed.charAt(hashes) == '#') {
                    hashes++;
                }
                boolean heading = hashes >= 1 && hashes <= MAX_HEADING
                        && (hashes == trimmed.length() || trimmed.charAt(hashes) == ' ');
                if (heading) {
                    line = "#".repeat(Math.min(hashes + HEADING_DEMOTION, MAX_HEADING)) + trimmed.substring(hashes);
                }
            }
            demoted.append(line);
            if (index < lines.length - 1) {
                demoted.append('\n');
            }
        }
        return demoted.toString();
    }

    private static final class Labels {

        private final ResourceBundle bundle;
        private final Locale locale;

        Labels(String languageTag) {
            this.locale = Locale.forLanguageTag(languageTag);
            this.bundle = ResourceBundle.getBundle(
                    BUNDLE,
                    locale,
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES)
            );
        }

        String raw(String key) {
            return bundle.getString(key);
        }

        String format(String key, Object... arguments) {
            return new MessageFormat(raw(key), locale).format(arguments);
        }

        String join(List<String> parts) {
            if (parts.size() == 1) {
                return parts.getFirst();
            }
            if (parts.size() == 2) {
                return format("list.pair", parts.get(0), parts.get(1));
            }
            String joined = parts.getFirst();
            for (String part : parts.subList(1, parts.size() - 1)) {
                joined = format("list.middle", joined, part);
            }
            return format("list.last", joined, parts.getLast());
        }
    }
}
