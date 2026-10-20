// /docs/api: docs/api.md itself, in the reference's frame. Each `METHOD /path` it names
// links to that operation's page.

import {useMemo} from 'react';
import {MDXProvider} from '@mdx-js/react';
import Link from '@docusaurus/Link';
import MDXComponents from '@theme/MDXComponents';
import DocItemContent from '@theme/DocItem/Content';
import {DocBreadcrumbs, DocFeedback, DocMetaProvider, DocPager} from '@site/src/components/docs/DocParts';
import {ApiShell, useApiCatalog} from './parts';
import styles from './api.module.css';

// Inside DocItemContent, so it takes precedence over the theme's own code component.
function useLinkedCode() {
  const {operations} = useApiCatalog();
  return useMemo(() => {
    const byEndpoint = new Map(operations.map((operation) => [`${operation.method} ${operation.path}`, operation.id]));
    return function LinkedCode(props) {
      const text = typeof props.children === 'string' ? props.children : null;
      const id = text && byEndpoint.get(text);
      const code = <MDXComponents.code {...props} />;
      return id ? <Link to={`/docs/api/${id}`}>{code}</Link> : code;
    };
  }, [operations]);
}

export default function ApiOverview({metadata, children}) {
  const code = useLinkedCode();
  return (
    <ApiShell current="overview">
      <main className={styles.main}>
        <div className={styles.overview}>
          <article>
            <DocBreadcrumbs />
            <DocMetaProvider editUrl={metadata.editUrl} lastUpdatedAt={metadata.lastUpdatedAt}>
              <DocItemContent>
                <MDXProvider components={{code}}>{children}</MDXProvider>
              </DocItemContent>
            </DocMetaProvider>
          </article>
          <DocFeedback />
          <DocPager previous={metadata.previous} next={metadata.next} />
        </div>
      </main>
    </ApiShell>
  );
}
