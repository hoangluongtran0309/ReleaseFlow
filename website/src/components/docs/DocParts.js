// The parts of a docs article around its Markdown: breadcrumbs, the meta row under the
// title, the "On this page" column, the feedback question and the previous/next pager.

import {createContext, useContext, useEffect, useId, useRef, useState} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Translate, {translate} from '@docusaurus/Translate';
import {useSidebarBreadcrumbs} from '@docusaurus/plugin-content-docs/client';
import {useTOCHighlight} from '@docusaurus/theme-common/internal';
import {ExternalMark, Icon} from '@site/src/components/ui';
import styles from './styles.module.css';

const issues = 'https://github.com/hoangluongtran0309/ReleaseFlow/issues/new/choose';

function editThisPage() {
  return translate({id: 'theme.common.editThisPage', message: 'Edit this page'});
}

export function DocBreadcrumbs() {
  const breadcrumbs = useSidebarBreadcrumbs();
  if (!breadcrumbs) {
    return null;
  }
  return (
    <nav className={styles.breadcrumbs} aria-label={translate({id: 'theme.docs.breadcrumbs.navAriaLabel', message: 'Breadcrumbs'})}>
      <ol>
        <li>
          <Link to="/docs">
            <Translate id="docs.breadcrumbs.docs">Docs</Translate>
          </Link>
        </li>
        {breadcrumbs.map((item, index) => {
          const last = index === breadcrumbs.length - 1;
          return (
            <li key={item.label}>
              {last ? (
                <span aria-current="page">{item.label}</span>
              ) : item.href ? (
                <Link to={item.href}>{item.label}</Link>
              ) : (
                <span>{item.label}</span>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

// The meta row sits under the article's H1, which the Markdown renders; DocItem/Layout
// provides what the row shows, and MDXComponents places it after the heading.
const DocMetaContext = createContext(null);

export function DocMetaProvider({editUrl, lastUpdatedAt, children}) {
  return <DocMetaContext.Provider value={{editUrl, lastUpdatedAt}}>{children}</DocMetaContext.Provider>;
}

export function DocMeta() {
  const meta = useContext(DocMetaContext);
  const {
    i18n: {currentLocale},
  } = useDocusaurusContext();
  if (!meta || (!meta.editUrl && !meta.lastUpdatedAt)) {
    return null;
  }
  const updated = meta.lastUpdatedAt
    ? new Intl.DateTimeFormat(currentLocale, {dateStyle: 'medium', timeZone: 'UTC'}).format(new Date(meta.lastUpdatedAt))
    : null;
  return (
    <div className={styles.docMeta}>
      {updated && (
        <span>
          <Translate id="docs.meta.updated" values={{date: <time dateTime={new Date(meta.lastUpdatedAt).toISOString()}>{updated}</time>}}>
            {'Updated {date}'}
          </Translate>
        </span>
      )}
      {meta.editUrl && (
        <Link to={meta.editUrl} className={styles.metaLink}>
          <Icon name="pen-line" size={14} />
          {editThisPage()}
          <ExternalMark size={11} />
        </Link>
      )}
    </div>
  );
}

export function DocToc({toc, editUrl}) {
  const titleId = useId();
  useTOCHighlight({
    linkClassName: styles.tocLink,
    linkActiveClassName: styles.tocLinkActive,
    minHeadingLevel: 2,
    maxHeadingLevel: 3,
  });

  function backToTop() {
    const heading = document.querySelector('main h1');
    window.scrollTo({top: 0});
    if (heading) {
      heading.setAttribute('tabindex', '-1');
      heading.focus({preventScroll: true});
    }
  }

  return (
    <aside className={styles.toc} aria-labelledby={titleId}>
      <div className={styles.tocInner}>
        <p id={titleId} className={styles.tocTitle}>
          {translate({id: 'theme.TOCCollapsible.toggleButtonLabel', message: 'On this page'})}
        </p>
        <ul className={styles.tocList}>
          {toc
            .filter((item) => item.level <= 3)
            .map((item) => (
              <li key={item.id} className={item.level === 3 ? styles.tocNested : undefined}>
                <a href={`#${item.id}`} className={styles.tocLink} dangerouslySetInnerHTML={{__html: item.value}} />
              </li>
            ))}
        </ul>
        <div className={styles.tocActions}>
          {editUrl && (
            <Link to={editUrl} className={styles.tocAction}>
              <Icon name="pen-line" size={14} />
              {editThisPage()}
              <ExternalMark size={11} />
            </Link>
          )}
          <Link to={issues} className={styles.tocAction}>
            <Icon name="file-warning" size={14} />
            <Translate id="docs.toc.report">Report a problem</Translate>
            <ExternalMark size={11} />
          </Link>
          <button type="button" className={styles.tocAction} onClick={backToTop}>
            <Icon name="chevron-up" size={14} />
            <Translate id="docs.toc.top">Back to top</Translate>
          </button>
        </div>
      </div>
    </aside>
  );
}

/** Asks whether the page helped, and only thanks the reader: nothing is sent or stored. */
export function DocFeedback() {
  const [answer, setAnswer] = useState(null);
  const thanks = useRef(null);
  // The pressed button disappears, so focus moves to the line that replaced it.
  useEffect(() => {
    if (answer) {
      thanks.current?.focus();
    }
  }, [answer]);
  return (
    <div className={styles.feedback}>
      {answer ? (
        <p ref={thanks} tabIndex={-1} className={styles.feedbackThanks}>
          <Translate
            id="docs.feedback.thanks"
            values={{
              issue: (
                <Link to={issues}>
                  <Translate id="docs.feedback.issue">open an issue</Translate>
                </Link>
              ),
            }}>
            {'Thanks for telling us. To say what was missing or wrong, {issue}.'}
          </Translate>
        </p>
      ) : (
        <>
          <p className={styles.feedbackQuestion}>
            <Translate id="docs.feedback.question">Was this page helpful?</Translate>
          </p>
          <div className={styles.feedbackButtons}>
            <button type="button" onClick={() => setAnswer('yes')}>
              <Translate id="docs.feedback.yes">Yes</Translate>
            </button>
            <button type="button" onClick={() => setAnswer('no')}>
              <Translate id="docs.feedback.no">No</Translate>
            </button>
          </div>
        </>
      )}
    </div>
  );
}

export function DocPager({previous, next}) {
  if (!previous && !next) {
    return null;
  }
  return (
    <nav className={styles.pager} aria-label={translate({id: 'theme.docs.paginator.navAriaLabel', message: 'Docs pages'})}>
      {previous && (
        <Link to={previous.permalink} className={styles.pagerLink}>
          <span className={styles.pagerLabel}>{translate({id: 'theme.docs.paginator.previous', message: 'Previous'})}</span>
          <span className={styles.pagerTitle}>← {previous.title}</span>
        </Link>
      )}
      {next && (
        <Link to={next.permalink} className={clsx(styles.pagerLink, styles.pagerNext)}>
          <span className={styles.pagerLabel}>{translate({id: 'theme.docs.paginator.next', message: 'Next'})}</span>
          <span className={styles.pagerTitle}>{next.title} →</span>
        </Link>
      )}
    </nav>
  );
}
