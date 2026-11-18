package com.hoangluongtran0309.releaseflow.change;

import java.util.List;
import java.util.UUID;

/**
 * A change of a release that the AI classified and that has no narrative for some active
 * audiences, such as one brought back after it was classified (ADR-0044). Its notes still
 * include it, under its neutral headline; a person writes the narratives, or asks the AI for
 * them at the cost shown.
 *
 * @param estimatedTokens the rough size of the request that would ask for them, or zero
 *                        when no AI provider is configured
 */
public record MissingNarrative(
        UUID changeId,
        String reference,
        String title,
        List<String> audienceNames,
        long estimatedTokens
) {
}
