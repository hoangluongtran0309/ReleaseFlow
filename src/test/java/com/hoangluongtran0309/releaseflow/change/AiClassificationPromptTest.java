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
        assertThat(AiClassificationPrompt.system(request(SourceType.GITHUB, "")))
                .contains("Every field, narratives included, states only what the evidence supports")
                .contains("For a tracker_issue, never name files, code, or configuration it changed")
                .contains("describes that dependency")
                .contains("Never write about what the evidence lacks")
                .contains("in one sentence")
                .contains("only what the change's own text says it deliberately does not do yet")
                .doesNotContain("{dependency_rule}");
        assertThat(AiClassificationPrompt.TEMPERATURE).isZero();
    }

    // Read by every change, the dependency rule stopped dbvault-cli !5, which dropped the
    // unversioned API paths, from being marked breaking.
    @Test
    void carriesTheDependencyRuleOnlyForADependencyUpdate() {
        String ordinary = AiClassificationPrompt.system(request(SourceType.GITLAB, "", false));
        String dependencyUpdate = AiClassificationPrompt.system(request(SourceType.GITHUB, "", true));

        assertThat(ordinary)
                .doesNotContain(AiClassificationPrompt.DEPENDENCY_RULE)
                .doesNotContain("dependency update: it is breaking only when");
        assertThat(dependencyUpdate).contains(AiClassificationPrompt.DEPENDENCY_RULE);
        assertThat(userMessage(request(SourceType.GITLAB, "", false)).path("change").path("dependency_update").booleanValue())
                .isFalse();
        assertThat(userMessage(request(SourceType.GITHUB, "", true)).path("change").path("dependency_update").booleanValue())
                .isTrue();
    }

    @Test
    void putsTheRuleForAnUnsureBreakingChangeFirst() {
        for (boolean dependencyUpdate : new boolean[] {false, true}) {
            String system = AiClassificationPrompt.system(request(SourceType.GITHUB, "", dependencyUpdate));
            String rules = system.substring(system.indexOf("Rules:\n"));

            assertThat(rules).startsWith("Rules:\n- If you are unsure whether the change is breaking,"
                    + " set breaking_change and needs_human_review to true.\n");
        }
    }

    @Test
    void asksEachAudienceOfTheRequestForARelevanceAHeadlineAndABody() {
        AiClassificationRequest request = request(SourceType.GITHUB, "");
        JsonNode narratives = AiClassificationPrompt.responseSchema(request, OBJECT_MAPPER)
                .path("properties").path("narratives");

        assertThat(narratives.path("required")).isEqualTo(OBJECT_MAPPER.valueToTree(request.audienceCodes()));
        for (String code : request.audienceCodes()) {
            assertThat(narratives.path("properties").path(code).path("required").toString())
                    .isEqualTo("[\"relevance\",\"headline\",\"body\"]");
        }
        assertThat(AiClassificationPrompt.system(request))
                .contains("relevance (none when this reader would neither notice nor need the change")
                .contains("one line of at most 20 words, without links")
                .contains("return relevance none with an empty headline and body");
    }

    private static AiClassificationRequest request(SourceType type, String context) {
        return request(type, context, false);
    }

    private static AiClassificationRequest request(SourceType type, String context, boolean dependencyUpdate) {
        AiClassificationRequest base = OpenAiCompatibleClassifierTest.request("Done.", null, "en");
        return new AiClassificationRequest(base.changeId(), base.title(), base.description(), base.labels(),
                base.targetBranch(), base.linkedIssues(), base.outputLanguage(), base.lockedCategory(), base.categories(),
                base.audiences(), base.contextThreshold(), type != null && type.isIssueTracker(), context, dependencyUpdate);
    }

    private static JsonNode userMessage(AiClassificationRequest request) {
        return OBJECT_MAPPER.readTree(AiClassificationPrompt.userMessage(request, OBJECT_MAPPER));
    }
}
