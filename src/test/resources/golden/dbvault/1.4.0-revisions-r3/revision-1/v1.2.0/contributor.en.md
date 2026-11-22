# Release v1.2.0

A retention preview, times in your own time zone, and a limit on failed sign-ins. The old restore-verification API path is gone: upgrade dbvault-cli together with the server.

## What's New

This release includes 14 changes: 1 breaking change, 1 security change, 4 new features, 4 bug fixes, 1 documentation update, and 3 maintenance changes.

> ⚠️ 1 breaking change requires action before upgrading: [read the upgrade guide](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md).

## ⚠️ Breaking Changes

- **Removed GET /api/v1/verifications/{id}; use backup-scoped path** ([#58](https://github.com/hoangtulangtoc/dbvault/pull/58)) — The GET /api/v1/verifications/{id} endpoint is gone and returns 404. Use GET /api/v1/backups/{backupId}/verifications/{id} instead. The POST endpoint already returns the correct Location. Update any code or scripts using the old path. See docs/api.md and the 1.2 upgrade guide.
  - Why: To simplify the API and avoid maintaining two paths to the same verification attempt, as only dbvault-cli used the old path and it now uses the new one.
  - Implementation: The old endpoint GET /api/v1/verifications/{id} is no longer available and returns 404. The POST endpoint for creating verifications already returns a Location header pointing to the new path. Documentation and upgrade guides have been updated accordingly.
  - Limitations: No deprecated period for the old endpoint; it is removed immediately.
  - Required step: Upgrade dbvault-cli to the latest version at the same time as the server, as older versions use the removed endpoint.

## 🔒 Security

- **Sign-in throttling added: 10 failures per address in 15 minutes triggers lockout** ([#60](https://github.com/hoangtulangtoc/dbvault/pull/60)) — A new in-memory rate limiter blocks sign-in attempts from an address after 10 failures in 15 minutes. The limits are configurable via DBVAULT_SIGN_IN_MAX_FAILURES and DBVAULT_SIGN_IN_LOCK. The mechanism is shared between the console and API, and resets on successful sign-in. Tests added in SignInThrottleTest, SecurityConfigTest, and ApiSecurityTest.
  - Why: To prevent repeated failed sign-in attempts from the same address, reducing the risk of password guessing attacks.
  - Implementation: After 10 failed sign-ins from one address in 15 minutes, further attempts from that address are blocked for 15 minutes. The console shows an error message, and the API returns HTTP 429 with Retry-After. The limit and lockout duration can be configured with DBVAULT_SIGN_IN_MAX_FAILURES and DBVAULT_SIGN_IN_LOCK. The count resets on successful sign-in. The mechanism is in-memory, so a server restart clears the counts. If a proxy does not forward client addresses, all clients may be locked out together.
  - Limitations: Behind a proxy that doesn't forward the client address, every client shares one address and is locked out together. The counts live in memory, so a restart forgets them.
  - Recommended step: We recommend also rate-limiting POST /login at your reverse proxy.

## ✨ New Features

- **Added --limit to dbvault backups for restricting output to newest backups** ([!10](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/10)) — The dbvault backups command now supports --limit \<count> to display only the \<count> newest backups for a target, in both table and JSON output. The option requires a value of 1 or more; 0 triggers a usage error (exit code 2). Tests and documentation were updated accordingly.
  - Why: To help users avoid paging through long backup histories and quickly access the most recent backups.
  - Implementation: The --limit option accepts a value of 1 or more and restricts the output to that number of newest backups for a given target, in both table and JSON output modes. Using --limit 0 results in a usage error (exit code 2). The README was updated to document this option.
  - Limitations: The --limit option does not accept 0 or negative values; using such values results in a usage error.

- **Retention preview endpoint and UI added for backup deletion simulation** ([#56](https://github.com/hoangtulangtoc/dbvault/pull/56)) — A Preview button was added to the retention form, calling `POST /retention/{targetId}/preview` to simulate which backups would be deleted by the current policy input. The logic matches actual retention runs, and new tests cover this in `ManageBackupRetentionServiceTest` and `BackupRetentionControllerTest`. No state changes occur until Save.
  - Why: To let users understand the impact of a retention policy before applying it, reducing the risk of accidental data loss.
  - Implementation: The retention form now includes a Preview button that sends the entered number to the endpoint `POST /retention/{targetId}/preview`. The response shows how many backups would be deleted and their start times, matching the logic of an actual retention run. No changes are saved or backups deleted until Save is pressed. The feature supports both English and Vietnamese.
  - Limitations: The preview only simulates deletion; actual changes occur only when Save is pressed.

- **Restore-verification API now nested under backups** ([#57](https://github.com/hoangtulangtoc/dbvault/pull/57)) — Read an attempt at `GET /api/v1/backups/{backupId}/verifications/{id}`; the POST's `Location` already points there. The old `GET /api/v1/verifications/{id}` is removed in this release, see the breaking change.
  - Why: It groups each verification under the backup it verifies, the path that stays once the old one is removed in this release (see the breaking change).
  - Implementation: The new endpoint is `GET /api/v1/backups/{backupId}/verifications/{id}`, and a new verification's `Location` points there. It answers `404` for an attempt of another backup.
  - Recommended step: Update any integration or script that reads a restore-verification attempt to use the new endpoint; the old one is removed in this same release.

- **Time zone selection added to console UI; times shown in chosen zone** ([#61](https://github.com/hoangtulangtoc/dbvault/pull/61)) — A time zone selector is now present in the sidebar. The selected zone is stored in a browser cookie and applied to all time displays in the console UI. The implementation does not affect backend logic, API, CLI, or logs, which continue to use UTC. Schedules retain their own zones.
  - Why: This change addresses the need for operators in different time zones to view times in their local zone, avoiding manual conversions.
  - Implementation: A new 'Time zone' option appears in the sidebar; selecting a zone and applying it updates all displayed times (backups, restores, verifications, retention runs, connection checks) to that zone, with the zone name shown. The choice is stored in a browser cookie and can also be set via the ?tz=\<zone> URL parameter. This setting is browser-specific and not tied to the user account. Schedules retain their own time zones, and the HTTP API, CLI, and logs remain in UTC. Labels are available in English and Vietnamese.

## 🐛 Bug Fixes

- **'verify' command now polls verification status under backup-specific API path** ([!11](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/11)) — The CLI now uses 'GET /api/v1/backups/\<backup-id>/verifications/\<verification-id>' for polling, matching the server's API change in 1.2.0. Tests expect the old path to return 404. Ensure compatibility by upgrading both CLI and server.
  - Why: The server removed the old verification API path, so the CLI needed to follow the new path to remain compatible.
  - Implementation: 'verify' now requests 'GET /api/v1/backups/\<backup-id>/verifications/\<verification-id>' instead of the old global verification path. The CLI exits with code 4 if used against a pre-1.2.0 server.
  - Limitations: This CLI version is not compatible with dbvault server 1.1 or earlier for verification polling.
  - Required step: Upgrade both the dbvault server to 1.2.0 or later and this CLI version together to ensure 'verify' works.

- **'dbvault verify' exits with usage error for zero or negative --timeout** ([!12](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/12)) — The CLI now checks for --timeout <= 0 before contacting the server. It exits with code 2 and an error message if the value is invalid. Tests updated: 'aTimeoutOfZeroOrLessIsAUsageErrorAndStartsNothing' covers this, and 'verifyGivesUpAfterItsTimeout' uses a positive timeout.
  - Why: Previously, a zero or negative timeout would start a verification on the server and immediately give up, leaving an unnecessary verification running.
  - Implementation: If --timeout is set to zero or a negative duration, 'dbvault verify' exits with code 2 and displays an error message. No verification is started on the server. Positive timeouts and the default behavior are unchanged.

- **Sizes just under a unit boundary round into the next unit** ([#62](https://github.com/hoangtulangtoc/dbvault/pull/62)) — `ByteSizes.readable` no longer prints values like 1024.0 KB; it switches unit from 1023.95.

- **Restores list clarifies deleted source backups in UI** ([#63](https://github.com/hoangtulangtoc/dbvault/pull/63)) — The Restores list now checks if the source backup exists; if not, it displays a localized message ('Backup deleted since') instead of a dash. Test coverage is in RestoreControllerTest.theListSaysWhenTheBackupARestoreCameFromWasDeleted.
  - Why: Previously, a deleted source backup showed only a dash, which looked like missing data and confused users; this clarifies the situation.
  - Implementation: The From backup column in the Restores list checks if the source backup still exists; if not, it displays a message indicating deletion, with translations for English and Vietnamese.

## 📚 Documentation

- **Upgrade guide for 1.2 documents removal of verification endpoint** ([#59](https://github.com/hoangtulangtoc/dbvault/pull/59)) — The documentation now includes an upgrade guide for 1.2, detailing the removal of GET /api/v1/verifications/{id}, who is affected, upgrade order, and rollback steps. No code changes, docs only.
  - Why: To inform users and developers about the upgrade process to version 1.2 and the impact of the removed API endpoint.
  - Implementation: The guide is located at docs/upgrading-to-1.2.md, linked from docs/README.md and docs/api.md. It explains who is affected by the removed endpoint, the recommended upgrade order, and rollback steps.

## 🔧 Maintenance

- **MongoBackupRestoreIT is skipped on GitHub Actions CI until DBV-45 is fixed** ([#55](https://github.com/hoangtulangtoc/dbvault/pull/55)) — The MongoBackupRestoreIT integration test will not run on GitHub Actions (when GITHUB_ACTIONS=true) due to frequent container failures. The test still runs locally. No production code is affected. See DBV-45 for ongoing investigation.
  - Why: The test frequently fails on CI due to the MongoDB container stopping unexpectedly, blocking pull request merges.
  - Implementation: The test is conditionally skipped when the GITHUB_ACTIONS environment variable is set to true; it still runs locally for developers. No production code or backup/restore logic is affected.
  - Limitations: This change does not address the underlying issue with the MongoDB container on CI; it only unblocks CI by skipping the test.

- **AWS SDK BOM dependency updated to 2.57.6** ([#52](https://github.com/hoangtulangtoc/dbvault/pull/52)) — The build now uses software.amazon.awssdk:bom version 2.57.6. Check for any compatibility issues if you use AWS SDK modules directly.

- **Staging MinIO access key rotated and secrets updated** ([OPS-15](https://linear.app/dbvault/issue/OPS-15/rotate-the-minio-access-key-of-the-staging-dbvault)) — The MinIO access key for the staging environment was rotated, with updates to environment and CI secrets. No code changes were made; dbvault continues to read credentials from environment variables.

**Upgrade guide:** [Read before upgrading](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md)
