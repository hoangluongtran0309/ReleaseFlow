import {useEffect} from 'react';
import OriginalSearchPage from '@theme-original/SearchPage';

// The local-search theme renders its page content in Docusaurus' layout wrapper,
// but does not expose that wrapper as the page's main landmark. Keep the upstream
// search implementation and add the missing semantic role after it mounts.
export default function SearchPage() {
  useEffect(() => {
    const pageContent = document.querySelector('.theme-layout-main');
    if (!pageContent) {
      return undefined;
    }

    const previousRole = pageContent.getAttribute('role');
    pageContent.setAttribute('role', 'main');

    return () => {
      if (previousRole === null) {
        pageContent.removeAttribute('role');
      } else {
        pageContent.setAttribute('role', previousRole);
      }
    };
  }, []);

  return <OriginalSearchPage />;
}
