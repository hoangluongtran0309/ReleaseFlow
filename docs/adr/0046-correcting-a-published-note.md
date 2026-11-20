# ADR-0046: Correcting a published note with a new revision

- Status: Accepted
- Date: 2026-11-15
- Amends: [ADR-0005](0005-immutable-release-note-snapshots.md) and
  [ADR-0011](0011-audience-release-notes.md), whose published note can never be
  corrected; [ADR-0022](0022-public-changelog.md), whose entry never changes and is cached
  as immutable; and [ADR-0020](0020-automation-rules-and-runs.md),
  [ADR-0024](0024-notion-and-confluence-actions.md), and
  [ADR-0025](0025-teams-and-zendesk-actions.md), for what an action does with a correction
- Refers to: [ADR-0015](0015-multilingual-release-notes.md),
  [ADR-0036](0036-translation-glossary.md), and
  [ADR-0040](0040-translation-placeholders.md) (translation, its cache, and the repair of
  an answer), [ADR-0043](0043-member-roles-and-removal.md) (removed members), and
  [ADR-0044](0044-retire-an-audience.md) (retired audiences and `SKIPPED`)
- Amended: 2026-11-20, for which job rendering again reads, two corrections to the
  Context, and how a corrected note meets ADR-0035's skip; see
  [the amendment](#amendment-2026-11-20-rendering-again-what-it-changes-in-dbvault-and-the-skip)

This record decides issue #22 and writes no code. Nothing it describes exists yet.

## Context

A published note cannot change. Triggers refuse every update and delete of
`release_audience_notes` once its release is `PUBLISHED` (ADR-0005, ADR-0011) and of
`public_changelog_entries` always (ADR-0022), and `docs/implementation-status.md` lists
"unpublishing or correcting published release notes" as deferred. An error that passes
review is public for good, and the only fix is a new release.

The dbvault lab has real cases. v1.2.0 was approved and published on 2026-11-02, on
1.1.0-rc.2, with twelve notes: six audiences in English and Vietnamese. Counted in the
captured notes (fixture set `v2`):

| Note | Placeholders in the wrong case | "Điểm cuối" for "Endpoint" |
| --- | --- | --- |
| `contributor` / vi | 7 (`<BACKUP-id>` ×2, `<COUNT>` ×2, `<VERIFICATION-id>` ×2, `<ZONE>`) | 4 |
| `operator` / vi | 3 (`<BACKUP-id>`, `<VERIFICATION-id>`, `<ZONE>`) | 2 |
| `aut_r4_probe` / vi | 0 | 1 |
| the other nine | 0 | 0 |

(Amended on 2026-11-20: rendering again also restores `dbvault-CLI` to `dbvault-cli` in
all six Vietnamese notes, so it changes four of the notes counted here as clean; see the
[amendment](#amendment-2026-11-20-rendering-again-what-it-changes-in-dbvault-and-the-skip).)

- **The placeholders** are the lab's FINDINGS #23. 1.1.0-rc.3 repairs DeepL's answer when
  a translation job succeeds (ADR-0040), so only notes written after it are right.
  ADR-0040 says so itself: "Published notes are not changed."
- **"Điểm cuối"** is a glossary matter: the kept term "Endpoint" was translated at the
  start of a sentence, seven times.
- **`aut_r4_probe`** is the audience ADR-0044 was written to retire.

Rule R1 copied v1.2.0 to a GitHub Release (`contributor`/en), the public changelog
(`end_user`/en and vi), Slack, email, and Teams (`operator`/en), Notion (`contributor`/en),
Confluence (`dba`/en), and Zendesk (`end_user`/vi), as the lab's plan defines R1. None of
those is one of the three damaged notes. So the lab's real errors sit on the release page
and in its downloads, and no copy outside ReleaseFlow carries them. A correction to an
English note, or to `end_user`/vi, would reach every one of those places, which is why the
destinations are decided here too. (Amended on 2026-11-20: `end_user`/vi carries
`dbvault-CLI`, which R1 copied to the public changelog and Zendesk; see the
[amendment](#amendment-2026-11-20-rendering-again-what-it-changes-in-dbvault-and-the-skip).)

Six facts of the code as it stands decide most of what follows:

- **A note is its Markdown and the snapshots it was written from.** `release_audience_notes`
  holds the content, the audience's template and footer as they were, and nothing
  structured. There is no stored list of the items a note was rendered from.
- **A release's changes stay reviewable after publication** (ADR-0005). Rendering a
  published note again reads them as they are now, with today's writer.
- **A translation job keeps what it asked with.** `translation_jobs` stores its input
  texts, its Project context, and its glossary version. The cache is per text, keyed by
  the text, the context, and the glossary version, and holds DeepL's raw answer; the
  repair of ADR-0040 is a pure function applied when a job succeeds.
- **An action run records little about what it delivered.** `external_reference` is a
  link: the GitHub Release, the Notion or Confluence page, the Zendesk article, the public
  entry. Slack, email, and Teams record none. The GitHub Release body ends with
  `<!-- releaseflow-action:{actionRunId} -->`; Notion's and Confluence's marker is never
  read back (ADR-0024).
- **A public entry is unique per release, audience, and language**, addressed by its ID,
  and served `public, max-age=86400, immutable`. The feed's item uses the entry's address
  as its `guid`.
- **Every member may approve, edit a note, and publish.** Only automation, audiences,
  members, and settings are for administrators.

**The roadmap discussion.** Discussion #24 asked "How should a published note be
corrected?" on 2026-10-22. As of 2026-11-15 it has no reply to that question, and
issue #22 has no comment: the discussion's one comment is the maintainer's own 1.2.0
announcement, which only lists #22 for 1.3.0. Nothing there bears on the design, so the
evidence below is the lab's alone.

## Designs compared

**A. Erratum entries.** The note stays as it is, and an erratum is added beside it: "in
the operator note, read `<backup-id>` for `<BACKUP-id>`".

- For: no change to notes or their triggers; nothing to version; a message destination
  can be sent one.
- Against: the note people read and copy from is still wrong. A placeholder is something a
  reader types, and the page, the download, and the GitHub Release would keep printing the
  wrong one. dbvault's case needs seventeen errata lines over three notes. Every reader
  and every destination has to show a note and its errata together, which is a revision in
  all but name, without the corrected text. A fix that already exists in the code, such as
  ADR-0040's, cannot be applied at all.

**B. Note revisions (recommended).** A correction is a new immutable revision of one note.
The latest revision is the note; every earlier one stays readable.

- For: the reader gets the right text, and the record of what was first published is
  untouched. Triggers keep refusing every change. A later fix can be applied by rendering
  again. An erratum is still there where it helps: the reason is shown with the correction.
- Against: every reader of a note has to say which revision it means; the public entry is
  no longer one page that never changes; each destination needs its own answer.

**C. Unlock, edit in place, and keep an audit row.** Rejected. The old text would live
only in an audit table that nothing forces to exist, the trigger's promise would be gone,
and a public page cached as immutable would change under its readers.

**D. A new release for the same version.** Rejected, and it is what people do today.
Versions are unique per Project, the tag is the note's link target, and automation would
announce a release that never happened.

## Decision

Design B. The parts below are numbered as issue #22's brief numbers them.

### 1. A correction is a revision

- **The note as published is revision 1 and is never touched.** `release_note_revisions`
  holds revisions 2 and up: the note, the revision number, the content, how the text was
  made (`MANUAL`, `RENDERED`, or `TRANSLATED`), the reason, the person's ID, the name they
  had then, the time, and the glossary status a translated text claims. A revision
  supersedes the one numbered just below it.
- **One audience and language at a time.** A correction never spans notes, and never
  changes a note's version, audience, language, or release. Only the Markdown changes.
- **A reason is required**, at most 500 characters, written in the note's own language.
  It is published wherever the correction is, and the form says so.
- **Rows are only ever inserted.** A trigger refuses every update and delete of a
  revision. A second trigger refuses an insert unless the release is `PUBLISHED` and the
  number is the next one, so two people correcting at once cannot both write revision 2.
  The triggers on `release_audience_notes` and `releases` do not change.
- **The request names the revision it corrects.** If that is no longer the latest, the
  correction is refused with `409 release_note_revision_stale`. Text identical to the
  latest revision is refused with `409 release_note_correction_unchanged`.
- **Not decided as corrections:** unpublishing, removing a change from a published
  release, and the single notes of releases published before V13 (`release_notes`), which
  stay read-only.

### 2. How the corrected text is made

Three ways, and each ends the same way: a person reads the difference from the latest
revision and saves it with a reason. Nothing writes a revision on its own.

- **By hand.** The person edits the latest revision's Markdown. Recorded as `MANUAL`.
- **Rendered again.** ReleaseFlow renders the note with today's writer, from the note's
  own template and footer snapshots, the release's changes as they read now, and its
  stored links and issue statuses. For a translated note, each text is read from the
  translation cache under the context and glossary version of the job that translated it
  for this release, then repaired as ADR-0040 repairs any answer. No provider is called.
  This is how rc.3's placeholder fix reaches dbvault v1.2.0: the same DeepL answers, with
  `<backup-id>` restored. Recorded as `RENDERED`.
- **Translated again**, for one note. The same rendering, but each text is looked up under
  the Project's context and the pair's glossary as they are today. Texts with no answer
  there are sent to DeepL through the existing queue, as ordinary translation jobs for the
  texts this note uses: the neutral summary fields, the release summary, and this
  audience's headlines and narratives. This is how a glossary change reaches a published
  note. Recorded as `TRANSLATED`.
- **Rendering again is a preview, never a silent fix.** It reads the changes as they are
  now and today's writer, so it can show more than the fix: a category corrected in the
  Change Inbox since publication, or wording the writer has changed between versions. The
  person sees every difference before saving, and may edit the result, which then counts
  as `MANUAL`. Saving sends back the hash of the preview, and a preview that would now
  differ is refused and shown again.
- **A text whose source changed since publication** has no answer in the cache under the
  old job. The preview marks it and counts it; it is filled only by translating again.
- **A note a person edited is corrected by hand only.** That holds for a note edited
  before publication and for a note with a `MANUAL` revision: a note a person edited never
  follows its template again (ADR-0011). Rendering or translating it again is refused with
  `409 release_note_manual`.
- **The glossary.** A translated revision claims the glossary only when every translation
  behind it used the version it asked for, as a note does (ADR-0036). A pair whose
  glossary changed is synced by its own job when a translation asks for it; the
  translation waits for a pending sync and never for a failed or unsupported one. A
  `MANUAL` revision claims nothing.
- **The cache.** Rendering again only reads it. Translating again writes the new answers
  under today's context and glossary version, raw, as any job does, so a later approval
  that needs the same text is answered without a request. No request, input hash, text
  hash, or cache key changes shape.
- **A finished translation never writes a revision.** `onTranslationFinished` already
  ignores a release that is not `APPROVED`. The preview simply has fewer texts missing
  when the person opens it again.

### 3. The original stays readable

- **The release page lists each note's revisions**: number, date, the person's name as
  recorded, how the text was made, and the reason. Each revision opens, downloads, and
  shows its difference from the one before. Revision 1 is labelled as published.
- **The latest revision is the note** wherever a note is read without a number: the
  release page, the download, and the content a new automation run snapshots. A rule run
  for the first time on a corrected release delivers the latest revision.
- **Each note shows where it went**: for every action that delivered it, the revision that
  destination last received, so "the public changelog still shows revision 1" is visible.
  `automation_action_runs` records the revision it carried.

### 4. The public changelog and the feed

- **`public_changelog_entry_revisions` is append-only** like the entries. A row copies the
  corrected content, the reason, the time, and the action run that wrote it. The entry row
  never changes.
- **The entry's address shows the latest revision**, under a line that reads "Corrected
  on 15 November 2026: {reason}", with a link to each earlier revision. An earlier
  revision is served at `…/releases/{entryId}/revisions/{n}`, says which revision replaced
  it, and asks search engines not to index it. Neither page names the person.
- **Caching.** The entry's address becomes `public, max-age=300`, with the entry ID and
  revision as its ETag. A numbered revision never changes and keeps
  `public, max-age=86400, immutable`.
- **The feed gains one item per correction**: its own `guid` (not a permalink), the
  correction's time as `pubDate`, "(corrected)" in the title, a link to the entry, and a
  description that starts with the correction line. The original item is left as it was.
  RSS 2.0 has no way to say an item changed, and a reader that has seen a `guid` does not
  show it again.
- **Only a correction run appends a revision.** Any other run that meets an entry on an
  older revision still fails as `public_changelog_conflict`. Delivering the same revision
  twice is the revision that is already there.

### 5. Automation

- **A correction is delivered only when a person asks.** `CORRECTION` is a new trigger
  type. An administrator starts it for one rule and one published release, and picks which
  of the rule's actions it includes. Publication, a schedule, a reminder, and a signed
  call never start one. Saving a revision delivers nothing.
- **There must be something to correct.** An action is offered only when it has a
  `SUCCEEDED` run for that release and the note has a later revision than that run
  carried. One that never succeeded is not a correction: running the rule delivers the
  latest revision. One left `UNKNOWN` is settled first, by a person, as today.
- **The rest is ADR-0020 unchanged.** The run walks its actions in the rule's order, the
  first that does not succeed stops those behind it, an unconfirmed outcome is `UNKNOWN`
  and repeated only when a person confirms, and an action for a retired audience is
  `SKIPPED`. The action uses its configuration and secret as they are now, and the target
  the earlier run recorded.
- **Text edited in place starts with the correction line**, in the note's language, from
  the same resource bundles as a note's labels.

What each destination does:

| Destination | A correction | Refused when |
| --- | --- | --- |
| Public changelog | Appends a revision (part 4). Offered by default. | — |
| GitHub Release | Edits the release of the version's tag in place. | The release is gone (`github_release_missing`); its body lacks this action's marker (`github_release_conflict`); or its body is not what ReleaseFlow last delivered (`github_release_edited`). |
| Confluence | Writes a new version of the page it created, with the reason as the version message. Confluence keeps every version. | The page is gone, or its version number is not the one ReleaseFlow last wrote (`confluence_page_edited`). |
| Notion | Replaces the content of the page it created, in one call. | The page is gone or archived, or a person edited it last (`notion_page_edited`). |
| Slack, Teams, email | Sends one message: the correction line, then the corrected note, with "Corrected:" in the subject or first line. Not offered by default. | Never; it is a new message. |
| Zendesk | Nothing. | Always (`correction_not_supported`). |

- **GitHub Release.** The executor reads the release by its tag. It edits only a body that
  holds the marker of this action's delivering run and that, line endings aside, is
  exactly the content and marker ReleaseFlow last delivered. Otherwise somebody has
  changed it, and their work is not overwritten. It never creates a release. The marker of
  the first delivery is kept, so later corrections find the same release. A repeat after
  an unknown outcome reads first, and finds either the old body or the new one.
- **Confluence.** The page ID is the one in the link the delivering run recorded; nothing
  is searched for. The expected version is 1 plus this action's earlier successful
  corrections for the release, sent as the update's own version check, so a page somebody
  edited is refused rather than overwritten.
- **Notion.** The page ID comes from the recorded link. One read tells whether the
  integration itself made the last edit; then `PATCH /v1/pages/{id}/markdown` with
  `replace_content` replaces the body. That endpoint exists in the pinned API version,
  `2026-03-11`; the slice confirms the request against the lab's Notion before relying on
  it. Notion's own page history is all that keeps the old text there.
- **A link that was never recorded** (a 2xx that named no page) leaves nothing to find:
  `correction_target_unknown`.
- **Slack, Teams, and email** cannot take a message back. A correction there is a second
  message, so the person chooses it each time, and a typo fix need not mail everyone.
- **Zendesk.** An article is corrected through a different call than the one that creates
  it, per locale, and a help centre shows readers no history. The lab's Zendesk is a
  stand-in, so the path could not be accepted against a real one. The page gives the
  article's link and the corrected text to paste. A second article is never created.
- **Metrics.** A correction's delivery is counted like any delivery. Where a series is
  labelled by trigger, `correction` joins the finite set and is registered at zero.

### 6. Who may correct

- **Administrators only**, by URL rule and again in the service: writing a revision,
  asking for a translation, and starting a correction run. A correction changes what the
  public reads with no review and approval before it, unlike publication. Every member
  reads revisions and previews.
- **Removed members (ADR-0043).** A revision keeps the person's ID and the name they had
  then, and the page reads the name from the revision. Removing or demoting the person
  changes no revision. Their sessions end on their next request, so they correct nothing
  more. A translation they asked for finishes; it carries no requester.
- **Retired audiences (ADR-0044).** A retired audience's published note stays readable and
  can be corrected by hand: `aut_r4_probe`/vi is one of dbvault's three. It is never
  rendered or translated again (`409 audience_retired`), since a retired audience is never
  written a note, previewed, or translated. A correction run skips its actions with
  `audience_retired`, so the corrected text reaches a destination only after the audience
  is brought back.
- **A language the Organization no longer writes.** Its published notes are corrected by
  hand or rendered again. Translating again is refused.

### 7. The golden test and the dbvault fixtures

- **With no correction, nothing changes.** Every note, every field an API response has
  today, and every DeepL request is byte for byte what it is now, as ADR-0044 required of
  retirement. `GoldenReleaseNotesIntegrationTest` approves releases and compares what
  approval writes, which is revision 1, so its package and `MANIFEST.sha256` stand.
- **A capture stays append-only because revision 1 never moves.** The API gives each
  revision by number. This record proposes that a new fixture set keep writing revision 1
  to the path it has today (`notes/<audience>.<language>.md`) and add
  `notes/<audience>.<language>.r<n>.md` and the revision's reason and origin beside it.
  A file captured in an earlier set is then identical in every later one, and a correction
  only adds files. The harness and its fixtures are the workspace's to change.
- **Rendering again is deterministic and gets its own golden case.** Proposed: a second
  package from the workspace, beside the first, built from set `v2`: v1.2.0's notes as
  1.1.0-rc.2 published them, the cached answers, and the expected revision 2 of
  `operator`/vi and `contributor`/vi. The test renders both again and compares. The ten
  placeholders are the difference this record expects; anything else the comparison shows
  is what today's writer does differently from rc.2's, and is decided when it is seen.
- **Translating again is not in the golden test.** It depends on a glossary edit and a
  new DeepL answer, so it is checked in the lab, from recordings.

### 8. Cost

- **No AI request**, for any of it. A statement the AI got wrong is corrected by hand.
- **By hand and rendered again: no provider.** dbvault's ten placeholders cost nothing.
- **DeepL only when a person asks to translate again**, and the page shows the characters
  first: the total length of the note's texts with no answer under today's context and
  glossary version, worked out without sending anything. The request carries that number,
  and is refused and shown again if the count has grown. A `456` is a stop, never a retry.
- **A glossary change empties the cache for its pair**, since the version is part of every
  key. Fixing "Điểm cuối" by adding the term and translating again therefore sends all of
  a note's texts: for the three notes, at most the 23,673 characters v1.2.0's whole
  Vietnamese translation cost at approval. Seven edits by hand cost nothing.
- **A correction run** calls only the destinations a person picked.

### Schema and API

- **One migration, `V40`:** `release_note_revisions` and
  `public_changelog_entry_revisions` with their triggers; the revision an action run
  carried on `automation_action_runs`; and `CORRECTION` in the trigger checks. No existing
  row changes.
- **Endpoints**, all under the existing paths:
  - `GET …/releases/{id}/notes/{noteId}/revisions`, and `…/revisions/{n}`;
  - `GET …/notes/{noteId}/correction-preview?origin=RENDERED|TRANSLATED`, which answers the
    text, the missing texts, and the characters DeepL would be sent;
  - `POST …/notes/{noteId}/revisions`, and `POST …/notes/{noteId}/translations`;
  - `POST /api/automation/rules/{ruleId}/corrections`;
  - `GET /changelog/{slug}/releases/{entryId}/revisions/{n}`, public.
- **Changed responses:** a note gains its revision number, and its download a `revision`
  parameter. Every failure has a code and both interface languages.

## Consequences

- An error in a published note can be fixed without a release, and what was first
  published is still there to read, on the release page and in public.
- A published note is no longer one text. Anything that reads one must mean the latest
  revision or name a number.
- A public entry's address stops being cacheable for a day. A reader or proxy that cached
  it under the old header may show the original for up to 24 hours after the first
  correction.
- A feed subscriber sees a second item for a corrected note.
- Corrections skip review and approval, which is why they are for administrators and
  always carry a reason.
- Rendering again can show more than the fix, and the person decides. It is never applied
  to several notes at once.
- A correction and its delivery are two steps. Between them the release page shows which
  destinations are behind.
- GitHub, Confluence, and Notion are edited only while they still hold what ReleaseFlow
  wrote. A destination somebody touched is refused, and is fixed there by hand.
- A Zendesk article, and a Slack, Teams, or email message already sent, keep the old text.

## Size, and which release

- **Slice A — revisions inside ReleaseFlow.** By hand and rendered again; the release
  page; the public changelog, its feed, and the correction run for the public changelog
  alone. `V40`; six new endpoints and three changed; two new pages (correct a note with
  its preview, a public earlier revision) and five changed (release notes, the run page,
  the rule's run form, the public entry, the public list); one destination. No provider
  call. It covers all three of dbvault's damaged notes.
- **Slice B — providers.** Translating again, and corrections to GitHub, Confluence,
  Notion, Slack, Teams, and email, with Zendesk's refusal. No migration expected; one new
  endpoint; the correction form and run page changed; seven executors touched, three new
  provider calls (GitHub edit, Confluence read and update, Notion read and replace), and
  their stand-ins in the browser suite.
- **Both:** the guides, `docs/api.md`, the architecture and status pages, their Vietnamese
  pages, both message bundles, and the browser suite at both sizes.
- **Recommendation: implement in 1.4.0, Slice A first.** Everything else in 1.3.0 is
  merged, and issue #22 is done when this record is accepted. Together the slices are the
  largest change since automation itself: they touch release, changelog, automation, and
  translation, and each provider path needs its own acceptance on the lab. Slice A alone
  would fit 1.3.0 if the release can wait for it.

## Not decided here

- Unpublishing a release or withdrawing a public entry.
- Correcting several notes in one step, or carrying one correction to a note's other
  languages.
- Updating a Zendesk article in place.
- Letting a member propose a correction for an administrator to accept.

## Amendment, 2026-11-20: rendering again, what it changes in dbvault, and the skip

- Status: Accepted
- Tracked by: [#87](https://github.com/hoangluongtran0309/ReleaseFlow/issues/87)
- Answers: the three questions [ADR-0035's amendment](0035-evidence-bound-notes-and-audience-relevance.md#amendment-2026-11-19-an-action-whose-note-has-nothing-for-its-audience)
  left to #87

The decision above stands. This amendment decides one thing it left open, corrects two
statements of its Context, and decides how a corrected note meets ADR-0035's skip.

**What was found.** The dbvault workspace built the second golden package that part 7
proposes, `1.4.0-revisions`. It renders v1.2.0 again on ReleaseFlow develop at `0de37f7`,
from set `v2`'s recorded DeepL answers alone: 174 texts, 0 characters sent. The six English
notes come back byte for byte as rc.2 published them. Each of the six Vietnamese notes
changes, only in the letter case of whole tokens, 29 of them:

| Note | Placeholders | `dbvault-CLI` → `dbvault-cli` |
| --- | --- | --- |
| `contributor` / vi | 7 | 3 |
| `operator` / vi | 3 | 4 |
| `aut_r4_probe` / vi | 0 | 5 |
| `dba` / vi | 0 | 3 |
| `end_user` / vi | 0 | 2 |
| `support` / vi | 0 | 2 |

ADR-0040 restores a lowercase hyphenated identifier as well as a placeholder, and every
Vietnamese note of v1.2.0 has `dbvault-CLI` for the source's `dbvault-cli`. The Context
counted placeholders and "Điểm cuối" only, so it missed these.

### Which job rendering again reads

Part 2 reads each text "under the context and glossary version of the job that translated
it for this release". A translation job belongs to one change, or to the release for its
summary, and nothing links a note to the job it was written from. So:

- **The job** is the latest `SUCCEEDED` job of the same subject (the change, or the
  release) into the note's language that completed no later than the release's
  publication. Publication requires every note to be ready, and nothing writes a note
  after it, so that job's answers are the ones the note was published with; a job that
  finished later was never part of the note.
- **Each text is looked up by itself** in the cache, under that job's context and its
  glossary version, and then under the empty version: a text the job translated without
  its glossary, on its last attempt or after an answer that came back untranslated, is
  cached there (ADR-0036). The answer found is repaired as ADR-0040 repairs any answer.
- **The job's stored output is not read.** It is keyed by field, so a field whose source
  text changed since publication would get an answer to a different text. The cache is
  keyed by the text itself, so such a text finds nothing, and the preview marks and counts
  it as part 2 says.
- **A subject with no such job**, or a text found under neither version, is missing in the
  same way. A note in the release's source language reads no job.

The golden case seeds the cache with the package's answers under the context and the
glossary version of the jobs it writes; the package itself has neither, since set `v2`
holds no Project context and no glossary.

### Two corrections to the Context

- **Rendering again changes six notes, not two.** "The other nine: 0" is wrong for three
  of them: `dba`/vi, `end_user`/vi, and `support`/vi each have `dbvault-CLI`, and so does
  `aut_r4_probe`/vi. Rendering v1.2.0 again changes four notes besides `operator`/vi and
  `contributor`/vi. The golden case of part 7 therefore renders all six and compares each
  with the package's revision 2. "Điểm cuối" is unchanged by it, as part 2 says: it needs
  translating again, or a hand.
- **An error is outside ReleaseFlow.** `end_user`/vi carries `dbvault-CLI` twice, and rule
  R1 copied it to the public changelog and to Zendesk. So "no copy outside ReleaseFlow
  carries them" is wrong. Slice A corrects the public changelog's entry: rendering
  `end_user`/vi again, then a correction run. Zendesk keeps the old text
  (`correction_not_supported`), and the article is corrected there by hand.

`aut_r4_probe` is active in set `v2`, so its revision 2 in the package is a real render. On
an Organization where it is retired, rendering again is refused with `409 audience_retired`
(part 6), and the same text can be saved by hand as a `MANUAL` revision.

### A corrected note and the skip for nothing to say

ADR-0035's amendment skips an action whose note has nothing for its audience, unless a
person wrote the note, and left three questions to #87.

- **A revision written by hand is a note a person wrote.** A note whose latest revision is
  `MANUAL` is delivered as written, whatever the changes say, as a note edited before
  publication is. That is also part 2's rule that such a note never follows its template
  again. A `RENDERED` or `TRANSLATED` revision is not a person's text: the skip asks the
  changes for it as for a note that follows its template.
- **A revision rendered again agrees with the skip as of when it was rendered.** It is
  written from the release's changes as they read then, by the digest whose
  `ReleaseNoteDigest.concerns` the skip also asks. So rendering again closes the known
  limit of ADR-0035's amendment for that note: if nothing concerns the audience, the
  revision says so and the action is skipped; if something does, the revision lists it and
  the action delivers it. The limit opens again only if a change of the release is reviewed
  again or given a narrative after that revision, and a person can render the note again.
- **A correction run does not skip for nothing to say, and offers nothing that was never
  delivered.** A correction replaces what a destination already holds. Skipping it because
  the latest revision now has nothing for its audience would leave that destination with
  text the note no longer says, and only a hand could fix it. So in a `CORRECTION` run,
  `nothing_for_audience` is not checked; `audience_retired` still is, first, as ADR-0044's
  order of checks says. An action whose runs for the release were all `SKIPPED`, for either
  reason, has no `SUCCEEDED` run, so part 5 does not offer it: nothing was delivered there
  to correct. The form names such an action and says why. Running the rule again, by hand,
  is a first delivery of the latest revision, and is skipped or delivered by ADR-0035's rule
  like any other run.

**Consequences.**

- `ReleaseAccess.hasNothingFor` reads the note's latest revision: a `MANUAL` latest is
  delivered as written. With no correction, every run is what it is today.
- The correction run of Slice A skips only a retired audience's action, and the form lists
  a skipped-before action as not offered, with its reason.
- The golden case of part 7 renders the six Vietnamese notes of the second package and
  compares each byte for byte; the first package and its test are unchanged.
- `end_user`/vi's entry on the lab's public changelog is the correction the lab's 1.4.0
  acceptance checks for Slice A; its Zendesk article is corrected there by hand.
