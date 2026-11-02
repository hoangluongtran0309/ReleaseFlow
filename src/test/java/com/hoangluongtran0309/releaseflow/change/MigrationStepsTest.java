package com.hoangluongtran0309.releaseflow.change;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationStepsTest {

    // Steps the AI wrote in the dbvault replays, and their kind.
    @ParameterizedTest
    @ValueSource(strings = {
            "No immediate action is required; existing passwords are migrated to per-target keys when edited.",
            "Existing passwords are re-encrypted with the new per-target key only when they are edited; no immediate action required.",
            "No manual steps are required for operators or users; existing targets are automatically marked as MySQL during migration.",
            "No migration required.",
            "Nothing to do.",
            "There is no action needed.",
            "This change does not require any migration.",
            "- No changes are needed.",
            "N/A",
            "Không cần thao tác gì; mật khẩu hiện có được chuyển sang khóa mới khi được sửa."
    })
    void dropsAStepThatOnlySaysNothingNeedsDoing(String step) {
        assertThat(MigrationSteps.stated(step)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Rename EXPORT_DIR to EXPORT_PATH.",
            "No action required unless the operator wants to enable restore verification; enabling it requires changes to Docker socket and configuration settings.",
            "To use the new monitoring stack, operators should write the required password files. For plain use, no action is needed.",
            "No immediate action is required. Operators who want to use restore verification must enable it and mount the Docker socket.",
            "Rename EXPORT_DIR to EXPORT_PATH. No further action is required.",
            "Rename EXPORT_DIR to EXPORT_PATH. No migration is required for the database.",
            "No action is required for most installations.",
            "No action is required until version 0.3.0, when the old paths are removed.",
            "Không cần thao tác gì nếu không dùng MongoDB."
    })
    void keepsAStepThatStillAsksSomething(String step) {
        assertThat(MigrationSteps.stated(step)).isEqualTo(step);
    }

    @Test
    void nothingStaysNothing() {
        assertThat(MigrationSteps.stated("")).isEmpty();
    }
}
