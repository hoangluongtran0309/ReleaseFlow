package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceType;
import com.hoangluongtran0309.releaseflow.project.LinearRefusalLog.Reason;
import com.hoangluongtran0309.releaseflow.project.LinearRefusalLog.Refusal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Verifies a Linear delivery with the connected source's own secret: a {@code Linear-Delivery}
 * header, a hex HMAC-SHA256 of the raw body in {@code Linear-Signature}, a
 * {@code webhookTimestamp} within the clock skew, and the workspace this source belongs to
 * (ADR-0018, amended 2026-11-19). Unlike the other verifiers this one reads the body, because
 * Linear puts the timestamp and the workspace inside it rather than in headers; an unreadable
 * body is simply one more way to fail.
 *
 * <p>The signature is checked before anything the body says, so a stale stamp or another
 * workspace is reported only for a body this source's secret signed. Every failure is the same
 * refusal to the caller; the log names which proof failed, at most once per source and reason
 * a minute ({@link LinearRefusalLog}), and never the body, a header's value, the secret, or
 * the caller.
 */
@Component
public class LinearWebhookVerifier {

    // Any real epoch-millisecond value is larger than this; any epoch-second value is not.
    private static final long MILLISECOND_THRESHOLD = 10_000_000_000L;

    private final IntegrationSourceRepository sourceRepository;
    private final CredentialCipher credentialCipher;
    private final SignatureFailureRecorder failures;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration clockSkew;
    private final LinearRefusalLog refusals;

    LinearWebhookVerifier(
            IntegrationSourceRepository sourceRepository,
            CredentialCipher credentialCipher,
            SignatureFailureRecorder failures,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${releaseflow.linear.webhook-clock-skew}") Duration clockSkew
    ) {
        this.sourceRepository = sourceRepository;
        this.credentialCipher = credentialCipher;
        this.failures = failures;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.clockSkew = clockSkew;
        this.refusals = new LinearRefusalLog(clock);
    }

    public Optional<VerifiedWebhook> verify(String webhookId, String signature, String deliveryId, byte[] body) {
        Optional<IntegrationSource> source = WebhookSources.parseWebhookId(webhookId)
                .flatMap(sourceRepository::findByWebhookId)
                .filter(candidate -> candidate.getSourceType() == SourceType.LINEAR);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        Refusal refusal = deliveryId == null ? Refusal.of(Reason.NO_DELIVERY_HEADER)
                : signature == null ? Refusal.of(Reason.NO_SIGNATURE_HEADER)
                : refusal(source.get(), signature, body);
        if (refusal != null) {
            refusals.refused(source.get().getId(), refusal);
            failures.record(source.get());
            return Optional.empty();
        }
        return source.map(WebhookSources::verified);
    }

    /** Writes the counts of refusals the log left out, once their minute is over. */
    @Scheduled(fixedDelayString = "PT10S")
    void sweepRefusalLog() {
        refusals.sweep();
    }

    @Transactional
    public void recordDelivery(VerifiedWebhook webhook, Instant deliveredAt) {
        sourceRepository.recordDelivery(webhook.sourceId(), webhook.organizationId(), deliveredAt);
    }

    // Why the delivery is refused, or null when every proof holds.
    private Refusal refusal(IntegrationSource source, String signature, byte[] body) {
        final String secret;
        try {
            secret = credentialCipher.decrypt(
                    new CredentialCipher.EncryptedSecret(source.getSecretNonce(), source.getSecretCiphertext()),
                    IntegrationSourceService.secretAuthenticatedData(source)
            );
        } catch (IllegalStateException exception) {
            return Refusal.of(Reason.SECRET_UNREADABLE);
        }
        if (!signatureMatches(secret, signature, body)) {
            return Refusal.of(Reason.SIGNATURE_MISMATCH);
        }
        final JsonNode payload;
        try {
            payload = objectMapper.readTree(body);
        } catch (JacksonException exception) {
            return Refusal.of(Reason.NOT_JSON);
        }
        if (payload == null || !payload.isObject()) {
            return Refusal.of(Reason.NOT_AN_OBJECT);
        }
        // Linear's time of sending, not createdAt, which is when the issue changed: a delivery
        // Linear sends a minute after the change is still fresh (ADR-0018, amended 2026-11-19).
        Instant sentAt = instant(payload.path("webhookTimestamp"));
        if (sentAt == null) {
            return Refusal.of(Reason.NO_TIMESTAMP);
        }
        long offset = sentAt.getEpochSecond() - clock.instant().getEpochSecond();
        if (Math.abs(offset) > clockSkew.toSeconds()) {
            return new Refusal(Reason.STALE_TIMESTAMP,
                    "webhookTimestamp is %+d s from this server's clock, beyond the allowed %d s"
                            .formatted(offset, clockSkew.toSeconds()));
        }
        if (!workspaceMatches(source, payload.path("organizationId"))) {
            return Refusal.of(Reason.OTHER_WORKSPACE);
        }
        return null;
    }

    private boolean signatureMatches(String secret, String signature, byte[] body) {
        byte[] expected = WebhookSignatures.hmacSha256(secret.getBytes(StandardCharsets.UTF_8), body);
        return MessageDigest.isEqual(
                HexFormat.of().formatHex(expected).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    // The delivery is only for the workspace this source was connected to.
    private static boolean workspaceMatches(IntegrationSource source, JsonNode organizationId) {
        return organizationId.isString() && organizationId.stringValue().equals(source.getExternalWorkspaceKey());
    }

    // Linear documents epoch milliseconds; seconds and an ISO-8601 instant are read too, which
    // weakens nothing, since the stamp is inside the signed body.
    private static Instant instant(JsonNode stamp) {
        try {
            if (stamp.isNumber()) {
                long raw = stamp.longValue();
                return raw > MILLISECOND_THRESHOLD ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
            }
            return stamp.isString() ? Instant.parse(stamp.stringValue()) : null;
        } catch (DateTimeException exception) {
            // DateTimeParseException is one, and so is a number of seconds beyond Instant's range.
            return null;
        }
    }
}
