# ADR-0032: Documentation site

- Status: Accepted
- Date: 2026-10-12
- Builds on: [ADR-0007](0007-ci-and-container-supply-chain.md), whose pinning rules the
  site's workflow follows, and [ADR-0030](0030-release-automation.md), which makes `main`
  the latest release.

## Context

The guides in `docs/` were readable only on GitHub, one file at a time, with no search,
no landing page, and nothing in Vietnamese although the interface is bilingual. The
Quickstart lived in the README, so the guides had no first page to start a reader on.

The guides are also the reference contributors keep aligned with the code in the same
pull request. A site that copied them, or asked for front matter or MDX the GitHub view
does not need, would give every change a second place to forget.

## Decision

- **Docusaurus 3, in `website/`, reading `docs/` in place.** The pages are the guides as
  they are, parsed as CommonMark; `website/` holds only the site around them: its
  configuration, sidebar, landing page, and translations. It has its own `package.json`
  and lockfile, so the application's build, browser tests, and dependency audit never
  load it. Docusaurus has versions and languages built in, and the repository already
  builds with Node.js. MkDocs Material, now in maintenance, would add a Python toolchain;
  Starlight versions documentation only through a third-party plugin.
- **A broken link fails the build.** Links, anchors, and Markdown file links all throw.
  A link to a file beside `docs/`, such as `CONTRIBUTING.md`, still works on GitHub and is
  pointed at that file on `main` in the site, where it would otherwise name nothing.
- **Published from `main` only, to GitHub Pages, by a workflow.** `main` is the latest
  release, so the site describes what can be installed; `develop` is only built, on every
  pull request, to keep links whole. The deploy job alone holds `pages: write` and
  `id-token: write`, and the `github-pages` environment accepts only `main`. Every action
  is pinned to a commit SHA. There is one version of the documentation until a release
  breaks what an earlier one documented.
- **English is the source; Vietnamese is a translation of it.** The English guides stay in
  `docs/`. Each translation sits under `website/i18n/vi/` at the same path, and a page with
  none falls back to its English one. The guides, the Getting started page, the REST API
  prose, and Configuration are translated; the architecture, the implementation status,
  and these decision records stay in English, as engineering records whose translation
  would drift from what they record. Product terms stay in English in both, as the glossary
  in `CONTRIBUTING.md` already says for the interface.
- **Search runs in the browser.** A local index in both languages needs no account or
  third party; a hosted search can replace it when the site has the readers to need one.
- **The site's dependencies are audited like the application's,** at high severity, with
  one difference: an advisory no release fixes yet may be listed in
  `scripts/ci/audit-website.sh` with the reason it cannot reach a static site, and the
  check fails once npm stops reporting it. Fixable transitive advisories are pinned to a
  fixed version with npm `overrides`.

## Consequences

- A guide is written once, for GitHub and the site together. A new guide is placed in
  `website/sidebars.js` on purpose; the decision records are listed by number.
- A change to a guide that has a Vietnamese translation updates the translation in the same
  pull request, or the two disagree on the site.
- Documentation fixes reach the site with the next release, not when they merge into
  `develop`. A fix that cannot wait is a hotfix.
- The site carries a JavaScript dependency tree of its own, which Dependabot updates
  weekly. An override is removed once Docusaurus requires a fixed version itself.
- GitHub Pages must be switched to deploy from GitHub Actions once, by hand, before the
  first deployment.
