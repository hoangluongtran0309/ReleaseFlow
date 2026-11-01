package com.hoangluongtran0309.releaseflow.project;

import jakarta.validation.constraints.Size;

/** What administrators say about a Project's product. Empty clears it. */
public class ProjectContextRequest {

    static final int LIMIT = 2000;

    @Size(max = LIMIT, message = "{validation.projectContext.tooLong}")
    private String context = "";

    public String getContext() {
        return context;
    }

    // Browsers submit textarea line breaks as CRLF.
    public void setContext(String context) {
        this.context = context == null ? "" : context.replace("\r\n", "\n").strip();
    }
}
