package com.hoangluongtran0309.releaseflow.change;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

/**
 * What one change says to one audience (ADR-0035): how much the audience needs to hear,
 * its one-line headline, and the body that templates print as {@code narrative}. A
 * missing relevance is full, and missing text is empty.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AudienceNarrative(Relevance relevance, String headline, String body) {

    public AudienceNarrative {
        relevance = Objects.requireNonNullElse(relevance, Relevance.FULL);
        headline = Objects.requireNonNullElse(headline, "");
        body = Objects.requireNonNullElse(body, "");
    }

    /** A narrative written as plain text before relevance existed: full, with no headline. */
    public static AudienceNarrative of(String body) {
        return new AudienceNarrative(Relevance.FULL, "", body);
    }

    /**
     * Whether it says nothing that having no narrative would not: full, with neither a
     * headline nor a body. Such a narrative is left out.
     */
    public boolean saysNothing() {
        return relevance == Relevance.FULL && headline.isBlank() && body.isBlank();
    }
}
