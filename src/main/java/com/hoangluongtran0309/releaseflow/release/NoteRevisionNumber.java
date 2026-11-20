package com.hoangluongtran0309.releaseflow.release;

import java.util.regex.Pattern;

/** A revision as a request names it: a positive whole number, or nothing for the latest. */
final class NoteRevisionNumber {

    // Up to nine digits, so the number always fits; no note has a billion revisions.
    private static final Pattern POSITIVE = Pattern.compile("[1-9][0-9]{0,8}");

    private NoteRevisionNumber() {
    }

    /** The revision named, or null when none was; anything else is refused. */
    static Integer parse(String value) {
        if (value == null) {
            return null;
        }
        if (!POSITIVE.matcher(value).matches()) {
            throw new InvalidNoteRevisionException();
        }
        return Integer.valueOf(value);
    }
}
