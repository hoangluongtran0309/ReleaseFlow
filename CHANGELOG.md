# Changelog

All notable changes to ReleaseFlow are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/). Each released version is a tag; see the
[releases][releases] for the artifacts published with it.

New changes go under `[Unreleased]`. `scripts/release.sh start <version>` turns that
section into the version's entry, and the GitHub release is written from it.

[releases]: https://github.com/hoangluongtran0309/ReleaseFlow/releases

## [Unreleased]

### Added

- Security changes have a section of their own. A change gains a security flag beside
  breaking: rules set it from a `security`, `security-fix`, `security fix`, or
  `vulnerability` label and from a CVE identifier, the AI may propose it, and a person
  confirms or corrects it in the Change Inbox and in a release's review. A security
  change always needs review, and the AI never clears the flag. Notes list security
  changes under 🔒 Security after breaking changes and count each change once; one that
  is both breaking and security appears once, under breaking changes, marked as security.
  The operator, contributor, and support notes always include a security change
  ([ADR-0037](docs/adr/0037-security-changes-and-release-note-structure.md)).
- A release can link an upgrade guide, which the breaking-change warning then links,
  and names the release it follows, by default the Project's most recently published
  one.
- A footer template per audience and language, rendered once at the end of each note
  with `version`, `previousVersion`, `compareUrl`, `upgradeGuideUrl`, and `releaseUrl`.
  The compare and release links come from the Project's one GitHub or GitLab source,
  with each version used as its tag name, and are built without calling the provider.
  The operator and contributor presets end with a Compare link and an Upgrade guide link,
  and existing operator and contributor audiences received them. See
  [Upgrading custom audience templates](docs/release-notes.md#upgrading-custom-audience-templates)
  for adding the footer and the limitations line to an audience your team created.
- A change returned by the API has `security`; a release has `upgradeGuideUrl`,
  `previousReleaseId`, and `previousVersion`; an audience has `footerTemplateBody` and
  `footerTemplateVariants`. A request that leaves the new fields out keeps working.
- An Organization glossary for translated notes. Administrators keep product terms as
  written in every language (CLI, console, image, pull request) and fix translations per
  language pair (`storage` → *lưu trữ*) on a Glossary page or through `/api/glossary`;
  members read it. Nothing is seeded: suggested terms are only offered. Each language pair
  is synced to DeepL as a glossary dictionary when an approval needs it, and a glossary
  edit translates again at the next approval, the release summary included. The release
  page says, per language, whether the glossary was sent with every translation, which is
  not a promise that DeepL kept every term, and why not when it was not;
  a pair DeepL cannot serve, or a failed sync, never blocks a release. A Vietnamese or
  non-Latin answer that comes back in the source language is translated again without the
  glossary, or fails, rather than being stored
  ([ADR-0036](docs/adr/0036-translation-glossary.md)). See
  [Upgrading to the glossary](docs/operations.md#upgrading-to-the-glossary) for adding
  terms, and for the one glossary a DeepL API Free account holds.
- A Project context: what administrators say about the product (what is a client and
  what a server, where settings live, what stays in English by design). The AI receives
  it with every change it classifies, and DeepL as context for the Project's
  translations ([ADR-0035](docs/adr/0035-evidence-bound-notes-and-audience-relevance.md)).
- A change's summary records how much its migration step asks — required, only if it
  applies, or recommended — and what the change deliberately does not do yet. The shipped
  templates label a step by its kind, and Edit summary sets both.
- Each audience's note shows only the changes that concern it. The AI says, for every
  audience, whether a change is not for it, worth a headline, or worth the detail, and
  writes a one-line headline beside its narrative. A breaking change is in every note,
  counts and the breaking warning describe what the note shows, and an audience that
  nothing concerns gets a note saying so. Templates gain `headline`, `relevance`, and the
  `brief` section, and Edit summary sets all three for each audience
  ([ADR-0035](docs/adr/0035-evidence-bound-notes-and-audience-relevance.md)).
- A fourth shipped audience, Support: what customers may ask about, what to tell them,
  and whether a step is required or only advisable. New Organizations start with it; an
  existing one can add an audience coded `support` and reset it to the preset.

- A change returned by the API has `audienceNarrativeDetails`: each audience's
  narrative as `relevance`, `headline`, and `body`. `audienceNarratives` stays a map of
  audience code to narrative text, and Edit summary accepts either an object or a plain
  string for each audience, so existing clients keep working.

### Changed

- The shipped templates lead with each audience's headline, keep their detail lines out
  of a brief change, and always print a required step. Audiences created before keep
  their templates until they are reset to the preset.
- The shipped intents say when a change does not concern their audience, and the
  operator intent asks for something to watch after deploying only when the change or the
  Project context names it.

- The AI is told to state only what a change's evidence supports in every field: no
  invented consequences or places in the product, no steps taken from a dependency's
  changelog, no files for a tracker issue that names none, and no remarks about missing
  detail. A tracker issue is sent as one rather than as a pull request.
- A release cannot be approved while one of its changes has no summary and a title of
  fewer than three words, such as "fix"; the release page marks it and asks for a summary.
- A migration kind the AI got wrong is corrected and sent to review, rather than costing
  the whole answer.
- A dependency update from Dependabot or Renovate reaches the AI without the release
  notes, changelog, and commits it quotes, so another project's upgrade steps no longer
  become this project's; it is breaking only when its own words say so. Only a dependency
  update is told that, so the AI still marks an ordinary change breaking when it is unsure.
- A change with a title like "fix" and an empty or very short description waits for a
  person's summary even when the AI wrote one, since the AI could only restate the title.
- Limitations that only remark on what the change did not explain, or only say what the
  change does not affect, are dropped.
- An AI migration step that only says nothing needs doing, such as "No immediate action
  is required", is dropped, so the change has no step rather than a recommended one.
- Every AI classification request uses temperature 0, so the same change varies less from
  one request to the next. `RELEASEFLOW_AI_TEMPERATURE` sets another value in the
  provider's range (`0` to `2` for OpenAI and DeepSeek, `0` to `1` for Anthropic), checked
  at startup ([ADR-0038](docs/adr/0038-configurable-ai-temperature.md)). A model that
  accepts only its default temperature, such as an OpenAI reasoning model or a Claude model
  released after Opus 4.6, keeps working only with `RELEASEFLOW_AI_TEMPERATURE=default`,
  which leaves the temperature out of the request as 1.0 did: set it before upgrading (see
  [Upgrading with a reasoning model](docs/operations.md#upgrading-with-a-reasoning-model)).

## [1.0.1] - 2026-10-25

### Fixed

- The Change Inbox filters wrap onto another row when the page is narrow, rather than
  shrinking until the arrow covers their text.
- A language chosen for an account, in another browser or through the API, reaches that
  person's other signed-in sessions on their next page instead of at their next sign-in
  ([ADR-0034](docs/adr/0034-account-language-per-request.md)).
- A placeholder such as `<id>` in a change's summary or narrative is escaped in the
  release note, so GitHub and other Markdown readers show it instead of dropping it as an
  HTML tag, as the release page already did.
- A Microsoft Teams message carries the note as plain text, as intended, so escaped text
  such as `<id>` or `*bold*` no longer shows its backslash there.
- A screen reader announces the account menu button with the name it shows, as well as
  what it does, so a person who speaks to it by what they see can open it.

## [1.0.0] - 2026-10-20

### Added

- Organizations with administrator and member roles, single-use invitation links,
  session sign-in, and an English or Vietnamese interface chosen by each person.
- Sources for each Project: merged pull requests from GitHub, merged merge requests from
  GitLab (gitlab.com or an allowlisted self-managed instance), and completed issues from
  Linear arrive by signed webhook; issues finished in Jira Cloud are polled. A GitLab
  project connected with signed headers is also given its secret as the signing token
  GitLab's own field takes.
- A resumable 90-day history import for GitHub and GitLab sources that never duplicates
  a webhook delivery.
- Write-only access tokens, confirmed with the provider before they are stored, so the
  files each change touched are listed. Webhook secrets, tokens, and action secrets are
  encrypted with AES-256-GCM under `RELEASEFLOW_CREDENTIAL_MASTER_KEY`.
- Rule-based classification from Conventional Commit titles, labels, documentation-only
  file lists, and `BREAKING CHANGE` footers, against a category catalog each
  Organization manages.
- Mandatory review for breaking and Unknown changes and for every review trigger:
  sensitive files (a deployment baseline plus each Project's additions), file lists that
  could not be read, sensitive words in issues, incomplete linked Jira issues, too little
  context, and possible duplicates.
- Optional AI classification with OpenAI, Anthropic, or DeepSeek: one request per change
  for a category, a neutral summary, and a narrative per audience, in the
  Organization's output language. The rules always win, and a failure becomes a review
  trigger rather than a retry.
- A Change Inbox per Project with filters, linked Jira issues, recorded human review,
  and decisions on AI category proposals and possible duplicates. An administrator can
  exclude a change from releases with a reason, so no release offers it again, and
  include it again later.
- Releases that move from draft through a decision on every change to approval and
  publication, several per Project, each with an optional planned time.
- Audiences with Mustache templates and AI narratives, three presets, and one release
  note per audience and language, translated by an optional DeepL queue and frozen when
  the release is published. The queue translates the release's own summary as well as
  each change's, and a template names a change the way its source does through
  `reference`: `#12` on GitHub, `!12` on GitLab, and the issue key on Jira and Linear.
- Automation rules that deliver a published note to GitHub Releases, Slack, email,
  Notion, Confluence Cloud, Microsoft Teams, Zendesk, or the public changelog, fired by
  a publication, a person, a cron schedule, a reminder before a planned release, or
  another system's signed call. A delivery nobody could confirm is never repeated
  without a person's consent.
- A public changelog for each Organization, with permanent entries and an RSS feed,
  readable without an account. A release published in several languages is listed once,
  with a link to each language.
- A REST API for everything the pages do, with Problem Details errors whose `code`
  never changes.
- A private management port with health and four Prometheus metrics, a non-root
  container image, and a Docker Compose demo stack with Prometheus and Grafana.
- Release automation: a tag is the release, and its workflow publishes the JAR, the
  image on GHCR, an SBOM, the quickstart bundle, checksums, and provenance attestations.
- A documentation site in English and Vietnamese, read from `docs/`, with product,
  self-hosting, community, and security pages, a REST API reference checked against the
  code on every build, a changelog page read from this file, and search.

[Unreleased]: https://github.com/hoangluongtran0309/ReleaseFlow/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/hoangluongtran0309/ReleaseFlow/releases/tag/v1.0.1
[1.0.0]: https://github.com/hoangluongtran0309/ReleaseFlow/releases/tag/v1.0.0
