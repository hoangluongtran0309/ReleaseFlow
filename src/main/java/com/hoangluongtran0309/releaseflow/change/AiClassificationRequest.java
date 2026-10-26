package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.OutputLanguage;
import com.hoangluongtran0309.releaseflow.audience.AudienceBrief;
import com.hoangluongtran0309.releaseflow.category.CategoryRef;
import com.hoangluongtran0309.releaseflow.jira.LinkedIssue;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * What is sent to the AI for one change. The author is deliberately not included.
 *
 * @param lockedCategory the category the fixed rules chose, which the AI must return,
 *     or null when the rules left the change Unknown
 * @param categories the Organization's active categories, Unknown included
 * @param audiences the audiences to write a narrative for
 * @param contextThreshold the context score below which a change needs review
 * @param trackerIssue whether the change is an issue an issue tracker completed rather
 *     than merged code
 * @param projectContext what the Project's administrators wrote about the product, or
 *     empty
 */
record AiClassificationRequest(
        UUID changeId,
        String title,
        String description,
        List<String> labels,
        String targetBranch,
        List<LinkedIssue> linkedIssues,
        OutputLanguage outputLanguage,
        CategoryRef lockedCategory,
        List<CategoryRef> categories,
        List<AudienceBrief> audiences,
        int contextThreshold,
        boolean trackerIssue,
        String projectContext
) {

    AiClassificationRequest {
        labels = List.copyOf(labels);
        projectContext = projectContext == null ? "" : projectContext;
        audiences = List.copyOf(audiences);
        // Unknown is always a valid answer, even if a caller passed a catalog without it.
        categories = categories.stream().anyMatch(CategoryRef::isUnknown)
                ? List.copyOf(categories)
                : Stream.concat(categories.stream(), Stream.of(CategoryRef.UNKNOWN)).toList();
    }

    static AiClassificationRequest of(
            UUID changeId,
            MergedPullRequest pullRequest,
            List<LinkedIssue> linkedIssues,
            OutputLanguage outputLanguage,
            CategoryRef rulesCategory,
            List<CategoryRef> categories,
            List<AudienceBrief> audiences,
            int contextThreshold,
            SourceType sourceType,
            String projectContext
    ) {
        return new AiClassificationRequest(
                changeId,
                pullRequest.title(),
                pullRequest.description(),
                pullRequest.labels(),
                pullRequest.targetBranch(),
                linkedIssues,
                outputLanguage,
                rulesCategory.isUnknown() ? null : rulesCategory,
                categories,
                audiences,
                contextThreshold,
                sourceType != null && sourceType.isIssueTracker(),
                projectContext
        );
    }

    List<String> audienceCodes() {
        return audiences.stream().map(AudienceBrief::code).toList();
    }

    List<String> categoryCodes() {
        return categories.stream().map(CategoryRef::code).toList();
    }
}
