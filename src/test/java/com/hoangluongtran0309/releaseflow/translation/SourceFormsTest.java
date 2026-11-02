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
}
