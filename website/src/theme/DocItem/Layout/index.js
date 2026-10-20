// A docs article: breadcrumbs and the Markdown in the middle column, then the feedback
// question and the pager; "On this page" sits beside it. The doc whose id is "index" is the
// docs home instead, and "api" is the REST API overview in the reference's own frame.

import {useDoc} from '@docusaurus/plugin-content-docs/client';
import ContentVisibility from '@theme/ContentVisibility';
import DocItemContent from '@theme/DocItem/Content';
import DocsHome from '@site/src/components/docs/DocsHome';
import ApiOverview from '@site/src/components/api/ApiOverview';
import {DocBreadcrumbs, DocFeedback, DocMetaProvider, DocPager, DocToc} from '@site/src/components/docs/DocParts';
import styles from '@site/src/components/docs/styles.module.css';

export default function DocItemLayout({children}) {
  const {metadata, toc, frontMatter} = useDoc();
  if (metadata.id === 'index') {
    return <DocsHome>{children}</DocsHome>;
  }
  if (metadata.id === 'api') {
    return <ApiOverview metadata={metadata}>{children}</ApiOverview>;
  }
  const showToc = !frontMatter.hide_table_of_contents && toc.length > 0;
  return (
    <>
      <main className={styles.article}>
        <div className={styles.articleInner}>
          <ContentVisibility metadata={metadata} />
          <article>
            <DocBreadcrumbs />
            <DocMetaProvider editUrl={metadata.editUrl} lastUpdatedAt={metadata.lastUpdatedAt}>
              <DocItemContent>{children}</DocItemContent>
            </DocMetaProvider>
          </article>
          <DocFeedback />
          <DocPager previous={metadata.previous} next={metadata.next} />
        </div>
      </main>
      {showToc && <DocToc toc={toc} editUrl={metadata.editUrl} />}
    </>
  );
}
