# Release v1.0.0

dbvault 1.0: backup metrics for Prometheus and Grafana, TLS required to every database target, a Vietnamese console, and JSON output in the CLI.

## What's New

This release includes 7 changes: 1 breaking change, 3 new features, 1 documentation update, and 2 maintenance changes.

> ⚠️ 1 breaking change requires action before upgrading: [read the upgrade guide](https://github.com/hoangtulangtoc/dbvault/blob/v1.0.0/docs/upgrading-to-1.0.md).

## ⚠️ Breaking Changes

- 🔒 Security: **Database target connections now require TLS encryption by default, with an explicit opt-out per target. Existing targets will fail to connect if their server does not support TLS after upgrade, unless explicitly configured otherwise. API, configuration, migration logic, test coverage, and documentation have been updated to reflect and enforce this new default requirement.** ([#44](https://github.com/hoangtulangtoc/dbvault/pull/44))
  - Migration: After upgrade, DBAs must verify every target: servers must support TLS or each affected target must opt out by clearing 'Require TLS'. Targets that cannot establish TLS will fail connection tests and backups until resolved.

## ✨ New Features

- **All CLI commands now support a JSON output mode using the '--output json' or '-o json' flag, which returns API-equivalent JSON responses. The option is documented in the README and five additional tests were added.** ([!7](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/7))

- **Prometheus metrics endpoint (/actuator/prometheus) was added, exposing detailed backup metrics per target, and an optional Docker Compose profile for Prometheus and Grafana with a pre-built dashboard for monitoring backups.** ([#43](https://github.com/hoangtulangtoc/dbvault/pull/43))

- **The console can now be shown in English or Vietnamese, chosen per browser (a link in the sidebar and on the sign-in page, or `?lang=`), and kept in a cookie. This first part translates the navigation, sign-in, the Targets list and form, and the Backups list; the other pages still show English.** ([#45](https://github.com/hoangtulangtoc/dbvault/pull/45))

## 📚 Documentation

- **Added an upgrade guide documenting the steps needed to upgrade to version 1.0, including the new requirement for TLS on every target and various operational and rollback guidance.** ([#47](https://github.com/hoangtulangtoc/dbvault/pull/47))

## 🔧 Maintenance

- **A manually triggered workflow was added to announce and sign an already published ReleaseFlow release in CI.** ([#42](https://github.com/hoangtulangtoc/dbvault/pull/42))

- **A third-party license report is now generated during the build and included in the application JAR as 'THIRD-PARTY.txt'. The report lists every bundled third-party library and its declared license, with dependencies lacking license information causing the build to fail. Documentation has been updated to explain this report.** ([#46](https://github.com/hoangtulangtoc/dbvault/pull/46))
