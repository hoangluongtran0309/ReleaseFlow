package com.hoangluongtran0309.releaseflow.configuration;

import com.hoangluongtran0309.releaseflow.ReleaseFlowApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.source.MutuallyExclusiveConfigurationPropertiesException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForwardedHeaderConfigurationTest {

    @Test
    void startupExplainsWhenBothForwardedHeaderStrategiesAreSet() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(ReleaseFlowApplication.class)
                .registerShutdownHook(false)
                .run("--server.port=0", "--management.server.port=0",
                        "--server.forward-headers-strategy=native",
                        "--server.request.header.forwarded.strategy=web-server").close())
                .rootCause()
                .isInstanceOf(MutuallyExclusiveConfigurationPropertiesException.class)
                .hasMessageContaining("server.forward-headers-strategy")
                .hasMessageContaining("server.request.header.forwarded.strategy");
    }
}
