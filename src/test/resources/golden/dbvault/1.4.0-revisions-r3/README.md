# ReleaseFlow golden package for 1.4.0: correcting a published note (ADR-0046 §7)

The second golden package that ADR-0046 §7 proposes, beside the first (`../1.1.0/`). It holds
dbvault **v1.2.0** as ReleaseFlow **1.1.0-rc.2** published it, the DeepL answers its Vietnamese
notes were written from, and the **expected revision 2** of each of its six Vietnamese notes:
the same note rendered again by today's ReleaseFlow, from the same inputs and the same cached
answers. A golden test renders each note again (`origin=RENDERED`) and compares byte for byte
with `revision-2/`. ADR-0046 §7 names `operator`/vi and `contributor`/vi; the package carries all
six because rendering again changes each of them (below).

The files are generated from the `rf-regression` repository (fixture set `v2`, overlay
`adr-0046`) by `tools/golden/build_revisions.py`, and are never edited by hand.

## Where revision 2 comes from

Revision 2 is not written by any new code. It is what ReleaseFlow **develop at `0de37f7`**
(which contains `3fd12e0`, the 1.3.1 back-merge, and with it ADR-0040's restore of placeholders,
identifiers and code spans after the answer) wrote when it approved v1.2.0 again from set v2:

- a render replay (`./replay.sh render --set v2 --ref 0de37f7 --overlay adr-0046`), AI off,
  every summary and audience narrative written as captured, the captured category, breaking and
  security flag confirmed for each change;
- DeepL answered from set v2's recorded answers only: 174 texts, **0 characters sent**, no
  pass-through text, no stub miss;
- only v1.2.0 rebuilt (`onlyReleases`), since v0.2.0–v1.1.0 of set v2 cannot be rendered from
  the cache alone (their release summaries were never translated).

Approving runs the same writer, templates and repair that ADR-0046 §2 "Rendered again" names:
the note's own template and footer (the audiences were last changed on 2026-11-01, before
v1.2.0's approval on 2026-11-02, so the captured ones are the snapshots), the release's changes
as they read now, and each cached answer repaired as ADR-0040 repairs it. No provider is called.

### The run reproduces revision 1, except the repair

- The 6 English notes are **byte-identical** to what rc.2 published.
- Each of the 6 Vietnamese notes differs only in the letter case of whole placeholders and
  hyphenated identifiers; same length, nothing else. The builder refuses any other difference.

| Note | Placeholders | `dbvault-CLI` → `dbvault-cli` |
|---|---|---|
| `contributor`/vi | 7: `<COUNT>`×2, `<ZONE>`, `<BACKUP-id>`×2, `<VERIFICATION-id>`×2 | 3 |
| `operator`/vi | 3: `<ZONE>`, `<BACKUP-id>`, `<VERIFICATION-id>` | 4 |
| `aut_r4_probe`/vi | 0 | 5 |
| `dba`/vi | 0 | 3 |
| `end_user`/vi | 0 | 2 |
| `support`/vi | 0 | 2 |

The ten placeholders are the difference ADR-0046 expects. **The identifiers are a second one**:
ADR-0040 restores a hyphenated identifier written in lowercase in the source, and every
Vietnamese note of v1.2.0 has `dbvault-CLI` for the source's `dbvault-cli`. That includes
`end_user`/vi, which rule R1 copied to the public changelog and Zendesk; ADR-0046's Context counts
it among "the other nine: 0" because it counts placeholders and "Điểm cuối" only.

**`aut_r4_probe`/vi.** ADR-0046 §6 refuses to render a retired audience's note again
(`409 audience_retired`); it is corrected by hand only. aut_r4_probe is active in set v2, so its
render is real and its revision 2 is here. A test that retires the audience first (ADR-0044)
expects the refusal, and may save this text as a `MANUAL` revision instead; `revisions.json`
says so on that entry.
"Điểm cuối" for "Endpoint" is unchanged, as the ADR says: it needs translating again.

## Files

| Path | What it is |
|---|---|
| `revision-1/v1.2.0/<audience>.<language>.md` | The 12 notes as rc.2 published them, byte for byte as downloaded (set v2, `raw/releaseflow/releases/v1.2.0/notes/`). |
| `revision-2/v1.2.0/<audience>.vi.md` | The expected revision 2 of the six Vietnamese notes. |
| `revision-2/v1.2.0/revisions.json` | Each revision: number, the one it supersedes, `origin` `RENDERED`, `reason` (null: the test supplies its own), sha256 of both revisions, and every placeholder and identifier that changed, one entry per occurrence with its line, `from` and `to`; and the commit and run that rendered them. |
| `inputs/organization.json` | Output and release languages, the six audiences (intent, templates and footers, main and `vi`, as captured), categories, sensitive-path additions, sources (no secrets). `projectContext` and `glossary` are null: see below. |
| `inputs/changes.json` | The 14 changes of v1.2.0 in release order, each with its record as captured: category, breaking, security and its reasons, the neutral summary (with `limitations` and `migrationKind`), each audience's relevance, headline and body, linked issues. |
| `inputs/releases/v1.2.0.json` | The release: summary, upgrade guide, previous version, decisions, the glossary status rc.2 recorded (`vi` `APPLIED`), the build that approved it (rc.2 `24ba831`), each note's sha256, and `replayOnlyEdits`. |
| `deepl/answers.jsonl` | The 174 answers (23,673 characters of source text), one per line, sorted by sha256 of `text`: `source_lang`, `target_lang`, `text`, `translation` (DeepL's raw answer, as `translation_cache` holds it; rc.2 repaired nothing), and `used_as` (change or release, and field). |
| `MANIFEST.sha256` | sha256 of every other file; check with `sha256sum -c MANIFEST.sha256`. |

### Decisions

All 14 captured decisions are `APPROVE`. With AI off, three changes read differently, and the
replay sent an `EDIT` restoring the captured value (`replayOnlyEdits`): `OPS-15` and `#62`
(category `UNKNOWN` → `MAINTENANCE`, `FIX`), and `#60` (security `false` → `true`; the lab's AI
flagged it). A test that inserts each record as captured needs none of them.

## What set v2 does not hold

- **The Project context and the glossary.** rc.2 sent both (the release records glossary
  `APPLIED`, and FINDINGS #23 came from a glossary request), but the capture tools of
  2026-11-02 read neither, and `translation_jobs` was read without its context and glossary
  version. `translation_cache` keys (`textHash`) are not the sha256 of the text alone, so they
  cannot be recomputed. Rendering again never calls DeepL, so a test can seed the cache and the
  job under any context and glossary version it chooses, and give each text the answer here.
  The render replay sent no context and no glossary, and was answered by text.
- **Earlier releases.** v1.2.0 was rebuilt alone, with no previous release. Its notes print
  no Compare line either way (dbvault has more than one code host, ADR-0037), and the English
  notes are identical to rc.2's.
- **Statuses of linked issues** read at review: v1.2.0 predates ADR-0041 and stores none. The
  replay read them (as of the release's creation, from the set's history); no template of these
  audiences prints a status.

## Sources (rf-regression)

| Input | Path |
|---|---|
| Captured release, notes, changes, audiences, translation jobs and cache | `fixtures/releaseflow/v2/` (`MANIFEST.sha256` verified) |
| Footers and the upgrade guide, as captured; `onlyReleases` | `overlays/adr-0046/` |
| Reads at review | `cassettes/v2/trackers/review-reads.json` (`tools/trackers/build_review_reads.py --set v2`) |
| Revision 2, and what the render sent | the run directory named in `revisions.json` (not in git) |
