package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.audience.ReleaseLinks;
import com.hoangluongtran0309.releaseflow.project.IntegrationSourceView;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseLinkBuilderTest {

    private static final IntegrationSourceView GITHUB = source(SourceType.GITHUB, "acme/app", "acme", "app", null);
    private static final IntegrationSourceView GITLAB =
            source(SourceType.GITLAB, "platform/tools/app", null, null, "https://gitlab.example.com");
    private static final IntegrationSourceView LINEAR = source(SourceType.LINEAR, "team-1", null, null, null);
    private static final IntegrationSourceView JIRA = source(SourceType.JIRA, "APP", null, null, "https://acme.atlassian.net");

    @Test
    void aGitHubSourceLinksTheComparisonAndTheReleaseByTag() {
        ReleaseLinks links = ReleaseLinkBuilder.links(List.of(GITHUB, LINEAR, JIRA), "v1.0.0", "v0.3.0", "https://docs.example.com/up");

        assertThat(links).isEqualTo(new ReleaseLinks(
                "v1.0.0",
                "v0.3.0",
                "https://github.com/acme/app/compare/v0.3.0...v1.0.0",
                "https://docs.example.com/up",
                "https://github.com/acme/app/releases/tag/v1.0.0"
        ));
    }

    @Test
    void aGitLabSourceKeepsTheSlashesOfItsProjectPath() {
        ReleaseLinks links = ReleaseLinkBuilder.links(List.of(JIRA, GITLAB), "1.1.0", "1.0.0", null);

        assertThat(links.compareUrl()).isEqualTo("https://gitlab.example.com/platform/tools/app/-/compare/1.0.0...1.1.0");
        assertThat(links.releaseUrl()).isEqualTo("https://gitlab.example.com/platform/tools/app/-/releases/1.1.0");
        assertThat(links.upgradeGuideUrl()).isEmpty();
    }

    @Test
    void theVersionIsTheTagAsWrittenAndEachSegmentIsEncoded() {
        ReleaseLinks links = ReleaseLinkBuilder.links(List.of(GITHUB), "release/2.0 beta", "1.0", null);

        assertThat(links.compareUrl()).isEqualTo("https://github.com/acme/app/compare/1.0...release%2F2.0%20beta");
        assertThat(links.releaseUrl()).isEqualTo("https://github.com/acme/app/releases/tag/release%2F2.0%20beta");
        assertThat(links.version()).isEqualTo("release/2.0 beta");
    }

    @Test
    void theFirstReleaseHasAReleaseLinkButNoComparison() {
        ReleaseLinks links = ReleaseLinkBuilder.links(List.of(GITHUB), "v0.1.0", null, null);

        assertThat(links.previousVersion()).isEmpty();
        assertThat(links.compareUrl()).isEmpty();
        assertThat(links.releaseUrl()).isEqualTo("https://github.com/acme/app/releases/tag/v0.1.0");
    }

    @Test
    void noCodeHostOrSeveralGiveNoProviderLinks() {
        for (List<IntegrationSourceView> sources : List.of(
                List.<IntegrationSourceView>of(),
                List.of(LINEAR, JIRA),
                List.of(GITHUB, GITLAB),
                List.of(GITHUB, source(SourceType.GITHUB, "acme/cli", "acme", "cli", null))
        )) {
            ReleaseLinks links = ReleaseLinkBuilder.links(sources, "2.0.0", "1.0.0", "https://docs.example.com/up");

            assertThat(links.compareUrl()).isEmpty();
            assertThat(links.releaseUrl()).isEmpty();
            assertThat(links.previousVersion()).isEqualTo("1.0.0");
            assertThat(links.upgradeGuideUrl()).isEqualTo("https://docs.example.com/up");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://docs.example.com/upgrade",
            "http://docs.example.com:8080/upgrade?from=1.0#steps",
            "HTTPS://Docs.Example.com/a_(b)"
    })
    void acceptsAnAbsoluteHttpAddress(String url) {
        assertThat(ReleaseLinkBuilder.validatedUpgradeGuideUrl(url)).isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "docs.example.com/upgrade",
            "/upgrade",
            "ftp://docs.example.com/upgrade",
            "javascript:alert(1)",
            "https://user:secret@docs.example.com/upgrade",
            "https://docs.example.com/up grade",
            "https:///upgrade"
    })
    void refusesAnythingElse(String url) {
        assertThatThrownBy(() -> ReleaseLinkBuilder.validatedUpgradeGuideUrl(url))
                .isInstanceOf(InvalidUpgradeGuideUrlException.class)
                .hasMessage("error.invalid_upgrade_guide_url.shape");
    }

    @Test
    void refusesAnAddressOverTwoThousandCharacters() {
        String url = "https://docs.example.com/" + "a".repeat(ReleaseLinkBuilder.MAX_UPGRADE_GUIDE_URL);

        assertThatThrownBy(() -> ReleaseLinkBuilder.validatedUpgradeGuideUrl(url))
                .isInstanceOf(InvalidUpgradeGuideUrlException.class)
                .hasMessage("error.invalid_upgrade_guide_url.tooLong");
        assertThat(ReleaseLinkBuilder.validatedUpgradeGuideUrl(null)).isNull();
    }

    private static IntegrationSourceView source(SourceType type, String key, String owner, String repository, String baseUrl) {
        return new IntegrationSourceView(UUID.randomUUID(), type, "webhook", key, owner, repository, baseUrl, null, null,
                null, null, null, Instant.EPOCH, null, false, null, null, null, null, null);
    }
}
