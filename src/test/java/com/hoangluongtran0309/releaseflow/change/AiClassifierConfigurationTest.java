package com.hoangluongtran0309.releaseflow.change;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiClassifierConfigurationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void noProviderMeansNoAi() throws Exception {
        try (AiClassifiers classifiers = create("", Map.of())) {
            assertThat(classifiers.isEnabled()).isFalse();
            assertThat(classifiers.active()).isEmpty();
        }
    }

    @Test
    void buildsTheSelectedProvider() throws Exception {
        try (AiClassifiers openAi = create("openai", Map.of("openai.api-key", "k", "openai.model", "gpt-test"));
             AiClassifiers deepSeek = create("deepseek", Map.of("deepseek.api-key", "k", "deepseek.model", "ds-test"));
             AiClassifiers anthropic = create(" anthropic ", Map.of("anthropic.api-key", "k", "anthropic.model", "claude-test"))) {
            assertThat(openAi.active().orElseThrow().provider()).isEqualTo(AiProvider.OPENAI);
            assertThat(deepSeek.active().orElseThrow().provider()).isEqualTo(AiProvider.DEEPSEEK);
            assertThat(anthropic.active().orElseThrow().provider()).isEqualTo(AiProvider.ANTHROPIC);
            assertThat(anthropic.active().orElseThrow().model()).isEqualTo("claude-test");
        }
    }

    @Test
    void theSelectedProviderNeedsAKeyAndAModel() {
        assertThatThrownBy(() -> create("anthropic", Map.of("anthropic.api-key", "k")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RELEASEFLOW_ANTHROPIC_MODEL");
        assertThatThrownBy(() -> create("openai", Map.of("openai.model", "gpt-test")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RELEASEFLOW_OPENAI_API_KEY");
    }

    @Test
    void rejectsUnknownProvidersAndCredentialsWithoutAProvider() {
        assertThatThrownBy(() -> create("gemini", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openai, anthropic, deepseek");
        assertThatThrownBy(() -> create("", Map.of("openai.api-key", "k", "openai.model", "gpt-test")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RELEASEFLOW_AI_PROVIDER=openai");
    }

    // Empty is what Compose forwards for an unset variable, so it means the default of 0 too.
    @ParameterizedTest
    @ValueSource(strings = {"0", "", "  ", "0.0", "-0"})
    void asksForTemperatureZeroByDefault(String value) {
        for (AiProvider provider : AiProvider.values()) {
            assertThat(AiClassifierConfiguration.temperature(value, provider)).isEqualTo(OptionalDouble.of(0.0));
        }
        assertThat(AiClassifierConfiguration.temperature(null, AiProvider.OPENAI)).isEqualTo(OptionalDouble.of(0.0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", " default ", "DEFAULT"})
    void defaultSendsNoTemperature(String value) {
        for (AiProvider provider : AiProvider.values()) {
            assertThat(AiClassifierConfiguration.temperature(value, provider)).isEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource({"openai, 0.7, 0.7", "openai, 2, 2.0", "deepseek, 1.3, 1.3", "deepseek, 2.0, 2.0",
            "anthropic, 0.5, 0.5", "anthropic, 1, 1.0"})
    void acceptsANumberInTheProvidersRange(String provider, String value, double expected) {
        assertThat(AiClassifierConfiguration.temperature(value, AiProvider.fromValue(provider).orElseThrow()))
                .isEqualTo(OptionalDouble.of(expected));
    }

    @ParameterizedTest
    @CsvSource({"openai, 2.01, 2", "deepseek, 3, 2", "anthropic, 1.2, 1", "openai, -0.1, 2", "anthropic, -1, 1",
            "openai, NaN, 2", "openai, Infinity, 2", "openai, 0.7f, 2", "openai, low, 2", "anthropic, 0x1, 1"})
    void refusesATemperatureOutsideTheProvidersRangeAtStartup(String provider, String value, String max) {
        assertThatThrownBy(() -> create(provider, value,
                Map.of(provider + ".api-key", "k", provider + ".model", "m")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RELEASEFLOW_AI_TEMPERATURE must be a number from 0 to " + max + " for " + provider
                        + ", or default to send none.");
    }

    @Test
    void ignoresTheTemperatureWithoutAProvider() throws Exception {
        try (AiClassifiers classifiers = create("", "default", Map.of())) {
            assertThat(classifiers.isEnabled()).isFalse();
        }
    }

    private static AiClassifiers create(String provider, Map<String, String> settings) {
        return create(provider, "0", settings);
    }

    private static AiClassifiers create(String provider, String temperature, Map<String, String> settings) {
        return AiClassifierConfiguration.create(
                provider,
                TIMEOUT,
                temperature,
                name -> settings.getOrDefault(name, name.endsWith("base-url") ? "http://localhost:9" : ""),
                OBJECT_MAPPER,
                new ClassificationMetrics(new SimpleMeterRegistry())
        );
    }
}
