# ADR-0036: An Organization glossary for translated notes

- Status: Accepted
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
  OpenAPI document. Which languages an account can use in a glossary is answered by
  `GET /v3/languages?resource=glossary`.
- `POST /v2/translate` takes `glossary_id` only together with `source_lang`, which
  ReleaseFlow always sends, and only when the glossary has a dictionary for the request's
  pair.
- Entries: TSV or CSV, unique source phrases, neither side empty, no control characters
  or leading or trailing whitespace, at most 1024 UTF-8 bytes per phrase. Glossary
  endpoints answer `429`, `456`, and `5xx` like the translate endpoint, and `400`, `403`,
  `404`, and `413` otherwise.
- **`456` is a stop, never a retry.** The glossary reference calls it too many requests,
  but DeepL's [error-handling page](https://developers.deepl.com/docs/best-practices/error-handling)
  lists every cause under one status: the character quota, a Cost Control or key-level
  limit, and "the account already has the maximum number of glossaries its plan allows",
  returned by creating a glossary. Its advice for `456` is "No. Retrying will not succeed
  until the limit is raised, the period resets, or (for glossaries) you delete one". The
  same page retries `429`, `529`, `500`, `503`, and `504` with exponential backoff, honours
  `Retry-After`, and retries nothing else.
- **Not documented, so checked against DeepL** on 2026-10-27 with an API Free key, by
  creating an `en → vi` glossary of six entries mapped to themselves ("console", "image",
  "images", "pull request", "storage", "digest"), translating one sentence with the
  Project context twice, without and with it, and deleting the glossary:
  - `GET /v3/languages?resource=glossary` lists `en` and `vi`, each `usable_as_source`
    and `usable_as_target`, with an empty `features` object. Being listed is what says a
    language can be used in a glossary; `features` says nothing about it.
  - An entry whose target equals its source is accepted: `201`, `entry_count` 6.
  - `context` and `glossary_id` combine. Without the glossary the sentence got
    "giao diện điều khiển", "hình ảnh", "mã băm", "yêu cầu kéo (pull request)", and
    "lưu trữ"; with it, "giao diện console", "các image", "digest", "pull request", and
    "storage". DeepL kept each term in English and still wrote the Vietnamese around it:
    it added "giao diện" before "console" and "hệ thống" before "storage", and wrote
    "images" as "các image", with Vietnamese's plural marker, although the dictionary
    mapped "images" to itself.
  - Glossaries are available on API Free.
- **A second check**, the same day with the same key, repeated the first one with the same
  answers, then asked about capitals:
  - Creating a second glossary, with "image" and "Image" both mapped to themselves, while
    the first still existed, was answered `456 Quota exceeded`. DeepL judged no entry, so
    whether one dictionary may hold both forms is still unknown. This is the error-handling
    page's glossary-limit cause, and it suggests that this API Free account's plan allows
    one glossary at a time. The number is inferred from the check: the page says only that
    the limit is the plan's.
  - "Image digests are pinned for every container image, and the console lists them."
    became, without a glossary, "Mã băm hình ảnh được ghim cho mỗi hình ảnh container, và
    bảng điều khiển liệt kê chúng.": "Image" and "image" as "hình ảnh", "console" as "bảng
    điều khiển". With the glossary of lowercase entries it came back in English: "Image
    digests are pinned for each container image, and the console lists them." Only
    "every" changed. "Image", "image", and "console" stayed as written because nothing
    was translated, so it says nothing about how a lowercase entry treats a capitalized
    word, and a translation that returns its source text is a failure the design must
    not report as success.
  - The sentence with both forms was not translated, since that glossary was never made.
- **A third check**, the same day with the same key, made one glossary at a time and
  deleted each before the next:
  - With lowercase entries, "Image digests are pinned for every container image, and the
    console lists them." came back in English three times out of three, once with "digests
    are" rewritten as "digest is". "The image digests are pinned…" came back in English
    too, and rewritten: "The images are digested and pinned…".
  - A dictionary with both "image" and "Image" mapped to themselves is accepted (`201`, 7
    entries). With it, "Image", "digest", and "console" were kept, and the later "image"
    became "hình ảnh" both times.
  - A glossary of "image" alone kept both "Image" and "image": a lowercase entry kept the
    capitalized word.
  - Tag handling, with no glossary and every term wrapped in an ignored `<x>` tag, kept
    every tagged term in all three answers, and once wrote a Vietnamese translation before
    four of them: "hình ảnh cơ sở (images)", "mã băm (digest)", "yêu cầu kéo (pull
    request)", "lưu trữ (storage)".
- **A test on dbvault's own texts** decided between the glossary and tags. Its texts are
  35 of the English texts DeepL translated for the dbvault regression fixture, 8,029
  characters: all 26 of v1.1.0, and the 9 of v0.2.0 and v0.3.0 with the most fixed pairs.
  They hold 62 kept terms and 22 fixed pairs of the glossary approved for that fixture
  (40 kept terms and 6 fixed pairs). Each request sent all 35 texts, as one approval does,
  with the Project context, and each variant ran three times:

  | Variant | Kept terms | Capitalized | Fixed pairs | Untranslated | Translation beside a term |
  | --- | --- | --- | --- | --- | --- |
  | A. One glossary: kept terms mapped to themselves, and the fixed pairs | 52–53 of 62 | 4 of 4 | 16–17 of 22 | 0 | 0 |
  | B. Kept terms in ignored tags, no glossary | 56 of 62 | 1 of 4 | 6 of 22 | 0 | 18–19 |
  | C. B, and a glossary of the fixed pairs only | 56–57 of 62 | 1 of 4 | 17 of 22 | 0 | 18 |
  | D. B, with each fixed pair's target written into the text in a tag | 56 of 62 | 1 of 4 | 20 of 22 | 0 | 17–18 |
  | E. B, and `custom_instructions` not to add a translation beside a tagged term | 56 of 62 | 1 of 4 | 6 of 22 | 0 | 19 |

  - "Kept terms" counts a plural as its term, since "images" written "các image" keeps it.
    Most of A's fixed pairs counted as missed are `/api/targets` inside code paths, which
    DeepL rightly left alone; A applied practically every fixed pair. Its real misses were
    "image" as "hình ảnh" in two texts, "engine" as "công cụ" and "secret" as "bí mật" in
    one each, and plurals written as their singular ("digests" as "digest").
  - With tags, DeepL translated the term and then wrote the tagged English after it in
    brackets, "hình ảnh cơ sở (base images)", "yêu cầu kéo (PRs)", "thông báo cảnh báo
    (flash messages)", about 18 times a run: the problem #14 reports, put back. In 4 texts
    a run, a term written twice lost its tag the second time and was translated. Tags keep
    only the case they wrap. The Free plan accepted `custom_instructions` for Vietnamese,
    and they changed nothing: E answered word for word as B did. D rewrote code paths
    (`'/api/targets'` became `'/api/đích'`), the risk of ReleaseFlow rewriting the text
    itself.
  - DeepL accepted a glossary together with tag handling (C), which it does not document.
  - **Check 3's English answers did not recur** on batched real texts: none of the 525
    answers was in English, by both rules below and by reading them.
- **Two rules for an answer that was not translated** were measured on the same data, after
  removing kept terms, fixed targets, code spans, URLs, and words written in capitals or
  with a capital inside (MongoDB, HTTP):
  - R1: a sentence with at least three words left to translate, none of them carrying a
    Vietnamese diacritic;
  - R2: at least half of an answer's words are words of its source text.

  Both flagged all three English answers of check 3 and neither of its Vietnamese ones, and
  neither flagged any of 972 correct Vietnamese translations: the 447 of the fixture and the
  525 of the test. R2's overlap on those never passed 0.37, with a median of 0.03. All of
  that is Vietnamese, which shares almost no words with English, so it shows nothing about
  R2 for a language that does.

## Decision

### The glossary

- **An Organization has one glossary**, which administrators manage and members can
  read. It holds two kinds of entry:
  - a **kept term**, a word or phrase that stays in its source language in every
    translation: "CLI", "console", "image", "pull request". DeepL adapts the words around
    a kept term, and may adapt its form to the target language's grammar, as "images"
    became "các image" in Vietnamese; a kept term is not frozen text;
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
  and translating it for one language is a choice an administrator can make. The result
  is sorted, so the same glossary always produces the same dictionary.
- **Capitals.** A kept term is entered only as written; no capitalized copy is added. A
  lowercase entry kept the capitalized word in the third check and in all 4 of 4 places in
  the test (Context), and adding "Image" beside "image" kept nothing more. An
  administrator who meets a form DeepL still changes adds it as its own entry.
- **Limits**:
  - at most 500 entries per Organization, kept terms and fixed translations together;
  - a term is 1 to 100 characters after surrounding whitespace is removed, on one line,
    with no control characters, which keeps every phrase far below DeepL's 1024 bytes and
    out of the TSV's separators;
  - a kept term is unique in the Organization, and a fixed translation's source term is
    unique in its pair. Uniqueness is exact: "Image" and "image" are two entries, and DeepL
    accepts both in one dictionary (Context).
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
  1. `GET /v3/languages?resource=glossary`. A pair is `UNSUPPORTED`, and nothing is
     sent, unless its source language is listed `usable_as_source` and its target language
     `usable_as_target`. `features` is not read: it is empty for `en` and `vi`, whose
     glossary works.
  2. Without a stored glossary, `POST /v3/glossaries` with this pair's dictionary, and
     the returned ID is stored. Otherwise `PUT /v3/glossaries/{id}/dictionaries`. A `404`
     means the glossary is gone, such as after a change of DeepL account: it is created
     again in the same attempt.
  3. A response that does not report as many entries as were sent is a failure.
- **Bounded retries.** `429`, every `5xx` (`529` included), and network failures are
  retried with the translation worker's backoff, at most five attempts in all. Anything
  else is `FAILED` at once: `400`, `403`, and `413`, and `456`, which DeepL's
  error-handling page says will not succeed until a glossary is deleted or the limit is
  raised (Context), stored as `quota_exceeded` as the translator already stores a `456`. A
  claim older than ten minutes is recovered as the translation worker's is. Errors are
  stored as fixed codes, never DeepL's message or body.
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

### An answer that was not translated

The third check got its English answers with a `200`, so a translation that returns its
source text must not be stored as one that succeeded. Every answer for a guarded target
language is checked, text by text, before it is stored or cached, by the rule for its
script:

- **Latin script with diacritics in nearly every sentence: R1** (Context). The languages
  are a closed list in the code, and it holds only Vietnamese, the language measured.
  German, French, Spanish, and the like use diacritics, but a correct sentence of theirs
  often has none ("Das ist ein Test"), so R1 would refuse correct translations; they fall
  under the last rule.
- **A non-Latin script: no character of that script** in a sentence with at least three
  words left to translate, words removed as for R1. The script of each language is a
  closed list in the code: Cyrillic for Russian, Ukrainian, and Bulgarian; Greek; Arabic;
  Hebrew; Thai; Han for Chinese; Han, Hiragana, or Katakana for Japanese; Hangul for
  Korean. This rule is not measured: it follows from the script, since a correct
  translation into it writes its words in it.
- **Any other target: no guard, a known gap.** R2 is the candidate, but it was measured on
  Vietnamese only, and a language that shares words with English, such as Dutch or
  Indonesian, may reach its threshold in a correct translation. It is not adopted until it
  is measured on such languages.

What follows a failed check:

- A text that fails, and was translated with the glossary, is translated again without it,
  once, in the same attempt, which counts as no extra retry. If its new answer passes, it
  is stored as translated without the glossary, with the reason
  `glossary_answer_untranslated`, and cached under no version.
- A text that fails without the glossary, the first time or again, makes the job `FAILED`
  with the fixed code `untranslated`, like any other failed translation: a person can retry
  it or edit the note, and it is never retried on its own.
- A text that fails is never cached. Translations already in the cache are not checked
  again.

### What the release page says

- For each target language, the release page and `GET` of the release say whether its
  notes' translations used the glossary: `applied`, `not_applied` with a reason
  (`unsupported_language_pair`, `glossary_sync_failed`, or `glossary_answer_untranslated`),
  `pending`, or `none` when the
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

### DeepL's context

- #14 also asks for DeepL's `context`. ADR-0035's Project context meets that request: it
  is already sent as `context` with every translation of the Project's texts, the release
  summary included, and is part of the input hash.
- The change's own text, such as its what-changed sentence in the source language, is not
  added. It would change every translation request, and so every input hash and cache key,
  without a measured need: the terms #14 reports are what the glossary fixes, and nothing
  shows that the change's text would have kept them.

### Tag handling for kept terms: tested and rejected

Kept terms could instead reach DeepL through its tag handling: each text sent as XML
(`tag_handling=xml`, `ignore_tags=x`, `split_sentences=1`), with `&`, `<`, and `>`
escaped and every kept term wrapped as `<x>console</x>`, matched by ReleaseFlow exactly, on
word boundaries, longest first, and unwrapped from the answer. It needs no glossary, so no
sync and no limit per plan, and it works for every language pair.

It is rejected on the test in Context. DeepL left each tagged term as written and then
translated it beside the tag, about 18 times a run, which is the problem #14 reports; it
dropped the tag of a repeated term in 4 texts a run; `custom_instructions` changed nothing;
and a matcher of ReleaseFlow's own rewrote code paths when fixed pairs were written into the
text. The glossary (variant A) kept a few terms fewer, never glossed one, applied the fixed
pairs, kept the capitalized forms, and leaves the text a person or the AI wrote as it is.

### Not decided here

- A glossary per Project. One Organization's products share their vocabulary, and a pair
  of glossaries in one request would need DeepL's `glossary_ids`; nothing has asked for it.

## Consequences

- An administrator who adds "console", "CLI", "image", "pull request", and "storage" gets
  them kept in every note approved afterwards, in every language DeepL serves with a
  glossary, the release summary included.
- An approval that needs a sync waits for it, a few seconds normally and at most the five
  bounded attempts, before its translations start. Approval itself stays offline.
- An Organization without a glossary sends the same requests, with the same hashes and
  cache, as before. Its answers into a guarded language are checked too, so an answer in
  the source language now fails where it was stored before; none of the 447 translations
  in the dbvault fixture would have.
- Answers into a Latin-script language other than Vietnamese are not checked: a
  translation returned in the source language there is stored as before, until a rule is
  measured for it.
- The glossary leaves the system for DeepL, as the texts and the Project context already
  do. DeepL keeps a glossary until it is deleted; ReleaseFlow never deletes one, so a
  removed pair's dictionary stays there unused, and an Organization's glossary outlives
  the Organization in the DeepL account.
- Each Organization uses one of the DeepL account's glossaries, of which DeepL documents a
  limit of 1000 per account and the error-handling page a limit per plan. If an API Free
  account holds only one glossary, as the second check suggests, a deployment on such a
  key serves the glossary of one Organization; every other Organization's sync is `FAILED`
  with `quota_exceeded`, shown on its release page, and its notes are translated without a
  glossary.
- DeepL matches glossary entries with its own handling of inflection and case, which
  ReleaseFlow does not control, and writes the target language around them: a kept term
  stays in English, but not necessarily character for character, and the words beside it
  are DeepL's. A term DeepL still translates needs another form in the glossary, or a
  person's edit of the note.
- The API grows by six endpoints, three error codes, and fields on the release; nothing is
  removed or renamed, so 1.1.0 stays a minor release.
- What DeepL does not document was settled by the checks and the test in Context, before
  this record was accepted. Whether the dbvault v1.1.0 Vietnamese notes keep the terms,
  #14's own test, is checked again with the implementation, before its pull request is
  merged.
