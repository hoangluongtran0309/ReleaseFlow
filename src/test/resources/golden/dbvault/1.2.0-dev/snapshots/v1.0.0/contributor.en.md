# Release v1.0.0

dbvault 1.0: backup metrics for Prometheus and Grafana, TLS required to every database target, a Vietnamese console, and JSON output in the CLI.

## What's New

This release includes 7 changes: 1 breaking change, 3 new features, 1 documentation update, and 2 maintenance changes.

> ⚠️ 1 breaking change requires action before upgrading: [read the upgrade guide](https://github.com/hoangtulangtoc/dbvault/blob/v1.0.0/docs/upgrading-to-1.0.md).

## ⚠️ Breaking Changes

- 🔒 Security: **Database target connections now require TLS encryption by default, with an explicit opt-out per target. Existing targets will fail to connect if their server does not support TLS after upgrade, unless explicitly configured otherwise. API, configuration, migration logic, test coverage, and documentation have been updated to reflect and enforce this new default requirement.** ([#44](https://github.com/hoangtulangtoc/dbvault/pull/44)) — Database target connection logic now defaults to requiring TLS: this is enforced by setting corresponding flags or environment variables for supported clients, introducing a boolean `tls_required` field to target configs (default TRUE), with logic and migration, API extension, UI warnings, and documentation updates. Per-target opt-out is handled both on the backend and UI, with integration tests updated accordingly.
  - Why: Previously, database credentials and dumps could be transmitted in plaintext due to default client behavior; this change enforces encryption to prevent data from crossing the network unencrypted, reducing the risk of credential exposure and interception.
  - Implementation: A new field `tls_required` (default TRUE) is added to `database_targets`, enforced in API, configuration forms, backup/restore command lines (e.g., `--ssl-mode=REQUIRED` for MySQL, `PGSSLMODE=require` for PostgreSQL, `--tls` for MongoDB), and documented in ADR-041 and deployment docs. Tests are updated to require TLS where available. Opting out sets clients to prefer but not require TLS; warnings are shown where TLS is not enforced. Migration V14 sets up DB schema changes. Server certificate validation is not enforced yet except for MongoDB, which requires certificates match system CA.
  - Migration: After upgrade, DBAs must verify every target: servers must support TLS or each affected target must opt out by clearing 'Require TLS'. Targets that cannot establish TLS will fail connection tests and backups until resolved.

## ✨ New Features

- **All CLI commands now support a JSON output mode using the '--output json' or '-o json' flag, which returns API-equivalent JSON responses. The option is documented in the README and five additional tests were added.** ([!7](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/7)) — You can now pass '--output json' to any CLI command to receive JSON matching the server's API output, simplifying integration and automated testing. Check the updated README for usage, and see ServerCommandsTest for examples.
  - Why: To enable easier integration with scripts and external tools that require machine-readable output, addressing user need for stable and parseable responses rather than formatted tables.
  - Implementation: When JSON mode is specified, CLI commands emit indented JSON matching the server's /api/v1 responses (including field nulls and raw byte sizes). The 'targets' and 'backups' commands print arrays as received; 'verify' prints its result without a start line and preserves exit codes for scripting. Errors are still reported on stderr in text form. Invalid formats fail with exit code 2.

- **Prometheus metrics endpoint (/actuator/prometheus) was added, exposing detailed backup metrics per target, and an optional Docker Compose profile for Prometheus and Grafana with a pre-built dashboard for monitoring backups.** ([#43](https://github.com/hoangtulangtoc/dbvault/pull/43)) — A new Prometheus metrics endpoint serves backup statistics, which are calculated in a background thread from the execution history and exposed at /actuator/prometheus. There is also an optional Docker Compose profile to provision Prometheus and Grafana, with documented setup in docs/deployment.md. Test coverage includes metrics exposure and authentication.
  - Why: To let operators and users monitor the status, performance, and failures of their database backups in real time using standard observability tools.
  - Implementation: Metrics include timestamps and sizes of the most recent successful backups, duration, and failure counts over the past 24 hours, all refreshing from backup history on a background thread. Access uses operator credentials, and Grafana/Prometheus versions are fixed by digest for reproducibility. Setup instructions and rationale are documented.

- **The console can now be shown in English or Vietnamese, chosen per browser (a link in the sidebar and on the sign-in page, or `?lang=`), and kept in a cookie. This first part translates the navigation, sign-in, the Targets list and form, and the Backups list; the other pages still show English.** ([#45](https://github.com/hoangtulangtoc/dbvault/pull/45)) — You can now add or update UI text for both English and Vietnamese by editing the respective 'messages.properties' files, ensuring all keys are present in both. Automatic tests enforce key parity. The language is set per browser (using a URL parameter, a cookie, or Accept-Language) instead of per operator account.
  - Why: To address requests from Vietnamese customers for a localized user interface, allowing operators to use the console in Vietnamese or English as needed.
  - Implementation: All user-facing text has moved to language property files, with a mechanism that ensures consistency between English and Vietnamese keys. Language preference is controlled by the 'dbvault_lang' cookie (HttpOnly, SameSite=Lax, one year), falls back to browser 'Accept-Language', and defaults to English. Translations cover navigation, sign-in, targets, backups listing, status badges, and pager. The language is not tied to user account but to the session/browser. English remains for HTTP API, CLI, logs, and notifications.

## 📚 Documentation

- **Added an upgrade guide documenting the steps needed to upgrade to version 1.0, including the new requirement for TLS on every target and various operational and rollback guidance.** ([#47](https://github.com/hoangtulangtoc/dbvault/pull/47)) — An upgrade guide for version 1.0 has been added to the documentation, detailing operational steps and schema changes relevant for the upgrade process, but no implementation or API code changes were made.
  - Why: To help operators perform a safe and informed upgrade to version 1.0, particularly due to the new TLS requirement and other notable changes.
  - Implementation: The new documentation (docs/upgrading-to-1.0.md) covers preparing for upgrade, performing the schema migration, post-upgrade validation, new features, and how to roll back to 0.3. It includes engine-specific checks for TLS capability and cautions about Flyway's one-way migrations.

## 🔧 Maintenance

- **A manually triggered workflow was added to announce and sign an already published ReleaseFlow release in CI.** ([#42](https://github.com/hoangtulangtoc/dbvault/pull/42)) — A new GitHub Actions workflow was introduced to manually announce a completed ReleaseFlow release, signing requests and status checks. No code changes required for contributors unless they contribute to the CI pipeline itself.
  - Why: To support securely announcing published releases and verifying their status within the CI process.
  - Implementation: The workflow is manually dispatched, signs the ReleaseFlow webhook request, follows the returned status path with a signed GET, and fails if the run ends unsuccessfully. Secrets and configuration are handled via GitHub Actions configuration, not committed to the repository.

- **A third-party license report is now generated during the build and included in the application JAR as 'THIRD-PARTY.txt'. The report lists every bundled third-party library and its declared license, with dependencies lacking license information causing the build to fail. Documentation has been updated to explain this report.** ([#46](https://github.com/hoangtulangtoc/dbvault/pull/46)) — A Maven build now generates 'THIRD-PARTY.txt' in the application JAR, listing all included third-party dependencies and their declared licenses, failing the build if any license is missing. Project modules and Lombok are excluded; consult 'docs/licences.md' for coverage and implementation notes.
  - Why: To provide transparency and legal clarity about the open-source libraries bundled in the application, ensuring compliance and making it easy for users to audit licenses.
  - Implementation: The 'license-maven-plugin' generates the report at build time using dependency POM information, covering runtime and compile dependencies (excluding project modules and Lombok). A template removes unwanted entries, and documentation ('docs/licences.md', 'NOTICE') has been updated to explain coverage and exclusions. The build fails if any included dependency has no license.

**Upgrade guide:** [Read before upgrading](https://github.com/hoangtulangtoc/dbvault/blob/v1.0.0/docs/upgrading-to-1.0.md)
