package com.hoangluongtran0309.releaseflow.release;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The difference between two texts, line by line, for a person to read before saving a
 * correction (ADR-0046): every line of the longest run the two share is kept, and the rest
 * is removed from the earlier text or added by the later one. A note is a few hundred lines,
 * so the quadratic table is small.
 */
public final class LineDiff {

    /** Above this many line pairs, the table would be too large; the difference is then whole. */
    static final long MAX_CELLS = 4_000_000L;

    private LineDiff() {
    }

    /** One line of the difference. */
    public record Line(Kind kind, String text) {

        public boolean isAdded() {
            return kind == Kind.ADDED;
        }

        public boolean isRemoved() {
            return kind == Kind.REMOVED;
        }
    }

    public enum Kind {
        SAME,
        REMOVED,
        ADDED
    }

    /** The lines of {@code before} and {@code after}, in order, each kept, removed, or added. */
    public static List<Line> between(String before, String after) {
        List<String> left = lines(before);
        List<String> right = lines(after);
        if ((long) left.size() * right.size() > MAX_CELLS) {
            return whole(left, right);
        }
        int[][] common = new int[left.size() + 1][right.size() + 1];
        for (int i = left.size() - 1; i >= 0; i--) {
            for (int j = right.size() - 1; j >= 0; j--) {
                common[i][j] = left.get(i).equals(right.get(j))
                        ? common[i + 1][j + 1] + 1
                        : Math.max(common[i + 1][j], common[i][j + 1]);
            }
        }
        List<Line> diff = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < left.size() && j < right.size()) {
            if (left.get(i).equals(right.get(j))) {
                diff.add(new Line(Kind.SAME, left.get(i++)));
                j++;
            } else if (common[i + 1][j] >= common[i][j + 1]) {
                diff.add(new Line(Kind.REMOVED, left.get(i++)));
            } else {
                diff.add(new Line(Kind.ADDED, right.get(j++)));
            }
        }
        while (i < left.size()) {
            diff.add(new Line(Kind.REMOVED, left.get(i++)));
        }
        while (j < right.size()) {
            diff.add(new Line(Kind.ADDED, right.get(j++)));
        }
        return Collections.unmodifiableList(diff);
    }

    /** Whether the two texts differ at all. */
    public static boolean changes(List<Line> diff) {
        return diff.stream().anyMatch(line -> line.kind() != Kind.SAME);
    }

    private static List<Line> whole(List<String> left, List<String> right) {
        List<Line> diff = new ArrayList<>();
        left.forEach(line -> diff.add(new Line(Kind.REMOVED, line)));
        right.forEach(line -> diff.add(new Line(Kind.ADDED, line)));
        return Collections.unmodifiableList(diff);
    }

    // A trailing newline ends the last line rather than starting an empty one.
    private static List<String> lines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        String body = text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
        return List.of(body.split("\n", -1));
    }
}
