package com.hoangluongtran0309.releaseflow.change;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class IssueKeysTest {

    @Test
    void findsTheTrackersKeysInFirstSeenOrderOnce() {
        IssueKeys.Evidence evidence = new IssueKeys.Evidence(
                "feat: export (APP-12)",
                "Closes app-7 and APP-12.",
                "main",
                "feature/APP-3",
                List.of("Refs OPS-9 and APP-7", "APP-21: fix the export")
        );

        assertThat(IssueKeys.extract("APP", evidence)).containsExactly("APP-12", "APP-7", "APP-3", "APP-21");
        assertThat(IssueKeys.extract("OPS", evidence)).containsExactly("OPS-9");
    }

    // ADR-0019's guard, kept in a title and a target branch: a key followed by a hyphen is not taken.
    @ParameterizedTest
    @ValueSource(strings = {"feature/APP-3-export", "XAPP-9", "APP-99x", "APP-0", "APP-", "APP_1", "APP-1-2", "MYAPP-4",
            "APP-3-export", "APP-3_export"})
    void aTitleOrTargetBranchNamesOnlyAStandaloneKey(String text) {
        assertThat(IssueKeys.extract("APP", new IssueKeys.Evidence(text, null, null, null, List.of()))).isEmpty();
        assertThat(IssueKeys.extract("APP", new IssueKeys.Evidence(null, null, text, null, List.of()))).isEmpty();
    }

    // ADR-0041: inside the branch a change was merged from, a key may be followed by -, /, or _.
    @ParameterizedTest
    @ValueSource(strings = {"feature/DBV-47-retention-preview", "DBV-47/preview", "bugfix/dbv-47_restore", "DBV-47",
            "release/DBV-47.1"})
    void aHeadBranchNamesAKeyFollowedByAHyphenSlashOrUnderscore(String branch) {
        assertThat(IssueKeys.extract("DBV", branch(branch))).containsExactly("DBV-47");
    }

    @ParameterizedTest
    @ValueSource(strings = {"feature/XDBV-47-x", "feature/DBV-047-x", "feature/DBV-47a", "DBV-4x/y", "feature/OPS-47-x",
            "release/2026-DBV-47x", "feature/dbv47-x", "deps/DBV-47b-1"})
    void aHeadBranchStillNamesNothingThatOnlyLooksLikeAKey(String branch) {
        assertThat(IssueKeys.extract("DBV", branch(branch))).isEmpty();
    }

    // ADR-0041: in a description or a commit message, a hyphen and a lowercase word may follow a tracker's key.
    @Test
    void aDescriptionOrCommitNamesAKeyFollowedByAHyphenAndALowercaseWord() {
        assertThat(IssueKeys.extract("DBV", description("\n\nSpec: DBV-12-quartz\n"))).containsExactly("DBV-12");
        assertThat(IssueKeys.extract("DBV", commit("DBV-12-quartz: schedule backups"))).containsExactly("DBV-12");
        assertThat(IssueKeys.extract("OPS", description("Follows OPS-7-rotation."))).containsExactly("OPS-7");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DBV-12-3", "DBV-12-Q", "DBV-12-", "DBV-12_quartz", "XDBV-12-quartz", "DBV-012-quartz"})
    void aDescriptionOrCommitKeepsTheStrictRuleOtherwise(String text) {
        assertThat(IssueKeys.extract("DBV", description(text))).isEmpty();
        assertThat(IssueKeys.extract("DBV", commit(text))).isEmpty();
    }

    // The relaxed rule is only ever applied to a tracker's own key.
    @Test
    void aWordShapedLikeAKeyOfNoTrackerNeverLinks() {
        IssueKeys.Evidence evidence = new IssueKeys.Evidence(
                "Timestamps are ISO-8601-compliant",
                "All times are ISO-8601-compliant, as RFC-3339-style strings.",
                null,
                "feature/ISO-8601-dates",
                List.of("Use ISO-8601-compliant times")
        );

        assertThat(IssueKeys.extract("DBV", evidence)).isEmpty();
        assertThat(IssueKeys.extract("OPS", evidence)).isEmpty();
    }

    @Test
    void stopsAtTenKeysAcrossAllEvidence() {
        IssueKeys.Evidence evidence = new IssueKeys.Evidence(
                String.join(" ", IntStream.rangeClosed(1, 6).mapToObj(n -> "APP-" + n).toList()),
                String.join(" ", IntStream.rangeClosed(7, 15).mapToObj(n -> "APP-" + n).toList()),
                null,
                null,
                List.of()
        );

        assertThat(IssueKeys.extract("APP", evidence))
                .hasSize(IssueKeys.MAX_KEYS)
                .startsWith("APP-1")
                .endsWith("APP-10");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "1APP", "AP-P", "A.B"})
    void aMalformedTrackerKeyFindsNothing(String trackerKey) {
        assertThat(IssueKeys.extract(trackerKey, description("1APP-1 AP-P-1 A.B-1 -1"))).isEmpty();
    }

    @Test
    void quotesTheTrackerKeyAndSkipsMissingEvidence() {
        List<String> commits = new ArrayList<>();
        commits.add("A_B-5 AXB-6");

        assertThat(IssueKeys.extract("a_b", new IssueKeys.Evidence(null, null, null, null, commits))).containsExactly("A_B-5");
        assertThat(IssueKeys.extract(null, new IssueKeys.Evidence("A_B-5", null, null, null, null))).isEmpty();
    }

    private static IssueKeys.Evidence branch(String branch) {
        return new IssueKeys.Evidence(null, null, null, branch, List.of());
    }

    private static IssueKeys.Evidence description(String description) {
        return new IssueKeys.Evidence(null, description, null, null, List.of());
    }

    private static IssueKeys.Evidence commit(String message) {
        return new IssueKeys.Evidence(null, null, null, null, List.of(message));
    }
}
