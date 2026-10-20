// npm --prefix website test

import assert from 'node:assert/strict';
import {test} from 'node:test';
import {ChangelogError, parseChangelog} from './parse.mjs';

const REPO = 'https://github.com/example/project';

const RELEASED = `# Changelog

Intro text the page does not show.

## [Unreleased]

## [1.1.0] - 2026-11-02

Calendar sync and a faster inbox.

### Added

- Calendar sync for releases with a planned time.

### Changed

- The inbox loads twice as fast,
  even with thousands of changes.

### Removed

- The legacy \`/api/v0\` endpoints.

### Upgrade notes

- Back up the database before upgrading.

## [1.0.0] - 2026-10-01

### Fixed

- **BREAKING**: webhook paths now include the source type.

### Security

- Tokens are encrypted with the master key.

[Unreleased]: ${REPO}/compare/v1.1.0...HEAD
[1.1.0]: ${REPO}/releases/tag/v1.1.0
[1.0.0]: ${REPO}/releases/tag/v1.0.0
`;

test('Keep a Changelog sections become types of change', () => {
  const {releases} = parseChangelog(RELEASED, REPO);
  const [latest, first] = releases;
  assert.deepEqual(
    latest.items.map((item) => item.type),
    ['new', 'improved', 'breaking'],
  );
  assert.equal(latest.items[1].text, 'The inbox loads twice as fast, even with thousands of changes.');
  assert.deepEqual(first.items.map((item) => [item.type, item.text]), [
    ['breaking', 'webhook paths now include the source type.'],
    ['security', 'Tokens are encrypted with the master key.'],
  ]);
});

test('a release keeps its date, summary, links and upgrade notes', () => {
  const {releases} = parseChangelog(RELEASED, REPO);
  const [latest, first] = releases;
  assert.equal(releases.length, 2, 'an empty Unreleased entry is not shown');
  assert.deepEqual(
    {id: latest.id, date: latest.date, latest: latest.latest, summary: latest.summary},
    {id: 'v1-1-0', date: '2026-11-02', latest: true, summary: 'Calendar sync and a faster inbox.'},
  );
  assert.equal(latest.releaseUrl, `${REPO}/releases/tag/v1.1.0`);
  assert.equal(latest.compareUrl, `${REPO}/compare/v1.0.0...v1.1.0`);
  assert.deepEqual(latest.upgradeNotes, [{type: 'upgrade', text: 'Back up the database before upgrading.'}]);
  assert.equal(first.latest, false);
  assert.equal(first.compareUrl, null, 'the first release has nothing to compare with');
});

test('only an unreleased entry, as before the first release, has no date or links', () => {
  const {releases} = parseChangelog('# Changelog\n\n## [Unreleased]\n\n### Changed\n\n- Something.\n', REPO);
  assert.deepEqual(releases, [
    {version: 'Unreleased', date: null, summary: null, items: [{type: 'improved', text: 'Something.'}], upgradeNotes: [], id: 'unreleased', latest: false, releaseUrl: null, compareUrl: null},
  ]);
});

test('a released version with no changes stops the build', () => {
  assert.throws(() => parseChangelog('## [Unreleased]\n\n## [1.0.0] - 2026-10-01\n', REPO), (error) => error instanceof ChangelogError && /1\.0\.0 is released but lists no changes/.test(error.message));
});

test('a heading or section the format does not know stops the build', () => {
  assert.throws(() => parseChangelog('## 1.0.0 (2026-10-01)\n\n### Added\n\n- A.\n', REPO), /is neither "## \[Unreleased\]" nor/);
  assert.throws(() => parseChangelog('## [Unreleased]\n\n### Improvements\n\n- A.\n', REPO), /"### Improvements" is not a Keep a Changelog section/);
  assert.throws(() => parseChangelog('## [Unreleased]\n\n### Added\n\nA paragraph.\n', REPO), /text inside a section that is not a "- " item/);
});

test('one version listed twice stops the build', () => {
  assert.throws(() => parseChangelog('## [1.0.0] - 2026-10-01\n\n### Added\n\n- A.\n\n## [1.0.0] - 2026-09-01\n\n### Added\n\n- B.\n', REPO), /1\.0\.0 has two entries/);
});
