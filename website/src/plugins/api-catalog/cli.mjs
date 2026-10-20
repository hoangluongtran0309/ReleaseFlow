// Builds the REST API catalog for one locale and prints it as JSON. The plugin runs this in
// its own Node process: Docusaurus evaluates plugins with jiti, where java-parser's
// chevrotain, which can only be imported, does not load.
//
//   node cli.mjs <repository root> <site dir> <locale> <default locale> <repository URL>

import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {readControllers, TypeIndex} from './java.mjs';
import {parseApiGuide} from './guide.mjs';
import {buildCatalog} from './catalog.mjs';

// The guides an operation's description may come from, the endpoint's own guide first.
const GUIDES = ['workspace.md', 'sources.md', 'changes.md', 'release-notes.md', 'automation.md', 'getting-started.md', 'configuration.md', 'operations.md', 'api.md'];

export function loadCatalog({repositoryRoot, siteDir, locale, defaultLocale, repository}) {
  const javaRoot = path.join(repositoryRoot, 'src/main/java');
  const docsRoot = path.join(repositoryRoot, 'docs');
  const translated = path.join(siteDir, 'i18n', locale, 'docusaurus-plugin-content-docs/current');
  // A guide in the reader's language, or the English one while it has no translation.
  const readGuide = (file) => {
    const local = path.join(translated, file);
    return fs.readFileSync(locale !== defaultLocale && fs.existsSync(local) ? local : path.join(docsRoot, file), 'utf8');
  };
  return buildCatalog({
    handlers: readControllers(javaRoot, repositoryRoot),
    index: new TypeIndex(javaRoot, repositoryRoot),
    api: parseApiGuide(fs.readFileSync(path.join(docsRoot, 'api.md'), 'utf8')),
    localApi: parseApiGuide(readGuide('api.md')),
    guides: Object.fromEntries(GUIDES.map((file) => [file, readGuide(file)])),
    repository,
  });
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const [repositoryRoot, siteDir, locale, defaultLocale, repository] = process.argv.slice(2);
  try {
    process.stdout.write(JSON.stringify(loadCatalog({repositoryRoot, siteDir, locale, defaultLocale, repository})));
  } catch (error) {
    process.stderr.write(`${error.message}\n`);
    process.exit(1);
  }
}
