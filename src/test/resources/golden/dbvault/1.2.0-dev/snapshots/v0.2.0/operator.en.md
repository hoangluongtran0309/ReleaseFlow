# Release v0.2.0

## What's New

This release includes 25 changes: 1 breaking change, 3 security changes, 11 new features, 3 bug fixes, 1 performance improvement, 2 documentation updates, and 4 maintenance changes.

> ⚠️ 1 breaking change requires action before upgrading.

## ⚠️ Breaking Changes

- **The CLI now requires the backup target ID to be provided with the --target-id option instead of as a positional argument.** ([!3](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/3)) — Update operational documentation and any automation to pass --target-id, not a positional value, for 'dbvault backups' commands. Monitor for usage errors (exit code 2) after deploy as old scripts may fail.
  - Why: Users were mistakenly passing the target's name instead of its ID as a positional argument, resulting in 'not found' errors. This change clarifies and enforces correct input.
  - Detail: The previous positional argument for target ID in 'dbvault backups' has been replaced with an explicit --target-id flag. Using the old form results in a usage error with exit code 2.
  - Migration: Update any scripts or manual commands to use --target-id \<id> instead of passing the target ID positionally.

## 🔒 Security

- **Encryption of each target's password now uses a unique key derived from the master key via HKDF with the target's ID as context, instead of encrypting all passwords with the same key.** ([#25](https://github.com/hoangtulangtoc/dbvault/pull/25)) — The encryption backend now uses per-target derived keys so that compromising the master key and a ciphertext exposes only one credential, not all. Credentials update to the new format when edited; monitor for any decryption issues during migration, but no global changes or rollback steps are necessary.
  - Why: To prevent a security vulnerability where leaking one master key and any ciphertext exposed all targets' credentials (DBV-16).
  - Detail: A new HKDF-SHA256 implementation derives per-target encryption keys. Old passwords decrypt as before and migrate to per-target keys on their next edit, while new passwords use the new format marked with a 'v2:' prefix. The master key or any per-target key alone can no longer decrypt all targets' credentials. `EncryptionPort` now provides 'encryptFor' and 'decryptFor', and all relevant flows use these methods.
  - Migration: No direct action is required; credentials migrate to the new key scheme individually on update.

- **A review was conducted on dbvault's credential encryption, covering encryption key management and exposure risks from database dumps.** ([OPS-6](https://linear.app/dbvault/issue/OPS-6/security-review-of-credential-encryption)) — A security assessment of credential encryption was performed; no operational changes or risks result from this review, but the findings may inform future hardening steps.
  - Why: To assess the security posture and identify areas for future improvement, with findings documented for the Hardening epic.
  - Detail: The review examined master key handling, key derivation, nonce reuse, and what can be leaked if only a database dump is obtained, but did not include code changes.

- **Integration tests no longer carry hard-coded S3 access credentials in the source; instead, credentials are injected via repository secrets for CI, or randomly generated for local runs. The S3 emulator image is now pinned by digest rather than by tag.** ([OPS-7](https://linear.app/dbvault/issue/OPS-7/rotate-s3-credentials-used-by-integration-tests)) — Only the integration test setup changes: S3 credentials are now managed via repository secrets, or generated at runtime for local tests. The S3 emulator is pinned to a specific image digest to avoid test flakiness from upstream updates. There is no operational risk or rollback concern for the application itself.
  - Why: This change prevents leaked or misused S3 credentials during testing, improves test security, and ensures that tests do not rely on shared, static credentials.
  - Detail: Repository secrets S3_TEST_ACCESS_KEY and S3_TEST_SECRET_KEY are provided to the verify workflow for S3 integration tests. If these are not set (e.g., local development), each test run generates a new set of random credentials. Tests use an S3 emulator image that is now referenced by a fixed digest to avoid unintentional updates from a moving tag.

## ✨ New Features

- **Improvements were made to the interface, including clearer history and target tables, new sidebar shell with theme support and custom font, improved status badges, interactive progress on submit buttons, inline error rendering, and expanded detail on backup and restore statuses.** ([#11](https://github.com/hoangtulangtoc/dbvault/pull/11)) — Expect interface improvements: more readable tables, error messages now render inline, and operational backup/restore statuses are easier to track. No operational changes or migration steps needed.
  - Why: These changes enhance usability, readability, and feedback within the user console, resolving confusion and an unfinished appearance.
  - Detail: Table formatting was updated to ensure status badges are contained in their own cells. A sidebar shell with light/dark themes and a new typeface was introduced. Submit buttons now have progress indicators and are protected from multiple submissions. Errors display within the console interface, and target lists now summarize backup statuses with links to detailed operation status.

- **Editing a target's connection details is now supported.** ([#14](https://github.com/hoangtulangtoc/dbvault/pull/14)) — After deployment, users are able to change connection details for targets; monitor for misconfigurations and ensure rollback options for incorrect edits.
  - Why: This feature was added to allow users to update connection information for an existing target.
  - Detail: A new capability has been introduced to modify the connection details associated with a configured target. Specific method names, APIs, or configuration parameters are not detailed.

- **The API now exposes backup checksums, including the recorded checksum value and the algorithm used.** ([#15](https://github.com/hoangtulangtoc/dbvault/pull/15)) — Backups now include a checksum and algorithm in the API output, providing an additional verification step for artifact integrity. No changes expected in operational procedures, but watch for client dependencies that may assume the previous response schema.
  - Why: Users need to verify the integrity of downloaded backup artifacts by comparing checksums.
  - Detail: The backup resource in the API now includes fields for the checksum (e.g., SHA-256) and the algorithm. API responses are updated accordingly, with no breaking changes to existing structure.

- **A new feature was added that allows backups to be restored into any registered target.** ([#16](https://github.com/hoangtulangtoc/dbvault/pull/16)) — With this update, restores can be directed to any registered target; verify target configuration before use and monitor for destination integrity after the operation.
  - Why: This enables greater flexibility by allowing users to choose the destination for a restore operation.
  - Detail: The restore process is enhanced to support specifying any registered target as the destination for a backup restore, rather than being limited to a default or original target.

- **Paging was added to the backup and restore history feature.** ([#17](https://github.com/hoangtulangtoc/dbvault/pull/17)) — The backup and restore history UI now pages results, which reduces load times when reviewing large histories; watch for any user feedback about missing or unexpected records in the paginated views.
  - Why: To improve the usability and handling of large history datasets for backup and restore operations.
  - Detail: The history for backup and restore operations now supports pagination, allowing users to view data in chunks rather than loading the entire history at once.

- **Bulk deletion of multiple backups and removal of a backup target together with its associated backups were added. The confirmation page totals files, bytes, and restore records before deletion. Pre-checks ensure that deletions are refused if a backup is running or being restored. Removing a target without backups keeps the one-click dialog; removing a target with backups requires confirmation and typing its name.** ([#18](https://github.com/hoangtulangtoc/dbvault/pull/18)) — Batch backup deletions and target removals with associated backups now offer summary confirmation and block on in-use backups or restores. Monitor deletions for refusals due to running operations; the process is all-or-nothing for bulk actions.
  - Why: This change improves usability and safety when managing backups, providing batch operations and stronger checks to prevent accidental disruption of active processes.
  - Detail: Bulk operations check all items for running or restoring status before any deletion. A confirmation page summarizes the impact. Single backup deletion now blocks if the backup is being restored. Target removal with backups now requires user confirmation with the target's name.

- **Database connection tests, backups, and restores are now routed based on the target database engine, with support added for PostgreSQL alongside existing MySQL functionality. PostgreSQL adapters, UI support, runtime handling, migration coverage, integration tests, and updated documentation are included. Restores are limited to the original engine type.** ([#20](https://github.com/hoangtulangtoc/dbvault/pull/20)) — Backup and restore operations now select the engine adapter (MySQL or PostgreSQL) dynamically based on each target's registration. Restores are only allowed to the same engine as the original, reducing accidental mismatches. Monitor for any backup or restore errors with newly added PostgreSQL support; no rollback step is required unless issues with engine routing arise.
  - Why: To enable support for PostgreSQL databases and to ensure backups and restores are handled according to the engine type, preventing incompatibility issues.
  - Detail: Routing logic determines the appropriate engine adapter (MySQL or PostgreSQL) for connection tests, backups, and restores. The code backfills existing targets as MySQL, restricts restores to the registered engine, and delivers necessary tests and docs. A registered target's engine cannot be changed post-registration.
  - Migration: No specific migration required unless supporting PostgreSQL targets; the system restricts changes to a target's engine after registration.

- **Added support for Quartz backup schedules.** ([#21](https://github.com/hoangtulangtoc/dbvault/pull/21)) — Backup jobs are now scheduled using Quartz. Monitor initial deployments for new scheduling behaviors and ensure Quartz integration does not conflict with existing cron or timer mechanisms.
  - Why: To provide enhanced scheduling capabilities for backups, as per specification DBV-12-quartz.
  - Detail: The implementation introduces integration with Quartz for managing backup job schedules. Referenced spec is DBV-12-quartz, but technical specifics are limited in this summary.

- **Backup scheduling for targets is now possible using cron expressions with a selected time zone.** ([DBV-12](https://hoangluongtran0309.atlassian.net/browse/DBV-12)) — You can configure backup schedules with cron syntax and a specific time zone. Schedules persist across restarts; after downtime, missed backups are executed on server startup. Monitor startup logs for any catch-up backup operations to watch for delays or resource impact.
  - Why: To allow operators to automate backups according to custom timetables and ensure reliability even after restarts or downtime.
  - Detail: Schedules for backups are stored persistently so they survive restarts. If a scheduled backup run is missed due to downtime, it will be triggered once the server starts again.

- **A new retention feature was added to keep only the newest N successful backups per target.** ([#22](https://github.com/hoangtulangtoc/dbvault/pull/22)) — A retention configuration now enforces keeping only the newest N successful backups per target; failed backups are ignored in this count, and deletions of old backups are recorded in logs. After deploying, validate retention settings and monitor backup deletion logs for expected behavior.
  - Why: To control storage growth by limiting the number of backups retained and discarding older ones.
  - Detail: Failed backups are excluded from the N count, and deletion of old backup artifacts is logged.
  - Migration: Configure the retention setting to specify how many recent backups to keep, if desired. No other special migration needed.

- **Added support for S3-compatible storage profiles, allowing artifacts to be stored in any S3-compatible bucket via named profiles.** ([#23](https://github.com/hoangtulangtoc/dbvault/pull/23)) — You can configure multiple named S3-compatible storage profiles with encrypted credentials for artifact storage. Before activating a profile, you can test it to ensure connectivity and proper permissions. Monitor storage endpoints for access or credential issues after deploy.
  - Why: To give operators the flexibility to use different S3-compatible storage solutions (such as AWS S3 and MinIO) for artifact storage, and to enhance security by encrypting secret keys.
  - Detail: The implementation supports multiple storage profiles, each referencing an S3-compatible endpoint with encrypted secret credentials. Profiles can be tested before use to ensure proper configuration.

## 🐛 Bug Fixes

- **The CLI now correctly prioritizes the DBVAULT_URL environment variable over the configuration file when determining which server to connect to.** ([!4](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/4)) — Be aware that DBVAULT_URL now overrides the config file's server address for the CLI. After deploying, check that automated scripts using DBVAULT_URL continue to target the right endpoints. Rollback is safe by returning to the previous version if unexpected server connections occur.
  - Why: Previously, the CLI ignored the DBVAULT_URL variable, always using the server address from the config file even when the environment variable was set. This fix ensures expected environment variable precedence.
  - Detail: DBVAULT_URL now takes precedence; if it's set, its value is used as the server address. If not, the CLI uses the address from ~/.config/dbvault/cli.properties, and if neither is set, it defaults to http://localhost:8090.

- **Local backup artifacts now use unique file paths for each backup execution to prevent collisions when multiple backups run at the same time for the same database.** ([#24](https://github.com/hoangtulangtoc/dbvault/pull/24)) — Backups now create unique artifact paths for each execution, eliminating file overwrites from concurrent jobs. There are no manual upgrade steps. Monitor for failed backups if local paths already exist.
  - Why: Previously, two backups initiated in the same second for the same database could overwrite each other's files, causing data loss.
  - Detail: Artifacts are now stored in the format BACKUP_DIR/\<target-id>/\<execution-id>/\<file>, matching existing remote key structures. The backup process now refuses to run if the local destination already exists, and it cleans up empty directories left behind after deletes. Legacy flat artifacts continue to be accessible.

- **The console now shows backup sizes in B, KB, MB, GB, or TB instead of a raw byte count.** ([#28](https://github.com/hoangtulangtoc/dbvault/pull/28))
  - Detail: Sizes are formatted on the Backups list, a backup's page, and the delete confirmations. A new ByteSizes helper formats them for the console templates: below 1024 bytes the exact count (1023 B); from 1024 bytes up, units of 1024 with one decimal (1024 bytes is 1.0 KB, 61,301,298 bytes is 58.5 MB). A missing size still shows —. On a backup's page, the size's tooltip gives the exact byte count.

## ⚡ Performance

- **The restore process now streams gunzip output directly into the MySQL client rather than decompressing the archive to disk first.** ([#19](https://github.com/hoangtulangtoc/dbvault/pull/19)) — Restores now use less disk space because the data is decompressed while being streamed into MySQL, not staged on disk first. Monitor for changes in CPU usage and be aware that thread allocation for each pipe has changed; check ADR-016 for technical guidelines.
  - Why: This change eliminates the need for free disk space equal to the size of the uncompressed archive, improving disk usage and efficiency during restores.
  - Detail: Gunzip is executed in its own dedicated thread which feeds decompressed data to MySQL's stdin, also using dedicated threads for each pipe to prevent thread pool exhaustion. Truncated or failing sources terminate the client process promptly. This approach is elaborated in ADR-016.

## 📚 Documentation

- **Added documentation on configuring S3-compatible storage profiles for backups, including setup steps and policy requirements.** ([#27](https://github.com/hoangtulangtoc/dbvault/pull/27)) — Operational documentation for S3-compatible backup storage now covers setup steps, required permissions, and which configuration fields should not be changed after backups exist.
  - Why: To guide users on configuring and managing object storage profiles for backups and restores.
  - Detail: The documentation explains setting up S3-compatible storage profiles (AWS S3 or MinIO), details each configuration field, outlines required bucket policies, describes backup/restore interactions with the staging directory, and clarifies which settings may or may not be changed after backups exist.

- **Added documentation guiding S3-compatible storage usage, detailing supported providers and required bucket permissions for dbvault.** ([DBV-19](https://hoangluongtran0309.atlassian.net/browse/DBV-19)) — A new guide documents how to set up AWS S3 and MinIO with dbvault, listing the bucket permissions needed. There is no operational impact or risk as this is documentation only.
  - Why: Customers requested clarity on which S3-compatible storage providers are supported and the necessary bucket permissions for dbvault operation.
  - Detail: The new guide covers AWS S3 and MinIO, including an example of a minimal bucket policy required for access. No changes were made to application code or configuration—only documentation was updated.

## 🔧 Maintenance

- **The PostgreSQL Testcontainers integration test now covers a wider range of data types (`jsonb`, `timestamptz`, `bytea`, `numeric`), as well as an index, view, foreign key, and bigserial sequence continuation checks.** ([#26](https://github.com/hoangtulangtoc/dbvault/pull/26)) — No operational impact or risk; the change only extends test coverage for various PostgreSQL features and has no effect on deployment, rollback, or production monitoring.
  - Why: To make the test suite better reflect real-world database schemas and prevent regressions in these additional PostgreSQL features.
  - Detail: The test now round-trips data using more PostgreSQL data types and objects, and checks proper sequence advancement for bigserial columns after a restore. The PostgreSQL version used in tests matches the installed `pg_dump`. No production code is changed.

- **Dependabot alerts and automated version update configuration were enabled for the dbvault repository.** ([OPS-8](https://linear.app/dbvault/issue/OPS-8/enable-dependabot-alerts-and-version-updates-for-dbvault)) — Dependabot now automatically creates pull requests for dependency updates weekly (Maven and GitHub Actions) on the develop branch. Monitor new PRs for build/test outcomes and unexpected integration issues, but no immediate operational action is required.
  - Why: This was done to improve dependency management and keep the codebase up to date with security and compatibility fixes.
  - Detail: A dependabot.yml file was added to configure weekly updates for Maven dependencies and GitHub Actions workflows, all targeting the develop branch.

- **The startup client binary checks for MySQL and PostgreSQL were refactored into a shared check called ClientBinaries.require, and a small unit test was added for this component.** ([#29](https://github.com/hoangtulangtoc/dbvault/pull/29)) — No operational procedures change with this update. No increased risk or monitoring is needed; rollback is straightforward if required, as behavior remains the same.
  - Why: This was done to avoid code duplication and simplify maintaining checks for client binaries across different database engines.
  - Detail: Previously, MysqlBinaries and PostgresBinaries duplicated logic for ensuring required client binaries were available; now, both engines use a single ClientBinaries.require method. The existing adapters no longer contain duplicated code, and tests now directly cover the shared logic.

- **S3 integration tests no longer contain hard-coded credentials; test credentials are now injected via repository secrets or generated randomly during local runs, and the S3 emulator image is pinned by digest.** ([#30](https://github.com/hoangtulangtoc/dbvault/pull/30)) — Operationally, there is no impact on production or DR: only integration test credentials and S3 test images are affected, reducing the risk of credential leaks. No rollback or monitoring actions are required.
  - Why: To prevent credential leaks and ensure tests do not pass with invalid or stale credentials, improving security and reliability in CI and local development.
  - Detail: Repository secrets S3_TEST_ACCESS_KEY and S3_TEST_SECRET_KEY are used by the CI workflow. Locally, tests auto-generate credentials if none are provided. The emulator Docker image is referenced by digest, avoiding ambiguous updates from a tag.
