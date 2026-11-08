# Golden release notes: dbvault

`GoldenReleaseNotesIntegrationTest` approves five real releases of dbvault, the project
ReleaseFlow is tested against end to end, and compares every note it writes, English and
Vietnamese, byte for byte with the note the workspace's render wrote from the same inputs.

`1.2.0-dev/` is the package the dbvault workspace generated for #19 (ADR-0041), copied here
unchanged: its own `README.md` says what each file holds and where it came from, and
`MANIFEST.sha256` lists the sha256 of every file. The test checks the manifest before it
starts, so a file changed here fails it.

**These files come from the dbvault workspace and are refreshed only from there.** Never
edit a snapshot, an input, or a recorded DeepL answer by hand, and never update a snapshot
to make the test pass. When a note changes on purpose, the workspace renders it again and
generates a new package, which replaces this directory as a whole.

## Reads at review (ADR-0041)

Requesting a release's review reads each linked issue again. The test drives that read
against answers the workspace recorded, as it drives DeepL, so it never asks today's trackers
and stays deterministic. A package provides them in `trackers/review-reads.jsonl`, one JSON
object per line: each issue's status when the release was reviewed, taken from the captured
Jira changelog and Linear history. The package's own README says how each one was derived.

```json
{"release": "v0.2.0", "tracker": "JIRA", "key": "DBV-16", "title": "…", "type": "Story", "status": "In Progress", "category": "indeterminate"}
{"release": "v0.2.0", "tracker": "LINEAR", "key": "OPS-7", "title": "…", "status": "Done", "category": "completed"}
```

`category` is the tracker's own: Jira's status category key (`new`, `indeterminate`,
`done`) or Linear's state type (`backlog`, `unstarted`, `started`, `completed`,
`canceled`). Each release needs an answer for every linked issue of its changes; one without
an answer fails the test. A package without the file reads nothing.

## Links made at intake (ADR-0041)

The test stores each change from its captured `record` and never runs the enricher, so a link
ADR-0041 makes at intake, from a head branch, a key followed by a word, or a Linear
identifier, is not in the record. A change whose links the render made differently also
carries `linkedIssuesReplayed` and `linkedContextStatusReplayed` in `inputs/changes.json`,
beside its unchanged `record`; the test stores those when present, so its notes show the
links the render's notes show (`#21` → DBV-12, `#36` → OPS-8, `#48` → OPS-13).
