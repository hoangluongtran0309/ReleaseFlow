# Release v1.1.0

The whole console in Vietnamese, a clear CLI error for a mistyped server address, and container images pinned by digest.

## What's New

This release includes 3 changes: 1 new feature and 2 bug fixes.

## ✨ New Features

- **The console interface now fully supports both English and Vietnamese across all pages and messages, with language selection per browser session.** — All pages and messages in the console are now available in English or Vietnamese, and you can choose the language through your browser.

## 🐛 Bug Fixes

- **The CLI now rejects server addresses that are not valid http(s) URLs, showing a clear message and exit code 2 instead of an internal error.** — If you don't include 'http://' or 'https://' in the server address, the program will now give a clear error message instead of crashing.

- **All container base images in Dockerfiles and docker-compose.yml are now pinned by digest, and Dependabot configuration was updated to watch for changes to these digests. Related deployment documentation was updated.** — No changes will be seen while using the product; everything works the same as before.
