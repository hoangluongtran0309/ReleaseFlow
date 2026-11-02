# Release v1.1.0

The whole console in Vietnamese, a clear CLI error for a mistyped server address, and container images pinned by digest.

## What's New

This release includes 3 changes: 1 new feature and 2 bug fixes.

## ✨ New Features

- **The console interface now fully supports both English and Vietnamese across all pages and messages, with language selection per browser session.** ([#53](https://github.com/hoangtulangtoc/dbvault/pull/53)) — This update does not affect schema, data, credentials, backup/restore logic, or database engines. Console language changes per session but does not impact stored data or database operation. No post-upgrade checks or rollbacks are required for this feature.
  - Tracked in [DBV-31](https://hoangluongtran0309.atlassian.net/browse/DBV-31): Vietnamese console (Done)

## 🐛 Bug Fixes

- **The CLI now rejects server addresses that are not valid http(s) URLs, showing a clear message and exit code 2 instead of an internal error.** ([!9](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/9)) — When upgrading, verify your DBVAULT_URL and configuration files specify the full http:// or https:// addresses. Failure to do so will cause CLI commands to fail with exit code 2 and an explicit configuration error, instead of a less clear failure. No data, schema, or database credentials are affected; roll back by reverting to prior config or version if needed.
  - Migration: Ensure all configuration and environment variables use a complete server address, including http(s) scheme and host, to avoid startup errors.
  - Tracked in [DBV-44](https://hoangluongtran0309.atlassian.net/browse/DBV-44): CLI: a server address without http:// fails with an internal error (In Progress)

- **All container base images in Dockerfiles and docker-compose.yml are now pinned by digest, and Dependabot configuration was updated to watch for changes to these digests. Related deployment documentation was updated.** ([#48](https://github.com/hoangtulangtoc/dbvault/pull/48)) — The change does not affect stored data, database schema, credentials, or restore processes. Container environments are built reproducibly using pinned base images, with no required checks or actions after upgrade.
