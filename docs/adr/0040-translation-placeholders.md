# ADR-0040: Restoring placeholders and identifiers in translated answers

- Status: Accepted
- Date: 2026-11-02
- Amends: [ADR-0036](0036-translation-glossary.md), for what a translation is recorded as
  once DeepL has answered
- Refers to: [ADR-0015](0015-multilingual-release-notes.md), for the translation queue and
  its cache

## Context

The 1.1.0 acceptance run translated dbvault v1.2.0 into Vietnamese with a glossary
(FINDINGS #23 in the lab). Sent with `glossary_id`, DeepL changed the letter case of every
angle-bracket placeholder in the 12 notes, all 10 of them:

- `<count>` → `<COUNT>`;
- `<backup-id>` → `<BACKUP-id>`;
- `<target-id>` → `<TARGET-id>`.

It did the same to a hyphenated name written in lowercase: `dbvault-cli` came back as
`dbvault-CLI`, beside the kept term `CLI`. The same texts sent without a glossary, or with
only the Project's `context`, come back as written, as they did on 1.0.x, which sent no
glossary. Code spans, CLI flags, environment variables, URLs, issue keys, and API paths came
back identical. The regression replay's recorded glossary answers have shown the same since
2026-10-28 (`<ID>`, `<TARGET-id>`).

A placeholder names what a reader types in its place, and `dbvault-cli` is the name of a
program: `<BACKUP-id>` and `dbvault-CLI` name something the change did not write, and
nothing in the note says they were changed.

Whatever the fix, it cannot change what is sent. The replay compares every DeepL request
byte for byte with its recordings, a translation is cached by its source text, context,
and glossary version (ADR-0036), and tag handling, which could protect a token from DeepL's
side, changes every request and was rejected for kept terms in ADR-0036.

## Decision

- **Two kinds of token are kept as the source writes them:**
  - a **placeholder**: `<`, a letter, then letters, digits, hyphens, or underscores, then
    `>`, such as `<count>`, `<backup-id>`, or `<target_id>`;
  - a **hyphenated identifier written in lowercase in the source**: letters and digits
    joined by hyphens, standing alone, such as `dbvault-cli`.

  Code spans, URLs, and paths came back intact and are not restored as such; a placeholder
  or identifier inside one is restored like any other.
- **A token is restored only where the translation differs from the source in letter case
  alone**, and only when the match is unambiguous: a token the source writes in more than
  one case, such as `<id>` and `<ID>`, is left as DeepL wrote it, and so is an identifier
  the source does not write in lowercase. Nothing else in the translation changes, and a
  translation with nothing to restore is kept byte for byte.
- **Restoring happens when a job succeeds**, in one pure function applied to every text of
  its output, whether DeepL just answered or the cache did. The untranslated guard keeps
  judging DeepL's raw answer, before any restore, so the requests it leads to are the ones
  sent before. The cache keeps DeepL's raw answer, so an answer cached before this record
  is repaired at the next approval that uses it, without a request.
- **A placeholder the translation lost is left lost and logged** with the release or the
  change and the language. It is not retried and does not fail the job: no request is
  added for it.
- **Requests do not change.** The request body, its batches, the input hash, the text hash,
  and the cache key are computed from the source texts exactly as before.

## Consequences

- Every DeepL request is byte-identical to one sent before this record, so the replay's
  recordings still match, and the glossary keeps being sent.
- A Vietnamese note shows each placeholder and lowercase identifier as its change wrote
  it, including notes whose translations were cached before, at their next approval.
  Published notes are not changed.
- A placeholder DeepL drops stays dropped, and only the log says so; the lab measured none.
- Only these two kinds of token are restored. A word DeepL capitalizes at the start of a
  sentence, such as `Sign-in` for a source `sign-in`, is a hyphenated lowercase identifier
  too, and gets the source's lowercase back. `{name}`, `$NAME`, or `%s` are not covered
  until one is measured to change.
