package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.change.ChangeReviewRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A decision on one change of a release in review. Both actions carry the category,
 * breaking flag, and security flag the reviewer saw, so a stale page never approves a
 * classification it did not show. A caller written before ADR-0037 may leave security
 * out, which confirms the current value.
 */
public class ReleaseDecisionRequest {

    @NotNull(message = "{validation.reviewAction.required}")
    private ReviewAction action;

    @NotBlank(message = "{validation.category.required}")
    private String category;

    @NotNull(message = "{validation.breaking.required}")
    private Boolean breaking;

    private Boolean security;

    @Size(max = 2000, message = "{validation.note.tooLong}")
    private String note;

    public ReviewAction getAction() {
        return action;
    }

    public void setAction(ReviewAction action) {
        this.action = action;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category == null ? null : category.strip();
    }

    public Boolean getBreaking() {
        return breaking;
    }

    public void setBreaking(Boolean breaking) {
        this.breaking = breaking;
    }

    public Boolean getSecurity() {
        return security;
    }

    public void setSecurity(Boolean security) {
        this.security = security;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note == null || note.isBlank() ? null : note.strip();
    }

    ChangeReviewRequest changeReview() {
        ChangeReviewRequest review = new ChangeReviewRequest();
        review.setCategory(category);
        review.setBreaking(breaking);
        review.setSecurity(security);
        return review;
    }
}
