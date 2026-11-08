# Golden release notes: dbvault

`GoldenReleaseNotesIntegrationTest` approves five real releases of dbvault, the project
ReleaseFlow is tested against end to end, and compares every note it writes, English and
Vietnamese, byte for byte with the note ReleaseFlow 1.1.0 wrote from the same inputs.

`1.1.0/` is the package the dbvault workspace generated for that version, copied here
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
object per line, built from the Jira and Linear payloads captured when each change was
recorded:

```json
{"release": "v0.2.0", "tracker": "JIRA", "key": "DBV-16", "title": "…", "type": "Story", "status": "In Progress", "category": "indeterminate"}
{"release": "v0.2.0", "tracker": "LINEAR", "key": "OPS-7", "title": "…", "status": "Done", "category": "completed"}
```

`category` is the tracker's own: Jira's status category key (`new`, `indeterminate`,
`done`) or Linear's state type (`backlog`, `unstarted`, `started`, `completed`,
`canceled`). Each release needs an answer for every linked issue of its changes; one without
an answer fails the test. A package without the file, such as `1.1.0`, reads nothing.
