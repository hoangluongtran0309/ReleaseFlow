# Release v1.0.0

dbvault 1.0: backup metrics for Prometheus and Grafana, TLS required to every database target, a Vietnamese console, and JSON output in the CLI.

## What's New

This release includes 7 changes: 1 breaking change, 3 new features, 1 documentation update, and 2 maintenance changes.

> ⚠️ 1 breaking change requires action before upgrading: [read the upgrade guide](https://github.com/hoangtulangtoc/dbvault/blob/v1.0.0/docs/upgrading-to-1.0.md).

## ⚠️ Breaking Changes

- 🔒 Security: **Database target connections now require TLS encryption by default, with an explicit opt-out per target. Existing targets will fail to connect if their server does not support TLS after upgrade, unless explicitly configured otherwise. API, configuration, migration logic, test coverage, and documentation have been updated to reflect and enforce this new default requirement.** ([#44](https://github.com/hoangtulangtoc/dbvault/pull/44)) — All database connections now require TLS by default, so after deployment, monitor for connection failures, especially on legacy/unsecured targets. Roll back opt-out per target if a server does not support TLS. Review monitoring alerts for failed backups or connection tests due to enforced encryption.
  - Why: Previously, database credentials and dumps could be transmitted in plaintext due to default client behavior; this change enforces encryption to prevent data from crossing the network unencrypted, reducing the risk of credential exposure and interception.
  - Detail: A new field `tls_required` (default TRUE) is added to `database_targets`, enforced in API, configuration forms, backup/restore command lines (e.g., `--ssl-mode=REQUIRED` for MySQL, `PGSSLMODE=require` for PostgreSQL, `--tls` for MongoDB), and documented in ADR-041 and deployment docs. Tests are updated to require TLS where available. Opting out sets clients to prefer but not require TLS; warnings are shown where TLS is not enforced. Migration V14 sets up DB schema changes. Server certificate validation is not enforced yet except for MongoDB, which requires certificates match system CA.
  - Migration: After upgrade, DBAs must verify every target: servers must support TLS or each affected target must opt out by clearing 'Require TLS'. Targets that cannot establish TLS will fail connection tests and backups until resolved.

## ✨ New Features

- **All CLI commands now support a JSON output mode using the '--output json' or '-o json' flag, which returns API-equivalent JSON responses. The option is documented in the README and five additional tests were added.** ([!7](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/7)) — With the new '--output json' option, automation systems can directly parse dbvault CLI output. There are no changes to exit codes or failure handling, and rollbacks are not required.
  - Why: To enable easier integration with scripts and external tools that require machine-readable output, addressing user need for stable and parseable responses rather than formatted tables.
  - Detail: When JSON mode is specified, CLI commands emit indented JSON matching the server's /api/v1 responses (including field nulls and raw byte sizes). The 'targets' and 'backups' commands print arrays as received; 'verify' prints its result without a start line and preserves exit codes for scripting. Errors are still reported on stderr in text form. Invalid formats fail with exit code 2.

- **Prometheus metrics endpoint (/actuator/prometheus) was added, exposing detailed backup metrics per target, and an optional Docker Compose profile for Prometheus and Grafana with a pre-built dashboard for monitoring backups.** ([#43](https://github.com/hoangtulangtoc/dbvault/pull/43)) — Backup metrics are exposed for Prometheus at /actuator/prometheus, refreshed every 30s from the backup history asynchronously to avoid DB lag on scrapes. To enable full observability, use the 'observability' Docker Compose profile, create password files for Prometheus and Grafana, and confirm access with operator credentials. No ports are published by default. Monitor the new dashboard after deployment and disable the profile to roll back changes.
  - Why: To let operators and users monitor the status, performance, and failures of their database backups in real time using standard observability tools.
  - Detail: Metrics include timestamps and sizes of the most recent successful backups, duration, and failure counts over the past 24 hours, all refreshing from backup history on a background thread. Access uses operator credentials, and Grafana/Prometheus versions are fixed by digest for reproducibility. Setup instructions and rationale are documented.

- **The console can now be shown in English or Vietnamese, chosen per browser (a link in the sidebar and on the sign-in page, or `?lang=`), and kept in a cookie. This first part translates the navigation, sign-in, the Targets list and form, and the Backups list; the other pages still show English.** ([#45](https://github.com/hoangtulangtoc/dbvault/pull/45)) — The operational UI now supports English and Vietnamese, selected by browser. Language choices are managed via URL, browser setting, or cookie, and persist through operator sessions. No operational risk or rollback impact; observe that all main UI elements on landing and navigation pages display correct language.
  - Why: To address requests from Vietnamese customers for a localized user interface, allowing operators to use the console in Vietnamese or English as needed.
  - Detail: All user-facing text has moved to language property files, with a mechanism that ensures consistency between English and Vietnamese keys. Language preference is controlled by the 'dbvault_lang' cookie (HttpOnly, SameSite=Lax, one year), falls back to browser 'Accept-Language', and defaults to English. Translations cover navigation, sign-in, targets, backups listing, status badges, and pager. The language is not tied to user account but to the session/browser. English remains for HTTP API, CLI, logs, and notifications.

## 📚 Documentation

- **Added an upgrade guide documenting the steps needed to upgrade to version 1.0, including the new requirement for TLS on every target and various operational and rollback guidance.** ([#47](https://github.com/hoangtulangtoc/dbvault/pull/47)) — A comprehensive upgrade guide is now available for moving to 1.0, including step-by-step preparation, TLS checks for all database targets, monitoring migration results, verifying post-upgrade backups, and a procedure for rollback if issues arise.
  - Why: To help operators perform a safe and informed upgrade to version 1.0, particularly due to the new TLS requirement and other notable changes.
  - Detail: The new documentation (docs/upgrading-to-1.0.md) covers preparing for upgrade, performing the schema migration, post-upgrade validation, new features, and how to roll back to 0.3. It includes engine-specific checks for TLS capability and cautions about Flyway's one-way migrations.

## 🔧 Maintenance

- **A manually triggered workflow was added to announce and sign an already published ReleaseFlow release in CI.** ([#42](https://github.com/hoangtulangtoc/dbvault/pull/42)) — A signed, manually triggered workflow now securely announces ReleaseFlow releases via CI. Secrets are managed in GitHub Actions. Monitor workflow runs for failures; no rollback or manual migrations are required.
  - Why: To support securely announcing published releases and verifying their status within the CI process.
  - Detail: The workflow is manually dispatched, signs the ReleaseFlow webhook request, follows the returned status path with a signed GET, and fails if the run ends unsuccessfully. Secrets and configuration are handled via GitHub Actions configuration, not committed to the repository.

- **A third-party license report is now generated during the build and included in the application JAR as 'THIRD-PARTY.txt'. The report lists every bundled third-party library and its declared license, with dependencies lacking license information causing the build to fail. Documentation has been updated to explain this report.** ([#46](https://github.com/hoangtulangtoc/dbvault/pull/46)) — Deployment now includes 'THIRD-PARTY.txt' in the application JAR, listing all bundled third-party licenses. There is no effect on runtime operation or rollback; builds will fail if any dependency's license information is missing.
  - Why: To provide transparency and legal clarity about the open-source libraries bundled in the application, ensuring compliance and making it easy for users to audit licenses.
  - Detail: The 'license-maven-plugin' generates the report at build time using dependency POM information, covering runtime and compile dependencies (excluding project modules and Lombok). A template removes unwanted entries, and documentation ('docs/licences.md', 'NOTICE') has been updated to explain coverage and exclusions. The build fails if any included dependency has no license.

**Upgrade guide:** [Read before upgrading](https://github.com/hoangtulangtoc/dbvault/blob/v1.0.0/docs/upgrading-to-1.0.md)
