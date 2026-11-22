# Release v1.2.0

A retention preview, times in your own time zone, and a limit on failed sign-ins. The old restore-verification API path is gone: upgrade dbvault-cli together with the server.

## What's New

This release includes 11 changes: 1 breaking change, 1 security change, 4 new features, 4 bug fixes, and 1 documentation update.

> ⚠️ 1 breaking change requires action before upgrading: [read the upgrade guide](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md).

## ⚠️ Breaking Changes

- **Verification API endpoint removed; update CLI and scripts to new path** ([#58](https://github.com/hoangtulangtoc/dbvault/pull/58)) — The endpoint GET /api/v1/verifications/{id} has been removed and now returns 404. Customers using dbvault-cli must upgrade it together with the server, as older CLI versions use the old path. Scripts should follow the Location header from POST or switch to the new endpoint.
  - What customers must do: Upgrade dbvault-cli to the latest version at the same time as the server, as older versions use the removed endpoint.
  - Limitations: No deprecated period for the old endpoint; it is removed immediately.

## 🔒 Security

- **Sign-in attempts are now rate-limited by address after repeated failures** ([#60](https://github.com/hoangtulangtoc/dbvault/pull/60)) — After 10 failed sign-ins from one address in 15 minutes, dbvault blocks further attempts from that address for 15 minutes. Customers will see an error message or HTTP 429. Advise customers to check their network setup if they are unexpectedly locked out, especially if behind a proxy. Suggest configuring the new environment variables and adding rate limiting at the reverse proxy.
  - What to recommend to customers: We recommend also rate-limiting POST /login at your reverse proxy.
  - Limitations: Behind a proxy that doesn't forward the client address, every client shares one address and is locked out together. The counts live in memory, so a restart forgets them.

## ✨ New Features

- **New --limit option for listing only newest backups in dbvault-cli** ([!10](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/10)) — Customers can now use --limit with dbvault backups to see only the most recent backups for a target, making it easier to find recent backups without scrolling through long histories.

- **Retention policy form now previews which backups would be deleted** ([#56](https://github.com/hoangtulangtoc/dbvault/pull/56)) — Support can inform customers that the retention policy form now offers a Preview button. This lets users see which backups would be deleted by a policy before saving, helping prevent accidental data loss. No backups are deleted until Save is pressed.
  - Limitations: The preview only simulates deletion; actual changes occur only when Save is pressed.

- **New API path for restore-verification attempts** ([#57](https://github.com/hoangtulangtoc/dbvault/pull/57)) — Restore-verification attempts are now read under their backup. The old path is removed in this release; see the breaking change for what customers must do.

- **Console now supports user-selected time zones for displayed times** ([#61](https://github.com/hoangtulangtoc/dbvault/pull/61)) — Customers can now choose their preferred time zone in the console sidebar, and all times will be shown accordingly. This helps users avoid manual time conversions. The setting is browser-specific and does not affect API, CLI, or logs, which remain in UTC.

## 🐛 Bug Fixes

- **dbvault-cli 'verify' now requires server 1.2.0+ for verification polling** ([!11](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/11)) — Customers using the 'verify' command must upgrade both the CLI and the server to version 1.2.0 or later. If the server is not upgraded, 'verify' will fail with exit code 4. No changes are visible in the command's options or output.
  - What customers must do: Upgrade both the dbvault server to 1.2.0 or later and this CLI version together to ensure 'verify' works.
  - Limitations: This CLI version is not compatible with dbvault server 1.1 or earlier for verification polling.

- **'dbvault verify' now rejects zero or negative --timeout values** ([!12](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/12)) — Customers who use 'dbvault verify' with a zero or negative --timeout will now see an error and no verification will be started. No action is needed unless they were using such values.

- **Backup sizes no longer show as 1024.0 KB** ([#62](https://github.com/hoangtulangtoc/dbvault/pull/62)) — A size just under 1 MB now reads 1.0 MB; nothing about the backup changed.

- **Restores list now shows when a source backup was deleted** ([#63](https://github.com/hoangtulangtoc/dbvault/pull/63)) — Customers will now see 'Backup deleted since' if a restore's source backup has been deleted, instead of a dash. This clarifies that the backup was removed and is not a loading error. No action is required.

## 📚 Documentation

- **Upgrade guide for version 1.2 added to documentation** ([#59](https://github.com/hoangtulangtoc/dbvault/pull/59)) — A new guide explains how to upgrade to 1.2, including the removal of a specific API endpoint. Customers using scripts or dbvault-cli 1.1 verify may be affected.

**Upgrade guide:** [Read before upgrading](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md)
