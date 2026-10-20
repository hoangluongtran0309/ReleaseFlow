// Search, as a modal over any page. The query runs in the local-search plugin's own worker
// against its own index (pinned to @easyops-cn/docusaurus-search-local 0.55.3, whose
// searchByWorker module this imports), so only the interface is the site's. The REST
// operations are pages of the api-catalog plugin, which that index does not cover, so they
// are matched from the plugin's own list by name, method and path. The navbar
// renders this component once: it is the trigger button and the dialog, and it answers
// Ctrl/⌘ K and openSearch() from anywhere on the page.

import {useCallback, useEffect, useId, useMemo, useRef, useState} from 'react';
import {createPortal} from 'react-dom';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import {useHistory, useLocation} from '@docusaurus/router';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import {usePluginData} from '@docusaurus/useGlobalData';
import Translate, {translate} from '@docusaurus/Translate';
import {fetchIndexesByWorker, searchByWorker} from '@easyops-cn/docusaurus-search-local/dist/client/client/theme/searchByWorker';
import {Icon} from '@site/src/components/ui';
import {OPEN_SEARCH_EVENT, ShortcutHint} from '@site/src/components/search';
import styles from './styles.module.css';

const RESULT_LIMIT = 24;
const TITLE = 0;

// Results are grouped by where they live: the REST API reference, the other guides, and the
// site's own pages.
function groupOf(url, baseUrl) {
  const path = url.startsWith(baseUrl) ? url.slice(baseUrl.length) : url.replace(/^\//, '');
  if (path === 'docs/api' || path.startsWith('docs/api/')) {
    return 'api';
  }
  return path === 'docs' || path.startsWith('docs/') ? 'docs' : 'pages';
}

function toItem(result, baseUrl) {
  const {document, page, type} = result;
  const trail = type === TITLE ? (document.b ?? []) : [...(page?.b ?? []), page?.t];
  return {
    id: `${document.i}`,
    href: `${document.u}${document.h ?? ''}`,
    title: document.t,
    trail: trail.filter(Boolean).join(' › '),
    group: groupOf(document.u, baseUrl),
  };
}

/** Operations whose name, method and path hold every word of the query. */
function matchOperations(operations, words, baseUrl) {
  const terms = words.toLowerCase().split(/\s+/).filter(Boolean);
  return operations
    .filter((operation) => {
      const haystack = `${operation.name} ${operation.method} ${operation.path}`.toLowerCase();
      return terms.every((term) => haystack.includes(term));
    })
    .slice(0, 8)
    .map((operation) => ({
      id: `operation-${operation.id}`,
      href: `${baseUrl}docs/api/${operation.id}`,
      title: operation.name,
      trail: `${operation.method} ${operation.path}`,
      group: 'api',
      tile: operation.method,
    }));
}

function useGroups() {
  return useMemo(
    () => [
      {key: 'docs', label: translate({id: 'search.group.docs', message: 'Docs'}), tile: 'DOC'},
      {key: 'api', label: translate({id: 'search.group.api', message: 'API'}), tile: 'API'},
      {key: 'pages', label: translate({id: 'search.group.pages', message: 'Pages'}), tile: 'PAGE'},
    ],
    [],
  );
}

function focusables(container) {
  return [...container.querySelectorAll('input, button, [tabindex="0"], a[href]:not([tabindex="-1"])')].filter(
    (element) => !element.disabled && element.offsetParent !== null,
  );
}

function SearchDialog({onClose}) {
  const {
    siteConfig: {baseUrl},
  } = useDocusaurusContext();
  const history = useHistory();
  const groups = useGroups();
  const {operations} = usePluginData('api-catalog');
  const dialogRef = useRef(null);
  const inputRef = useRef(null);
  const sequence = useRef(0);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('loading');
  const [items, setItems] = useState([]);
  const [active, setActive] = useState(0);
  const baseId = useId();
  const listId = `${baseId}-results`;
  const titleId = `${baseId}-title`;

  useEffect(() => {
    inputRef.current?.focus();
    fetchIndexesByWorker(baseUrl, '').then(
      () => setStatus('ready'),
      () => setStatus('error'),
    );
  }, [baseUrl]);

  useEffect(() => {
    const words = query.trim();
    const current = ++sequence.current;
    if (!words || status !== 'ready') {
      setItems([]);
      return;
    }
    searchByWorker(baseUrl, '', words, RESULT_LIMIT).then(
      (results) => {
        if (current === sequence.current) {
          setItems([...matchOperations(operations, words, baseUrl), ...results.map((result) => toItem(result, baseUrl))]);
          setActive(0);
        }
      },
      () => current === sequence.current && setStatus('error'),
    );
  }, [query, status, baseUrl, operations]);

  // Keyboard order follows the groups as they are shown.
  const ordered = useMemo(
    () => groups.flatMap((group) => items.filter((item) => item.group === group.key)),
    [groups, items],
  );

  function go(item) {
    onClose({restoreFocus: false});
    history.push(item.href);
  }

  function onKeyDown(event) {
    if (event.key === 'Escape') {
      event.preventDefault();
      onClose({restoreFocus: true});
    } else if (event.key === 'ArrowDown' && ordered.length) {
      event.preventDefault();
      setActive((index) => (index + 1) % ordered.length);
    } else if (event.key === 'ArrowUp' && ordered.length) {
      event.preventDefault();
      setActive((index) => (index - 1 + ordered.length) % ordered.length);
    } else if (event.key === 'Enter' && (event.target === inputRef.current || event.target.id === listId) && ordered[active]) {
      event.preventDefault();
      go(ordered[active]);
    } else if (event.key === 'Tab') {
      const elements = focusables(dialogRef.current);
      const first = elements[0];
      const last = elements[elements.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
  }

  useEffect(() => {
    document.getElementById(`${listId}-${active}`)?.scrollIntoView({block: 'nearest'});
  }, [active, listId]);

  const words = query.trim();
  const activeId = ordered[active] ? `${listId}-${active}` : undefined;
  const allResults = `${baseUrl}search?q=${encodeURIComponent(words)}`;

  return (
    <div className={styles.backdrop} onMouseDown={(event) => event.target === event.currentTarget && onClose({restoreFocus: true})}>
      <div ref={dialogRef} className={styles.dialog} role="dialog" aria-modal="true" aria-labelledby={titleId} onKeyDown={onKeyDown}>
        <h2 id={titleId} className={styles.visuallyHidden}>
          <Translate id="search.dialog.title">Search ReleaseFlow</Translate>
        </h2>
        <div className={styles.inputRow}>
          <Icon name="search" size={20} />
          <label htmlFor={`${baseId}-input`} className={styles.visuallyHidden}>
            <Translate id="search.input.label">Search the docs and pages</Translate>
          </label>
          <input
            ref={inputRef}
            id={`${baseId}-input`}
            className={styles.input}
            type="search"
            autoComplete="off"
            spellCheck={false}
            placeholder={translate({id: 'search.input.placeholder', message: 'Search guides, configuration and API'})}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            role="combobox"
            aria-expanded={ordered.length > 0}
            aria-controls={listId}
            aria-autocomplete="list"
            aria-activedescendant={activeId}
          />
          <button type="button" className={styles.esc} onClick={() => onClose({restoreFocus: true})}>
            <span aria-hidden="true">Esc</span>
            <span className={styles.visuallyHidden}>
              <Translate id="search.close">Close search</Translate>
            </span>
          </button>
        </div>
        <div className={styles.body}>
          <div
            id={listId}
            role="listbox"
            tabIndex={0}
            className={styles.list}
            aria-label={translate({id: 'search.results.label', message: 'Search results'})}
            aria-activedescendant={activeId}
            hidden={!ordered.length}>
            {groups.map((group) => {
              const members = ordered.filter((item) => item.group === group.key);
              if (!members.length) {
                return null;
              }
              return (
                <div key={group.key} role="group" aria-labelledby={`${listId}-${group.key}`} className={styles.group}>
                  <div id={`${listId}-${group.key}`} role="presentation" className={styles.groupLabel}>
                    {group.label}
                  </div>
                  {members.map((item) => {
                    const index = ordered.indexOf(item);
                    return (
                      <a
                        key={item.id}
                        id={`${listId}-${index}`}
                        role="option"
                        aria-selected={index === active}
                        tabIndex={-1}
                        href={item.href}
                        className={clsx(styles.result, index === active && styles.selected)}
                        onMouseMove={() => setActive(index)}
                        onClick={(event) => {
                          if (!event.metaKey && !event.ctrlKey && !event.shiftKey && event.button === 0) {
                            event.preventDefault();
                            go(item);
                          }
                        }}>
                        <span className={clsx(styles.tile, styles[`tile-${group.key}`])} aria-hidden="true">
                          {item.tile ?? group.tile}
                        </span>
                        <span className={styles.resultText}>
                          <span className={styles.resultTitle}>{item.title}</span>
                          {item.trail && <span className={styles.resultTrail}>{item.trail}</span>}
                        </span>
                        <span className={styles.enter} aria-hidden="true">
                          ↵
                        </span>
                      </a>
                    );
                  })}
                </div>
              );
            })}
          </div>
          <div role="status" className={styles.message}>
            {status === 'error' && <Translate id="search.error">Search is unavailable right now.</Translate>}
            {status === 'loading' && words && <Translate id="search.loading">Loading the search index…</Translate>}
            {status === 'ready' && !words && <Translate id="search.empty.prompt">Type to search the guides, the API reference and the site’s pages.</Translate>}
            {status === 'ready' && words && !ordered.length && (
              <>
                <strong className={styles.noResults}>
                  <Translate id="search.noResults.title" values={{query: words}}>
                    {'No results for “{query}”'}
                  </Translate>
                </strong>
                <span>
                  <Translate
                    id="search.noResults.text"
                    values={{
                      discussions: (
                        <Link to="https://github.com/hoangluongtran0309/ReleaseFlow/discussions">
                          <Translate id="search.noResults.discussions">ask in Discussions</Translate>
                        </Link>
                      ),
                    }}>
                    {'Try fewer or different words, or {discussions}.'}
                  </Translate>
                </span>
              </>
            )}
          </div>
        </div>
        <div className={styles.footer}>
          <div className={styles.hints} aria-hidden="true">
            <span>
              <kbd>↑</kbd>
              <kbd>↓</kbd>
              <Translate id="search.hint.navigate">Navigate</Translate>
            </span>
            <span>
              <kbd>↵</kbd>
              <Translate id="search.hint.open">Open</Translate>
            </span>
            <span>
              <kbd>Esc</kbd>
              <Translate id="search.hint.close">Close</Translate>
            </span>
          </div>
          {words && status === 'ready' ? (
            <a className={styles.allResults} href={allResults} onClick={() => onClose({restoreFocus: false})}>
              <Translate id="search.count" values={{count: ordered.length}}>
                {'{count} results'}
              </Translate>
              <span aria-hidden="true">·</span>
              <Translate id="search.allResults">Open the search page</Translate>
            </a>
          ) : null}
        </div>
      </div>
    </div>
  );
}

export default function SearchBar() {
  const [open, setOpen] = useState(false);
  const returnFocus = useRef(null);
  const {pathname} = useLocation();

  const show = useCallback(() => {
    if (!open) {
      returnFocus.current = document.activeElement;
      setOpen(true);
    }
  }, [open]);

  const close = useCallback(({restoreFocus}) => {
    setOpen(false);
    const target = returnFocus.current;
    if (restoreFocus && target && document.contains(target)) {
      requestAnimationFrame(() => target.focus());
    }
  }, []);

  useEffect(() => {
    function onKeyDown(event) {
      if (event.key.toLowerCase() === 'k' && (event.metaKey || event.ctrlKey) && !event.altKey) {
        event.preventDefault();
        show();
      }
    }
    window.addEventListener('keydown', onKeyDown);
    window.addEventListener(OPEN_SEARCH_EVENT, show);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
      window.removeEventListener(OPEN_SEARCH_EVENT, show);
    };
  }, [show]);

  useEffect(() => setOpen(false), [pathname]);

  // While the dialog is open the page behind it can be neither read nor scrolled.
  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const page = document.getElementById('__docusaurus');
    const overflow = document.body.style.overflow;
    page?.setAttribute('inert', '');
    document.body.style.overflow = 'hidden';
    return () => {
      page?.removeAttribute('inert');
      document.body.style.overflow = overflow;
    };
  }, [open]);

  return (
    <>
      <button type="button" className={styles.trigger} onClick={show} aria-keyshortcuts="Control+K Meta+K">
        <Icon name="search" size={15} strokeWidth={2.2} />
        <span className={styles.triggerLabel}>
          <Translate id="search.trigger">Search</Translate>
        </span>
        <ShortcutHint className={styles.triggerKbd} />
      </button>
      {open && createPortal(<SearchDialog onClose={close} />, document.body)}
    </>
  );
}
