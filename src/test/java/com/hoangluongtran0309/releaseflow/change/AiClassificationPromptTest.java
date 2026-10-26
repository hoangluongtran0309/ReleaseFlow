package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AiClassificationPromptTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(SourceType.class)
    void namesWhetherTheChangeIsMergedCodeOrATrackerIssue(SourceType type) {
        JsonNode user = userMessage(request(type, ""));

        assertThat(user.path("change").path("kind").stringValue())
                .isEqualTo(type == SourceType.LINEAR || type == SourceType.JIRA ? "tracker_issue" : "code_change");
        assertThat(user.has("pull_request")).isFalse();
    }

    @Test
    void sendsTheProjectsContextOnlyWhenThereIsOne() {
        assertThat(userMessage(request(SourceType.GITHUB, "")).path("project_context").isNull()).isTrue();
        assertThat(userMessage(request(null, "The CLI is a client.")).path("project_context").stringValue())
                .isEqualTo("The CLI is a client.");
        assertThat(userMessage(request(null, "")).path("change").path("kind").stringValue()).isEqualTo("code_change");
    }

    @Test
    void forbidsWhatTheEvidenceDoesNotSupportInEveryField() {
        assertThat(AiClassificationPrompt.SYSTEM)
                .contains("Every field, narratives included, states only what the evidence supports")
                .contains("For a tracker_issue, never name files, code, or configuration it changed")
                .contains("describes that dependency")
                .contains("Never write about what the evidence lacks")
                .contains("in one sentence");
    }

    private static AiClassificationRequest request(SourceType type, String context) {
        AiClassificationRequest base = OpenAiCompatibleClassifierTest.request("Done.", null, "en");
        return new AiClassificationRequest(base.changeId(), base.title(), base.description(), base.labels(),
                base.targetBranch(), base.linkedIssues(), base.outputLanguage(), base.lockedCategory(), base.categories(),
                base.audiences(), base.contextThreshold(), type != null && type.isIssueTracker(), context);
    }

    private static JsonNode userMessage(AiClassificationRequest request) {
        return OBJECT_MAPPER.readTree(AiClassificationPrompt.userMessage(request, OBJECT_MAPPER));
    }
}
