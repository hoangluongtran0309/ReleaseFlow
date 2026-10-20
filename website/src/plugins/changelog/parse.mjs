// Reads CHANGELOG.md, written in the Keep a Changelog format that scripts/changelog.sh
// stamps: `## [Unreleased]`, then `## [x.y.z] - YYYY-MM-DD` entries, each with `###`
// sections of `- ` items, and link references at the end. A paragraph between an entry's
// heading and its first section is that release's summary. Anything else is an error, so
// the page never silently drops part of an entry.

// Keep a Changelog sections, and the type of change each one shows as.
export const SECTION_TYPES = {
  added: 'new',
  changed: 'improved',
  fixed: 'fixed',
  security: 'security',
  removed: 'breaking',
  deprecated: 'breaking',
};

// Not a Keep a Changelog section: steps an operator takes before upgrading. Shown apart.
const UPGRADE_NOTES = 'upgrade notes';

const RELEASE = /^## \[(\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?)\] - (\d{4}-\d{2}-\d{2})$/;
const BREAKING = /^(\*\*)?BREAKING(\*\*)?:?\s*(\*\*)?/;

export class ChangelogError extends Error {
  constructor(problems) {
    super(`changelog: CHANGELOG.md cannot be shown on the site:\n${problems.map((problem) => `  - ${problem}`).join('\n')}`);
    this.problems = problems;
  }
}

export function releaseAnchor(version) {
  return version === 'Unreleased' ? 'unreleased' : `v${version.replace(/\./g, '-')}`;
}

export function parseChangelog(markdown, repository) {
  const problems = [];
  const releases = [];
  const references = {};
  let release = null;
  let section = null;
  let item = null;

  markdown.split('\n').forEach((line, index) => {
    const where = `line ${index + 1}`;
    const reference = /^\[([^\]]+)\]:\s*(\S+)\s*$/.exec(line);
    if (reference) {
      references[reference[1]] = reference[2];
      item = null;
      return;
    }
    if (line.startsWith('## ')) {
      const dated = RELEASE.exec(line);
      if (line === '## [Unreleased]') {
        release = {version: 'Unreleased', date: null, summary: null, items: [], upgradeNotes: []};
      } else if (dated) {
        release = {version: dated[1], date: dated[2], summary: null, items: [], upgradeNotes: []};
      } else {
        problems.push(`${where}: "${line}" is neither "## [Unreleased]" nor "## [x.y.z] - YYYY-MM-DD"`);
        release = null;
      }
      if (release) {
        releases.push(release);
      }
      section = null;
      item = null;
      return;
    }
    if (line.startsWith('### ')) {
      const name = line.slice(4).trim().toLowerCase();
      if (!release) {
        problems.push(`${where}: "${line}" is outside a release`);
      } else if (name === UPGRADE_NOTES) {
        section = 'upgrade';
      } else if (SECTION_TYPES[name]) {
        section = SECTION_TYPES[name];
      } else {
        problems.push(`${where}: "${line}" is not a Keep a Changelog section (${Object.keys(SECTION_TYPES).join(', ')})`);
        section = null;
      }
      item = null;
      return;
    }
    const bullet = /^- (.*)$/.exec(line);
    if (bullet) {
      if (!release || !section) {
        problems.push(`${where}: a change outside a release section`);
        return;
      }
      const breaking = BREAKING.test(bullet[1]);
      item = {type: breaking && section !== 'upgrade' ? 'breaking' : section, text: bullet[1].replace(BREAKING, '')};
      (section === 'upgrade' ? release.upgradeNotes : release.items).push(item);
      return;
    }
    if (item && /^\s+\S/.test(line)) {
      item.text = `${item.text} ${line.trim()}`;
      return;
    }
    if (line.trim()) {
      item = null;
      if (release && section) {
        problems.push(`${where}: text inside a section that is not a "- " item`);
      } else if (release) {
        release.summary = release.summary ? `${release.summary} ${line.trim()}` : line.trim();
      }
    } else {
      item = null;
    }
  });

  const seen = new Set();
  for (const entry of releases) {
    if (seen.has(entry.version)) {
      problems.push(`${entry.version} has two entries`);
    }
    seen.add(entry.version);
    if (entry.version !== 'Unreleased' && !entry.items.length) {
      problems.push(`${entry.version} is released but lists no changes`);
    }
  }
  if (problems.length) {
    throw new ChangelogError(problems);
  }

  const dated = releases.filter((entry) => entry.date);
  return {
    releases: releases
      .filter((entry) => entry.items.length || entry.upgradeNotes.length)
      .map((entry) => {
        const previous = dated[dated.indexOf(entry) + 1];
        // A release compares with the one before it; the unreleased entry with its own
        // reference, which scripts/changelog.sh writes once a version exists.
        let compareUrl = null;
        if (!entry.date) {
          compareUrl = references.Unreleased ?? null;
        } else if (previous) {
          compareUrl = `${repository}/compare/v${previous.version}...v${entry.version}`;
        }
        return {
          ...entry,
          id: releaseAnchor(entry.version),
          latest: entry === dated[0],
          releaseUrl: entry.date ? references[entry.version] ?? null : null,
          compareUrl,
        };
      }),
    references,
  };
}
