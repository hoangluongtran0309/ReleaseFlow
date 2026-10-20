// The site header: the logo, the main sections, then language, GitHub, theme and search.
// At 860px and below the sections, language and GitHub move into a disclosure panel under
// the bar.

import {useEffect, useId, useRef, useState} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import {useLocation} from '@docusaurus/router';
import {translate} from '@docusaurus/Translate';
import {useThemeConfig} from '@docusaurus/theme-common';
import {useAlternatePageUtils, useLocationChange} from '@docusaurus/theme-common/internal';
import ThemedImage from '@theme/ThemedImage';
import LocaleDropdownNavbarItem from '@theme/NavbarItem/LocaleDropdownNavbarItem';
import NavbarColorModeToggle from '@theme/Navbar/ColorModeToggle';
import SearchBar from '@theme/SearchBar';
import {ExternalMark, Icon, isExternal} from '@site/src/components/ui';
import styles from './styles.module.css';

function useCurrent(to) {
  const {pathname} = useLocation();
  const target = useBaseUrl(to ?? '').replace(/\/$/, '');
  if (!to || isExternal(to)) {
    return undefined;
  }
  if (pathname.replace(/\/$/, '') === target) {
    return 'page';
  }
  return pathname.startsWith(`${target}/`) ? 'true' : undefined;
}

function SectionLink({item, className, onClick}) {
  const current = useCurrent(item.to);
  const href = item.to ?? item.href;
  return (
    <Link to={href} className={className} aria-current={current} onClick={onClick}>
      {item.label}
      {isExternal(href) && <ExternalMark size={12} />}
    </Link>
  );
}

function Logo() {
  const {logo} = useThemeConfig().navbar;
  const sources = {light: useBaseUrl(logo.src), dark: useBaseUrl(logo.srcDark)};
  return (
    <Link to="/" className={styles.logo}>
      <ThemedImage sources={sources} alt={logo.alt} width={138} height={32} />
    </Link>
  );
}

function MobileLocales({onClick}) {
  const {
    i18n: {currentLocale, locales, localeConfigs},
  } = useDocusaurusContext();
  const alternatePageUtils = useAlternatePageUtils();
  return (
    <ul className={styles.panelList} aria-label={translate({id: 'navbar.languages', message: 'Languages'})}>
      {locales.map((locale) => (
        <li key={locale}>
          <a
            className={styles.panelLink}
            href={alternatePageUtils.createUrl({locale, fullyQualified: false})}
            hrefLang={localeConfigs[locale].htmlLang}
            lang={localeConfigs[locale].htmlLang}
            aria-current={locale === currentLocale ? 'true' : undefined}
            onClick={onClick}>
            <Icon name="globe" size={16} />
            {localeConfigs[locale].label}
          </a>
        </li>
      ))}
    </ul>
  );
}

export default function Navbar() {
  const {items} = useThemeConfig().navbar;
  const [shown, setShown] = useState(false);
  const panelId = useId();
  const buttonRef = useRef(null);
  const sections = items.filter((item) => item.position !== 'right' && item.type !== 'localeDropdown');
  const external = items.filter((item) => item.position === 'right' && item.href);
  const {type, position, ...localeDropdown} = items.find((item) => item.type === 'localeDropdown') ?? {};
  const toggle = () => setShown((value) => !value);

  useLocationChange(() => setShown(false));

  useEffect(() => {
    if (!shown) {
      return undefined;
    }
    function onKeyDown(event) {
      if (event.key === 'Escape') {
        setShown(false);
        buttonRef.current?.focus();
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [shown]);

  const close = () => setShown(false);

  return (
    <header className={clsx('navbar', styles.navbar)}>
      <div className={styles.bar}>
        <Logo />
        <nav className={styles.sections} aria-label={translate({id: 'navbar.sections', message: 'Main'})}>
          {sections.map((item) => (
            <SectionLink key={item.label} item={item} className={styles.link} />
          ))}
        </nav>
        <div className={styles.tools}>
          <div className={styles.localeSlot}>
            {type && <LocaleDropdownNavbarItem {...localeDropdown} className={styles.link} />}
          </div>
          {external.map((item) => (
            <SectionLink key={item.label} item={item} className={clsx(styles.link, styles.desktopOnly)} />
          ))}
          <NavbarColorModeToggle className={styles.themeToggle} />
          <div className={styles.search}>
            <SearchBar />
          </div>
          <button
            ref={buttonRef}
            type="button"
            className={styles.menuButton}
            aria-expanded={shown}
            aria-controls={panelId}
            aria-label={translate({id: 'navbar.menu.open', message: 'Menu'})}
            onClick={toggle}>
            <Icon name={shown ? 'x' : 'menu'} size={18} />
          </button>
        </div>
      </div>
      <div id={panelId} className={styles.panel} hidden={!shown}>
        <nav aria-label={translate({id: 'navbar.sections.mobile', message: 'Main menu'})}>
          <ul className={styles.panelList}>
            {[...sections, ...external].map((item) => (
              <li key={item.label}>
                <SectionLink item={item} className={styles.panelLink} onClick={close} />
              </li>
            ))}
          </ul>
          <MobileLocales onClick={close} />
        </nav>
      </div>
    </header>
  );
}
