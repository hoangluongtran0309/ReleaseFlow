// Self-hosting: what the quickstart needs and does, how a shared deployment differs, and
// where the operations guides are. Every fact here comes from quickstart/quickstart.sh,
// quickstart/compose.yaml, docs/getting-started.md and docs/operations.md; change them
// together.

import Link from '@docusaurus/Link';
import Translate, {translate} from '@docusaurus/Translate';
import useBaseUrl from '@docusaurus/useBaseUrl';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import {
  ActionButton,
  ButtonLink,
  Card,
  CheckList,
  Checklist,
  Container,
  CtaBand,
  Display,
  Eyebrow,
  Faq,
  Grid,
  Hero,
  Lead,
  LinkCard,
  ScreenshotFrame,
  Section,
  SectionHeader,
  Steps,
  Terminal,
  TextLink,
  Actions,
} from '@site/src/components/ui';
import {openSearch} from '@site/src/components/search';
import styles from './self-host.module.css';

const repository = 'https://github.com/hoangluongtran0309/ReleaseFlow';

const quickstart = `mkdir releaseflow && cd releaseflow
base=https://github.com/hoangluongtran0309/ReleaseFlow/releases/latest/download
curl -fsSL --remote-name-all "$base/compose.yaml" "$base/quickstart.sh" "$base/SHA256SUMS"
sha256sum --check --ignore-missing SHA256SUMS
bash quickstart.sh`;

function HeroSection() {
  return (
    <Hero labelledBy="self-host-title">
      <Container className={styles.split}>
        <div className={styles.heroCopy}>
          <Eyebrow>
            <Translate id="selfHost.hero.eyebrow">Self-hosting</Translate>
          </Eyebrow>
          <Display id="self-host-title">
            <Translate id="selfHost.hero.title">Run ReleaseFlow on infrastructure you already trust.</Translate>
          </Display>
          <Lead>
            <Translate id="selfHost.hero.lead">
              One Spring Boot application and one PostgreSQL database, started with Docker Compose. Everyone runs the
              same Apache-2.0 code: no hosted control plane, no enterprise edition, no open-core split.
            </Translate>
          </Lead>
          <Actions>
            <ButtonLink to="/docs/getting-started#quickstart" arrow>
              <Translate id="selfHost.hero.guide">Full quickstart guide</Translate>
            </ButtonLink>
            <ButtonLink to={`${repository}/releases`} variant="ghost">
              <Translate id="selfHost.hero.releases">Releases on GitHub</Translate>
            </ButtonLink>
          </Actions>
          <CheckList items={['Docker Compose v2', 'Java 21 · Spring Boot', 'PostgreSQL 17']} />
        </div>
        <div className={styles.heroTerminal}>
          <Terminal
            label={translate({id: 'selfHost.terminal.label', message: 'Quickstart'})}
            code={quickstart}
            codeLabel={translate({id: 'selfHost.terminal.codeLabel', message: 'Quickstart commands'})}
            copyLabel={translate({id: 'selfHost.terminal.copy', message: 'Copy the quickstart commands'})}
            footer={
              <Translate
                id="selfHost.terminal.open"
                values={{url: <code>http://localhost:8080/register</code>}}>
                {'Then open {url} to create the first administrator.'}
              </Translate>
            }
            warning={
              <>
                <strong>
                  <Translate id="selfHost.terminal.keepEnv.title">Keep the generated .env file.</Translate>
                </strong>{' '}
                <Translate id="selfHost.terminal.keepEnv.text">
                  It contains the database password and the key that encrypts stored credentials.
                </Translate>
              </>
            }
          />
        </div>
      </Container>
    </Hero>
  );
}

function Requirements() {
  return (
    <Section labelledBy="requirements-title">
      <Container className={styles.stack}>
        <SectionHeader
          id="requirements-title"
          eyebrow={translate({id: 'selfHost.requirements.eyebrow', message: 'Before you start'})}
          title={translate({id: 'selfHost.requirements.title', message: 'What the host needs'})}
          lead={translate({
            id: 'selfHost.requirements.lead',
            message: 'A single machine you control, with a few standard tools. Nothing to compile and no repository to clone.',
          })}
        />
        <Grid min={220}>
          <Card icon="server" title="Docker Compose v2">
            <Translate id="selfHost.requirements.docker">
              Docker Engine or Docker Desktop, with a Compose recent enough to wait for a healthy service. It runs the
              application and PostgreSQL containers.
            </Translate>
          </Card>
          <Card icon="key" title="openssl">
            <Translate id="selfHost.requirements.openssl">
              Generates the database password and the credential master key on the first run.
            </Translate>
          </Card>
          <Card icon="file-check" title={translate({id: 'selfHost.requirements.verify.title', message: 'curl and sha256sum'})}>
            <Translate id="selfHost.requirements.verify">
              Download the release files and check them against SHA256SUMS. On macOS, use shasum -a 256.
            </Translate>
          </Card>
          <Card icon="cpu" title={translate({id: 'selfHost.requirements.platform.title', message: 'Linux or macOS'})}>
            <Translate id="selfHost.requirements.platform">
              On Windows, run it inside WSL 2. The image is amd64 only, so Apple silicon runs it under emulation.
            </Translate>
          </Card>
          <Card icon="globe" title={translate({id: 'selfHost.requirements.network.title', message: 'Network'})}>
            <Translate id="selfHost.requirements.network">
              The first start downloads the two images. After that it calls only the sources you connect and the
              providers you enable; sources that send webhooks must be able to reach it.
            </Translate>
          </Card>
        </Grid>
      </Container>
    </Section>
  );
}

function QuickstartSteps() {
  const {
    i18n: {currentLocale},
  } = useDocusaurusContext();
  const media = (file) => useBaseUrl(`/media/self-host/${file}`);
  return (
    <Section band="alt" labelledBy="steps-title">
      <Container className={styles.splitTop}>
        <div className={styles.stepsIntro}>
          <SectionHeader
            id="steps-title"
            eyebrow={translate({id: 'selfHost.steps.eyebrow', message: 'Under the hood'})}
            title={translate({id: 'selfHost.steps.title', message: 'What the quickstart does'})}
            lead={translate({
              id: 'selfHost.steps.lead',
              message: 'Five steps: you run the first two yourself, and quickstart.sh does the rest. Read it before you run it.',
            })}
          />
          <TextLink to={`${repository}/blob/main/quickstart/quickstart.sh`}>
            <Translate id="selfHost.steps.source">Read quickstart.sh</Translate>
          </TextLink>
        </div>
        <Steps
          className={styles.stepsList}
          items={[
            {
              title: translate({id: 'selfHost.steps.download.title', message: 'Download the release files'}),
              text: translate({
                id: 'selfHost.steps.download.text',
                message: 'compose.yaml, quickstart.sh and SHA256SUMS come from the latest GitHub release.',
              }),
            },
            {
              title: translate({id: 'selfHost.steps.verify.title', message: 'Verify before running anything'}),
              text: translate({
                id: 'selfHost.steps.verify.text',
                message: 'sha256sum checks the downloaded files against the published checksums.',
              }),
            },
            {
              title: translate({id: 'selfHost.steps.secrets.title', message: 'Check the host, then write .env'}),
              text: translate({
                id: 'selfHost.steps.secrets.text',
                message:
                  'The script confirms Docker, Compose v2 and a reachable daemon, and installs nothing. On the first run it writes a new database password and credential master key into .env, readable only by you.',
              }),
            },
            {
              title: translate({id: 'selfHost.steps.start.title', message: 'Start two containers'}),
              text: translate({
                id: 'selfHost.steps.start.text',
                message: 'The ReleaseFlow application and PostgreSQL, and nothing else. It waits until the application is healthy.',
              }),
            },
            {
              title: translate({id: 'selfHost.steps.private.title', message: 'Stay private by default'}),
              text: translate({
                id: 'selfHost.steps.private.text',
                message: 'The application listens on 127.0.0.1 only, and the database port is not published at all.',
              }),
            },
          ]}
        />
      </Container>
      <Container className={styles.quickstartVideo}>
        <ScreenshotFrame
          caption={translate({
            id: 'selfHost.steps.video.caption',
            message:
              'The commands above against the v1.0.0 release, in 38 seconds. The images were already on this machine; a first start downloads them, which takes longer.',
          })}>
          <video controls muted playsInline preload="metadata" poster={media('s-1.poster.webp')} width={1280} height={760}>
            <source src={media('s-1.mp4')} type="video/mp4" />
            <track kind="captions" srcLang="en" label="English" src={media('s-1.en.vtt')} default={currentLocale === 'en'} />
            <track kind="captions" srcLang="vi" label="Tiếng Việt" src={media('s-1.vi.vtt')} default={currentLocale === 'vi'} />
          </video>
        </ScreenshotFrame>
      </Container>
    </Section>
  );
}

function Arrow() {
  return (
    <span className={styles.arrow} aria-hidden="true">
      →
    </span>
  );
}

function Topology() {
  return (
    <Section id="topology" labelledBy="topology-title">
      <Container className={styles.stack}>
        <SectionHeader
          id="topology-title"
          eyebrow={translate({id: 'selfHost.topology.eyebrow', message: 'Going to production'})}
          title={translate({id: 'selfHost.topology.title', message: 'Put your own proxy in front, keep the rest private.'})}>
          <p className={styles.body}>
            <Translate
              id="selfHost.topology.lead"
              values={{
                cookie: <code>RELEASEFLOW_SESSION_COOKIE_SECURE=true</code>,
                url: <code>RELEASEFLOW_PUBLIC_BASE_URL</code>,
              }}>
              {
                'The quickstart is for one machine: it listens on loopback only, and TLS, backups and secret storage are a real deployment’s own to bring. To share ReleaseFlow with your team, serve it over HTTPS from a reverse proxy you already run, and set {cookie} and {url} to the public address.'
              }
            </Translate>
          </p>
        </SectionHeader>
        <figure
          className={styles.topology}
          aria-label={translate({
            id: 'selfHost.topology.figure',
            message:
              'Your team’s browsers and the webhooks from GitHub, GitLab and Linear reach a reverse proxy on your infrastructure, which forwards to ReleaseFlow on 127.0.0.1 port 8080; ReleaseFlow uses a PostgreSQL database that is not published, and makes outbound calls only to the source APIs and providers you configure.',
          })}>
          <div className={styles.inbound}>
            <div className={styles.node}>
              <span className={styles.nodeMeta}>
                <Translate id="selfHost.topology.team">Your team</Translate>
              </span>
              <span className={styles.nodeName}>
                <Translate id="selfHost.topology.browser">Browser</Translate>
              </span>
            </div>
            <div className={styles.node}>
              <span className={styles.nodeMeta}>
                <Translate id="selfHost.topology.webhooks">Source webhooks</Translate>
              </span>
              <span className={styles.nodeName}>GitHub · GitLab · Linear</span>
            </div>
          </div>
          <Arrow />
          <div className={styles.boundary}>
            <span className={styles.boundaryLabel}>
              <Translate id="selfHost.topology.boundary">Your infrastructure</Translate>
            </span>
            <div className={styles.boundaryRow}>
              <div className={styles.node}>
                <span className={styles.nodeName}>
                  <Translate id="selfHost.topology.proxy">Reverse proxy</Translate>
                </span>
                <span className={styles.nodeMeta}>
                  <Translate id="selfHost.topology.proxy.meta">TLS on your domain</Translate>
                </span>
              </div>
              <Arrow />
              <div className={styles.node}>
                <span className={styles.nodeName}>ReleaseFlow</span>
                <span className={styles.nodeMeta}>
                  <span className={styles.mono}>127.0.0.1:8080</span> · Java 21
                </span>
              </div>
              <Arrow />
              <div className={styles.node}>
                <span className={styles.nodeName}>PostgreSQL</span>
                <span className={styles.nodeMeta}>
                  <Translate id="selfHost.topology.database.meta">Not published · encrypted credentials</Translate>
                </span>
              </div>
            </div>
          </div>
          <Arrow />
          <div className={styles.outbound}>
            <span className={styles.nodeMeta}>
              <Translate id="selfHost.topology.outbound">Outbound, only what you configure</Translate>
            </span>
            <span className={styles.nodeName}>
              <Translate id="selfHost.topology.outbound.list">Source APIs · AI · DeepL · Email · Delivery</Translate>
            </span>
          </div>
        </figure>
      </Container>
    </Section>
  );
}

function ProductionChecklist() {
  return (
    <Section band="alt" id="production-checklist" labelledBy="checklist-title">
      <Container className={styles.stack}>
        <div className={styles.headerRow}>
          <SectionHeader
            id="checklist-title"
            eyebrow={translate({id: 'selfHost.checklist.eyebrow', message: 'Production checklist'})}
            title={translate({id: 'selfHost.checklist.title', message: 'Before your team relies on it'})}
          />
          <TextLink to="/docs/operations">
            <Translate id="selfHost.checklist.guide">Operations guide</Translate>
          </TextLink>
        </div>
        <Checklist
          storageKey="releaseflow:production-checklist:v1"
          items={[
            {
              id: 'https',
              lead: translate({id: 'selfHost.checklist.https.lead', message: 'Serve over HTTPS.'}),
              text: translate({
                id: 'selfHost.checklist.https.text',
                message: 'Put a reverse proxy on your own domain in front, and set RELEASEFLOW_SESSION_COOKIE_SECURE=true.',
              }),
            },
            {
              id: 'backup',
              lead: translate({id: 'selfHost.checklist.backup.lead', message: 'Back up PostgreSQL.'}),
              text: translate({
                id: 'selfHost.checklist.backup.text',
                message: 'On a schedule, and test a restore at least once.',
              }),
            },
            {
              id: 'env',
              lead: translate({id: 'selfHost.checklist.env.lead', message: 'Keep the .env file safe.'}),
              text: translate({
                id: 'selfHost.checklist.env.text',
                message: 'It holds the master key that encrypts stored credentials; without it, the data cannot be opened again.',
              }),
            },
            {
              id: 'database',
              lead: translate({id: 'selfHost.checklist.database.lead', message: 'Leave the database unpublished.'}),
              text: translate({id: 'selfHost.checklist.database.text', message: 'Only the application should reach it.'}),
            },
            {
              id: 'providers',
              lead: translate({id: 'selfHost.checklist.providers.lead', message: 'Enable only the providers you need.'}),
              text: translate({
                id: 'selfHost.checklist.providers.text',
                message: 'Each one is an outbound connection you chose.',
              }),
            },
            {
              id: 'releases',
              lead: translate({id: 'selfHost.checklist.releases.lead', message: 'Watch releases and advisories.'}),
              text: translate({
                id: 'selfHost.checklist.releases.text',
                message: 'Read the changelog before every upgrade: the database migrates forward only.',
              }),
            },
          ]}
        />
      </Container>
    </Section>
  );
}

function Operations() {
  return (
    <Section id="operations" labelledBy="operations-title">
      <Container className={styles.stack}>
        <SectionHeader
          id="operations-title"
          eyebrow={translate({id: 'selfHost.operations.eyebrow', message: 'Operations'})}
          title={translate({id: 'selfHost.operations.title', message: 'Run it day to day'})}
        />
        <Grid min={260}>
          <LinkCard
            to="/docs/configuration"
            icon="sliders-vertical"
            title={translate({id: 'selfHost.operations.configuration.title', message: 'Configuration reference'})}>
            <Translate id="selfHost.operations.configuration.text">
              Every setting the application reads, grouped by purpose.
            </Translate>
          </LinkCard>
          <LinkCard
            to="/docs/getting-started#quickstart"
            icon="rotate-cw"
            title={translate({id: 'selfHost.operations.upgrade.title', message: 'Upgrading'})}>
            <Translate id="selfHost.operations.upgrade.text">
              Download the newer bundle beside .env, verify it, and run the script again.
            </Translate>
          </LinkCard>
          <LinkCard
            to="/docs/operations#health-and-metrics"
            icon="activity"
            title={translate({id: 'selfHost.operations.health.title', message: 'Health and metrics'})}>
            <Translate id="selfHost.operations.health.text">
              A private management port that serves health and four Prometheus metrics.
            </Translate>
          </LinkCard>
          <LinkCard
            to="/docs/operations#published-artifacts"
            icon="package"
            title={translate({id: 'selfHost.operations.artifacts.title', message: 'Published artifacts'})}>
            <Translate id="selfHost.operations.artifacts.text">
              What each release publishes, and how to verify the downloads.
            </Translate>
          </LinkCard>
        </Grid>
        <div className={styles.dashboard}>
          <ScreenshotFrame
            src={useBaseUrl('/media/self-host/s-2-grafana.webp')}
            width={1600}
            height={1000}
            alt={translate({
              id: 'selfHost.operations.dashboard.alt',
              message:
                'The ReleaseFlow Operations dashboard in Grafana over 24 hours: changes needing review, classification throughput and time, AI error rate, action failures, unconfirmed deliveries, and whether Prometheus reaches the application',
            })}
            caption={translate({
              id: 'selfHost.operations.dashboard.caption',
              message:
                'The dashboard the demo stack provisions in Grafana, over a day of a sample workspace. Grafana is not translated.',
            })}
          />
        </div>
      </Container>
    </Section>
  );
}

function Questions() {
  return (
    <Section band="alt" labelledBy="faq-title">
      <Container size="narrow" className={styles.stack}>
        <SectionHeader
          id="faq-title"
          align="center"
          eyebrow={translate({id: 'selfHost.faq.eyebrow', message: 'FAQ'})}
          title={translate({id: 'selfHost.faq.title', message: 'Self-hosting questions'})}
        />
        <Faq
          items={[
            {
              question: translate({id: 'selfHost.faq.edition.q', message: 'Is there a hosted or paid edition?'}),
              answer: translate({
                id: 'selfHost.faq.edition.a',
                message:
                  'No. Everyone runs the same Apache-2.0 code. There is no hosted control plane or enterprise edition to depend on.',
              }),
            },
            {
              question: translate({id: 'selfHost.faq.ai.q', message: 'Do I need an AI provider?'}),
              answer: translate({
                id: 'selfHost.faq.ai.a',
                message:
                  'No. The rules classify changes without it. AI, translation, email and delivery integrations are optional, and each runs only when you configure it.',
              }),
            },
            {
              question: translate({id: 'selfHost.faq.outbound.q', message: 'Which outbound connections does it make?'}),
              answer: translate({
                id: 'selfHost.faq.outbound.a',
                message:
                  'Only to the sources you connect and the providers you configure. You decide which external services it may reach.',
              }),
            },
            {
              question: translate({id: 'selfHost.faq.secrets.q', message: 'How are tokens and webhook secrets stored?'}),
              answer: translate({
                id: 'selfHost.faq.secrets.a',
                message:
                  'Encrypted in PostgreSQL with the master key you supply. They are never read back into a page or an API response.',
              }),
            },
            {
              question: translate({id: 'selfHost.faq.help.q', message: 'Where do I get help?'}),
              answer: (
                <Translate
                  id="selfHost.faq.help.a"
                  values={{
                    discussions: (
                      <Link to={`${repository}/discussions`}>
                        <Translate id="selfHost.faq.help.discussions">GitHub Discussions</Translate>
                      </Link>
                    ),
                    issues: (
                      <Link to={`${repository}/issues`}>
                        <Translate id="selfHost.faq.help.issues">Issues</Translate>
                      </Link>
                    ),
                    security: (
                      <Link to="/security">
                        <Translate id="selfHost.faq.help.security">private reporting</Translate>
                      </Link>
                    ),
                  }}>
                  {'Ask in {discussions}, report bugs in {issues}, and send security problems through {security}.'}
                </Translate>
              ),
            },
          ]}
        />
      </Container>
    </Section>
  );
}

export default function SelfHost() {
  return (
    <Layout
      title={translate({id: 'selfHost.meta.title', message: 'Self-host'})}
      description={translate({
        id: 'selfHost.meta.description',
        message: 'Run ReleaseFlow and PostgreSQL on your own machine with the Docker Compose quickstart.',
      })}>
      <main>
        <HeroSection />
        <Requirements />
        <QuickstartSteps />
        <Topology />
        <ProductionChecklist />
        <Operations />
        <Questions />
        <CtaBand
          id="self-host-help-title"
          title={translate({id: 'selfHost.cta.title', message: 'Stuck on a step?'})}
          actions={
            <>
              <ActionButton onClick={openSearch} icon="search" aria-keyshortcuts="Control+K Meta+K">
                <Translate id="selfHost.cta.search">Search the docs</Translate>
              </ActionButton>
              <ButtonLink to={`${repository}/discussions`} variant="ghost">
                <Translate id="selfHost.cta.discussions">Ask in Discussions</Translate>
              </ButtonLink>
            </>
          }>
          <Translate id="selfHost.cta.text">
            Search the docs, or ask in Discussions with your release version and what you ran.
          </Translate>
        </CtaBand>
      </main>
    </Layout>
  );
}
