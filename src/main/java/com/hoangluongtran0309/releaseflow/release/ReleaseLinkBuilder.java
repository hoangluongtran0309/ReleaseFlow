package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.audience.ReleaseLinks;
import com.hoangluongtran0309.releaseflow.project.IntegrationSourceService;
import com.hoangluongtran0309.releaseflow.project.IntegrationSourceView;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The release-wide values a note's footer sees (ADR-0037). The compare and release links
 * come from the Project's one code host, read from the coordinates it was stored with: a
 * Project with no GitHub or GitLab source, or with more than one, gets neither, and
 * Linear and Jira sources never count. Each version is the tag name exactly as written;
 * nothing adds or removes a {@code v}. Building them reads only the database: no provider
 * is called, so a link may name a tag that does not exist yet.
 */
@Component
class ReleaseLinkBuilder {

    static final int MAX_UPGRADE_GUIDE_URL = 2000;
    private static final String GITHUB = "https://github.com/";

    private final IntegrationSourceService sourceService;

    ReleaseLinkBuilder(IntegrationSourceService sourceService) {
        this.sourceService = sourceService;
    }

    ReleaseLinks links(UUID organizationId, UUID projectId, String version, String previousVersion, String upgradeGuideUrl) {
        return links(sourceService.list(organizationId, projectId), version, previousVersion, upgradeGuideUrl);
    }

    /** The links for a Project with these sources; pure, so it can be checked without one. */
    static ReleaseLinks links(
            List<IntegrationSourceView> sources,
            String version,
            String previousVersion,
            String upgradeGuideUrl
    ) {
        List<IntegrationSourceView> codeHosts = sources.stream()
                .filter(source -> source.type() == SourceType.GITHUB || source.type() == SourceType.GITLAB)
                .toList();
        String repository = codeHosts.size() == 1 ? repositoryUrl(codeHosts.getFirst()) : null;
        String previous = previousVersion == null ? "" : previousVersion;
        String compareUrl = "";
        String releaseUrl = "";
        if (repository != null) {
            boolean gitLab = codeHosts.getFirst().type() == SourceType.GITLAB;
            String separator = gitLab ? "/-/" : "/";
            if (!previous.isEmpty()) {
                compareUrl = repository + separator + "compare/" + segment(previous) + "..." + segment(version);
            }
            releaseUrl = repository + separator + (gitLab ? "releases/" : "releases/tag/") + segment(version);
        }
        return new ReleaseLinks(version, previous, compareUrl, upgradeGuideUrl, releaseUrl);
    }

    /**
     * The upgrade guide as stored: an absolute HTTP or HTTPS address of at most
     * {@value #MAX_UPGRADE_GUIDE_URL} characters, with a host and no user information. A
     * query and a fragment are allowed. Null stays null.
     */
    static String validatedUpgradeGuideUrl(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() > MAX_UPGRADE_GUIDE_URL) {
            throw new InvalidUpgradeGuideUrlException("error.invalid_upgrade_guide_url.tooLong", MAX_UPGRADE_GUIDE_URL);
        }
        final URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw new InvalidUpgradeGuideUrlException("error.invalid_upgrade_guide_url.shape");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!("https".equals(scheme) || "http".equals(scheme))
                || uri.getHost() == null
                || uri.getRawUserInfo() != null) {
            throw new InvalidUpgradeGuideUrlException("error.invalid_upgrade_guide_url.shape");
        }
        return value;
    }

    // The web address of the code host's project, or null when its coordinates are missing.
    private static String repositoryUrl(IntegrationSourceView source) {
        if (source.type() == SourceType.GITHUB) {
            if (source.owner() == null || source.repository() == null) {
                return null;
            }
            return GITHUB + segment(source.owner()) + "/" + segment(source.repository());
        }
        if (source.apiBaseUrl() == null || source.externalProjectKey() == null) {
            return null;
        }
        // The slashes of a GitLab group path stay separators.
        String path = Arrays.stream(source.externalProjectKey().split("/"))
                .map(ReleaseLinkBuilder::segment)
                .collect(Collectors.joining("/"));
        return source.apiBaseUrl() + "/" + path;
    }

    private static String segment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}
