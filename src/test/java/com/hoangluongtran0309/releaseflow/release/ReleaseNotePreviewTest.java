package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.category.CategoryRef;
import com.hoangluongtran0309.releaseflow.change.AiStatus;
import com.hoangluongtran0309.releaseflow.change.ChangeView;
import com.hoangluongtran0309.releaseflow.change.ChangedFileStatus;
import com.hoangluongtran0309.releaseflow.change.ClassificationSource;
import com.hoangluongtran0309.releaseflow.change.ContextAssessment;
import com.hoangluongtran0309.releaseflow.change.ContextStatus;
import com.hoangluongtran0309.releaseflow.change.NeutralSummary;
import com.hoangluongtran0309.releaseflow.change.ProcessingStatus;
import com.hoangluongtran0309.releaseflow.support.TestCategories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseNotePreviewTest {

    @Test
    void listsBreakingChangesFirstThenCategoriesInAFixedOrder() {
        List<ReleaseNoteSection> sections = ReleaseNotePreview.sections(List.of(
                change(4, "chore: bump dependencies", TestCategories.MAINTENANCE, false, 4),
                change(2, "fix: handle empty tables", TestCategories.FIX, false, 2),
                change(3, "feat(api)!: drop the v1 export", TestCategories.FEATURE, true, 3),
                change(1, "feat(ui): add the inbox", TestCategories.FEATURE, false, 1),
                change(5, "docs: explain releases", TestCategories.DOCUMENTATION, false, 5),
                change(6, "perf: cache previews", TestCategories.PERFORMANCE, false, 6)
        ));

        assertThat(sections).extracting(ReleaseNoteSection::title).containsExactly(
                "Breaking changes", "Features", "Fixes", "Performance", "Documentation", "Maintenance"
        );
        assertThat(sections.getFirst().items()).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("drop the v1 export");
            assertThat(item.category()).isEqualTo("FEATURE");
            assertThat(item.categoryLabel()).isEqualTo("Feature");
            assertThat(item.pullRequestNumber()).isEqualTo(3);
            assertThat(item.url()).isEqualTo("https://github.com/acme/releaseflow/pull/3");
        });
        assertThat(sections.get(1).items()).extracting(ReleaseNoteItem::title).containsExactly("add the inbox");
    }

    @Test
    void ordersItemsByMergeTimeAndOmitsEmptySections() {
        List<ReleaseNoteSection> sections = ReleaseNotePreview.sections(List.of(
                change(9, "fix: later fix", TestCategories.FIX, false, 20),
                change(8, "fix: earlier fix", TestCategories.FIX, false, 10)
        ));

        assertThat(sections).singleElement().satisfies(section -> {
            assertThat(section.title()).isEqualTo("Fixes");
            assertThat(section.items()).extracting(ReleaseNoteItem::title).containsExactly("earlier fix", "later fix");
        });
        assertThat(ReleaseNotePreview.sections(List.of())).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "feat: add CSV export | add CSV export",
            "FIX(api)!:   trim input | trim input",
            "  docs(readme): typo | typo",
            "Improve the exporter | Improve the exporter",
            "feature: add export | feature: add export",
            "Hotfix: login | Hotfix: login",
            "feat: | feat:"
    })
    void removesOnlyConventionalCommitPrefixes(String title, String expected) {
        assertThat(ReleaseNotePreview.displayTitle(title)).isEqualTo(expected);
    }

    // A title is all a note has of a change without a summary (ADR-0035).
    @ParameterizedTest
    @CsvSource(delimiterString = "|", value = {
            "fix                                   | true",
            "fix: typo                             | true",
            "update deps                           | true",
            "chore(deps): bump DBV-12 #28 !9       | true",
            "Backup scheduling OPS-7               | true",
            "feat: add table export                | false",
            "Fix login redirect                    | false",
            "Toàn bộ console tiếng Việt            | false"
    })
    void aChangeWithoutASummaryNeedsOneWhenItsTitleHasFewerThanThreeWords(String title, boolean needed) {
        ChangeView change = change(1, title, TestCategories.FIX, false, 1);

        assertThat(ReleaseNotePreview.needsSummary(change)).isEqualTo(needed);
        // Without a description, an AI summary only restates the title, so it changes nothing.
        assertThat(ReleaseNotePreview.needsSummary(change.withContent(
                new NeutralSummary("Fixes a typo in the backup guide.", "", "", ""), Map.of(), "en"))).isEqualTo(needed);
    }

    private static ChangeView change(int number, String title, CategoryRef category, boolean breaking, int minute) {
        return change(number, title, category, breaking, minute, null, null, null, null);
    }

    // An AI can only restate a bare title it was given nothing else about (ADR-0035).
    @ParameterizedTest
    @CsvSource(delimiterString = "|", nullValues = "null", value = {
            "null                                                         | 90 | false | true",
            "'<!-- Describe your change. -->'                             | 90 | false | true",
            "Sizes now read as 1.2 MB instead of a raw count of bytes.     | 90 | false | false",
            "Sizes now read as 1.2 MB instead of a raw count of bytes.     | 30 | false | true",
            "null                                                         | 90 | true  | false"
    })
    void anAiSummaryOfABareTitleNeedsADescriptionToWorkFrom(String description, int score, boolean byPerson, boolean needed) {
        ContextAssessment context = new ContextAssessment(score,
                score < 60 ? ContextStatus.INSUFFICIENT : ContextStatus.SUFFICIENT, List.of());
        ChangeView change = change(28, "fix", TestCategories.FIX, false, 1, description,
                new NeutralSummary("A fix was made in the codebase.", "", "", ""),
                byPerson ? Instant.parse("2026-10-20T08:00:00Z") : null, context);

        assertThat(ReleaseNotePreview.needsSummary(change)).isEqualTo(needed);
        // A title long enough to stand alone needs nothing more, whatever the AI had.
        assertThat(ReleaseNotePreview.needsSummary(change(29, "fix: show readable sizes", TestCategories.FIX, false, 1,
                description, null, null, context))).isFalse();
    }

    private static ChangeView change(
            int number,
            String title,
            CategoryRef category,
            boolean breaking,
            int minute,
            String description,
            NeutralSummary summary,
            Instant summaryEditedAt,
            ContextAssessment context
    ) {
        return new ChangeView(
                UUID.randomUUID(),
                number,
                title,
                description,
                "mai-dev",
                List.of(),
                "main",
                "a".repeat(40),
                Instant.parse("2026-09-01T10:00:00Z").plusSeconds(minute * 60L),
                "https://github.com/acme/releaseflow/pull/" + number,
                category.code(),
                category.displayName(),
                category.group(),
                breaking,
                false,
                false,
                List.of(),
                ClassificationSource.RULES,
                AiStatus.NOT_REQUESTED,
                null,
                null,
                false,
                null,
                null,
                null,
                ProcessingStatus.COMPLETED,
                ChangedFileStatus.COLLECTED,
                null,
                List.of(),
                List.of(),
                List.of(),
                summary,
                null,
                null,
                Map.of(),
                summaryEditedAt == null ? null : "Mai Tran",
                summaryEditedAt,
                context,
                null,
                null,
                null,
                null
        );
    }
}
