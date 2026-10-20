// The security policy: how to report, what happens next, which versions get fixes, how a
// deployment is protected, and what is in scope. Every promise here is SECURITY.md's, and
// every protection is one AGENTS.md and the guides hold the code to; change them together.

import Link from '@docusaurus/Link';
import {usePluginData} from '@docusaurus/useGlobalData';
import Translate, {translate} from '@docusaurus/Translate';
import Heading from '@theme/Heading';
import Layout from '@theme/Layout';
import {
  Actions,
  ButtonLink,
  Container,
  CtaBand,
  Display,
  Eyebrow,
  Grid,
  Hero,
  Icon,
  Lead,
  Pill,
  Section,
  SectionHeader,
  TextLink,
} from '@site/src/components/ui';
import styles from './security.module.css';

const repository = 'https://github.com/hoangluongtran0309/ReleaseFlow';
const securityContact = 'hoangluongtran0309@gmail.com';

function Protection({icon, title, children}) {
  return (
    <div className={styles.protection}>
      <Icon name={icon} size={22} strokeWidth={1.8} />
      <div>
        <Heading as="h3" className={styles.cardTitle}>
          {title}
        </Heading>
        <p className={styles.cardText}>{children}</p>
      </div>
    </div>
  );
}

function SupportedVersions() {
  const {latest} = usePluginData('changelog');
  const supported = (
    <Pill tone="fixed">
      <Translate id="security.versions.supported">Supported</Translate>
    </Pill>
  );
  return (
    <div className={styles.tableWrap} tabIndex={0} role="group" aria-labelledby="versions-title">
      <table className={styles.table}>
        <thead>
          <tr>
            <th scope="col">
              <Translate id="security.versions.version">Version</Translate>
            </th>
            <th scope="col">
              <Translate id="security.versions.fixes">Security fixes</Translate>
            </th>
          </tr>
        </thead>
        <tbody>
          {latest && (
            <tr>
              <td>
                <code>v{latest}</code>{' '}
                <span className={styles.latest}>
                  <Translate id="security.versions.latest">Latest release</Translate>
                </span>
              </td>
              <td>{supported}</td>
            </tr>
          )}
          <tr>
            <td>
              <code>main</code>
            </td>
            <td>{supported}</td>
          </tr>
          {latest && (
            <tr>
              <td>
                <Translate id="security.versions.older">Older versions</Translate>
              </td>
              <td>
                <Pill tone="breaking">
                  <Translate id="security.versions.unsupported">Not supported</Translate>
                </Pill>
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

export default function Security() {
  const {latest} = usePluginData('changelog');
  return (
    <Layout
      title={translate({id: 'security.meta.title', message: 'Security'})}
      description={translate({id: 'security.meta.description', message: 'How to report a vulnerability in ReleaseFlow privately, and what happens next.'})}>
      <main>
        <Hero labelledBy="security-title">
          <Container className={styles.split}>
            <div className={styles.heroCopy}>
              <Eyebrow className={styles.eyebrow}>
                <Icon name="lock" size={16} />
                <Translate id="security.hero.eyebrow">Security policy</Translate>
              </Eyebrow>
              <Display id="security-title">
                <Translate id="security.hero.title">Report vulnerabilities privately.</Translate>
              </Display>
              <Lead>
                <Translate id="security.hero.lead">
                  Please do not open a public issue. GitHub’s private reporting opens a draft advisory only you and the
                  maintainer can read, so a fix can ship before the details are public.
                </Translate>
              </Lead>
              <Actions>
                <ButtonLink to={`${repository}/security/advisories/new`}>
                  <Translate id="security.hero.report">Report privately on GitHub</Translate>
                </ButtonLink>
                <ButtonLink to={`${repository}/blob/main/SECURITY.md`} variant="ghost">
                  <Translate id="security.hero.policy">Read SECURITY.md</Translate>
                </ButtonLink>
              </Actions>
              <p className={styles.email}>
                <Translate id="security.hero.email" values={{email: <a href={`mailto:${securityContact}`}>{securityContact}</a>}}>
                  {'If private reporting is unavailable to you, email {email}.'}
                </Translate>
              </p>
            </div>
            <div className={styles.include}>
              <Heading as="h2" className={styles.includeTitle}>
                <Translate id="security.include.title">What to include</Translate>
              </Heading>
              <ul>
                <li>
                  <Icon name="check" size={16} strokeWidth={2.2} />
                  <Translate id="security.include.found">What you found</Translate>
                </li>
                <li>
                  <Icon name="check" size={16} strokeWidth={2.2} />
                  <Translate id="security.include.reproduce">How to reproduce it</Translate>
                </li>
                <li>
                  <Icon name="check" size={16} strokeWidth={2.2} />
                  <Translate id="security.include.reach">What an attacker could reach</Translate>
                </li>
                <li>
                  <Icon name="check" size={16} strokeWidth={2.2} />
                  <Translate id="security.include.proof">
                    A proof of concept helps; a working exploit is not required and never needs to be public
                  </Translate>
                </li>
              </ul>
            </div>
          </Container>
        </Hero>

        <Section labelledBy="process-title">
          <Container className={styles.stack}>
            <SectionHeader
              id="process-title"
              eyebrow={translate({id: 'security.process.eyebrow', message: 'After you report'})}
              title={translate({id: 'security.process.title', message: 'What happens next'})}
              lead={translate({
                id: 'security.process.lead',
                message: 'ReleaseFlow is a personal project with one maintainer, so there is no paid triage rota and no bounty.',
              })}
            />
            <ol className={styles.process}>
              {[
                {
                  title: translate({id: 'security.process.report.title', message: 'Report privately'}),
                  text: translate({id: 'security.process.report.text', message: 'Through a private draft advisory on GitHub, or by email.'}),
                  when: translate({id: 'security.process.report.when', message: 'Whenever you find it'}),
                },
                {
                  title: translate({id: 'security.process.reply.title', message: 'First reply'}),
                  text: translate({id: 'security.process.reply.text', message: 'The maintainer reads the report and answers you.'}),
                  when: translate({id: 'security.process.reply.when', message: 'Within a week'}),
                },
                {
                  title: translate({id: 'security.process.fix.title', message: 'Fix'}),
                  text: translate({id: 'security.process.fix.text', message: 'A report that turns out to be a real weakness is fixed in the latest version and on main.'}),
                  when: translate({id: 'security.process.fix.when', message: 'When the fix is ready'}),
                },
                {
                  title: translate({id: 'security.process.disclose.title', message: 'Disclose'}),
                  text: translate({id: 'security.process.disclose.text', message: 'The fix, the advisory and the credit go out together.'}),
                  when: translate({id: 'security.process.disclose.when', message: 'With the fix'}),
                },
              ].map((step, index) => (
                <li key={step.title} className={styles.processStep}>
                  <span className={styles.processNumber} aria-hidden="true">
                    {String(index + 1).padStart(2, '0')}
                  </span>
                  <Heading as="h3" className={styles.cardTitle}>
                    {step.title}
                  </Heading>
                  <p className={styles.cardText}>{step.text}</p>
                  <p className={styles.when}>{step.when}</p>
                </li>
              ))}
            </ol>
          </Container>
        </Section>

        <Section band="alt" labelledBy="versions-title">
          <Container className={styles.split}>
            <div className={styles.versionsCopy}>
              <SectionHeader
                id="versions-title"
                eyebrow={translate({id: 'security.versions.eyebrow', message: 'Supported versions'})}
                title={translate({id: 'security.versions.title', message: 'Which versions get fixes'})}
              />
              <p className={styles.body}>
                <Translate id="security.versions.lead" values={{changelog: <Link to="/changelog">{translate({id: 'security.versions.changelog', message: 'changelog'})}</Link>}}>
                  {'Only the latest released version and the current main. Older versions get no fixes, and with one maintainer there are no backports: please upgrade. The {changelog} says what each version changed.'}
                </Translate>
              </p>
              {!latest && (
                <p className={styles.note}>
                  <Translate id="security.versions.none">No version has been released yet, so main is the only supported version.</Translate>
                </p>
              )}
            </div>
            <div className={styles.versionsTable}>
              <SupportedVersions />
            </div>
          </Container>
        </Section>

        <Section labelledBy="model-title">
          <Container className={styles.stack}>
            <div className={styles.headerRow}>
              <SectionHeader
                id="model-title"
                eyebrow={translate({id: 'security.model.eyebrow', message: 'Security model'})}
                title={translate({id: 'security.model.title', message: 'How ReleaseFlow protects a deployment'})}
              />
              <TextLink to="/docs/architecture">
                <Translate id="security.model.architecture">Read the architecture</Translate>
              </TextLink>
            </div>
            <Grid min={320}>
              <Protection icon="key" title={translate({id: 'security.model.encrypted.title', message: 'Encrypted credentials'})}>
                <Translate id="security.model.encrypted.text">
                  Webhook secrets and access tokens are encrypted with the master key you supply; ReleaseFlow refuses to start
                  without a valid one.
                </Translate>
              </Protection>
              <Protection icon="lock" title={translate({id: 'security.model.writeOnly.title', message: 'Secrets are write-only'})}>
                <Translate id="security.model.writeOnly.text">
                  A stored secret is never read back into a page, an API response or a log, and a webhook secret is shown once.
                </Translate>
              </Protection>
              <Protection icon="server" title={translate({id: 'security.model.tenants.title', message: 'Organizations stay apart'})}>
                <Translate id="security.model.tenants.text">
                  The Organization always comes from the signed-in person, never from a request; another Organization’s data is
                  simply not found.
                </Translate>
              </Protection>
              <Protection icon="file-check" title={translate({id: 'security.model.signed.title', message: 'Signed webhooks, CSRF always on'})}>
                <Translate id="security.model.signed.text">
                  Each webhook proves itself with its own secret over the raw body. CSRF protection is never turned off; only
                  signed paths are exempt.
                </Translate>
              </Protection>
              <Protection icon="globe" title={translate({id: 'security.model.private.title', message: 'Private by default'})}>
                <Translate id="security.model.private.text">
                  The quickstart listens on loopback only and never publishes the database; outbound calls go only to what you
                  configure.
                </Translate>
              </Protection>
              <Protection icon="package" title={translate({id: 'security.model.releases.title', message: 'Verifiable releases'})}>
                <Translate id="security.model.releases.text">
                  Each release ships SHA256SUMS, an SBOM and a provenance attestation, and the code is scanned by CodeQL,
                  Dependency Review and a secret scan.
                </Translate>
              </Protection>
            </Grid>
          </Container>
        </Section>

        <Section band="alt" labelledBy="scope-title">
          <Container className={styles.stack}>
            <Heading as="h2" id="scope-title" className={styles.h2}>
              <Translate id="security.scope.title">Scope</Translate>
            </Heading>
            <div className={styles.scope}>
              <div className={styles.scopeCard}>
                <Heading as="h3" className={styles.scopeTitle}>
                  <Icon name="check" size={18} strokeWidth={2.2} />
                  <Translate id="security.scope.in">In scope</Translate>
                </Heading>
                <p className={styles.cardText}>
                  <Translate id="security.scope.in.text">
                    The application and its configuration: authentication and session handling, tenant isolation between
                    Organizations, webhook signature verification, secret storage and the reveal-once rules, the outbound
                    clients and their allowlists, and the CSRF and security-header configuration.
                  </Translate>
                </p>
              </div>
              <div className={styles.scopeCard}>
                <Heading as="h3" className={styles.scopeTitle}>
                  <Icon name="x" size={18} strokeWidth={2.2} />
                  <Translate id="security.scope.out">Out of scope</Translate>
                </Heading>
                <ul className={styles.scopeList}>
                  <li>
                    <Translate id="security.scope.out.demo" values={{file: <code>docker-compose.demo.yml</code>}}>
                      {'The demo Compose stack ({file}), which exists to evaluate ReleaseFlow locally and is not a production deployment.'}
                    </Translate>
                  </li>
                  <li>
                    <Translate id="security.scope.out.admin">
                      A finding that needs an administrator’s own credentials to reach something an administrator may already reach.
                    </Translate>
                  </li>
                  <li>
                    <Translate id="security.scope.out.scanner">A scanner’s report with no explanation of what it actually reaches.</Translate>
                  </li>
                </ul>
              </div>
            </div>
          </Container>
        </Section>

        <CtaBand
          id="security-cta-title"
          title={translate({id: 'security.cta.title', message: 'Running it for a team?'})}
          actions={
            <ButtonLink to="/self-host#production-checklist" arrow>
              <Translate id="security.cta.checklist">Production checklist</Translate>
            </ButtonLink>
          }>
          <Translate id="security.cta.text">HTTPS, backups and the master key come before your team relies on it.</Translate>
        </CtaBand>
      </main>
    </Layout>
  );
}
