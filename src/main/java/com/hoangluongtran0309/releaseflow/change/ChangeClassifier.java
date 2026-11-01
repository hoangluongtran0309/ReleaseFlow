package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.category.CategoryGroup;
import com.hoangluongtran0309.releaseflow.category.CategoryRef;
import com.hoangluongtran0309.releaseflow.source.ChangedFiles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Explainable, fixed rules. A documentation-only file list outranks a title type, which
 * outranks labels; anything the rules do not recognize is Unknown. Unknown, breaking,
 * security, and triggered changes always need human review.
 *
 * <p>Security is a flag beside the category, set only by evidence that says so without an
 * AI: a security label, or a CVE identifier in the title, description, or a label
 * (ADR-0037). Broad words such as "auth" stay sensitive-path or keyword evidence.
 *
 * <p>Each rule names a group and a preferred category code. The Organization's catalog
 * decides the result: the preferred category if it is active, otherwise the first active
 * category of the group by code. A group without an active category locks nothing.
 */
final class ChangeClassifier {

    private static final Pattern TITLE_TYPE = Pattern.compile(
            "^(feat|fix|perf|docs|refactor|chore|ci|build|test)(\\([^)]*\\))?(!)?:\\s*\\S",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BREAKING_FOOTER = Pattern.compile("(?m)^BREAKING[ -]CHANGE:\\s*\\S");
    private static final Rule FEATURE = new Rule(CategoryGroup.FEATURE, "FEATURE");
    private static final Rule FIX = new Rule(CategoryGroup.FIX, "FIX");
    private static final Rule PERFORMANCE = new Rule(CategoryGroup.PERFORMANCE, "PERFORMANCE");
    private static final Rule DOCUMENTATION = new Rule(CategoryGroup.DOCUMENTATION, "DOCUMENTATION");
    private static final Rule MAINTENANCE = new Rule(CategoryGroup.MAINTENANCE, "MAINTENANCE");
    private static final Map<String, Rule> TITLE_TYPES = Map.of(
            "feat", FEATURE,
            "fix", FIX,
            "perf", PERFORMANCE,
            "docs", DOCUMENTATION,
            "refactor", MAINTENANCE,
            "chore", MAINTENANCE,
            "ci", MAINTENANCE,
            "build", MAINTENANCE,
            "test", MAINTENANCE
    );
    private static final Map<String, Rule> LABELS = Map.ofEntries(
            Map.entry("enhancement", FEATURE),
            Map.entry("feature", FEATURE),
            Map.entry("bug", FIX),
            Map.entry("bugfix", FIX),
            Map.entry("performance", PERFORMANCE),
            Map.entry("documentation", DOCUMENTATION),
            Map.entry("docs", DOCUMENTATION),
            Map.entry("dependencies", MAINTENANCE),
            Map.entry("maintenance", MAINTENANCE),
            Map.entry("chore", MAINTENANCE),
            Map.entry("refactor", MAINTENANCE)
    );
    private static final Set<String> BREAKING_LABELS = Set.of("breaking-change", "breaking change", "breaking");
    private static final Set<String> SECURITY_LABELS = Set.of("security", "security-fix", "security fix", "vulnerability");
    private static final Pattern CVE = Pattern.compile("\\bCVE-\\d{4}-\\d{4,}\\b", Pattern.CASE_INSENSITIVE);

    private ChangeClassifier() {
    }

    /**
     * @param linkedContext what the Project's issue tracker could add, if it has one
     * @param sensitivePaths the Project's effective sensitive-path rules
     * @param catalog the Organization's active categories
     */
    static ChangeClassification classify(
            MergedPullRequest pullRequest,
            ChangedFiles files,
            LinkedContext linkedContext,
            SensitivePaths sensitivePaths,
            List<CategoryRef> catalog
    ) {
        List<String> reasons = new ArrayList<>();
        List<ReviewTrigger> triggers = new ArrayList<>();
        boolean breaking = false;
        boolean security = false;

        if (files.isCollected()) {
            sensitivePaths.matches(files.files()).stream()
                    .map(ReviewTrigger::sensitivePath)
                    .forEach(triggers::add);
        } else if (files.isNotSupported()) {
            // Nothing failed: this source has no files, so its own words are the evidence.
            triggers.addAll(SensitiveKeywords.matches(pullRequest.title(), pullRequest.description()));
        } else {
            triggers.add(ReviewTrigger.changedFilesUnavailable());
        }
        // An issue the tracker would not show is evidence nobody read, so a person must.
        if (linkedContext.status().incomplete()) {
            triggers.add(ReviewTrigger.linkedContextUnavailable(linkedContext.status()));
        }
        CategoryRef documentation = null;
        boolean documentationOnly = files.isCollected()
                && !files.files().isEmpty()
                && files.files().stream().allMatch(file -> isDocumentation(file.path()));
        if (documentationOnly) {
            reasons.add("All changed files are documentation");
            documentation = resolve(catalog, DOCUMENTATION, reasons);
        }

        CategoryRef titleCategory = null;
        Matcher title = TITLE_TYPE.matcher(pullRequest.title().strip());
        if (title.find()) {
            String type = title.group(1).toLowerCase(Locale.ROOT);
            reasons.add("Title type \"" + type + "\"");
            titleCategory = resolve(catalog, TITLE_TYPES.get(type), reasons);
            if (title.group(3) != null) {
                breaking = true;
                reasons.add("Title breaking marker \"!\"");
            }
        }

        Map<String, CategoryRef> labelCategories = new LinkedHashMap<>();
        for (String label : pullRequest.labels()) {
            String name = label.strip().toLowerCase(Locale.ROOT);
            if (SECURITY_LABELS.contains(name)) {
                security = true;
                reasons.add("Security label \"" + label + "\"");
            } else if (BREAKING_LABELS.contains(name)) {
                breaking = true;
                reasons.add("Breaking label \"" + label + "\"");
            } else if (LABELS.containsKey(name)) {
                reasons.add("Label \"" + label + "\"");
                CategoryRef resolved = resolve(catalog, LABELS.get(name), reasons);
                if (resolved != null) {
                    labelCategories.put(label, resolved);
                }
            }
        }

        if (pullRequest.description() != null && BREAKING_FOOTER.matcher(pullRequest.description()).find()) {
            breaking = true;
            reasons.add("BREAKING CHANGE footer");
        }

        String cve = cve(pullRequest);
        if (cve != null) {
            security = true;
            reasons.add("CVE identifier \"" + cve + "\"");
        }

        Set<String> labelCodes = labelCategories.values().stream().map(CategoryRef::code).collect(Collectors.toSet());
        CategoryRef category;
        if (documentation != null) {
            category = documentation;
        } else if (titleCategory != null) {
            category = titleCategory;
        } else if (labelCodes.size() == 1) {
            category = labelCategories.values().iterator().next();
        } else {
            category = CategoryRef.UNKNOWN;
            if (labelCodes.size() > 1) {
                reasons.add("Conflicting labels " + labelCategories.keySet().stream()
                        .map(label -> "\"" + label + "\"")
                        .collect(Collectors.joining(", ")));
            } else if (reasons.stream().noneMatch(reason -> reason.startsWith("No active category"))) {
                reasons.add("No category rule matched");
            }
        }

        return new ChangeClassification(
                category,
                breaking,
                security,
                breaking || security || category.isUnknown() || !triggers.isEmpty(),
                reasons,
                triggers
        );
    }

    // The first CVE identifier in the title, the description, or a label, as written.
    private static String cve(MergedPullRequest pullRequest) {
        List<String> texts = new ArrayList<>();
        texts.add(pullRequest.title());
        texts.add(pullRequest.description());
        texts.addAll(pullRequest.labels());
        for (String text : texts) {
            if (text != null) {
                Matcher matcher = CVE.matcher(text);
                if (matcher.find()) {
                    return matcher.group();
                }
            }
        }
        return null;
    }

    // The preferred category if active, else the first active one of its group by code.
    private static CategoryRef resolve(List<CategoryRef> catalog, Rule rule, List<String> reasons) {
        CategoryRef resolved = catalog.stream()
                .filter(category -> category.code().equals(rule.preferredCode()))
                .findFirst()
                .or(() -> catalog.stream()
                        .filter(category -> category.group() == rule.group() && !category.isUnknown())
                        .min(Comparator.comparing(CategoryRef::code)))
                .orElse(null);
        if (resolved == null) {
            String reason = "No active category in the " + rule.group().getLabel() + " group";
            if (!reasons.contains(reason)) {
                reasons.add(reason);
            }
        }
        return resolved;
    }

    private static boolean isDocumentation(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.startsWith("docs/")
                || normalized.endsWith(".md")
                || normalized.endsWith(".adoc")
                || normalized.endsWith(".rst");
    }

    private record Rule(CategoryGroup group, String preferredCode) {
    }
}
