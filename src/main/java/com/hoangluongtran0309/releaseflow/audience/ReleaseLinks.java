package com.hoangluongtran0309.releaseflow.audience;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The release-wide values an audience's footer template sees (ADR-0037). Every value is
 * text and never null; an empty one is false in a section, so
 * {@code {{#compareUrl}}...{{/compareUrl}}} disappears when there is no comparison.
 *
 * @param previousVersion the version of the release this one follows, or empty
 * @param compareUrl the code host's comparison of the two versions as tags, or empty
 * @param upgradeGuideUrl the guide a person linked to the release, or empty
 * @param releaseUrl the code host's page for the version as a tag, or empty
 */
public record ReleaseLinks(
        String version,
        String previousVersion,
        String compareUrl,
        String upgradeGuideUrl,
        String releaseUrl
) {

    /** The variables a footer may use, in the order the editor offers them. */
    public static final List<String> VARIABLES = List.of(
            "version",
            "previousVersion",
            "compareUrl",
            "upgradeGuideUrl",
            "releaseUrl"
    );

    static final ReleaseLinks SAMPLE = new ReleaseLinks(
            "v1.4.0",
            "v1.3.0",
            "https://github.com/acme/app/compare/v1.3.0...v1.4.0",
            "https://docs.acme.example/upgrade/1.4",
            "https://github.com/acme/app/releases/tag/v1.4.0"
    );

    public ReleaseLinks {
        version = Objects.requireNonNullElse(version, "");
        previousVersion = Objects.requireNonNullElse(previousVersion, "");
        compareUrl = Objects.requireNonNullElse(compareUrl, "");
        upgradeGuideUrl = Objects.requireNonNullElse(upgradeGuideUrl, "");
        releaseUrl = Objects.requireNonNullElse(releaseUrl, "");
    }

    // Every value is a SectionValue, so a section over one still reaches the others.
    Map<String, Object> context() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("version", new SectionValue(version, context));
        context.put("previousVersion", new SectionValue(previousVersion, context));
        context.put("compareUrl", new SectionValue(compareUrl, context));
        context.put("upgradeGuideUrl", new SectionValue(upgradeGuideUrl, context));
        context.put("releaseUrl", new SectionValue(releaseUrl, context));
        return context;
    }
}
