package com.hoangluongtran0309.releaseflow.automation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TeamsTextTest {

    @Test
    void showsWhatTheNoteEscapedWithoutItsBackslashes() {
        String note = """
                # Release 1.4.0

                Backups live under BACKUP_DIR/\\<target-id>/.

                ## ✨ New Features

                - add \\*bold\\* \\[link\\](x) \\<b\\>
                """;

        assertThat(TeamsText.render(note)).isEqualTo("""
                Release 1.4.0

                Backups live under BACKUP_DIR/<target-id>/.

                ✨ New Features

                - add *bold* [link](x) <b>""");
    }

    @Test
    void dropsMarkupButKeepsLinkAddressesAndCode() {
        assertThat(TeamsText.render("- **Export** — see [docs](https://example.com) and `tls_required`"))
                .isEqualTo("- Export — see \"docs\" (https://example.com) and \"tls_required\"");
    }

    @Test
    void writesNothingForAnEmptyNote() {
        assertThat(TeamsText.render("")).isEmpty();
        assertThat(TeamsText.render(null)).isEmpty();
    }
}
