package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.category.CategoryGroup;
import com.hoangluongtran0309.releaseflow.category.CategoryRef;
import com.hoangluongtran0309.releaseflow.category.CategorySuggestionDraft;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Validates an AI answer against the shared contract. Anything missing or mistyped
 * is rejected as a whole, and so is a category outside the catalog that was sent;
 * unknown extra fields are ignored. Narratives are optional: only the requested
 * audiences are read, and a missing or empty one is left out. A category suggestion
 * is kept only with an Unknown answer and a valid code that is not already listed. An
 * unknown relevance is full, a headline over 20 words or several lines is dropped, and so
 * is a headline, or a sentence of a body, that remarks on the input. A narrative that says
 * nothing more than having none is left out. A
 * migration kind that does not match the step is corrected rather than rejected, and
 * the answer records that it was; a step that only says nothing needs doing is no step. A
 * missing limitations field is empty, and a limitation about the input or the change's
 * scope rather than what it does not do yet is dropped (ADR-0035).
 */
final class AiClassificationParser {

    static final int SUMMARY_FIELD_LIMIT = 2000;
    static final int SUGGESTION_NAME_LIMIT = 120;
    static final int CONTEXT_REASON_LIMIT = 5;
    static final int CONTEXT_REASON_LENGTH = 120;
    static final int MIGRATION_KIND_LENGTH = 32;
    static final int HEADLINE_WORDS = 20;
    static final String INVALID = "The AI returned an invalid classification.";

    private final ObjectMapper objectMapper;

    AiClassificationParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    AiClassification parse(String content, AiClassificationRequest request) {
        final JsonNode result;
        try {
            result = objectMapper.readTree(content == null ? "" : content);
        } catch (JacksonException exception) {
            throw invalid();
        }
        if (result == null || !result.isObject()) {
            throw invalid();
        }
        String code = CategoryRef.normalize(result.path("category").asString("")).orElseThrow(AiClassificationParser::invalid);
        CategoryRef category = request.categories().stream()
                .filter(candidate -> candidate.code().equals(code))
                .findFirst()
                .orElseThrow(AiClassificationParser::invalid);
        JsonNode breaking = result.path("breaking_change");
        JsonNode security = result.path("security_change");
        JsonNode needsReview = result.path("needs_human_review");
        if (!breaking.isBoolean() || !security.isBoolean() || !needsReview.isBoolean()) {
            throw invalid();
        }
        JsonNode core = result.path("neutral_core");
        if (!core.isObject()) {
            throw invalid();
        }
        String whatChanged = text(core, "what_changed");
        if (whatChanged.isBlank()) {
            throw invalid();
        }
        JsonNode context = result.path("context_sufficiency");
        if (!context.isObject() || !context.path("score").isNumber()) {
            throw invalid();
        }
        String migrationStep = MigrationSteps.stated(text(core, "migration_step"));
        JsonNode returnedKind = core.path("migration_kind");
        Optional<MigrationKind> stated = statedKind(returnedKind);
        // none with a step becomes recommended; any other kind without one, none. A step
        // whose kind is missing says nothing about how much it asks, so it is unspecified.
        MigrationKind migrationKind = stated
                .filter(kind -> (kind == MigrationKind.NONE) == migrationStep.isBlank())
                .orElse(migrationStep.isBlank() ? MigrationKind.NONE
                        : returnedKind.isString() ? MigrationKind.RECOMMENDED : MigrationKind.UNSPECIFIED);
        // A correction a person should check: a step without a usable kind, or a kind for no step.
        boolean corrected = stated.map(kind -> kind != migrationKind).orElse(!migrationStep.isBlank());
        return new AiClassification(
                category,
                breaking.booleanValue(),
                security.booleanValue(),
                needsReview.booleanValue(),
                new NeutralSummary(
                        whatChanged,
                        text(core, "why_changed"),
                        text(core, "technical_detail"),
                        migrationStep,
                        migrationKind,
                        Limitations.stated(optionalText(core, "limitations"))
                ),
                narratives(result.path("narratives"), request.audienceCodes()),
                category.isUnknown() ? suggestion(result.path("suggested_category"), request) : null,
                (int) Math.clamp(Math.round(context.path("score").doubleValue()), 0, 100),
                contextReasons(context.path("reasons")),
                corrected ? returnedKindText(returnedKind) : null
        );
    }

    // One of the four kinds the AI may return; unspecified is never one of them.
    private static Optional<MigrationKind> statedKind(JsonNode returned) {
        return Optional.of(returned)
                .filter(JsonNode::isString)
                .flatMap(node -> MigrationKind.fromValue(node.stringValue()))
                .filter(kind -> kind != MigrationKind.UNSPECIFIED);
    }

    // What a reviewer is shown about a corrected kind; the AI's own text, cut short.
    private static String returnedKindText(JsonNode returned) {
        String text = returned.isString() ? returned.stringValue().strip() : "";
        return cut(text, MIGRATION_KIND_LENGTH);
    }

    private static List<String> contextReasons(JsonNode reasons) {
        List<String> kept = new ArrayList<>();
        if (!reasons.isArray()) {
            return kept;
        }
        for (JsonNode reason : reasons) {
            if (kept.size() == CONTEXT_REASON_LIMIT) {
                break;
            }
            if (reason.isString() && !reason.stringValue().isBlank()) {
                kept.add(cut(reason.stringValue().strip(), CONTEXT_REASON_LENGTH));
            }
        }
        return kept;
    }

    private static CategorySuggestionDraft suggestion(JsonNode node, AiClassificationRequest request) {
        if (!node.isObject() || request.lockedCategory() != null) {
            return null;
        }
        String code = CategoryRef.normalize(node.path("code").asString("")).orElse(null);
        if (code == null || request.categoryCodes().contains(code)) {
            return null;
        }
        String name = node.path("display_name").asString("").strip();
        if (name.isEmpty()) {
            name = code;
        }
        return new CategorySuggestionDraft(
                code,
                cut(name, SUGGESTION_NAME_LIMIT),
                CategoryGroup.fromValue(node.path("group").asString("")).orElse(CategoryGroup.OTHER),
                cut(node.path("rationale").asString("").strip(), CategorySuggestionDraft.RATIONALE_LIMIT)
        );
    }

    private static String cut(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit - 1) + "…";
    }

    // One audience's narrative never makes the answer unusable: whatever is malformed in it
    // is dropped, and a plain string is a body, as narratives were before ADR-0035.
    private static Map<String, AudienceNarrative> narratives(JsonNode narratives, List<String> audienceCodes) {
        Map<String, AudienceNarrative> result = new LinkedHashMap<>();
        for (String code : audienceCodes) {
            JsonNode value = narratives.path(code);
            AudienceNarrative narrative = null;
            if (value.isObject()) {
                narrative = new AudienceNarrative(
                        Relevance.fromValue(value.path("relevance").asString("")).orElse(Relevance.FULL),
                        headline(value.path("headline")),
                        body(value.path("body"))
                );
            } else if (value.isString()) {
                narrative = AudienceNarrative.of(body(value));
            }
            if (narrative != null && !narrative.saysNothing()) {
                result.put(code, narrative);
            }
        }
        return result;
    }

    private static String headline(JsonNode value) {
        String headline = value.isString() ? value.stringValue().strip() : "";
        boolean usable = !headline.contains("\n")
                && headline.split("\\s+").length <= HEADLINE_WORDS
                && !Limitations.isRemarkOnTheInput(headline);
        return usable ? limit(headline) : "";
    }

    private static String body(JsonNode value) {
        return value.isString() ? limit(Limitations.withoutRemarksOnTheInput(value.stringValue().strip())) : "";
    }

    // A field added to the contract later: an answer without it is still usable.
    private static String optionalText(JsonNode core, String field) {
        JsonNode value = core.path(field);
        return value.isString() ? limit(value.stringValue().strip()) : "";
    }

    private static String text(JsonNode core, String field) {
        JsonNode value = core.path(field);
        if (!value.isString()) {
            throw invalid();
        }
        return limit(value.stringValue().strip());
    }

    private static String limit(String value) {
        return value.length() <= SUMMARY_FIELD_LIMIT ? value : value.substring(0, SUMMARY_FIELD_LIMIT - 1) + "…";
    }

    private static AiClassificationException invalid() {
        return new AiClassificationException(INVALID);
    }
}
