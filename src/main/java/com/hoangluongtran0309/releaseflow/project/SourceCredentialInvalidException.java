package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/**
 * A credential a reconnect or a pasted secret needs is missing or not in the form the
 * provider uses. Which credentials a source needs depends on its type, which the stored
 * source knows and the request does not, so this is checked by the service.
 */
public class SourceCredentialInvalidException extends LocalizedException {

    private final String field;
    private final String code;

    private SourceCredentialInvalidException(String code, String field) {
        super("error." + code + "." + field);
        this.code = code;
        this.field = field;
    }

    static SourceCredentialInvalidException required(String field) {
        return new SourceCredentialInvalidException("source_credential_required", field);
    }

    /** A pasted GitLab signing token is {@code whsec_} followed by Base64, as GitLab writes it. */
    static SourceCredentialInvalidException signingToken(String field) {
        return new SourceCredentialInvalidException("source_signing_token_invalid", field);
    }

    public String code() {
        return code;
    }

    /** The request property the failure belongs under. */
    public String field() {
        return field;
    }
}
