import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import Translate, {translate} from '@docusaurus/Translate';
import Heading from '@theme/Heading';
import Layout from '@theme/Layout';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import styles from './index.module.css';

// The brand mark from favicon.svg, decorative wherever it appears beside words.
function FlowMark({className, size}) {
  return <img className={className} src={useBaseUrl('/favicon.svg')} alt="" width={size} height={size} />;
}

function CheckIcon() {
  return (
    <svg viewBox="0 0 20 20" aria-hidden="true">
      <path d="m5 10 3 3 7-7" />
    </svg>
  );
}

function WorkflowDiagram() {
  return (
    <aside
      className={styles.diagram}
      aria-label={translate({
        id: 'homepage.diagram.aria',
        message: 'Sources flow through rules and human review to audience-ready release notes.',
      })}>
      <div className={styles.diagramGroup}>
        <span className={styles.diagramLabel}>
          <Translate id="homepage.diagram.sources">Sources</Translate>
        </span>
        <div className={styles.sourceGrid}>
          {['GitHub', 'GitLab', 'Linear', 'Jira'].map((source) => (
            <span key={source}>{source}</span>
          ))}
        </div>
      </div>

      <span className={styles.diagramArrow} aria-hidden="true">
        →
      </span>

      <div className={`${styles.diagramGroup} ${styles.diagramCore}`}>
        <FlowMark className={styles.diagramMark} size={40} />
        <strong>
          <Translate id="homepage.diagram.core">ReleaseFlow</Translate>
        </strong>
        <span>
          <Translate id="homepage.diagram.review">Rules + human review</Translate>
        </span>
      </div>

      <span className={styles.diagramArrow} aria-hidden="true">
        →
      </span>

      <div className={styles.diagramGroup}>
        <span className={styles.diagramLabel}>
          <Translate id="homepage.diagram.outputs">Audience-ready notes</Translate>
        </span>
        <div className={styles.outputList}>
          <span>GitHub Releases</span>
          <span>Slack</span>
          <span>Public changelog</span>
        </div>
      </div>
    </aside>
  );
}

function HomepageHeader() {
  return (
    <header className={styles.hero}>
      <div className={`container ${styles.heroGrid}`}>
        <div className={styles.heroCopy}>
          <div className={styles.eyebrow}>
            <FlowMark className={styles.eyebrowMark} size={24} />
            <Translate id="homepage.eyebrow">Open source · self-hosted</Translate>
          </div>
          <Heading as="h1" className={styles.title}>
            <Translate id="homepage.title">Release notes your team reviews. Infrastructure you control.</Translate>
          </Heading>
          <p className={styles.tagline}>
            <Translate id="homepage.tagline">
              Collect merged work, classify it with explainable rules, review what matters, and publish a note for
              every audience—from one application running on your own infrastructure.
            </Translate>
          </p>
          <div className={styles.actions}>
            <Link className="button button--primary button--lg" to="/self-host">
              <Translate id="homepage.action.selfHost">Self-host ReleaseFlow</Translate>
              <span aria-hidden="true">→</span>
            </Link>
            <Link className="button button--secondary button--lg" to="/docs">
              <Translate id="homepage.action.docs">Explore the documentation</Translate>
            </Link>
          </div>
          <ul className={styles.valueList}>
            <li>
              <CheckIcon />
              <Translate id="homepage.value.review">Human-reviewed workflow</Translate>
            </li>
            <li>
              <CheckIcon />
              <Translate id="homepage.value.quickstart">Docker Compose quickstart</Translate>
            </li>
            <li>
              <CheckIcon />
              <Translate id="homepage.value.license">Apache 2.0, no open-core split</Translate>
            </li>
          </ul>
        </div>
        <WorkflowDiagram />
      </div>
    </header>
  );
}

export default function Home() {
  return (
    <Layout
      description={translate({
        id: 'homepage.description',
        message: 'Self-hosted, human-reviewed release notes from merged work to every audience.',
      })}>
      <main>
        <HomepageHeader />
        <HomepageFeatures />
      </main>
    </Layout>
  );
}
