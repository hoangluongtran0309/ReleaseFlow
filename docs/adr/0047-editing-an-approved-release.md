# ADR-0047: Editing an approved release

- Status: Accepted
- Date: 2026-11-23
- Amends: [ADR-0010](0010-release-review-lifecycle.md), whose release can change only as a draft
- Refers to: [ADR-0015](0015-multilingual-release-notes.md) and
  [ADR-0036](0036-translation-glossary.md) (translating a note, its cache, and the glossary),
  [ADR-0037](0037-security-changes-and-release-note-structure.md) (the upgrade guide link),
  [ADR-0043](0043-member-roles-and-removal.md) (removed members), and
  [ADR-0046](0046-correcting-a-published-note.md) (correcting a published note)

This record decides the first part of issue #114 and writes no code. Nothing it describes
exists yet. The review history #114 also asks for is a decision of its own, tracked in
#143 for 1.5.0.

## Context

A release can change its version, summary, upgrade guide link, and previous release only
while it is a draft (`409 release_status_conflict`, `notDraft`). Correcting any of them
after approval means returning to draft, which deletes every release decision, the
approval, and the notes (ADR-0010). In the 1.3.0 acceptance run, correcting dbvault v1.4.0's
summary that way took two returns to draft and three approvals (dbvault finding 33).

Everything else a note is written from already changes while the release is approved: a
change's summary and narratives can be edited, and the notes that follow their templates are
rendered again from their stored templates (`ReleaseService.editSummary`); a note can be
edited by hand. The release's own summary and its upgrade guide link are the only texts of a
note that cannot.

The same run found a second, smaller fault. Editing one audience's narrative changed two
notes, but moved the update time of all five Vietnamese notes, though four were unchanged byte
for byte. `AudienceReleaseNote.rerender` moves `updatedAt` whenever the content, the
translation status, or the glossary use changes. The translation status is the language's,
shared by every note in it, so one new text to translate marks every note of that language
`PENDING`, then `READY` again, and each step moves its update time.

## Decision

### 1. The summary and the upgrade guide link change without a return to draft

- **While a release is in review or approved,** its summary and its upgrade guide link can be
  edited, through the release's existing edit (`PUT …/releases/{releaseId}` and the release
  page). Each is validated exactly as it is today: the summary is plain text of at most 2000
  characters, and the link is an absolute `http` or `https` URL that
  `ReleaseLinkBuilder.validatedUpgradeGuideUrl` accepts, or empty.
- **The version and the previous release stay draft-only.** The version is the tag the release
  is published as and names its notes, downloads, and compare link; the previous release
  decides the compare link and which release this one follows. Changing either outside a draft
  is still `409 release_status_conflict` (`notDraft`). The edit leaves both out, or sends them
  unchanged.
- **In review,** no note exists yet, so the edit changes only the release.
- **Once approved,** the notes that follow their templates are rendered again from their stored
  template and footer snapshots, as an edit of a change's summary renders them. A note a person
  edited keeps its text; the page names each such note as still showing the earlier summary or
  link, so it can be edited by hand. Nothing else about the release changes: its changes, their
  decisions, and its approval stay as they are.
- **The release says it changed after approval.** For each of the two fields, the release
  records who last changed it after approval, under their name then, and when. The release
  page shows "Summary changed after approval by {name} on {date}" and the same for the link,
  beside the approval, so whoever publishes knows the approver did not see that text. The API
  returns the same. A removed member keeps their recorded name (ADR-0043). Returning to draft
  clears both, with the approval; publication freezes them with the release.
- **It takes the release lock first,** like every write to a release (#119, #122): the row is
  locked before anything else is read or written, so an edit and a correction, an approval, or
  a run being created for the release serialise.
- **Who may edit:** every member who may approve the release. Today that is every member,
  administrators and members alike (ADR-0010). This grants no member anything new: any member
  can already return a release to draft, edit it, and approve it again. The difference is that
  the edit no longer costs the release's review, and is now recorded. The edit and the approval
  check the same rule, so if approval is ever restricted to a role, editing an approved release
  is restricted with it.

### 2. Publication is the boundary

A published release never changes: the database refuses any update of its row
(`reject_published_release_mutation`, V8). After publication, its summary and link are part of
its notes, and a note is corrected by a new revision (ADR-0046), by hand or rendered again,
and delivered by a correction run. Rendering a published note again reads the release's summary
and link as they were published.

### 3. What an edit costs

- **Approval's own rule holds:** the edit never calls DeepL. It records translation jobs, which
  the worker runs after the edit commits, and publication waits until every note is ready again
  (ADR-0015).
- **An edit of the summary sends the new summary's characters, once for each language the
  release is translated into, and nothing else.** Every other text of the release is the same
  text under the same Project context and glossary version as at approval, so its translation
  is found and reused. An unchanged summary sends nothing.
- **An edit of the link sends nothing.** The link is not translated.
- **One exception, unchanged from today:** if the Project context or the glossary changed since
  the release was approved, rendering its notes again translates every text of the release under
  the new context and version, as editing a change's summary already does. When it applies,
  the page warns before the edit is saved and names how many characters would be sent to DeepL,
  counted the way ADR-0046's correction preview counts `missingCharacters`: the source
  code points of each distinct text that has no translation under the new context and version,
  once for each language it would be translated into. Counting sends nothing.

### 4. A note's update time moves only when its text does

`updatedAt` is when the note's content last changed. A re-render that leaves the content as it
was still records a new translation status and glossary use, without moving `updatedAt`.

What reads a note's `updatedAt` today, so that the fix stops nothing that relied on the status
moving it:

| Reader | Reads `updatedAt`? |
| --- | --- |
| REST: each note of `GET …/releases/{releaseId}` and `GET …/notes` (`docs/release-notes.md`) | Yes, the only reader. For a published note with revisions, it is already the latest revision's time. |
| The release page and every other template | No |
| The translation poll (`app.js`, `translationPoll`) | No: it reads `translationStatus`, which still changes |
| ETags, `Last-Modified`, the note download | No: no note response carries a validator or a date header |
| Automation (`AutomationRunFactory`, the correction offer, `NoteDeliveries`) | No: they read the content, the revision, and the translation status |
| The public changelog | No: it keeps its own `published_at` and `corrected_at` |
| Sorting, queries, constraints, triggers | No: nothing orders by, queries, or constrains it beyond `NOT NULL` |
| Tests | No test asserts it |
| Outside ReleaseFlow | dbvault's capture tools record the notes API, which is how finding 33 was seen |

So the one visible effect is the API field itself, which now means what its name says. Because
dbvault's capture tools record that field, the code change's dbvault check shows the replays
still identical after normalization, and finding 33's case, an edit of one audience's narrative,
leaving the update time of each note it did not change as it was.

### 5. The release summary is plain text

The release summary is plain text, not Markdown: Markdown in it is printed escaped, and a code
span in it is translated like the rest of the text. This is how it works today and is not a
defect; `docs/release-notes.md` and its Vietnamese page say so.

## Consequences

- A typo in an approved release's summary or upgrade guide link costs an edit and, for the
  summary, its translation, instead of a return to draft and a second review of every change.
- An approved release is no longer exactly what was approved: its summary and link may have
  changed since, and the release page and API say who changed them and when.
- ADR-0010's "its change list and decisions cannot change without returning to draft" still
  holds. Its "A release can return to draft from `IN_REVIEW` or `APPROVED`" is unchanged; it is
  only no longer needed for these two fields.
- The note API's `updatedAt` stops moving for a note whose text did not change.

## Size

- **One migration**, the next free version when it is built: the two fields' "changed after
  approval" records on `releases` (who, under what name, and when), each set only while the
  release is approved, cleared on a return to draft, and frozen at publication.
- **No new endpoint or error code.** The release's edit accepts the two fields in review and once
  approved; `409 release_status_conflict` (`notDraft`) still refuses the version and the
  previous release.
- **Pages:** the release page's edit form outside a draft, the "changed after approval" lines,
  and the list of notes a person edited. Both message bundles, and the browser suite at both
  sizes.
- **Docs:** `docs/release-notes.md` and `docs/api.md`, their Vietnamese pages, the architecture
  and status pages, and `CHANGELOG.md`.

## Not decided here

- The review history of a change (#143, 1.5.0).
- Editing the version or the previous release after a draft.
- Editing a published release, other than through ADR-0046's revisions.

## Amendment, 2026-11-23: holding a re-translation, the notes a person edited, and the update time

Building the decision showed that three parts of it were not enough as written.

### Holding an edit that re-translates the release

Part 3's exception warns on the page before the edit is saved, but a caller of the API has no
page. Its Size says "No new error code".

- **An edit that would translate more than its new summary is held until the caller echoes
  its cost.** That happens only once approved, when the Project context or a glossary changed
  since approval, so that rendering the notes again sends texts other than the summary. The
  edit then carries `retranslationCharacters`, the count it was shown. Without it, or when the
  count is no longer the current one, the answer is `409 release_edit_retranslates`, with
  `characters`, the current count, and `languages`, the languages that would be sent anything.
  Nothing is saved or queued.
- **The count follows part 3.** It includes the source code points of each distinct text
  without a translation under the current context and glossary version, the new summary
  included, once for each language. Counting sends nothing, and without a translation provider it is `0` and
  nothing is held.
- **An edit that sends only its new summary is never held**, and neither is an edit in review,
  which has no notes.
- **A link-only edit is held too** when the Project context or a glossary changed since
  approval. This qualifies part 3's "An edit of the link sends nothing": the link itself is
  never translated, but saving it renders the notes again, and that translates every text of
  the release under the new context and version, exactly as an edit of the summary would.
- **The page sends the count it displayed.** A held edit returns the page with the warning, the
  count, and the same text; saving again sends that count.
- So there is **one new error code**, `release_edit_retranslates`.

### A published release

Part 2 makes publication the boundary. The edit takes the release lock and then checks
publication, as every write to a release does, so an edit of a published release, or one that
waited behind a publication, answers the existing `409 release_published`, not
`release_status_conflict`. A publication that waited behind an edit finds the notes as the edit
left them: while they are being translated again, it answers `409 translations_not_ready`.

### The notes a person edited

Part 1's page names a note a person edited "as still showing the earlier summary or link". It
names one only when its last edit by a person came before the latest change of the summary or
the link after approval: a note edited after that change may already say it. That needs the
time of the person's edit, which a note did not keep, and `updatedAt` is not it.

- **A note records when a person last edited it** (`last_edited_at`, `V42`), with who did. Every
  edit from now on sets both; the database allows the time only with an editor, and not the
  other way round.
- **`V42` gives a note of an approved release that a person edited its `updated_at` as that
  time:** a re-render never writes such a note, so it is still the time of the edit.
- **A note a person edited before `V42` on a release already published has no
  `last_edited_at`:** the database refuses any write to a published release's notes
  (`release_audience_notes_follow_release_status`, `V13`), and that is not bypassed. The API
  returns `null` for it. Nothing reads it there: the list is shown only while a release is
  approved.

### The update time moves only when the ready text does

Part 4 has `updatedAt` move when the note's content changes. That is still not enough in
dbvault finding 33's case. While a language is being translated, every note of it is rendered
with its changes' new text untranslated, then rendered again with the translation, which for
four of the five Vietnamese notes is the text they had before. Their content changes twice and
comes back, and each change moved `updatedAt`.

- **`updatedAt` is when the note's ready text last changed.** A note records the SHA-256 of its
  text when it was last ready (`ready_content_sha256`, `V42`). A re-render records the content,
  translation status, and glossary use it gives; `updatedAt` moves only when the note is ready
  and its text differs from that digest, which then becomes the new one. While its language is
  translating, or a translation failed, the note shows its interim text and keeps its time.
- **A person's edit moves `updatedAt` and sets the digest**, since their text is ready as written.
  A note that was not ready at approval gets its first digest, and a new time, when it becomes
  ready.
- **`V42` sets the digest of each ready note of an approved release**, from its content, and none
  for a note that is pending or failed, or of a published release, which is never rendered again.
- **So a note that passes through `PENDING` with its text unchanged keeps its `updatedAt`.**
  This replaces part 4's rule, that `updatedAt` moves whenever the content changes: it follows
  the ready text, through `ready_content_sha256`, and finding 33's four unchanged Vietnamese
  notes keep their time.
- Nothing in part 4's table of readers changes: `translationStatus` still moves with the
  language, and the translation poll reads that.

### Size

The one migration is `V42`. Besides the two fields' records on `releases`, it adds
`last_edited_at` and `ready_content_sha256` to the notes and fills them for approved releases'
notes only.
