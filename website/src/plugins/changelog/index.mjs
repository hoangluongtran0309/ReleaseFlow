// A local Docusaurus plugin: /changelog, generated from CHANGELOG.md at build time.

import fs from 'node:fs';
import path from 'node:path';
import {parseChangelog} from './parse.mjs';

export default function changelogPlugin(context, {repository}) {
  const file = path.resolve(context.siteDir, '..', 'CHANGELOG.md');
  return {
    name: 'changelog',

    getPathsToWatch() {
      return [file];
    },

    async loadContent() {
      return parseChangelog(fs.readFileSync(file, 'utf8'), repository);
    },

    async contentLoaded({content, actions}) {
      // The newest released version, for the security policy's supported versions.
      actions.setGlobalData({latest: content.releases.find((release) => release.latest)?.version ?? null});
      const data = await actions.createData('changelog.json', JSON.stringify(content));
      actions.addRoute({
        path: `${context.baseUrl}changelog`,
        component: '@site/src/components/changelog/ChangelogPage',
        modules: {changelog: data},
        exact: true,
      });
    },
  };
}
