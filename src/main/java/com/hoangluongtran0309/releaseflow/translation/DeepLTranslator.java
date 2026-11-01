package com.hoangluongtran0309.releaseflow.translation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * DeepL's {@code POST /v2/translate}, and its v3 glossaries (ADR-0036). Texts are sent in
 * batches that stay under DeepL's request limits, and a response that does not return one
 * translation per text is rejected. A Project's context goes in {@code context}, which
 * DeepL neither translates nor bills, and a glossary in {@code glossary_id}. A {@code 456}
 * is a stop, never a retry: it is the character quota, or the number of glossaries the
 * plan allows. The API key is never logged.
 */
final class DeepLTranslator implements Translator {

    static final int MAX_BATCH_BYTES = 120 * 1024;
    static final int MAX_BATCH_TEXTS = 50;
    static final String FREE_BASE_URL = "https://api-free.deepl.com";
    static final String PRO_BASE_URL = "https://api.deepl.com";

    private static final Logger log = LoggerFactory.getLogger(DeepLTranslator.class);
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int QUOTA_EXCEEDED = 456;
    private static final int NOT_FOUND = 404;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    DeepLTranslator(String apiKey, String baseUrl, Duration timeout, ObjectMapper objectMapper) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        // A redirect proves nothing about who answered, so it is never followed.
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build()
        );
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl == null || baseUrl.isBlank() ? defaultBaseUrl(apiKey) : baseUrl.strip())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key " + apiKey)
                .build();
        this.objectMapper = objectMapper;
    }

    // DeepL issues keys ending in ":fx" for its free API, which has its own host.
    static String defaultBaseUrl(String apiKey) {
        return apiKey.endsWith(":fx") ? FREE_BASE_URL : PRO_BASE_URL;
    }

    @Override
    public List<String> translate(List<String> texts, String sourceLanguage, String targetLanguage, String context,
                                  String glossaryId) throws TranslationException {
        Translator.requireNoTransaction();
        List<String> translations = new ArrayList<>(texts.size());
        for (List<String> batch : batches(texts)) {
            translations.addAll(translateBatch(batch, sourceCode(sourceLanguage), targetCode(targetLanguage), context,
                    glossaryId));
        }
        return translations;
    }

    @Override
    public GlossaryLanguages glossaryLanguages() throws TranslationException {
        Translator.requireNoTransaction();
        JsonNode languages = read(send(() -> restClient.get()
                .uri("/v3/languages?resource=glossary")
                .retrieve()
                .body(String.class), false));
        if (!languages.isArray()) {
            throw new TranslationException(TranslationException.INVALID_RESPONSE, false);
        }
        // Being listed is what says a language can be used in a glossary; "features" is empty
        // for languages whose glossaries work, so it is not read.
        Set<String> sources = new HashSet<>();
        Set<String> targets = new HashSet<>();
        for (JsonNode language : languages.values()) {
            String code = language.path("lang").asString("").toLowerCase(Locale.ROOT);
            if (code.isEmpty()) {
                continue;
            }
            if (language.path("usable_as_source").asBoolean(false)) {
                sources.add(TranslationTexts.primary(code));
            }
            if (language.path("usable_as_target").asBoolean(false)) {
                targets.add(TranslationTexts.primary(code));
            }
        }
        return new GlossaryLanguages(sources, targets);
    }

    @Override
    public String createGlossary(String name, String sourceLanguage, String targetLanguage, String tsv, int entries)
            throws TranslationException {
        Translator.requireNoTransaction();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", name);
        body.putArray("dictionaries").add(dictionary(sourceLanguage, targetLanguage, tsv));
        JsonNode created = read(send(() -> restClient.post()
                .uri("/v3/glossaries")
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .body(String.class), false));
        String id = created.path("glossary_id").asString("");
        JsonNode dictionaries = created.path("dictionaries");
        if (id.isEmpty() || !dictionaries.isArray() || dictionaries.size() != 1
                || dictionaries.get(0).path("entry_count").asInt(-1) != entries) {
            throw new TranslationException(TranslationException.INVALID_RESPONSE, false);
        }
        return id;
    }

    @Override
    public void replaceDictionary(String glossaryId, String sourceLanguage, String targetLanguage, String tsv, int entries)
            throws TranslationException {
        Translator.requireNoTransaction();
        JsonNode replaced = read(send(() -> restClient.put()
                .uri("/v3/glossaries/{id}/dictionaries", glossaryId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(dictionary(sourceLanguage, targetLanguage, tsv)))
                .retrieve()
                .body(String.class), true));
        if (replaced.path("entry_count").asInt(-1) != entries) {
            throw new TranslationException(TranslationException.INVALID_RESPONSE, false);
        }
    }

    // A dictionary is created with root language codes: it applies to every region of its language.
    private ObjectNode dictionary(String sourceLanguage, String targetLanguage, String tsv) {
        ObjectNode dictionary = objectMapper.createObjectNode();
        dictionary.put("source_lang", TranslationTexts.primary(sourceLanguage));
        dictionary.put("target_lang", TranslationTexts.primary(targetLanguage));
        dictionary.put("entries", tsv);
        dictionary.put("entries_format", "tsv");
        return dictionary;
    }

    private String send(Supplier<String> call, boolean notFoundIsGone) throws TranslationException {
        try {
            return call.get();
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            log.warn("DeepL returned HTTP {} to a glossary request.", status);
            if (notFoundIsGone && status == NOT_FOUND) {
                throw new TranslationException(TranslationException.GLOSSARY_NOT_FOUND, false);
            }
            throw failure(exception);
        } catch (RestClientException exception) {
            log.warn("Could not reach DeepL.");
            throw new TranslationException(TranslationException.UNAVAILABLE, true);
        }
    }

    private JsonNode read(String response) throws TranslationException {
        try {
            return objectMapper.readTree(response == null ? "" : response);
        } catch (JacksonException exception) {
            throw new TranslationException(TranslationException.INVALID_RESPONSE, false);
        }
    }

    // 429 and every 5xx are retried with backoff; 456, the quota or the plan's glossaries, never is.
    private static TranslationException failure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == TOO_MANY_REQUESTS) {
            return new TranslationException(TranslationException.RATE_LIMITED, true);
        }
        if (status == QUOTA_EXCEEDED) {
            return new TranslationException(TranslationException.QUOTA_EXCEEDED, false);
        }
        return exception.getStatusCode().is5xxServerError()
                ? new TranslationException(TranslationException.UNAVAILABLE, true)
                : new TranslationException(TranslationException.REJECTED, false);
    }

    /** Consecutive texts, at most {@value #MAX_BATCH_TEXTS} and about 120 KiB per request. */
    static List<List<String>> batches(List<String> texts) {
        List<List<String>> batches = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int bytes = 0;
        for (String text : texts) {
            int size = text.getBytes(StandardCharsets.UTF_8).length;
            if (!current.isEmpty() && (current.size() == MAX_BATCH_TEXTS || bytes + size > MAX_BATCH_BYTES)) {
                batches.add(current);
                current = new ArrayList<>();
                bytes = 0;
            }
            current.add(text);
            bytes += size;
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }

    // DeepL takes a source language without region.
    static String sourceCode(String languageTag) {
        return Locale.forLanguageTag(languageTag).getLanguage().toUpperCase(Locale.ROOT);
    }

    // DeepL needs a regional variant for English and Portuguese targets.
    static String targetCode(String languageTag) {
        Locale locale = Locale.forLanguageTag(languageTag);
        String language = locale.getLanguage().toUpperCase(Locale.ROOT);
        String region = locale.getCountry();
        if (!region.isEmpty()) {
            return language + "-" + region.toUpperCase(Locale.ROOT);
        }
        return switch (language) {
            case "EN" -> "EN-US";
            case "PT" -> "PT-BR";
            default -> language;
        };
    }

    private List<String> translateBatch(List<String> batch, String source, String target, String context,
                                        String glossaryId) throws TranslationException {
        ObjectNode body = objectMapper.createObjectNode();
        batch.forEach(body.putArray("text")::add);
        body.put("source_lang", source);
        body.put("target_lang", target);
        if (context != null && !context.isBlank()) {
            body.put("context", context);
        }
        if (glossaryId != null) {
            body.put("glossary_id", glossaryId);
        }
        String response;
        try {
            response = restClient.post()
                    .uri("/v2/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            log.warn("DeepL returned HTTP {}.", status);
            TranslationException failure = failure(exception);
            // Refused with a glossary: the glossary is the likely cause, so it is sent again
            // and the translation retried, the last time without it.
            if (glossaryId != null && failure.code().equals(TranslationException.REJECTED)) {
                throw new TranslationException(TranslationException.GLOSSARY_REFUSED, true);
            }
            throw failure;
        } catch (RestClientException exception) {
            log.warn("Could not reach DeepL.");
            throw new TranslationException(TranslationException.UNAVAILABLE, true);
        }
        return parse(response, batch.size());
    }

    private List<String> parse(String response, int expected) throws TranslationException {
        final JsonNode translations;
        try {
            translations = objectMapper.readTree(response == null ? "" : response).path("translations");
        } catch (JacksonException exception) {
            throw new TranslationException(TranslationException.INVALID_RESPONSE, false);
        }
        if (!translations.isArray() || translations.size() != expected) {
            throw new TranslationException(TranslationException.INVALID_RESPONSE, false);
        }
        List<String> texts = new ArrayList<>(expected);
        for (JsonNode translation : translations.values()) {
            JsonNode text = translation.path("text");
            if (!text.isString()) {
                throw new TranslationException(TranslationException.INVALID_RESPONSE, false);
            }
            texts.add(text.stringValue());
        }
        return texts;
    }
}
