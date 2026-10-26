package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.jira.LinkedIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudienceTemplateTest {

    private static final AudienceItem ITEM = new AudienceItem(
            "Adds <b>CSV</b> & JSON export.",
            "",
            "Streams rows.",
            "",
            "",
            "",
            "Download data from any table.",
            "#12",
            12,
            "https://github.com/acme/app/pull/12",
            List.of()
    );

    @Test
    void rendersEveryVariableAndLeavesOutEmptySections() {
        String template = """
                - {{whatChanged}} [#{{pullRequestNumber}}]({{pullRequestUrl}})
                {{#whyChanged}}
                  - Why: {{.}}
                {{/whyChanged}}
                {{#technicalDetail}}
                  - Detail: {{.}}
                {{/technicalDetail}}
                {{#narrative}}
                  - {{.}}
                {{/narrative}}
                {{migrationStep}}""";

        assertThat(AudienceTemplate.render(template, ITEM)).isEqualTo("""
                - Adds <b>CSV</b> & JSON export. [#12](https://github.com/acme/app/pull/12)
                  - Detail: Streams rows.
                  - Download data from any table.
                """);
    }

    @Test
    void labelsAMigrationStepByItsKindAndPrintsLimitations() {
        String template = """
                {{#migrationRequired}}Required: {{migrationStep}}{{/migrationRequired}}\
                {{#migrationConditional}}If: {{migrationStep}}{{/migrationConditional}}\
                {{#migrationRecommended}}Recommended: {{migrationStep}}{{/migrationRecommended}}\
                {{#migrationUnspecified}}Migration: {{migrationStep}}{{/migrationUnspecified}}\
                {{#limitations}} Not yet: {{.}}{{/limitations}} ({{migrationKind}})""";

        assertThat(AudienceTemplate.render(template, migration("Re-save each target.", "required", "Only MongoDB.")))
                .isEqualTo("Required: Re-save each target. Not yet: Only MongoDB. (required)");
        assertThat(AudienceTemplate.render(template, migration("No direct action is required.", "unspecified", "")))
                .isEqualTo("Migration: No direct action is required. (unspecified)");
        // A step whose writer named no kind is unspecified, and no step has no kind at all.
        assertThat(AudienceTemplate.render(template, migration("Set X.", null, ""))).isEqualTo("Migration: Set X. (unspecified)");
        assertThat(AudienceTemplate.render(template, migration("", "required", ""))).isEqualTo(" ()");
    }

    @Test
    void theShippedPresetsNeverPresentAnUnspecifiedStepAsRequired() {
        AudienceItem unspecified = migration("No direct action is required; credentials migrate on update.", "unspecified", "");
        AudienceItem required = migration("Re-save each target.", "required", "");

        AudiencePresets.forLanguage("en").forEach(preset -> {
            assertThat(AudienceTemplate.render(preset.templateBody(), unspecified)).doesNotContain("Required step");
            assertThat(AudienceTemplate.render(preset.templateBody(), required)).contains("  - Required step: Re-save each target.");
        });
        assertThat(AudienceTemplate.render(AudiencePresets.find(AudiencePresets.OPERATOR, "en").orElseThrow().templateBody(),
                unspecified)).contains("  - Migration: No direct action is required");
        // End users read a step only when they cannot do without it.
        assertThat(AudienceTemplate.render(AudiencePresets.find(AudiencePresets.END_USER, "en").orElseThrow().templateBody(),
                migration("Re-save each target.", "recommended", ""))).doesNotContain("Re-save");
    }

    private static AudienceItem migration(String step, String kind, String limitations) {
        return new AudienceItem("Keys per target.", "", "", step, kind, limitations, "", "#25", 25,
                "https://github.com/acme/app/pull/25", List.of());
    }

    @Test
    void rendersTheSampleForThePreview() {
        assertThat(AudienceTemplate.sample("{{whatChanged}} — {{narrative}}"))
                .isEqualTo("A safer deployment workflow is now available. — "
                        + "Release managers can review audience-specific notes before they are published.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n"})
    void rejectsABlankTemplate(String body) {
        assertThatThrownBy(() -> AudienceTemplate.validate(body))
                .isInstanceOf(InvalidAudienceTemplateException.class)
                .hasMessage("error.template_invalid.required")
                .extracting("code").isEqualTo(InvalidAudienceTemplateException.INVALID);
    }

    @Test
    void rejectsAnOverlongTemplate() {
        assertThatThrownBy(() -> AudienceTemplate.validate("x".repeat(AudienceTemplate.MAX_LENGTH + 1)))
                .isInstanceOf(InvalidAudienceTemplateException.class)
                .hasMessage("error.template_invalid.tooLong")
                .extracting("arguments").isEqualTo(new Object[]{AudienceTemplate.MAX_LENGTH});
    }

    @ParameterizedTest
    @ValueSource(strings = {"{{narratives.operator}}", "{{ narratives . end_user }}", "{{&narratives.x}}", "{{{narratives.x}}}"})
    void rejectsATemplateThatNamesAnotherAudience(String body) {
        assertThatThrownBy(() -> AudienceTemplate.validate(body))
                .isInstanceOf(InvalidAudienceTemplateException.class)
                .extracting("code").isEqualTo(InvalidAudienceTemplateException.NARRATIVES_PATH);
    }

    @Test
    void readsATemplateOnceHoweverItIsPunctuated() {
        // The check for another audience's narrative is a scan, not a search: a template
        // full of braces and spaces costs time in proportion to its length, and the
        // length is capped before the scan begins.
        String braces = "{{{{" + " ".repeat(AudienceTemplate.MAX_LENGTH - 8) + "}}}}";

        assertThatCode(() -> AudienceTemplate.validate(braces)).doesNotThrowAnyException();

        // A narrative named after all that punctuation is still caught, whichever
        // spelling of the tag it hides behind.
        assertThatThrownBy(() -> AudienceTemplate.validate("{{ {  narratives.operator }}"))
                .isInstanceOf(InvalidAudienceTemplateException.class)
                .extracting("code").isEqualTo(InvalidAudienceTemplateException.NARRATIVES_PATH);
        assertThatThrownBy(() -> AudienceTemplate.validate("{{  &  narratives  . x }}"))
                .isInstanceOf(InvalidAudienceTemplateException.class)
                .extracting("code").isEqualTo(InvalidAudienceTemplateException.NARRATIVES_PATH);

        // The word on its own is not a path into another audience, and neither is one
        // that no tag opens.
        assertThatCode(() -> AudienceTemplate.validate("These are the narratives. {{whatChanged}}"))
                .doesNotThrowAnyException();

        // And the audience's own narrative is not what the rule is about.
        assertThatCode(() -> AudienceTemplate.validate("- {{whatChanged}}{{#narrative}} — {{.}}{{/narrative}}"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{{#whatChanged}}unclosed", "{{userImpact}}", "{{#unknown}}x{{/unknown}}", "{{/whatChanged}}"})
    void rejectsInvalidMustacheAndUnknownVariables(String body) {
        assertThatThrownBy(() -> AudienceTemplate.validate(body))
                .isInstanceOf(InvalidAudienceTemplateException.class)
                .hasMessage("error.template_invalid.mustache")
                .extracting("code").isEqualTo(InvalidAudienceTemplateException.INVALID);
    }

    @Test
    void listsTheIssuesAChangeMentionsAndLeavesTheSectionOutWithoutThem() {
        String template = "- {{whatChanged}}{{#linkedIssues}} [{{key}}]({{url}}) {{title}} ({{type}}, {{status}}){{/linkedIssues}}";
        AudienceItem linked = new AudienceItem("Export.", "", "", "", "", "", "", "#12", 12, "https://github.com/acme/app/pull/12",
                List.of(
                        new LinkedIssue("APP-7", "Export tables", "never shown", "Story", "Done",
                                "https://acme.atlassian.net/browse/APP-7"),
                        new LinkedIssue("APP-8", "Stream rows", "", "Task", "Done",
                                "https://acme.atlassian.net/browse/APP-8")
                ));

        AudienceTemplate.validate(template);
        assertThat(AudienceTemplate.render(template, linked)).isEqualTo(
                "- Export. [APP-7](https://acme.atlassian.net/browse/APP-7) Export tables (Story, Done)"
                        + " [APP-8](https://acme.atlassian.net/browse/APP-8) Stream rows (Task, Done)");
        assertThat(AudienceTemplate.render(template, ITEM)).isEqualTo("- Adds <b>CSV</b> & JSON export.");
        assertThat(AudienceTemplate.sample(template)).contains("[APP-7](https://acme.atlassian.net/browse/APP-7)");
        assertThat(AudienceItem.VARIABLES).endsWith("linkedIssues");
        // A tracker's description is long, untrusted prose, so a note cannot print it.
        assertThatThrownBy(() -> AudienceTemplate.validate("{{#linkedIssues}}{{description}}{{/linkedIssues}}"))
                .isInstanceOf(InvalidAudienceTemplateException.class);
    }

    @Test
    void theShippedDetailedPresetsNameAChangeTheWayItsSourceDoes() {
        AudienceItem mergeRequest = new AudienceItem(ITEM.whatChanged(), "", "", "", "", "", "", "!2", 2,
                "https://gitlab.com/acme/app/-/merge_requests/2", List.of());

        AudiencePresets.forLanguage("en").stream()
                .filter(preset -> !preset.code().equals(AudiencePresets.END_USER))
                .forEach(preset -> assertThat(AudienceTemplate.render(preset.templateBody(), mergeRequest))
                        .contains("([!2](https://gitlab.com/acme/app/-/merge_requests/2))"));
    }

    @Test
    void theShippedPresetsIgnoreLinkedIssues() {
        AudienceItem linked = new AudienceItem(ITEM.whatChanged(), ITEM.whyChanged(), ITEM.technicalDetail(),
                ITEM.migrationStep(), ITEM.migrationKind(), ITEM.limitations(), ITEM.narrative(), ITEM.reference(), ITEM.pullRequestNumber(), ITEM.pullRequestUrl(),
                List.of(new LinkedIssue("APP-7", "Export tables", "", "Story", "Done",
                        "https://acme.atlassian.net/browse/APP-7")));

        AudiencePresets.forLanguage("en").forEach(preset -> assertThat(AudienceTemplate.render(preset.templateBody(), linked))
                .isEqualTo(AudienceTemplate.render(preset.templateBody(), ITEM)));
    }

    @Test
    void acceptsEveryShippedPreset() {
        for (String language : new String[]{"en", "vi", "pt-BR"}) {
            AudiencePresets.forLanguage(language).forEach(preset -> AudienceTemplate.validate(preset.templateBody()));
        }
    }
}
