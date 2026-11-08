# Release v0.1.0

MySQL MVP

## What's New

This release includes 15 changes: 1 security change, 6 new features, 1 performance improvement, 2 documentation updates, and 5 maintenance changes.

## 🔒 Security

- **single operator sign-in with CSRF protection (DBV-6)**

## ✨ New Features

- **register and list MySQL targets (DBV-1)**

- **test the connection to a MySQL target (DBV-2)**

- **run a full logical MySQL backup (DBV-3)**

- **restore a backup after explicit confirmation (DBV-5)**

- **Delete backup artifacts from history**

- **list targets and backups (DBV-8)**

## ⚡ Performance

- **gzip the dump while it is written (DBV-4)**

## 📚 Documentation

- **add README, branching model and contributing guide**

- **DBV-35: Publish the dbvault onboarding checklist for customers**

## 🔧 Maintenance

- **bootstrap multi-module Maven build**

- **add Maven verify and workflow lint**

- **bootstrap dbvault-cli with picocli**

- **ship Docker image and compose quickstart (DBV-7)**

- **publish the image on tags and attach the jar to releases**
