# Release v0.2.0

## What's New

This release includes 25 changes: 1 breaking change, 3 security changes, 11 new features, 3 bug fixes, 1 performance improvement, 2 documentation updates, and 4 maintenance changes.

> ⚠️ 1 breaking change requires action before upgrading.

## ⚠️ Breaking Changes

- **The CLI now requires the backup target ID to be provided with the --target-id option instead of as a positional argument.** — You now need to specify the backup target using --target-id when using the backup command. If you use the old way, it will show an error instead of guessing what you meant.

## 🔒 Security

- **Encryption of each target's password now uses a unique key derived from the master key via HKDF with the target's ID as context, instead of encrypting all passwords with the same key.** — Your passwords are now better protected, as each one has its own encryption key. You don't need to do anything, and your data remains accessible.

- **A review was conducted on dbvault's credential encryption, covering encryption key management and exposure risks from database dumps.** — There are no changes affecting how you use the product in this update.

- **Integration tests no longer carry hard-coded S3 access credentials in the source; instead, credentials are injected via repository secrets for CI, or randomly generated for local runs. The S3 emulator image is now pinned by digest rather than by tag.** — You will not notice any difference when using the product; this update only changes how some internal tests work.

## ✨ New Features

- **Improvements were made to the interface, including clearer history and target tables, new sidebar shell with theme support and custom font, improved status badges, interactive progress on submit buttons, inline error rendering, and expanded detail on backup and restore statuses.** — You'll see a clearer, easier-to-read console, with themes, improved tables, and better feedback when actions are in progress or errors happen.

- **Editing a target's connection details is now supported.** — You can now update the connection details of an existing target directly from the interface.

- **The API now exposes backup checksums, including the recorded checksum value and the algorithm used.** — You can now see a checksum with each backup, letting you verify that your downloaded files are complete and unchanged.

- **A new feature was added that allows backups to be restored into any registered target.** — You can now restore a backup to any of your saved locations, not just the original one.

- **Paging was added to the backup and restore history feature.** — You can now view your backup and restore history page by page, so it is easier to find recent records without loading everything at once.

- **Bulk deletion of multiple backups and removal of a backup target together with its associated backups were added. The confirmation page totals files, bytes, and restore records before deletion. Pre-checks ensure that deletions are refused if a backup is running or being restored. Removing a target without backups keeps the one-click dialog; removing a target with backups requires confirmation and typing its name.** — You can now delete several backups at once, and remove a backup target together with all its backups if you confirm. The system warns you and blocks deletes if any backup is running or being restored.

- **Database connection tests, backups, and restores are now routed based on the target database engine, with support added for PostgreSQL alongside existing MySQL functionality. PostgreSQL adapters, UI support, runtime handling, migration coverage, integration tests, and updated documentation are included. Restores are limited to the original engine type.** — You can now use the service with PostgreSQL databases as well as MySQL, with the system making sure your backups and restores happen correctly for your chosen database type.

- **Added support for Quartz backup schedules.** — You can now schedule backups using the new system, making it easier to manage when backups happen.

- **Backup scheduling for targets is now possible using cron expressions with a selected time zone.** — You can now set up regular automatic backups by choosing a schedule and time zone. If the system was off and missed a backup, it will make up for it when it starts again.

- **A new retention feature was added to keep only the newest N successful backups per target.** — Now, the system can limit how many recent backups are saved, so older backups are automatically removed and storage is kept under control.

- **Added support for S3-compatible storage profiles, allowing artifacts to be stored in any S3-compatible bucket via named profiles.** — You can now choose where your files are stored and use different S3-compatible options like AWS S3 or MinIO. Your secret keys are stored securely and can be tested before files are uploaded.

## 🐛 Bug Fixes

- **The CLI now correctly prioritizes the DBVAULT_URL environment variable over the configuration file when determining which server to connect to.** — If you set the DBVAULT_URL environment variable, the CLI will now correctly connect to the server you specify there, instead of always using the one in its config file.

- **Local backup artifacts now use unique file paths for each backup execution to prevent collisions when multiple backups run at the same time for the same database.** — When creating multiple backups at once, each backup now gets its own file so they won't overwrite each other.

- **The console now shows backup sizes in B, KB, MB, GB, or TB instead of a raw byte count.**

## ⚡ Performance

- **The restore process now streams gunzip output directly into the MySQL client rather than decompressing the archive to disk first.** — Restores now need less disk space and are faster, as backups are unpacked while importing, not before.

## 📚 Documentation

- **Added documentation on configuring S3-compatible storage profiles for backups, including setup steps and policy requirements.** — There are new instructions showing how to store your backups in S3 or MinIO and what is needed to set this up.

- **Added documentation guiding S3-compatible storage usage, detailing supported providers and required bucket permissions for dbvault.** — The documentation now explains which S3-compatible storage services work with dbvault and the permissions needed for setup.

## 🔧 Maintenance

- **The PostgreSQL Testcontainers integration test now covers a wider range of data types (`jsonb`, `timestamptz`, `bytea`, `numeric`), as well as an index, view, foreign key, and bigserial sequence continuation checks.** — This update does not affect how you use the product and does not change any user-facing features.

- **Dependabot alerts and automated version update configuration were enabled for the dbvault repository.** — You will not notice any changes in how the product works, but the team is improving how they keep underlying software up to date.

- **The startup client binary checks for MySQL and PostgreSQL were refactored into a shared check called ClientBinaries.require, and a small unit test was added for this component.** — There are no visible changes; everything works the same as before.

- **S3 integration tests no longer contain hard-coded credentials; test credentials are now injected via repository secrets or generated randomly during local runs, and the S3 emulator image is pinned by digest.** — There are no user-visible changes; this update only improves how developers run tests internally.
