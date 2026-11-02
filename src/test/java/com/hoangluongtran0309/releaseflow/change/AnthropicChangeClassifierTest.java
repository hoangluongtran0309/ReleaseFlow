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
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.List;
import java.util.OptionalDouble;

import static com.hoangluongtran0309.releaseflow.change.OpenAiCompatibleClassifierTest.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class AnthropicChangeClassifierTest {

    private static final String API_KEY = "sk-ant-test-key-that-must-not-leak";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final OptionalDouble ZERO = OptionalDouble.of(AiClassificationPrompt.TEMPERATURE);
    private static final String WARNING = "Anthropic model claude-test refused the temperature, so classification "
            + "requests leave it out until ReleaseFlow restarts. Set RELEASEFLOW_AI_TEMPERATURE=default to avoid the "
            + "first refusal.";

    /**
     * Anthropic's refusal of a temperature (ADR-0039), in the error shape its errors page
     * documents. The page quotes no message for it; this one is as a refusal was reported.
     */
    static final String DEPRECATED = """
            {"type":"error","error":{"type":"invalid_request_error",\
            "message":"`temperature` is deprecated for this model."},"request_id":"req_011CSHoEeqs5C35K2UUqR7Fy"}""";

    private AnthropicStub stub;
    private AnthropicChangeClassifier classifier;

    @BeforeEach
    void startStub() {
        stub = AnthropicStub.start();
        classifier = new AnthropicChangeClassifier(API_KEY, "claude-test", ZERO, stub.baseUrl(), Duration.ofSeconds(5), OBJECT_MAPPER);
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
        assertThat(recorded.apiKey()).isEqualTo(API_KEY);
        assertThat(recorded.anthropicVersion()).isNotBlank();

        JsonNode body = OBJECT_MAPPER.readTree(recorded.body());
        assertThat(body.path("model").stringValue()).isEqualTo("claude-test");
        assertThat(body.path("max_tokens").longValue()).isEqualTo(AnthropicChangeClassifier.MAX_TOKENS);
        assertThat(body.path("temperature").isNumber()).isTrue();
        assertThat(body.path("temperature").doubleValue()).isZero();
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

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.7, 1.0})
    void sendsTheConfiguredTemperature(double temperature) {
        try (AnthropicChangeClassifier configured = new AnthropicChangeClassifier(
                API_KEY, "claude-test", OptionalDouble.of(temperature), stub.baseUrl(), Duration.ofSeconds(5),
                OBJECT_MAPPER)) {
            stub.respondWithClassification("fix", false, false, "Empty tables export a header.");

            configured.classify(request(null, null, "en"));
        }

        JsonNode body = OBJECT_MAPPER.readTree(stub.requests().getFirst().body());
        assertThat(body.path("temperature").isNumber()).isTrue();
        assertThat(body.path("temperature").doubleValue()).isEqualTo(temperature);
    }

    @Test
    void leavesTheTemperatureOutForTheProviderDefault() {
        try (AnthropicChangeClassifier configured = new AnthropicChangeClassifier(
                API_KEY, "claude-test", OptionalDouble.empty(), stub.baseUrl(), Duration.ofSeconds(5), OBJECT_MAPPER)) {
            stub.respondWithClassification("fix", false, false, "Empty tables export a header.");

            AiClassification answer = configured.classify(request(null, null, "en"));

            assertThat(answer.category()).isEqualTo(TestCategories.FIX);
        }

        JsonNode body = OBJECT_MAPPER.readTree(stub.requests().getFirst().body());
        assertThat(body.has("temperature")).isFalse();
        assertThat(body.path("max_tokens").longValue()).isEqualTo(AnthropicChangeClassifier.MAX_TOKENS);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.7})
    void aRefusedTemperatureIsSentOnceMoreWithoutItAndNeverAgain(double temperature, CapturedOutput output) {
        stub.refuseTemperature(DEPRECATED);
        stub.respondWithClassification("fix", false, false, "Empty tables export a header.");
        try (AnthropicChangeClassifier configured = classifier(OptionalDouble.of(temperature))) {
            AiClassification first = configured.classify(request(null, null, "en"));
            AiClassification second = configured.classify(request(null, null, "en"));

            assertThat(first.category()).isEqualTo(TestCategories.FIX);
            assertThat(second.category()).isEqualTo(TestCategories.FIX);
        }

        List<JsonNode> bodies = stub.requests().stream().map(recorded -> OBJECT_MAPPER.readTree(recorded.body())).toList();
        assertThat(bodies).hasSize(3);
        assertThat(bodies.get(0).path("temperature").doubleValue()).isEqualTo(temperature);
        assertThat(bodies.get(1).has("temperature")).isFalse();
        assertThat(bodies.get(2).has("temperature")).isFalse();
        // The second request is the first without its temperature, and nothing else.
        assertThat(((ObjectNode) bodies.get(0).deepCopy()).without("temperature")).isEqualTo(bodies.get(1));
        assertThat(output.getOut()).containsOnlyOnce(WARNING);
    }

    @Test
    void aRefusalOnTheSecondRequestIsTheChangesFailure() {
        stub.refuseTemperature(DEPRECATED);
        stub.respond(529, "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}");

        assertFailure("Anthropic returned HTTP 529.");
        assertThat(stub.requests()).hasSize(2);
    }

    // Only a 400 invalid_request_error whose message names temperature is a refusal of it.
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "400|{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"max_tokens: 64000 > 32000, which is the maximum allowed number of output tokens for claude-test\"}}",
            "400|{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"top_temperature: Extra inputs are not permitted\"}}",
            "400|{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"`temperature` is deprecated for this model.\"}}",
            "429|{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"`temperature` is deprecated for this model.\"}}",
            "400|<html>Bad Request</html>"
    })
    void anyOtherErrorIsNotSentAgain(int status, String errorBody, CapturedOutput output) {
        stub.respond(status, errorBody);

        assertFailure("Anthropic returned HTTP " + status + ".");
        assertThat(stub.requests()).hasSize(1);

        stub.respondWithClassification("fix", false, false, "Empty tables export a header.");
        classifier.classify(request(null, null, "en"));
        assertThat(OBJECT_MAPPER.readTree(stub.requests().get(1).body()).path("temperature").isNumber()).isTrue();
        assertThat(output.getOut()).doesNotContain(WARNING);
    }

    @Test
    void aRequestThatSucceedsKeepsItsTemperature(CapturedOutput output) {
        stub.respondWithClassification("fix", false, false, "Empty tables export a header.");

        classifier.classify(request(null, null, "en"));
        classifier.classify(request(null, null, "en"));

        assertThat(stub.requests()).hasSize(2).allSatisfy(recorded -> {
            JsonNode body = OBJECT_MAPPER.readTree(recorded.body());
            assertThat(body.path("temperature").isNumber()).isTrue();
            assertThat(body.path("temperature").doubleValue()).isZero();
        });
        assertThat(output.getOut()).doesNotContain(WARNING);
    }

    @Test
    void theProviderDefaultIsNeverRefused(CapturedOutput output) {
        stub.refuseTemperature(DEPRECATED);
        stub.respondWithClassification("fix", false, false, "Empty tables export a header.");
        try (AnthropicChangeClassifier configured = classifier(OptionalDouble.empty())) {
            configured.classify(request(null, null, "en"));
            configured.classify(request(null, null, "en"));
        }

        assertThat(stub.requests()).hasSize(2)
                .allSatisfy(recorded -> assertThat(OBJECT_MAPPER.readTree(recorded.body()).has("temperature")).isFalse());
        assertThat(output.getOut()).doesNotContain(WARNING);
    }

    private AnthropicChangeClassifier classifier(OptionalDouble temperature) {
        return new AnthropicChangeClassifier(API_KEY, "claude-test", temperature, stub.baseUrl(), Duration.ofSeconds(5),
                OBJECT_MAPPER);
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
                API_KEY, "claude-test", ZERO, closedBaseUrl, Duration.ofSeconds(2), OBJECT_MAPPER);
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
