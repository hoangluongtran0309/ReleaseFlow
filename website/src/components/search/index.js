// Every search control opens the one overlay that src/theme/SearchBar mounts in the navbar.
// A control only announces the request; the overlay remembers which element had focus and
// gives it back when it closes.

import {useEffect, useState} from 'react';
import clsx from 'clsx';
import {Icon} from '@site/src/components/ui';
import styles from './styles.module.css';

export const OPEN_SEARCH_EVENT = 'releaseflow:open-search';

export function openSearch() {
  window.dispatchEvent(new CustomEvent(OPEN_SEARCH_EVENT));
}

/** "Ctrl" everywhere, "⌘" on Apple platforms once the browser says so. */
export function useShortcutModifier() {
  const [modifier, setModifier] = useState('Ctrl');
  useEffect(() => {
    const platform = navigator.userAgentData?.platform ?? navigator.platform ?? '';
    if (/mac|iphone|ipad/i.test(platform)) {
      setModifier('⌘');
    }
  }, []);
  return modifier;
}

export function ShortcutHint({className}) {
  const modifier = useShortcutModifier();
  return (
    <span className={clsx(styles.kbds, className)} aria-hidden="true">
      <kbd>{modifier}</kbd>
      <kbd>K</kbd>
    </span>
  );
}

/** The large search field of the docs home and the 404 page: a button that opens search. */
export function SearchField({placeholder}) {
  return (
    <button type="button" className={styles.field} onClick={openSearch} aria-keyshortcuts="Control+K Meta+K">
      <Icon name="search" size={18} />
      <span className={styles.fieldText}>{placeholder}</span>
      <ShortcutHint />
    </button>
  );
}
