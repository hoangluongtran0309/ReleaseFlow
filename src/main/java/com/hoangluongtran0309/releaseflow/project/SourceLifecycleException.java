package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;
import org.springframework.http.HttpStatus;

/**
 * A lifecycle step the source's state does not allow (ADR-0042): a disconnected source
 * cannot be used, a connected one cannot be reconnected, and a secret is set the way its
 * provider makes it or not at all.
 */
public class SourceLifecycleException extends LocalizedException {

    private final String code;

    private SourceLifecycleException(String code, Object... arguments) {
        super("error." + code, arguments);
        this.code = code;
    }

    /** The source is disconnected, so nothing may be set on it, read through it, or imported. */
    public static SourceLifecycleException disconnected() {
        return new SourceLifecycleException("source_disconnected");
    }

    /** Only a disconnected source can be reconnected. */
    static SourceLifecycleException notDisconnected() {
        return new SourceLifecycleException("source_not_disconnected");
    }

    /** The Project already holds this key, disconnected, and reconnecting it keeps its changes. */
    static SourceLifecycleException reconnectRequired(String externalProjectKey) {
        return new SourceLifecycleException("source_reconnect_required", externalProjectKey);
    }

    /** A polled source has no webhook, so no secret. */
    static SourceLifecycleException noWebhook() {
        return new SourceLifecycleException("source_has_no_webhook");
    }

    /** ReleaseFlow generates this source's secret; it cannot be pasted in. */
    static SourceLifecycleException secretGenerated() {
        return new SourceLifecycleException("source_secret_generated");
    }

    /** The provider makes this source's secret; ReleaseFlow cannot generate one. */
    static SourceLifecycleException secretPasted() {
        return new SourceLifecycleException("source_secret_pasted");
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
