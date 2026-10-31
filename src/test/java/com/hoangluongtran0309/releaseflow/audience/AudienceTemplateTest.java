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
            "",
            null,
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

        assertThat(AudienceTemplate.render(template, migration("Rename EXPORT_DIR to EXPORT_PATH.", "required", "Only MongoDB.")))
                .isEqualTo("Required: Rename EXPORT_DIR to EXPORT_PATH. Not yet: Only MongoDB. (required)");
        assertThat(AudienceTemplate.render(template, migration("No direct action is required.", "unspecified", "")))
                .isEqualTo("Migration: No direct action is required. (unspecified)");
        // A step whose writer named no kind is unspecified, and no step has no kind at all.
        assertThat(AudienceTemplate.render(template, migration("Set X.", null, ""))).isEqualTo("Migration: Set X. (unspecified)");
        assertThat(AudienceTemplate.render(template, migration("", "required", ""))).isEqualTo(" ()");
    }

    @Test
    void theShippedPresetsNeverPresentAnUnspecifiedStepAsRequired() {
        AudienceItem unspecified = migration("No direct action is required; credentials migrate on update.", "unspecified", "");
        AudienceItem required = migration("Rename EXPORT_DIR to EXPORT_PATH.", "required", "");

        AudiencePresets.forLanguage("en").forEach(preset -> {
            assertThat(AudienceTemplate.render(preset.templateBody(), unspecified)).doesNotContain("Required step");
            assertThat(AudienceTemplate.render(preset.templateBody(), required)).contains("  - Required step: Rename EXPORT_DIR to EXPORT_PATH.");
        });
        assertThat(AudienceTemplate.render(AudiencePresets.find(AudiencePresets.OPERATOR, "en").orElseThrow().templateBody(),
                unspecified)).contains("  - Migration: No direct action is required");
        // End users read a step only when they cannot do without it.
        assertThat(AudienceTemplate.render(AudiencePresets.find(AudiencePresets.END_USER, "en").orElseThrow().templateBody(),
                migration("Rename EXPORT_DIR to EXPORT_PATH.", "recommended", ""))).doesNotContain("EXPORT_PATH");
    }

    // ADR-0035: a bold headline, the detail lines only for a full change, and a required
    // step whatever the relevance.
    @Test
    void theShippedPresetsLeadWithTheHeadlineAndKeepABriefChangeShort() {
        AudienceItem full = relevant(AudienceItem.FULL);
        AudienceItem brief = relevant(AudienceItem.BRIEF);
        String operator = AudiencePresets.find(AudiencePresets.OPERATOR, "en").orElseThrow().templateBody();

        assertThat(AudienceTemplate.render(operator, full)).isEqualTo("""
                - **Exports queue in the background.** ([#25](https://github.com/acme/app/pull/25)) — Watch the queue.
                  - Why: Large exports timed out.
                  - Detail: Rows stream in pages.
                  - Limitations: Only CSV for now.
                  - Required step: Set EXPORT_QUEUE=on.
                """);
        assertThat(AudienceTemplate.render(operator, brief)).isEqualTo("""
                - **Exports queue in the background.** ([#25](https://github.com/acme/app/pull/25)) — Watch the queue.
                  - Required step: Set EXPORT_QUEUE=on.
                """);
        for (String language : List.of("en", "vi")) {
            AudiencePresets.forLanguage(language).forEach(preset -> {
                String fullNote = AudienceTemplate.render(preset.templateBody(), full);
                String briefNote = AudienceTemplate.render(preset.templateBody(), brief);
                assertThat(fullNote).as(preset.code()).startsWith("- **Exports queue in the background.**");
                assertThat(briefNote).as(preset.code()).contains("Set EXPORT_QUEUE=on.")
                        .doesNotContain("Large exports timed out.", "Rows stream in pages.", "Only CSV for now.");
            });
        }
        // Without a headline, what changed leads, as before.
        assertThat(AudienceTemplate.render(operator, migration("", "", ""))).startsWith("- **Keys per target.**");
    }

    @Test
    void theSupportPresetSaysWhetherAStepIsRequiredOrOnlyAdvisable() {
        String support = AudiencePresets.find(AudiencePresets.SUPPORT, "en").orElseThrow().templateBody();

        assertThat(AudienceTemplate.render(support, migration("Re-save each target.", "recommended", "Only MySQL.")))
                .isEqualTo("""
                        - **Keys per target.** ([#25](https://github.com/acme/app/pull/25))
                          - Recommended step: Re-save each target.
                          - Limitations: Only MySQL.
                        """);
        assertThat(AudienceTemplate.render(support, migration("Rename EXPORT_DIR.", "required", "")))
                .contains("  - Required step: Rename EXPORT_DIR.");
    }

    @Test
    void registrationSeedsFourPresetsWhoseIntentsSayWhenAChangeIsNone() {
        assertThat(AudiencePresets.forLanguage("en").stream().map(AudiencePresets.Preset::code))
                .containsExactly("operator", "contributor", "end_user", "support");
        assertThat(AudiencePresets.find(AudiencePresets.SUPPORT, "vi").orElseThrow().displayName()).isEqualTo("Hỗ trợ");
        AudiencePresets.forLanguage("en").forEach(preset -> assertThat(preset.communicationIntent())
                .as(preset.code()).containsPattern("A change is none for [a-z ]+ (only )?when"));
        // Asked for what to watch in general, the AI invented advice nobody gave.
        assertThat(AudiencePresets.OPERATOR_INTENT)
                .doesNotContain("what to watch after deploy.")
                .contains("only when the change or the project context names it");
    }

    private static AudienceItem relevant(String relevance) {
        return new AudienceItem("Exports no longer time out.", "Large exports timed out.", "Rows stream in pages.",
                "Set EXPORT_QUEUE=on.", "required", "Only CSV for now.", "Watch the queue.",
                "Exports queue in the background.", relevance, "#25", 25, "https://github.com/acme/app/pull/25", List.of());
    }

    private static AudienceItem migration(String step, String kind, String limitations) {
        return new AudienceItem("Keys per target.", "", "", step, kind, limitations, "", "", null, "#25", 25,
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
        AudienceItem linked = new AudienceItem("Export.", "", "", "", "", "", "", "", null, "#12", 12, "https://github.com/acme/app/pull/12",
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
        AudienceItem mergeRequest = new AudienceItem(ITEM.whatChanged(), "", "", "", "", "", "", "", null, "!2", 2,
                "https://gitlab.com/acme/app/-/merge_requests/2", List.of());

        AudiencePresets.forLanguage("en").stream()
                .filter(preset -> !preset.code().equals(AudiencePresets.END_USER))
                .forEach(preset -> assertThat(AudienceTemplate.render(preset.templateBody(), mergeRequest))
                        .contains("([!2](https://gitlab.com/acme/app/-/merge_requests/2))"));
    }

    @Test
    void theShippedPresetsIgnoreLinkedIssues() {
        AudienceItem linked = new AudienceItem(ITEM.whatChanged(), ITEM.whyChanged(), ITEM.technicalDetail(),
                ITEM.migrationStep(), ITEM.migrationKind(), ITEM.limitations(), ITEM.narrative(), ITEM.headline(), ITEM.relevance(), ITEM.reference(), ITEM.pullRequestNumber(), ITEM.pullRequestUrl(),
                List.of(new LinkedIssue("APP-7", "Export tables", "", "Story", "Done",
                        "https://acme.atlassian.net/browse/APP-7")));

        AudiencePresets.forLanguage("en").forEach(preset -> assertThat(AudienceTemplate.render(preset.templateBody(), linked))
                .isEqualTo(AudienceTemplate.render(preset.templateBody(), ITEM)));
    }

    @Test
    void acceptsEveryShippedPreset() {
        for (String language : new String[]{"en", "vi", "pt-BR"}) {
            AudiencePresets.forLanguage(language).forEach(preset -> {
                AudienceTemplate.validate(preset.templateBody());
                AudienceTemplate.validateFooter(preset.footerBody());
            });
        }
    }

    // ADR-0037: whoever upgrades, or answers for an upgrade, reads what is not done yet on a
    // line of its own, in both languages; end users do not.
    @Test
    void theUpgraderPresetsPrintLimitationsOnALineOfTheirOwn() {
        AudienceItem full = relevant(AudienceItem.FULL);
        for (String[] language : new String[][]{{"en", "  - Limitations: Only CSV for now.\n"}, {"vi", "  - Giới hạn: Only CSV for now.\n"}}) {
            for (String code : List.of(AudiencePresets.OPERATOR, AudiencePresets.CONTRIBUTOR, AudiencePresets.SUPPORT)) {
                assertThat(AudienceTemplate.render(AudiencePresets.find(code, language[0]).orElseThrow().templateBody(), full))
                        .as(code + " " + language[0]).contains(language[1]);
            }
            assertThat(AudienceTemplate.render(
                    AudiencePresets.find(AudiencePresets.END_USER, language[0]).orElseThrow().templateBody(), full))
                    .doesNotContain("Only CSV for now.");
        }
    }

    @Test
    void theUpgraderPresetFootersLinkTheComparisonAndTheGuideButNotTheRelease() {
        ReleaseLinks links = new ReleaseLinks("v1.0.0", "v0.3.0", "https://github.com/acme/app/compare/v0.3.0...v1.0.0",
                "https://docs.example.com/upgrade", "https://github.com/acme/app/releases/tag/v1.0.0");
        for (String code : List.of(AudiencePresets.OPERATOR, AudiencePresets.CONTRIBUTOR)) {
            assertThat(AudienceTemplate.renderFooter(AudiencePresets.find(code, "en").orElseThrow().footerBody(), links))
                    .isEqualTo("""
                            **Compare:** [v0.3.0...v1.0.0](https://github.com/acme/app/compare/v0.3.0...v1.0.0)

                            **Upgrade guide:** [Read before upgrading](https://docs.example.com/upgrade)""");
            assertThat(AudienceTemplate.renderFooter(AudiencePresets.find(code, "vi").orElseThrow().footerBody(), links))
                    .isEqualTo("""
                            **So sánh:** [v0.3.0...v1.0.0](https://github.com/acme/app/compare/v0.3.0...v1.0.0)

                            **Hướng dẫn nâng cấp:** [Đọc trước khi nâng cấp](https://docs.example.com/upgrade)""");
            assertThat(AudienceTemplate.renderFooter(AudiencePresets.find(code, "en").orElseThrow().footerBody(),
                    new ReleaseLinks("v0.1.0", "", "", "", "https://github.com/acme/app/releases/tag/v0.1.0"))).isEmpty();
        }
        for (String code : List.of(AudiencePresets.END_USER, AudiencePresets.SUPPORT)) {
            assertThat(AudiencePresets.find(code, "en").orElseThrow().footerBody()).isEmpty();
        }
    }

    @Test
    void rendersTheSampleFooterForThePreview() {
        assertThat(AudienceTemplate.sampleFooter("[{{previousVersion}}...{{version}}]({{compareUrl}}) {{releaseUrl}} {{upgradeGuideUrl}}"))
                .isEqualTo("[v1.3.0...v1.4.0](https://github.com/acme/app/compare/v1.3.0...v1.4.0)"
                        + " https://github.com/acme/app/releases/tag/v1.4.0 https://docs.acme.example/upgrade/1.4");
        assertThat(AudienceTemplate.sampleFooter("")).isEmpty();
        assertThat(AudienceTemplate.sampleFooter(null)).isEmpty();
    }

    @Test
    void aFooterSectionSeesItsOwnValueAndTheOthersAndAnInvertedOneShowsWhenEmpty() {
        String footer = "{{#compareUrl}}<{{.}}> {{version}}{{#previousVersion}} after {{.}}{{/previousVersion}}{{/compareUrl}}"
                + "{{^previousVersion}}First release {{version}}{{/previousVersion}}";

        assertThat(AudienceTemplate.renderFooter(footer, new ReleaseLinks("v2", "v1", "https://x/compare", "", "")))
                .isEqualTo("<https://x/compare> v2 after v1");
        assertThat(AudienceTemplate.renderFooter(footer, new ReleaseLinks("v1", "", "", "", "")))
                .isEqualTo("First release v1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{{whatChanged}}", "{{#narrative}}x{{/narrative}}", "{{tag}}", "{{#compareUrl}}unclosed", "{{/version}}"})
    void rejectsAFooterThatUsesAnItemVariableAnUnknownOneOrInvalidMustache(String body) {
        assertThatThrownBy(() -> AudienceTemplate.validateFooter(body))
                .isInstanceOfSatisfying(InvalidAudienceTemplateException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(InvalidAudienceTemplateException.INVALID);
                    assertThat(exception.field()).isEqualTo("footerTemplateBody");
                });
    }

    @Test
    void rejectsAnOverlongFooterAndAnItemTemplateThatUsesAFooterVariable() {
        assertThatThrownBy(() -> AudienceTemplate.validateFooter("x".repeat(AudienceTemplate.MAX_LENGTH + 1)))
                .isInstanceOf(InvalidAudienceTemplateException.class)
                .hasMessage("error.template_invalid.tooLong");
        assertThatThrownBy(() -> AudienceTemplate.validate("- {{whatChanged}} {{compareUrl}}"))
                .isInstanceOfSatisfying(InvalidAudienceTemplateException.class,
                        exception -> assertThat(exception.field()).isEqualTo("templateBody"));
        assertThatCode(() -> AudienceTemplate.validateFooter("  \n")).doesNotThrowAnyException();
    }
}
