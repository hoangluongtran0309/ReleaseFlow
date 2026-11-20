# ADR-0040: Restoring placeholders and identifiers in translated answers

- Status: Accepted
- Date: 2026-11-02
- Amends: [ADR-0036](0036-translation-glossary.md), for what a translation is recorded as
  once DeepL has answered
- Refers to: [ADR-0015](0015-multilingual-release-notes.md), for the translation queue and
  its cache
- Amended: 2026-11-19, for code spans; see
  [the amendment](#amendment-2026-11-19-code-spans)

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
  or identifier inside one is restored like any other. (Amended on 2026-11-19: code spans
  are restored as a third kind of token; see the end.)
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

## Amendment, 2026-11-19: code spans

- Status: Accepted
- Tracked by: [#107](https://github.com/hoangluongtran0309/ReleaseFlow/issues/107)

The decision above stands. A code span becomes a third kind of token a translation gets
back from its source, and the sentence saying code spans are not restored no longer holds.

**What was found.** That sentence held while every span in the lab's notes was an
identifier or a command. It does not hold for a span that holds words. The dbvault console
prints `under 1 s` and `0 s` in English in both of its languages. With those strings in code
spans in a change's headlines and bodies, the Vietnamese notes of dbvault v1.4.0 (dbvault
acceptance finding 30) have:

- `under 1 s` 6 times and `dưới 1 giây` 5 times;
- `0 giây` for `0 s` 3 times out of 3;
- both forms in one item: the Contributor headline keeps `under 1 s`, and its body has
  `dưới 1 giây`.

A code span tells a reader to type or look for exactly that text. A translated span names
something the product never prints, and nothing in the note says it was changed.

**Decision.**

- **A code span is the text between a backtick and the next one**, as `UntranslatedGuard`
  already reads it. Its text is what lies between the two backticks.
- **A text that this reading would misread gets no span back.** A fenced code block, a
  double-backtick span, or an odd number of backticks in the source or the translation
  would pair backticks that do not belong together. Such a text keeps its spans as DeepL
  wrote them, and is not logged as unpaired. Its placeholders and identifiers are still
  restored.
- **A span of the translation whose text the source writes is kept as it is**, whether the
  source writes that text inside a span or not. DeepL sometimes adds a span around a word
  the source writes bare, such as `bigserial` or `--target-id`. Such a span names what the
  source names, so it stays.
- **The source's spans are matched occurrence by occurrence.** Walking the source's spans in
  order, each one whose exact text the translation still holds as a span, and that no earlier
  source span has used, is held. The translation's spans that are neither kept nor held are
  its translated spans. The source's spans that are not held are its lost spans.
- **Translated spans are paired with lost spans in order, and each gets its source text
  back**, but only when there are as many translated spans as lost spans. Otherwise nothing
  is restored in that text, and the text is logged with the release or the change, the
  text's key, the language, and the two counts. The log never holds the spans' text. It is
  not retried and does not fail the job, as a lost placeholder is not.
- **Spans are restored first.** Placeholders and lowercase identifiers are then restored as
  above, outside the spans and inside the ones the translation kept. A placeholder inside a
  restored span comes back with its span, as the source wrote it.
- **Nothing outside a span changes**, and a translation with nothing to restore is kept byte
  for byte.
- **The rest of this record holds for spans unchanged.** The restore runs when a job
  succeeds, in the same pure function, on every text of its output, whether DeepL just
  answered or the cache did. The untranslated guard judges DeepL's raw answer. The cache
  keeps the raw answer, so an answer cached before this amendment is repaired at the next
  approval that uses it, without a request. The request body, its batches, the input hash,
  the text hash, and the cache key do not change.

**Quoted text is not protected.** In dbvault v1.4.1's Support · vi note, one body turned
'limit' into 'giới hạn' and the 'error' field into 'lỗi', while the same note's headline and
the other Vietnamese notes kept them. But quotes hold names and ordinary words alike, and
nothing in the text tells them apart. Giving quoted text back would undo correct
translations, such as a quoted label of the Vietnamese console. Instead:

- `docs/release-notes.md` and its Vietnamese translation say that a code span keeps a name
  as written and quoted text is translated, so a person editing a summary writes the code
  span, not the quotes.
- Asking the AI to put field names, parameters, and literal interface strings in code spans
  would change every classification request. That is left to 1.4.0, beside dbvault
  acceptance finding 32.

**Known limits.**

- **Two spans DeepL both reorders and translates get each other's text.** Pairing is by
  position, and nothing in a translated span says which source span it came from. A
  stricter rule, restoring only when exactly one span is lost, would leave unrepaired the
  case the lab showed: one text with both `under 1 s` and `0 s` translated. A test pins
  this swap, so the limit cannot change unnoticed.
- **A span DeepL translates into text the source also writes is kept.** It counts as a span
  around a source word. Into Vietnamese, from English, this has not been seen.

**Consequences.**

- A Vietnamese note shows each code span as its change wrote it, including notes whose
  translations were cached before, at their next approval. Published notes are not
  changed. Correcting them is
  [#87](https://github.com/hoangluongtran0309/ReleaseFlow/issues/87): its rendering again
  repairs answers as this record does, so it applies this too.
- Every DeepL request stays byte-identical, so the replay's recordings still match.
- `golden/dbvault/1.2.0-dev` is unchanged. Of its 331 DeepL answers, 7 hold a code span.
  In 5, the spans are the source's own. In the other 2, DeepL added a span around a word the
  source writes bare (`bigserial`, `--target-id`), which is kept. Nothing is restored, and
  `GoldenReleaseNotesIntegrationTest` passes on its current package.
- rf-regression set v4's Vietnamese notes of dbvault v1.4.0 would render differently. How
  its fixtures change is decided in the dbvault workspace.
- The release summary is plain text by design and has no code spans (dbvault acceptance
  finding 33), so it is not affected.
- **The tests:**
  - unit tests:
    - a kept span;
    - one translated span restored;
    - two translated spans in one text, both restored in order;
    - two spans DeepL reordered and translated, which get each other's text;
    - a span DeepL added around a bare source word, kept;
    - unequal counts, left as written and logged without the text;
    - a fenced code block, and a double-backtick span, each kept as DeepL wrote it or
      restored correctly, never broken;
    - a placeholder inside a translated span;
    - a translation with nothing to restore, kept byte for byte;
  - a DeepL stub test showing that the request body, the input hash, and the cache key are
    unchanged;
  - the golden test on its current package.
