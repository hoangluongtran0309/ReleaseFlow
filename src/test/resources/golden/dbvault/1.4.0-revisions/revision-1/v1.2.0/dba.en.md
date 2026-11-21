# Release v1.2.0

A retention preview, times in your own time zone, and a limit on failed sign-ins. The old restore-verification API path is gone: upgrade dbvault-cli together with the server.

## What's New

This release includes 7 changes: 1 breaking change, 1 security change, 3 new features, and 2 bug fixes.

> ⚠️ 1 breaking change requires action before upgrading: [read the upgrade guide](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md).

## ⚠️ Breaking Changes

- **Verification API path changed; CLI upgrade required** ([#58](https://github.com/hoangtulangtoc/dbvault/pull/58)) — The verification endpoint changed, requiring dbvault-cli to be upgraded with the server. No impact on stored data or restore behavior, but CLI 1.1 and older will not work with the new server.
  - Required step: Upgrade dbvault-cli to the latest version at the same time as the server, as older versions use the removed endpoint.

## 🔒 Security

- **Sign-in lockout after repeated failures may affect access** ([#60](https://github.com/hoangtulangtoc/dbvault/pull/60)) — After 10 failed sign-ins from one address in 15 minutes, further attempts are blocked for 15 minutes. No changes to stored data or schema, but DBAs should ensure proxies forward client addresses to avoid accidental lockouts.

## ✨ New Features

- **dbvault-cli can now list only the newest backups with --limit** ([!10](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/10)) — You can now use --limit with dbvault backups to quickly view the most recent backups for a target, reducing output size when reviewing backup history. No changes to stored data or restore behavior.

- **Preview backup deletions before saving retention policy changes** ([#56](https://github.com/hoangtulangtoc/dbvault/pull/56)) — DBAs can now preview which backups a retention policy would delete before applying changes. This helps avoid unintended data loss; no backups are deleted until Save is pressed.

- **Console times can be shown in a chosen time zone** ([#61](https://github.com/hoangtulangtoc/dbvault/pull/61)) — You can now set the console to display times in your local time zone, making it easier to interpret backup and restore times. This does not affect stored data, schedules, or logs, which remain in UTC.

## 🐛 Bug Fixes

- **'verify' command requires dbvault server 1.2.0+ for verification polling** ([!11](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/11)) — After upgrading, ensure both the CLI and server are at least version 1.2.0 for verification to work. No changes to stored data or restore behavior.
  - Required step: Upgrade both the dbvault server to 1.2.0 or later and this CLI version together to ensure 'verify' works.

- **Restores list clarifies deleted backups** ([#63](https://github.com/hoangtulangtoc/dbvault/pull/63)) — When viewing restores, if the source backup was deleted, the UI now shows 'Backup deleted since' instead of a dash. No changes to stored data or restore behavior.

**Upgrade guide:** [Read before upgrading](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md)
