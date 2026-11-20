# Release v1.2.0

A retention preview, times in your own time zone, and a limit on failed sign-ins. The old restore-verification API path is gone: upgrade dbvault-cli together with the server.

## What's New

This release includes 9 changes: 1 breaking change, 1 security change, 3 new features, and 4 bug fixes.

> ⚠️ 1 breaking change requires action before upgrading: [read the upgrade guide](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md).

## ⚠️ Breaking Changes

- **The API endpoint GET /api/v1/verifications/{id} was removed and now returns 404; use GET /api/v1/backups/{backupId}/verifications/{id} instead.**
  - Required step: Upgrade dbvault-cli to the latest version at the same time as the server, as older versions use the removed endpoint.

## 🔒 Security

- **Too many failed sign-ins will temporarily block your address** — If you enter the wrong password too many times, you will need to wait 15 minutes before trying again. This helps protect your account.

## ✨ New Features

- **See only the newest backups with the new --limit option** — You can now use --limit to show just the latest backups for a database, making it easier to find recent backups.

- **See which backups would be deleted before saving retention changes** — You can now preview which backups a retention rule would delete before saving it. No backups are deleted until you confirm the change.

- **See times in your own time zone in the console** — You can now pick your time zone in the sidebar, and all times in the console will be shown in that zone. This makes it easier to read backup and restore times.

## 🐛 Bug Fixes

- **'verify' command now requires newer server version** — To use the 'verify' command, both the CLI and server must be updated to version 1.2.0 or later. The command's usage and output remain the same.
  - Required step: Upgrade both the dbvault server to 1.2.0 or later and this CLI version together to ensure 'verify' works.

- **Error shown for zero or negative --timeout in 'dbvault verify'** — If you set --timeout to zero or a negative value, you will now see an error and the verification will not start.

- **Backup sizes no longer show as 1024.0 KB** — A backup just under 1 MB now shows as 1.0 MB.

- **Restores page now shows when a backup was deleted** — If a restore's backup was deleted, you'll now see 'Backup deleted since' instead of a dash. This helps you understand why the backup is missing.
