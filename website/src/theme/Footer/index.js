// The site footer: the brand column, then the link columns from themeConfig.footer, whose
// labels Docusaurus translates, and the copyright line.

import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import Translate from '@docusaurus/Translate';
import {useThemeConfig} from '@docusaurus/theme-common';
import ThemedImage from '@theme/ThemedImage';
import {ExternalMark, Icon, isExternal} from '@site/src/components/ui';
import styles from './styles.module.css';

const websiteSource = 'https://github.com/hoangluongtran0309/ReleaseFlow/tree/develop/website';

function FooterLink({item}) {
  const href = item.to ?? item.href;
  return (
    <Link to={href} className={styles.link}>
      {item.label}
      {isExternal(href) && <ExternalMark size={11} />}
    </Link>
  );
}

export default function Footer() {
  const {footer, navbar} = useThemeConfig();
  const sources = {light: useBaseUrl(navbar.logo.src), dark: useBaseUrl(navbar.logo.srcDark)};
  return (
    <footer className={styles.footer}>
      <div className={styles.inner}>
        <div className={styles.columns}>
          <div className={styles.brand}>
            <Link to="/" className={styles.logo}>
              <ThemedImage sources={sources} alt={navbar.logo.alt} width={138} height={32} />
            </Link>
            <p className={styles.tagline}>
              <Translate id="footer.tagline">Release notes your team reviews. Infrastructure you control.</Translate>
            </p>
            <p className={styles.facts}>
              <Translate id="footer.facts">Apache 2.0 · Self-hosted · Java 21</Translate>
            </p>
          </div>
          {footer.links.map((column) => (
            <nav key={column.title} className={styles.column} aria-label={column.title}>
              <p className={styles.title}>{column.title}</p>
              <ul>
                {column.items.map((item) => (
                  <li key={item.label}>
                    <FooterLink item={item} />
                  </li>
                ))}
              </ul>
            </nav>
          ))}
        </div>
        <div className={styles.bottom}>
          <p>{footer.copyright}</p>
          <Link to={websiteSource} className={styles.link}>
            <Icon name="pen-line" size={13} />
            <Translate id="footer.improve">Improve this site on GitHub</Translate>
            <ExternalMark size={11} />
          </Link>
        </div>
      </div>
    </footer>
  );
}
