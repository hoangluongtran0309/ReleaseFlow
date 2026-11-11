package com.hoangluongtran0309.releaseflow.project;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class SourceTokenRequest {

    @NotBlank(message = "{validation.token.required}")
    @Size(max = 255, message = "{validation.token.tooLong}")
    @Pattern(regexp = "\\S*", message = "{validation.token.noSpaces}")
    private String token;

    // Only a Jira source reads it: a new token may belong to another account (ADR-0042).
    @Size(max = 255, message = "{validation.accountEmail.tooLong}")
    @Email(message = "{validation.accountEmail.invalid}")
    private String accountEmail;

    public String getAccountEmail() {
        return accountEmail;
    }

    public void setAccountEmail(String accountEmail) {
        this.accountEmail = accountEmail == null || accountEmail.isBlank() ? null : accountEmail.strip();
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token == null ? null : token.strip();
    }

    // Keeps the token out of logs and error pages that print the request object.
    @Override
    public String toString() {
        return "SourceTokenRequest[token=***, accountEmail=%s]".formatted(accountEmail);
    }
}
