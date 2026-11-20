package com.hoangluongtran0309.releaseflow.release;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A correction of a published note (ADR-0046): the revision it corrects, how its text was
 * made, and why. A correction by hand sends its Markdown; one rendered again sends the hash
 * of the preview a person read instead, and the text is rendered again when it is saved.
 * The reason is published wherever the correction is.
 */
public class NoteCorrectionRequest {

    static final int MAX_REASON_LENGTH = 500;

    @NotNull(message = "{validation.noteCorrection.correctsRequired}")
    @Min(value = 1, message = "{validation.noteCorrection.correctsRequired}")
    private Integer corrects;

    private ReleaseNoteRevisionView.Origin origin = ReleaseNoteRevisionView.Origin.MANUAL;

    @Size(max = ReleaseNoteRequest.MAX_LENGTH, message = "{validation.releaseNote.tooLong}")
    private String content;

    private String previewHash;

    @NotBlank(message = "{validation.noteCorrection.reasonRequired}")
    @Size(max = MAX_REASON_LENGTH, message = "{validation.noteCorrection.reasonTooLong}")
    private String reason;

    public Integer getCorrects() {
        return corrects;
    }

    public void setCorrects(Integer corrects) {
        this.corrects = corrects;
    }

    public ReleaseNoteRevisionView.Origin getOrigin() {
        return origin;
    }

    public void setOrigin(ReleaseNoteRevisionView.Origin origin) {
        this.origin = origin == null ? ReleaseNoteRevisionView.Origin.MANUAL : origin;
    }

    public String getPreviewHash() {
        return previewHash;
    }

    public void setPreviewHash(String previewHash) {
        this.previewHash = previewHash;
    }

    /** A correction is written by hand or rendered again; translating again is not offered here. */
    @AssertTrue(message = "{validation.noteCorrection.originUnsupported}")
    public boolean isOriginOffered() {
        return origin == ReleaseNoteRevisionView.Origin.MANUAL || origin == ReleaseNoteRevisionView.Origin.RENDERED;
    }

    /** A correction by hand has its text. */
    @AssertTrue(message = "{validation.releaseNote.required}")
    public boolean isContentWritten() {
        return origin != ReleaseNoteRevisionView.Origin.MANUAL || (content != null && !content.isBlank());
    }

    /** A correction rendered again names the preview a person read. */
    @AssertTrue(message = "{validation.noteCorrection.previewRequired}")
    public boolean isPreviewNamed() {
        return origin != ReleaseNoteRevisionView.Origin.RENDERED || (previewHash != null && !previewHash.isBlank());
    }

    public String getContent() {
        return content;
    }

    // Browsers submit textarea line breaks as CRLF; notes are stored with LF, as an edit is.
    public void setContent(String content) {
        this.content = content == null ? null : content.replace("\r\n", "\n").strip() + "\n";
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason == null ? null : reason.replace("\r\n", "\n").strip();
    }
}
