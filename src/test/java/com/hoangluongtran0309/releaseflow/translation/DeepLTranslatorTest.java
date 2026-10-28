package com.hoangluongtran0309.releaseflow.translation;

import com.hoangluongtran0309.releaseflow.support.DeepLStub;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepLTranslatorTest {

    private static final DeepLStub DEEPL = DeepLStub.start();
    private static final DeepLTranslator TRANSLATOR = new DeepLTranslator(
            "deepl-test-key:fx", DEEPL.baseUrl(), Duration.ofSeconds(5), new ObjectMapper()
    );

    @AfterAll
    static void stopStub() {
        DEEPL.close();
    }

    @BeforeEach
    void resetStub() {
        DEEPL.reset();
    }

    @Test
    void sendsTheTextsWithTheKeyAndLanguageCodes() throws Exception {
        assertThat(TRANSLATOR.translate(List.of("Tables export as CSV.", "Users asked."), "en", "vi", "", null))
                .containsExactly("[VI] (dịch) Tables export as CSV.", "[VI] (dịch) Users asked.");

        assertThat(DEEPL.requests()).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/v2/translate");
            assertThat(request.authorization()).isEqualTo("DeepL-Auth-Key deepl-test-key:fx");
            assertThat(request.sourceLanguage()).isEqualTo("EN");
            assertThat(request.targetLanguage()).isEqualTo("VI");
            assertThat(request.context()).isNull();
        });
    }

    @Test
    void sendsTheProjectsContextWhichIsNotTranslated() throws Exception {
        assertThat(TRANSLATOR.translate(List.of("The whole console is in Vietnamese."), "en", "vi",
                "The console is the web UI. The CLI stays English.", null))
                .containsExactly("[VI] (dịch) The whole console is in Vietnamese.");

        assertThat(DEEPL.requests()).singleElement().satisfies(request -> {
            assertThat(request.texts()).containsExactly("The whole console is in Vietnamese.");
            assertThat(request.context()).isEqualTo("The console is the web UI. The CLI stays English.");
        });
    }

    @ParameterizedTest
    @CsvSource({"en, EN-US", "en-GB, EN-GB", "pt, PT-BR", "pt-PT, PT-PT", "vi, VI", "vi-VN, VI-VN", "de, DE"})
    void mapsTargetLanguages(String tag, String code) {
        assertThat(DeepLTranslator.targetCode(tag)).isEqualTo(code);
    }

    @Test
    void sourceLanguagesDropTheirRegion() {
        assertThat(DeepLTranslator.sourceCode("en-GB")).isEqualTo("EN");
        assertThat(DeepLTranslator.sourceCode("vi")).isEqualTo("VI");
    }

    @Test
    void choosesTheFreeHostForAFreeKey() {
        assertThat(DeepLTranslator.defaultBaseUrl("abc:fx")).isEqualTo(DeepLTranslator.FREE_BASE_URL);
        assertThat(DeepLTranslator.defaultBaseUrl("abc")).isEqualTo(DeepLTranslator.PRO_BASE_URL);
    }

    @Test
    void splitsBatchesByCountAndSize() throws Exception {
        List<String> many = IntStream.range(0, 120).mapToObj(index -> "Text " + index).toList();
        assertThat(DeepLTranslator.batches(many)).extracting(List::size).containsExactly(50, 50, 20);

        String large = "x".repeat(50 * 1024);
        assertThat(DeepLTranslator.batches(List.of(large, large, large, "short")))
                .extracting(List::size).containsExactly(2, 2);

        assertThat(TRANSLATOR.translate(many, "en", "vi", "", null)).hasSize(120).last().isEqualTo("[VI] (dịch) Text 119");
        assertThat(DEEPL.requests()).extracting(request -> request.texts().size()).containsExactly(50, 50, 20);
    }

    @Test
    void rateLimitsAndServerErrorsCanBeRetried() {
        DEEPL.fail(429, 1);
        assertThatThrownBy(() -> TRANSLATOR.translate(List.of("a"), "en", "vi", "", null))
                .isInstanceOfSatisfying(TranslationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TranslationException.RATE_LIMITED);
                    assertThat(exception.retryable()).isTrue();
                });
        DEEPL.fail(503, 1);
        assertThatThrownBy(() -> TRANSLATOR.translate(List.of("a"), "en", "vi", "", null))
                .isInstanceOfSatisfying(TranslationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TranslationException.UNAVAILABLE);
                    assertThat(exception.retryable()).isTrue();
                });
    }

    @Test
    void aRejectedKeyOrAnExhaustedQuotaIsNotRetried() {
        DEEPL.fail(403, 1);
        assertThatThrownBy(() -> TRANSLATOR.translate(List.of("a"), "en", "vi", "", null))
                .isInstanceOfSatisfying(TranslationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TranslationException.REJECTED);
                    assertThat(exception.retryable()).isFalse();
                });
        DEEPL.fail(456, 1);
        assertThatThrownBy(() -> TRANSLATOR.translate(List.of("a"), "en", "vi", "", null))
                .isInstanceOfSatisfying(TranslationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TranslationException.QUOTA_EXCEEDED);
                    assertThat(exception.retryable()).isFalse();
                });
    }

    @Test
    void aResponseWithTheWrongNumberOfTranslationsIsRejected() {
        DEEPL.dropOneTranslation();

        assertThatThrownBy(() -> TRANSLATOR.translate(List.of("a", "b"), "en", "vi", "", null))
                .isInstanceOfSatisfying(TranslationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TranslationException.INVALID_RESPONSE);
                    assertThat(exception.retryable()).isFalse();
                });
    }

    @Test
    void anUnreachableProviderCanBeRetried() {
        DeepLTranslator unreachable = new DeepLTranslator("key", "http://127.0.0.1:1", Duration.ofSeconds(1),
                new ObjectMapper());

        assertThatThrownBy(() -> unreachable.translate(List.of("a"), "en", "vi", "", null))
                .isInstanceOfSatisfying(TranslationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TranslationException.UNAVAILABLE);
                    assertThat(exception.retryable()).isTrue();
                });
    }

    @Test
    void aGlossaryIsCreatedWithRootLanguagesAndItsEntriesAreCounted() throws Exception {
        String id = TRANSLATOR.createGlossary("releaseflow-org", "en", "vi-VN", "console\tconsole\nstorage\tlưu trữ", 2);

        assertThat(id).isEqualTo(DEEPL.glossaries().keySet().iterator().next());
        assertThat(DEEPL.glossaries().get(id)).containsOnlyKeys("en:vi");
        assertThat(DEEPL.glossaries().get(id).get("en:vi")).containsEntry("console", "console").containsEntry("storage", "lưu trữ");
        assertThat(DEEPL.requests().getLast().body())
                .contains("\"source_lang\":\"en\"", "\"target_lang\":\"vi\"", "\"entries_format\":\"tsv\"");

        TRANSLATOR.replaceDictionary(id, "en", "vi", "console\tconsole", 1);
        assertThat(DEEPL.glossaries().get(id).get("en:vi")).containsOnlyKeys("console");
        assertThatThrownBy(() -> TRANSLATOR.replaceDictionary(id, "en", "vi", "a\ta\nb\tb", 3))
                .as("an answer that does not count what was sent")
                .isInstanceOfSatisfying(TranslationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(TranslationException.INVALID_RESPONSE));
    }

    @Test
    void aSecondGlossaryOnAFreeAccountIsAStopAndAMissingOneIsGone() throws Exception {
        TRANSLATOR.createGlossary("first", "en", "vi", "CLI\tCLI", 1);

        assertThatThrownBy(() -> TRANSLATOR.createGlossary("second", "en", "de", "CLI\tCLI", 1))
                .isInstanceOfSatisfying(TranslationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TranslationException.QUOTA_EXCEEDED);
                    assertThat(exception.retryable()).isFalse();
                });
        assertThatThrownBy(() -> TRANSLATOR.replaceDictionary("deleted", "en", "vi", "CLI\tCLI", 1))
                .isInstanceOfSatisfying(TranslationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TranslationException.GLOSSARY_NOT_FOUND);
                    assertThat(exception.retryable()).isFalse();
                });
        DEEPL.fail("/v3", 529, 1);
        assertThatThrownBy(() -> TRANSLATOR.createGlossary("again", "en", "vi", "CLI\tCLI", 1))
                .isInstanceOfSatisfying(TranslationException.class, exception -> assertThat(exception.retryable()).isTrue());
    }

    @Test
    void theGlossaryLanguagesAreTheListedOnesWhateverTheirFeatures() throws Exception {
        DEEPL.glossaryLanguages(Set.of("en", "vi"));

        Translator.GlossaryLanguages languages = TRANSLATOR.glossaryLanguages();

        assertThat(languages.supports("en", "vi")).isTrue();
        assertThat(languages.supports("en", "th")).isFalse();
        assertThat(DEEPL.requests().getLast().path()).isEqualTo("/v3/languages");
    }

    @Test
    void aTranslationCarriesItsGlossaryBesideTheContext() throws Exception {
        String id = TRANSLATOR.createGlossary("g", "en", "vi", "storage\tlưu trữ", 1);

        assertThat(TRANSLATOR.translate(List.of("New storage."), "en", "vi", "dbvault backs up databases.", id))
                .containsExactly("[VI] (dịch) New lưu trữ.");
        assertThat(DEEPL.translations()).singleElement().satisfies(request -> {
            assertThat(request.glossaryId()).isEqualTo(id);
            assertThat(request.context()).isEqualTo("dbvault backs up databases.");
        });

        DEEPL.refuseGlossary(1);
        assertThatThrownBy(() -> TRANSLATOR.translate(List.of("a"), "en", "vi", "", id))
                .as("refused over its glossary: the glossary is sent again and the translation retried")
                .isInstanceOfSatisfying(TranslationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TranslationException.GLOSSARY_REFUSED);
                    assertThat(exception.retryable()).isTrue();
                });
    }
}
