package com.hoangluongtran0309.releaseflow.release;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A correction of a published note, written by hand (ADR-0046): the revision it corrects,
 * the corrected Markdown, and why. The reason is published wherever the correction is.
 */
public class NoteCorrectionRequest {

    static final int MAX_REASON_LENGTH = 500;

    @NotNull(message = "{validation.noteCorrection.correctsRequired}")
    @Min(value = 1, message = "{validation.noteCorrection.correctsRequired}")
    private Integer corrects;

    @NotBlank(message = "{validation.releaseNote.required}")
    @Size(max = ReleaseNoteRequest.MAX_LENGTH, message = "{validation.releaseNote.tooLong}")
    private String content;

    @NotBlank(message = "{validation.noteCorrection.reasonRequired}")
    @Size(max = MAX_REASON_LENGTH, message = "{validation.noteCorrection.reasonTooLong}")
    private String reason;

    public Integer getCorrects() {
        return corrects;
    }

    public void setCorrects(Integer corrects) {
        this.corrects = corrects;
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
