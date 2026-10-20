// @ts-check
// The order a reader meets the guides in. Every page in ../docs is listed here, so a new guide
// is placed on purpose; the decision records follow their numbers. A top-level category's
// customProps.key names its card on the docs home (src/components/docs/DocsHome.js).

import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const adrDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../docs/adr');
const decisionRecords = fs
  .readdirSync(adrDir)
  .filter((file) => /^\d{4}-.+\.md$/.test(file))
  .sort()
  .map((file) => `adr/${file.slice(0, -'.md'.length)}`);

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docs: [
    {type: 'category', label: 'Get started', customProps: {key: 'getStarted'}, collapsible: false, items: ['index', 'getting-started']},
    {
      type: 'category',
      label: 'Guides',
      customProps: {key: 'guides'},
      collapsible: false,
      items: ['workspace', 'sources', 'changes', 'release-notes', 'automation'],
    },
    {type: 'category', label: 'Reference', customProps: {key: 'reference'}, collapsible: false, items: ['api', 'configuration']},
    {type: 'category', label: 'Operate', customProps: {key: 'operate'}, collapsible: false, items: ['operations']},
    {
      type: 'category',
      label: 'Internals',
      customProps: {key: 'internals'},
      items: [
        'architecture',
        'implementation-status',
        {
          type: 'category',
          label: 'Decision records',
          link: {type: 'doc', id: 'adr/README'},
          items: decisionRecords,
        },
      ],
    },
  ],
};

export default sidebars;
