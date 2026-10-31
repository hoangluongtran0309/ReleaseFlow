package com.hoangluongtran0309.releaseflow.change;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum AiProvider {
    OPENAI("OpenAI", BigDecimal.TWO),
    ANTHROPIC("Anthropic", BigDecimal.ONE),
    DEEPSEEK("DeepSeek", BigDecimal.TWO);

    private final String label;
    private final BigDecimal maxTemperature;

    AiProvider(String label, BigDecimal maxTemperature) {
        this.label = label;
        this.maxTemperature = maxTemperature;
    }

    public String getValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String getLabel() {
        return label;
    }

    /** The highest temperature the provider's API accepts; the lowest is 0 for all three. */
    BigDecimal maxTemperature() {
        return maxTemperature;
    }

    static Optional<AiProvider> fromValue(String value) {
        return Arrays.stream(values())
                .filter(provider -> provider.getValue().equals(value))
                .findFirst();
    }
}
