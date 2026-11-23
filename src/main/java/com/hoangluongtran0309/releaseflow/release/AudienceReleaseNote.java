package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.translation.GlossaryUse;
import com.hoangluongtran0309.releaseflow.translation.TranslationState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The note one audience gets in one language for an approved or published release. It
 * is rendered at approval from a snapshot of the audience's template and footer for that
 * language, and follows later summary edits and translations until a person edits it;
 * then it is manual and never rendered again. The database allows writes only while the
 * release is approved, and publication only once every note is ready, so a published note
 * is final.
 */
@Entity
@Table(name = "release_audience_notes")
class AudienceReleaseNote {

    @Id
    private UUID id;

    @Column(name = "release_id", nullable = false, updatable = false)
    private UUID releaseId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "audience_id", nullable = false, updatable = false)
    private UUID audienceId;

    @Column(name = "audience_code", nullable = false, length = 64, updatable = false)
    private String audienceCode;

    @Column(name = "audience_name", nullable = false, length = 120, updatable = false)
    private String audienceName;

    @Column(nullable = false, length = 16, updatable = false)
    private String language;

    @Column(name = "template_body_snapshot", nullable = false, columnDefinition = "text", updatable = false)
    private String templateBodySnapshot;

    // Blank for a note written before footers existed (ADR-0037, V34).
    @Column(name = "footer_template_snapshot", nullable = false, columnDefinition = "text", updatable = false)
    private String footerTemplateSnapshot = "";

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "auto_rerender", nullable = false)
    private boolean autoRerender;

    @Enumerated(EnumType.STRING)
    @Column(name = "translation_status", nullable = false, length = 10)
    private TranslationState.Status translationStatus;

    // What the translations behind the note did with the glossary (ADR-0036).
    @Enumerated(EnumType.STRING)
    @Column(name = "glossary_status", nullable = false, length = 20)
    private GlossaryUse.Status glossaryStatus = GlossaryUse.Status.NONE;

    @Column(name = "glossary_reason", length = 40)
    private String glossaryReason;

    @Column(name = "last_edited_by")
    private UUID lastEditedBy;

    @Column(name = "last_editor_name", length = 120)
    private String lastEditorName;

    // When a person last edited the note (V42); null on a note nobody edited, and on one edited
    // before V42 whose release was already published.
    @Column(name = "last_edited_at")
    private Instant lastEditedAt;

    // The SHA-256 of the text the note had when it was last ready (V42); null until it is.
    @Column(name = "ready_content_sha256", length = 64)
    private String readyContentSha256;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AudienceReleaseNote() {
    }

    AudienceReleaseNote(
            UUID id,
            Release release,
            UUID audienceId,
            String audienceCode,
            String audienceName,
            String language,
            String templateBodySnapshot,
            String footerTemplateSnapshot,
            String content,
            TranslationState.Status translationStatus,
            GlossaryUse glossary,
            Instant createdAt
    ) {
        this.id = id;
        this.releaseId = release.getId();
        this.organizationId = release.getOrganizationId();
        this.projectId = release.getProjectId();
        this.audienceId = audienceId;
        this.audienceCode = audienceCode;
        this.audienceName = audienceName;
        this.language = language;
        this.templateBodySnapshot = templateBodySnapshot;
        this.footerTemplateSnapshot = footerTemplateSnapshot;
        this.content = content;
        this.autoRerender = true;
        this.translationStatus = translationStatus;
        this.glossaryStatus = glossary.status();
        this.glossaryReason = glossary.reason();
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        if (translationStatus == TranslationState.Status.READY) {
            this.readyContentSha256 = RenderedNote.hash(content);
        }
    }

    /**
     * The note as its template renders it now, with how far its translations have come; a
     * manual note is kept. Its update time is when its ready text last changed (ADR-0047):
     * while its language is translating, or a translation failed, the note shows its changes'
     * text untranslated and keeps the time, and once ready it moves only if the text differs
     * from the text it had when it was last ready.
     */
    void rerender(String content, TranslationState.Status translationStatus, GlossaryUse glossary, Instant at) {
        if (!autoRerender) {
            return;
        }
        this.content = content;
        this.translationStatus = translationStatus;
        this.glossaryStatus = glossary.status();
        this.glossaryReason = glossary.reason();
        if (translationStatus == TranslationState.Status.READY) {
            String ready = RenderedNote.hash(content);
            if (!ready.equals(readyContentSha256)) {
                this.readyContentSha256 = ready;
                this.updatedAt = at;
            }
        }
    }

    GlossaryUse glossary() {
        return new GlossaryUse(glossaryStatus, glossaryReason);
    }

    /** A person's text, ready as written. The note never follows its template again. */
    void edit(String content, UUID editor, String editorName, Instant at) {
        this.content = content;
        this.translationStatus = TranslationState.Status.READY;
        // The glossary does not apply to words a person wrote.
        this.glossaryStatus = GlossaryUse.Status.NONE;
        this.glossaryReason = null;
        this.autoRerender = false;
        this.lastEditedBy = editor;
        this.lastEditorName = editorName;
        this.lastEditedAt = at;
        this.readyContentSha256 = RenderedNote.hash(content);
        this.updatedAt = at;
    }

    /** The note as published, or as it stands while its release is approved: revision 1. */
    AudienceNoteView view() {
        return view(null);
    }

    /** The note as its latest revision reads, or as revision 1 when there is none. */
    AudienceNoteView view(ReleaseNoteRevision latest) {
        if (latest == null) {
            return new AudienceNoteView(id, audienceId, audienceCode, audienceName, language, content, autoRerender,
                    lastEditorName, lastEditedAt, updatedAt, translationStatus, glossaryStatus, glossaryReason, 1);
        }
        GlossaryUse glossary = latest.glossary();
        return new AudienceNoteView(id, audienceId, audienceCode, audienceName, language, latest.getContent(),
                autoRerender, lastEditorName, lastEditedAt, latest.getCreatedAt(), translationStatus, glossary.status(),
                glossary.reason(), latest.getRevision());
    }

    /** Each note as its latest revision reads, from the release's latest revisions. */
    static List<AudienceNoteView> views(List<AudienceReleaseNote> notes, List<ReleaseNoteRevision> latest) {
        Map<UUID, ReleaseNoteRevision> byNote = latest.stream()
                .collect(Collectors.toMap(ReleaseNoteRevision::getNoteId, Function.identity()));
        return notes.stream().map(note -> note.view(byNote.get(note.id))).toList();
    }

    /** Revision 1: the note as its release was published. */
    ReleaseNoteRevisionView publishedRevision(Release release) {
        return new ReleaseNoteRevisionView(1, ReleaseNoteRevisionView.Origin.PUBLISHED, content, null,
                release.getPublisherName(), release.getPublishedAt(), glossaryStatus, glossaryReason);
    }

    UUID getId() {
        return id;
    }

    String getAudienceCode() {
        return audienceCode;
    }

    String getAudienceName() {
        return audienceName;
    }

    String getLanguage() {
        return language;
    }

    boolean isAutoRerender() {
        return autoRerender;
    }

    TranslationState.Status getTranslationStatus() {
        return translationStatus;
    }

    String getTemplateBodySnapshot() {
        return templateBodySnapshot;
    }

    String getFooterTemplateSnapshot() {
        return footerTemplateSnapshot;
    }
}
