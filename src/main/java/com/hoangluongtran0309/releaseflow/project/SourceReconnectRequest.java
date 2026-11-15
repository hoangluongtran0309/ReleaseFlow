package com.hoangluongtran0309.releaseflow.project;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The credentials a disconnected source is reconnected with. Which are required depends on
 * its type: GitHub and GitLab take an optional token, a signed GitLab source optionally a
 * signing token GitLab generated, Linear its signing secret and an API key, and Jira an
 * account email and API token. Nothing that identifies the source is taken, because a
 * reconnect never changes what the source is.
 */
public class SourceReconnectRequest {

    @Size(max = 255, message = "{validation.token.tooLong}")
    @Pattern(regexp = "\\S*", message = "{validation.token.noSpaces}")
    private String token;

    @Size(max = 255, message = "{validation.webhookSecret.tooLong}")
    private String webhookSecret;

    @Size(max = 255, message = "{validation.accountEmail.tooLong}")
    @Email(message = "{validation.accountEmail.invalid}")
    private String accountEmail;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = blankToNull(token);
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = blankToNull(webhookSecret);
    }

    public String getAccountEmail() {
        return accountEmail;
    }

    public void setAccountEmail(String accountEmail) {
        this.accountEmail = blankToNull(accountEmail);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    // Keeps credentials out of logs and error pages that print the request object.
    @Override
    public String toString() {
        return "SourceReconnectRequest[token=***, webhookSecret=***, accountEmail=%s]".formatted(accountEmail);
    }
}
