package com.hoangluongtran0309.releaseflow.configuration;

import org.springframework.boot.test.context.SpringBootTest;

/**
 * A deployment still on the deprecated {@code SERVER_FORWARD_HEADERS_STRATEGY=native} keeps
 * every redirect and Secure cookie {@link ForwardedHeaderIntegrationTest} proves, until
 * Spring Boot removes the name.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.forward-headers-strategy=native")
class DeprecatedForwardHeadersStrategyIntegrationTest extends ForwardedHeaderIntegrationTest {
}
