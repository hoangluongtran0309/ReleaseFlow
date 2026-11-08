# Release v1.1.0

The whole console in Vietnamese, a clear CLI error for a mistyped server address, and container images pinned by digest.

## What's New

This release includes 3 changes: 1 new feature and 2 bug fixes.

## ✨ New Features

- **The console interface now fully supports both English and Vietnamese across all pages and messages, with language selection per browser session.** ([#53](https://github.com/hoangtulangtoc/dbvault/pull/53)) — Customers will see all console pages and messages in either English or Vietnamese, based on their browser settings. No action is required apart from choosing the preferred language in their browser.

## 🐛 Bug Fixes

- **The CLI now rejects server addresses that are not valid http(s) URLs, showing a clear message and exit code 2 instead of an internal error.** ([!9](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/9)) — If customers set DBVAULT_URL or a server address without 'http://' or 'https://', they'll now see a clear error message and exit code 2 telling them which setting is wrong and how to fix it, rather than a confusing internal error. Advise them to include the full URL scheme.
  - Migration: Ensure all configuration and environment variables use a complete server address, including http(s) scheme and host, to avoid startup errors.

- **All container base images in Dockerfiles and docker-compose.yml are now pinned by digest, and Dependabot configuration was updated to watch for changes to these digests. Related deployment documentation was updated.** ([#48](https://github.com/hoangtulangtoc/dbvault/pull/48)) — Customers will not notice any change in functionality or workflow. Builds now always use the exact same base images for improved consistency and security, with no action required on their part.
