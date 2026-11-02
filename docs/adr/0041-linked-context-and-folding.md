# ADR-0041: One piece of work once: branch and Linear keys, folding suggestions, and issue status at review

- Status: Accepted
- Date: 2026-11-02
- Amends: [ADR-0019](0019-jira-sources-and-linked-context.md), for where a key is found,
  which trackers a key links to, and when a linked issue's status is read
- Builds on: [ADR-0013](0013-context-sufficiency-and-duplicates.md), whose possible duplicates are
  evidence only, and [ADR-0033](0033-change-exclusion.md), whose exclusion takes a reason

## Context

One piece of work should appear once in a release note, linked to its tracker issues, with
each issue's status as it was when the release was reviewed. dbvault, the project
ReleaseFlow is tested against end to end, shows where that fails (issue #19):

- **v0.2.0** published `#21` and `DBV-12`, `#30` and `OPS-7`, and `#27` and `DBV-19` as two
  items each, and showed `DBV-44` "In Progress" in the release that shipped its fix.
- **v1.2.0**, accepted on 1.1.0, took in the stories `DBV-46`, `DBV-47`, and `DBV-48` as
  changes of their own beside `!10`, `#56`, and `#61`, and a reviewer rejected each one by
  hand. `#56` names its story only in its branch, `feature/DBV-47-retention-preview`, so
  nothing linked it. `!10` and `#61` were linked while their stories were In Progress, and
  that status is what their notes showed; `DBV-49` stayed In Progress while `#63` shipped.

What ReleaseFlow does today:

- `LinkedContextEnricher` scans a code change's title, description, target branch, and
  commit messages, and only for keys of the Project's Jira project. A Linear change gets no
  linked context, and a Linear identifier in a code change links to nothing.
- The head branch of a GitHub pull request and the source branch of a GitLab merge request
  are **already stored**, from webhooks and from the history import alike
  (`changes.head_branch`, V31, [ADR-0035](0035-evidence-bound-notes-and-audience-relevance.md)),
  but only to recognise a dependency update; the enricher does not scan them.
- `JiraKeys` refuses a key followed by a hyphen, as the old project did, and ADR-0019 and
  `docs/sources.md` state it: `feature/APP-3-export` names no issue.
- A linked issue keeps the title and status read when its change was recorded, and a note
  prints that status however long ago it was read.

The captured dbvault data says more about v0.2.0 than the issue's summary does:

- `#21` names `DBV-12` in its description only, as `Spec: DBV-12-quartz`: a key followed by
  a hyphen, outside a branch. Its branch is `feature/quartz-schedules`.
- `#30` and `OPS-7` share no key anywhere: not in the title, description, branch
  (`ci/rotate-s3-test-credentials`), or commits. They are tied by a possible duplicate
  (ADR-0013, similarity 0.947) that a person **confirmed**.
- `#27` and `DBV-19` share neither a key nor a duplicate candidate: the pull request, its
  branch (`docs/s3-storage-guide`), and its commits never name the story.

## Decision

### 1. Head branches are scanned for keys

- The enricher adds the change's stored head branch to the text it scans, after the target
  branch. A change recorded before V31 has none and is scanned as before; nothing is
  migrated, and no provider is asked for a branch it did not send.
- **Inside a branch name**, a key followed by `-`, `/`, or `_`, or by the end of the name,
  counts: `feature/DBV-47-retention-preview`, `DBV-47/preview`, and `bugfix/dbv-47_restore`
  each name `DBV-47`. What may come before a key is unchanged, and so is the rest of the
  pattern: the project's own key, case-insensitive, a number without a leading zero, and at
  most ten keys per change.
- **In a description or a commit message**, a key followed by `-` and a lowercase word
  counts too, as in `Spec: DBV-12-quartz`, but only when its prefix is the key of one of
  the Project's tracker sources: its Jira project key or its Linear team key (`DBV` and
  `OPS` for dbvault). Any other prefix keeps the strict rule, so `ISO-8601-compliant`
  never links. A key followed by `-` and anything else, such as `DBV-12-3` or `DBV-12-Q`,
  still names nothing. This links `#21` to `DBV-12`.
- **In a title and a target branch** the strict rule of ADR-0019 stays: a key followed by
  a hyphen names nothing.
- Tests show the false positives stay out: `XDBV-47`, `DBV-047`, `DBV-47a`, `DBV-4x`, a key
  of another project, a key inside a longer token such as `release/2026-DBV-47x`, and
  `ISO-8601-compliant` in a description of a Project whose trackers are `DBV` and `OPS`.

### 2. Linear identifiers link like Jira keys

- A Linear identifier, such as `OPS-7`, links to the Project's Linear source as a Jira key
  links to its Jira source: the same places scanned (including head branches, by the rule
  above), the same ten-key limit, case-insensitive, matched by the source team's key.
- The team key is not stored today, since a Linear source is identified by its team's ID.
  It is read once over GraphQL when a source is connected, and once for each existing
  source at its first enrichment after this change, and kept; a team whose key cannot be
  read links nothing and adds `LINKED_CONTEXT_UNAVAILABLE` to the change being enriched.
- Each identifier is read over Linear's GraphQL API with the source's token, outside any
  transaction, and kept only when the issue belongs to the source's team. The rules of
  ADR-0019 hold unchanged: linked issues are evidence only, never settle a change, and an
  identifier found but not read adds `LINKED_CONTEXT_UNAVAILABLE`.
- A Project with a Jira project and a Linear team of the same key reads the key from both;
  each source answers for its own issues.
- A Linear change itself still gets no linked context, as today.
- A linked issue records which tracker it came from (`JIRA` or `LINEAR`) and its status
  category, `open` or `done` (Jira's status category `done`; Linear's state type
  `completed` or `canceled`). Issues recorded before keep neither, and their category is
  unknown, which counts as not done.

### 3. Folding suggestions on a release's Review tab

- While a release is in review, ReleaseFlow suggests folding a **tracker change** (a Jira or
  Linear change) into a **code change** (a GitHub or GitLab change) of the same release
  when either:
  - the code change holds the tracker change's key as a linked issue, from either tracker;
    or
  - a person confirmed the two as a possible duplicate (ADR-0013).
- A suggestion names its evidence: the key and where it was found, or the confirmed
  duplicate and who confirmed it. It is computed from what is stored; it calls no provider.
- **Evidence only.** A person accepts or dismisses each suggestion; nothing is folded on
  its own, and a change is never merged into another or deleted.
- **Accepting** keeps the issue as the code change's linked context, adding it from the
  tracker change's own record when the code change does not hold it yet (a confirmed
  duplicate), and takes the tracker change out of the note, in one of two ways the person
  chooses:
  - **reject** it from the release, as a review decision ([ADR-0010](0010-release-review-lifecycle.md)), so it is available
    again; or
  - **exclude** it from releases (ADR-0033), with a reason prefilled as
    "`DBV-12` is tracked by `#21`, which carries the release-note entry." and editable.
- Each suggestion is decided once, by one person, with the time and, for an acceptance,
  the way chosen; a dismissed suggestion is not offered again in that release. A release
  returned to draft keeps its decisions.

### 4. Issue status as of review

- **Requesting review queues one read job for the release.** A worker claims it with
  `FOR UPDATE SKIP LOCKED` and reads each distinct linked issue of the release's changes
  again, from its own tracker, outside any transaction: a Jira issue's status and status
  category, a Linear issue's state. A read that fails is retried with backoff, at most
  five attempts in all; an issue still unread then keeps its recorded status. Returning to
  draft and requesting review again reads again.
- **What a note may show.** For each linked issue, templates get:
  - `status`: the status as of review when it was read, otherwise the recorded one;
  - `recordedStatus`: the status read when the change was recorded;
  - `statusAtReview`: the status as of review, empty when it was not read.

  **A status that is open, or whose category is unknown, is empty in every one of them
  unless a person accepted it**, so a published note never shows an open status that
  nobody chose to show.
- **An empty status prints nothing.** Every template ReleaseFlow ships that prints a status
  wraps it as `{{#status}} ({{status}}){{/status}}`; the presets print no linked issue
  today, so none changes yet. A template that prints `({{status}})` bare prints `()` for an
  empty status, so the upgrade note in the release-notes guide tells administrators to wrap
  theirs the same way, with a line of dba's template before and after. The CHANGELOG says
  that `{{status}}` is now the status as of review, and can be empty.
- **The release warns** on its Review tab, and in `GET` of the release, about each linked
  issue still open as of review while its change ships, with **Show this status**: a
  person accepts showing that status, once, recorded with their name, the time, and the
  status they accepted. A later read that returns another status asks again. The warning
  never blocks approval or publication.
- Notes written at approval use the statuses read by then. A read that finishes later
  renders again the notes that still follow their template, until publication, as a
  translation does; publication does not wait for a read.

### 5. Cost

No decision adds an AI request, a DeepL request, or a GitHub or GitLab request.

| Decision | Requests added |
| --- | --- |
| 1. Head branches | None: the branch is already stored from the webhook or the import's list. |
| 2. Linear keys | Linear GraphQL: one request per Linear source, once, for its team key; then, per code change that names Linear identifiers, one request per distinct identifier, at most ten, with ADR-0019's bounded retries. Only Projects with a Linear source. |
| 3. Folding | None: suggestions come from stored linked issues and duplicate decisions. |
| 4. Status at review | Per review request, one Jira request per distinct Jira key and one Linear request per distinct Linear identifier among the release's changes, each retried at most four more times. For dbvault's recorded links that is 8, 9, 6, 4, and 2 Jira reads for v0.1.0 to v1.1.0, before the keys decisions 1 and 2 add. |

## Consequences

- `#56` links `DBV-47` from its branch, and `!10`, `#56`, and `#61` each get a folding
  suggestion for their story in v1.2.0; accepting the three makes each piece of work one
  item, where a reviewer had to find and reject them by hand.
- In v0.2.0, `#21` links `DBV-12` from its description and gets a suggestion, and `#30` and
  `OPS-7` get one from their confirmed duplicate. `#27` and `DBV-19` get none, since no
  evidence ties them: a reviewer rejects or excludes `DBV-19` with a reason (ADR-0033), as
  today.
- `DBV-44`, `DBV-49`, and every story still open when its release is reviewed no longer
  print an open status in a note unless a person accepts it.
- The dbvault expectations this targets, once the workspace's replay acts on suggestions
  and statuses as a reviewer would: **E14** (no open status in v1.1.0's dba note) flips
  from the status rule alone; **E36** (one item per story in v1.2.0) flips when the three
  suggestions are accepted; **E06** (one item per pair in v0.2.0) flips for `#21` and
  `DBV-12` and for `#30` and `OPS-7` when their suggestions are accepted, and for `#27` and
  `DBV-19` only when the replay rejects or excludes `DBV-19`, since nothing suggests it.
- The golden release notes (#18) change where a linked issue's status is open: their dba
  template prints `({{status}})` bare, so those lines end in `()`, since nobody accepts the
  statuses in that run. The snapshots are regenerated from the workspace, never edited
  here.
- Custom templates that print `{{status}}` read the status as of review now, and wrap it
  in a section so that an empty status prints nothing.
- A Linear team renamed after its issues were written about keeps matching only its new
  key.
- One new job table and worker, for the reads at review, with the queue rules every other
  job follows; a table for folding decisions; a table for statuses as of review and their
  acceptances; a column for a Linear source's team key.

## Not decided here

- **Folding without evidence.** `#27` and `DBV-19` share nothing ReleaseFlow can see.
  Rejecting `DBV-19` from the release, or excluding it with a reason (ADR-0033), already
  covers such a pair. A reviewer's own "fold into…" action on the Review tab, choosing the
  code change, is a possible later action; this record does not add it.
