# Release v1.2.0

A retention preview, times in your own time zone, and a limit on failed sign-ins. The old restore-verification API path is gone: upgrade dbvault-cli together with the server.

## What's New

This release includes 11 changes: 1 breaking change, 1 security change, 4 new features, 4 bug fixes, and 1 documentation update.

> ⚠️ 1 breaking change requires action before upgrading: [read the upgrade guide](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md).

## ⚠️ Breaking Changes

- **Verification endpoint removed; CLI upgrade required with server** ([#58](https://github.com/hoangtulangtoc/dbvault/pull/58)) — Customers must upgrade dbvault-cli at the same time as the server, as the old verification endpoint is removed and older CLI versions will fail. Advise customers to use the new endpoint or follow the Location header from POST.
  - Required step: Upgrade dbvault-cli to the latest version at the same time as the server, as older versions use the removed endpoint.
  - Limitations: No deprecated period for the old endpoint; it is removed immediately.

## 🔒 Security

- **Sign-in lockout after repeated failures: what to tell customers** ([#60](https://github.com/hoangtulangtoc/dbvault/pull/60)) — If a customer is locked out after too many failed sign-ins, explain that the system blocks further attempts from their address for 15 minutes. Advise checking proxy settings if multiple users are affected, and recommend configuring the new environment variables or adding rate limiting at the reverse proxy.
  - Recommended step: We recommend also rate-limiting POST /login at your reverse proxy.
  - Limitations: Behind a proxy that doesn't forward the client address, every client shares one address and is locked out together. The counts live in memory, so a restart forgets them.

## ✨ New Features

- **dbvault backups now supports --limit to show only newest backups** ([!10](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/10)) — Advise customers to use --limit with dbvault backups if they want to see only the most recent backups for a target. This helps avoid long output when many backups exist.

- **Retention policy form now previews deletions before saving** ([#56](https://github.com/hoangtulangtoc/dbvault/pull/56)) — Customers can now use the Preview button in the retention policy form to see which backups would be deleted by a policy before saving. Advise them that no backups are deleted until Save is pressed.
  - Limitations: The preview only simulates deletion; actual changes occur only when Save is pressed.

- **Restore-verification API endpoint changed** ([#57](https://github.com/hoangtulangtoc/dbvault/pull/57)) — Restore-verification attempts are now read under their backup. The old path is removed in this release; see the breaking change for what customers must do.

- **Console now lets users pick a time zone for displayed times** ([#61](https://github.com/hoangtulangtoc/dbvault/pull/61)) — Users can select a time zone in the sidebar, and all times in the console will be shown in that zone. This helps avoid confusion for users outside UTC. The setting is browser-specific and does not affect API, CLI, or logs.

## 🐛 Bug Fixes

- **'verify' fails with exit code 4 if server is not 1.2.0+** ([!11](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/11)) — If customers report 'verify' failing, check that both the CLI and server are version 1.2.0 or later. Advise upgrading both together; no command-line changes are visible.
  - Required step: Upgrade both the dbvault server to 1.2.0 or later and this CLI version together to ensure 'verify' works.
  - Limitations: This CLI version is not compatible with dbvault server 1.1 or earlier for verification polling.

- **'dbvault verify' blocks zero or negative --timeout values** ([!12](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/12)) — If a customer reports an error with 'dbvault verify' and a zero or negative --timeout, explain that this is now a usage error and no verification is started. Advise them to use a positive timeout.

- **Backup sizes no longer show as 1024.0 KB** ([#62](https://github.com/hoangtulangtoc/dbvault/pull/62)) — If a customer asks about a size like 1024.0 KB, it now reads 1.0 MB; nothing about the backup changed.

- **Restores list now explains deleted source backups** ([#63](https://github.com/hoangtulangtoc/dbvault/pull/63)) — If customers ask about a dash in the Restores list, tell them it now says 'Backup deleted since' when the source backup was removed. No steps are needed.

## 📚 Documentation

- **New upgrade guide for 1.2 details API change impact** ([#59](https://github.com/hoangtulangtoc/dbvault/pull/59)) — Support can refer to the new guide for information on the removed verification endpoint and steps for affected users upgrading to 1.2.
