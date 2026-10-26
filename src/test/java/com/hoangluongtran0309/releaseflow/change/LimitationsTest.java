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

    @Test
    void nothingStaysNothing() {
        assertThat(Limitations.stated("")).isEmpty();
    }
}
