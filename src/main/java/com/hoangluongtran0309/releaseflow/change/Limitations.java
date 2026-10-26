package com.hoangluongtran0309.releaseflow.change;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Keeps an AI's limitations to what a change says it deliberately does not do yet
 * (ADR-0035). A sentence about the input itself — that something was "not provided" or
 * "not specified" — says nothing about the change, so it is dropped, whatever the prompt
 * asked. Only an AI answer is filtered: a person's words stay as written.
 */
final class Limitations {

    // English and Vietnamese, the languages ReleaseFlow writes its own text in.
    private static final Pattern ABOUT_THE_INPUT = Pattern.compile(
            "\\bnot (provided|specified|detailed|mentioned|described|stated|given|documented|clear)\\b"
                    + "|\\bno (information|details|further details|specifics)\\b"
                    + "|\\bunclear\\b"
                    + "|\\b(pull request|merge request|description|summary|evidence|input|issue|ticket)s?"
                    + " (does not|doesn't|did not|didn't|do not|don't)"
                    + " (say|mention|state|specify|describe|explain|include|provide|detail)"
                    + "|không được (cung cấp|nêu|mô tả|nói rõ)|không nêu rõ|chưa được nêu|không có thông tin|không rõ",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern LIST_MARKER = Pattern.compile("^\\s*([-*+]|\\d+[.)])\\s*$");

    private Limitations() {
    }

    /** The limitations without any sentence about the input; empty when nothing is left. */
    static String stated(String limitations) {
        List<String> lines = new ArrayList<>();
        for (String line : limitations.split("\n", -1)) {
            String kept = String.join(" ", Arrays.stream(SENTENCE_END.split(line))
                    .filter(sentence -> !ABOUT_THE_INPUT.matcher(sentence).find())
                    .toList());
            if (!kept.isBlank() && !LIST_MARKER.matcher(kept).matches()) {
                lines.add(kept.stripTrailing());
            }
        }
        return String.join("\n", lines).strip();
    }
}
