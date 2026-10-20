// The docs home at /docs. Its topic cards are the categories of sidebars.js, each named by
// its customProps.key, and below them it shows docs/index.md itself, with its headings one
// level down so the page keeps a single H1.

import {MDXProvider} from '@mdx-js/react';
import Link from '@docusaurus/Link';
import Translate, {translate} from '@docusaurus/Translate';
import {useDocsSidebar} from '@docusaurus/plugin-content-docs/client';
import Heading from '@theme/Heading';
import MDXContent from '@theme/MDXContent';
import {Container, Eyebrow, ExternalMark, Icon, isExternal, Lead} from '@site/src/components/ui';
import {SearchField} from '@site/src/components/search';
import styles from './home.module.css';

const repository = 'https://github.com/hoangluongtran0309/ReleaseFlow';

const demotedHeadings = {
  h1: (props) => <Heading as="h2" {...props} />,
  h2: (props) => <Heading as="h3" {...props} />,
  h3: (props) => <Heading as="h4" {...props} />,
};

function useTopics() {
  return {
    getStarted: {icon: 'terminal', text: translate({id: 'docsHome.topic.getStarted', message: 'What ReleaseFlow is, and how to run it'})},
    guides: {icon: 'inbox', text: translate({id: 'docsHome.topic.guides', message: 'From the first source to an automated delivery'})},
    reference: {icon: 'code', text: translate({id: 'docsHome.topic.reference', message: 'The REST API and every setting'})},
    operate: {icon: 'activity', text: translate({id: 'docsHome.topic.operate', message: 'Run, watch and verify a deployment'})},
    internals: {icon: 'cpu', text: translate({id: 'docsHome.topic.internals', message: 'How it is built, and the decisions behind it'})},
  };
}

/** A category's links, one level deep: a nested category is listed by its own page. */
function topicLinks(category) {
  return category.items.filter((item) => item.href && (item.type === 'link' || item.type === 'category'));
}

function StartCard({to, icon, title, children, cta, recommended}) {
  return (
    <Link to={to} className={recommended ? `${styles.startCard} ${styles.recommended}` : styles.startCard}>
      <span className={styles.startTop}>
        <Icon name={icon} size={24} strokeWidth={1.8} />
        {recommended && (
          <span className={styles.recommendedLabel}>
            <Translate id="docsHome.start.recommended">Recommended first</Translate>
          </span>
        )}
      </span>
      <span className={styles.startTitle}>{title}</span>
      <span className={styles.startText}>{children}</span>
      <span className={styles.startCta}>
        {cta}
        <Icon name="arrow-right" size={14} strokeWidth={2.2} />
      </span>
    </Link>
  );
}

function HelpCard({to, icon, title, children}) {
  return (
    <Link to={to} className={styles.helpCard}>
      <Icon name={icon} size={22} strokeWidth={1.8} />
      <span className={styles.helpText}>
        <span className={styles.helpTitle}>
          {title}
          {isExternal(to) && <ExternalMark size={12} />}
        </span>
        <span>{children}</span>
      </span>
    </Link>
  );
}

export default function DocsHome({children}) {
  const sidebar = useDocsSidebar();
  const topics = useTopics();
  const categories = (sidebar?.items ?? []).filter((item) => item.type === 'category' && topics[item.customProps?.key]);
  return (
    <main className={styles.home}>
      <section className={styles.hero} aria-labelledby="docs-home-title">
        <Container className={styles.heroInner}>
          <Eyebrow>
            <Translate id="docsHome.eyebrow">Documentation</Translate>
          </Eyebrow>
          <Heading as="h1" id="docs-home-title" className={styles.title}>
            <Translate id="docsHome.title">ReleaseFlow documentation</Translate>
          </Heading>
          <Lead className={styles.lead}>
            <Translate id="docsHome.lead">
              Install it, connect your sources, review your first release, and deliver its notes to every audience.
            </Translate>
          </Lead>
          <SearchField placeholder={translate({id: 'docsHome.search', message: 'Search guides, configuration and API'})} />
        </Container>
      </section>

      <section className={styles.band} aria-labelledby="docs-start-title">
        <Container className={styles.stack}>
          <Heading as="h2" id="docs-start-title" className={styles.sectionTitle}>
            <Translate id="docsHome.start.title">Start here</Translate>
          </Heading>
          <div className={styles.startGrid}>
            <StartCard
              recommended
              to="/docs/getting-started#quickstart"
              icon="terminal"
              title={translate({id: 'docsHome.start.quickstart.title', message: 'Quickstart'})}
              cta={translate({id: 'docsHome.start.quickstart.cta', message: 'Start the quickstart'})}>
              <Translate id="docsHome.start.quickstart.text">
                Run ReleaseFlow and PostgreSQL with Docker Compose, then create the first administrator.
              </Translate>
            </StartCard>
            <StartCard
              to="/docs#how-a-change-moves-through-it"
              icon="git-merge"
              title={translate({id: 'docsHome.start.concepts.title', message: 'How a change moves'})}
              cta={translate({id: 'docsHome.start.concepts.cta', message: 'Learn the model'})}>
              <Translate id="docsHome.start.concepts.text">
                Sources, classification, review, audiences and publishing, and how one change passes through them.
              </Translate>
            </StartCard>
            <StartCard
              to="/self-host#production-checklist"
              icon="file-check"
              title={translate({id: 'docsHome.start.production.title', message: 'Production checklist'})}
              cta={translate({id: 'docsHome.start.production.cta', message: 'Prepare for production'})}>
              <Translate id="docsHome.start.production.text">
                HTTPS, backups, the master key and upgrades, before your team relies on it.
              </Translate>
            </StartCard>
          </div>
        </Container>
      </section>

      <section className={styles.band} aria-labelledby="docs-topics-title">
        <Container className={styles.stack}>
          <Heading as="h2" id="docs-topics-title" className={styles.sectionTitle}>
            <Translate id="docsHome.topics.title">Browse by topic</Translate>
          </Heading>
          <div className={styles.topicGrid}>
            {categories.map((category) => {
              const topic = topics[category.customProps.key];
              return (
                <nav key={category.label} className={styles.topic} aria-label={category.label}>
                  <div className={styles.topicHead}>
                    <span className={styles.topicIcon}>
                      <Icon name={topic.icon} size={18} />
                    </span>
                    <div>
                      <Heading as="h3" className={styles.topicTitle}>
                        {category.label}
                      </Heading>
                      <p className={styles.topicText}>{topic.text}</p>
                    </div>
                  </div>
                  <ul className={styles.topicLinks}>
                    {topicLinks(category).map((item) => (
                      <li key={item.href}>
                        <Link to={item.href}>
                          {item.label}
                          <span aria-hidden="true">→</span>
                        </Link>
                      </li>
                    ))}
                  </ul>
                </nav>
              );
            })}
          </div>
        </Container>
      </section>

      <section className={`${styles.band} ${styles.introBand}`} aria-label={translate({id: 'docsHome.intro.label', message: 'Introduction'})}>
        <Container size="narrow">
          <div className="theme-doc-markdown markdown">
            <MDXContent>
              <MDXProvider components={demotedHeadings}>{children}</MDXProvider>
            </MDXContent>
          </div>
        </Container>
      </section>

      <section className={styles.helpBand} aria-labelledby="docs-help-title">
        <Container className={styles.stack}>
          <Heading as="h2" id="docs-help-title" className={styles.sectionTitle}>
            <Translate id="docsHome.help.title">Can’t find what you need?</Translate>
          </Heading>
          <div className={styles.helpGrid}>
            <HelpCard to={`${repository}/discussions`} icon="message-square" title={translate({id: 'docsHome.help.discussions.title', message: 'Ask in Discussions'})}>
              <Translate id="docsHome.help.discussions.text">Questions about setup and workflow.</Translate>
            </HelpCard>
            <HelpCard to={`${repository}/issues/new/choose`} icon="file-warning" title={translate({id: 'docsHome.help.report.title', message: 'Report a docs problem'})}>
              <Translate id="docsHome.help.report.text">Something wrong, missing or out of date.</Translate>
            </HelpCard>
            <HelpCard to={`${repository}/tree/develop/docs`} icon="pen-line" title={translate({id: 'docsHome.help.improve.title', message: 'Improve the docs'})}>
              <Translate id="docsHome.help.improve.text">Every page has an edit link to its source.</Translate>
            </HelpCard>
          </div>
        </Container>
      </section>
    </main>
  );
}
