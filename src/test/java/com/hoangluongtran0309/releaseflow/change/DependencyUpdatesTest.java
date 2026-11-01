package com.hoangluongtran0309.releaseflow.change;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyUpdatesTest {

    // The shape Dependabot writes, with the Gradle step the AI once offered a Maven project.
    private static final String DEPENDABOT = """
            Bumps [org.springframework.boot:spring-boot-starter-parent](https://github.com/spring-projects/spring-boot) from 4.1.0 to 4.1.1.
            <details>
            <summary>Release notes</summary>
            <p><em>Sourced from <a href="https://github.com/spring-projects/spring-boot/releases">spring-boot-starter-parent's releases</a>.</em></p>
            <blockquote>
            <h2>v4.1.1</h2>
            <h2>:warning: Noteworthy Changes</h2>
            <ul>
            <li>The Gradle plugin now requires opt-in for gRPC configuration.</li>
            </ul>
            </blockquote>
            </details>
            <details>
            <summary>Commits</summary>
            <ul><li>See full diff in <a href="https://github.com/spring-projects/spring-boot/compare">compare view</a></li></ul>
            </details>
            <br />

            Dependabot will resolve any conflicts with this PR as long as you don't alter it yourself.

            ---

            <details>
            <summary>Dependabot commands and options</summary>
            <br />
            You can trigger Dependabot actions by commenting on this PR.
            </details>
            """;

    private static final String RENOVATE = """
            This PR contains the following updates:

            | Package | Change |
            |---|---|
            | [eclipse-temurin](https://github.com/adoptium) | `21.0.8` -> `21.0.9` |

            ---

            ### Release Notes

            #### adoptium/temurin (eclipse-temurin)

            > Rebuild your images with the new `--release` flag before upgrading.

            ### Changelog

            - Breaking: drops Alpine 3.18.

            ### Configuration

            📅 **Schedule**: Branch creation - At any time.
            """;

    @Test
    void keepsWhatDependabotSaysItselfAndDropsWhatItQuotes() {
        String own = DependencyUpdates.ownText(DEPENDABOT.replace("\n", "\r\n"));

        assertThat(own)
                .startsWith("Bumps [org.springframework.boot:spring-boot-starter-parent]")
                .contains("from 4.1.0 to 4.1.1.")
                .contains("Dependabot will resolve any conflicts")
                .doesNotContain("Gradle", "gRPC", "Release notes", "Commits", "compare view", "Dependabot commands");
    }

    @Test
    void keepsWhatRenovateSaysItselfAndDropsItsQuotedSections() {
        String own = DependencyUpdates.ownText(RENOVATE);

        assertThat(own)
                .contains("`21.0.8` -> `21.0.9`")
                .contains("### Configuration")
                .doesNotContain("Release Notes", "adoptium/temurin (eclipse-temurin)", "--release", "Alpine", "Changelog");
    }

    @Test
    void anElementLeftOpenRunsToTheEndAndNothingLeftIsNothing() {
        assertThat(DependencyUpdates.ownText("Bumps x from 1 to 2.\n<details><summary>Release notes</summary>\nDo y."))
                .isEqualTo("Bumps x from 1 to 2.");
        assertThat(DependencyUpdates.ownText("<details>only quoted</details>")).isNull();
        assertThat(DependencyUpdates.ownText(null)).isNull();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "dependabot[bot], dependabot/maven/org.springframework.boot-4.1.1, true",
            "Renovate[bot],   null,                                            true",
            "renovate-bot,    renovate/eclipse-temurin-21.x,                   true",
            "maintainer,      dependabot/docker/eclipse-temurin-23cc573,       true",
            "maintainer,      feature/dependabot-config,                       false",
            "dependabot-fan,  fix/readable-sizes,                              false",
            "null,            null,                                            false"
    })
    void aBotIsKnownByItsAuthorOrItsBranch(String author, String branch, boolean expected) {
        assertThat(DependencyUpdates.isDependencyUpdate(change(author, branch, "Details."))).isEqualTo(expected);
    }

    @Test
    void onlyADependencyUpdateHasItsDescriptionTrimmedForTheAi() {
        AiClassificationRequest base = OpenAiCompatibleClassifierTest.request("Done.", null, "en");
        AiClassificationRequest bump = request(change("dependabot[bot]", "dependabot/maven/x", DEPENDABOT), base);
        AiClassificationRequest person = request(change("maintainer", "fix/x", DEPENDABOT), base);

        assertThat(bump.dependencyUpdate()).isTrue();
        assertThat(bump.description()).doesNotContain("Gradle").contains("from 4.1.0 to 4.1.1.");
        assertThat(person.dependencyUpdate()).isFalse();
        assertThat(person.description()).isEqualTo(DEPENDABOT);
    }

    private static AiClassificationRequest request(MergedPullRequest change, AiClassificationRequest base) {
        return AiClassificationRequest.of(base.changeId(), change, List.of(), base.outputLanguage(), base.categories().getFirst(),
                base.categories(), base.audiences(), base.contextThreshold(), null, "");
    }

    private static MergedPullRequest change(String author, String branch, String description) {
        return new MergedPullRequest("38", 38, "chore(deps): bump spring-boot-starter-parent from 4.1.0 to 4.1.1",
                description, author, List.of("dependencies"), "develop", "a".repeat(40),
                Instant.parse("2026-10-16T03:35:19Z"), "https://github.com/acme/dbvault/pull/38", branch);
    }
}
