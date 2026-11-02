package com.hoangluongtran0309.releaseflow.audience;

/** An audience's templates for one language. They are checked by {@link AudienceTemplate}. */
public class AudienceTemplateRequest {

    private String templateBody;

    private String footerTemplateBody;

    public String getTemplateBody() {
        return templateBody;
    }

    // Browsers submit textarea line breaks as CRLF; templates are stored with LF.
    public void setTemplateBody(String templateBody) {
        this.templateBody = templateBody == null ? null : templateBody.replace("\r\n", "\n");
    }

    public String getFooterTemplateBody() {
        return footerTemplateBody;
    }

    // Left out, it keeps the footer an audience has, or none for a new one (ADR-0037).
    public void setFooterTemplateBody(String footerTemplateBody) {
        this.footerTemplateBody = footerTemplateBody == null ? null : footerTemplateBody.replace("\r\n", "\n");
    }
}
