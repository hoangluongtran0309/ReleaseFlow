# Release v1.1.0

The whole console in Vietnamese, a clear CLI error for a mistyped server address, and container images pinned by digest.

## What's New

This release includes 3 changes: 1 new feature and 2 bug fixes.

## ✨ New Features

- **The console interface now fully supports both English and Vietnamese across all pages and messages, with language selection per browser session.** ([#53](https://github.com/hoangtulangtoc/dbvault/pull/53)) — UI text is now sourced from language bundles using MessageFormat where parameters are needed; flash messages and form validations are localized. Test coverage ensures key consistency and proper pattern formatting, while sentences were moved to data attributes as per ADR-042. English-only areas (API, CLI, logs) remain by design.
  - Why: To meet customer requests for Vietnamese language support and provide a localized user experience as described in DBV-31.
  - Implementation: All console user-facing text is now managed using language bundles and MessageFormat for argument patterns. Flash messages, form checks, and live job status are localized based on the browser language. Automated tests verify translations, message formatting, and key consistency between bundles, while English is retained for HTTP API, CLI, logs, and notifications as per ADR-042.

## 🐛 Bug Fixes

- **The CLI now rejects server addresses that are not valid http(s) URLs, showing a clear message and exit code 2 instead of an internal error.** ([!9](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/9)) — Server address parsing enforces the presence of http(s) scheme and host in DBVAULT_URL or config (server=). Improper formats result in exit code 2 with an explicit message identifying the problematic setting. See ServerCommandsTest for new test coverage and reference updated README on exit codes.
  - Why: Previously, a malformed server address (like one missing the scheme) caused an unclear internal error and exit code 1, confusing users.
  - Implementation: Parsing now requires server addresses (from DBVAULT_URL or server= in CLI config) to start with http:// or https:// and include a host. Invalid formats trigger exit code 2 and a specific error message; the README documents this behavior. ServerCommandsTest covers these cases with new tests.
  - Migration: Ensure all configuration and environment variables use a complete server address, including http(s) scheme and host, to avoid startup errors.

- **All container base images in Dockerfiles and docker-compose.yml are now pinned by digest, and Dependabot configuration was updated to watch for changes to these digests. Related deployment documentation was updated.** ([#48](https://github.com/hoangtulangtoc/dbvault/pull/48)) — Base images in all Dockerfiles and docker-compose.yml are pinned by digest, ensuring reproducible builds. Dependabot now tracks digest updates for both Docker and Docker Compose, triggering PRs for updates instead of silent changes. Deployment documentation was clarified accordingly.
  - Why: To ensure rebuilds always use the exact same base images unless intentionally updated, improving reproducibility and security.
  - Implementation: Dockerfiles specify the platform index digest for each base, including maven, docker CLI, Eclipse Temurin, and Postgres; Dependabot is configured to create PRs when a new digest is available.
