# Release v0.3.0

MongoDB backups, notifications, and restore verification.

## What's New

This release includes 13 changes: 3 breaking changes, 5 new features, 1 bug fix, 1 documentation update, and 3 maintenance changes.

> ⚠️ 3 breaking changes require action before upgrading.

## ⚠️ Breaking Changes

- **A new 'verify' command was added to the CLI, allowing users to start and monitor a restore verification for a backup. All commands now use the versioned API endpoint (/api/v1), dropping support for unversioned paths and older (0.2) server APIs.** ([!5](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/5))
  - Migration: Update both CLI and server to version 0.3 or newer before use. Adjust any scripts or integrations to replace unversioned API paths with '/api/v1'. Verify that older servers (0.2) are no longer in use.

- **The HTTP API endpoints are now versioned under /api/v1. All previous /api/targets... paths now respond with a 308 redirect to their new /api/v1... equivalent and are deprecated. A new endpoint for starting and tracking backup restore verifications has been added, and API docs have been updated. CSRF protection was removed from the API (but not the console).** ([#34](https://github.com/hoangtulangtoc/dbvault/pull/34))
  - Migration: Update all scripts, integrations, and clients to call /api/v1 endpoints directly; ensure redirect-following is enabled or update to a compatible client version before upgrading.

- **Legacy unversioned '/api/targets' and '/api/targets/{id}/backups' endpoints were removed. The related redirect controller and its test were also deleted. Documentation was updated to reflect the removal instead of a redirect.** ([#35](https://github.com/hoangtulangtoc/dbvault/pull/35))
  - Migration: Update all clients or scripts to use '/api/v1/...' endpoints instead of the legacy '/api/...'. No rollback to the unversioned paths after this change.

## ✨ New Features

- **Added MongoDB backup and restore support as a third engine, using mongodump/mongorestore, and updated authentication/database handling, artifact formats, and CLI/CI requirements.** ([#31](https://github.com/hoangtulangtoc/dbvault/pull/31))
  - Migration: Apply migration V11 to add the authentication database column. No change is needed unless using MongoDB targets.

- **Added notification channels (Slack, email, generic webhook) to alert operators about backup and restore events, with new management pages and per-target subscriptions.** ([#32](https://github.com/hoangtulangtoc/dbvault/pull/32))
  - Migration: After upgrading, review and configure notification channels and event subscriptions for each target as desired; update docker-compose with SMTP options if using email.

- **Added restore verification for backups, allowing a backup to be restored into an isolated, temporary database for validation without affecting production data.** ([#33](https://github.com/hoangtulangtoc/dbvault/pull/33))
  - Migration: No immediate action is needed on upgrade. To start using the feature, set DBVAULT_VERIFICATION_ENABLED=true and ensure Docker socket is accessible, then optionally update notification channels. No database targets or existing backups are affected by default.

- **An on-call rotation was set up to receive alerts for failed scheduled backups, with notifications via Slack, escalation after 30 minutes, and an initial response checklist.** ([OPS-10](https://linear.app/dbvault/issue/OPS-10/set-up-an-on-call-rotation-for-backup-failures))

- **A Grafana dashboard was created to display metrics about backup jobs, such as backup duration and size per target, recent failures, and time since the last successful backup.** ([OPS-11](https://linear.app/dbvault/issue/OPS-11/build-the-grafana-dashboard-for-backup-jobs))

## 🐛 Bug Fixes

- **Cron schedules around daylight saving time changes now run as expected, instead of being skipped or repeated. A new WallClockCronTrigger replaces Quartz's cron trigger for scheduling, matching wall-clock time in the selected time zone. Times that do not exist due to a spring daylight saving jump are now correctly scheduled later, and repeated times in autumn only trigger once on their first occurrence.** ([#41](https://github.com/hoangtulangtoc/dbvault/pull/41))

## 📚 Documentation

- **A step-by-step runbook for performing the monthly restore drill was added.** ([OPS-9](https://linear.app/dbvault/issue/OPS-9/write-the-restore-drill-runbook))

## 🔧 Maintenance

- **A new test was added that uses a generated fixture simulating a long backup history with 5000 entries, including both successful and failed backups, to verify that the command-line output handles large histories correctly.** ([!6](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/6))

- **Dependabot version updates were enabled to automatically propose updates for Maven dependencies and GitHub Actions once a week, through pull requests targeting the 'develop' branch.** ([#36](https://github.com/hoangtulangtoc/dbvault/pull/36))

- **The dependency org.springframework.boot:spring-boot-starter-parent was updated from version 4.1.0 to 4.1.1.** ([#37](https://github.com/hoangtulangtoc/dbvault/pull/37))
  - Migration: If you use the Spring Boot Gradle plugin with Protobuf and gRPC, review your build configuration as automatic gRPC configuration now requires an explicit opt-in. Otherwise, no migration steps are required.
