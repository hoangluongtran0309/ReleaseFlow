# Release v1.0.0

dbvault 1.0: backup metrics for Prometheus and Grafana, TLS required to every database target, a Vietnamese console, and JSON output in the CLI.

## What's New

This release includes 7 changes: 1 breaking change, 3 new features, 1 documentation update, and 2 maintenance changes.

> ⚠️ 1 breaking change requires action before upgrading: [read the upgrade guide](https://github.com/hoangtulangtoc/dbvault/blob/v1.0.0/docs/upgrading-to-1.0.md).

## ⚠️ Breaking Changes

- 🔒 Security: **Database target connections now require TLS encryption by default, with an explicit opt-out per target. Existing targets will fail to connect if their server does not support TLS after upgrade, unless explicitly configured otherwise. API, configuration, migration logic, test coverage, and documentation have been updated to reflect and enforce this new default requirement.** — Now, your database connections will always be encrypted unless you manually turn this off for a specific server. If a server can’t support encrypted connections, it will stop working until you update its settings.

## ✨ New Features

- **All CLI commands now support a JSON output mode using the '--output json' or '-o json' flag, which returns API-equivalent JSON responses. The option is documented in the README and five additional tests were added.** — You can now ask the command-line tool to print information in JSON, which is useful if you are using scripts or other programs to read the results. The normal table output remains the default.

- **Prometheus metrics endpoint (/actuator/prometheus) was added, exposing detailed backup metrics per target, and an optional Docker Compose profile for Prometheus and Grafana with a pre-built dashboard for monitoring backups.** — You can now see up-to-date statistics about your backups, including whether any have failed or how long they take, using monitoring tools like Grafana.

- **The console can now be shown in English or Vietnamese, chosen per browser (a link in the sidebar and on the sign-in page, or `?lang=`), and kept in a cookie. This first part translates the navigation, sign-in, the Targets list and form, and the Backups list; the other pages still show English.** — You can now switch the console to Vietnamese from the sidebar or the sign-in page. The main pages (targets, backups, sign-in) are translated first; the remaining pages still show English and will follow.

## 📚 Documentation

- **Added an upgrade guide documenting the steps needed to upgrade to version 1.0, including the new requirement for TLS on every target and various operational and rollback guidance.** — There is now an upgrade guide with steps to help you move to version 1.0, explaining what's changed and anything you'll need to do.

## 🔧 Maintenance

- **A manually triggered workflow was added to announce and sign an already published ReleaseFlow release in CI.** — No visible changes will occur during normal use.

- **A third-party license report is now generated during the build and included in the application JAR as 'THIRD-PARTY.txt'. The report lists every bundled third-party library and its declared license, with dependencies lacking license information causing the build to fail. Documentation has been updated to explain this report.** — The application now documents which third-party libraries it contains and the licenses they use, but you will not see any difference in how the application works.
