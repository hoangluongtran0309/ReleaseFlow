package com.hoangluongtran0309.releaseflow.project;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignaturesTest {

    // 32 bytes whose Base64url form has both '-' and '_', so the two alphabets differ.
    private static final byte[] KEY = new byte[32];

    static {
        for (int index = 0; index < KEY.length; index++) {
            KEY[index] = (byte) (0xFB + index * 7);
        }
    }

    @Test
    void aSecretIsRewrittenAsTheStandardWebhooksFormOfTheSameBytes() {
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(KEY);
        assertThat(secret).contains("-").contains("_");

        String signingToken = WebhookSignatures.standardWebhookSecret(secret);

        assertThat(signingToken).startsWith("whsec_");
        String encoded = signingToken.substring("whsec_".length());
        assertThat(encoded).doesNotContain("-").doesNotContain("_").endsWith("=");
        assertThat(Base64.getDecoder().decode(encoded)).isEqualTo(KEY);
    }

    @Test
    void whatGitLabSignsWithTheSigningTokenVerifiesAgainstTheSecret() {
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(KEY);
        byte[] body = "{\"object_kind\":\"push\"}".getBytes(StandardCharsets.UTF_8);
        byte[] gitLabKey = Base64.getDecoder().decode(
                WebhookSignatures.standardWebhookSecret(secret).substring("whsec_".length()));
        String signature = "v1," + Base64.getEncoder().encodeToString(WebhookSignatures.hmacSha256(
                gitLabKey, "msg_1.1760000000.".concat(new String(body, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8)));

        assertThat(WebhookSignatures.standardWebhook(body, "msg_1", "1760000000", signature, secret)).isTrue();
    }
}
