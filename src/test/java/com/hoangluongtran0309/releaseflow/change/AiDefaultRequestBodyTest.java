package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.support.AnthropicStub;
import com.hoangluongtran0309.releaseflow.support.OpenAiStub;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0038: a deployment that leaves {@code RELEASEFLOW_AI_TEMPERATURE} unset, or sets it to
 * nothing, sends every provider exactly the bytes it sent before the setting existed, so
 * recorded requests still match. The setting is read as the application reads it, from
 * {@code application.properties} over the environment.
 */
class AiDefaultRequestBodyTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String UNSET = "unset";

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "openai|unset", "openai|''", "openai|'  '",
            "deepseek|unset", "deepseek|''", "deepseek|'  '",
            "anthropic|unset", "anthropic|''", "anthropic|'  '"
    })
    void sendsTheBytesOfTheRequestBeforeTheSetting(String provider, String environmentValue) throws Exception {
        String temperature = resolvedTemperature(environmentValue);
        assertThat(temperature).isEqualTo(UNSET.equals(environmentValue) ? "0" : environmentValue);

        assertThat(requestBody(provider, temperature)).isEqualTo(fixture(provider));
    }

    // What @Value("${releaseflow.ai.temperature}") receives for this environment.
    private static String resolvedTemperature(String environmentValue) throws IOException {
        Map<String, Object> environment = new HashMap<>();
        if (!UNSET.equals(environmentValue)) {
            environment.put("RELEASEFLOW_AI_TEMPERATURE", environmentValue);
        }
        MutablePropertySources sources = new MutablePropertySources();
        sources.addLast(new MapPropertySource("environment", environment));
        sources.addLast(new ResourcePropertySource(new ClassPathResource("application.properties")));
        return new PropertySourcesPropertyResolver(sources).getRequiredProperty("releaseflow.ai.temperature");
    }

    private static String requestBody(String provider, String temperature) throws Exception {
        if ("anthropic".equals(provider)) {
            try (AnthropicStub stub = AnthropicStub.start()) {
                stub.respondWithClassification("fix", false, false, "Empty tables export a header.");
                classify(provider, temperature, stub.baseUrl());
                return stub.requests().getFirst().body();
            }
        }
        try (OpenAiStub stub = OpenAiStub.start()) {
            stub.respondWithClassification("fix", false, "Empty tables export a header.");
            classify(provider, temperature, stub.baseUrl());
            return stub.requests().getFirst().body();
        }
    }

    private static void classify(String provider, String temperature, String baseUrl) throws Exception {
        Map<String, String> settings = Map.of(
                provider + ".api-key", "test-key",
                provider + ".model", provider + "-test-model",
                provider + ".base-url", baseUrl
        );
        try (AiClassifiers classifiers = AiClassifierConfiguration.create(
                provider,
                Duration.ofSeconds(5),
                temperature,
                name -> settings.getOrDefault(name, ""),
                OBJECT_MAPPER,
                new ClassificationMetrics(new SimpleMeterRegistry())
        )) {
            classifiers.active().orElseThrow().classify(OpenAiCompatibleClassifierTest.request(null, null, "en"));
        }
    }

    private static String fixture(String provider) throws IOException {
        return new ClassPathResource("ai/default-request-" + provider + ".json").getContentAsString(StandardCharsets.UTF_8);
    }
}
