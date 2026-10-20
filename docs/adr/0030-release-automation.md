# ADR-0030: Release automation

- Status: Accepted
- Date: 2026-10-10
- Completes: `S24`, whose other half — the open-core/enterprise module split — was
  abandoned in [ADR-0029](0029-one-module-and-the-apache-licence.md).
- Amends: [CONTRIBUTING.md](../../CONTRIBUTING.md), which said tags and release artifacts
  would be added "only when a real publication process exists". One now does.

## Context

A release has to be built somewhere everyone can see and nobody can quietly redo, which
rules out somebody's laptop. It also has to carry one version in every place that shows
one — the tag, the JAR, the image, the release notes — and the usual way of getting that
is to write the version into the POM before the release and a `-SNAPSHOT` after it. That
is two commits per release whose only content is a number, a back-merge that exists only
to carry the second one, and a release that fails if either is forgotten. The number is
already known exactly once: when somebody decides to tag.

## Decision

- **A tag is the release, the only input, and the version.** Pushing
  `v<major>.<minor>.<patch>` runs the workflow against exactly that commit. There is no
  version input to get wrong and no branch to be on by accident.
- **The POM never holds a version.** It says `<version>${revision}</version>`, a Maven
  CI-friendly version, with `revision` defaulting to `0.0.0-SNAPSHOT` for every local and
  CI build. The Release workflow builds with `-Drevision=<the tag without its v>`, and the
  Dockerfile takes the same value as the `RELEASEFLOW_VERSION` build argument. Spring
  Boot's parent already writes `Implementation-Version` into the JAR's manifest, and the
  image carries `org.opencontainers.image.version`, so both say what they are.
- **No `flatten-maven-plugin`.** It exists to resolve `${revision}` in a POM that is
  installed or deployed for others to depend on. This POM is neither: the JAR is an
  application, never a library in a Maven repository.
- **Guards refuse a tag that does not describe a reviewed release**, before anything is
  built:
  - the tag must be `vX.Y.Z`, or `vX.Y.Z-rc.N` for a release candidate;
  - the tagged commit must be an ancestor of `main`, so nothing unmerged is published —
    except a release candidate, which must be on `release/X.Y.Z` or `hotfix/X.Y.Z`
    instead, because testing it is the point of not having merged yet;
  - `CHANGELOG.md` at the tag must have a `## [X.Y.Z] - YYYY-MM-DD` entry, which also
    becomes the GitHub release's notes. Without one there is nothing to tell anybody, and
    nobody reviewed it.
  A tag pushed by mistake therefore fails rather than ships, and deleting it is enough.
  After the build, the JAR's manifest must name the tag's version, so a build that
  ignored `-Drevision` fails too.
- **`scripts/release.sh` cuts a release in two commands.** `start <version>` branches
  `release/<version>` from `develop`, dates the `[Unreleased]` entry of `CHANGELOG.md`,
  commits, pushes, and opens the pull request into `main`; `finish <version>`, once that
  is merged, tags `main` and pushes the tag, then opens the back-merge into `develop`.
  `rc <version>` tags release candidates, and `start --hotfix` branches from `main`.
  Every command checks before it changes anything and has a `--dry-run`. The CHANGELOG is
  read and written only by `scripts/changelog.sh`, which the workflow uses too, so the two
  agree on what an entry is. CI runs a whole release of both scripts against a local
  remote.
- **The suite runs again on the tagged commit.** It already ran on the pull request that
  merged it. A release is the one build nobody gets to redo, so it is worth the minutes.
- **One SBOM, of the image.** CycloneDX, produced by the Trivy that already scans the
  image with a checksum-verified binary. The image is what the JAR ships inside, so an
  SBOM of it lists the Java dependencies and the base image's own packages together — a
  JAR-only SBOM would describe less than what is actually deployed. This also avoids
  adding a Maven plugin that would run on every build for the sake of one build a year.
- **The image is tagged `X.Y.Z`, `X.Y`, `X` and `latest`, and the release names its
  digest.** A deployment can pin as tightly as it wants to; the release notes show the
  digest, because that is the only tag that cannot move. A release candidate gets only
  `X.Y.Z-rc.N` and is marked a pre-release, so nothing that follows a moving tag moves to
  it.
- **Provenance is attested for both the image and the JAR**, so somebody who downloaded
  either can ask GitHub which workflow, at which commit, produced it.
- **The checksum file names its inputs rather than globbing them.** Whether a glob sees the
  file the shell is about to write depends on expansion order; naming them removes the
  question.

## Alternatives

- **release-please or semantic-release**, which compute the version from Conventional
  Commits and open the release pull request themselves. They fit trunk-based development;
  here `main` only receives release merges from `develop`, a tag created by the
  workflow's own token does not start another workflow without a personal access token,
  and the changelog would be generated from commit subjects rather than written for
  readers. The version is a decision worth one typed number.
- **Deriving the version from `git describe` at build time** (`jgitver` and similar).
  It needs the full history and tags in every build, including the Docker build context,
  and makes a local build's version depend on what was fetched.

## Consequences

- No commit ever changes a version. The release pull request changes `CHANGELOG.md` and
  nothing else, and the back-merge carries only that.
- Publishing needs no credential of anybody's: `GITHUB_TOKEN` pushes to GHCR under the
  repository's own namespace, and the attestation is signed with the workflow's identity.
  `scripts/release.sh` needs only the maintainer's own `git` and `gh`.
- A local build calls itself `0.0.0-SNAPSHOT`. That is deliberate: only a release has a
  version, and anything else is visibly not one.
- The changelog entry is dated when the release is prepared, not when it is tagged. A
  release that waits should have its date corrected in the pull request.
- The workflow builds twice — once for `verify`, once inside `docker build`. That is slower
  than sharing the JAR between them, and it is what keeps the image reproducible from the
  Dockerfile alone rather than from whatever happened to be in `target/`.
- `latest` moves on every release. A deployment that follows it is asking to be upgraded;
  the README says to pin the digest instead.
- A release can only be tagged once `main` carries the release commit, so the order is
  fixed: merge, then tag. That is the point.
