## What changes

<!-- What a reviewer or a user would notice, in a few sentences. -->

## Why

<!-- The problem this solves. Link the issue if there is one: Closes #... -->

## Checklist

- [ ] The pull request targets `develop` (or `main`, for a `release/` or `hotfix/` branch) and its title follows the commit convention in `CONTRIBUTING.md`
- [ ] `./mvnw verify` passes with JDK 21
- [ ] A changed page keeps `npm run e2e` green, accessibility included
- [ ] A changed guide keeps `npm --prefix website run build` green, and its Vietnamese translation changed with it
- [ ] Every new or changed wording is in both `messages/ui.properties` and `messages/ui_vi.properties`
- [ ] `CHANGELOG.md` has an entry under `[Unreleased]`, and the guide in `docs/`, `docs/architecture.md`, and `docs/implementation-status.md` match the new behavior
- [ ] A new decision is recorded as the next ADR and listed in `docs/adr/README.md`
- [ ] No credentials, tokens, or generated files in the diff
