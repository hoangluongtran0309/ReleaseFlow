package com.hoangluongtran0309.releaseflow.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A webhook secret the provider made: Linear's signing secret, or a signing token GitLab generated. */
public class WebhookSecretRequest {

    @NotBlank(message = "{validation.webhookSecret.required}")
    @Size(max = 255, message = "{validation.webhookSecret.tooLong}")
    private String webhookSecret;

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret == null ? null : webhookSecret.strip();
    }

    @Override
    public String toString() {
        return "WebhookSecretRequest[webhookSecret=***]";
    }
}
