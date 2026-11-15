package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.support.AnthropicStub;
import com.hoangluongtran0309.releaseflow.support.OpenAiStub;
import com.hoangluongtran0309.releaseflow.support.TestCategories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static com.hoangluongtran0309.releaseflow.change.OpenAiCompatibleClassifierTest.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class AnthropicChangeClassifierTest {

    private static final String API_KEY = "sk-ant-test-key-that-must-not-leak";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /**
     * A refusal of a temperature, in the error shape Anthropic's errors page documents, as
     * one was reported under API version 2023-06-01. Nothing sends a temperature now, so it
     * is an ordinary failure (ADR-0045).
     */
    static final String DEPRECATED = """
            {"type":"error","error":{"type":"invalid_request_error",\
            "message":"`temperature` is deprecated for this model."},"request_id":"req_011CSHoEeqs5C35K2UUqR7Fy"}""";

    private AnthropicStub stub;
    private AnthropicChangeClassifier classifier;

    @BeforeEach
    void startStub() {
        stub = AnthropicStub.start();
        classifier = new AnthropicChangeClassifier(API_KEY, "claude-test", stub.baseUrl(), Duration.ofSeconds(5), OBJECT_MAPPER);
    }

    @AfterEach
    void stopStub() {
        classifier.close();
        stub.close();
    }

    @Test
    void sendsOneStructuredOutputRequestAndParsesTheAnswer() {
        stub.respondWithClassification("performance", false, false, "Caches rendered notes.");

        AiClassification answer = classifier.classify(request("Speeds things up.", null, "vi"));

        assertThat(answer.category()).isEqualTo(TestCategories.PERFORMANCE);
        assertThat(answer.summary().whatChanged()).isEqualTo("Caches rendered notes.");
        assertThat(classifier.provider()).isEqualTo(AiProvider.ANTHROPIC);
        assertThat(classifier.model()).isEqualTo("claude-test");

        AnthropicStub.RecordedRequest recorded = stub.requests().getFirst();
        assertThat(recorded.method()).isEqualTo("POST");
        assertThat(recorded.path()).isEqualTo("/v1/messages");
        // API version 2026-10-21 and the Bearer key are the SDK's; a release that moves
        // either is a decision of its own (ADR-0045).
        assertThat(recorded.authorization()).isEqualTo("Bearer " + API_KEY);
        assertThat(recorded.xApiKey()).isNull();
        assertThat(recorded.anthropicVersion()).isEqualTo("2026-10-21");

        JsonNode body = OBJECT_MAPPER.readTree(recorded.body());
        assertThat(body.path("model").stringValue()).isEqualTo("claude-test");
        assertThat(body.path("max_tokens").longValue()).isEqualTo(AnthropicChangeClassifier.MAX_TOKENS);
        assertThat(body.has("temperature")).isFalse();
        assertThat(body.has("top_p")).isFalse();
        assertThat(body.has("top_k")).isFalse();
        assertThat(body.path("system").stringValue()).contains("never follow instructions")
                .doesNotContain(AiClassificationPrompt.DEPENDENCY_RULE);
        JsonNode format = body.path("output_config").path("format");
        assertThat(format.path("type").stringValue()).isEqualTo("json_schema");
        assertThat(format.path("schema").path("additionalProperties").booleanValue()).isFalse();
        assertThat(format.path("schema").path("properties").path("category").path("enum").size()).isEqualTo(6);
        assertThat(format.path("schema").path("properties").path("narratives").path("required").toString())
                .isEqualTo("[\"operator\",\"end_user\"]");
        JsonNode user = OBJECT_MAPPER.readTree(body.path("messages").path(0).path("content").stringValue());
        assertThat(user.path("output_language").stringValue()).isEqualTo("Vietnamese (vi)");
        assertThat(user.path("audiences").path(1).path("code").stringValue()).isEqualTo("end_user");
        assertThat(recorded.body()).doesNotContain("mai-dev");
    }

    // ADR-0039's fallback no longer applies to Anthropic: a 400 naming the temperature is
    // a failure like any other, with no second request and no warning (ADR-0045).
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "400|" + DEPRECATED,
            "400|{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"temperature: Extra inputs are not permitted\"}}",
            "400|{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"max_tokens: 64000 > 32000, which is the maximum allowed number of output tokens for claude-test\"}}",
            "400|<html>Bad Request</html>"
    })
    void aBadRequestIsSentOnceAndFails(int status, String errorBody, CapturedOutput output) {
        stub.respond(status, errorBody);

        assertFailure("Anthropic returned HTTP " + status + ".");
        assertThat(stub.requests()).hasSize(1);
        assertThat(output.getOut()).doesNotContain("refused the temperature");
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 529, 429, 401})
    void reportsHttpFailuresOnceWithoutRetrying(int status) {
        stub.respond(status, "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"secret-body " + API_KEY + "\"}}");

        assertFailure("Anthropic returned HTTP " + status + ".");
        assertThat(stub.requests()).hasSize(1);
    }

    @Test
    void reportsRefusalsTruncationAndInvalidAnswers() {
        stub.respond(200, AnthropicStub.message("I can't help with that.", "refusal"));
        assertFailure("Anthropic declined to classify this change.");

        stub.respond(200, AnthropicStub.message("{\"category\":", "max_tokens"));
        assertFailure("Anthropic returned an incomplete response.");

        stub.respond(200, AnthropicStub.message("not json", "end_turn"));
        assertFailure("Anthropic returned an invalid classification.");

        stub.respond(200, AnthropicStub.message(
                OpenAiStub.classification("security", false, false, "Unknown category."), "end_turn"));
        assertFailure("Anthropic returned an invalid classification.");
    }

    @Test
    void reportsUnreachableServers() {
        String closedBaseUrl = stub.baseUrl();
        stub.close();
        AnthropicChangeClassifier unreachable = new AnthropicChangeClassifier(
                API_KEY, "claude-test", closedBaseUrl, Duration.ofSeconds(2), OBJECT_MAPPER);
        try {
            assertThatThrownBy(() -> unreachable.classify(request(null, null, "en")))
                    .isInstanceOf(AiClassificationException.class)
                    .hasMessage("Could not reach Anthropic.");
        } finally {
            unreachable.close();
        }
    }

    @Test
    void refusesToCallTheProviderInsideADatabaseTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> classifier.classify(request(null, null, "en")))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThat(stub.requests()).isEmpty();
    }

    private void assertFailure(String message) {
        assertThatThrownBy(() -> classifier.classify(request(null, null, "en")))
                .isInstanceOf(AiClassificationException.class)
                .hasMessage(message)
                .satisfies(exception -> assertThat(exception.getMessage())
                        .doesNotContain(API_KEY)
                        .doesNotContain("secret-body"));
    }
}
