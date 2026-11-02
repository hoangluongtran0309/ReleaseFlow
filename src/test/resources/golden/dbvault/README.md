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
