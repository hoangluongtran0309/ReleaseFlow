package com.hoangluongtran0309.releaseflow.change;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LimitationsTest {

    @Test
    void keepsWhatTheChangeSaysItDoesNotDoYet() {
        assertThat(Limitations.stated("Server certificate validation is enforced only for MongoDB."))
                .isEqualTo("Server certificate validation is enforced only for MongoDB.");
        assertThat(Limitations.stated("Existing passwords keep the old scheme until each target is next saved.\n"
                + "- The CLI stays in English."))
                .isEqualTo("Existing passwords keep the old scheme until each target is next saved.\n- The CLI stays in English.");
    }

    // Remarks the AI wrote about its input in the dbvault replays, and their kind.
    @ParameterizedTest
    @ValueSource(strings = {
            "Specific method names, APIs, or configuration parameters are not detailed.",
            "Rollback steps are not provided.",
            "The exact version is not specified in the pull request.",
            "No information about performance impact is given.",
            "The pull request does not mention which databases are affected.",
            "Whether existing schedules migrate is unclear.",
            "No details on error handling.",
            "No detail about the retry policy.",
            "No mention of multi-user support or role-based access.",
            "The change makes no mention of Windows.",
            "It does not mention whether existing schedules migrate.",
            "Support for other databases is not mentioned.",
            "Không đề cập đến hỗ trợ nhiều người dùng.",
            "Phạm vi ảnh hưởng không được nêu rõ.",
            "Không có thông tin về cách rollback."
    })
    void dropsASentenceAboutTheInputItself(String remark) {
        assertThat(Limitations.stated(remark)).isEmpty();
        assertThat(Limitations.stated("Only MongoDB validates certificates yet. " + remark))
                .isEqualTo("Only MongoDB validates certificates yet.");
        assertThat(Limitations.stated("- " + remark + "\n- Only MongoDB validates certificates yet."))
                .isEqualTo("- Only MongoDB validates certificates yet.");
    }

    // Sentences the AI wrote in the dbvault replays that only say what the change touches.
    @ParameterizedTest
    @ValueSource(strings = {
            "The change does not affect default (text) output; only the newly specified json format is altered.",
            "No changes to production code or user-facing features; the improvements apply only to test coverage.",
            "No code functionality or user experience is affected; updates and alerts only help with project maintenance.",
            "Only the integration test infrastructure is changed; no production configuration or storage profile is affected.",
            "Only the compression process has changed; other backup logic remains as before.",
            "No specific changes to dbvault code were made; only the parent dependency version was updated.",
            "This has no impact on existing backups.",
            "It only affects the CI environment.",
            "The next-run info and the misfire rule are unchanged.",
            "No other user-facing features are mentioned as changed.",
            "Thay đổi này không ảnh hưởng đến API HTTP."
    })
    void dropsASentenceThatOnlyStatesTheScope(String scope) {
        assertThat(Limitations.stated(scope)).isEmpty();
        assertThat(Limitations.stated("Only MongoDB validates certificates yet. " + scope))
                .isEqualTo("Only MongoDB validates certificates yet.");
        assertThat(Limitations.stated("- " + scope + "\n- Only MongoDB validates certificates yet."))
                .isEqualTo("- Only MongoDB validates certificates yet.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Certificate validation is enforced only for MongoDB.",
            "Localization applies only to the web console; HTTP API, CLI, logs, and notifications remain English-only.",
            "Restore only applies the backup's dump; tables not present in the backup are left unchanged.",
            "Restores still do not reset the schema; tables not present in the backup remain unchanged.",
            "Storage profile secrets are not included in this change.",
            "Error messages are not output as JSON and remain on stderr."
    })
    void keepsALimitationThatMentionsWhereItApplies(String limitation) {
        assertThat(Limitations.stated(limitation)).isEqualTo(limitation);
    }

    @Test
    void nothingStaysNothing() {
        assertThat(Limitations.stated("")).isEmpty();
    }
}
