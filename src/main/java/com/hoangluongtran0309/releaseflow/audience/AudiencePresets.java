package com.hoangluongtran0309.releaseflow.audience;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * The three audiences every Organization starts with. Display names and the labels in
 * the templates follow the Organization's output language (English or Vietnamese,
 * otherwise English); the communication intents are instructions for the AI and stay
 * in English. V13 seeded the values of its time for Organizations that existed before;
 * they keep their templates until an administrator resets a preset.
 */
final class AudiencePresets {

    static final String OPERATOR = "operator";
    static final String CONTRIBUTOR = "contributor";
    static final String END_USER = "end_user";

    static final String OPERATOR_INTENT = "Focus on operational risk, rollback, and what to watch after deploy. "
            + "Keep concrete numbers and failure modes.";
    static final String CONTRIBUTOR_INTENT = "Focus on implementation detail and what other developers must change "
            + "in their own code. Technical vocabulary is expected.";
    static final String END_USER_INTENT = "Plain language, no jargon and no technical metrics. "
            + "Say only what the person will notice while using the product.";

    // A migration step is labelled by its kind (ADR-0035). One recorded before kinds
    // existed keeps the neutral label, so it is never presented as required.
    private static final String MIGRATION_LINES = """
            {{#migrationRequired}}
              - %1$s: {{migrationStep}}
            {{/migrationRequired}}
            {{#migrationConditional}}
              - %2$s: {{migrationStep}}
            {{/migrationConditional}}
            {{#migrationRecommended}}
              - %3$s: {{migrationStep}}
            {{/migrationRecommended}}
            {{#migrationUnspecified}}
              - %4$s: {{migrationStep}}
            {{/migrationUnspecified}}
            """;
    private static final String DETAILED_BODY = """
            - **{{whatChanged}}** ([{{reference}}]({{pullRequestUrl}})){{#narrative}} — {{.}}{{/narrative}}
            {{#whyChanged}}
              - %s: {{.}}
            {{/whyChanged}}
            {{#technicalDetail}}
              - %s: {{.}}
            {{/technicalDetail}}
            {{#limitations}}
              - %s: {{.}}
            {{/limitations}}
            %s""";
    // End users read a step only when they cannot do without it.
    private static final String PLAIN_BODY = """
            - **{{whatChanged}}**{{#narrative}} — {{.}}{{/narrative}}
            {{#migrationRequired}}
              - %s: {{migrationStep}}
            {{/migrationRequired}}
            """;
    private static final String BUNDLE = "messages/audience-presets";

    record Preset(String code, String displayName, String communicationIntent, String templateBody) {
    }

    private AudiencePresets() {
    }

    static List<Preset> forLanguage(String languageTag) {
        ResourceBundle labels = ResourceBundle.getBundle(
                BUNDLE,
                Locale.forLanguageTag(languageTag),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES)
        );
        String why = labels.getString("label.why");
        String limitations = labels.getString("label.limitations");
        String required = labels.getString("label.migration.required");
        String migration = MIGRATION_LINES.formatted(
                required,
                labels.getString("label.migration.conditional"),
                labels.getString("label.migration.recommended"),
                labels.getString("label.migration")
        );
        return List.of(
                new Preset(
                        OPERATOR,
                        labels.getString("operator.displayName"),
                        OPERATOR_INTENT,
                        DETAILED_BODY.formatted(why, labels.getString("label.detail"), limitations, migration)
                ),
                new Preset(
                        CONTRIBUTOR,
                        labels.getString("contributor.displayName"),
                        CONTRIBUTOR_INTENT,
                        DETAILED_BODY.formatted(why, labels.getString("label.implementation"), limitations, migration)
                ),
                new Preset(END_USER, labels.getString("end_user.displayName"), END_USER_INTENT, PLAIN_BODY.formatted(required))
        );
    }

    static Optional<Preset> find(String code, String languageTag) {
        return forLanguage(languageTag).stream().filter(preset -> preset.code().equals(code)).findFirst();
    }
}
