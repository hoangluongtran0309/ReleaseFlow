# Release v0.1.0

MySQL MVP

## What's New

This release includes 15 changes: 1 security change, 6 new features, 1 performance improvement, 2 documentation updates, and 5 maintenance changes.

## 🔒 Security

- **single operator sign-in with CSRF protection (DBV-6)** ([#12](https://github.com/hoangtulangtoc/dbvault/pull/12))

## ✨ New Features

- **register and list MySQL targets (DBV-1)** ([#4](https://github.com/hoangtulangtoc/dbvault/pull/4))

- **test the connection to a MySQL target (DBV-2)** ([#5](https://github.com/hoangtulangtoc/dbvault/pull/5))

- **run a full logical MySQL backup (DBV-3)** ([#6](https://github.com/hoangtulangtoc/dbvault/pull/6))

- **restore a backup after explicit confirmation (DBV-5)** ([#8](https://github.com/hoangtulangtoc/dbvault/pull/8))

- **Delete backup artifacts from history** ([#9](https://github.com/hoangtulangtoc/dbvault/pull/9))

- **list targets and backups (DBV-8)** ([!2](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/2))

## ⚡ Performance

- **gzip the dump while it is written (DBV-4)** ([#7](https://github.com/hoangtulangtoc/dbvault/pull/7))

## 📚 Documentation

- **add README, branching model and contributing guide** ([#3](https://github.com/hoangtulangtoc/dbvault/pull/3))

- **DBV-35: Publish the dbvault onboarding checklist for customers** ([DBV-35](https://hoangluongtran0309.atlassian.net/browse/DBV-35))

## 🔧 Maintenance

- **bootstrap multi-module Maven build** ([#1](https://github.com/hoangtulangtoc/dbvault/pull/1))

- **add Maven verify and workflow lint** ([#2](https://github.com/hoangtulangtoc/dbvault/pull/2))

- **bootstrap dbvault-cli with picocli** ([!1](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/1))

- **ship Docker image and compose quickstart (DBV-7)** ([#10](https://github.com/hoangtulangtoc/dbvault/pull/10))

- **publish the image on tags and attach the jar to releases** ([#13](https://github.com/hoangtulangtoc/dbvault/pull/13))
