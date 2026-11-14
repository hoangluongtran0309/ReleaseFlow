package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.change.ChangeView;
import com.hoangluongtran0309.releaseflow.change.MissingNarrative;
import com.hoangluongtran0309.releaseflow.translation.GlossaryUse;
import com.hoangluongtran0309.releaseflow.translation.TranslationState;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A release as shown to users. {@code notes} holds the stored note of each audience,
 * written at approval. {@code preview} groups the live changes of an unpublished
 * release; for a release published before audiences existed, {@code preview} and
 * {@code markdown} come from its legacy snapshot instead. {@code decisions} holds the
 * review decision on each change that has one. {@code glossaries} says, for each note
 * language whose pair has a glossary, whether its notes' translations used it.
 * {@code previousVersion} is the version of {@code previousReleaseId}, the published
 * release this one follows, which the notes compare against (ADR-0037).
 * {@code linkedIssues} holds each linked issue of its changes as of review, and
 * {@code linkedIssueRead} the state of reading them ({@code null} before a review was
 * requested); {@code foldingSuggestions} holds, while in review, each tracker change a
 * code change of the release may carry, and {@code foldDecisions} what people decided
 * (ADR-0041).
 */
public record ReleaseView(
        UUID id,
        UUID projectId,
        String version,
        String summary,
        ReleaseStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant plannedReleaseAt,
        String upgradeGuideUrl,
        UUID previousReleaseId,
        String previousVersion,
        List<ChangeView> changes,
        List<ReleaseDecisionView> decisions,
        int reviewedCount,
        List<ReleaseNoteSection> preview,
        Instant approvedAt,
        String approverName,
        Instant publishedAt,
        String publisherName,
        String markdown,
        List<AudienceNoteView> notes,
        List<ReleaseGlossaryView> glossaries,
        List<LinkedIssueStatusView> linkedIssues,
        String linkedIssueRead,
        List<FoldingSuggestion> foldingSuggestions,
        List<FoldDecisionView> foldDecisions,
        List<MissingNarrative> missingNarratives
) {

    public ReleaseView {
        changes = List.copyOf(changes);
        decisions = List.copyOf(decisions);
        preview = List.copyOf(preview);
        notes = List.copyOf(notes);
        glossaries = List.copyOf(glossaries);
        linkedIssues = List.copyOf(linkedIssues);
        foldingSuggestions = List.copyOf(foldingSuggestions);
        foldDecisions = List.copyOf(foldDecisions);
        missingNarratives = List.copyOf(missingNarratives);
    }

    /** The linked issues still open while their change ships, whose status nobody accepted (ADR-0041). */
    public List<LinkedIssueStatusView> openLinkedIssues() {
        return linkedIssues.stream().filter(LinkedIssueStatusView::warning).toList();
    }

    /** The folding suggestions for one tracker change of this release. */
    public List<FoldingSuggestion> foldingSuggestionsFor(UUID trackerChangeId) {
        return foldingSuggestions.stream().filter(suggestion -> suggestion.trackerChangeId().equals(trackerChangeId)).toList();
    }

    /** The decision that folded a tracker change into a code change, if there was one. */
    public FoldDecisionView foldDecisionFor(UUID trackerChangeId) {
        return foldDecisions.stream().filter(decision -> decision.trackerChangeId().equals(trackerChangeId))
                .findFirst().orElse(null);
    }

    /**
     * One entry per note language, from the notes that follow their templates, which all
     * share the language's translations; a language's notes that a person wrote say nothing.
     */
    static List<ReleaseGlossaryView> glossaries(List<AudienceNoteView> notes) {
        Map<String, ReleaseGlossaryView> byLanguage = new LinkedHashMap<>();
        for (AudienceNoteView note : notes) {
            if (note.autoRerender() && note.glossaryStatus() != GlossaryUse.Status.NONE) {
                byLanguage.putIfAbsent(note.language(),
                        new ReleaseGlossaryView(note.language(), note.glossaryStatus(), note.glossaryReason()));
            }
        }
        return List.copyOf(byLanguage.values());
    }

    /** The decision on a change of this release, or {@code null} if it has none yet. */
    public ReleaseDecisionView decision(UUID changeId) {
        return decisions.stream().filter(decision -> decision.changeId().equals(changeId)).findFirst().orElse(null);
    }

    /** Whether a person has to write a change's summary before the release is approved. */
    public boolean needsSummary(UUID changeId) {
        return changes.stream()
                .filter(change -> change.id().equals(changeId))
                .anyMatch(ReleaseNotePreview::needsSummary);
    }

    public boolean breaking() {
        return changes.stream().anyMatch(ChangeView::breaking);
    }

    /** A release published before audiences existed, shown from its single legacy note. */
    public boolean legacyNote() {
        return markdown != null;
    }

    /** Whether every stored note is ready, so the release can be published. */
    public boolean translationsReady() {
        return notes.stream().allMatch(note -> note.translationStatus() == TranslationState.Status.READY);
    }

    public long notesTranslating() {
        return notes.stream().filter(note -> note.translationStatus() == TranslationState.Status.PENDING).count();
    }

    public long notesUntranslated() {
        return notes.stream().filter(note -> note.translationStatus() == TranslationState.Status.FAILED).count();
    }

    public boolean fullyReviewed() {
        return !changes.isEmpty() && reviewedCount == changes.size();
    }
}
