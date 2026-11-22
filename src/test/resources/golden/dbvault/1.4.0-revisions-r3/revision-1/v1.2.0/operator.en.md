# Release v1.2.0

A retention preview, times in your own time zone, and a limit on failed sign-ins. The old restore-verification API path is gone: upgrade dbvault-cli together with the server.

## What's New

This release includes 11 changes: 1 breaking change, 1 security change, 4 new features, 4 bug fixes, and 1 maintenance change.

> ⚠️ 1 breaking change requires action before upgrading: [read the upgrade guide](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md).

## ⚠️ Breaking Changes

- **API breaking change: verification endpoint removed; CLI upgrade required** ([#58](https://github.com/hoangtulangtoc/dbvault/pull/58)) — After upgrading the server, dbvault-cli must also be upgraded, as older versions use a removed API endpoint. The old endpoint now returns 404. No configuration changes are needed, but CLI and server must be upgraded together.
  - Why: To simplify the API and avoid maintaining two paths to the same verification attempt, as only dbvault-cli used the old path and it now uses the new one.
  - Detail: The old endpoint GET /api/v1/verifications/{id} is no longer available and returns 404. The POST endpoint for creating verifications already returns a Location header pointing to the new path. Documentation and upgrade guides have been updated accordingly.
  - Limitations: No deprecated period for the old endpoint; it is removed immediately.
  - Required step: Upgrade dbvault-cli to the latest version at the same time as the server, as older versions use the removed endpoint.

## 🔒 Security

- **Sign-in rate limiting by address; check proxy configuration and environment variables** ([#60](https://github.com/hoangtulangtoc/dbvault/pull/60)) — After 10 failed sign-ins from one address in 15 minutes, that address is blocked for 15 minutes. Configure DBVAULT_SIGN_IN_MAX_FAILURES and DBVAULT_SIGN_IN_LOCK as needed. Ensure your reverse proxy forwards client addresses to avoid locking out all users. Counts are in-memory and reset on server restart.
  - Why: To prevent repeated failed sign-in attempts from the same address, reducing the risk of password guessing attacks.
  - Detail: After 10 failed sign-ins from one address in 15 minutes, further attempts from that address are blocked for 15 minutes. The console shows an error message, and the API returns HTTP 429 with Retry-After. The limit and lockout duration can be configured with DBVAULT_SIGN_IN_MAX_FAILURES and DBVAULT_SIGN_IN_LOCK. The count resets on successful sign-in. The mechanism is in-memory, so a server restart clears the counts. If a proxy does not forward client addresses, all clients may be locked out together.
  - Limitations: Behind a proxy that doesn't forward the client address, every client shares one address and is locked out together. The counts live in memory, so a restart forgets them.
  - Recommended step: We recommend also rate-limiting POST /login at your reverse proxy.

## ✨ New Features

- **dbvault backups supports --limit to restrict output to newest backups** ([!10](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/10)) — Operators can now use --limit with dbvault backups to display only the newest backups for a target, simplifying scripts and manual checks. No changes to deployment or configuration are required.
  - Why: To help users avoid paging through long backup histories and quickly access the most recent backups.
  - Detail: The --limit option accepts a value of 1 or more and restricts the output to that number of newest backups for a given target, in both table and JSON output modes. Using --limit 0 results in a usage error (exit code 2). The README was updated to document this option.
  - Limitations: The --limit option does not accept 0 or negative values; using such values results in a usage error.

- **Retention policy preview shows potential backup deletions before saving** ([#56](https://github.com/hoangtulangtoc/dbvault/pull/56)) — Operators can preview which backups a retention policy would delete before saving changes. No operational steps are required; no backups are deleted until Save is pressed.

- **API endpoint for restore-verification attempts updated** ([#57](https://github.com/hoangtulangtoc/dbvault/pull/57)) — Scripts that read restore-verification attempts should use the new path under the backup; the old path is removed in this release.

- **Operators can select time zone for all console-displayed times** ([#61](https://github.com/hoangtulangtoc/dbvault/pull/61)) — You can now choose your preferred time zone in the sidebar, and all times in the console will be shown in that zone, labeled accordingly. This setting is browser-specific and does not affect schedules, API, CLI, or logs, which remain in UTC.
  - Why: This change addresses the need for operators in different time zones to view times in their local zone, avoiding manual conversions.
  - Detail: A new 'Time zone' option appears in the sidebar; selecting a zone and applying it updates all displayed times (backups, restores, verifications, retention runs, connection checks) to that zone, with the zone name shown. The choice is stored in a browser cookie and can also be set via the ?tz=\<zone> URL parameter. This setting is browser-specific and not tied to the user account. Schedules retain their own time zones, and the HTTP API, CLI, and logs remain in UTC. Labels are available in English and Vietnamese.

## 🐛 Bug Fixes

- **CLI 'verify' requires server 1.2.0+; upgrade both together** ([!11](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/11)) — Deployments must upgrade both the dbvault server and CLI to 1.2.0 or later for verification polling to function. Using the CLI with an older server causes 'verify' to exit with code 4.
  - Why: The server removed the old verification API path, so the CLI needed to follow the new path to remain compatible.
  - Detail: 'verify' now requests 'GET /api/v1/backups/\<backup-id>/verifications/\<verification-id>' instead of the old global verification path. The CLI exits with code 4 if used against a pre-1.2.0 server.
  - Limitations: This CLI version is not compatible with dbvault server 1.1 or earlier for verification polling.
  - Required step: Upgrade both the dbvault server to 1.2.0 or later and this CLI version together to ensure 'verify' works.

- **`dbvault verify` refuses a zero or negative --timeout with exit code 2** ([!12](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/12)) — A script that passed `--timeout PT0S` or a negative duration used to start a verification and get exit code 9; it now gets exit code 2 (a usage error) and nothing starts on the server.

- **Backup sizes no longer read 1024.0 KB** ([#62](https://github.com/hoangtulangtoc/dbvault/pull/62)) — A size just under 1 MiB now reads 1.0 MB in the console.

- **Restores list now indicates deleted source backups** ([#63](https://github.com/hoangtulangtoc/dbvault/pull/63)) — The UI now clearly states when a restore's source backup was deleted. No operational changes or actions are needed.

## 🔧 Maintenance

- **Staging environment MinIO credentials rotated** ([OPS-15](https://linear.app/dbvault/issue/OPS-15/rotate-the-minio-access-key-of-the-staging-dbvault)) — The MinIO access key for staging was rotated and secrets updated. No changes to deployment or configuration are required for production or user environments.

**Upgrade guide:** [Read before upgrading](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md)
