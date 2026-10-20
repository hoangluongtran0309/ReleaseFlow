// /changelog, from CHANGELOG.md (see src/plugins/changelog). The filter shows one type of
// change at a time; releases left with nothing to show are hidden with it.

import {useState} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Translate, {translate} from '@docusaurus/Translate';
import Heading from '@theme/Heading';
import Layout from '@theme/Layout';
import {ButtonLink, Container, Eyebrow, Icon, InlineMarkdown, Lead, Pill} from '@site/src/components/ui';
import styles from './styles.module.css';

const repository = 'https://github.com/hoangluongtran0309/ReleaseFlow';

// Links in CHANGELOG.md are relative to the repository root.
function resolveHref(target) {
  return /^(https?:|#)/.test(target) ? target : `${repository}/blob/main/${target.replace(/^\.\//, '')}`;
}

function useTypes() {
  return [
    {id: 'all', label: translate({id: 'changelog.filter.all', message: 'All'})},
    {id: 'new', label: translate({id: 'changelog.type.new', message: 'New'})},
    {id: 'improved', label: translate({id: 'changelog.type.improved', message: 'Improved'})},
    {id: 'fixed', label: translate({id: 'changelog.type.fixed', message: 'Fixed'})},
    {id: 'security', label: translate({id: 'changelog.type.security', message: 'Security'})},
    {id: 'breaking', label: translate({id: 'changelog.type.breaking', message: 'Breaking'})},
  ];
}

function useDate() {
  const {
    i18n: {currentLocale},
  } = useDocusaurusContext();
  const format = new Intl.DateTimeFormat(currentLocale, {dateStyle: 'long', timeZone: 'UTC'});
  return (date) => format.format(new Date(`${date}T00:00:00Z`));
}

function versionLabel(release) {
  return release.date ? `v${release.version}` : translate({id: 'changelog.unreleased', message: 'Unreleased'});
}

function Release({release, items, showUpgradeNotes, types, formatDate}) {
  const titleId = `${release.id}-title`;
  return (
    <article id={release.id} className={styles.release} aria-labelledby={titleId}>
      <header className={styles.releaseHead}>
        <Heading as="h2" id={titleId} className={styles.version}>
          {versionLabel(release)}
        </Heading>
        {release.date ? (
          <time className={styles.date} dateTime={release.date}>
            {formatDate(release.date)}
          </time>
        ) : (
          <p className={styles.date}>
            <Translate id="changelog.unreleased.note">In development; not in a release yet.</Translate>
          </p>
        )}
        {release.latest && (
          <span className={styles.latest}>
            <Translate id="changelog.latest">Latest</Translate>
          </span>
        )}
      </header>
      <div className={styles.releaseBody}>
        {release.summary && (
          <p className={styles.summary}>
            <InlineMarkdown text={release.summary} resolveHref={resolveHref} />
          </p>
        )}
        <ul className={styles.items}>
          {items.map((item, index) => (
            <li key={index}>
              <span className={styles.itemType}>
                <Pill tone={item.type}>{types.find((type) => type.id === item.type).label}</Pill>
              </span>
              <span className={styles.itemText}>
                <InlineMarkdown text={item.text} resolveHref={resolveHref} />
              </span>
            </li>
          ))}
        </ul>
        {showUpgradeNotes && release.upgradeNotes.length > 0 && (
          <div className={styles.upgrade}>
            <Icon name="triangle-alert" size={18} />
            <div>
              <p>
                <strong>
                  <Translate id="changelog.upgrade">Upgrade notes.</Translate>
                </strong>
              </p>
              <ul>
                {release.upgradeNotes.map((note, index) => (
                  <li key={index}>
                    <InlineMarkdown text={note.text} resolveHref={resolveHref} />
                  </li>
                ))}
              </ul>
            </div>
          </div>
        )}
        {(release.releaseUrl || release.compareUrl) && (
          <div className={styles.links}>
            {release.releaseUrl && (
              <Link to={release.releaseUrl}>
                <Translate id="changelog.assets">Release assets and checksums</Translate>
                <Icon name="external-link" size={12} strokeWidth={2.2} />
              </Link>
            )}
            {release.compareUrl && (
              <Link to={release.compareUrl}>
                <Translate id="changelog.compare">Compare changes</Translate>
                <Icon name="external-link" size={12} strokeWidth={2.2} />
              </Link>
            )}
          </div>
        )}
      </div>
    </article>
  );
}

export default function ChangelogPage({changelog}) {
  const types = useTypes();
  const formatDate = useDate();
  const [filter, setFilter] = useState('all');
  const allItems = changelog.releases.flatMap((release) => release.items);
  const count = (type) => (type === 'all' ? allItems.length : allItems.filter((item) => item.type === type).length);
  const visible = changelog.releases
    .map((release) => ({
      release,
      items: filter === 'all' ? release.items : release.items.filter((item) => item.type === filter),
    }))
    .filter(({release, items}) => items.length || (filter === 'all' && release.upgradeNotes.length));

  return (
    <Layout
      title={translate({id: 'changelog.meta.title', message: 'Changelog'})}
      description={translate({id: 'changelog.meta.description', message: 'Every ReleaseFlow release, grouped by type of change.'})}>
      <main>
        <section className={styles.hero} aria-labelledby="changelog-title">
          <Container className={styles.heroInner}>
            <div className={styles.heroCopy}>
              <Eyebrow>
                <Translate id="changelog.eyebrow">Changelog</Translate>
              </Eyebrow>
              <Heading as="h1" id="changelog-title" className={styles.title}>
                <Translate id="changelog.title">What changed in ReleaseFlow</Translate>
              </Heading>
              <Lead>
                <Translate id="changelog.lead">
                  Every release, grouped by type of change. Read the upgrade notes before moving between versions.
                </Translate>
              </Lead>
            </div>
            <ButtonLink to={`${repository}/releases`} variant="ghost">
              <Translate id="changelog.watch">Watch releases on GitHub</Translate>
            </ButtonLink>
          </Container>
        </section>
        <Container className={styles.body}>
          <nav className={styles.index} aria-label={translate({id: 'changelog.index.label', message: 'Releases'})}>
            <p className={styles.indexTitle}>
              <Translate id="changelog.index.title">Releases</Translate>
            </p>
            {changelog.releases.map((release) => (
              <a key={release.id} href={`#${release.id}`} className={styles.indexLink}>
                <span className={styles.indexVersion}>{versionLabel(release)}</span>
                {release.date && <span className={styles.indexDate}>{formatDate(release.date)}</span>}
              </a>
            ))}
            <Link to={`${repository}/releases`} className={styles.indexAll}>
              <Translate id="changelog.index.all">All releases on GitHub</Translate>
              <Icon name="external-link" size={12} strokeWidth={2.2} />
            </Link>
          </nav>
          <div className={styles.feed}>
            <div className={styles.chips} role="group" aria-label={translate({id: 'changelog.filter.label', message: 'Filter by type of change'})}>
              {types.map((type) => (
                <button
                  key={type.id}
                  type="button"
                  className={clsx(styles.chip, filter === type.id && styles.chipPressed)}
                  aria-pressed={filter === type.id}
                  onClick={() => setFilter(type.id)}>
                  {type.label}
                  <span className={styles.chipCount}>{count(type.id)}</span>
                </button>
              ))}
            </div>
            <p className={styles.visuallyHidden} role="status">
              <Translate id="changelog.filter.status" values={{count: visible.reduce((total, entry) => total + entry.items.length, 0)}}>
                {'{count} changes shown'}
              </Translate>
            </p>
            {visible.length ? (
              visible.map(({release, items}) => (
                <Release
                  key={release.id}
                  release={release}
                  items={items}
                  showUpgradeNotes={filter === 'all' || filter === 'breaking'}
                  types={types}
                  formatDate={formatDate}
                />
              ))
            ) : (
              <p className={styles.empty}>
                <Translate id="changelog.empty">No changes of this type yet.</Translate>
              </p>
            )}
          </div>
        </Container>
      </main>
    </Layout>
  );
}
