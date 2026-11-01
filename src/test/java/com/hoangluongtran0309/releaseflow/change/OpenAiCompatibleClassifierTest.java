package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.OutputLanguage;
import com.hoangluongtran0309.releaseflow.audience.AudienceBrief;
import com.hoangluongtran0309.releaseflow.category.CategoryRef;
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
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class OpenAiCompatibleClassifierTest {

    private static final String API_KEY = "sk-test-key-that-must-not-leak";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final OptionalDouble ZERO = OptionalDouble.of(AiClassificationPrompt.TEMPERATURE);
    private static final String WARNING = "refused the temperature, so classification requests leave it out until "
            + "ReleaseFlow restarts. Set RELEASEFLOW_AI_TEMPERATURE=default to avoid the first refusal.";

    /**
     * OpenAI's refusals of a temperature (ADR-0039): the error names the parameter in
     * {@code error.param}, which its error-codes guide documents. The first was reported for
     * o3-mini in openai/openai-python#2072; the second is the refusal of a value other than
     * the default.
     */
    static final String UNSUPPORTED_PARAMETER = """
            {"error":{"message":"Unsupported parameter: 'temperature' is not supported with this model.",\
            "type":"invalid_request_error","param":"temperature","code":"unsupported_parameter"}}""";
    static final String UNSUPPORTED_VALUE = """
            {"error":{"message":"Unsupported value: 'temperature' does not support 0 with this model. \
            Only the default (1) value is supported.","type":"invalid_request_error","param":"temperature",\
            "code":"unsupported_value"}}""";

    private OpenAiStub stub;
    private OpenAiCompatibleClassifier openAi;

    @BeforeEach
    void startStub() {
        stub = OpenAiStub.start();
        openAi = OpenAiCompatibleClassifier.openAi(API_KEY, "test-model", ZERO, stub.baseUrl(), Duration.ofSeconds(2), OBJECT_MAPPER);
    }

    @AfterEach
    void stopStub() {
        stub.close();
    }

    @Test
    void sendsAStrictStructuredRequestAndParsesTheAnswer() {
        stub.respondWithClassification("fix", true, true, "Empty tables now export a header row.");

        AiClassification answer = openAi.classify(request("x".repeat(5000), TestCategories.FIX, "vi"));

        assertThat(answer).isEqualTo(new AiClassification(
                TestCategories.FIX,
                true,
                false,
                true,
                new NeutralSummary("Empty tables now export a header row.", "Requested by users.",
                        "Handled in the exporter.", ""),
                Map.of(),
                null,
                90,
                List.of(),
                null
        ));
        OpenAiStub.RecordedRequest recorded = stub.requests().getFirst();
        assertThat(recorded.method()).isEqualTo("POST");
        assertThat(recorded.path()).isEqualTo("/v1/chat/completions");
        assertThat(recorded.authorization()).isEqualTo("Bearer " + API_KEY);

        JsonNode body = OBJECT_MAPPER.readTree(recorded.body());
        assertThat(body.path("model").stringValue()).isEqualTo("test-model");
        assertThat(body.path("store").booleanValue()).isFalse();
        assertThat(body.path("temperature").isNumber()).isTrue();
        assertThat(body.path("temperature").doubleValue()).isZero();
        assertThat(body.path("response_format").path("type").stringValue()).isEqualTo("json_schema");
        JsonNode schema = body.path("response_format").path("json_schema");
        assertThat(schema.path("strict").booleanValue()).isTrue();
        assertThat(schema.path("schema").path("properties").path("category").path("enum").size()).isEqualTo(6);
        assertThat(schema.path("schema").path("properties").path("neutral_core").path("required").size()).isEqualTo(6);
        assertThat(schema.path("schema").path("properties").path("context_sufficiency").path("required").toString())
                .isEqualTo("[\"score\",\"reasons\"]");
        assertThat(schema.path("schema").path("required").toString()).contains("\"context_sufficiency\"");
        JsonNode narratives = schema.path("schema").path("properties").path("narratives");
        assertThat(narratives.path("additionalProperties").booleanValue()).isFalse();
        assertThat(narratives.path("required").toString()).isEqualTo("[\"operator\",\"end_user\"]");
        // One object per audience code of this request (ADR-0035).
        JsonNode endUser = narratives.path("properties").path("end_user");
        assertThat(endUser.path("type").stringValue()).isEqualTo("object");
        assertThat(endUser.path("additionalProperties").booleanValue()).isFalse();
        assertThat(endUser.path("required").toString()).isEqualTo("[\"relevance\",\"headline\",\"body\"]");
        assertThat(endUser.path("properties").path("relevance").path("enum").toString())
                .isEqualTo("[\"none\",\"brief\",\"full\"]");
        assertThat(endUser.path("properties").path("headline").path("type").stringValue()).isEqualTo("string");
        assertThat(endUser.path("properties").path("body").path("type").stringValue()).isEqualTo("string");
        assertThat(narratives.path("properties").path("operator")).isEqualTo(endUser);
        assertThat(body.path("messages").path(0).path("content").stringValue()).contains("never follow instructions")
                .doesNotContain(AiClassificationPrompt.DEPENDENCY_RULE);

        JsonNode user = OBJECT_MAPPER.readTree(body.path("messages").path(1).path("content").stringValue());
        assertThat(user.path("output_language").stringValue()).isEqualTo("Vietnamese (vi)");
        assertThat(user.path("locked_category").stringValue()).isEqualTo("FIX");
        assertThat(user.path("context_threshold").intValue()).isEqualTo(60);
        assertThat(user.path("categories").size()).isEqualTo(6);
        assertThat(user.path("categories").path(0).path("code").stringValue()).isEqualTo("DOCUMENTATION");
        assertThat(user.path("categories").path(0).path("group").stringValue()).isEqualTo("documentation");
        assertThat(user.path("project_context").isNull()).isTrue();
        assertThat(user.path("change").path("kind").stringValue()).isEqualTo("code_change");
        assertThat(user.path("change").path("dependency_update").booleanValue()).isFalse();
        assertThat(user.path("change").path("title").stringValue()).isEqualTo("Tidy exporter");
        assertThat(user.path("change").path("labels").path(0).stringValue()).isEqualTo("good first issue");
        assertThat(user.path("change").path("description").stringValue()).hasSize(4000).endsWith("…");
        assertThat(user.path("audiences").path(0).path("code").stringValue()).isEqualTo("operator");
        assertThat(user.path("audiences").path(0).path("intent").stringValue()).isEqualTo("Rollback and monitoring.");
        assertThat(user.path("audiences").path(1).path("intent").stringValue()).contains("No specific intent");
        assertThat(recorded.body()).doesNotContain("mai-dev");
    }

    @Test
    void deepSeekAsksForAJsonObjectAndSpellsOutTheSchema() {
        OpenAiCompatibleClassifier deepSeek = OpenAiCompatibleClassifier.deepSeek(
                API_KEY, "deepseek-test", ZERO, stub.baseUrl(), Duration.ofSeconds(2), OBJECT_MAPPER);
        stub.respondWithClassification("maintenance", false, "Upgrades the build plugin.");

        AiClassification answer = deepSeek.classify(request(null, null, "en"));

        assertThat(answer.category()).isEqualTo(TestCategories.MAINTENANCE);
        assertThat(deepSeek.provider()).isEqualTo(AiProvider.DEEPSEEK);
        JsonNode body = OBJECT_MAPPER.readTree(stub.requests().getFirst().body());
        assertThat(body.path("response_format").path("type").stringValue()).isEqualTo("json_object");
        assertThat(body.has("store")).isFalse();
        assertThat(body.path("messages").path(0).path("content").stringValue())
                .contains("Respond with JSON only")
                .contains("\"neutral_core\"")
                .contains("\"required\":[\"operator\",\"end_user\"]");
        JsonNode user = OBJECT_MAPPER.readTree(body.path("messages").path(1).path("content").stringValue());
        assertThat(user.path("locked_category").isNull()).isTrue();
    }

    // 0 is sent exactly as before RELEASEFLOW_AI_TEMPERATURE existed, so recorded requests still match.
    @ParameterizedTest
    @CsvSource({"openai, 0.0", "openai, 0.7", "deepseek, 0.0", "deepseek, 1.5"})
    void sendsTheConfiguredTemperature(String provider, double temperature) {
        OpenAiCompatibleClassifier classifier = classifier(provider, OptionalDouble.of(temperature));
        stub.respondWithClassification("fix", false, "Empty tables export a header.");

        classifier.classify(request(null, null, "en"));

        String recorded = stub.requests().getFirst().body();
        assertThat(recorded).contains("\"temperature\":" + temperature + ",");
        JsonNode body = OBJECT_MAPPER.readTree(recorded);
        assertThat(body.path("temperature").isNumber()).isTrue();
        assertThat(body.path("temperature").doubleValue()).isEqualTo(temperature);
    }

    // A model that accepts only its default, such as an OpenAI reasoning model, gets no temperature.
    @ParameterizedTest
    @ValueSource(strings = {"openai", "deepseek"})
    void leavesTheTemperatureOutForTheProviderDefault(String provider) {
        OpenAiCompatibleClassifier classifier = classifier(provider, OptionalDouble.empty());
        stub.respondWithClassification("fix", false, "Empty tables export a header.");

        AiClassification answer = classifier.classify(request(null, null, "en"));

        assertThat(answer.category()).isEqualTo(TestCategories.FIX);
        JsonNode body = OBJECT_MAPPER.readTree(stub.requests().getFirst().body());
        assertThat(body.has("temperature")).isFalse();
        assertThat(body.path("model").isString()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"openai, 0.0, PARAMETER", "openai, 0.7, VALUE", "deepseek, 0.0, VALUE", "deepseek, 1.5, PARAMETER"})
    void aRefusedTemperatureIsSentOnceMoreWithoutItAndNeverAgain(
            String provider, double temperature, String refusal, CapturedOutput output) {
        OpenAiCompatibleClassifier classifier = classifier(provider, OptionalDouble.of(temperature));
        stub.refuseTemperature("PARAMETER".equals(refusal) ? UNSUPPORTED_PARAMETER : UNSUPPORTED_VALUE);
        stub.respondWithClassification("fix", false, "Empty tables export a header.");

        AiClassification first = classifier.classify(request(null, null, "en"));
        AiClassification second = classifier.classify(request(null, null, "en"));

        assertThat(first.category()).isEqualTo(TestCategories.FIX);
        assertThat(second.category()).isEqualTo(TestCategories.FIX);
        List<JsonNode> bodies = stub.requests().stream().map(recorded -> OBJECT_MAPPER.readTree(recorded.body())).toList();
        assertThat(bodies).hasSize(3);
        assertThat(bodies.get(0).path("temperature").doubleValue()).isEqualTo(temperature);
        assertThat(bodies.get(1).has("temperature")).isFalse();
        assertThat(bodies.get(2).has("temperature")).isFalse();
        // The second request is the first without its temperature, and nothing else.
        assertThat(((ObjectNode) bodies.get(0).deepCopy()).without("temperature"))
                .isEqualTo(bodies.get(1));
        String label = AiProvider.fromValue(provider).orElseThrow().getLabel();
        String model = "deepseek".equals(provider) ? "deepseek-test" : "test-model";
        assertThat(output.getOut()).containsOnlyOnce(label + " model " + model + " " + WARNING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"openai", "deepseek"})
    void aRefusalOnTheSecondRequestIsTheChangesFailure(String provider) {
        OpenAiCompatibleClassifier classifier = classifier(provider, ZERO);
        stub.refuseTemperature(UNSUPPORTED_PARAMETER);
        stub.respond(500, "{\"error\":{\"message\":\"overloaded\"}}");

        assertThatThrownBy(() -> classifier.classify(request(null, null, "en")))
                .isInstanceOf(AiClassificationException.class)
                .hasMessage(AiProvider.fromValue(provider).orElseThrow().getLabel() + " returned HTTP 500.");
        assertThat(stub.requests()).hasSize(2);
    }

    // Only a 400 that names temperature as its parameter is a refusal of it.
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "openai|400|{\"error\":{\"message\":\"Invalid schema for response_format 'change_classification'.\",\"type\":\"invalid_request_error\",\"param\":\"response_format\",\"code\":null}}",
            "openai|400|{\"error\":{\"message\":\"Unsupported parameter: 'temperature' is not supported with this model.\",\"type\":\"invalid_request_error\",\"param\":null,\"code\":null}}",
            "deepseek|400|{\"error\":{\"message\":\"Invalid request\",\"type\":\"invalid_request_error\",\"param\":null,\"code\":\"invalid_request_error\"}}",
            "openai|422|{\"error\":{\"message\":\"temperature\",\"type\":\"invalid_request_error\",\"param\":\"temperature\"}}",
            "deepseek|400|<html>Bad Request</html>"
    })
    void anyOtherErrorIsNotSentAgain(String provider, int status, String errorBody, CapturedOutput output) {
        OpenAiCompatibleClassifier classifier = classifier(provider, ZERO);
        stub.respond(status, errorBody);

        assertThatThrownBy(() -> classifier.classify(request(null, null, "en")))
                .isInstanceOf(AiClassificationException.class)
                .hasMessage(AiProvider.fromValue(provider).orElseThrow().getLabel() + " returned HTTP " + status + ".");
        assertThat(stub.requests()).hasSize(1);

        stub.respondWithClassification("fix", false, "Empty tables export a header.");
        classifier.classify(request(null, null, "en"));
        assertThat(OBJECT_MAPPER.readTree(stub.requests().get(1).body()).path("temperature").isNumber()).isTrue();
        assertThat(output.getOut()).doesNotContain(WARNING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"openai", "deepseek"})
    void aRequestThatSucceedsKeepsItsTemperature(String provider, CapturedOutput output) {
        OpenAiCompatibleClassifier classifier = classifier(provider, ZERO);
        stub.respondWithClassification("fix", false, "Empty tables export a header.");

        classifier.classify(request(null, null, "en"));
        classifier.classify(request(null, null, "en"));

        assertThat(stub.requests()).hasSize(2)
                .allSatisfy(recorded -> assertThat(recorded.body()).contains("\"temperature\":0.0,"));
        assertThat(output.getOut()).doesNotContain(WARNING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"openai", "deepseek"})
    void theProviderDefaultIsNeverRefused(String provider, CapturedOutput output) {
        OpenAiCompatibleClassifier classifier = classifier(provider, OptionalDouble.empty());
        stub.refuseTemperature(UNSUPPORTED_VALUE);
        stub.respondWithClassification("fix", false, "Empty tables export a header.");

        classifier.classify(request(null, null, "en"));
        classifier.classify(request(null, null, "en"));

        assertThat(stub.requests()).hasSize(2)
                .allSatisfy(recorded -> assertThat(OBJECT_MAPPER.readTree(recorded.body()).has("temperature")).isFalse());
        assertThat(output.getOut()).doesNotContain(WARNING);
    }

    private OpenAiCompatibleClassifier classifier(String provider, OptionalDouble temperature) {
        return "deepseek".equals(provider)
                ? OpenAiCompatibleClassifier.deepSeek(API_KEY, "deepseek-test", temperature, stub.baseUrl(),
                Duration.ofSeconds(2), OBJECT_MAPPER)
                : OpenAiCompatibleClassifier.openAi(API_KEY, "test-model", temperature, stub.baseUrl(),
                Duration.ofSeconds(2), OBJECT_MAPPER);
    }

    @Test
    void returnsTheNarrativesOfTheRequestedAudiences() {
        stub.respondWithNarratives("fix", "Empty tables export a header.",
                Map.of("operator", "No action needed.", "end_user", "Empty exports now open in spreadsheets."));

        AiClassification answer = openAi.classify(request(null, null, "en"));

        assertThat(answer.narratives()).containsOnly(
                Map.entry("operator", AudienceNarrative.of("No action needed.")),
                Map.entry("end_user", AudienceNarrative.of("Empty exports now open in spreadsheets."))
        );
    }

    @ParameterizedTest
    @CsvSource({"500, OpenAI returned HTTP 500.", "429, OpenAI returned HTTP 429.", "401, OpenAI returned HTTP 401."})
    void reportsHttpFailuresWithoutTheResponseBody(int status, String message) {
        stub.respond(status, "{\"error\":{\"message\":\"echo " + API_KEY + " secret-body\"}}");

        assertFailure(message);
        assertThat(stub.requests()).hasSize(1);
    }

    @Test
    void reportsRefusalsIncompleteAnswersAndInvalidClassifications() {
        stub.respond(200, """
                {"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":null,"refusal":"I cannot help."}}]}
                """);
        assertFailure("OpenAI declined to classify this change.");

        stub.respond(200, """
                {"choices":[{"finish_reason":"length","message":{"role":"assistant","content":"{\\"category\\":"}}]}
                """);
        assertFailure("OpenAI returned an incomplete response.");

        stub.respondWithClassification("security", false, "Not a known category.");
        assertFailure("OpenAI returned an invalid classification.");

        stub.respondWithClassification("fix", false, "   ");
        assertFailure("OpenAI returned an invalid classification.");

        stub.respond(200, OpenAiStub.completionWithContent(
                "{\"category\":\"fix\",\"breaking_change\":false,\"security_change\":false,\"needs_human_review\":false}"));
        assertFailure("OpenAI returned an invalid classification.");

        stub.respond(200, OpenAiStub.completionWithContent("not json"));
        assertFailure("OpenAI returned an invalid classification.");

        stub.respond(200, "<html>gateway</html>");
        assertFailure("OpenAI returned an invalid classification.");
    }

    @Test
    void reportsTimeoutsAndUnreachableServers() {
        OpenAiCompatibleClassifier impatient = OpenAiCompatibleClassifier.openAi(
                API_KEY, "test-model", ZERO, stub.baseUrl(), Duration.ofMillis(200), OBJECT_MAPPER);
        stub.respond(200, OpenAiStub.completion("fix", false, false, "Late."), Duration.ofSeconds(2));

        assertThatThrownBy(() -> impatient.classify(request(null, null, "en")))
                .isInstanceOf(AiClassificationException.class)
                .hasMessage("OpenAI did not respond within 200 ms.");

        String closedBaseUrl = stub.baseUrl();
        stub.close();
        OpenAiCompatibleClassifier unreachable = OpenAiCompatibleClassifier.openAi(
                API_KEY, "test-model", ZERO, closedBaseUrl, Duration.ofSeconds(2), OBJECT_MAPPER);
        assertThatThrownBy(() -> unreachable.classify(request(null, null, "en")))
                .isInstanceOf(AiClassificationException.class)
                .hasMessage("Could not reach OpenAI.");
    }

    @Test
    void refusesToCallTheProviderInsideADatabaseTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> openAi.classify(request(null, null, "en")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("inside a database transaction");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThat(stub.requests()).isEmpty();
    }

    private void assertFailure(String message) {
        assertThatThrownBy(() -> openAi.classify(request(null, null, "en")))
                .isInstanceOf(AiClassificationException.class)
                .hasMessage(message)
                .satisfies(exception -> assertThat(exception.getMessage())
                        .doesNotContain(API_KEY)
                        .doesNotContain("secret-body"));
    }

    static AiClassificationRequest request(String description, CategoryRef lockedCategory, String language) {
        return new AiClassificationRequest(
                UUID.randomUUID(),
                "Tidy exporter",
                description,
                List.of("good first issue"),
                "main",
                List.of(),
                OutputLanguage.parse(language),
                lockedCategory,
                TestCategories.CATALOG,
                List.of(new AudienceBrief("operator", "Rollback and monitoring."), new AudienceBrief("end_user", "")),
                60,
                false,
                "",
                false
        );
    }
}
