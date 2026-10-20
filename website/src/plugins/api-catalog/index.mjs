// A local Docusaurus plugin: the REST API reference, one page per operation at
// /docs/api/<method>-<path>, generated from the controllers, docs/api.md and the guides.
// See catalog.mjs for what it checks; any mismatch stops the build.

import {execFileSync} from 'node:child_process';
import path from 'node:path';

export default function apiCatalogPlugin(context, {repository}) {
  const {siteDir, baseUrl, i18n} = context;
  const repositoryRoot = path.resolve(siteDir, '..');
  const cli = path.join(siteDir, 'src/plugins/api-catalog/cli.mjs');

  return {
    name: 'api-catalog',

    getPathsToWatch() {
      return [
        path.join(repositoryRoot, 'src/main/java/**/*.java'),
        path.join(repositoryRoot, 'docs/*.md'),
        path.join(siteDir, 'i18n', i18n.currentLocale, 'docusaurus-plugin-content-docs/current/*.md'),
      ];
    },

    async loadContent() {
      try {
        const output = execFileSync(
          process.execPath,
          [cli, repositoryRoot, siteDir, i18n.currentLocale, i18n.defaultLocale, repository],
          {encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe']},
        );
        return JSON.parse(output);
      } catch (error) {
        throw new Error(error.stderr?.trim() || error.message);
      }
    },

    async contentLoaded({content, actions}) {
      const {addRoute, createData, setGlobalData} = actions;
      setGlobalData({
        groups: content.groups,
        operations: content.operations.map(({id, method, path: operationPath, name, group}) => ({id, method, path: operationPath, name, group})),
      });
      for (const operation of content.operations) {
        const data = await createData(`${operation.id}.json`, JSON.stringify(operation));
        addRoute({
          path: `${baseUrl}docs/api/${operation.id}`,
          component: '@site/src/components/api/OperationPage',
          modules: {operation: data},
          exact: true,
        });
      }
    },
  };
}
