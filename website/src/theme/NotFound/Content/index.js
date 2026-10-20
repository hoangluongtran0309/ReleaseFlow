// The 404 page. The address the reader asked for is shown only as text in a decorative
// figure, and only once the page runs in the browser, where it is the real address rather
// than the 404 file that was served.

import {useEffect, useState} from 'react';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import Translate, {translate} from '@docusaurus/Translate';
import Heading from '@theme/Heading';
import {Container, Icon, Lead} from '@site/src/components/ui';
import {SearchField} from '@site/src/components/search';
import styles from './styles.module.css';

function QuickLink({to, title, children}) {
  return (
    <Link to={to} className={styles.quickLink}>
      <span className={styles.quickTitle}>
        {title}
        <Icon name="arrow-right" size={14} strokeWidth={2.2} />
      </span>
      <span className={styles.quickText}>{children}</span>
    </Link>
  );
}

export default function NotFoundContent() {
  const siteRoot = useBaseUrl('/');
  const [requested, setRequested] = useState(null);
  useEffect(() => {
    const path = window.location.pathname;
    setRequested(path.startsWith(siteRoot) ? `/${path.slice(siteRoot.length)}` : path);
  }, [siteRoot]);

  return (
    <main className={styles.page}>
      <Container className={styles.inner}>
        <div className={styles.copy}>
          <p className={styles.code}>
            <Translate id="notFound.code">Error 404</Translate>
          </p>
          <Heading as="h1" className={styles.title}>
            <Translate id="notFound.title">This page didn’t make the release.</Translate>
          </Heading>
          <Lead>
            <Translate id="notFound.lead">
              It may have moved when the docs were reorganised, or the link has a typo. Search for it, or start from one of
              these.
            </Translate>
          </Lead>
          <SearchField placeholder={translate({id: 'notFound.search', message: 'Search the docs, the API and the site'})} />
          <div className={styles.quickLinks}>
            <QuickLink to="/docs" title={translate({id: 'notFound.links.docs.title', message: 'Docs home'})}>
              <Translate id="notFound.links.docs.text">Guides, configuration and reference</Translate>
            </QuickLink>
            <QuickLink to="/docs/getting-started#quickstart" title={translate({id: 'notFound.links.quickstart.title', message: 'Quickstart'})}>
              <Translate id="notFound.links.quickstart.text">Run it with Docker Compose</Translate>
            </QuickLink>
            <QuickLink to="/self-host" title={translate({id: 'notFound.links.selfHost.title', message: 'Self-host'})}>
              <Translate id="notFound.links.selfHost.text">Requirements and the production checklist</Translate>
            </QuickLink>
            <QuickLink to="/changelog" title={translate({id: 'notFound.links.changelog.title', message: 'Changelog'})}>
              <Translate id="notFound.links.changelog.text">What changed in each release</Translate>
            </QuickLink>
          </div>
          <p className={styles.report}>
            <Translate
              id="notFound.report"
              values={{
                report: (
                  <Link to="https://github.com/hoangluongtran0309/ReleaseFlow/issues/new/choose">
                    <Translate id="notFound.report.link">Report it</Translate>
                  </Link>
                ),
              }}>
              {'Followed a broken link on this site? {report} and it will be fixed.'}
            </Translate>
          </p>
        </div>
        <figure className={styles.figure} aria-hidden="true">
          <p className={styles.figureLabel}>
            <Translate id="notFound.figure.title">Release review</Translate>
          </p>
          <div className={`${styles.row} ${styles.rowOut}`}>
            <div className={styles.rowHead}>
              <span className={styles.path}>{requested ?? '…'}</span>
              <span className={styles.out}>
                <Translate id="notFound.figure.out">Left out</Translate>
              </span>
            </div>
            <span className={styles.evidence}>
              <Translate id="notFound.figure.evidence">Evidence: no page at this address</Translate>
            </span>
          </div>
          {['/docs', '/self-host'].map((path) => (
            <div key={path} className={styles.row}>
              <div className={styles.rowHead}>
                <span className={styles.path}>{path}</span>
                <span className={styles.in}>
                  <Translate id="notFound.figure.in">Included</Translate>
                </span>
              </div>
            </div>
          ))}
        </figure>
      </Container>
    </main>
  );
}
