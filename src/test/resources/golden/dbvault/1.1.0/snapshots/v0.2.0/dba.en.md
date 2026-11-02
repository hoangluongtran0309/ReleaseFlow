# Release v0.2.0

## What's New

This release includes 25 changes: 1 breaking change, 3 security changes, 11 new features, 3 bug fixes, 1 performance improvement, 2 documentation updates, and 4 maintenance changes.

> ⚠️ 1 breaking change requires action before upgrading.

## ⚠️ Breaking Changes

- **The CLI now requires the backup target ID to be provided with the --target-id option instead of as a positional argument.** ([!3](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/3))
  - Migration: Update any scripts or manual commands to use --target-id \<id> instead of passing the target ID positionally.
  - Tracked in [DBV-17](https://hoangluongtran0309.atlassian.net/browse/DBV-17): CLI: rename --target to --target-id (Done)

## 🔒 Security

- **Encryption of each target's password now uses a unique key derived from the master key via HKDF with the target's ID as context, instead of encrypting all passwords with the same key.** ([#25](https://github.com/hoangtulangtoc/dbvault/pull/25))
  - Migration: No direct action is required; credentials migrate to the new key scheme individually on update.
  - Tracked in [DBV-16](https://hoangluongtran0309.atlassian.net/browse/DBV-16): Every target's credentials are encrypted with the same key (In Progress)

- **A review was conducted on dbvault's credential encryption, covering encryption key management and exposure risks from database dumps.** ([OPS-6](https://linear.app/dbvault/issue/OPS-6/security-review-of-credential-encryption))

- **Integration tests no longer carry hard-coded S3 access credentials in the source; instead, credentials are injected via repository secrets for CI, or randomly generated for local runs. The S3 emulator image is now pinned by digest rather than by tag.** ([OPS-7](https://linear.app/dbvault/issue/OPS-7/rotate-s3-credentials-used-by-integration-tests)) — There is no impact on production data, schema, or storage configuration. This update affects only test suite behavior and does not alter deployed databases or require any DBA action.

## ✨ New Features

- **Improvements were made to the interface, including clearer history and target tables, new sidebar shell with theme support and custom font, improved status badges, interactive progress on submit buttons, inline error rendering, and expanded detail on backup and restore statuses.** ([#11](https://github.com/hoangtulangtoc/dbvault/pull/11))

- **Editing a target's connection details is now supported.** ([#14](https://github.com/hoangtulangtoc/dbvault/pull/14))

- **The API now exposes backup checksums, including the recorded checksum value and the algorithm used.** ([#15](https://github.com/hoangtulangtoc/dbvault/pull/15))
  - Tracked in [DBV-15](https://hoangluongtran0309.atlassian.net/browse/DBV-15): Expose backup checksums in the API (Done)

- **A new feature was added that allows backups to be restored into any registered target.** ([#16](https://github.com/hoangtulangtoc/dbvault/pull/16))

- **Paging was added to the backup and restore history feature.** ([#17](https://github.com/hoangtulangtoc/dbvault/pull/17))

- **Bulk deletion of multiple backups and removal of a backup target together with its associated backups were added. The confirmation page totals files, bytes, and restore records before deletion. Pre-checks ensure that deletions are refused if a backup is running or being restored. Removing a target without backups keeps the one-click dialog; removing a target with backups requires confirmation and typing its name.** ([#18](https://github.com/hoangtulangtoc/dbvault/pull/18))

- **Database connection tests, backups, and restores are now routed based on the target database engine, with support added for PostgreSQL alongside existing MySQL functionality. PostgreSQL adapters, UI support, runtime handling, migration coverage, integration tests, and updated documentation are included. Restores are limited to the original engine type.** ([#20](https://github.com/hoangtulangtoc/dbvault/pull/20))
  - Migration: No specific migration required unless supporting PostgreSQL targets; the system restricts changes to a target's engine after registration.
  - Tracked in [DBV-10](https://hoangluongtran0309.atlassian.net/browse/DBV-10): Route backups by engine and add PostgreSQL (Done)

- **Added support for Quartz backup schedules.** ([#21](https://github.com/hoangtulangtoc/dbvault/pull/21))

- **Backup scheduling for targets is now possible using cron expressions with a selected time zone.** ([DBV-12](https://hoangluongtran0309.atlassian.net/browse/DBV-12))

- **A new retention feature was added to keep only the newest N successful backups per target.** ([#22](https://github.com/hoangtulangtoc/dbvault/pull/22))
  - Migration: Configure the retention setting to specify how many recent backups to keep, if desired. No other special migration needed.
  - Tracked in [DBV-13](https://hoangluongtran0309.atlassian.net/browse/DBV-13): Keep the newest N backups per target (Done)

- **Added support for S3-compatible storage profiles, allowing artifacts to be stored in any S3-compatible bucket via named profiles.** ([#23](https://github.com/hoangtulangtoc/dbvault/pull/23))
  - Tracked in [DBV-11](https://hoangluongtran0309.atlassian.net/browse/DBV-11): S3-compatible storage profiles (Done)

## 🐛 Bug Fixes

- **The CLI now correctly prioritizes the DBVAULT_URL environment variable over the configuration file when determining which server to connect to.** ([!4](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/4))
  - Tracked in [DBV-18](https://hoangluongtran0309.atlassian.net/browse/DBV-18): CLI ignores the DBVAULT\_URL environment variable (Done)

- **Local backup artifacts now use unique file paths for each backup execution to prevent collisions when multiple backups run at the same time for the same database.** ([#24](https://github.com/hoangtulangtoc/dbvault/pull/24))
  - Tracked in [DBV-14](https://hoangluongtran0309.atlassian.net/browse/DBV-14): Two backups started in the same second overwrite each other (Done)

- **The console now shows backup sizes in B, KB, MB, GB, or TB instead of a raw byte count.** ([#28](https://github.com/hoangtulangtoc/dbvault/pull/28))

## ⚡ Performance

- **The restore process now streams gunzip output directly into the MySQL client rather than decompressing the archive to disk first.** ([#19](https://github.com/hoangtulangtoc/dbvault/pull/19))
  - Tracked in [DBV-32](https://hoangluongtran0309.atlassian.net/browse/DBV-32): Stream decompression into the restore client (Done)

## 📚 Documentation

- **Added documentation on configuring S3-compatible storage profiles for backups, including setup steps and policy requirements.** ([#27](https://github.com/hoangtulangtoc/dbvault/pull/27)) — New docs detail how to configure and maintain S3 or MinIO storage for dbvault backups, including what IAM bucket policies are needed and which profile settings are safe to change after backups are made.

- **Added documentation guiding S3-compatible storage usage, detailing supported providers and required bucket permissions for dbvault.** ([DBV-19](https://hoangluongtran0309.atlassian.net/browse/DBV-19)) — There is now a guide describing how to configure AWS S3 and MinIO storage with dbvault, including the minimal bucket policy for secure operation. No schema, data, or credential changes are involved.

## 🔧 Maintenance

- **The PostgreSQL Testcontainers integration test now covers a wider range of data types (`jsonb`, `timestamptz`, `bytea`, `numeric`), as well as an index, view, foreign key, and bigserial sequence continuation checks.** ([#26](https://github.com/hoangtulangtoc/dbvault/pull/26))

- **Dependabot alerts and automated version update configuration were enabled for the dbvault repository.** ([OPS-8](https://linear.app/dbvault/issue/OPS-8/enable-dependabot-alerts-and-version-updates-for-dbvault))

- **The startup client binary checks for MySQL and PostgreSQL were refactored into a shared check called ClientBinaries.require, and a small unit test was added for this component.** ([#29](https://github.com/hoangtulangtoc/dbvault/pull/29)) — There are no changes to stored data, database schema, credentials, or how the tool interacts with database engines like MySQL or PostgreSQL. This refactoring does not affect how you run or configure dbvault.

- **S3 integration tests no longer contain hard-coded credentials; test credentials are now injected via repository secrets or generated randomly during local runs, and the S3 emulator image is pinned by digest.** ([#30](https://github.com/hoangtulangtoc/dbvault/pull/30)) — This change only affects the S3 integration test environment and does not impact production data, schemas, or storage configuration. No database checks or migrations are required after upgrading.
