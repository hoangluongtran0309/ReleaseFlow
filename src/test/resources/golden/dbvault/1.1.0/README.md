# ReleaseFlow golden package for 1.1.0 (#18)

Real render inputs from dbvault, the project used to test ReleaseFlow end to end, and the notes
ReleaseFlow wrote from them: five releases (v0.1.0, v0.2.0, v0.3.0, v1.0.0, v1.1.0), six
audiences, English and Vietnamese. A golden test approves these releases with AI off and DeepL
answered from `deepl/`, and compares every note byte for byte with `snapshots/`.

The files are generated from the `rf-regression` repository (fixture set `v1` with overlay
`adr-0037`) and are never edited by hand. To change them, regenerate them from a new render.

## Where the notes come from

`snapshots/` holds the notes of the render replay of ReleaseFlow **`c05b28c`** (PR #54), run
`20261102T083055Z-c05b28c-render-adr-0037`, reported in rf-regression commit **`1c76be9`**
(`reports/2026-11-02-c05b28c-render/`). That render had AI off, used DeepL cassettes only (331
texts from cassettes, 0 misses, 0 characters sent to DeepL), and produced no lost-placeholder warning.

ReleaseFlow **v1.1.0** (`61e372a`), **v1.1.0-rc.3** (`aa33927`) and `c05b28c` share one tree
(`f06b8cd`), so v1.1.0 writes these same notes from these inputs.

Each note was checked against report `1c76be9` before it was copied. The 6 notes the report
found identical to `fixtures/releaseflow/snapshots/v1` are byte-equal to that snapshot. The 31
notes the report found different are equal to that snapshot with the report's diff applied. The
9 notes the report counted as new (no v1.0.0 snapshot) are equal to the sha256 the run recorded
when it downloaded them.

Set `v2` (dbvault v1.2.0) is left out: its captured notes are 1.1.0-rc.2's and carry FINDINGS #23.

## Files

| Path | What it is |
|---|---|
| `inputs/organization.json` | The Organization and Project as the render set them up: name, output language, the **Project context** (sent to DeepL as `context`), the **glossary** (48 kept terms, 6 fixed translations), release languages, categories, sensitive-path additions, the four sources (public keys only), the six **audiences**, and the **timeline** of configuration changes and approvals. |
| `inputs/changes.json` | All 91 changes in intake order (by captured `mergedAt`). Each has its `releases`, its `exclusion` (28 changes, with the reason), `summaryWritten` (the body sent to `PUT …/changes/{id}/summary`, with the audience narratives) and where that came from, and `record`, the change as the captured ReleaseFlow held it. |
| `inputs/releases/<version>.json` | One release: its summary, upgrade guide, previous release, languages, `members` (in captured order), `decisions` (what was sent for each member, see below), the summaries written at approval, `atApproval` (each member's classification, security flag, summary and per-audience relevance as ReleaseFlow showed them after approval), the glossary status, and the notes it produced with their sha256. |
| `snapshots/<version>/<audience>.<language>.md` | The 46 notes (25 en, 21 vi; v0.1.0 has no vi, because the languages were set after it), byte for byte as downloaded from `…/notes/{id}/download`. |
| `deepl/answers.jsonl` | The 331 DeepL answers that render used, one per line, sorted by `cassette_sha256`. |
| `deepl/glossary-en-vi.tsv` | The en→vi glossary dictionary that ReleaseFlow created on DeepL, and that every one of the 331 requests named: 54 entries, `source<TAB>target`, in the order the request key holds them. |
| `MANIFEST.sha256` | sha256 of every other file; check with `sha256sum -c MANIFEST.sha256`. |

### Audiences

`operator`, `contributor`, `end_user` and `support` are **reset to their preset**. The presets
are ReleaseFlow's own: `AudiencePresets.java` at `c05b28c`, blob `727cc26`. `support` was not in
the captured Organization: it was added under its code and reset before intake
(`addedBeforeIntake`). The render reset all four again when it set the release languages, so
their vi templates are the shipped ones too.

`dba` and `aut_r4_probe` are audiences an administrator created. Each one carries its display
name, its communication intent, its templates (`main` and `vi`) and its footers (`main` and
`vi`), from overlay `adr-0037`. They were added at their place in the `timeline`: `dba` before
v0.2.0, and `aut_r4_probe` before v1.1.0.

### Order of the render

1. Register the Organization and create the Project and its sources.
2. Set the Project context and the glossary. Create any missing categories. Reset the preset
   audiences.
3. Take in every change in `changes.json` order, then exclude the 28 changes that have an
   `exclusion`.
4. Follow `timeline`. For a `release` event: create the release (summary, `upgradeGuideUrl`;
   the previous release is the default, the last one published), add `members`, and request
   review. Write each `summaryWritten` from the capture, send each decision, and approve.
   Approving v0.2.0 answers `409 change_summary_required` for `#28`: write its
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

## Sources (rf-regression, at `1c76be9`)

| Input | Path | Last commit |
|---|---|---|
| Captured changes, releases, decisions, exclusions, config | `fixtures/releaseflow/v1/normalized/` (`MANIFEST.sha256` verified, sha256 `7f6201a2…`) | `d3eaf6c` |
| Project context, glossary, audience intents/templates/footers, v1.0.0 upgrade guide, #28's summary | `overlays/adr-0037/` | `a46f5e6` |
| DeepL answers | `cassettes/v1/deepl/` (331 of its 1,320 files) | `c804be2` |
| Notes, and what the render sent | `.runs/20261102T083055Z-c05b28c-render-adr-0037/` (not in git), checked against `reports/2026-11-02-c05b28c-render/report.json` | `1c76be9` |
| Comparison snapshot for the check | `fixtures/releaseflow/snapshots/v1/` | `7d300e3` |
| Replay that made the render | `replay/driver.py`, `replay/stubs.py` | `7c1679e` |

ReleaseFlow image: `rfreplay-build:c05b28c4ce1662ab2c27`, built from `git archive` of `c05b28c`.

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
