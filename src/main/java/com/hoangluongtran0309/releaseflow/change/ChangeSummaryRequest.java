package com.hoangluongtran0309.releaseflow.change;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A person's version of a change's neutral summary and of what it says to each
 * audience. Empty fields are allowed except what changed. A request that names no
 * migration kind, as callers written before ADR-0035 send, records an unspecified one
 * for a step and none without. What it says to an audience is a relevance, a headline,
 * and a body, or a plain string, which is a full body with no headline.
 */
public class ChangeSummaryRequest {

    static final int FIELD_LIMIT = 2000;

    @NotBlank(message = "{validation.whatChanged.required}")
    @Size(max = FIELD_LIMIT, message = "{validation.whatChanged.tooLong}")
    private String whatChanged;

    @Size(max = FIELD_LIMIT, message = "{validation.whyItChanged.tooLong}")
    private String whyChanged = "";

    @Size(max = FIELD_LIMIT, message = "{validation.technicalDetail.tooLong}")
    private String technicalDetail = "";

    @Size(max = FIELD_LIMIT, message = "{validation.migrationStep.tooLong}")
    private String migrationStep = "";

    private MigrationKind migrationKind;

    @Size(max = FIELD_LIMIT, message = "{validation.limitations.tooLong}")
    private String limitations = "";

    private Map<String, Narrative> narratives = new LinkedHashMap<>();

    public String getWhatChanged() {
        return whatChanged;
    }

    public void setWhatChanged(String whatChanged) {
        this.whatChanged = clean(whatChanged);
    }

    public String getWhyChanged() {
        return whyChanged;
    }

    public void setWhyChanged(String whyChanged) {
        this.whyChanged = clean(whyChanged);
    }

    public String getTechnicalDetail() {
        return technicalDetail;
    }

    public void setTechnicalDetail(String technicalDetail) {
        this.technicalDetail = clean(technicalDetail);
    }

    public String getMigrationStep() {
        return migrationStep;
    }

    public void setMigrationStep(String migrationStep) {
        this.migrationStep = clean(migrationStep);
    }

    public MigrationKind getMigrationKind() {
        return migrationKind;
    }

    public void setMigrationKind(MigrationKind migrationKind) {
        this.migrationKind = migrationKind;
    }

    public String getLimitations() {
        return limitations;
    }

    public void setLimitations(String limitations) {
        this.limitations = clean(limitations);
    }

    public Map<String, Narrative> getNarratives() {
        return narratives;
    }

    public void setNarratives(Map<String, Narrative> narratives) {
        this.narratives = new LinkedHashMap<>();
        if (narratives != null) {
            narratives.forEach((code, narrative) -> this.narratives.put(code, narrative == null ? new Narrative() : narrative));
        }
    }

    @AssertTrue(message = "{validation.narrative.tooLong}")
    public boolean isNarrativesWithinLimit() {
        return narratives.values().stream().allMatch(narrative -> narrative != null
                && clean(narrative.getHeadline()).length() <= FIELD_LIMIT
                && clean(narrative.getBody()).length() <= FIELD_LIMIT);
    }

    // Only a step has a kind, and every step has one other than none.
    @AssertTrue(message = "{validation.migrationKind.mismatch}")
    public boolean isMigrationKindMatchingStep() {
        return migrationKind == null || (migrationKind == MigrationKind.NONE) == migrationStep.isEmpty();
    }

    NeutralSummary summary() {
        return new NeutralSummary(whatChanged, whyChanged, technicalDetail, migrationStep, migrationKind, limitations);
    }

    // Keeps the narratives of the given audiences that say something more than having none
    // would. Form binding fills the map directly, so values are cleaned here as well; a
    // headline is one line.
    Map<String, AudienceNarrative> narratives(Iterable<String> audienceCodes) {
        Map<String, AudienceNarrative> kept = new LinkedHashMap<>();
        for (String code : audienceCodes) {
            Narrative sent = narratives.get(code);
            if (sent == null) {
                continue;
            }
            AudienceNarrative narrative = new AudienceNarrative(
                    sent.getRelevance(),
                    clean(sent.getHeadline()).replaceAll("\\s+", " "),
                    clean(sent.getBody())
            );
            if (!narrative.saysNothing()) {
                kept.put(code, narrative);
            }
        }
        return kept;
    }

    // Browsers submit textarea line breaks as CRLF.
    private static String clean(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").strip();
    }

    /** What a person says to one audience; a missing relevance is full. */
    public static class Narrative {

        private Relevance relevance = Relevance.FULL;
        private String headline = "";
        private String body = "";

        public Narrative() {
        }

        // A plain string, as callers written before ADR-0035 send: the body, in full.
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Narrative of(String body) {
            Narrative narrative = new Narrative();
            narrative.setBody(body);
            return narrative;
        }

        public Relevance getRelevance() {
            return relevance;
        }

        public void setRelevance(Relevance relevance) {
            this.relevance = relevance == null ? Relevance.FULL : relevance;
        }

        public String getHeadline() {
            return headline;
        }

        public void setHeadline(String headline) {
            this.headline = headline == null ? "" : headline;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body == null ? "" : body;
        }
    }
}
