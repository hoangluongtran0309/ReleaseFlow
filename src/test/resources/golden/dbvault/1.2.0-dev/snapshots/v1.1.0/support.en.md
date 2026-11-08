# Release v1.1.0

The whole console in Vietnamese, a clear CLI error for a mistyped server address, and container images pinned by digest.

## What's New

This release includes 3 changes: 1 new feature and 2 bug fixes.

## ✨ New Features

- **The console interface now fully supports both English and Vietnamese across all pages and messages, with language selection per browser session.** ([#53](https://github.com/hoangtulangtoc/dbvault/pull/53))

## 🐛 Bug Fixes

- **The CLI now rejects server addresses that are not valid http(s) URLs, showing a clear message and exit code 2 instead of an internal error.** ([!9](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/9))
  - Migration: Ensure all configuration and environment variables use a complete server address, including http(s) scheme and host, to avoid startup errors.

- **All container base images in Dockerfiles and docker-compose.yml are now pinned by digest, and Dependabot configuration was updated to watch for changes to these digests. Related deployment documentation was updated.** ([#48](https://github.com/hoangtulangtoc/dbvault/pull/48))
