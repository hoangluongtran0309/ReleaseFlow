// The docs wrapper: the sidebar beside every article. The docs home and the REST API
// overview bring their own layouts.

import {useDocsSidebar} from '@docusaurus/plugin-content-docs/client';
import {useLocation} from '@docusaurus/router';
import useBaseUrl from '@docusaurus/useBaseUrl';
import DocsSidebar from '@site/src/components/docs/Sidebar';
import styles from '@site/src/components/docs/styles.module.css';

export default function DocRootLayout({children}) {
  const sidebar = useDocsSidebar();
  const {pathname} = useLocation();
  const docsHome = useBaseUrl('/docs');
  const apiOverview = useBaseUrl('/docs/api');
  if ([docsHome, apiOverview].includes(pathname.replace(/\/$/, ''))) {
    return children;
  }
  return (
    <div className={styles.shell}>
      {sidebar && <DocsSidebar items={sidebar.items} />}
      {children}
    </div>
  );
}
