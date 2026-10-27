# ADR-0036: An Organization glossary for translated notes

- Status: Proposed
- Date: 2026-10-27
- Amends: [ADR-0015](0015-multilingual-release-notes.md), for what a translation is
  asked with, the identity of its job and cache, and the release page
- Refers to: [ADR-0035](0035-evidence-bound-notes-and-audience-relevance.md), whose
  Project context stays DeepL's `context` and part of the input hash, unchanged

## Context

DeepL gets a change's texts, the language pair, and, since ADR-0035, the Project context.
Nothing fixes how a product term is translated
([#14](https://github.com/hoangluongtran0309/ReleaseFlow/issues/14)). The Vietnamese
notes of dbvault v1.1.0 translated:

- "console" as "giao diện dòng lệnh", which means command-line interface, the opposite
  of what the release did;
- "image" as "hình ảnh" (picture), "container base images" included;
- "pull request" as "yêu cầu kéo";
- "storage" as "bộ nhớ" (memory), and "flash messages" as "thông báo cảnh báo" (warning
  messages).

The release summary, which a person wrote in English, went the same way. Vietnamese
developers read these terms in English. Today the only remedy is editing every translated
note by hand, which makes the note Manual, so later summary corrections stop reaching it.

The context does not solve this: DeepL reads it as guidance, which binds no term.

What DeepL documents today (developers.deepl.com, read on 2026-10-27):

- **v3 multilingual glossaries** are the recommended API. One glossary holds a
  dictionary per language pair. `POST /v3/glossaries` creates one,
  `PUT /v3/glossaries/{id}/dictionaries` replaces one dictionary's entries in place, and
  v2 glossaries cannot be edited at all.
- A dictionary is created with **root language codes** (`en`, `pt`, never `en-US`), and
  applies to every regional target of its language.
- `vi` is in both `GlossarySourceLanguage` and `GlossaryTargetLanguage` of the published
  OpenAPI document. Which pairs an account can use is answered by
  `GET /v3/languages?resource=glossary`; a glossary needs the glossary feature on both
  languages.
- `POST /v2/translate` takes `glossary_id` only together with `source_lang`, which
  ReleaseFlow always sends, and only when the glossary has a dictionary for the request's
  pair.
- Entries: TSV or CSV, unique source phrases, neither side empty, no control characters
  or leading or trailing whitespace, at most 1024 UTF-8 bytes per phrase. Glossary
  endpoints answer `429`, `456` ("too many requests to the glossary endpoints"), and
  `5xx` like the translate endpoint, and `400`, `403`, `404`, and `413` otherwise.
- **Not documented**: whether an entry whose target equals its source (the way to keep a
  term as written) is accepted; whether `context` and `glossary_id` combine; and whether
  API Free keys may use glossaries. The design below does not depend on the answers being
  yes: each one, if no, is a sync or translation that fails visibly and never blocks.
  The real answers are checked against DeepL before this record is accepted
  (Consequences).

## Decision

### The glossary

- **An Organization has one glossary**, which administrators manage and members can
  read. It holds two kinds of entry:
  - a **kept term**, text that stays as written in every language: "CLI", "console",
    "image", "pull request";
  - a **fixed translation**, for one language pair: a source term and the target term it
    always becomes, such as `en → vi`, "storage" → "lưu trữ".

  A fixed translation names its two languages by their primary language (`en`, `vi`):
  DeepL applies a dictionary to every region of its language, and `vi-VN` and `vi` are
  already the same language to the translation queue (ADR-0015). Its languages are any
  two different ones the output language accepts; one that no note is written in yet is
  kept and used once it is.
- **What a pair's dictionary holds.** For a source and target language, the kept terms,
  each mapped to itself, and that pair's fixed translations. A fixed translation wins over
  a kept term with the same source text, for its pair only: keeping "console" everywhere
  and translating it for one language is a choice an administrator can make. A kept term
  that starts with a lowercase letter is also entered with its first letter capitalized,
  since it opens sentences ("Image digests are pinned"), unless that form is in the
  glossary already. The result is sorted, so the same glossary always produces the same
  dictionary.
- **Limits**:
  - at most 500 entries per Organization, kept terms and fixed translations together;
  - a term is 1 to 100 characters after surrounding whitespace is removed, on one line,
    with no control characters, which keeps every phrase far below DeepL's 1024 bytes and
    out of the TSV's separators;
  - a kept term is unique in the Organization, and a fixed translation's source term is
    unique in its pair. Uniqueness is exact: "Image" and "image" are two entries, as they
    are two phrases to DeepL.
- **Nothing is seeded.** Registration and upgrades add no entry. The page, and
  `GET /api/glossary/suggestions`, offer a fixed list of kept terms that are common in
  software release notes and not yet in the glossary: API, CLI, console, image, container
  image, digest, pull request, merge request, commit, branch, tag, repository, endpoint,
  exit code, webhook, access token, storage, and flash message. An administrator ticks the
  ones to keep and accepts them; nothing else is added. The list is code, not data, and
  changes in a pull request like any other wording.
- **REST**, additions only:

  | Method and path | Role |
  | --- | --- |
  | `GET /api/glossary` | member |
  | `GET /api/glossary/suggestions` | member |
  | `POST /api/glossary/kept-terms` | admin |
  | `DELETE /api/glossary/kept-terms/{termId}` | admin |
  | `POST /api/glossary/translations` | admin |
  | `DELETE /api/glossary/translations/{translationId}` | admin |

  `POST /api/glossary/kept-terms` takes a list, so accepting suggestions is one request
  that adds all of them or none. A fixed translation is changed by deleting it and adding
  another; the version (below) makes that the same as an edit. `GET /api/glossary`
  returns the entries and, for each language pair the Organization translates, the state
  of its last sync.
- **Problem Details**, each with a title and detail in both interface languages:
  - `400 validation_failed`, with field errors for a blank, overlong, multi-line, or
    control-character term, an invalid language, or a pair whose two languages are the
    same;
  - `404 glossary_term_not_found`;
  - `409 glossary_term_taken`, naming the term that is already there;
  - `409 glossary_limit`, when the request would pass 500 entries.
- **A page**, Glossary, in the navigation beside Audiences and Categories but shown to
  members too, in English and Vietnamese. It
  lists kept terms and fixed translations, shows each pair's sync state, offers the
  suggestions as a checklist, and is read-only to members. Its forms follow
  `CONTRIBUTING.md`: the server validates, and errors are written under their fields.
- **The glossary never reaches the AI.** The classification request, its prompt, its
  schema, and its user message are unchanged, so recorded AI replays stay valid. Only
  DeepL requests change.

### The version

- **A pair's glossary version** is the SHA-256 of the dictionary ReleaseFlow would send
  for it: its sorted TSV. An edit that leaves the dictionary as it was, such as deleting a
  term and adding it again, leaves the version as it was. A pair whose dictionary would be
  empty has no version.
- Editing an entry of one pair changes no other pair's version, except a kept term, which
  is in every pair.

### Job and cache identity

- `TranslationTexts.inputHash` adds the pair's glossary version beside the context.
  `textHash`, the cache key, adds the version a translation actually used. Without a
  version both are computed exactly as now, so an Organization with no glossary keeps
  every job and cached text it has, and upgrading translates nothing again.
- **A glossary edit re-translates at the next approval.** Approval asks for the texts with
  the current version; a changed version is a new input hash and a new job, and texts
  cached under the old version are not hits. Approval is where texts are asked for, so an
  edit made after a release is approved reaches it only if it returns to draft and is
  approved again; published notes never change.
- **Unchanged input is never sent twice.** The same texts, context, and version find the
  existing job, or every text in the cache, as they do now.
- A job records the version it asked for, which is part of its identity, and whether it
  used it. A text translated without the glossary it asked for is cached under no version,
  so it never answers a later request for the glossary.

### How the glossary reaches DeepL

- **One DeepL glossary per Organization**, v3 multilingual, named after the Organization's
  ID, with one dictionary per language pair it translates. ReleaseFlow stores its DeepL
  ID. Nothing else about the Organization leaves the system.
- **A sync job per pair.** `glossary_syncs` holds one row per Organization, source
  language, and target language: the version wanted, the version synced, a status
  (`PENDING`, `SYNCED`, `UNSUPPORTED`, `FAILED`), attempts, the next attempt time, the
  claim, and a fixed error code. When approval asks for a translation whose pair has a
  version that is not synced, it sets the row to that version and `PENDING`, in the
  approval's transaction, which writes only rows. Pairs are therefore synced when they are
  needed, not on every edit, and a glossary edit costs nothing until the next approval.
- **The worker**, like the translation worker: it claims a due row with
  `FOR UPDATE SKIP LOCKED` in a short transaction, holding the Organization's glossary row
  so one sync per Organization runs at a time; calls DeepL outside any transaction; and
  writes the result in another short one, only while it still holds the claim. In order:
  1. `GET /v3/languages?resource=glossary`. A pair whose two languages are not both
     listed with the glossary feature is `UNSUPPORTED`, and nothing is sent.
  2. Without a stored glossary, `POST /v3/glossaries` with this pair's dictionary, and
     the returned ID is stored. Otherwise `PUT /v3/glossaries/{id}/dictionaries`. A `404`
     means the glossary is gone, such as after a change of DeepL account: it is created
     again in the same attempt.
  3. A response that does not report as many entries as were sent is a failure.
- **Bounded retries.** `429`, `456`, `5xx`, and network failures are retried with the
  translation worker's backoff, at most five attempts in all. Anything else, `400`,
  `403`, and `413` included, is `FAILED` at once. A claim older than ten minutes is
  recovered as the translation worker's is. Errors are stored as fixed codes, never
  DeepL's message or body.
- **Translation waits for a pending sync, and never for a failed one.** A translation job
  whose pair is `PENDING` for its version is not due yet; the sync is bounded, so neither
  is the wait. Then:
  - `SYNCED` at the job's version: the request carries `glossary_id` with `context`, and
    the job records that it used the glossary;
  - `UNSUPPORTED` or `FAILED`: the request is made without `glossary_id`, exactly as now,
    and the job records that it did not use the glossary, and why;
  - no version for the pair: nothing changes.

  If DeepL refuses a translate request because of its glossary, the pair goes back to
  `PENDING` and the job is retried within its own five attempts; the last of them is made
  without the glossary, so a broken glossary never fails a translation that would succeed
  without it.
- **Without a translation provider nothing is synced**: translation jobs fail at once, as
  ADR-0015 decided.

### What the release page says

- For each target language, the release page and `GET` of the release say whether its
  notes' translations used the glossary: `applied`, `not_applied` with a reason
  (`unsupported_language_pair` or `glossary_sync_failed`), `pending`, or `none` when the
  pair has no glossary. A language is `applied` only when every translation behind its
  notes used the glossary at the version it asked for. A note never claims a glossary it
  did not use.
- `not_applied` never blocks approval or publication; the notes are ready as they are now.
  "Retry translations" also starts again a pair whose sync failed, and translates again
  the texts that were translated without their glossary, while the release is not
  published.
- A note edited by hand says it is Manual, as now; the glossary does not apply to words a
  person wrote.

### The release summary

- The release's own summary is translated through the same `ensure` as a change's texts
  ([ADR-0015](0015-multilingual-release-notes.md)): same pair, same version, same sync,
  same record of whether the glossary was used. Nothing is added for it, and the release
  page counts it with the rest.

### Not decided here

- What DeepL's `context` carries stays what ADR-0035 decided, the Project context. Adding
  the change's own text or the Project's name to it, as #14 also asks, would change the
  input hash of every translation and is left to its own decision.
- A glossary per Project. One Organization's products share their vocabulary, and a pair
  of glossaries in one request would need DeepL's `glossary_ids`; nothing has asked for it.

## Consequences

- An administrator who adds "console", "CLI", "image", "pull request", and "storage" gets
  them kept in every note approved afterwards, in every language DeepL serves with a
  glossary, the release summary included.
- An approval that needs a sync waits for it, a few seconds normally and at most the five
  bounded attempts, before its translations start. Approval itself stays offline.
- An Organization without a glossary sees no change: the same requests, hashes, and cache.
- The glossary leaves the system for DeepL, as the texts and the Project context already
  do. DeepL keeps a glossary until it is deleted; ReleaseFlow never deletes one, so a
  removed pair's dictionary stays there unused, and an Organization's glossary outlives
  the Organization in the DeepL account.
- Each Organization uses one of the DeepL account's glossaries, of which DeepL documents
  a limit of 1000 per account.
- DeepL matches glossary entries with its own handling of inflection and case, which
  ReleaseFlow does not control. A term DeepL still changes needs another form in the
  glossary, or a person's edit of the note.
- The API grows by six endpoints, three error codes, and fields on the release; nothing is
  removed or renamed, so 1.1.0 stays a minor release.
- **Before this record is accepted**, one approval against a real DeepL key, the dbvault
  v1.1.0 replay, must show that an `en → vi` dictionary of kept terms (entries mapped to
  themselves) is accepted, and that a translation sent with both `context` and
  `glossary_id` keeps those terms. If DeepL refuses entries mapped to themselves, kept
  terms need another mechanism and this record is revised before any code depends on it.
