package com.hoangluongtran0309.releaseflow.release;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hoangluongtran0309.releaseflow.release.LineDiff.Kind.ADDED;
import static com.hoangluongtran0309.releaseflow.release.LineDiff.Kind.REMOVED;
import static com.hoangluongtran0309.releaseflow.release.LineDiff.Kind.SAME;
import static org.assertj.core.api.Assertions.assertThat;

class LineDiffTest {

    @Test
    void keepsEveryLineOfTheSameText() {
        List<LineDiff.Line> diff = LineDiff.between("# 1.2.0\n\nRun `dbvault-cli`.\n", "# 1.2.0\n\nRun `dbvault-cli`.\n");

        assertThat(diff).extracting(LineDiff.Line::kind).containsExactly(SAME, SAME, SAME);
        assertThat(LineDiff.changes(diff)).isFalse();
    }

    @Test
    void showsAChangedLineAsTheOldOneRemovedAndTheNewOneAdded() {
        List<LineDiff.Line> diff = LineDiff.between(
                "# 1.2.0\n\nRestore <BACKUP-id>.\nThen verify.\n",
                "# 1.2.0\n\nRestore <backup-id>.\nThen verify.\n");

        assertThat(diff).containsExactly(
                new LineDiff.Line(SAME, "# 1.2.0"),
                new LineDiff.Line(SAME, ""),
                new LineDiff.Line(REMOVED, "Restore <BACKUP-id>."),
                new LineDiff.Line(ADDED, "Restore <backup-id>."),
                new LineDiff.Line(SAME, "Then verify."));
        assertThat(LineDiff.changes(diff)).isTrue();
    }

    @Test
    void addsAndRemovesWholeLinesAroundWhatTheTextsShare() {
        assertThat(LineDiff.between("a\nb\nc\n", "a\nc\nd\n")).containsExactly(
                new LineDiff.Line(SAME, "a"),
                new LineDiff.Line(REMOVED, "b"),
                new LineDiff.Line(SAME, "c"),
                new LineDiff.Line(ADDED, "d"));
        assertThat(LineDiff.between("", "a\n")).containsExactly(new LineDiff.Line(ADDED, "a"));
        assertThat(LineDiff.between("a\n", "")).containsExactly(new LineDiff.Line(REMOVED, "a"));
    }

    @Test
    void treatsAMissingFinalNewlineAsTheSameLastLine() {
        assertThat(LineDiff.between("a\nb", "a\nb\n")).extracting(LineDiff.Line::kind).containsExactly(SAME, SAME);
    }

    @Test
    void showsTextsTooLongToCompareAsReplacedWhole() {
        String before = "x\n".repeat(2_001);
        String after = "y\n".repeat(2_000);

        List<LineDiff.Line> diff = LineDiff.between(before, after);

        assertThat(diff).hasSize(4_001);
        assertThat(diff.getFirst().kind()).isEqualTo(REMOVED);
        assertThat(diff.getLast().kind()).isEqualTo(ADDED);
    }
}
