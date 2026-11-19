package com.hoangluongtran0309.releaseflow.translation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

// ADR-0040: a translation gets its source's placeholders and lowercase hyphenated identifiers
// back as written, where it differs in letter case alone, and nothing else changes.
class SourceFormsTest {

    // Answers DeepL returned with a glossary: the replay's recordings (<ID>, <TARGET-id>), and
    // the lab's v1.2.0 notes (<COUNT>, <BACKUP-id>, <ZONE>, dbvault-CLI).
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            GET /api/v1/verifications/<id> is removed.     | GET /api/v1/verifications/<ID> đã bị loại bỏ.      | GET /api/v1/verifications/<id> đã bị loại bỏ.
            Restore with --target <target-id>.             | Khôi phục với --target <TARGET-id>.                | Khôi phục với --target <target-id>.
            Retries <count> times.                         | Thử lại <COUNT> lần.                               | Thử lại <count> lần.
            Run dbvault backup verify <backup-id>.         | Chạy dbvault backup verify <BACKUP-id>.            | Chạy dbvault backup verify <backup-id>.
            Times are shown in <zone>.                     | Thời gian được hiển thị theo <ZONE>.                | Thời gian được hiển thị theo <zone>.
            Upgrade dbvault-cli with the server.           | Nâng cấp dbvault-CLI cùng với máy chủ.             | Nâng cấp dbvault-cli cùng với máy chủ.
            """)
    void recordedAnswersComeBackInTheirSourceForm(String source, String answer, String restored) {
        assertThat(SourceForms.restore(source, answer)).isEqualTo(restored);
        assertThat(SourceForms.lostPlaceholders(source, answer)).isEmpty();
    }

    @Test
    void aPlaceholderUsedTwiceIsRestoredEachTime() {
        assertThat(SourceForms.restore(
                "Verify <backup-id>, then restore <backup-id> to <target-id>.",
                "Xác minh <BACKUP-id>, rồi khôi phục <backup-id> vào <TARGET-id>."))
                .isEqualTo("Xác minh <backup-id>, rồi khôi phục <backup-id> vào <target-id>.");
    }

    @Test
    void aPlaceholderInsideACodeSpanIsRestoredAndTheSpanIsNotOtherwiseTouched() {
        assertThat(SourceForms.restore(
                "Run `dbvault backup verify <backup-id>` after each backup.",
                "Chạy `dbvault backup verify <BACKUP-id>` sau mỗi bản sao lưu."))
                .isEqualTo("Chạy `dbvault backup verify <backup-id>` sau mỗi bản sao lưu.");
    }

    @Test
    void aTextWithNothingToRestoreKeepsItsTranslationByteForByte() {
        String answer = "Bảng <B>xuất</B> ra CSV với <COUNT> dòng qua DBVAULT-CLI.";
        assertThat(SourceForms.restore("Tables export as CSV.", answer)).isSameAs(answer);
        String unchanged = "Thử lại <count> lần với dbvault-cli.";
        assertThat(SourceForms.restore("Retries <count> times with dbvault-cli.", unchanged)).isSameAs(unchanged);
    }

    @Test
    void onlyTheSourcesTokensChangeAndOnlyInCase() {
        assertThat(SourceForms.restore(
                "Use <count> with dbvault-cli; sign-in stays.",
                "Dùng <COUNT> và <LIMIT> với dbvault-CLI, không phải <counts> hay dbvault-CLIs; Sign-In giữ nguyên."))
                .isEqualTo("Dùng <count> và <LIMIT> với dbvault-cli, không phải <counts> hay dbvault-CLIs; sign-in giữ nguyên.");
    }

    @Test
    void aTokenTheSourceWritesInMoreThanOneCaseIsLeftAlone() {
        assertThat(SourceForms.restore("Maps <id> to <ID>.", "Ánh xạ <ID> sang <Id>."))
                .isEqualTo("Ánh xạ <ID> sang <Id>.");
        assertThat(SourceForms.restore("Install dbvault-cli, also named DBVAULT-CLI.", "Cài dbvault-CLI, còn gọi là DBVAULT-CLI."))
                .as("written in two cases in the source")
                .isEqualTo("Cài dbvault-CLI, còn gọi là DBVAULT-CLI.");
        assertThat(SourceForms.restore("Install Dbvault-Cli.", "Cài DBVAULT-CLI."))
                .as("an identifier is restored only when the source writes it in lowercase")
                .isEqualTo("Cài DBVAULT-CLI.");
    }

    @Test
    void onlyAPlaceholderTheTranslationNoLongerHasInAnyCaseIsLost() {
        assertThat(SourceForms.lostPlaceholders("Verify <backup-id> on <target-id>.", "Xác minh <BACKUP-id> trên mục tiêu."))
                .containsExactly("<target-id>");
        assertThat(SourceForms.lostPlaceholders("Verify <backup-id> twice: <backup-id>.", "Xác minh <BACKUP-id> hai lần."))
                .as("a placeholder the translation still holds once is not lost")
                .isEmpty();
        assertThat(SourceForms.restore("Verify <backup-id> on <target-id>.", "Xác minh <BACKUP-id> trên mục tiêu."))
                .as("a lost placeholder is left lost")
                .isEqualTo("Xác minh <backup-id> trên mục tiêu.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            <count>          | true
            <backup-id>      | true
            <target_id2>     | true
            <ünïcode-x>      | true
            <1st>            | false
            </closing>       | false
            <-id>            | false
            <a b>            | false
            <>               | false
            """)
    void aPlaceholderIsALetterThenLettersDigitsHyphensOrUnderscores(String token, boolean placeholder) {
        assertThat(SourceForms.PLACEHOLDER.matcher(token).matches()).isEqualTo(placeholder);
    }

    // ADR-0040, amended 2026-11-19: code spans. The answers are the lab's v1.4.0 notes
    // (`under 1 s`, `0 s`) and the golden package's (`bigserial`).

    @Test
    void aSpanTheTranslationKeepsStaysAsItIs() {
        String answer = "Bảng điều khiển hiển thị `under 1 s` cho bản sao lưu nhanh.";
        assertThat(SourceForms.restore("The console shows `under 1 s` for a fast backup.", answer)).isSameAs(answer);
        assertThat(SourceForms.unpairedSpans("The console shows `under 1 s` for a fast backup.", answer)).isEmpty();
    }

    @Test
    void aTranslatedSpanGetsItsSourceTextBack() {
        assertThat(SourceForms.restore(
                "The console shows `under 1 s` for a fast backup.",
                "Bảng điều khiển hiển thị `dưới 1 giây` cho bản sao lưu nhanh."))
                .isEqualTo("Bảng điều khiển hiển thị `under 1 s` cho bản sao lưu nhanh.");
    }

    @Test
    void twoTranslatedSpansInOneTextAreBothRestoredInOrder() {
        assertThat(SourceForms.restore(
                "A fast backup shows `under 1 s`, and an empty one shows `0 s`.",
                "Bản sao lưu nhanh hiển thị `dưới 1 giây`, còn bản rỗng hiển thị `0 giây`."))
                .isEqualTo("Bản sao lưu nhanh hiển thị `under 1 s`, còn bản rỗng hiển thị `0 s`.");
    }

    @Test
    void aKeptSpanAndATranslatedOneInOneTextAreToldApart() {
        assertThat(SourceForms.restore(
                "A fast backup shows `under 1 s`, and an empty one shows `0 s`.",
                "Bản sao lưu nhanh hiển thị `under 1 s`, còn bản rỗng hiển thị `0 giây`."))
                .isEqualTo("Bản sao lưu nhanh hiển thị `under 1 s`, còn bản rỗng hiển thị `0 s`.");
        // The same span twice: one occurrence kept, the other translated.
        assertThat(SourceForms.restore(
                "It shows `under 1 s`, then `under 1 s` again.",
                "Nó hiển thị `dưới 1 giây`, rồi `under 1 s` lần nữa."))
                .isEqualTo("Nó hiển thị `under 1 s`, rồi `under 1 s` lần nữa.");
    }

    // The known limit: pairing is by position, so two spans DeepL both reordered and
    // translated get each other's text. If this test changes, the amendment must too.
    @Test
    void twoSpansDeepLBothReorderedAndTranslatedGetEachOthersText() {
        assertThat(SourceForms.restore(
                "A fast backup shows `under 1 s`, and an empty one shows `0 s`.",
                "Bản rỗng hiển thị `0 giây`, còn bản sao lưu nhanh hiển thị `dưới 1 giây`."))
                .isEqualTo("Bản rỗng hiển thị `under 1 s`, còn bản sao lưu nhanh hiển thị `0 s`.");
    }

    @Test
    void aSpanDeepLAddedAroundAWordTheSourceWritesBareIsKept() {
        String answer = "Khóa chính dùng `bigserial` thay vì serial, với `0 s` khi trống.";
        assertThat(SourceForms.restore("Primary keys use bigserial instead of serial, with `0 s` when empty.", answer))
                .isSameAs(answer);
        assertThat(SourceForms.unpairedSpans("Primary keys use bigserial instead of serial, with `0 s` when empty.", answer))
                .isEmpty();
    }

    @Test
    void unequalCountsLeaveTheSpansAsWrittenAndSaySo() {
        // DeepL translated one span and dropped the backticks of the other.
        String source = "A fast backup shows `under 1 s`, and an empty one shows `0 s`.";
        String answer = "Bản sao lưu nhanh hiển thị `dưới 1 giây`, còn bản rỗng hiển thị 0 s.";
        assertThat(SourceForms.restore(source, answer)).isSameAs(answer);
        assertThat(SourceForms.unpairedSpans(source, answer)).contains(new SourceForms.UnpairedSpans(1, 2));
    }

    @Test
    void aPlaceholderInsideATranslatedSpanComesBackWithItsSpan() {
        assertThat(SourceForms.restore(
                "Run `verify <backup-id> now` after each backup.",
                "Chạy `xác minh <BACKUP-id> ngay` sau mỗi bản sao lưu."))
                .isEqualTo("Chạy `verify <backup-id> now` sau mỗi bản sao lưu.");
    }

    @Test
    void aTextWithoutSpansToRestoreKeepsItsTranslationByteForByte() {
        String answer = "Bảng điều khiển nhanh hơn, và `dbvault backups` vẫn như cũ.";
        assertThat(SourceForms.restore("The console is faster, and `dbvault backups` is unchanged.", answer))
                .isSameAs(answer);
    }

    // A fenced block's three backticks would pair with the wrong ones, so a text holding one
    // keeps its spans as DeepL wrote them: never broken, and not reported as unpaired.
    @Test
    void aTextWithAFencedCodeBlockKeepsItsSpansAsWritten() {
        String source = "Shows `under 1 s`:\n\n```\ndbvault backups --since 1d\n```\n";
        String answer = "Hiển thị `dưới 1 giây`:\n\n```\ndbvault backups --since 1d\n```\n";
        assertThat(SourceForms.restore(source, answer)).isSameAs(answer);
        assertThat(SourceForms.unpairedSpans(source, answer)).isEmpty();
    }

    // A double-backtick span, which may hold a backtick, is not read as two spans either.
    @Test
    void aTextWithADoubleBacktickSpanKeepsItsSpansAsWritten() {
        String source = "Quote with ``a `b` c`` and show `under 1 s`.";
        String answer = "Trích dẫn bằng ``a `b` c`` và hiển thị `dưới 1 giây`.";
        assertThat(SourceForms.restore(source, answer)).isSameAs(answer);
        assertThat(SourceForms.unpairedSpans(source, answer)).isEmpty();
        // Its placeholders are still restored.
        assertThat(SourceForms.restore("Use ``<id>`` here.", "Dùng ``<ID>`` ở đây.")).isEqualTo("Dùng ``<id>`` ở đây.");
    }
}
