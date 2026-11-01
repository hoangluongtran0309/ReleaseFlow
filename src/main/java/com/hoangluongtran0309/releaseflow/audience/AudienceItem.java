package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.jira.LinkedIssue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The values one audience template sees for one change. Text values are never null;
 * an empty value counts as false in a section, so {@code {{#narrative}}...{{/narrative}}}
 * disappears when there is no narrative.
 *
 * @param migrationKind {@code required}, {@code conditional}, {@code recommended}, or
 *     {@code unspecified} for a step recorded before kinds existed; empty when there is
 *     no step (ADR-0035)
 * @param narrative the body of what the change says to this audience
 * @param headline this audience's one-line headline, or {@code whatChanged} when it has
 *     none, so it always has a value
 * @param relevance {@code brief} or {@code full}; a change that is {@code none} for the
 *     audience is not in its note, unless it is breaking, which makes it brief
 */
public record AudienceItem(
        String whatChanged,
        String whyChanged,
        String technicalDetail,
        String migrationStep,
        String migrationKind,
        String limitations,
        String narrative,
        String headline,
        String relevance,
        String reference,
        int pullRequestNumber,
        String pullRequestUrl,
        List<LinkedIssue> linkedIssues
) {

    /** The variables a template may use, in the order the editor offers them. */
    public static final List<String> VARIABLES = List.of(
            "whatChanged",
            "whyChanged",
            "technicalDetail",
            "migrationStep",
            "migrationKind",
            "migrationRequired",
            "migrationConditional",
            "migrationRecommended",
            "migrationUnspecified",
            "limitations",
            "narrative",
            "headline",
            "relevance",
            "brief",
            "reference",
            "pullRequestNumber",
            "pullRequestUrl",
            "linkedIssues"
    );

    static final String REQUIRED = "required";
    static final String CONDITIONAL = "conditional";
    static final String RECOMMENDED = "recommended";
    static final String UNSPECIFIED = "unspecified";
    public static final String BRIEF = "brief";
    public static final String FULL = "full";

    static final AudienceItem SAMPLE = new AudienceItem(
            "A safer deployment workflow is now available.",
            "Teams needed clearer control before publishing release notes.",
            "Approval renders Markdown from the stored neutral summary.",
            "Re-save each audience to adopt the new template.",
            RECOMMENDED,
            "Notes are written in the Organization's output language only.",
            "Release managers can review audience-specific notes before they are published.",
            "Review each audience's note before publishing.",
            FULL,
            "#101",
            101,
            "https://github.com/acme/app/pull/101",
            List.of(new LinkedIssue("APP-7", "Publish release notes per audience", "", "Story", "Done",
                    "https://acme.atlassian.net/browse/APP-7"))
    );

    public AudienceItem {
        whatChanged = Objects.requireNonNullElse(whatChanged, "");
        whyChanged = Objects.requireNonNullElse(whyChanged, "");
        technicalDetail = Objects.requireNonNullElse(technicalDetail, "");
        migrationStep = Objects.requireNonNullElse(migrationStep, "");
        migrationKind = migrationStep.isEmpty() ? "" : Objects.requireNonNullElse(migrationKind, UNSPECIFIED);
        limitations = Objects.requireNonNullElse(limitations, "");
        narrative = Objects.requireNonNullElse(narrative, "");
        headline = headline == null || headline.isBlank() ? whatChanged : headline;
        relevance = BRIEF.equals(relevance) ? BRIEF : FULL;
        reference = Objects.requireNonNullElse(reference, "#" + pullRequestNumber);
        pullRequestUrl = Objects.requireNonNullElse(pullRequestUrl, "");
        linkedIssues = linkedIssues == null ? List.of() : List.copyOf(linkedIssues);
    }

    private static Map<String, Object> issue(LinkedIssue issue) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("key", issue.key());
        context.put("title", issue.title());
        context.put("type", issue.type());
        context.put("status", issue.status());
        context.put("url", issue.url());
        return context;
    }

    Map<String, Object> context() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("whatChanged", whatChanged);
        context.put("whyChanged", whyChanged);
        context.put("technicalDetail", technicalDetail);
        context.put("migrationStep", migrationStep);
        context.put("migrationKind", migrationKind);
        // One section per kind, since Mustache cannot compare values.
        context.put("migrationRequired", REQUIRED.equals(migrationKind));
        context.put("migrationConditional", CONDITIONAL.equals(migrationKind));
        context.put("migrationRecommended", RECOMMENDED.equals(migrationKind));
        context.put("migrationUnspecified", UNSPECIFIED.equals(migrationKind));
        context.put("limitations", limitations);
        context.put("narrative", narrative);
        context.put("headline", headline);
        context.put("relevance", relevance);
        // A section, since Mustache cannot compare values: presets hide detail lines inside {{^brief}}.
        context.put("brief", BRIEF.equals(relevance));
        // #12 on GitHub, !12 on GitLab, the issue key on Jira and Linear.
        context.put("reference", reference);
        context.put("pullRequestNumber", pullRequestNumber);
        context.put("pullRequestUrl", pullRequestUrl);
        // A section over an empty list simply disappears, so a template may always use it.
        context.put("linkedIssues", linkedIssues.stream().map(AudienceItem::issue).toList());
        return context;
    }
}
