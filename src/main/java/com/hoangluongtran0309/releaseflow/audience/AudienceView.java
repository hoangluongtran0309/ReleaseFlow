package com.hoangluongtran0309.releaseflow.audience;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * An audience with its main templates and its templates for other languages, keyed by
 * language tag. Each language has an item template and a footer (ADR-0037).
 */
public record AudienceView(
        UUID id,
        String code,
        String displayName,
        String communicationIntent,
        String templateBody,
        String footerTemplateBody,
        boolean preset,
        Instant createdAt,
        Instant updatedAt,
        Map<String, String> templateVariants,
        Map<String, String> footerTemplateVariants
) {

    public AudienceView {
        footerTemplateBody = footerTemplateBody == null ? "" : footerTemplateBody;
        templateVariants = sorted(templateVariants);
        footerTemplateVariants = sorted(footerTemplateVariants);
    }

    /** The template for one language: its variant if there is one, otherwise the main template. */
    public String templateFor(String language) {
        return templateVariants.getOrDefault(language, templateBody);
    }

    /** The footer for one language: its variant's if there is one, otherwise the main footer. */
    public String footerFor(String language) {
        return footerTemplateVariants.getOrDefault(language, footerTemplateBody);
    }

    private static Map<String, String> sorted(Map<String, String> variants) {
        return variants == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(variants));
    }
}
