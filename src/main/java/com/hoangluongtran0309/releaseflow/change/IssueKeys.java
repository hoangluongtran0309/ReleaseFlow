package com.hoangluongtran0309.releaseflow.change;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the issue keys of one tracker, a Jira project or a Linear team, in whatever a change
 * says about itself. Only that tracker's keys count, and only a handful of them: a change
 * that names a hundred issues is not better evidence than one that names three.
 *
 * <p>Where a key may stand depends on where it was written (ADR-0041). Everywhere, nothing
 * that is part of the key's own token may come before it, and a number has no leading zero.
 * After it:
 * <ul>
 *     <li>in a title or a target branch, nothing that continues the token, a hyphen
 *     included, as the old project required: {@code APP-3-export} names nothing;</li>
 *     <li>in a branch the change was merged from, a {@code -}, {@code /}, or {@code _} may
 *     follow: {@code feature/APP-3-export} names {@code APP-3};</li>
 *     <li>in a description or a commit message, a hyphen and a lowercase word may follow as
 *     well: {@code Spec: APP-12-quartz} names {@code APP-12}, {@code APP-12-3} does not.</li>
 * </ul>
 * The relaxed rules apply only to the trackers' own keys, which are the only ones looked
 * for, so {@code ISO-8601-compliant} never names anything unless a tracker's key is ISO.
 */
final class IssueKeys {

    static final int MAX_KEYS = 10;
    private static final Pattern TRACKER_KEY = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final String BEFORE = "(?<![A-Za-z0-9_])";
    private static final String STRICT = "(?![A-Za-z0-9_-])";
    private static final String IN_BRANCH = "(?![A-Za-z0-9])";
    private static final String IN_PROSE = "(?:(?![A-Za-z0-9_-])|(?=-[a-z]))";

    /** What a change says about itself, by where it says it. */
    record Evidence(String title, String description, String targetBranch, String headBranch, List<String> commits) {

        Evidence {
            commits = commits == null ? List.of() : List.copyOf(commits);
        }
    }

    private IssueKeys() {
    }

    /** The tracker's keys in the change's evidence, in the order they are first seen, at most {@value #MAX_KEYS}. */
    static List<String> extract(String trackerKey, Evidence evidence) {
        String tracker = trackerKey == null ? "" : trackerKey.strip().toUpperCase(Locale.ROOT);
        if (!TRACKER_KEY.matcher(tracker).matches()) {
            return List.of();
        }
        Pattern strict = pattern(tracker, STRICT);
        Pattern inProse = pattern(tracker, IN_PROSE);
        List<Text> texts = new ArrayList<>();
        texts.add(new Text(evidence.title(), strict));
        texts.add(new Text(evidence.description(), inProse));
        texts.add(new Text(evidence.targetBranch(), strict));
        texts.add(new Text(evidence.headBranch(), pattern(tracker, IN_BRANCH)));
        evidence.commits().forEach(commit -> texts.add(new Text(commit, inProse)));

        Set<String> keys = new LinkedHashSet<>();
        for (Text text : texts) {
            if (text.value() == null) {
                continue;
            }
            Matcher matcher = text.pattern().matcher(text.value());
            while (matcher.find() && keys.size() < MAX_KEYS) {
                keys.add(matcher.group(1).toUpperCase(Locale.ROOT));
            }
            if (keys.size() == MAX_KEYS) {
                break;
            }
        }
        return List.copyOf(keys);
    }

    private static Pattern pattern(String tracker, String after) {
        return Pattern.compile(BEFORE + "((?i:" + Pattern.quote(tracker) + ")-[1-9][0-9]*)" + after);
    }

    private record Text(String value, Pattern pattern) {
    }
}
