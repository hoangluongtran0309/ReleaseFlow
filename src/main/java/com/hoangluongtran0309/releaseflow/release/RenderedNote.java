package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.translation.GlossaryUse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A published note rendered again (ADR-0046): the revision it would correct and its text,
 * the text rendering gives now, how many of the note's texts had no answer it was published
 * with and how long they are, and what the answers found did with the glossary. The hash of
 * the text is what saving sends back, so a preview that would now differ is never saved.
 */
record RenderedNote(
        int corrects,
        String latestContent,
        String content,
        int missingTexts,
        int missingCharacters,
        GlossaryUse glossary
) {

    String previewHash() {
        return hash(content);
    }

    boolean changed() {
        return !content.equals(latestContent);
    }

    static String hash(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is always available.", exception);
        }
    }
}
