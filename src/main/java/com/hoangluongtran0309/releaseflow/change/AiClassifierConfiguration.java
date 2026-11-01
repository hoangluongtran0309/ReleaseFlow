package com.hoangluongtran0309.releaseflow.change;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the one AI provider named by {@code RELEASEFLOW_AI_PROVIDER}. A selected
 * provider needs its API key and model; there is no default model. Credentials for
 * a provider that is not selected stop startup, so AI is never switched off silently.
 * {@code RELEASEFLOW_AI_TEMPERATURE} is checked against the selected provider's range
 * (ADR-0038); without a provider it is not read, since nothing is sent.
 */
@Configuration(proxyBeanMethods = false)
class AiClassifierConfiguration {

    /** The value of {@code RELEASEFLOW_AI_TEMPERATURE} that leaves the temperature out of the request. */
    static final String PROVIDER_DEFAULT_TEMPERATURE = "default";

    @Bean
    AiClassifiers aiClassifiers(
            @Value("${releaseflow.ai.provider}") String provider,
            @Value("${releaseflow.ai.timeout}") Duration timeout,
            @Value("${releaseflow.ai.temperature}") String temperature,
            Environment environment,
            ObjectMapper objectMapper,
            ClassificationMetrics metrics
    ) {
        return create(provider, timeout, temperature, name -> environment.getProperty("releaseflow." + name, ""),
                objectMapper, metrics);
    }

    static AiClassifiers create(
            String providerValue,
            Duration timeout,
            String temperatureValue,
            Function<String, String> settings,
            ObjectMapper objectMapper,
            ClassificationMetrics metrics
    ) {
        String value = providerValue == null ? "" : providerValue.strip();
        if (value.isEmpty()) {
            for (AiProvider provider : AiProvider.values()) {
                if (!setting(settings, provider, "api-key").isEmpty() || !setting(settings, provider, "model").isEmpty()) {
                    throw new IllegalStateException(
                            "RELEASEFLOW_" + provider.name() + "_API_KEY or _MODEL is set, but RELEASEFLOW_AI_PROVIDER "
                                    + "is empty. Set RELEASEFLOW_AI_PROVIDER=" + provider.getValue()
                                    + " to enable AI classification, or remove the provider settings."
                    );
                }
            }
            return new AiClassifiers(null);
        }

        AiProvider provider = AiProvider.fromValue(value).orElseThrow(() -> new IllegalStateException(
                "RELEASEFLOW_AI_PROVIDER must be one of "
                        + Arrays.stream(AiProvider.values()).map(AiProvider::getValue).collect(Collectors.joining(", "))
                        + "."
        ));
        String apiKey = setting(settings, provider, "api-key");
        String model = setting(settings, provider, "model");
        if (apiKey.isEmpty() || model.isEmpty()) {
            throw new IllegalStateException(
                    "RELEASEFLOW_AI_PROVIDER=" + provider.getValue() + " needs RELEASEFLOW_" + provider.name()
                            + "_API_KEY and RELEASEFLOW_" + provider.name() + "_MODEL."
            );
        }
        OptionalDouble temperature = temperature(temperatureValue, provider);
        String baseUrl = setting(settings, provider, "base-url");
        // Metered here rather than in each provider, so all three are counted alike.
        return new AiClassifiers(new MeteredChangeClassifier(switch (provider) {
            case OPENAI -> OpenAiCompatibleClassifier.openAi(apiKey, model, temperature, baseUrl, timeout,
                    objectMapper);
            case DEEPSEEK -> OpenAiCompatibleClassifier.deepSeek(apiKey, model, temperature, baseUrl, timeout,
                    objectMapper);
            case ANTHROPIC -> new AnthropicChangeClassifier(apiKey, model, temperature, baseUrl, timeout, objectMapper);
        }, metrics));
    }

    /**
     * The temperature every request asks for: {@link AiClassificationPrompt#TEMPERATURE} when
     * the setting is unset or blank, none for {@code default}, or a number within the
     * provider's range. Blank counts as unset, since an environment variable can be set to
     * nothing.
     */
    static OptionalDouble temperature(String value, AiProvider provider) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            return OptionalDouble.of(AiClassificationPrompt.TEMPERATURE);
        }
        if (text.equalsIgnoreCase(PROVIDER_DEFAULT_TEMPERATURE)) {
            return OptionalDouble.empty();
        }
        BigDecimal number;
        try {
            number = new BigDecimal(text);
        } catch (NumberFormatException exception) {
            number = null;
        }
        if (number == null || number.signum() < 0 || number.compareTo(provider.maxTemperature()) > 0) {
            throw new IllegalStateException(
                    "RELEASEFLOW_AI_TEMPERATURE must be a number from 0 to " + provider.maxTemperature().toPlainString()
                            + " for " + provider.getValue() + ", or " + PROVIDER_DEFAULT_TEMPERATURE
                            + " to send none."
            );
        }
        return OptionalDouble.of(number.doubleValue());
    }

    private static String setting(Function<String, String> settings, AiProvider provider, String name) {
        String value = settings.apply(provider.getValue() + "." + name);
        return value == null ? "" : value.strip();
    }
}
