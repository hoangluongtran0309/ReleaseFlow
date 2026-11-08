# Release v1.1.0

The whole console in Vietnamese, a clear CLI error for a mistyped server address, and container images pinned by digest.

## What's New

This release includes 3 changes: 1 new feature and 2 bug fixes.

## ✨ New Features

- **The console interface now fully supports both English and Vietnamese across all pages and messages, with language selection per browser session.** ([#53](https://github.com/hoangtulangtoc/dbvault/pull/53)) — No operational risk introduced: the change only affects interface language, not storage or backend logic. Rollback is the same as any UI update; watch for any missed translations or user feedback on language display.
  - Why: To meet customer requests for Vietnamese language support and provide a localized user experience as described in DBV-31.
  - Detail: All console user-facing text is now managed using language bundles and MessageFormat for argument patterns. Flash messages, form checks, and live job status are localized based on the browser language. Automated tests verify translations, message formatting, and key consistency between bundles, while English is retained for HTTP API, CLI, logs, and notifications as per ADR-042.

## 🐛 Bug Fixes

- **The CLI now rejects server addresses that are not valid http(s) URLs, showing a clear message and exit code 2 instead of an internal error.** ([!9](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/9)) — Monitor for CLI failures with exit code 2 and messages about invalid server addresses after deploy. Invalid DBVAULT_URL or server lines now block startup with a visible error; correct configs as needed to restore service. No schema or data impact, low rollback risk.
  - Why: Previously, a malformed server address (like one missing the scheme) caused an unclear internal error and exit code 1, confusing users.
  - Detail: Parsing now requires server addresses (from DBVAULT_URL or server= in CLI config) to start with http:// or https:// and include a host. Invalid formats trigger exit code 2 and a specific error message; the README documents this behavior. ServerCommandsTest covers these cases with new tests.
  - Migration: Ensure all configuration and environment variables use a complete server address, including http(s) scheme and host, to avoid startup errors.

- **All container base images in Dockerfiles and docker-compose.yml are now pinned by digest, and Dependabot configuration was updated to watch for changes to these digests. Related deployment documentation was updated.** ([#48](https://github.com/hoangtulangtoc/dbvault/pull/48)) — Operationally, base images are now locked to specific digests, preventing unexpected changes during rebuilds and making deployments more predictable. Rollback procedures remain unchanged; monitor Dependabot PRs for image updates.
  - Why: To ensure rebuilds always use the exact same base images unless intentionally updated, improving reproducibility and security.
  - Detail: Dockerfiles specify the platform index digest for each base, including maven, docker CLI, Eclipse Temurin, and Postgres; Dependabot is configured to create PRs when a new digest is available.
