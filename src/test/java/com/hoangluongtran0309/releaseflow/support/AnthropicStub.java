package com.hoangluongtran0309.releaseflow.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A local HTTP server standing in for the Claude Messages API, so tests exercise the
 * official SDK's real HTTP path without a network or an API key.
 */
public final class AnthropicStub implements AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    // What a model that takes only its own temperature answers to a request that names one.
    private volatile String temperatureRefusal;
    private volatile int status = 200;
    private volatile String body = message(OpenAiStub.classification("feature", false, false, "Adds a capability."), "end_turn");

    private AnthropicStub() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public static AnthropicStub start() {
        return new AnthropicStub();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void respond(int status, String body) {
        this.status = status;
        this.body = body;
    }

    /**
     * Answers 400 with {@code errorBody} to every request whose body has a {@code temperature},
     * as a model that accepts only its own does, and as before to any other.
     */
    public void refuseTemperature(String errorBody) {
        temperatureRefusal = errorBody;
    }

    private boolean refusesTemperature(String requestBody) {
        return temperatureRefusal != null && OBJECT_MAPPER.readTree(requestBody).has("temperature");
    }

    public void respondWithClassification(String category, boolean breaking, boolean needsReview, String summary) {
        respond(200, message(OpenAiStub.classification(category, breaking, needsReview, summary), "end_turn"));
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    public void reset() {
        requests.clear();
        temperatureRefusal = null;
        respondWithClassification("feature", false, false, "Adds a capability.");
    }

    /** A Messages API response whose only content block is {@code text}. */
    public static String message(String text, String stopReason) {
        return OBJECT_MAPPER.writeValueAsString(Map.of(
                "id", "msg_test",
                "type", "message",
                "role", "assistant",
                "model", "claude-test",
                "content", List.of(Map.of("type", "text", "text", text)),
                "stop_reason", stopReason,
                "usage", Map.of("input_tokens", 10, "output_tokens", 20)
        ));
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("x-api-key"),
                exchange.getRequestHeaders().getFirst("anthropic-version"),
                requestBody
        ));
        boolean refused = refusesTemperature(requestBody);
        byte[] payload = (refused ? temperatureRefusal : body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(refused ? 400 : status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    public record RecordedRequest(String method, String path, String apiKey, String anthropicVersion, String body) {
    }
}
