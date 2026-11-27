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

- A published note has revisions: revision 1 is the note as published, and the API reads
  each one at `GET …/notes/{noteId}/revisions` and `…/revisions/{revision}`. A note's
  `revision` and each run action's `noteRevision` say which revision is meant. With no
  correction, every note reads exactly as before
  ([ADR-0046](docs/adr/0046-correcting-a-published-note.md), [#87](https://github.com/hoangluongtran0309/ReleaseFlow/issues/87)).
- An administrator corrects a published note by hand: **Correct this note** shows the
  difference from the latest revision and saves the text as the next revision, with a
  reason. The release page lists each note's revisions with their differences and
  downloads (`?revision=n`), and which revision each destination last received. Saving
  delivers nothing ([ADR-0046](docs/adr/0046-correcting-a-published-note.md), [#87](https://github.com/hoangluongtran0309/ReleaseFlow/issues/87)).
- **Render again** corrects a published note with today's writer, its own template, and the
  translations it was published with, repaired as ADR-0040 repairs them, without asking DeepL
  or the AI; the person reads the difference and saves it as a rendered revision, or edits it
  into one by hand. A note a person wrote, and a retired audience's note, are corrected by
  hand only, and a note whose latest revision was written by hand is delivered as written
  ([ADR-0046](docs/adr/0046-correcting-a-published-note.md), [#87](https://github.com/hoangluongtran0309/ReleaseFlow/issues/87)).
- A corrected public changelog entry shows its latest revision under a line in the entry's
  language, such as "Corrected on 15 November 2026: {reason}", and links each earlier
  revision, which keeps its own address at `…/releases/{entryId}/revisions/{n}`, cached for a
  day and not indexed. The feed adds an item per correction and leaves the published one as
  it was. Neither names the person. An entry is now cached for five minutes rather than a
  day, so a correction reaches readers; an entry nobody corrected is otherwise served as
  before ([ADR-0046](docs/adr/0046-correcting-a-published-note.md), [#87](https://github.com/hoangluongtran0309/ReleaseFlow/issues/87)).
- An administrator delivers a corrected note to the public changelog with **Deliver a
  correction**: the form offers each destination that holds an earlier revision, names every
  other with why it is not offered, and the reason given is the line the corrected entry
  shows. A correction is never skipped for having nothing to say, delivers the latest
  revision once, and never replaces newer text with older. Corrections to GitHub, Confluence,
  Notion, Slack, Teams, and email come later (#88), and Zendesk's are made there by hand
  ([ADR-0046](docs/adr/0046-correcting-a-published-note.md), [#87](https://github.com/hoangluongtran0309/ReleaseFlow/issues/87)).
- A release in review or approved can change its summary and upgrade guide without returning
  to draft, so its decisions and approval stay. Once approved, the notes that follow their
  template are written again, only the new summary is translated, and the release page and
  API say who changed each after approval and when, and which notes edited by hand still
  show the earlier text. An edit that would translate the whole release, because the Project
  context or a glossary changed since approval, is held until the person, or the API caller
  with `retranslationCharacters`, confirms the characters it sends to DeepL
  (`409 release_edit_retranslates`). The version and the previous release still change only
  in a draft. A note's `lastEditedAt` says when a person last edited it
  ([ADR-0047](docs/adr/0047-editing-an-approved-release.md), [#114](https://github.com/hoangluongtran0309/ReleaseFlow/issues/114)).

### Changed

- Upgrade note: behind an HTTPS proxy, set `SERVER_REQUEST_HEADER_FORWARDED_STRATEGY=web-server`,
  Spring Boot 4.2.0's name for it. The old `SERVER_FORWARD_HEADERS_STRATEGY=native` still works
  in 1.4.0 but is deprecated, and it logs no warning, so move to the new name now. A deployment
  must not set both: startup stops if it does. Redirects now carry a relative `Location`, which
  the browser resolves against the public address. See
  [Deploying to production](docs/operations.md#deploying-to-production).
- Upgrade note: with Spring Boot 4.2.0 and Micrometer 1.18, observations no longer create their
  active long-task timers, so the Prometheus scrape loses every `*_active_seconds` series
  (`_count`, `_sum`, and `_max` of `http_server_requests_active_seconds`,
  `spring_security_authorizations_active_seconds`, `spring_security_filterchains_active_seconds`,
  `spring_security_http_secured_requests_active_seconds`, and
  `tasks_scheduled_execution_active_seconds`) and gains the JVM's
  `jvm_memory_used_after_last_gc_bytes`. ReleaseFlow's four metric families keep their names and
  labels. `management.metrics.observations.include-active-long-task-timer=true`
  (`MANAGEMENT_METRICS_OBSERVATIONS_INCLUDE_ACTIVE_LONG_TASK_TIMER`) turns the timers back on.
- Every run for a release, from publication, by hand, a schedule, a reminder, a signed call, or
  a correction, is created with the release locked, so it carries each note as of a moment no
  correction is half-way through.

### Fixed

- The approved-release edit and the published-note correction preview count the source
  characters of a repeated text once per translation, even when several changes, fields,
  or the release summary share it. Concurrent workers reuse the first job's cached answer.
- The public changelog's RSS feed closes each item. Since the feed was added, every item was
  left open, so with two entries or more each one sat inside the one before it, as 1.3.0 and
  1.3.1 serve it, and most readers showed only the newest entry. Each item is now a child of
  the channel, and its own content is unchanged.
- A public changelog entry's `ETag` names the reader's interface language, so a reader who
  switches between English and Vietnamese gets the page in the new language instead of a
  `304` that kept the old one.
- A release note's `updatedAt` moves only when its ready text changes. Editing one audience's
  narrative used to move the update time of every note in each translated language, though
  most stayed the same byte for byte, because each went through translating and back
  ([ADR-0047](docs/adr/0047-editing-an-approved-release.md), [#114](https://github.com/hoangluongtran0309/ReleaseFlow/issues/114)).

## [1.3.1] - 2026-11-20

### Fixed

- A Linear delivery is checked against `webhookTimestamp`, the time Linear sent it, not
  `createdAt`, the time the issue changed, so a completion Linear sends more than a
  minute late is recorded instead of lost. The server log names the proof a refused
  delivery failed, without its body or the secret, at most once a minute per source and
  reason, with a count of those it left out
  ([ADR-0018](docs/adr/0018-linear-sources.md), [#94](https://github.com/hoangluongtran0309/ReleaseFlow/issues/94)).
- Reconnecting a Linear source says it needs a Linear API key besides the signing secret,
  and labels that field API key, as connecting does
  ([#106](https://github.com/hoangluongtran0309/ReleaseFlow/issues/106)).
- An automation action whose note has nothing for its audience is skipped with
  `nothing_for_audience` instead of delivered, so a rule no longer writes a permanent
  public changelog entry, page, article, or message that only says nothing in the release
  concerns its reader; the actions behind it still run. A note a person edited is
  delivered as written
  ([ADR-0035](docs/adr/0035-evidence-bound-notes-and-audience-relevance.md),
  [ADR-0044](docs/adr/0044-retire-an-audience.md), [#108](https://github.com/hoangluongtran0309/ReleaseFlow/issues/108)).
- Accepting a linked issue's status while its review-time read was being recorded could fail
  with an error, or silently undo either the acceptance or the read. A new review requested
  meanwhile could keep the old read and never read the issue again. Every change to a
  release and what belongs to it now locks the release first, so these run one after the
  other ([#119](https://github.com/hoangluongtran0309/ReleaseFlow/issues/119)).
- A translated note keeps the text of each code span as the change wrote it: DeepL
  translated the words inside spans, so a Vietnamese note could not quote what an English
  interface prints (`under 1 s` came back as `dưới 1 giây`). Requests, the cache, and their
  keys are unchanged; a cached answer is repaired at the next approval
  ([ADR-0040](docs/adr/0040-translation-placeholders.md), [#107](https://github.com/hoangluongtran0309/ReleaseFlow/issues/107)).

## [1.3.0] - 2026-11-18

### Added

- Administrators change a member's role and remove a member, on the Members page or
  through the API. The last administrator can't be demoted or removed, even when two
  administrators try at once ([ADR-0043](docs/adr/0043-member-roles-and-removal.md)).
- A member whose role changes, or who is removed, is signed out of every session on its
  next request, through the pages and the API. A removed member can't sign in, and the
  invitations they made that are still pending are revoked, as are a demoted
  administrator's.
- Everything a removed member recorded keeps the name they had. Their address is free
  again, and a person invited back joins as a new member. The Members page lists removed
  members and who changed whose role, and when.
- A source can be disconnected from its own page, which first says what will happen. Its
  changes stay, with their reviews and releases; its webhook secret and access token are
  destroyed, its webhook path answers like one that never existed, and its imports and
  polls stop ([ADR-0042](docs/adr/0042-source-lifecycle-and-health.md)).
- A disconnected source is reconnected under its own ID, with a new webhook path and
  secret, so a history import afterwards adds no change twice. Connecting the same
  repository, project, or team again in the same Project points to the reconnect instead;
  another Project may connect it once it is disconnected.
- A webhook secret can be rotated without changing its path: generated for GitHub and
  GitLab, pasted for Linear and for a signing token GitLab generated. The old secret stops
  at once, and the page says where the new one goes and how to send again what the
  provider refused in between. A new Jira token may belong to another account.
- Each source's health shows in the Change Inbox's Source sync panel, which now lists
  every source, on the overview, and on the Projects page: its last delivery, how many
  deliveries were refused for their signature since the secret was set and when the last
  one was, a **Quiet** mark when it has been silent for longer than it usually goes, and
  an **Overdue** mark for a Jira source whose read is late. Nothing about a refused
  delivery is kept beyond its count and time.
- An administrator can retire an audience instead of deleting it, even while notes,
  rules, or runs name it, and bring it back later. From the next approval a retired
  audience gets no note, no AI narrative, and no translation. Its templates, narratives,
  and published notes stay, and who retired it or brought it back is shown
  ([ADR-0044](docs/adr/0044-retire-an-audience.md)). The last active audience can't be
  retired, even when two administrators try at once.
- When an audience comes back, its notes include every change, under its neutral headline
  when it has no narrative. The release's Review tab lists those changes, and a person can
  ask the AI for only the missing narratives, with the request's rough size in tokens
  shown before anything is sent.

- The Review tab lists the linked issues whose open status a person accepted showing, under
  **Open statuses the notes show**: each with its key, the status the notes print, who
  accepted it, and when, while the release is in review or approved. Before, an issue left
  the page once its status was accepted, and only the API said who had chosen it.

### Changed

- **Behavior change for Anthropic:** ReleaseFlow calls Claude through anthropic-java 3.8.0,
  with Anthropic API version `2026-10-21`, and sends no temperature: that version refuses
  one on every model ([ADR-0045](docs/adr/0045-anthropic-api-version-2026-10-21.md)). No
  setting needs to change.
  - On Claude 4.6 and earlier (such as Sonnet 4.6, Opus 4.6, or Haiku 4.5), classification
    runs at the default temperature, `1.0`, instead of `0`, so notes vary more between runs
    than on 1.2. No setting brings `0` back on Anthropic; OpenAI and DeepSeek still take it.
  - On Claude 4.7 and later, classification samples as before, and the first request after
    each start is no longer refused for its temperature, so its warning goes away.
  - `RELEASEFLOW_AI_TEMPERATURE` is still checked at startup with Anthropic but never sent;
    a number other than `0` logs one warning that it is ignored.
  - The API key is sent as `Authorization: Bearer` instead of `x-api-key`. A proxy or
    gateway at `RELEASEFLOW_ANTHROPIC_BASE_URL` that reads `x-api-key` or checks
    `anthropic-version` must accept the new headers before upgrading. See
    [Upgrading with Anthropic](docs/operations.md#upgrading-with-anthropic).
  - The Anthropic SDK is licensed under Apache-2.0 instead of MIT; it ships no `NOTICE`.
- A GitHub Release action whose repository's source is disconnected fails as
  `github_source_disconnected` and succeeds when run again after the reconnect; the
  Automation page marks an enabled rule that has no GitHub source to borrow. A rule with a
  GitHub Release action can be enabled only while its project has one connected GitHub
  source.
- **Behavior change:** editing a change's summary now keeps the narratives of retired
  audiences. Before, saving the summary kept only the narratives the request listed, so a
  narrative for an audience it left out was erased; now each narrative it does not
  replace is kept.
- An automation action for a retired audience is skipped, with `audience_retired`, instead
  of being attempted: nothing is sent for it, the actions behind it still run, and the run
  page marks it **Skipped**. Action runs gain the `SKIPPED` status. A rule that delivers a
  retired audience can't be enabled (`409 automation_audience_retired`).
- Deleting the last *active* audience is refused with `409 audience_last`; a retired
  audience nothing names can still be deleted.

### Fixed

- A DeepL request lists a job's texts in the order of their keys, so the same texts go in
  the same order on every run. Before, the order changed from one run to the next. DeepL
  translates each text on its own, so no translation or note changes.
- The release API lists a release's fold decisions in a fixed order, by when each was
  decided and then by the tracker change, rather than in whatever order the database read
  them.
- The instructions for connecting a Linear team can now be followed. They said to create
  Linear's webhook at the source's path before ReleaseFlow had given the source one; the
  Projects page and the guide now say to create the webhook disabled at a holding address
  of ReleaseFlow's own, connect the team with its signing secret, then point the webhook
  at the source's path and enable it. Connecting itself is unchanged.
- An automation run names the audience of an action that had no note, instead of showing
  its ID: an action skipped for a retired audience, or one that failed as
  `release_note_missing`. A run recorded with the ID shows the audience's current name; the
  stored row does not change.
- Every page names a Linear source by its team's key, such as `OPS`, instead of the team's
  ID; before 1.3.0 the Projects page showed the ID too. The API's source views carry the key
  as `externalTeamKey`, beside the team's ID in `externalProjectKey`. A team whose key was
  never read is still named by its ID.

## [1.2.0] - 2026-11-08

### Added

- Linear issues link to GitHub and GitLab changes as Jira issues do: an identifier such
  as `OPS-7` of the Project's Linear team is read from Linear and shown under linked
  issues. The team's key is read once and kept; a team whose key cannot be read marks
  the change's linked issues incomplete.
- Issue keys are found in a pull or merge request's head branch, such as
  `feature/DBV-47-retention-preview`, and in a description or commit where a tracker's
  key is followed by a hyphen and a word, such as `Spec: DBV-12-quartz`
  ([ADR-0041](docs/adr/0041-linked-context-and-folding.md)).
- Requesting review reads every issue linked to the release's changes again, in the
  background, with bounded retries. The Review tab lists each issue still open while its
  change ships, and **Show this status** lets a member choose to print that status.
- The Review tab suggests folding a Jira or Linear change into the code change of the
  same release that names its key or that a person confirmed as its duplicate. A member
  accepts by rejecting the tracker change from the release, an administrator may exclude
  it with a prefilled reason instead, or the suggestion is dismissed; each is decided once.
  A fold keeps the work's breaking and security flags on the code change, recorded as its
  review, and reads the issue it adds as of review.
- Templates get `recordedStatus` and `statusAtReview` for each linked issue.

### Changed

- `{{status}}` of a linked issue is now its status as of the release's review, and it can
  be empty: an issue that is not finished, or whose tracker did not say, shows no status
  unless a person accepted it. A custom template that prints `({{status}})` prints `()`
  then; wrap it as `{{#status}} ({{status}}){{/status}}`, as the
  [release-note guide](docs/release-notes.md#upgrading-custom-audience-templates) shows.

## [1.1.0] - 2026-11-01

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
  ([ADR-0036](docs/adr/0036-translation-glossary.md)). A translation keeps the
  placeholders (`<backup-id>`) and lowercase hyphenated names (`dbvault-cli`) of its source
  as written, which DeepL changes the case of when it uses a glossary, without changing
  any request ([ADR-0040](docs/adr/0040-translation-placeholders.md)). See
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
  released after Opus 4.6, keeps working with no action: when it refuses the temperature,
  the same request is sent once more without it, and later requests leave it out until
  ReleaseFlow restarts, with one warning in the log
  ([ADR-0039](docs/adr/0039-ai-temperature-fallback.md)). `RELEASEFLOW_AI_TEMPERATURE=default`
  leaves it out from the first request and so avoids that refusal (see
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

[Unreleased]: https://github.com/hoangluongtran0309/ReleaseFlow/compare/v1.3.1...HEAD
[1.3.1]: https://github.com/hoangluongtran0309/ReleaseFlow/releases/tag/v1.3.1
[1.3.0]: https://github.com/hoangluongtran0309/ReleaseFlow/releases/tag/v1.3.0
[1.2.0]: https://github.com/hoangluongtran0309/ReleaseFlow/releases/tag/v1.2.0
[1.1.0]: https://github.com/hoangluongtran0309/ReleaseFlow/releases/tag/v1.1.0
[1.0.1]: https://github.com/hoangluongtran0309/ReleaseFlow/releases/tag/v1.0.1
[1.0.0]: https://github.com/hoangluongtran0309/ReleaseFlow/releases/tag/v1.0.0
