package com.hoangluongtran0309.releaseflow.audience;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * The four audiences every Organization starts with. Display names and the labels in
 * the templates follow the Organization's output language (English or Vietnamese,
 * otherwise English); the communication intents are instructions for the AI and stay
 * in English, and each ends by saying when a change is none for its audience
 * (ADR-0035). V13 seeded the values of its time for Organizations that existed before;
 * they keep their templates until an administrator resets a preset, and can add the
 * support audience themselves.
 */
final class AudiencePresets {

    static final String OPERATOR = "operator";
    static final String CONTRIBUTOR = "contributor";
    static final String END_USER = "end_user";
    static final String SUPPORT = "support";

    // A note names something to watch only when the evidence does: asked for in general,
    // the AI invented advice nobody gave.
    static final String OPERATOR_INTENT = "Focus on operational risk, rollback, and what changes in how the product "
            + "is deployed, configured, or run. Keep concrete numbers and failure modes. Name something to watch "
            + "after deploying only when the change or the project context names it. "
            + "A change is none for operators when it changes nothing about deploying, configuring, or running the "
            + "product, such as a test-only, CI-only, or documentation-only change.";
    static final String CONTRIBUTOR_INTENT = "Focus on implementation detail and what other developers must change "
            + "in their own code. Technical vocabulary is expected. "
            + "A change is none for contributors only when it touches nothing developers build, test, or call, "
            + "such as a change to deployment settings alone.";
    static final String END_USER_INTENT = "Plain language, no jargon and no technical metrics. "
            + "Say only what the person will notice while using the product. "
            + "A change is none for end users when they cannot see it: build, CI, dependency, test, and internal "
            + "changes.";
    static final String SUPPORT_INTENT = "Write for the support team: what customers may notice or ask about, "
            + "what to tell them, and whether a step is required or only advisable. Plain, precise language. "
            + "A change is none for support when customers cannot notice it: build, CI, dependency, test, and "
            + "internal changes.";

    // A migration step is labelled by its kind (ADR-0035), and a required one is printed
    // whatever the relevance. One recorded before kinds existed keeps the neutral label,
    // so it is never presented as required.
    private static final String REQUIRED_LINE = """
            {{#migrationRequired}}
              - %s: {{migrationStep}}
            {{/migrationRequired}}
            """;
    private static final String OTHER_MIGRATION_LINES = """
            {{#migrationConditional}}
              - %1$s: {{migrationStep}}
            {{/migrationConditional}}
            {{#migrationRecommended}}
              - %2$s: {{migrationStep}}
            {{/migrationRecommended}}
            {{#migrationUnspecified}}
              - %3$s: {{migrationStep}}
            {{/migrationUnspecified}}
            """;
    // A brief change keeps its headline and a required step; the detail lines are for full ones.
    private static final String DETAILED_BODY = """
            - **{{headline}}** ([{{reference}}]({{pullRequestUrl}})){{#narrative}} — {{.}}{{/narrative}}
            {{^brief}}
            {{#whyChanged}}
              - %s: {{.}}
            {{/whyChanged}}
            {{#technicalDetail}}
              - %s: {{.}}
            {{/technicalDetail}}
            {{#limitations}}
              - %s: {{.}}
            {{/limitations}}
            {{/brief}}
            %s{{^brief}}
            %s{{/brief}}
            """;
    // End users read a step only when they cannot do without it.
    private static final String PLAIN_BODY = """
            - **{{headline}}**{{#narrative}} — {{.}}{{/narrative}}
            %s""";
    // Support learns whether a step is required or only advisable, and what is not done yet.
    private static final String SUPPORT_BODY = """
            - **{{headline}}** ([{{reference}}]({{pullRequestUrl}})){{#narrative}} — {{.}}{{/narrative}}
            %s{{^brief}}
            %s{{#limitations}}
              - %s: {{.}}
            {{/limitations}}
            {{/brief}}
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
        String required = REQUIRED_LINE.formatted(labels.getString("label.migration.required"));
        String otherMigration = OTHER_MIGRATION_LINES.formatted(
                labels.getString("label.migration.conditional"),
                labels.getString("label.migration.recommended"),
                labels.getString("label.migration")
        );
        return List.of(
                new Preset(
                        OPERATOR,
                        labels.getString("operator.displayName"),
                        OPERATOR_INTENT,
                        DETAILED_BODY.formatted(why, labels.getString("label.detail"), limitations, required, otherMigration)
                ),
                new Preset(
                        CONTRIBUTOR,
                        labels.getString("contributor.displayName"),
                        CONTRIBUTOR_INTENT,
                        DETAILED_BODY.formatted(why, labels.getString("label.implementation"), limitations, required,
                                otherMigration)
                ),
                new Preset(END_USER, labels.getString("end_user.displayName"), END_USER_INTENT, PLAIN_BODY.formatted(required)),
                new Preset(
                        SUPPORT,
                        labels.getString("support.displayName"),
                        SUPPORT_INTENT,
                        SUPPORT_BODY.formatted(required, otherMigration, limitations)
                )
        );
    }

    static Optional<Preset> find(String code, String languageTag) {
        return forLanguage(languageTag).stream().filter(preset -> preset.code().equals(code)).findFirst();
    }
}
