package com.hoangluongtran0309.releaseflow.changelog;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CorrectionLineTest {

    @Test
    void readsInTheNotesLanguage() {
        Instant corrected = Instant.parse("2026-11-15T10:00:00Z");

        assertThat(CorrectionLine.of("en", corrected, "Placeholders keep their case."))
                .isEqualTo("Corrected on 15 November 2026: Placeholders keep their case.");
        assertThat(CorrectionLine.of("vi", corrected, "Giữ nguyên chữ hoa của placeholder."))
                .isEqualTo("Đã sửa vào ngày 15 tháng 11 năm 2026: Giữ nguyên chữ hoa của placeholder.");
    }

    @Test
    void keepsTheDayInUtcAsAPublishedDateDoes() {
        // Already 16 November in Vietnam; the line says the 15th, as the published date would.
        assertThat(CorrectionLine.of("vi", Instant.parse("2026-11-15T23:59:59Z"), "x"))
                .isEqualTo("Đã sửa vào ngày 15 tháng 11 năm 2026: x");
        assertThat(CorrectionLine.of("en", Instant.parse("2026-11-16T00:00:00Z"), "x"))
                .isEqualTo("Corrected on 16 November 2026: x");
    }

    @Test
    void readsALanguageWithoutATranslationInEnglishAsTheNotesLabelsAre() {
        assertThat(CorrectionLine.of("de", Instant.parse("2026-11-15T10:00:00Z"), "x"))
                .startsWith("Corrected on 15 ");
    }

    @Test
    void returnsThePersonsReasonAsWrittenForWhoeverEscapesIt() {
        assertThat(CorrectionLine.of("en", Instant.parse("2026-11-15T10:00:00Z"), "<b>it's {0}</b> & more"))
                .isEqualTo("Corrected on 15 November 2026: <b>it's {0}</b> & more");
    }
}
