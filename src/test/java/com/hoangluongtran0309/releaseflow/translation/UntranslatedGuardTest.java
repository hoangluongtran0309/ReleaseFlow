package com.hoangluongtran0309.releaseflow.translation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guard against an answer left in its source language (ADR-0036), against what DeepL
 * really answered: the English it returned with a glossary in the third check, and the
 * Vietnamese it wrote for dbvault's release note texts, with and without a glossary.
 */
class UntranslatedGuardTest {

    private static final List<String> GLOSSARY = read("dbvault-glossary.tsv").stream()
            .map(line -> line.split("\t")[1])
            .toList();
    private static final UntranslatedGuard VIETNAMESE = new UntranslatedGuard("vi", GLOSSARY);

    @Test
    void theEnglishAnswersOfTheThirdCheckFail() {
        assertThat(List.of(
                "Image digests are pinned for each container image, and the console lists them.",
                "Image digest is pinned for each container image, and the console lists them.",
                "The images are digested and pinned for each container image, and the console lists them."
        )).allSatisfy(answer -> assertThat(VIETNAMESE.untranslated(answer)).as(answer).isTrue());
    }

    @Test
    void itsVietnameseAnswersPass() {
        assertThat(List.of(
                "Image digest được ghim cho mỗi hình ảnh container, và console liệt kê chúng.",
                "Image băm được ghim cho mỗi image container, và bảng điều khiển liệt kê chúng.",
                "Các mã băm Image được cố định cho mỗi container image, và console liệt kê chúng."
        )).allSatisfy(answer -> assertThat(VIETNAMESE.untranslated(answer)).as(answer).isFalse());
    }

    @Test
    void theFixturesVietnameseTranslationsPass() {
        List<String> withoutGlossary = read("vietnamese-translations.txt");
        List<String> withGlossary = read("vietnamese-translations-with-glossary.txt");

        assertThat(withoutGlossary).hasSize(60)
                .allSatisfy(answer -> assertThat(VIETNAMESE.untranslated(answer)).as(answer).isFalse());
        assertThat(withGlossary).hasSize(35)
                .allSatisfy(answer -> assertThat(VIETNAMESE.untranslated(answer)).as(answer).isFalse());
    }

    @Test
    void oneEnglishSentenceInAVietnameseAnswerFailsIt() {
        assertThat(VIETNAMESE.untranslated("Giờ đây console hiển thị bằng tiếng Việt. Every pull request now uses the new storage."))
                .isTrue();
    }

    @Test
    void onlyWordsLeftToTranslateCount() {
        UntranslatedGuard guard = new UntranslatedGuard("vi", List.of("pull request", "storage", "base images"));

        assertThat(guard.untranslated("Đã sửa `docker compose up --build` và https://example.com/docs/install.html."))
                .as("code spans and URLs").isFalse();
        assertThat(guard.untranslated("Xem ~/.config/dbvault/cli.properties, docker-compose.yml và DBVAULT_URL."))
                .as("paths, file names, and identifiers").isFalse();
        assertThat(guard.untranslated("Pull request base images storage."))
                .as("kept terms, in any case").isFalse();
        assertThat(guard.untranslated("MongoDB, PostgreSQL, CLI, HTTP API."))
                .as("words written in capitals or with a capital inside").isFalse();
        assertThat(guard.untranslated("Fixed it."))
                .as("fewer than three words left").isFalse();
        assertThat(guard.untranslated("Fixed the export."))
                .as("three words left, none Vietnamese").isTrue();
    }

    @Test
    void aNonLatinTargetNeedsCharactersOfItsScript() {
        UntranslatedGuard japanese = new UntranslatedGuard("ja-JP", List.of());
        UntranslatedGuard russian = new UntranslatedGuard("ru", List.of());

        assertThat(japanese.untranslated("コンソールがベトナム語で表示されます。")).isFalse();
        assertThat(japanese.untranslated("The whole console is in Vietnamese.")).isTrue();
        assertThat(russian.untranslated("Вся консоль теперь на вьетнамском.")).isFalse();
        assertThat(russian.untranslated("The whole console is in Vietnamese.")).isTrue();
        assertThat(russian.untranslated("CLI, MongoDB.")).as("nothing left to translate").isFalse();
    }

    @Test
    void otherLatinTargetsAreNotGuarded() {
        // German, French, and Dutch: a correct sentence can have no diacritic, and no rule is measured for them.
        assertThat(List.of("de", "fr", "nl", "id", "en-GB")).allSatisfy(language -> {
            UntranslatedGuard guard = new UntranslatedGuard(language, List.of());
            assertThat(guard.guards()).isFalse();
            assertThat(guard.untranslated("The whole console is in Vietnamese.")).isFalse();
        });
        assertThat(new UntranslatedGuard("vi-VN", List.of()).guards()).isTrue();
    }

    private static List<String> read(String name) {
        try (InputStream in = UntranslatedGuardTest.class.getResourceAsStream("/translation/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .map(line -> line.replace("\\n", "\n"))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
