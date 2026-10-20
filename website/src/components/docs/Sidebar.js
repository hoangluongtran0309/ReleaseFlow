// The docs sidebar, built from sidebars.js through Docusaurus' sidebar context. A filter
// narrows the links as the reader types; at 860px and below the whole list folds behind a
// "Browse docs" button.

import {useId, useMemo, useState} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import {useLocation} from '@docusaurus/router';
import Translate, {translate} from '@docusaurus/Translate';
import {isActiveSidebarItem} from '@docusaurus/plugin-content-docs/client';
import {useLocationChange} from '@docusaurus/theme-common/internal';
import {Icon} from '@site/src/components/ui';
import styles from './styles.module.css';

// Case- and accent-insensitive, so "cau hinh" finds "Cấu hình".
function normalize(text) {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .trim();
}

/** The items whose label matches, keeping a category whose label or descendants match. */
function filterItems(items, words) {
  if (!words) {
    return items;
  }
  return items.flatMap((item) => {
    if (item.type === 'link') {
      return normalize(item.label).includes(words) ? [item] : [];
    }
    if (item.type === 'category') {
      if (normalize(item.label).includes(words)) {
        return [item];
      }
      const children = filterItems(item.items, words);
      return children.length ? [{...item, items: children}] : [];
    }
    return [];
  });
}

function ItemLink({item, pathname}) {
  const current = item.href && isActiveSidebarItem({type: 'link', href: item.href, label: item.label}, pathname);
  return (
    <Link to={item.href} className={styles.sideLink} aria-current={current ? 'page' : undefined}>
      {item.label}
    </Link>
  );
}

function NestedCategory({item, pathname, filtering}) {
  const listId = useId();
  const [expanded, setExpanded] = useState(() => isActiveSidebarItem(item, pathname));
  const open = filtering || expanded;
  return (
    <li>
      <div className={styles.nestedRow}>
        {item.href ? <ItemLink item={item} pathname={pathname} /> : <span className={styles.sideLink}>{item.label}</span>}
        <button
          type="button"
          className={styles.expand}
          aria-expanded={open}
          aria-controls={listId}
          aria-label={translate(
            {id: 'docs.sidebar.toggle', message: 'Pages in {category}'},
            {category: item.label},
          )}
          onClick={() => setExpanded(!open)}>
          <Icon name="chevron-down" size={16} />
        </button>
      </div>
      <ul id={listId} className={styles.nestedList} hidden={!open}>
        {item.items.map((child) => (
          <SidebarItem key={child.href ?? child.label} item={child} pathname={pathname} filtering={filtering} />
        ))}
      </ul>
    </li>
  );
}

function SidebarItem({item, pathname, filtering}) {
  if (item.type === 'category') {
    return <NestedCategory item={item} pathname={pathname} filtering={filtering} />;
  }
  if (item.type !== 'link') {
    return null;
  }
  return (
    <li>
      <ItemLink item={item} pathname={pathname} />
    </li>
  );
}

export default function DocsSidebar({items}) {
  const {pathname} = useLocation();
  const panelId = useId();
  const filterId = useId();
  const [filter, setFilter] = useState('');
  const [open, setOpen] = useState(false);
  const words = normalize(filter);
  const visible = useMemo(() => filterItems(items, words), [items, words]);

  useLocationChange(() => setOpen(false));

  return (
    <nav className={styles.sidebar} aria-label={translate({id: 'docs.sidebar.label', message: 'Documentation pages'})}>
      <button type="button" className={styles.browse} aria-expanded={open} aria-controls={panelId} onClick={() => setOpen(!open)}>
        <Icon name={open ? 'x' : 'menu'} size={18} />
        <Translate id="docs.sidebar.browse">Browse docs</Translate>
      </button>
      <div id={panelId} className={clsx(styles.sidePanel, open && styles.sidePanelOpen)}>
        <div className={styles.filter}>
          <Icon name="search" size={15} />
          <label htmlFor={filterId} className={styles.visuallyHidden}>
            <Translate id="docs.sidebar.filter.label">Filter pages</Translate>
          </label>
          <input
            id={filterId}
            type="search"
            autoComplete="off"
            placeholder={translate({id: 'docs.sidebar.filter.placeholder', message: 'Filter pages'})}
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
          />
        </div>
        {visible.map((item) =>
          item.type === 'category' ? (
            <div key={item.label} className={styles.sideGroup}>
              <p className={styles.sideGroupLabel}>{item.label}</p>
              <ul>
                {item.items.map((child) => (
                  <SidebarItem key={child.href ?? child.label} item={child} pathname={pathname} filtering={Boolean(words)} />
                ))}
              </ul>
            </div>
          ) : (
            <ul key={item.href ?? item.label} className={styles.sideGroup}>
              <SidebarItem item={item} pathname={pathname} filtering={Boolean(words)} />
            </ul>
          ),
        )}
        <p className={styles.noMatch} role="status">
          {words && !visible.length && (
            <Translate id="docs.sidebar.noMatch" values={{filter: filter.trim()}}>
              {'No page matches “{filter}”.'}
            </Translate>
          )}
        </p>
      </div>
    </nav>
  );
}
