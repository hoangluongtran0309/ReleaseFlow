// A guide may link to a file beside docs/, such as CONTRIBUTING.md or .env.example. That link
// works on GitHub but names nothing the site publishes, so here it is pointed at the file on
// main, the branch the site is built from. Links inside docs/ are left to Docusaurus, which
// checks them. A translation is read as if it sat where its English page does.

import fs from 'node:fs';
import path from 'node:path';

const LOCALIZED_DOCS = /^.*[\\/]i18n[\\/][^\\/]+[\\/]docusaurus-plugin-content-docs[\\/]current(?=[\\/]|$)/;

function isRelative(url) {
  return url !== '' && !url.startsWith('#') && !url.startsWith('/') && !/^[a-z][a-z0-9+.-]*:/i.test(url);
}

export default function remarkRepositoryLinks({repository, repositoryRoot, docsRoot}) {
  return (tree, file) => {
    const pageDir = path.dirname(file.path).replace(LOCALIZED_DOCS, docsRoot);

    const visit = (node) => {
      if ((node.type === 'link' || node.type === 'definition') && isRelative(node.url)) {
        const hashAt = node.url.indexOf('#');
        const target = hashAt === -1 ? node.url : node.url.slice(0, hashAt);
        const hash = hashAt === -1 ? '' : node.url.slice(hashAt);
        const resolved = path.resolve(pageDir, decodeURI(target));
        const inDocs = resolved === docsRoot || resolved.startsWith(docsRoot + path.sep);
        if (!inDocs && fs.existsSync(resolved)) {
          const fromRoot = path.relative(repositoryRoot, resolved).split(path.sep).join('/');
          node.url = `${repository}/blob/main/${fromRoot}${hash}`;
        }
      }
      node.children?.forEach(visit);
    };

    visit(tree);
  };
}
