package com.hoangluongtran0309.releaseflow.change;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * The Claude Messages API through the official Java SDK, with Structured Outputs
 * ({@code output_config.format}) enforcing the shared response schema. The SDK's
 * automatic retries are switched off so each change costs exactly one request, but for a
 * model that refuses the temperature: it gets the same request once more without it, and
 * none after that (ADR-0039).
 */
final class AnthropicChangeClassifier implements AiChangeClassifier, AutoCloseable {

    static final long MAX_TOKENS = 16000L;
    static final String INVALID = "Anthropic returned an invalid classification.";

    // The parameter's name as a whole word, as in "`temperature` is deprecated for this model."
    private static final Pattern TEMPERATURE_NAMED = Pattern.compile("(?<![A-Za-z0-9_])temperature(?![A-Za-z0-9_])");

    private static final Logger log = LoggerFactory.getLogger(AnthropicChangeClassifier.class);

    private final String model;
    private final OptionalDouble temperature;
    // Set once the model refused the temperature; nothing clears it but a restart.
    private final AtomicBoolean temperatureRefused = new AtomicBoolean();
    private final Duration timeout;
    private final AnthropicClient client;
    private final ObjectMapper objectMapper;
    private final AiClassificationParser parser;

    AnthropicChangeClassifier(
            String apiKey,
            String model,
            OptionalDouble temperature,
            String baseUrl,
            Duration timeout,
            ObjectMapper objectMapper
    ) {
        this.model = model;
        this.temperature = temperature;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.parser = new AiClassificationParser(objectMapper);
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .timeout(timeout)
                .maxRetries(0)
                .build();
    }

    @Override
    public AiProvider provider() {
        return AiProvider.ANTHROPIC;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public AiClassification classify(AiClassificationRequest request) {
        AiChangeClassifier.requireNoTransaction();
        Message response = send(request, temperature.isPresent() && !temperatureRefused.get());

        // Check why generation stopped before reading any content.
        Optional<StopReason> stopReason = response.stopReason();
        if (stopReason.isPresent() && StopReason.REFUSAL.equals(stopReason.get())) {
            throw failure(request, "Anthropic declined to classify this change.");
        }
        if (stopReason.isEmpty() || !StopReason.END_TURN.equals(stopReason.get())) {
            throw failure(request, "Anthropic returned an incomplete response.");
        }
        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .findFirst()
                .orElseThrow(() -> failure(request, INVALID));
        try {
            return parser.parse(text, request);
        } catch (AiClassificationException exception) {
            throw failure(request, INVALID);
        }
    }

    private Message send(AiClassificationRequest request, boolean withTemperature) {
        try {
            return client.messages().create(params(request, withTemperature));
        } catch (AnthropicServiceException exception) {
            if (withTemperature && refusesTemperature(exception)) {
                temperatureRefused();
                return send(request, false);
            }
            throw failure(request, "Anthropic returned HTTP " + exception.statusCode() + ".");
        } catch (AnthropicIoException exception) {
            throw failure(request, causedByTimeout(exception)
                    ? "Anthropic did not respond within " + OpenAiCompatibleClassifier.describe(timeout) + "."
                    : "Could not reach Anthropic.");
        } catch (AnthropicException exception) {
            throw failure(request, "Could not reach Anthropic.");
        }
    }

    private MessageCreateParams params(AiClassificationRequest request, boolean withTemperature) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS);
        // Left out for a model that accepts only its default, or once it refused one.
        if (withTemperature) {
            builder.temperature(temperature.getAsDouble());
        }
        return builder
                .system(AiClassificationPrompt.system(request))
                .addUserMessage(AiClassificationPrompt.userMessage(request, objectMapper))
                .outputConfig(OutputConfig.builder()
                        .format(JsonOutputFormat.builder().schema(schema(request)).build())
                        .build())
                .build();
    }

    /**
     * A 400 {@code invalid_request_error} whose message names {@code temperature}: Anthropic
     * names no parameter field, so the message is what says which one it refused (ADR-0039).
     * The body is read for this alone.
     */
    private static boolean refusesTemperature(AnthropicServiceException exception) {
        if (exception.statusCode() != 400) {
            return false;
        }
        Map<String, JsonValue> error = object(object(exception.body()).get("error"));
        return "invalid_request_error".equals(string(error.get("type")))
                && TEMPERATURE_NAMED.matcher(string(error.get("message"))).find();
    }

    private static Map<String, JsonValue> object(JsonValue value) {
        if (value == null) {
            return Map.of();
        }
        Optional<Map<String, JsonValue>> object = value.asObject();
        return object.orElse(Map.of());
    }

    private static String string(JsonValue value) {
        if (value == null) {
            return "";
        }
        Optional<String> string = value.asString();
        return string.orElse("");
    }

    private void temperatureRefused() {
        if (temperatureRefused.compareAndSet(false, true)) {
            log.warn("Anthropic model {} refused the temperature, so classification requests leave it out until "
                    + "ReleaseFlow restarts. Set RELEASEFLOW_AI_TEMPERATURE=default to avoid the first refusal.", model);
        }
    }

    // The schema names the Organization's audiences, so it is built for each request.
    private JsonOutputFormat.Schema schema(AiClassificationRequest request) {
        Map<String, Object> fields = objectMapper.convertValue(
                AiClassificationPrompt.responseSchema(request, objectMapper),
                new TypeReference<Map<String, Object>>() {
                }
        );
        JsonOutputFormat.Schema.Builder schema = JsonOutputFormat.Schema.builder();
        fields.forEach((name, value) -> schema.putAdditionalProperty(name, JsonValue.from(value)));
        return schema.build();
    }

    @Override
    public void close() {
        client.close();
    }

    private static AiClassificationException failure(AiClassificationRequest request, String message) {
        log.warn("Anthropic classification of change {} failed: {}", request.changeId(), message);
        return new AiClassificationException(message);
    }

    private static boolean causedByTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedIOException) {
                return true;
            }
        }
        return false;
    }
}
