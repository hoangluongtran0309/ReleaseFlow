# Release v0.3.0

MongoDB backups, notifications, and restore verification.

## What's New

This release includes 13 changes: 3 breaking changes, 5 new features, 1 bug fix, 1 documentation update, and 3 maintenance changes.

> ⚠️ 3 breaking changes require action before upgrading.

## ⚠️ Breaking Changes

- **A new 'verify' command was added to the CLI, allowing users to start and monitor a restore verification for a backup. All commands now use the versioned API endpoint (/api/v1), dropping support for unversioned paths and older (0.2) server APIs.** ([!5](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/5)) — The 'verify' command allows you to run and wait for server-side restore verification from the CLI, reflecting the outcome via exit codes. After upgrading both server and CLI to 0.3+, ensure you do not use older (0.2) servers as they are no longer compatible.
  - Migration: Update both CLI and server to version 0.3 or newer before use. Adjust any scripts or integrations to replace unversioned API paths with '/api/v1'. Verify that older servers (0.2) are no longer in use.
  - Tracked in [DBV-26](https://hoangluongtran0309.atlassian.net/browse/DBV-26): CLI: verify command (Done)

- **The HTTP API endpoints are now versioned under /api/v1. All previous /api/targets... paths now respond with a 308 redirect to their new /api/v1... equivalent and are deprecated. A new endpoint for starting and tracking backup restore verifications has been added, and API docs have been updated. CSRF protection was removed from the API (but not the console).** ([#34](https://github.com/hoangtulangtoc/dbvault/pull/34)) — Database backup verification is now available and API endpoints have moved under /api/v1, so scripts that access the API directly must be updated to follow the new versioned paths; ensure migration for any automation and note that manual verification via API is also possible.
  - Migration: Update all scripts, integrations, and clients to call /api/v1 endpoints directly; ensure redirect-following is enabled or update to a compatible client version before upgrading.
  - Tracked in [DBV-24](https://hoangluongtran0309.atlassian.net/browse/DBV-24): Version the HTTP API under /api/v1 (Done)
  - Tracked in [DBV-26](https://hoangluongtran0309.atlassian.net/browse/DBV-26): CLI: verify command (Done)

- **Legacy unversioned '/api/targets' and '/api/targets/{id}/backups' endpoints were removed. The related redirect controller and its test were also deleted. Documentation was updated to reflect the removal instead of a redirect.** ([#35](https://github.com/hoangtulangtoc/dbvault/pull/35)) — After upgrading, any scripts or tools that use the old '/api/targets' endpoints will fail with 404 and need to switch to '/api/v1/targets'. Verify database client scripts and automation are updated before deployment to avoid disruption; there is no mechanism to revert to the old endpoints.
  - Migration: Update all clients or scripts to use '/api/v1/...' endpoints instead of the legacy '/api/...'. No rollback to the unversioned paths after this change.
  - Tracked in [DBV-24](https://hoangluongtran0309.atlassian.net/browse/DBV-24): Version the HTTP API under /api/v1 (Done)

## ✨ New Features

- **Added MongoDB backup and restore support as a third engine, using mongodump/mongorestore, and updated authentication/database handling, artifact formats, and CLI/CI requirements.** ([#31](https://github.com/hoangtulangtoc/dbvault/pull/31)) — You can now add MongoDB databases as backup/restore targets. Configure the 'authentication database' (defaults to 'admin') during setup. Existing SQL targets are unaffected. After upgrade, confirm mongodump/mongorestore availability and review new backup artifact names (.archive.gz). Rollback requires removing MongoDB targets or ignoring the extra column.
  - Migration: Apply migration V11 to add the authentication database column. No change is needed unless using MongoDB targets.
  - Tracked in [DBV-21](https://hoangluongtran0309.atlassian.net/browse/DBV-21): MongoDB logical backup and restore (Done)

- **Added notification channels (Slack, email, generic webhook) to alert operators about backup and restore events, with new management pages and per-target subscriptions.** ([#32](https://github.com/hoangtulangtoc/dbvault/pull/32)) — Notification events do not directly affect database schema or stored data outside the new notification channel/subscription tables (migration V12). After upgrade, check that new tables are present; deleting a target removes its subscriptions, while channels in use cannot be deleted. SMTP credentials for email need to be configured in deployment as needed; rolling back simply removes notification functionality.
  - Migration: After upgrading, review and configure notification channels and event subscriptions for each target as desired; update docker-compose with SMTP options if using email.
  - Tracked in [DBV-22](https://hoangluongtran0309.atlassian.net/browse/DBV-22): Notify operators through Slack, email and webhooks (Done)

- **Added restore verification for backups, allowing a backup to be restored into an isolated, temporary database for validation without affecting production data.** ([#33](https://github.com/hoangtulangtoc/dbvault/pull/33)) — Backups can now be verified by restoring them into a transient database container (MySQL 8.4, PostgreSQL 17-alpine, MongoDB 8.0), which validates that data is actually restorable without touching production or existing targets. After upgrade, nothing changes until you enable this feature (DBVAULT_VERIFICATION_ENABLED=true) and ensure Docker socket access; to roll back, simply disable this flag.
  - Migration: No immediate action is needed on upgrade. To start using the feature, set DBVAULT_VERIFICATION_ENABLED=true and ensure Docker socket is accessible, then optionally update notification channels. No database targets or existing backups are affected by default.
  - Tracked in [DBV-23](https://hoangluongtran0309.atlassian.net/browse/DBV-23): Isolated restore verification (Done)

- **An on-call rotation was set up to receive alerts for failed scheduled backups, with notifications via Slack, escalation after 30 minutes, and an initial response checklist.** ([OPS-10](https://linear.app/dbvault/issue/OPS-10/set-up-an-on-call-rotation-for-backup-failures)) — If a scheduled backup fails, alerting now pages the on-call engineer on Slack and escalates after 30 minutes, helping ensure timely response; no changes impact stored data, schema, or rollback/restore behavior.

- **A Grafana dashboard was created to display metrics about backup jobs, such as backup duration and size per target, recent failures, and time since the last successful backup.** ([OPS-11](https://linear.app/dbvault/issue/OPS-11/build-the-grafana-dashboard-for-backup-jobs)) — You now have a Grafana dashboard showing backup durations, sizes, failures in the last 24 hours, and time since the most recent successful backup for each target. No changes to your database are required. Confirm that your metrics flow to Grafana as expected after upgrade.

## 🐛 Bug Fixes

- **Cron schedules around daylight saving time changes now run as expected, instead of being skipped or repeated. A new WallClockCronTrigger replaces Quartz's cron trigger for scheduling, matching wall-clock time in the selected time zone. Times that do not exist due to a spring daylight saving jump are now correctly scheduled later, and repeated times in autumn only trigger once on their first occurrence.** ([#41](https://github.com/hoangtulangtoc/dbvault/pull/41)) — Scheduled jobs now reliably run once per day across daylight saving transitions. For example, a job set at 02:30 in America/New_York will be triggered at 03:30 on the spring forward day, ensuring no skipped or repeated runs. There are no changes to schema or stored data, and no special upgrade steps are required.
  - Tracked in [DBV-25](https://hoangluongtran0309.atlassian.net/browse/DBV-25): Schedules skip or repeat runs at daylight saving changes (Done)

## 📚 Documentation

- **A step-by-step runbook for performing the monthly restore drill was added.** ([OPS-9](https://linear.app/dbvault/issue/OPS-9/write-the-restore-drill-runbook)) — There is now a documented procedure for monthly restore drills: restore a chosen backup into an isolated environment, compare row counts and checksums, and record the findings. Review the runbook to align your backup validation practices.

## 🔧 Maintenance

- **A new test was added that uses a generated fixture simulating a long backup history with 5000 entries, including both successful and failed backups, to verify that the command-line output handles large histories correctly.** ([!6](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/6)) — There are no changes affecting actual stored backups or how backup histories are managed. This update only adds a test to check how the CLI displays backup histories, so there is nothing you need to do after upgrade.

- **Dependabot version updates were enabled to automatically propose updates for Maven dependencies and GitHub Actions once a week, through pull requests targeting the 'develop' branch.** ([#36](https://github.com/hoangtulangtoc/dbvault/pull/36)) — No database schema, credentials, or stored data is affected by this change. Routine dependency updates are now automated; there are no changes impacting database operation or restore processes.
  - Tracked in [OPS-8](https://linear.app/dbvault/issue/OPS-8/enable-dependabot-alerts-and-version-updates-for-dbvault): Enable Dependabot alerts and version updates for dbvault (Done)

- **The dependency org.springframework.boot:spring-boot-starter-parent was updated from version 4.1.0 to 4.1.1.** ([#37](https://github.com/hoangtulangtoc/dbvault/pull/37)) — No changes to database schema, data handling, or credentials are introduced by this update of the Spring Boot parent dependency.
  - Migration: If you use the Spring Boot Gradle plugin with Protobuf and gRPC, review your build configuration as automatic gRPC configuration now requires an explicit opt-in. Otherwise, no migration steps are required.
