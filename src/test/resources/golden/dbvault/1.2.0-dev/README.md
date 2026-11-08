# ReleaseFlow golden package for 1.2.0-dev (#19, ADR-0041)

Real render inputs from dbvault, the project used to test ReleaseFlow end to end, and the notes
ReleaseFlow wrote from them on branch `feature/linked-context-and-folding`: five releases
(v0.1.0, v0.2.0, v0.3.0, v1.0.0, v1.1.0), six audiences, English and Vietnamese. A golden test
approves these releases with AI off, DeepL answered from `deepl/` and the reads at review answered
from `trackers/`, and compares every note byte for byte with `snapshots/`.

The files are generated from the `rf-regression` repository (fixture set `v1` with overlay
`adr-0041`) and are never edited by hand. To change them, regenerate them from a new render.

## Where the notes come from

`snapshots/` holds the notes of the render replay of ReleaseFlow **`4d2844f`** (#19, on
`feature/linked-context-and-folding`), run `20261108T103837Z-4d2844f-render-adr-0041`, reported in
rf-regression commit **`1783f71`** (`reports/2026-11-08-4d2844f-render/`). That render had AI off,
used DeepL cassettes only (331 texts from cassettes, 0 misses, 0 characters sent to DeepL), made
**only the captured decisions** (no folding suggestion accepted), and had 0 stub misses.

Each note was checked against report `1783f71` before it was copied, as for 1.1.0: against
`fixtures/releaseflow/snapshots/v1` with the report's diff applied, or, for a note the report
counts as new, against the sha256 the run recorded.

## Differences from the 1.1.0 package

38 of the 46 notes are byte-identical to `1.1.0/snapshots/`. The 8 dba notes of v0.2.0 to v1.1.0
(en and vi) differ only in their `Tracked in` / `Theo dõi tại` lines:

- **Status as of review** (12 lines per language): `In Progress` → `Done` for DBV-16 (v0.2.0),
  DBV-21, DBV-22, DBV-23, DBV-24 (two lines), DBV-25, DBV-26 (v0.3.0), DBV-30, DBV-33, DBV-34
  (v1.0.0) and DBV-44 (v1.1.0); and DBV-31 in v1.0.0, `(To Do)` before, now prints no status: it
  was still In Progress at that review, open, and nobody accepted it. dba's template wraps the
  status as `{{#status}} ({{status}}){{/status}}` (overlay `adr-0041`).
- **New links** (3 lines per language), which ADR-0041 adds at intake: `#21` → DBV-12 (`Spec:
  DBV-12-quartz` in its description), `#36` → OPS-8 (a Linear identifier), `#48` → OPS-13 (branch
  `ci/OPS-13-pin-image-digests`). `#54` → OPS-13 too, but `#54` is excluded.

**The golden test at `4d2844f` cannot reproduce the new links.** It inserts each change from
`record`, whose `linkedIssues` are the captured ones, and does not run the enricher. So its
v0.2.0, v0.3.0 and v1.1.0 dba notes (en, vi) lack those three lines. The package carries what the
render linked as `linkedIssuesReplayed` (and `linkedContextStatusReplayed`) on those four changes
in `inputs/changes.json`, beside the unchanged `record`; a test that inserts `linkedIssuesReplayed`
when present reproduces the snapshots.

## Files

| Path | What it is |
|---|---|
| `inputs/organization.json` | As in 1.1.0, with overlay `adr-0041`'s dba templates. |
| `inputs/changes.json` | All 91 changes in intake order, as in 1.1.0; four of them also carry `linkedIssuesReplayed` (above). |
| `inputs/releases/<version>.json` | One release, as in 1.1.0. |
| `snapshots/<version>/<audience>.<language>.md` | The 46 notes (25 en, 21 vi), byte for byte as downloaded. |
| `deepl/answers.jsonl`, `deepl/glossary-en-vi.tsv` | The 331 DeepL answers and the glossary, as in 1.1.0 (the new lines send no new text: issue titles and status names are not translated). |
| `trackers/review-reads.jsonl` | **New.** The answer to each read at review, one JSON object per line: `release`, `tracker`, `key`, `title`, `type` (Jira only), `status`, `category` (Jira's status category key, or Linear's state type); 32 lines (8, 10, 7, 4, 3). |
| `MANIFEST.sha256` | sha256 of every other file; check with `sha256sum -c MANIFEST.sha256`. |

### Reads at review

Each answer is the status the issue had **when the release was created** (the review request
was not captured; it lies between creation and approval, and no captured status changed in
that window), taken from the capture's **Jira changelog** and **Linear state history** (user
decision, 2026-11-03): what ReleaseFlow's read would have seen. All Jira statuses come from the
changelog; OPS-8 and OPS-13 come from Linear's history. Jira categories are derived from
dbvault's workflow (To Do → new, In Progress → indeterminate, Done → done); Linear's `completed` is
captured. Titles and types are the ones the records hold. Only DBV-31 in v1.0.0 is open
(`In Progress`, `indeterminate`). The render's stubs served exactly these 32 answers at review;
the builder checks that. Built by rf-regression `tools/trackers/build_review_reads.py`; the rule
and every status's source are in `cassettes/v1/trackers/README.md`.

### Audiences

`operator`, `contributor`, `end_user` and `support` are **reset to their preset**. The presets
are ReleaseFlow's own: `AudiencePresets.java` at `c05b28c`, blob `727cc26`. `support` was not in
the captured Organization: it was added under its code and reset before intake
(`addedBeforeIntake`). The render reset all four again when it set the release languages, so
their vi templates are the shipped ones too.

`dba` and `aut_r4_probe` are audiences an administrator created. Each one carries its display
name, its communication intent, its templates (`main` and `vi`) and its footers (`main` and
`vi`), from overlay `adr-0041` (dba's templates differ from `adr-0037`'s only by the status wrap above). They were added at their place in the `timeline`: `dba` before
v0.2.0, and `aut_r4_probe` before v1.1.0.

### Order of the render

1. Register the Organization and create the Project and its sources.
2. Set the Project context and the glossary. Create any missing categories. Reset the preset
   audiences.
3. Take in every change in `changes.json` order, then exclude the 28 changes that have an
   `exclusion`. Then make the two captured decisions on possible duplicates: `OPS-7` ~ `#30`
   **confirmed**, `#41` ~ `#39` dismissed (fixture `duplicate-candidates.json`; they change no note
   here, and they are what lets ADR-0041 suggest folding `OPS-7` into `#30`).
4. Follow `timeline`. For a `release` event: create the release, add `members`, request review,
   and **wait for the read at review** (`linkedIssueRead` `SUCCEEDED`). Write each
   `summaryWritten` from the capture, send each decision (no folding suggestion is decided), and
   approve. Approving v0.2.0 answers `409 change_summary_required` for `#28`: write its
   `summaryWritten` (from the overlay) and approve again. Then download the notes and publish.

### Decisions

`action` is what the render sent. `capturedAction` is what the captured release recorded. They
differ for 11 changes, where `capturedAction` is `APPROVE` but `action` is `EDIT`: with AI off,
the rules classified these changes differently, and an `EDIT` restored the captured category
(`category`, `breaking`). `security` is the flag the decision confirmed, as ReleaseFlow showed it
when review started, and `securityFlaggedBy` says who set it. The rules flagged five changes:
`#12`, `#25`, `OPS-6`, `OPS-7` and `#44`.

### DeepL answers

Every request named the same glossary (`glossary-en-vi.tsv`) and sent the Project context. So a
stub can answer each request by **(`source_lang`, `target_lang`, `text`)** alone: that triple is
unique across the 331 lines. It should still check that the request names a glossary with these
entries and sends this context. In each line, `"context": "projectContext"` stands for
`organization.json`'s `projectContext`, and `"glossary": "en-vi"` stands for
`glossary-en-vi.tsv`.

`cassette_sha256` is the name of the rf-regression cassette that served the text. It is the sha256 of
this JSON, built with Python's `json.dumps(key, sort_keys=True, ensure_ascii=False)`:

```json
{"text": …, "source_lang": "EN", "target_lang": "VI", "context": <projectContext>,
 "glossary": {"source_lang": "EN", "target_lang": "VI", "entries": [[source, target], …]},
 "options": {}}
```

The builder recomputed every one of these hashes from this package alone.

The answers were recorded from DeepL API Free on 2026-10-31, in rf-regression commit `c804be2`.
`billed_characters` and `recorded_at` are kept from the cassette.

## Sources (rf-regression, at `1783f71`)

| Input | Path | Last commit |
|---|---|---|
| Captured changes, releases, decisions, exclusions, duplicates, config | `fixtures/releaseflow/v1/normalized/` (`MANIFEST.sha256` verified) | `d3eaf6c` |
| Project context, glossary, audience intents/templates/footers, v1.0.0 upgrade guide, #28's summary | `overlays/adr-0041/` | `57af8dd` |
| DeepL answers | `cassettes/v1/deepl/` (331 files) | `c804be2` |
| Answers at review | `cassettes/v1/trackers/review-reads.json` | `b922b4f` |
| Notes, and what the render sent | `.runs/20261108T103837Z-4d2844f-render-adr-0041/` (not in git), checked against `reports/2026-11-08-4d2844f-render/report.json` | `1783f71` |
| Comparison snapshot for the check | `fixtures/releaseflow/snapshots/v1/` | `7d300e3` |
| Replay that made the render | `replay/driver.py`, `replay/stubs.py` | `ce31745` |

ReleaseFlow image: `rfreplay-build:4d2844ff848a5f36f3ca`, built from `git archive` of `4d2844f`.

## Scrubbing

These files hold no tokens, secrets, webhook ids or paths, signatures, cookies, CSRF tokens, API
keys or email addresses. Fixture v1 was already scrubbed this way (`v1/SCRUBBED.md`). On top of
that, this package leaves out the webhook payloads, the provider records, every lab id of the
Organization, Project, categories and sources, and the Linear team and workspace ids. A scan of
every file found none of the `releaseflow-lab/.env` credentials (the rule of
`tools/capture/scrub.py`), no email of any kind, and no token-shaped text. gitleaks 8.30.1 (the
version ReleaseFlow's CI installs) found nothing with its default rules.

These are kept on purpose:

- public URLs: GitHub `hoangtulangtoc/dbvault`, GitLab `dbvault1/dbvault-cli`, Linear
  `linear.app/dbvault`, and the Jira site `hoangluongtran0309.atlassian.net`. They are the item
  links of the notes.
- `localhost:8090`, `localhost:8080` and `127.0.0.1:27017`. These are dbvault's documented
  defaults, quoted in its pull requests, and some of them appear in the notes.
- display names, and the lab ids of two dismissed duplicate candidates in `record.reviewTriggers`,
  as in the fixture.
