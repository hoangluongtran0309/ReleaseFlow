# Release v0.2.0

## What's New

This release includes 25 changes: 1 breaking change, 3 security changes, 11 new features, 3 bug fixes, 1 performance improvement, 2 documentation updates, and 4 maintenance changes.

> ⚠️ 1 breaking change requires action before upgrading.

## ⚠️ Breaking Changes

- **The CLI now requires the backup target ID to be provided with the --target-id option instead of as a positional argument.** ([!3](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/3)) — The CLI now requires --target-id for backup operations instead of a positional argument, to prevent confusion between name and ID. Update your code and scripts accordingly, as the positional form now triggers a usage error.
  - Why: Users were mistakenly passing the target's name instead of its ID as a positional argument, resulting in 'not found' errors. This change clarifies and enforces correct input.
  - Implementation: The previous positional argument for target ID in 'dbvault backups' has been replaced with an explicit --target-id flag. Using the old form results in a usage error with exit code 2.
  - Migration: Update any scripts or manual commands to use --target-id \<id> instead of passing the target ID positionally.

## 🔒 Security

- **Encryption of each target's password now uses a unique key derived from the master key via HKDF with the target's ID as context, instead of encrypting all passwords with the same key.** ([#25](https://github.com/hoangtulangtoc/dbvault/pull/25)) — Passwords are now encrypted per target using keys derived from the master key via HKDF-SHA256, with 'encryptFor' and 'decryptFor' methods added to EncryptionPort. Existing password values remain compatible and are migrated to per-target keys upon update.
  - Why: To prevent a security vulnerability where leaking one master key and any ciphertext exposed all targets' credentials (DBV-16).
  - Implementation: A new HKDF-SHA256 implementation derives per-target encryption keys. Old passwords decrypt as before and migrate to per-target keys on their next edit, while new passwords use the new format marked with a 'v2:' prefix. The master key or any per-target key alone can no longer decrypt all targets' credentials. `EncryptionPort` now provides 'encryptFor' and 'decryptFor', and all relevant flows use these methods.
  - Migration: No direct action is required; credentials migrate to the new key scheme individually on update.

- **A review was conducted on dbvault's credential encryption, covering encryption key management and exposure risks from database dumps.** ([OPS-6](https://linear.app/dbvault/issue/OPS-6/security-review-of-credential-encryption)) — A security review of credential encryption practices was completed, focusing on key management, derivation, and possible data exposure from dumps. No implementation or interface changes were made.
  - Why: To assess the security posture and identify areas for future improvement, with findings documented for the Hardening epic.
  - Implementation: The review examined master key handling, key derivation, nonce reuse, and what can be leaked if only a database dump is obtained, but did not include code changes.

- **Integration tests no longer carry hard-coded S3 access credentials in the source; instead, credentials are injected via repository secrets for CI, or randomly generated for local runs. The S3 emulator image is now pinned by digest rather than by tag.** ([OPS-7](https://linear.app/dbvault/issue/OPS-7/rotate-s3-credentials-used-by-integration-tests)) — Integration tests no longer include hard-coded S3 credentials; instead, credentials are injected from repository secrets in CI, or generated locally per test run. The S3 emulator image is now referenced by digest, improving both security and reproducibility. No changes are required for production code or API usage.
  - Why: This change prevents leaked or misused S3 credentials during testing, improves test security, and ensures that tests do not rely on shared, static credentials.
  - Implementation: Repository secrets S3_TEST_ACCESS_KEY and S3_TEST_SECRET_KEY are provided to the verify workflow for S3 integration tests. If these are not set (e.g., local development), each test run generates a new set of random credentials. Tests use an S3 emulator image that is now referenced by a fixed digest to avoid unintentional updates from a moving tag.

## ✨ New Features

- **Improvements were made to the interface, including clearer history and target tables, new sidebar shell with theme support and custom font, improved status badges, interactive progress on submit buttons, inline error rendering, and expanded detail on backup and restore statuses.** ([#11](https://github.com/hoangtulangtoc/dbvault/pull/11)) — UI tables and sidebar shell were refactored for clarity and usability, new typeface and theme support were added, and submit component logic now prevents double submissions and surfaces errors inline.
  - Why: These changes enhance usability, readability, and feedback within the user console, resolving confusion and an unfinished appearance.
  - Implementation: Table formatting was updated to ensure status badges are contained in their own cells. A sidebar shell with light/dark themes and a new typeface was introduced. Submit buttons now have progress indicators and are protected from multiple submissions. Errors display within the console interface, and target lists now summarize backup statuses with links to detailed operation status.

- **Editing a target's connection details is now supported.** ([#14](https://github.com/hoangtulangtoc/dbvault/pull/14)) — You can now edit the connection details for a target, enabling updates to its configuration. If you interact with the target management logic, be aware of changes regarding editable fields and UI hooks.
  - Why: This feature was added to allow users to update connection information for an existing target.
  - Implementation: A new capability has been introduced to modify the connection details associated with a configured target. Specific method names, APIs, or configuration parameters are not detailed.

- **The API now exposes backup checksums, including the recorded checksum value and the algorithm used.** ([#15](https://github.com/hoangtulangtoc/dbvault/pull/15)) — The API response schema for backup resources now includes checksum and algorithm fields. Ensure that any API clients or handlers parsing backup responses are updated to handle these new fields if needed.
  - Why: Users need to verify the integrity of downloaded backup artifacts by comparing checksums.
  - Implementation: The backup resource in the API now includes fields for the checksum (e.g., SHA-256) and the algorithm. API responses are updated accordingly, with no breaking changes to existing structure.

- **A new feature was added that allows backups to be restored into any registered target.** ([#16](https://github.com/hoangtulangtoc/dbvault/pull/16)) — You can now call the restore operation and specify any registered target, rather than being constrained to the original or default target.
  - Why: This enables greater flexibility by allowing users to choose the destination for a restore operation.
  - Implementation: The restore process is enhanced to support specifying any registered target as the destination for a backup restore, rather than being limited to a default or original target.

- **Paging was added to the backup and restore history feature.** ([#17](https://github.com/hoangtulangtoc/dbvault/pull/17)) — Pagination was implemented for the backup and restore history view, so update any custom queries or UIs that assume full history fetches.
  - Why: To improve the usability and handling of large history datasets for backup and restore operations.
  - Implementation: The history for backup and restore operations now supports pagination, allowing users to view data in chunks rather than loading the entire history at once.

- **Bulk deletion of multiple backups and removal of a backup target together with its associated backups were added. The confirmation page totals files, bytes, and restore records before deletion. Pre-checks ensure that deletions are refused if a backup is running or being restored. Removing a target without backups keeps the one-click dialog; removing a target with backups requires confirmation and typing its name.** ([#18](https://github.com/hoangtulangtoc/dbvault/pull/18)) — Bulk deletion APIs and UI have been implemented for backups, with atomic pre-checks for running or restoring, and enhanced confirmation flows. Target removal now supports confirmation-driven batch deletes. Review ADR-015 for rationale and details.
  - Why: This change improves usability and safety when managing backups, providing batch operations and stronger checks to prevent accidental disruption of active processes.
  - Implementation: Bulk operations check all items for running or restoring status before any deletion. A confirmation page summarizes the impact. Single backup deletion now blocks if the backup is being restored. Target removal with backups now requires user confirmation with the target's name.

- **Database connection tests, backups, and restores are now routed based on the target database engine, with support added for PostgreSQL alongside existing MySQL functionality. PostgreSQL adapters, UI support, runtime handling, migration coverage, integration tests, and updated documentation are included. Restores are limited to the original engine type.** ([#20](https://github.com/hoangtulangtoc/dbvault/pull/20)) — Connection tests, backups, and restores are now dispatched according to each target's database engine type, with new adapters and integration for PostgreSQL. The restore operation remains locked to the target's registered engine to prevent mismatches. Be aware that engine assignment is immutable post-registration, and ensure your integrations follow the updated routing for engine adapters.
  - Why: To enable support for PostgreSQL databases and to ensure backups and restores are handled according to the engine type, preventing incompatibility issues.
  - Implementation: Routing logic determines the appropriate engine adapter (MySQL or PostgreSQL) for connection tests, backups, and restores. The code backfills existing targets as MySQL, restricts restores to the registered engine, and delivers necessary tests and docs. A registered target's engine cannot be changed post-registration.
  - Migration: No specific migration required unless supporting PostgreSQL targets; the system restricts changes to a target's engine after registration.

- **Added support for Quartz backup schedules.** ([#21](https://github.com/hoangtulangtoc/dbvault/pull/21)) — Support for backup scheduling using Quartz has been introduced. Review interactions with the new scheduling logic and Quartz integration if you are modifying related scheduling systems.
  - Why: To provide enhanced scheduling capabilities for backups, as per specification DBV-12-quartz.
  - Implementation: The implementation introduces integration with Quartz for managing backup job schedules. Referenced spec is DBV-12-quartz, but technical specifics are limited in this summary.

- **Backup scheduling for targets is now possible using cron expressions with a selected time zone.** ([DBV-12](https://hoangluongtran0309.atlassian.net/browse/DBV-12)) — Support has been added to define backup schedules using cron expressions and time zones. Schedules persist across restarts, and if a scheduled run is missed due to downtime, it executes once on startup. Review usage of cron parsing and persistence logic as needed.
  - Why: To allow operators to automate backups according to custom timetables and ensure reliability even after restarts or downtime.
  - Implementation: Schedules for backups are stored persistently so they survive restarts. If a scheduled backup run is missed due to downtime, it will be triggered once the server starts again.

- **A new retention feature was added to keep only the newest N successful backups per target.** ([#22](https://github.com/hoangtulangtoc/dbvault/pull/22)) — A retention setting was introduced to keep only the newest N successful backups per target; failed backups do not count toward N and deletions are logged. If integrating, ensure the retention parameter is set correctly and check logging for deleted artifacts.
  - Why: To control storage growth by limiting the number of backups retained and discarding older ones.
  - Implementation: Failed backups are excluded from the N count, and deletion of old backup artifacts is logged.
  - Migration: Configure the retention setting to specify how many recent backups to keep, if desired. No other special migration needed.

- **Added support for S3-compatible storage profiles, allowing artifacts to be stored in any S3-compatible bucket via named profiles.** ([#23](https://github.com/hoangtulangtoc/dbvault/pull/23)) — Support was added for named S3-compatible storage profiles, letting you configure multiple endpoints (e.g., AWS S3, MinIO) by specifying encrypted credentials. You can test each profile before using it, and should update relevant integration code to reference the new profile abstraction.
  - Why: To give operators the flexibility to use different S3-compatible storage solutions (such as AWS S3 and MinIO) for artifact storage, and to enhance security by encrypting secret keys.
  - Implementation: The implementation supports multiple storage profiles, each referencing an S3-compatible endpoint with encrypted secret credentials. Profiles can be tested before use to ensure proper configuration.

## 🐛 Bug Fixes

- **The CLI now correctly prioritizes the DBVAULT_URL environment variable over the configuration file when determining which server to connect to.** ([!4](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/4)) — The CLI logic was updated so that DBVAULT_URL from the environment overrides the server address found in cli.properties. This aligns with common env-over-config loading patterns and allows scripts to redirect CLI invocations easily. No changes needed in consumer code.
  - Why: Previously, the CLI ignored the DBVAULT_URL variable, always using the server address from the config file even when the environment variable was set. This fix ensures expected environment variable precedence.
  - Implementation: DBVAULT_URL now takes precedence; if it's set, its value is used as the server address. If not, the CLI uses the address from ~/.config/dbvault/cli.properties, and if neither is set, it defaults to http://localhost:8090.

- **Local backup artifacts now use unique file paths for each backup execution to prevent collisions when multiple backups run at the same time for the same database.** ([#24](https://github.com/hoangtulangtoc/dbvault/pull/24)) — Backup files now use a per-target, per-execution directory structure, preventing timestamp collisions. If the destination already exists, the backup fails early. Old flat file paths still resolve as before. The new structure matches remote keys; see ADR-033 for rationale.
  - Why: Previously, two backups initiated in the same second for the same database could overwrite each other's files, causing data loss.
  - Implementation: Artifacts are now stored in the format BACKUP_DIR/\<target-id>/\<execution-id>/\<file>, matching existing remote key structures. The backup process now refuses to run if the local destination already exists, and it cleans up empty directories left behind after deletes. Legacy flat artifacts continue to be accessible.

- **The console now shows backup sizes in B, KB, MB, GB, or TB instead of a raw byte count.** ([#28](https://github.com/hoangtulangtoc/dbvault/pull/28))
  - Implementation: Sizes are formatted on the Backups list, a backup's page, and the delete confirmations. A new ByteSizes helper formats them for the console templates: below 1024 bytes the exact count (1023 B); from 1024 bytes up, units of 1024 with one decimal (1024 bytes is 1.0 KB, 61,301,298 bytes is 58.5 MB). A missing size still shows —. On a backup's page, the size's tooltip gives the exact byte count.

## ⚡ Performance

- **The restore process now streams gunzip output directly into the MySQL client rather than decompressing the archive to disk first.** ([#19](https://github.com/hoangtulangtoc/dbvault/pull/19)) — The restore workflow now streams gunzip output directly into the MySQL client, avoiding temporary extraction to disk; each pipe has a dedicated thread to avoid thread pool exhaustion on constrained hosts. No code changes are required for custom integrations, but be aware of new threading behavior and resource use.
  - Why: This change eliminates the need for free disk space equal to the size of the uncompressed archive, improving disk usage and efficiency during restores.
  - Implementation: Gunzip is executed in its own dedicated thread which feeds decompressed data to MySQL's stdin, also using dedicated threads for each pipe to prevent thread pool exhaustion. Truncated or failing sources terminate the client process promptly. This approach is elaborated in ADR-016.

## 📚 Documentation

- **Added documentation on configuring S3-compatible storage profiles for backups, including setup steps and policy requirements.** ([#27](https://github.com/hoangtulangtoc/dbvault/pull/27)) — Documentation now describes how to configure and use S3-compatible storage profiles, clarifying each profile field, required IAM policies, and profile mutability after backups are present.
  - Why: To guide users on configuring and managing object storage profiles for backups and restores.
  - Implementation: The documentation explains setting up S3-compatible storage profiles (AWS S3 or MinIO), details each configuration field, outlines required bucket policies, describes backup/restore interactions with the staging directory, and clarifies which settings may or may not be changed after backups exist.

- **Added documentation guiding S3-compatible storage usage, detailing supported providers and required bucket permissions for dbvault.** ([DBV-19](https://hoangluongtran0309.atlassian.net/browse/DBV-19)) — A documentation guide was added explaining supported S3-compatible storage providers and their required bucket permissions. No code changes; no developer action required.
  - Why: Customers requested clarity on which S3-compatible storage providers are supported and the necessary bucket permissions for dbvault operation.
  - Implementation: The new guide covers AWS S3 and MinIO, including an example of a minimal bucket policy required for access. No changes were made to application code or configuration—only documentation was updated.

## 🔧 Maintenance

- **The PostgreSQL Testcontainers integration test now covers a wider range of data types (`jsonb`, `timestamptz`, `bytea`, `numeric`), as well as an index, view, foreign key, and bigserial sequence continuation checks.** ([#26](https://github.com/hoangtulangtoc/dbvault/pull/26)) — The PostgreSQL Testcontainers test logic has been expanded to exercise and validate round-tripping of additional data types (jsonb, timestamptz, bytea, numeric), as well as database objects like indexes, views, and foreign keys, including checks for correct bigserial sequence continuation post-restore. No changes required for your code.
  - Why: To make the test suite better reflect real-world database schemas and prevent regressions in these additional PostgreSQL features.
  - Implementation: The test now round-trips data using more PostgreSQL data types and objects, and checks proper sequence advancement for bigserial columns after a restore. The PostgreSQL version used in tests matches the installed `pg_dump`. No production code is changed.

- **Dependabot alerts and automated version update configuration were enabled for the dbvault repository.** ([OPS-8](https://linear.app/dbvault/issue/OPS-8/enable-dependabot-alerts-and-version-updates-for-dbvault)) — Dependabot support is now enabled for the repository, with a new configuration file for weekly Maven and GitHub Actions version checks. This automates dependency upgrades and helps maintain security and compatibility.
  - Why: This was done to improve dependency management and keep the codebase up to date with security and compatibility fixes.
  - Implementation: A dependabot.yml file was added to configure weekly updates for Maven dependencies and GitHub Actions workflows, all targeting the develop branch.

- **The startup client binary checks for MySQL and PostgreSQL were refactored into a shared check called ClientBinaries.require, and a small unit test was added for this component.** ([#29](https://github.com/hoangtulangtoc/dbvault/pull/29)) — Startup binary checks for MySQL and PostgreSQL are now unified in ClientBinaries.require, eliminating duplicate code; a direct unit test for this utility was also added. No changes are required in other modules that use these checks.
  - Why: This was done to avoid code duplication and simplify maintaining checks for client binaries across different database engines.
  - Implementation: Previously, MysqlBinaries and PostgresBinaries duplicated logic for ensuring required client binaries were available; now, both engines use a single ClientBinaries.require method. The existing adapters no longer contain duplicated code, and tests now directly cover the shared logic.

- **S3 integration tests no longer contain hard-coded credentials; test credentials are now injected via repository secrets or generated randomly during local runs, and the S3 emulator image is pinned by digest.** ([#30](https://github.com/hoangtulangtoc/dbvault/pull/30)) — S3 credentials for integration tests are now sourced from repository secrets in CI or generated randomly for local runs, and the test S3 emulator image is pinned using an immutable digest. Update any local test scripts that rely on hard-coded keys.
  - Why: To prevent credential leaks and ensure tests do not pass with invalid or stale credentials, improving security and reliability in CI and local development.
  - Implementation: Repository secrets S3_TEST_ACCESS_KEY and S3_TEST_SECRET_KEY are used by the CI workflow. Locally, tests auto-generate credentials if none are provided. The emulator Docker image is referenced by digest, avoiding ambiguous updates from a tag.
