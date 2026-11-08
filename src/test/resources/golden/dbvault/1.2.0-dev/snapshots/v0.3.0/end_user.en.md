# Release v0.3.0

MongoDB backups, notifications, and restore verification.

## What's New

This release includes 13 changes: 3 breaking changes, 5 new features, 1 bug fix, 1 documentation update, and 3 maintenance changes.

> ⚠️ 3 breaking changes require action before upgrading.

## ⚠️ Breaking Changes

- **A new 'verify' command was added to the CLI, allowing users to start and monitor a restore verification for a backup. All commands now use the versioned API endpoint (/api/v1), dropping support for unversioned paths and older (0.2) server APIs.** — You can now ask dbvault to check that a backup will actually restore. This new command tells you if verification passed, failed, or timed out, and needs a recent version of both the tool and the server.

- **The HTTP API endpoints are now versioned under /api/v1. All previous /api/targets... paths now respond with a 308 redirect to their new /api/v1... equivalent and are deprecated. A new endpoint for starting and tracking backup restore verifications has been added, and API docs have been updated. CSRF protection was removed from the API (but not the console).** — Using the API now requires a different address (/api/v1), and there's a new feature to check backups automatically; older tools might need to be updated to keep working.

- **Legacy unversioned '/api/targets' and '/api/targets/{id}/backups' endpoints were removed. The related redirect controller and its test were also deleted. Documentation was updated to reflect the removal instead of a redirect.** — Any tools or scripts using the older API paths will not work anymore; they'll need to use the updated paths that start with '/api/v1'.

## ✨ New Features

- **Added MongoDB backup and restore support as a third engine, using mongodump/mongorestore, and updated authentication/database handling, artifact formats, and CLI/CI requirements.** — You can now back up and restore MongoDB databases using dbvault, like you already do for MySQL and PostgreSQL.

- **Added notification channels (Slack, email, generic webhook) to alert operators about backup and restore events, with new management pages and per-target subscriptions.** — You can now receive alerts by email, Slack, or other services when a backup or restore starts, succeeds, or fails, so you don’t need to watch the dashboard for updates.

- **Added restore verification for backups, allowing a backup to be restored into an isolated, temporary database for validation without affecting production data.** — You can now check any backup by restoring it into a temporary, safe environment to confirm it really works—no risk to your real data. This option is off unless you choose to turn it on.

- **An on-call rotation was set up to receive alerts for failed scheduled backups, with notifications via Slack, escalation after 30 minutes, and an initial response checklist.** — You may notice faster resolution of backup-related problems, but there are no direct changes to how you use the product.

- **A Grafana dashboard was created to display metrics about backup jobs, such as backup duration and size per target, recent failures, and time since the last successful backup.** — You can now see more details about your backups—like how long they take, their size, and any recent failures—in a new dashboard.

## 🐛 Bug Fixes

- **Cron schedules around daylight saving time changes now run as expected, instead of being skipped or repeated. A new WallClockCronTrigger replaces Quartz's cron trigger for scheduling, matching wall-clock time in the selected time zone. Times that do not exist due to a spring daylight saving jump are now correctly scheduled later, and repeated times in autumn only trigger once on their first occurrence.** — Scheduled actions no longer miss or repeat at the start and end of daylight saving time; each is now run once each day as expected.

## 📚 Documentation

- **A step-by-step runbook for performing the monthly restore drill was added.** — You may not notice any changes, but this update helps make sure backup recoveries are tested regularly and work reliably.

## 🔧 Maintenance

- **A new test was added that uses a generated fixture simulating a long backup history with 5000 entries, including both successful and failed backups, to verify that the command-line output handles large histories correctly.** — You won't notice any changes, as this update only adds a backend test to make sure the command works as expected with a lot of backup data.

- **Dependabot version updates were enabled to automatically propose updates for Maven dependencies and GitHub Actions once a week, through pull requests targeting the 'develop' branch.** — You shouldn't notice any difference when using dbvault; this only changes behind-the-scenes maintenance.

- **The dependency org.springframework.boot:spring-boot-starter-parent was updated from version 4.1.0 to 4.1.1.** — There are no changes you will notice in daily use from this update.
