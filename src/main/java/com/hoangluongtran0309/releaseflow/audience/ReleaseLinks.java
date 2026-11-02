package com.hoangluongtran0309.releaseflow.audience;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import java.io.IOException;
import java.io.Writer;
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

    // Every value is a FooterValue, so a section over one still reaches the others.
    Map<String, Object> context() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("version", new FooterValue(version, context));
        context.put("previousVersion", new FooterValue(previousVersion, context));
        context.put("compareUrl", new FooterValue(compareUrl, context));
        context.put("upgradeGuideUrl", new FooterValue(upgradeGuideUrl, context));
        context.put("releaseUrl", new FooterValue(releaseUrl, context));
        return context;
    }

    /**
     * One footer value. It prints as its text, and a section over it renders only when the
     * text is not empty, with every footer value still in reach and {@code {{.}}} as its
     * own text. A plain string would not do: in standards mode a name inside a section is
     * looked up only in the section's own value, so {@code {{#compareUrl}}} could not
     * print {@code {{version}}}.
     */
    private static final class FooterValue implements Mustache.InvertibleLambda {

        private final String text;
        private final Map<String, Object> values;

        FooterValue(String text, Map<String, Object> values) {
            this.text = text;
            this.values = values;
        }

        @Override
        public void execute(Template.Fragment fragment, Writer out) throws IOException {
            if (!text.isEmpty()) {
                fragment.execute(new Scope(values, text), out);
            }
        }

        @Override
        public void executeInverse(Template.Fragment fragment, Writer out) throws IOException {
            if (text.isEmpty()) {
                fragment.execute(new Scope(values, text), out);
            }
        }

        @Override
        public String toString() {
            return text;
        }
    }

    // The footer values inside a section, which print as the section's own value.
    private static final class Scope extends LinkedHashMap<String, Object> {

        private final String text;

        Scope(Map<String, Object> values, String text) {
            super(values);
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
