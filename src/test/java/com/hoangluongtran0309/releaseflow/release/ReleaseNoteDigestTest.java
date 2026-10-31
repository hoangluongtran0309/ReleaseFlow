package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.category.CategoryGroup;
import com.hoangluongtran0309.releaseflow.audience.ReleaseLinks;
import com.hoangluongtran0309.releaseflow.category.CategoryRef;
import com.hoangluongtran0309.releaseflow.change.AiStatus;
import com.hoangluongtran0309.releaseflow.change.AudienceNarrative;
import com.hoangluongtran0309.releaseflow.change.ChangeView;
import com.hoangluongtran0309.releaseflow.change.ChangedFileStatus;
import com.hoangluongtran0309.releaseflow.change.ClassificationSource;
import com.hoangluongtran0309.releaseflow.change.NeutralSummary;
import com.hoangluongtran0309.releaseflow.change.ProcessingStatus;
import com.hoangluongtran0309.releaseflow.change.Relevance;
import com.hoangluongtran0309.releaseflow.jira.LinkedIssue;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import com.hoangluongtran0309.releaseflow.support.TestCategories;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseNoteDigestTest {

    private static final String TEMPLATE = "- {{whatChanged}}{{#narrative}} — {{.}}{{/narrative}}";

    @Test
    void putsBreakingChangesFirstAndCountsEverySection() {
        List<ChangeView> changes = List.of(
                change(4, "docs: explain export", TestCategories.DOCUMENTATION, false, 4, null, Map.of()),
                change(1, "feat: add export", TestCategories.FEATURE, false, 1, null, Map.of()),
                change(3, "fix: handle empty tables", TestCategories.FIX, false, 3, null, Map.of()),
                change(2, "feat!: rename the API", TestCategories.FEATURE, true, 2, null, Map.of()),
                change(5, "feat: add import", TestCategories.FEATURE, false, 5, null, Map.of()),
                change(6, "perf: stream rows", TestCategories.PERFORMANCE, false, 6, null, Map.of()),
                change(7, "chore: bump plugin", TestCategories.MAINTENANCE, false, 7, null, Map.of())
        );

        assertThat(ReleaseNoteDigest.render("2.0.0", "  A big one.  ", changes, TEMPLATE, "operator", "en")).isEqualTo("""
                # Release 2.0.0

                A big one.

                ## What's New

                This release includes 7 changes: 1 breaking change, 2 new features, 1 bug fix, 1 performance improvement, 1 documentation update, and 1 maintenance change.

                > ⚠️ 1 breaking change requires action before upgrading.

                ## ⚠️ Breaking Changes

                - rename the API

                ## ✨ New Features

                - add export

                - add import

                ## 🐛 Bug Fixes

                - handle empty tables

                ## ⚡ Performance

                - stream rows

                ## 📚 Documentation

                - explain export

                ## 🔧 Maintenance

                - bump plugin
                """);
    }

    // ADR-0035: a note shows what concerns its audience, and counts what it shows.
    @Test
    void selectsAndCountsTheChangesOfEachAudience() {
        AudienceNarrative notForEndUsers = new AudienceNarrative(Relevance.NONE, "", "");
        List<ChangeView> changes = List.of(
                change(1, "feat: add export", TestCategories.FEATURE, false, 1, null,
                        Map.of("end_user", AudienceNarrative.of("Download any table."))),
                change(2, "fix: retry the CI cache", TestCategories.FIX, false, 2, null,
                        Map.of("end_user", notForEndUsers, "operator", new AudienceNarrative(Relevance.FULL, "", "CI only."))),
                change(3, "chore: bump plugin", TestCategories.MAINTENANCE, false, 3, null, Map.of("end_user", notForEndUsers)),
                // No narrative for an audience means it is included, as before ADR-0035.
                change(4, "fix: trim input", TestCategories.FIX, false, 4, null, Map.of())
        );

        String endUser = ReleaseNoteDigest.render("1.1.0", null, changes, TEMPLATE, "end_user", "en");
        String operator = ReleaseNoteDigest.render("1.1.0", null, changes, TEMPLATE, "operator", "en");

        assertThat(endUser)
                .contains("This release includes 2 changes: 1 new feature and 1 bug fix.")
                .contains("- add export — Download any table.", "- trim input")
                .doesNotContain("retry the CI cache", "bump plugin", "Maintenance");
        assertThat(operator)
                .contains("This release includes 4 changes: 1 new feature, 2 bug fixes, and 1 maintenance change.")
                .contains("- retry the CI cache — CI only.", "- bump plugin");
    }

    @Test
    void neverLeavesOutABreakingChangeAndReadsNoneOnItAsBrief() {
        List<ChangeView> changes = List.of(
                change(1, "feat!: drop the v1 API", TestCategories.FEATURE, true, 1, null,
                        Map.of("end_user", new AudienceNarrative(Relevance.NONE, "", ""))),
                change(2, "fix: retry the CI cache", TestCategories.FIX, false, 2, null,
                        Map.of("end_user", new AudienceNarrative(Relevance.NONE, "", "")))
        );
        String template = "- {{headline}} ({{relevance}}){{^brief}} — details{{/brief}}";

        assertThat(ReleaseNoteDigest.render("2.0.0", null, changes, template, "end_user", "en"))
                .contains("This release includes 1 change: 1 breaking change.")
                .contains("> ⚠️ 1 breaking change requires action before upgrading.")
                .contains("- drop the v1 API (brief)")
                .doesNotContain("details", "retry the CI cache");
    }

    @Test
    void writesANoteForAnAudienceLeftWithNothing() {
        List<ChangeView> changes = List.of(
                change(1, "chore: bump plugin", TestCategories.MAINTENANCE, false, 1, null,
                        Map.of("end_user", new AudienceNarrative(Relevance.NONE, "", "")))
        );

        assertThat(ReleaseNoteDigest.render("1.0.1", "  Housekeeping.  ", changes, TEMPLATE, "end_user", "en")).isEqualTo("""
                # Release 1.0.1

                Housekeeping.

                Nothing in this release concerns this audience.
                """);
        assertThat(ReleaseNoteDigest.render("1.0.1", null, changes, TEMPLATE, "end_user", "vi"))
                .isEqualTo("# Bản phát hành 1.0.1\n\nBản phát hành này không có thay đổi nào liên quan tới nhóm đọc này.\n");
    }

    @Test
    void givesTemplatesTheHeadlineOrWhatChangedAndTheRelevance() {
        NeutralSummary summary = new NeutralSummary("Tables export as CSV.", "", "", "");
        ChangeView change = change(1, "feat: add export", TestCategories.FEATURE, false, 1, summary, Map.of(
                "operator", new AudienceNarrative(Relevance.BRIEF, "Exports run <async>.", "Watch the queue."),
                "end_user", AudienceNarrative.of("Download any table.")));
        String template = "- **{{headline}}** {{relevance}}{{#brief}} brief{{/brief}}{{#narrative}} — {{.}}{{/narrative}}";

        assertThat(ReleaseNoteDigest.render("1.0.0", null, List.of(change), template, "operator", "en"))
                .contains("- **Exports run \\<async>.** brief brief — Watch the queue.");
        assertThat(ReleaseNoteDigest.render("1.0.0", null, List.of(change), template, "end_user", "en"))
                .contains("- **Tables export as CSV.** full — Download any table.");
        assertThat(ReleaseNoteDigest.render("1.0.0", null, List.of(change), template, "contributor", "en"))
                .contains("- **Tables export as CSV.** full");
    }

    @Test
    void sectionsCustomCategoriesByTheirGroup() {
        CategoryRef security = new CategoryRef("SECURITY", "Security", CategoryGroup.FIX);
        CategoryRef legal = new CategoryRef("LEGAL", "Legal", CategoryGroup.OTHER);
        List<ChangeView> changes = List.of(
                change(1, "Patch token check", security, false, 1, null, Map.of()),
                change(2, "Update licence notice", legal, false, 2, null, Map.of())
        );

        assertThat(ReleaseNoteDigest.render("1.0.1", null, changes, TEMPLATE, "operator", "en"))
                .contains("This release includes 2 changes: 1 bug fix and 1 other change.")
                .contains("## 🐛 Bug Fixes\n\n- Patch token check\n")
                .contains("## Other Changes\n\n- Update licence notice\n");
    }

    @Test
    void leavesOutEmptySectionsAndTheBreakingWarning() {
        List<ChangeView> changes = List.of(
                change(1, "feat: add export", TestCategories.FEATURE, false, 1, null, Map.of()),
                change(2, "fix: trim input", TestCategories.FIX, false, 2, null, Map.of())
        );

        String note = ReleaseNoteDigest.render("1.1.0", null, changes, TEMPLATE, "operator", "en");

        assertThat(note).startsWith("# Release 1.1.0\n\n## What's New\n\n"
                + "This release includes 2 changes: 1 new feature and 1 bug fix.\n\n## ✨ New Features");
        assertThat(note).doesNotContain("Breaking", "> ", "Documentation");
    }

    @Test
    void writesVietnameseLabelsForVietnamese() {
        List<ChangeView> changes = List.of(
                change(1, "feat: add export", TestCategories.FEATURE, false, 1, null, Map.of()),
                change(2, "feat!: drop v1", TestCategories.FEATURE, true, 2, null, Map.of()),
                change(3, "fix: trim", TestCategories.FIX, false, 3, null, Map.of()),
                change(4, "fix: pad", TestCategories.FIX, false, 4, null, Map.of())
        );

        String note = ReleaseNoteDigest.render("1.2.0", null, changes, TEMPLATE, "operator", "vi-VN");

        assertThat(note)
                .startsWith("# Bản phát hành 1.2.0\n\n## Có gì mới\n\n"
                        + "Bản phát hành này gồm 4 thay đổi: 1 thay đổi phá vỡ, 1 tính năng mới và 2 lỗi được sửa.\n\n"
                        + "> ⚠️ 1 thay đổi phá vỡ cần được xử lý trước khi nâng cấp.")
                .contains("## ⚠️ Thay đổi phá vỡ", "## ✨ Tính năng mới", "## 🐛 Sửa lỗi");
    }

    @Test
    void fallsBackToEnglishForOtherLanguagesAndUsesPlurals() {
        List<ChangeView> changes = List.of(
                change(1, "feat!: a", TestCategories.FEATURE, true, 1, null, Map.of()),
                change(2, "feat!: b", TestCategories.FIX, true, 2, null, Map.of())
        );

        assertThat(ReleaseNoteDigest.render("3.0.0", null, changes, TEMPLATE, "operator", "ja"))
                .contains("This release includes 2 changes: 2 breaking changes.")
                .contains("> ⚠️ 2 breaking changes require action before upgrading.");
    }

    @Test
    void usesTheSummaryAndTheAudiencesOwnNarrative() {
        NeutralSummary summary = new NeutralSummary("Tables export as CSV.", "Users asked.", "Streams rows.", "Nothing.");
        ChangeView change = change(1, "feat: add export", TestCategories.FEATURE, false, 1, summary,
                Map.of("operator", AudienceNarrative.of("Watch the export queue."), "end_user", AudienceNarrative.of("Download any table.")));
        String template = "- {{whatChanged}} ({{whyChanged}}; {{technicalDetail}}; {{migrationStep}}){{#narrative}} — {{.}}{{/narrative}}";

        assertThat(ReleaseNoteDigest.render("1.0.0", null, List.of(change), template, "end_user", "en"))
                .contains("- Tables export as CSV. (Users asked.; Streams rows.; Nothing.) — Download any table.");
        assertThat(ReleaseNoteDigest.render("1.0.0", null, List.of(change), template, "contributor", "en"))
                .contains("- Tables export as CSV. (Users asked.; Streams rows.; Nothing.)\n");
    }

    @Test
    void namesEachChangeTheWayItsSourceDoes() {
        String template = "- {{reference}} ({{pullRequestNumber}})";
        List<ChangeView> changes = List.of(
                change(1, "feat: github", TestCategories.FEATURE, false, 1, null, Map.of(), List.of(),
                        "https://github.com/acme/app/pull/1", SourceType.GITHUB),
                change(1, "feat: gitlab", TestCategories.FEATURE, false, 2, null, Map.of(), List.of(),
                        "https://gitlab.com/acme/app/-/merge_requests/1", SourceType.GITLAB),
                change(7, "APP-7: jira", TestCategories.FEATURE, false, 3, null, Map.of(), List.of(),
                        "https://acme.atlassian.net/browse/APP-7", SourceType.JIRA),
                change(123, "linear", TestCategories.FEATURE, false, 4, null, Map.of(), List.of(),
                        "https://linear.app/acme/issue/ENG-123/export-tables", SourceType.LINEAR),
                // An address that does not carry the key, and a change older than source types.
                change(9, "linear elsewhere", TestCategories.FEATURE, false, 5, null, Map.of(), List.of(),
                        "https://linear.app/acme/view/9", SourceType.LINEAR),
                change(10, "legacy", TestCategories.FEATURE, false, 6, null, Map.of(), List.of(),
                        "https://github.com/acme/app/pull/10", null)
        );

        assertThat(ReleaseNoteDigest.render("1.0.0", null, changes, template, "operator", "en"))
                .contains("- #1 (1)\n\n- !1 (1)\n\n- APP-7 (7)\n\n- ENG-123 (123)\n\n- #9 (9)\n\n- #10 (10)");
    }

    @Test
    void escapesAPullRequestTitleUsedInsteadOfASummary() {
        ChangeView change = change(1, "feat: add *bold* [link](x) <b>", TestCategories.FEATURE, false, 1, null, Map.of());

        assertThat(ReleaseNoteDigest.render("1.0.0_rc", null, List.of(change), TEMPLATE, "operator", "en"))
                .startsWith("# Release 1.0.0\\_rc")
                .contains("- add \\*bold\\* \\[link\\](x) \\<b\\>");
    }

    @Test
    void escapesWhatTheTrackerWroteAboutALinkedIssue() {
        ChangeView change = change(1, "feat: export", TestCategories.FEATURE, false, 1, null, Map.of(),
                List.of(new LinkedIssue("APP-7", "Export *all* [tables](x)", "secret description", "Story_type",
                        "<Done>", "https://acme.atlassian.net/browse/APP-7")));
        String template = "{{whatChanged}}{{#linkedIssues}} [{{key}}]({{url}}) {{title}} {{type}} {{status}}"
                + "{{/linkedIssues}}";

        assertThat(ReleaseNoteDigest.render("1.0.0", null, List.of(change), template, "operator", "en"))
                .contains("export [APP-7](https://acme.atlassian.net/browse/APP-7) "
                        + "Export \\*all\\* \\[tables\\](x) Story\\_type \\<Done\\>\n");
    }

    @Test
    void escapesRawHtmlInSummaryAndNarrativeSoAPlaceholderStaysVisible() {
        NeutralSummary summary = new NeutralSummary(
                "Backups are stored under BACKUP_DIR/<target-id>/<execution-id>/.",
                "<details>Paths were flat.</details>",
                "Run `dbvault verify --backup-id <id>`.",
                "Nothing.");
        ChangeView change = change(1, "feat: layout", TestCategories.FEATURE, false, 1, summary,
                Map.of("operator", AudienceNarrative.of("Watch <target-id> directories.")));
        String template = "- {{whatChanged}}\n\n{{whyChanged}}\n\n{{technicalDetail}}{{#narrative}} — {{.}}{{/narrative}}";

        String note = ReleaseNoteDigest.render("1.0.0", null, List.of(change), template, "operator", "en");

        assertThat(note)
                .contains("- Backups are stored under BACKUP_DIR/\\<target-id>/\\<execution-id>/.")
                .contains("\\<details>Paths were flat.\\</details>")
                .contains("Run `dbvault verify --backup-id <id>`.")
                .contains("— Watch \\<target-id> directories.");
        assertThat(rawHtml(note)).isEmpty();
        assertThat(text(note)).contains("BACKUP_DIR/<target-id>/<execution-id>/", "Watch <target-id> directories.");
    }

    @Test
    void escapesRawHtmlInTheNarrativeOfAChangeWithoutASummary() {
        ChangeView change = change(1, "feat: layout", TestCategories.FEATURE, false, 1, null,
                Map.of("operator", AudienceNarrative.of("Watch <target-id> directories.")));

        String note = ReleaseNoteDigest.render("1.0.0", null, List.of(change), TEMPLATE, "operator", "en");

        assertThat(note).contains("- layout — Watch \\<target-id> directories.");
        assertThat(rawHtml(note)).isEmpty();
    }

    @Test
    void demotesHeadingsInsideItemsButNotInCode() {
        assertThat(ReleaseNoteDigest.demoteHeadings("## What changed\ntext\n### Detail\n##### five\n#tag\n```\n# code\n```\n# top"))
                .isEqualTo("#### What changed\ntext\n##### Detail\n###### five\n#tag\n```\n# code\n```\n### top");

        ChangeView change = change(1, "feat: export", TestCategories.FEATURE, false, 1,
                new NeutralSummary("## Export", "", "", ""), Map.of());
        assertThat(ReleaseNoteDigest.render("1.0.0", null, List.of(change), "{{whatChanged}}", "operator", "en"))
                .contains("## ✨ New Features\n\n#### Export\n");
    }

    @Test
    void refusesAReleaseWithoutChanges() {
        assertThatThrownBy(() -> ReleaseNoteDigest.render("1.0.0", null, List.of(), TEMPLATE, "operator", "en"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** The raw HTML a CommonMark reader, GitHub's among them, would find in the note. */
    private static List<String> rawHtml(String markdown) {
        List<String> html = new ArrayList<>();
        Parser.builder().build().parse(markdown).accept(new AbstractVisitor() {
            @Override
            public void visit(HtmlInline node) {
                html.add(node.getLiteral());
            }

            @Override
            public void visit(HtmlBlock node) {
                html.add(node.getLiteral());
            }
        });
        return html;
    }

    /** The text the note shows, without its markup. */
    private static String text(String markdown) {
        StringBuilder text = new StringBuilder();
        Parser.builder().build().parse(markdown).accept(new AbstractVisitor() {
            @Override
            public void visit(Text node) {
                text.append(node.getLiteral());
            }
        });
        return text.toString();
    }

    private static final String FOOTER = """
            {{#compareUrl}}
            **Compare:** [{{previousVersion}}...{{version}}]({{compareUrl}})
            {{/compareUrl}}

            {{#upgradeGuideUrl}}
            **Upgrade guide:** [Read before upgrading]({{upgradeGuideUrl}})
            {{/upgradeGuideUrl}}
            """;

    private static List<ChangeView> securityRelease() {
        return List.of(
                change(1, "feat!: rename the API", TestCategories.FEATURE, true, 1, null, Map.of()),
                secure(2, "fix: derive a key per target", TestCategories.FIX, false, 2, Map.of()),
                secure(3, "feat!: remove CSRF protection from the API", TestCategories.FEATURE, true, 3, Map.of()),
                change(4, "feat: add export", TestCategories.FEATURE, false, 4, null, Map.of())
        );
    }

    @Test
    void putsSecurityChangesAfterBreakingOnesAndEachChangeInOneSection() {
        assertThat(ReleaseNoteDigest.render("2.0.0", null, securityRelease(), TEMPLATE, "operator", "en")).isEqualTo("""
                # Release 2.0.0

                ## What's New

                This release includes 4 changes: 2 breaking changes, 1 security change, and 1 new feature.

                > ⚠️ 2 breaking changes require action before upgrading.

                ## ⚠️ Breaking Changes

                - rename the API

                - 🔒 Security: remove CSRF protection from the API

                ## 🔒 Security

                - derive a key per target

                ## ✨ New Features

                - add export
                """);
    }

    @Test
    void writesTheSecuritySectionAndMarkerInVietnamese() {
        String note = ReleaseNoteDigest.render("2.0.0", null, securityRelease(), TEMPLATE, "operator", "vi");

        assertThat(note)
                .contains("Bản phát hành này gồm 4 thay đổi: 2 thay đổi phá vỡ, 1 thay đổi bảo mật và 1 tính năng mới.")
                .contains("\n## 🔒 Bảo mật\n\n- derive a key per target\n")
                .contains("- 🔒 Bảo mật: remove CSRF protection from the API");
    }

    @Test
    void marksABreakingSecurityChangeWhateverTheTemplatePrints() {
        List<ChangeView> changes = List.of(
                secure(3, "feat!: remove CSRF protection from the API", TestCategories.FEATURE, true, 3, Map.of()));

        assertThat(ReleaseNoteDigest.render("2.0.0", null, changes, "{{whatChanged}}", "operator", "en"))
                .contains("## ⚠️ Breaking Changes\n\n🔒 Security: remove CSRF protection from the API\n");
        assertThat(ReleaseNoteDigest.render("2.0.0", null, changes, "1. **{{whatChanged}}**", "operator", "en"))
                .contains("\n1. 🔒 Security: **remove CSRF protection from the API**\n");
    }

    @Test
    void aSecurityChangeAlwaysConcernsOperatorsContributorsAndSupport() {
        Map<String, AudienceNarrative> none = Map.of(
                "operator", new AudienceNarrative(Relevance.NONE, "", ""),
                "contributor", new AudienceNarrative(Relevance.NONE, "", ""),
                "support", new AudienceNarrative(Relevance.NONE, "", ""),
                "end_user", new AudienceNarrative(Relevance.NONE, "", ""),
                "dba", new AudienceNarrative(Relevance.NONE, "", "")
        );
        List<ChangeView> changes = List.of(
                secure(2, "fix: derive a key per target", TestCategories.FIX, false, 2, none),
                change(4, "feat: add export", TestCategories.FEATURE, false, 4, null, Map.of())
        );

        for (String upgrader : List.of("operator", "contributor", "support")) {
            assertThat(ReleaseNoteDigest.render("2.0.0", null, changes, "- {{whatChanged}} ({{relevance}})", upgrader, "en"))
                    .contains("## 🔒 Security\n\n- derive a key per target (brief)\n")
                    .contains("This release includes 2 changes: 1 security change and 1 new feature.");
        }
        for (String other : List.of("end_user", "dba")) {
            assertThat(ReleaseNoteDigest.render("2.0.0", null, changes, TEMPLATE, other, "en"))
                    .doesNotContain("Security")
                    .contains("This release includes 1 change: 1 new feature.");
        }
    }

    @Test
    void aBreakingSecurityChangeIsInEveryAudiencesNote() {
        Map<String, AudienceNarrative> none = Map.of("end_user", new AudienceNarrative(Relevance.NONE, "", ""));
        List<ChangeView> changes = List.of(
                secure(3, "feat!: remove CSRF protection from the API", TestCategories.FEATURE, true, 3, none));

        assertThat(ReleaseNoteDigest.render("2.0.0", null, changes, "- {{whatChanged}} ({{relevance}})", "end_user", "en"))
                .contains("- 🔒 Security: remove CSRF protection from the API (brief)")
                .contains("This release includes 1 change: 1 breaking change.");
    }

    @Test
    void linksTheBreakingWarningToTheUpgradeGuide() {
        List<ChangeView> changes = List.of(change(1, "feat!: rename the API", TestCategories.FEATURE, true, 1, null, Map.of()));
        ReleaseLinks links = new ReleaseLinks("2.0.0", "", "", "https://docs.example.com/upgrade_(2.0)?from=1#steps", "");

        assertThat(ReleaseNoteDigest.render(null, changes, TEMPLATE, "", links, "operator", "en")).contains(
                "> ⚠️ 1 breaking change requires action before upgrading: [read the upgrade guide]"
                        + "(https://docs.example.com/upgrade_%282.0%29?from=1#steps).\n");
        assertThat(ReleaseNoteDigest.render(null, changes, TEMPLATE, "", links, "operator", "vi")).contains(
                "> ⚠️ 1 thay đổi phá vỡ cần được xử lý trước khi nâng cấp: [đọc hướng dẫn nâng cấp]"
                        + "(https://docs.example.com/upgrade_%282.0%29?from=1#steps).\n");
        assertThat(ReleaseNoteDigest.render("2.0.0", null, changes, TEMPLATE, "operator", "en"))
                .contains("> ⚠️ 1 breaking change requires action before upgrading.\n");
    }

    @Test
    void endsTheNoteWithTheFooterRenderedOnce() {
        List<ChangeView> changes = List.of(
                change(1, "feat: add export", TestCategories.FEATURE, false, 1, null, Map.of()),
                change(2, "fix: handle empty tables", TestCategories.FIX, false, 2, null, Map.of())
        );
        ReleaseLinks links = new ReleaseLinks("v2.0.0", "v1.9.0", "https://github.com/acme/app/compare/v1.9.0...v2.0.0",
                "https://docs.example.com/upgrade", "https://github.com/acme/app/releases/tag/v2.0.0");

        assertThat(ReleaseNoteDigest.render(null, changes, TEMPLATE, FOOTER, links, "operator", "en")).endsWith("""
                ## 🐛 Bug Fixes

                - handle empty tables

                **Compare:** [v1.9.0...v2.0.0](https://github.com/acme/app/compare/v1.9.0...v2.0.0)

                **Upgrade guide:** [Read before upgrading](https://docs.example.com/upgrade)
                """);
    }

    @Test
    void aFooterLineDisappearsWithoutItsLinkAndABlankFooterAddsNothing() {
        List<ChangeView> changes = List.of(change(1, "feat: add export", TestCategories.FEATURE, false, 1, null, Map.of()));
        ReleaseLinks first = new ReleaseLinks("v1.0.0", "", "", "https://docs.example.com/upgrade",
                "https://github.com/acme/app/releases/tag/v1.0.0");
        ReleaseLinks nothing = new ReleaseLinks("v1.0.0", "", "", "", "");

        assertThat(ReleaseNoteDigest.render(null, changes, TEMPLATE, FOOTER, first, "operator", "en"))
                .endsWith("- add export\n\n**Upgrade guide:** [Read before upgrading](https://docs.example.com/upgrade)\n")
                .doesNotContain("Compare");
        assertThat(ReleaseNoteDigest.render(null, changes, TEMPLATE, FOOTER, nothing, "operator", "en"))
                .isEqualTo(ReleaseNoteDigest.render("v1.0.0", null, changes, TEMPLATE, "operator", "en"))
                .endsWith("- add export\n");
        assertThat(ReleaseNoteDigest.render(null, changes, TEMPLATE, "  \n", first, "operator", "en"))
                .endsWith("- add export\n");
    }

    @Test
    void aFooterFollowsTheLineOfANoteNothingConcernsAndEscapesTheVersions() {
        List<ChangeView> changes = List.of(change(1, "feat: add export", TestCategories.FEATURE, false, 1, null,
                Map.of("end_user", new AudienceNarrative(Relevance.NONE, "", ""))));
        ReleaseLinks links = new ReleaseLinks("v2_0*", "v1_9", "https://github.com/acme/app/compare/v1_9...v2_0%2A", "", "");

        assertThat(ReleaseNoteDigest.render(null, changes, TEMPLATE, FOOTER, links, "end_user", "en")).isEqualTo("""
                # Release v2\\_0\\*

                Nothing in this release concerns this audience.

                **Compare:** [v1\\_9...v2\\_0\\*](https://github.com/acme/app/compare/v1_9...v2_0%2A)
                """);
    }

    private static ChangeView change(
            int number,
            String title,
            CategoryRef category,
            boolean breaking,
            int minute,
            NeutralSummary summary,
            Map<String, AudienceNarrative> narratives
    ) {
        return change(number, title, category, breaking, minute, summary, narratives, List.of());
    }

    private static ChangeView change(
            int number,
            String title,
            CategoryRef category,
            boolean breaking,
            int minute,
            NeutralSummary summary,
            Map<String, AudienceNarrative> narratives,
            List<LinkedIssue> linkedIssues
    ) {
        return change(number, title, category, breaking, minute, summary, narratives, linkedIssues,
                "https://github.com/acme/releaseflow/pull/" + number, null);
    }

    private static ChangeView change(
            int number,
            String title,
            CategoryRef category,
            boolean breaking,
            int minute,
            NeutralSummary summary,
            Map<String, AudienceNarrative> narratives,
            List<LinkedIssue> linkedIssues,
            String url,
            SourceType sourceType
    ) {
        return change(number, title, category, breaking, false, minute, summary, narratives, linkedIssues, url, sourceType);
    }

    // A change whose security flag a rule, the AI, or a person set.
    private static ChangeView secure(
            int number,
            String title,
            CategoryRef category,
            boolean breaking,
            int minute,
            Map<String, AudienceNarrative> narratives
    ) {
        return change(number, title, category, breaking, true, minute, null, narratives, List.of(),
                "https://github.com/acme/releaseflow/pull/" + number, null);
    }

    private static ChangeView change(
            int number,
            String title,
            CategoryRef category,
            boolean breaking,
            boolean security,
            int minute,
            NeutralSummary summary,
            Map<String, AudienceNarrative> narratives,
            List<LinkedIssue> linkedIssues,
            String url,
            SourceType sourceType
    ) {
        return new ChangeView(
                UUID.randomUUID(),
                number,
                title,
                null,
                "mai-dev",
                List.of(),
                "main",
                "a".repeat(40),
                Instant.parse("2026-09-01T10:00:00Z").plusSeconds(minute * 60L),
                url,
                category.code(),
                category.displayName(),
                category.group(),
                breaking,
                security,
                false,
                List.of(),
                ClassificationSource.RULES,
                summary == null ? AiStatus.NOT_REQUESTED : AiStatus.SUCCEEDED,
                null,
                null,
                false,
                null,
                null,
                null,
                ProcessingStatus.COMPLETED,
                ChangedFileStatus.COLLECTED,
                null,
                linkedIssues,
                List.of(),
                List.of(),
                summary,
                summary == null ? null : "en",
                null,
                narratives,
                null,
                null,
                null,
                null,
                null,
                sourceType,
                null
        );
    }
}
