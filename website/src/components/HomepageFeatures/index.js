import Link from '@docusaurus/Link';
import Translate, {translate} from '@docusaurus/Translate';
import useBaseUrl from '@docusaurus/useBaseUrl';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Heading from '@theme/Heading';
import ThemedImage from '@theme/ThemedImage';
import styles from './styles.module.css';

const repository = 'https://github.com/hoangluongtran0309/ReleaseFlow';

const quickstart = `mkdir releaseflow && cd releaseflow
base=https://github.com/hoangluongtran0309/ReleaseFlow/releases/latest/download
curl -fsSL --remote-name-all "$base/compose.yaml" "$base/quickstart.sh" "$base/SHA256SUMS"
sha256sum --check --ignore-missing SHA256SUMS
bash quickstart.sh`;

function ArrowIcon() {
  return (
    <svg viewBox="0 0 20 20" aria-hidden="true">
      <path d="M4 10h12M11 5l5 5-5 5" />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg viewBox="0 0 20 20" aria-hidden="true">
      <path d="m5 10 3 3 7-7" />
    </svg>
  );
}

/** A screenshot of the application in the reader's language and theme, 1600 × 1000. */
function ShowcaseFrame({title, description, slug, alt}) {
  const {
    i18n: {currentLocale},
  } = useDocusaurusContext();
  const light = useBaseUrl(`/media/home/${slug}.${currentLocale}.light.webp`);
  const dark = useBaseUrl(`/media/home/${slug}.${currentLocale}.dark.webp`);
  return (
    <article className={styles.showcaseCard}>
      <div className={styles.mediaFrame}>
        <div className={styles.browserBar} aria-hidden="true">
          <span />
          <span />
          <span />
        </div>
        <ThemedImage className={styles.screenshot} sources={{light, dark}} alt={alt} width={1600} height={1000} />
      </div>
      <div className={styles.showcaseCopy}>
        <Heading as="h3">{title}</Heading>
        <p>{description}</p>
      </div>
    </article>
  );
}

function ProductShowcase() {
  return (
    <section id="product" className={styles.section} aria-labelledby="product-heading">
      <div className="container">
        <div className={styles.sectionHeader}>
          <span className={styles.eyebrow}>
            <Translate id="homepage.showcase.eyebrow">Product showcase</Translate>
          </span>
          <Heading as="h2" id="product-heading" className={styles.heading}>
            <Translate id="homepage.showcase.heading">The two moments every release needs</Translate>
          </Heading>
          <p className={styles.sectionDescription}>
            <Translate id="homepage.showcase.description">
              Screenshots of ReleaseFlow at work on a sample project, in the language and theme you are reading in.
            </Translate>
          </p>
        </div>
        <div className={styles.showcaseGrid}>
          <ShowcaseFrame
            slug="h-1-change-inbox"
            alt={translate({
              id: 'homepage.showcase.inbox.alt',
              message:
                'The Change Inbox: the sync state of a Jira, a GitHub, and a GitLab source, then a change with its category and review badges',
            })}
            title={<Translate id="homepage.showcase.inbox.title">Change Inbox</Translate>}
            description={
              <Translate id="homepage.showcase.inbox.description">
                Review normalized changes from GitHub, GitLab, Linear, and Jira with their category and review
                evidence visible.
              </Translate>
            }
          />
          <ShowcaseFrame
            slug="h-2-release-review"
            alt={translate({
              id: 'homepage.showcase.release.alt',
              message: 'A release in review with two of its five changes decided, and the preview of its contributor note',
            })}
            title={<Translate id="homepage.showcase.release.title">Release review</Translate>}
            description={
              <Translate id="homepage.showcase.release.description">
                Decide on every change before approval, then publish frozen release notes written for each audience.
              </Translate>
            }
          />
        </div>
      </div>
    </section>
  );
}

function SelfHosting() {
  return (
    <section id="self-host" className={`${styles.section} ${styles.sectionMuted}`} aria-labelledby="self-host-heading">
      <div className={`container ${styles.selfHostGrid}`}>
        <div>
          <span className={styles.eyebrow}>
            <Translate id="homepage.selfhost.eyebrow">Self-hosting / quickstart</Translate>
          </span>
          <Heading as="h2" id="self-host-heading" className={styles.heading}>
            <Translate id="homepage.selfhost.heading">Run it on one machine in a few commands</Translate>
          </Heading>
          <p className={styles.sectionDescription}>
            <Translate id="homepage.selfhost.description">
              The released quickstart runs ReleaseFlow and PostgreSQL with Docker Compose. There is no repository to
              clone and nothing to compile.
            </Translate>
          </p>
          <ul className={styles.requirements}>
            <li>
              <CheckIcon />
              <Translate id="homepage.selfhost.requirement.docker">Docker Compose v2 and openssl</Translate>
            </li>
            <li>
              <CheckIcon />
              <Translate id="homepage.selfhost.requirement.stack">The application and PostgreSQL—nothing else</Translate>
            </li>
            <li>
              <CheckIcon />
              <Translate id="homepage.selfhost.requirement.local">
                Application bound to loopback; database not published
              </Translate>
            </li>
          </ul>
          <div className={styles.inlineLinks}>
            <Link to="/docs/getting-started#quickstart">
              <Translate id="homepage.selfhost.link.guide">Full quickstart guide</Translate>
              <ArrowIcon />
            </Link>
            <Link to="/docs/configuration">
              <Translate id="homepage.selfhost.link.configuration">Configuration</Translate>
              <ArrowIcon />
            </Link>
            <Link to="/self-host#operations">
              <Translate id="homepage.selfhost.link.operations">Operations</Translate>
              <ArrowIcon />
            </Link>
          </div>
        </div>

        <div className={styles.terminal}>
          <div className={styles.terminalHeader}>
            <span aria-hidden="true">
              <i />
              <i />
              <i />
            </span>
            <span>
              <Translate id="homepage.selfhost.terminal.title">Quickstart</Translate>
            </span>
          </div>
          <pre
            tabIndex={0}
            aria-label={translate({
              id: 'homepage.selfhost.command.aria',
              message: 'Quickstart commands',
            })}>
            <code>{quickstart}</code>
          </pre>
          <div className={styles.terminalFooter}>
            <span>
              <Translate id="homepage.selfhost.open">Then open</Translate>{' '}
              <code>http://localhost:8080/register</code>
            </span>
          </div>
          <p className={styles.keepEnv}>
            <strong>
              <Translate id="homepage.selfhost.keepenv.title">Keep the generated .env file.</Translate>
            </strong>{' '}
            <Translate id="homepage.selfhost.keepenv.description">
              It contains the database password and the key that encrypts stored credentials.
            </Translate>
          </p>
        </div>
      </div>
    </section>
  );
}

function PrivacyArchitecture() {
  return (
    <section id="privacy" className={styles.section} aria-labelledby="privacy-heading">
      <div className="container">
        <div className={styles.sectionHeader}>
          <span className={styles.eyebrow}>
            <Translate id="homepage.privacy.eyebrow">Privacy & architecture</Translate>
          </span>
          <Heading as="h2" id="privacy-heading" className={styles.heading}>
            <Translate id="homepage.privacy.heading">Your deployment is the system of record</Translate>
          </Heading>
          <p className={styles.sectionDescription}>
            <Translate id="homepage.privacy.description">
              ReleaseFlow is one Spring Boot application and one PostgreSQL database. You decide where they run and
              which external services they may reach.
            </Translate>
          </p>
        </div>

        <div className={styles.privacyGrid}>
          <article>
            <span className={styles.cardNumber}>01</span>
            <Heading as="h3">
              <Translate id="homepage.privacy.deploy.title">Deploy it yourself</Translate>
            </Heading>
            <p>
              <Translate id="homepage.privacy.deploy.description">
                Run the same Apache-2.0 code on your infrastructure. There is no hosted control plane or enterprise
                edition to depend on.
              </Translate>
            </p>
          </article>
          <article>
            <span className={styles.cardNumber}>02</span>
            <Heading as="h3">
              <Translate id="homepage.privacy.secrets.title">Protect stored credentials</Translate>
            </Heading>
            <p>
              <Translate id="homepage.privacy.secrets.description">
                Webhook secrets and provider tokens are encrypted with the master key you supply and are never read
                back into a page or API response.
              </Translate>
            </p>
          </article>
          <article>
            <span className={styles.cardNumber}>03</span>
            <Heading as="h3">
              <Translate id="homepage.privacy.optional.title">Choose every outbound service</Translate>
            </Heading>
            <p>
              <Translate id="homepage.privacy.optional.description">
                AI, translation, email, and delivery integrations are optional. They run only when you configure and
                use them.
              </Translate>
            </p>
          </article>
        </div>

        <div
          className={styles.architecture}
          role="img"
          aria-label={translate({
            id: 'homepage.architecture.aria',
            message:
              'GitHub, GitLab, Linear, and Jira connect to a self-hosted ReleaseFlow application and PostgreSQL database. Optional configured providers connect from ReleaseFlow.',
          })}>
          <div className={styles.architectureNode}>
            <span>
              <Translate id="homepage.architecture.sources">Your sources</Translate>
            </span>
            <strong>GitHub · GitLab · Linear · Jira</strong>
          </div>
          <span className={styles.architectureArrow} aria-hidden="true">
            →
          </span>
          <div className={styles.selfHostedBoundary}>
            <span className={styles.boundaryLabel}>
              <Translate id="homepage.architecture.boundary">Your infrastructure</Translate>
            </span>
            <div>
              <strong>
                <Translate id="homepage.architecture.application">ReleaseFlow application</Translate>
              </strong>
              <span>Java 21 · Spring Boot</span>
            </div>
            <div>
              <strong>PostgreSQL</strong>
              <span>
                <Translate id="homepage.architecture.database">Data + encrypted credentials</Translate>
              </span>
            </div>
          </div>
          <span className={styles.architectureArrow} aria-hidden="true">
            →
          </span>
          <div className={styles.architectureNode}>
            <span>
              <Translate id="homepage.architecture.optional">Optional providers</Translate>
            </span>
            <strong>AI · DeepL · Email · Delivery</strong>
          </div>
        </div>

        <div className={styles.architectureLink}>
          <Link to="/docs/architecture">
            <Translate id="homepage.privacy.link.architecture">Read the full architecture</Translate>
            <ArrowIcon />
          </Link>
        </div>
      </div>
    </section>
  );
}

function ResourceLink({to, title, children}) {
  return (
    <li>
      <Link to={to}>
        <span>
          <strong>{title}</strong>
          <small>{children}</small>
        </span>
        <ArrowIcon />
      </Link>
    </li>
  );
}

function CommunityHealth() {
  return (
    <section id="community" className={`${styles.section} ${styles.sectionMuted}`} aria-labelledby="community-heading">
      <div className="container">
        <div className={styles.sectionHeader}>
          <span className={styles.eyebrow}>
            <Translate id="homepage.community.eyebrow">Community & project health</Translate>
          </span>
          <Heading as="h2" id="community-heading" className={styles.heading}>
            <Translate id="homepage.community.heading">Inspect the project. Help shape what comes next.</Translate>
          </Heading>
          <p className={styles.sectionDescription}>
            <Translate id="homepage.community.description">
              ReleaseFlow keeps its code, decisions, release history, security process, and contribution path in the
              open.
            </Translate>
          </p>
        </div>

        <div className={styles.communityGrid}>
          <article className={styles.resourcePanel}>
            <div className={styles.panelHeading}>
              <Heading as="h3">
                <Translate id="homepage.community.panel.title">Join the community</Translate>
              </Heading>
              <p>
                <Translate id="homepage.community.panel.description">
                  Ask questions, report a problem, or make a contribution.
                </Translate>
              </p>
            </div>
            <ul className={styles.resourceList}>
              <ResourceLink
                to={`${repository}/discussions`}
                title={<Translate id="homepage.community.discussions.title">Discussions</Translate>}>
                <Translate id="homepage.community.discussions.description">Questions, ideas, and feedback</Translate>
              </ResourceLink>
              <ResourceLink
                to={`${repository}/issues/new/choose`}
                title={<Translate id="homepage.community.issues.title">Issues</Translate>}>
                <Translate id="homepage.community.issues.description">Bugs and feature requests</Translate>
              </ResourceLink>
              <ResourceLink
                to={`${repository}/blob/main/CONTRIBUTING.md`}
                title={<Translate id="homepage.community.contributing.title">Contributing</Translate>}>
                <Translate id="homepage.community.contributing.description">Development and pull request guide</Translate>
              </ResourceLink>
            </ul>
          </article>

          <article className={styles.resourcePanel}>
            <div className={styles.panelHeading}>
              <Heading as="h3">
                <Translate id="homepage.health.panel.title">Verify project health</Translate>
              </Heading>
              <p>
                <Translate id="homepage.health.panel.description">
                  Follow the source-of-truth links instead of estimated counters.
                </Translate>
              </p>
            </div>
            <div className={styles.healthGrid}>
              <Link to={`${repository}/actions/workflows/ci.yml`}>
                <strong>CI</strong>
                <span>
                  <Translate id="homepage.health.ci">Build and test workflow</Translate>
                </span>
              </Link>
              <Link to={`${repository}/actions/workflows/codeql.yml`}>
                <strong>CodeQL</strong>
                <span>
                  <Translate id="homepage.health.codeql">Security analysis</Translate>
                </span>
              </Link>
              <Link to={`${repository}/releases`}>
                <strong>
                  <Translate id="homepage.health.releases.title">Releases</Translate>
                </strong>
                <span>
                  <Translate id="homepage.health.releases.description">Published artifacts</Translate>
                </span>
              </Link>
              <Link to="/changelog">
                <strong>Changelog</strong>
                <span>
                  <Translate id="homepage.health.changelog">Recorded changes</Translate>
                </span>
              </Link>
              <Link to="/security">
                <strong>
                  <Translate id="homepage.health.security.title">Security</Translate>
                </strong>
                <span>
                  <Translate id="homepage.health.security.description">Private reporting policy</Translate>
                </span>
              </Link>
              <Link to={`${repository}/blob/main/LICENSE`}>
                <strong>Apache 2.0</strong>
                <span>
                  <Translate id="homepage.health.license">Project licence</Translate>
                </span>
              </Link>
            </div>
          </article>
        </div>
      </div>
    </section>
  );
}

export default function HomepageFeatures() {
  return (
    <>
      <ProductShowcase />
      <SelfHosting />
      <PrivacyArchitecture />
      <CommunityHealth />
    </>
  );
}
