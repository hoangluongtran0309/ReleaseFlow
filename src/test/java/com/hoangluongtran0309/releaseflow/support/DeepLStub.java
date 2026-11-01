package com.hoangluongtran0309.releaseflow.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A local HTTP server standing in for DeepL's {@code POST /v2/translate}, its v3
 * glossaries, and {@code GET /v3/languages?resource=glossary}. By default it "translates"
 * each text by prefixing the target language, such as {@code [DE] text}; a Vietnamese
 * answer also carries {@code (dịch)}, so it reads as Vietnamese to the untranslated guard,
 * as {@code [VI] (dịch) text}. A request with a glossary first replaces each entry whose
 * target differs from its source. Like an API Free account, it holds one glossary at a
 * time: creating a second one is answered {@code 456}.
 */
public final class DeepLStub implements AutoCloseable {

    public static final String VIETNAMESE_MARKER = "(dịch) ";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern DICTIONARIES = Pattern.compile("/v3/glossaries/([^/]+)/dictionaries");
    private static final Pattern GLOSSARY = Pattern.compile("/v3/glossaries/([^/]+)");

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresLeft = new AtomicInteger();
    private final AtomicInteger glossaryRefusalsLeft = new AtomicInteger();
    private final AtomicInteger glossaryIds = new AtomicInteger();
    // Glossary ID -> "source:target" -> source term -> target term.
    private final Map<String, Map<String, Map<String, String>>> glossaries = new ConcurrentHashMap<>();
    private volatile int failureStatus = 500;
    private volatile String failurePath = "/";
    private volatile boolean dropOne;
    private volatile boolean untranslatedWithGlossary;
    private volatile boolean untranslated;
    private volatile int maxGlossaries = 1;
    private volatile Set<String> glossaryLanguages = Set.of("en", "vi", "de", "fr", "ja");

    private DeepLStub() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public static DeepLStub start() {
        return new DeepLStub();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** The next {@code times} requests fail with this HTTP status. */
    public void fail(int status, int times) {
        fail("/", status, times);
    }

    /** The next {@code times} requests whose path starts with {@code pathPrefix} fail with this HTTP status. */
    public void fail(String pathPrefix, int status, int times) {
        failurePath = pathPrefix;
        failureStatus = status;
        failuresLeft.set(times);
    }

    /** Answers with one translation fewer than the texts it was sent. */
    public void dropOneTranslation() {
        dropOne = true;
    }

    /** Answers a translation sent with a glossary with its texts unchanged, as DeepL once did. */
    public void answerUntranslatedWithGlossary() {
        untranslatedWithGlossary = true;
    }

    /** Answers every translation with its texts unchanged, with a glossary or without. */
    public void answerUntranslated() {
        untranslated = true;
    }

    /** Refuses the next {@code times} translations sent with a glossary with a {@code 400}. */
    public void refuseGlossary(int times) {
        glossaryRefusalsLeft.set(times);
    }

    /** The primary languages a glossary can use, as source and as target. */
    public void glossaryLanguages(Set<String> languages) {
        glossaryLanguages = Set.copyOf(languages);
    }

    /** How many glossaries the account may hold; 1, as on API Free, by default. */
    public void maxGlossaries(int max) {
        maxGlossaries = max;
    }

    /** Glossaries the stub holds, each as "source:target" -> its entries. */
    public Map<String, Map<String, Map<String, String>>> glossaries() {
        return Map.copyOf(glossaries);
    }

    /** A glossary created outside ReleaseFlow, taking a slot of the account. */
    public String addForeignGlossary() {
        String id = "foreign-" + glossaryIds.incrementAndGet();
        glossaries.put(id, new ConcurrentHashMap<>());
        return id;
    }

    /** Deletes every glossary, as a change of DeepL account would leave ReleaseFlow. */
    public void forgetGlossaries() {
        glossaries.clear();
    }

    public void reset() {
        requests.clear();
        failuresLeft.set(0);
        failurePath = "/";
        glossaryRefusalsLeft.set(0);
        dropOne = false;
        untranslatedWithGlossary = false;
        untranslated = false;
        maxGlossaries = 1;
        glossaryLanguages = Set.of("en", "vi", "de", "fr", "ja");
        glossaries.clear();
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    /** Requests to {@code POST /v2/translate} only. */
    public List<RecordedRequest> translations() {
        return requests.stream().filter(request -> request.path().equals("/v2/translate")).toList();
    }

    /** Every text sent for translation, in order. */
    public List<String> translatedTexts() {
        return translations().stream().flatMap(request -> request.texts().stream()).toList();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode body = raw.isBlank() ? OBJECT_MAPPER.createObjectNode() : OBJECT_MAPPER.readTree(raw);
        String path = exchange.getRequestURI().getPath();
        List<String> texts = new ArrayList<>();
        body.path("text").values().forEach(text -> texts.add(text.stringValue()));
        requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                path,
                exchange.getRequestHeaders().getFirst("Authorization"),
                body.path("source_lang").isString() ? body.path("source_lang").stringValue() : null,
                body.path("target_lang").isString() ? body.path("target_lang").stringValue() : null,
                List.copyOf(texts),
                body.path("context").isString() ? body.path("context").stringValue() : null,
                body.path("glossary_id").isString() ? body.path("glossary_id").stringValue() : null,
                raw
        ));
        if (path.startsWith(failurePath) && failuresLeft.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
            send(exchange, failureStatus, "{\"message\":\"stubbed failure\"}");
            return;
        }
        String method = exchange.getRequestMethod();
        if (path.equals("/v2/translate")) {
            translate(exchange, body, texts);
        } else if (path.equals("/v3/languages")) {
            languages(exchange);
        } else if (path.equals("/v3/glossaries") && method.equals("POST")) {
            create(exchange, body);
        } else if (path.equals("/v3/glossaries") && method.equals("GET")) {
            List<Map<String, String>> listed = glossaries.keySet().stream().map(id -> Map.of("glossary_id", id)).toList();
            send(exchange, 200, OBJECT_MAPPER.writeValueAsString(Map.of("glossaries", listed)));
        } else if (DICTIONARIES.matcher(path).matches() && method.equals("PUT")) {
            replace(exchange, body, DICTIONARIES.matcher(path));
        } else if (GLOSSARY.matcher(path).matches() && method.equals("DELETE")) {
            Matcher matcher = GLOSSARY.matcher(path);
            matcher.matches();
            send(exchange, glossaries.remove(matcher.group(1)) == null ? 404 : 204, "");
        } else {
            send(exchange, 404, "{\"message\":\"Not found\"}");
        }
    }

    private void translate(HttpExchange exchange, JsonNode body, List<String> texts) throws IOException {
        String target = body.path("target_lang").stringValue();
        String glossaryId = body.path("glossary_id").isString() ? body.path("glossary_id").stringValue() : null;
        Map<String, String> entries = Map.of();
        if (glossaryId != null) {
            if (glossaryRefusalsLeft.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
                send(exchange, 400, "{\"message\":\"Invalid glossary\"}");
                return;
            }
            String pair = primary(body.path("source_lang").stringValue()) + ":" + primary(target);
            Map<String, Map<String, String>> glossary = glossaries.get(glossaryId);
            if (glossary == null || !glossary.containsKey(pair)) {
                send(exchange, 400, "{\"message\":\"No dictionary for this language pair\"}");
                return;
            }
            entries = glossary.get(pair);
        }
        List<Map<String, String>> translations = new ArrayList<>();
        for (String text : texts) {
            String translated = text;
            if (!untranslated && !(glossaryId != null && untranslatedWithGlossary)) {
                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    if (!entry.getKey().equals(entry.getValue())) {
                        translated = translated.replaceAll("\\b" + Pattern.quote(entry.getKey()) + "\\b",
                                Matcher.quoteReplacement(entry.getValue()));
                    }
                }
                String upper = target.toUpperCase(Locale.ROOT);
                translated = "[" + upper + "] " + (upper.startsWith("VI") ? VIETNAMESE_MARKER : "") + translated;
            }
            translations.add(Map.of("detected_source_language", "EN", "text", translated));
        }
        if (dropOne && !translations.isEmpty()) {
            translations.removeLast();
        }
        send(exchange, 200, OBJECT_MAPPER.writeValueAsString(Map.of("translations", translations)));
    }

    private void languages(HttpExchange exchange) throws IOException {
        // Like DeepL's own answer, "features" is empty: being listed is what counts.
        List<Map<String, Object>> languages = glossaryLanguages.stream()
                .map(language -> Map.<String, Object>of("lang", language, "name", language, "usable_as_source", true,
                        "usable_as_target", true, "features", Map.of()))
                .toList();
        send(exchange, 200, OBJECT_MAPPER.writeValueAsString(languages));
    }

    private void create(HttpExchange exchange, JsonNode body) throws IOException {
        if (glossaries.size() >= maxGlossaries) {
            send(exchange, 456, "{\"message\":\"Quota exceeded\"}");
            return;
        }
        String id = "glossary-" + glossaryIds.incrementAndGet();
        Map<String, Map<String, String>> dictionaries = new ConcurrentHashMap<>();
        List<Map<String, Object>> created = new ArrayList<>();
        for (JsonNode dictionary : body.path("dictionaries").values()) {
            Map<String, String> entries = entries(dictionary);
            String pair = dictionary.path("source_lang").stringValue() + ":" + dictionary.path("target_lang").stringValue();
            dictionaries.put(pair, entries);
            created.add(Map.of("source_lang", dictionary.path("source_lang").stringValue(),
                    "target_lang", dictionary.path("target_lang").stringValue(), "entry_count", entries.size()));
        }
        glossaries.put(id, dictionaries);
        send(exchange, 201, OBJECT_MAPPER.writeValueAsString(Map.of("glossary_id", id, "name",
                body.path("name").stringValue(), "dictionaries", created)));
    }

    private void replace(HttpExchange exchange, JsonNode body, Matcher matcher) throws IOException {
        matcher.matches();
        Map<String, Map<String, String>> glossary = glossaries.get(matcher.group(1));
        if (glossary == null) {
            send(exchange, 404, "{\"message\":\"Glossary not found\"}");
            return;
        }
        Map<String, String> entries = entries(body);
        glossary.put(body.path("source_lang").stringValue() + ":" + body.path("target_lang").stringValue(), entries);
        send(exchange, 200, OBJECT_MAPPER.writeValueAsString(Map.of("source_lang", body.path("source_lang").stringValue(),
                "target_lang", body.path("target_lang").stringValue(), "entry_count", entries.size())));
    }

    private static Map<String, String> entries(JsonNode dictionary) {
        Map<String, String> entries = new LinkedHashMap<>();
        for (String line : dictionary.path("entries").stringValue().split("\n")) {
            String[] parts = line.split("\t", 2);
            entries.put(parts[0], parts[1]);
        }
        return entries;
    }

    private static String primary(String language) {
        return language.toLowerCase(Locale.ROOT).split("-")[0];
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length == 0 ? -1 : payload.length);
        if (payload.length > 0) {
            exchange.getResponseBody().write(payload);
        }
        exchange.close();
    }

    public record RecordedRequest(
            String method,
            String path,
            String authorization,
            String sourceLanguage,
            String targetLanguage,
            List<String> texts,
            String context,
            String glossaryId,
            String body
    ) {
    }
}
