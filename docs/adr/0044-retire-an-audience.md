# ADR-0044: Retiring an audience without deleting it

- Status: Accepted
- Date: 2026-11-12
- Amends: [ADR-0011](0011-audience-release-notes.md), whose every audience gets a narrative
  and a note, and whose audience in use can never stop; and the rule that an Organization
  keeps at least one audience, which becomes at least one active audience
- Refers to: [ADR-0009](0009-automatic-ai-classification.md) (one AI request per change),
  [ADR-0035](0035-evidence-bound-notes-and-audience-relevance.md) (narratives
  and relevance), [ADR-0015](0015-multilingual-release-notes.md) and
  [ADR-0036](0036-translation-glossary.md) (translation), and
  [ADR-0020](0020-automation-rules-and-runs.md) (actions)

## Context

An audience that a release note uses cannot be deleted (`409 audience_in_use`), and
nothing else stops it. Every later release pays for it three times:

- **The AI request.** `AudienceService.briefs` lists every audience, so the one request per
  change carries each audience's code and intent, and its response schema asks for a
  narrative for each.
- **Approval.** `ReleaseService.writeNotes` writes one note per audience per release
  language.
- **Translation.** `ReleaseNoteWriter.texts` sends a change's neutral summary and every
  narrative it stores, keyed `narrative.<code>` and `headline.<code>`, so a narrative is
  translated into every release language whether its note is read or not.

dbvault's `aut_r4_probe` was added before v1.1.0 to test an automation rule. Every
release since has produced a note for it that nobody reads.

Four facts of the current model decide what follows:

- **Narratives live on the change**, in `audience_narratives`, keyed by code, and each
  change gets exactly one AI request. A narrative that was never asked for is never
  asked for later.
- **A person's summary edit replaces the whole map.** `Change.editSummary` takes the
  narratives for the codes `briefs` lists and drops the rest.
- **Notes are written at approval and frozen at publication.** An approved release
  already holds its notes; the notes of a published one cannot change (ADR-0005).
- **The translation cache is per text.** A translation job is keyed by the hash of all of a
  change's texts, but each text is looked up in the cache on its own, and only the texts
  not in it go to DeepL.

## Decision

### Retiring and bringing back

- **An audience is active or retired.** `POST /api/audiences/{id}/retire` and
  `POST /api/audiences/{id}/restore` answer the audience's view. On the Audiences page,
  **Retire** asks first and names the enabled rules that deliver the audience;
  **Bring back** does it at once. Administrators only, by URL rule and again in the
  service. Both pages and Problem Details are written in both interface languages.
- **The row stays.** Retiring sets `retired_at`, `retired_by`, and `retirer_name`; bringing
  it back clears them. Its code, name, intent, templates, footers, and language templates
  are kept, and remain editable while it is retired.
- **Who did it and when.** `audience_status_changes` records every retirement and every
  return, with the person's ID, the name they had then, and the time. Rows are only ever
  inserted. The Audiences page shows a retired audience's retirement, and each audience's
  most recent change.
- **Codes.** `409 audience_last_active` for the last active audience,
  `409 audience_already_retired` and `409 audience_not_retired` for a step that changes
  nothing, `404 audience_not_found` as today.

### What a retired audience gets

- **No narrative in the AI request.** `briefs` lists active audiences only. A change
  classified while the audience is retired is asked for no narrative for it, and its schema
  lists no such code.
- **No note at the next approval**, and no note preview: `writeNotes` and `previewNotes`
  read active audiences only.
- **No translation.** `ReleaseNoteWriter.texts` leaves out the narratives and headlines of
  retired audiences. A change translated while the audience was active therefore gets a new
  job, because its set of texts changed; every text it shares with the old job is read from
  the cache, so DeepL is not asked again for any of them.
- **Its narratives are kept.** Narratives already written for it stay on their changes,
  untouched. A person's summary edit keeps the stored narratives of retired audiences and
  replaces only those of active ones, so editing a change never erases a narrative written
  for an audience that is only resting.
- **An Organization that retires nothing is unchanged byte for byte.** With no retired
  audience, `briefs`, the translation texts, and the notes are what they are today, so the
  AI and DeepL requests — and the replay's cassettes and the golden snapshots — match
  exactly.

### Releases under way

- **Draft or in review:** nothing is written yet, so the next approval writes no note for
  the retired audience.
- **Approved:** the notes were written at approval and stay, the retired audience's
  included, with any translation still under way; publishing publishes them. Retiring an
  audience is about the releases to come, and silently removing a note from an approved
  release would change what was approved. To drop it from that release, return the release
  to draft and approve it again.
- **Published:** its notes stay as they are — readable on the release page, downloadable,
  and in the public changelog wherever an action put them. Nothing published refers to an
  audience's state.

### Bringing it back

- **The next approval writes its note again**, and the next AI request asks for its
  narrative.
- **A change classified while it was retired has no narrative and no relevance for it**,
  and the automatic request is not made again: each change gets exactly one automatic AI
  request (ADR-0009).
- **The note always includes such a change, and never drops it.** With no relevance, the
  change counts as fully relevant (ADR-0035), so it is in the note under its neutral
  headline — its `whatChanged` — with no narrative. A release in review when the audience
  comes back is approved with a note for it, built that way.
- **The Review tab lists those changes**: the changes of the release that the AI
  classified and that have no narrative for an active audience, grouped by audience. For
  each, a person either writes the narrative on the release page, or asks the AI for the
  missing narratives. That request is the same evidence the change was classified from,
  asking only for the audiences without a narrative; it writes those narratives and nothing
  else — never the category, the flags, the summary, or a narrative that exists — so it
  never undoes a recorded review or a person's words. It is made only when a person asks,
  never automatically, and the page shows its rough cost before it is sent: the number of
  requests and the estimated tokens, from the length of the request it would send. A full
  second classification is not offered, because it would replace what a human review
  settled.
- **An approved release stays as it was approved**, without a note for it; returning it to
  draft and approving it again writes one.

### At least one active audience

- **The service locks first, then counts.** Retiring, like deleting, starts with
  `AudienceRepository.lockAllByOrganizationId` (`SELECT … FOR UPDATE` on the
  Organization's audiences), so two administrators who retire the last two audiences at
  once run one after the other, and the second is refused with `409 audience_last_active`.
  Deleting keeps its own code, `409 audience_last`, and now refuses whenever it would leave
  no active audience, not only no audience at all.
- **A deferred constraint trigger is the backstop.** `audience_definitions_keep_an_active_audience`
  fires at commit on an update that changes `retired_at`, locks the Organization's row,
  and raises when no active audience remains, so two writers that bypass the service still
  cannot both commit. The service maps that failure to the same `409`.

### Automation

- **Enabling a rule** with an action for a retired audience is refused with
  `409 automation_audience_retired`. A rule is still written freely.
- **An action that reaches a retired audience is skipped, not failed.** A failed action
  stops the actions behind it (ADR-0020), so failing would let one retired audience stop
  every other audience's delivery in the same rule; dbvault's R1 Announce has nine actions.
  The worker checks the audience when it claims the action, before any call, and records
  the action as `SKIPPED` with `audience_retired`, even when a published note for that
  audience exists; then it goes on to the next action.
- **`SKIPPED` is a new terminal state of an action run.** It carries its reason, like a
  failure, but it never stops the run, a run's outcome is decided by its other actions, and
  a run whose every action was skipped ends `SUCCEEDED` with nothing delivered. A skipped
  action is not retried: it attempted nothing. It records no delivery metric. To deliver
  once the audience is back, run the rule again.
- **The run page marks a skipped action** with its reason, in both interface languages.
- **Retiring does not disable rules.** The confirmation names the enabled rules that
  deliver the audience, and the Automation page marks each one until it is brought back or
  its action changed.

### Presets and languages

- **A preset can be retired** like any audience, and its reset to the shipped version
  still works while it is retired; it stays retired.
- **A new release language** gives every audience, retired ones included, a template in
  that language, as today. It costs nothing to keep, and an audience brought back has a
  template in every language.
- **Registration** seeds the presets active, as today.

### Cost

Retiring saves, for each audience retired:

- **AI, per change:** the audience's brief in the request — its code and intent, about 275
  characters for `aut_r4_probe`, roughly 70 tokens — plus its entry in the response schema,
  roughly 40 tokens more; and the narrative in the answer, which averaged 189 characters
  for `aut_r4_probe` over 41 changes on the dbvault lab, roughly 50 output tokens plus
  about 15 for its keys and relevance. In all about 110 input and 65 output tokens per
  change.
- **DeepL, per change and extra release language:** that narrative's headline and body,
  about 190 characters for `aut_r4_probe`.
- **Per release:** both of the above times the number of changes. A release of 20 changes
  with one extra language saves about 2,200 input and 1,300 output tokens and about
  3,800 DeepL characters, and one note per release language that nobody reads.

Retiring and bringing back themselves send nothing to the AI, DeepL, or any provider.
Asking the AI for the missing narratives of a returning audience costs one request per
change, about as much as the change's classification did, and is sent only when a person
asks.

### Schema

- `V39` adds `retired_at`, `retired_by` (composite foreign key to `app_users`), and
  `retirer_name` to `audience_definitions`, with a check that they are all set or all
  null; adds `audience_status_changes`; creates the constraint trigger; and lets
  `automation_action_runs` be `SKIPPED`, with an error code and a completion time like a
  failure. No existing row changes, so every audience starts active.

## Consequences

- An audience nobody reads stops costing AI tokens, DeepL characters, and a note per
  release, and nothing it produced is lost.
- An audience brought back has gaps: the changes classified while it was retired have no
  narrative for it, and are never asked again.
- An approved release keeps the note of an audience retired after its approval, unless it
  is approved again.
- A person's summary edit no longer erases the narratives of retired audiences. Before, an
  edit kept only the audiences it listed; now it keeps every narrative it does not replace.
- A rule keeps delivering its other audiences while one of them is retired.
- An Organization that retires nothing behaves exactly as before.

## Not decided here

- Deleting an audience together with its notes, and archiving its published notes.
- Asking the AI again, for a returning audience, about changes classified while it was
  retired.
