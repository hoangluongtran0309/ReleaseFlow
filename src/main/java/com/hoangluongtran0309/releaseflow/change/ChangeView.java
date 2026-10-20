package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.category.CategoryGroup;
import com.hoangluongtran0309.releaseflow.jira.LinkedIssue;
import com.hoangluongtran0309.releaseflow.category.CategoryRef;
import com.hoangluongtran0309.releaseflow.source.ChangedFile;
import com.hoangluongtran0309.releaseflow.source.SourceType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ChangeView(
        UUID id,
        int pullRequestNumber,
        String title,
        String description,
        String authorLogin,
        List<String> labels,
        String targetBranch,
        String mergeCommitSha,
        Instant mergedAt,
        String url,
        String category,
        String categoryName,
        CategoryGroup categoryGroup,
        boolean breaking,
        boolean needsReview,
        List<String> reasons,
        ClassificationSource classificationSource,
        AiStatus aiStatus,
        String aiModel,
        String aiFailure,
        boolean aiEligible,
        UUID reviewedBy,
        String reviewerName,
        Instant reviewedAt,
        ProcessingStatus processingStatus,
        ChangedFileStatus changedFileStatus,
        LinkedContextStatus linkedContextStatus,
        List<LinkedIssue> linkedIssues,
        List<ChangedFile> changedFiles,
        List<ReviewTrigger> reviewTriggers,
        NeutralSummary neutralSummary,
        String contentLanguage,
        AiProvider aiProvider,
        Map<String, String> audienceNarratives,
        String summaryEditorName,
        Instant summaryEditedAt,
        ContextAssessment context,
        UUID sourceId,
        ChangeOrigin origin,
        SourceType sourceType,
        ChangeExclusion exclusion
) {

    // An issue key as Jira and Linear print it, read from the address of the issue.
    private static final Pattern JIRA_KEY = Pattern.compile("/browse/([A-Z][A-Z0-9_]*-[0-9]+)/?$");
    private static final Pattern LINEAR_IDENTIFIER = Pattern.compile("/issue/([A-Z][A-Z0-9]*-[0-9]+)(?:/|$)");

    public ChangeView {
        // Sorted by audience code so pages list narratives in a stable order.
        audienceNarratives = audienceNarratives == null
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(audienceNarratives));
        linkedIssues = linkedIssues == null ? List.of() : List.copyOf(linkedIssues);
    }

    /** What this change says to one audience, or an empty string. */
    public String narrative(String audienceCode) {
        return audienceNarratives.getOrDefault(audienceCode, "");
    }

    /** The same change with its summary and narratives written in another language. */
    public ChangeView withContent(NeutralSummary summary, Map<String, String> narratives, String language) {
        return new ChangeView(id, pullRequestNumber, title, description, authorLogin, labels, targetBranch, mergeCommitSha,
                mergedAt, url, category, categoryName, categoryGroup, breaking, needsReview, reasons,
                classificationSource, aiStatus, aiModel, aiFailure, aiEligible, reviewedBy, reviewerName, reviewedAt,
                processingStatus, changedFileStatus, linkedContextStatus, linkedIssues, changedFiles, reviewTriggers, summary, language, aiProvider,
                narratives, summaryEditorName, summaryEditedAt, context, sourceId, origin, sourceType, exclusion);
    }

    /**
     * How a reader of the source names this change: {@code #12} for a GitHub pull request,
     * {@code !12} for a GitLab merge request, and the issue key, such as {@code APP-12},
     * for Jira and Linear. A key that cannot be read from the issue's address, and a
     * change recorded before sources had types, fall back to {@code #12}.
     */
    public String reference() {
        if (sourceType == SourceType.GITLAB) {
            return "!" + pullRequestNumber;
        }
        Pattern key = switch (sourceType == null ? SourceType.GITHUB : sourceType) {
            case JIRA -> JIRA_KEY;
            case LINEAR -> LINEAR_IDENTIFIER;
            default -> null;
        };
        if (key != null && url != null) {
            Matcher matcher = key.matcher(url);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "#" + pullRequestNumber;
    }

    /** The category the change carries, as a snapshot. */
    public CategoryRef categoryRef() {
        return new CategoryRef(category, categoryName, categoryGroup);
    }

    public boolean unknownCategory() {
        return CategoryRef.UNKNOWN.code().equals(category);
    }

    public boolean processing() {
        return processingStatus == ProcessingStatus.PROCESSING;
    }

    /** Whether a person decided this change will never ship. */
    public boolean excluded() {
        return exclusion != null;
    }

    static ChangeView from(Change change) {
        return new ChangeView(
                change.getId(),
                change.getPullRequestNumber(),
                change.getTitle(),
                change.getDescription(),
                change.getAuthorLogin(),
                change.getLabels(),
                change.getTargetBranch(),
                change.getMergeCommitSha(),
                change.getMergedAt(),
                change.getUrl(),
                change.getCategory().code(),
                change.getCategory().displayName(),
                change.getCategory().group(),
                change.isBreaking(),
                change.isNeedsReview(),
                change.getClassificationReasons(),
                change.getClassificationSource(),
                change.getAiStatus(),
                change.getAiModel(),
                change.getAiFailure(),
                change.isAiEligible(),
                change.getReviewedBy(),
                change.getReviewerName(),
                change.getReviewedAt(),
                change.getProcessingStatus(),
                change.getChangedFileStatus(),
                change.getLinkedContextStatus(),
                change.getLinkedIssues(),
                change.getChangedFiles(),
                change.getReviewTriggers(),
                change.getNeutralSummary(),
                change.getContentLanguage(),
                change.getAiProvider(),
                change.getAudienceNarratives(),
                change.getSummaryEditorName(),
                change.getSummaryEditedAt(),
                change.getContext(),
                change.getSourceId(),
                change.getOrigin(),
                change.getSourceType(),
                change.getExclusion()
        );
    }
}
