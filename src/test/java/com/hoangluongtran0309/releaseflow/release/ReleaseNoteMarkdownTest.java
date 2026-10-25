package com.hoangluongtran0309.releaseflow.release;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseNoteMarkdownTest {

    @Test
    void escapesMarkdownSyntaxInUntrustedText() {
        assertThat(ReleaseNoteMarkdown.escape("handle *bold* [links] <tags> `code` \\ and _under_"))
                .isEqualTo("handle \\*bold\\* \\[links\\] \\<tags\\> \\`code\\` \\\\ and \\_under\\_");
    }

    @Test
    void leavesPlainTextAlone() {
        assertThat(ReleaseNoteMarkdown.escape("Add CSV export (#12).")).isEqualTo("Add CSV export (#12).");
    }

    @Test
    void escapesTheOpeningBracketOfInlineHtml() {
        assertThat(ReleaseNoteMarkdown.escapeRawHtml("Stored under BACKUP_DIR/<target-id>/<execution-id>/."))
                .isEqualTo("Stored under BACKUP_DIR/\\<target-id>/\\<execution-id>/.");
        assertThat(ReleaseNoteMarkdown.escapeRawHtml("Pass <b>bold</b> and <!-- a comment -->."))
                .isEqualTo("Pass \\<b>bold\\</b> and \\<!-- a comment -->.");
    }

    @Test
    void escapesEveryTagOfAnHtmlBlock() {
        assertThat(ReleaseNoteMarkdown.escapeRawHtml("<details>\n<summary>More</summary>\n</details>"))
                .isEqualTo("\\<details>\n\\<summary>More\\</summary>\n\\</details>");
    }

    @Test
    void leavesCodeAutolinksAndOtherMarkdownAsWritten() {
        String text = """
                Run `dbvault verify --backup-id <id>` and see <https://example.com>.
                Then a < b, a -> b, **bold**, _emphasis_, and [a link](https://example.com).

                ```html
                <html><body></body></html>
                ```

                    <indented code="yes">
                """;
        assertThat(ReleaseNoteMarkdown.escapeRawHtml(text)).isEqualTo(text);
    }

    @Test
    void escapingTwiceChangesNothingMore() {
        String once = ReleaseNoteMarkdown.escapeRawHtml("Use <id> in <details>, not `<id>`.\n\n<div>\nblock\n</div>");
        assertThat(ReleaseNoteMarkdown.escapeRawHtml(once)).isEqualTo(once);
    }

    @Test
    void leavesTextWithoutHtmlAndNothingAlone() {
        assertThat(ReleaseNoteMarkdown.escapeRawHtml("Add CSV export (#12).")).isEqualTo("Add CSV export (#12).");
        assertThat(ReleaseNoteMarkdown.escapeRawHtml("")).isEmpty();
        assertThat(ReleaseNoteMarkdown.escapeRawHtml(null)).isNull();
    }
}
