package com.hoangluongtran0309.releaseflow.translation;

import java.time.Instant;

/**
 * Where one language pair the Organization translates stands with DeepL. {@code status}
 * is {@code empty} (no entry applies to the pair), {@code not_synced} (it is synced at the
 * next approval that needs it), {@code outdated} (edited since its last sync), {@code
 * pending}, {@code synced}, {@code unsupported} (DeepL serves no glossary for the pair), or
 * {@code failed}, with the fixed {@code error} code of its last failure.
 */
public record GlossaryPairView(
        String sourceLanguage,
        String targetLanguage,
        int entryCount,
        String status,
        String error,
        Instant updatedAt
) {

    public boolean problem() {
        return status.equals("unsupported") || status.equals("failed");
    }
}
