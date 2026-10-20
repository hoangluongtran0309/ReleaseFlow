// Community: where each kind of contribution goes, how a first pull request lands, and who
// maintains the project. Facts come from CONTRIBUTING.md, CODE_OF_CONDUCT.md, SECURITY.md,
// .github/ISSUE_TEMPLATE and the labels the repository uses; change them together.

import Link from '@docusaurus/Link';
import Translate, {translate} from '@docusaurus/Translate';
import CodeBlock from '@theme/CodeBlock';
import Heading from '@theme/Heading';
import Layout from '@theme/Layout';
import {
  Actions,
  ButtonLink,
  Container,
  Display,
  Eyebrow,
  ExternalMark,
  Grid,
  Hero,
  Lead,
  LinkCard,
  Section,
  SectionHeader,
} from '@site/src/components/ui';
import styles from './community.module.css';

const repository = 'https://github.com/hoangluongtran0309/ReleaseFlow';
const conductContact = 'hoangluongtran0309@gmail.com';

// Labels the repository's issue templates and Dependabot apply.
const LABELS = ['bug', 'enhancement', 'documentation'];

const branchCommands = `git clone https://github.com/<you>/ReleaseFlow.git
cd ReleaseFlow
git switch -c fix/short-description origin/develop`;

function Step({number, title, children}) {
  return (
    <li className={styles.step}>
      <span className={styles.stepNumber} aria-hidden="true">
        {number}
      </span>
      <div className={styles.stepBody}>
        <Heading as="h3" className={styles.stepTitle}>
          {title}
        </Heading>
        {children}
      </div>
    </li>
  );
}

export default function Community() {
  return (
    <Layout
      title={translate({id: 'community.meta.title', message: 'Community'})}
      description={translate({id: 'community.meta.description', message: 'Where to ask, report and contribute to ReleaseFlow.'})}>
      <main>
        <Hero labelledBy="community-title">
          <Container className={styles.heroCopy}>
            <Eyebrow>
              <Translate id="community.hero.eyebrow">Community</Translate>
            </Eyebrow>
            <Display id="community-title">
              <Translate id="community.hero.title">Build ReleaseFlow in the open.</Translate>
            </Display>
            <Lead>
              <Translate id="community.hero.lead">
                Questions, bug reports, docs fixes and code all move the project forward. Here is where each one goes, and
                what happens after you send it.
              </Translate>
            </Lead>
            <Actions>
              <ButtonLink to={`${repository}/discussions`}>
                <Translate id="community.hero.discussions">Join the discussion</Translate>
              </ButtonLink>
              <ButtonLink to={`${repository}/blob/main/CONTRIBUTING.md`} variant="ghost">
                <Translate id="community.hero.contributing">Contributing guide</Translate>
              </ButtonLink>
            </Actions>
          </Container>
        </Hero>

        <Section labelledBy="where-title">
          <Container className={styles.stack}>
            <SectionHeader
              id="where-title"
              eyebrow={translate({id: 'community.where.eyebrow', message: 'Where to go'})}
              title={translate({id: 'community.where.title', message: 'Pick the right place'})}
            />
            <Grid min={220}>
              <LinkCard to={`${repository}/discussions`} icon="message-square" title={translate({id: 'community.where.question.title', message: 'Ask a question or share an idea'})}>
                <Translate id="community.where.question.text">
                  Discussions are for setup trouble, workflow questions, and talking an idea through before opening an issue.
                </Translate>
              </LinkCard>
              <LinkCard to={`${repository}/issues/new/choose`} icon="file-warning" title={translate({id: 'community.where.bug.title', message: 'Report a bug'})}>
                <Translate id="community.where.bug.text">
                  The bug report template asks for your version, what you ran, and what you expected.
                </Translate>
              </LinkCard>
              <LinkCard to={`${repository}/issues/new/choose`} icon="sliders-vertical" title={translate({id: 'community.where.feature.title', message: 'Request a feature'})}>
                <Translate id="community.where.feature.text">
                  The feature request template starts from the problem you have, before the feature you want.
                </Translate>
              </LinkCard>
              <LinkCard to="/docs" icon="pen-line" title={translate({id: 'community.where.docs.title', message: 'Improve the docs'})}>
                <Translate id="community.where.docs.text">
                  Every docs page links to its source file. A Vietnamese page changes in the same pull request as its English one.
                </Translate>
              </LinkCard>
              <LinkCard to="/security" icon="lock" title={translate({id: 'community.where.security.title', message: 'Report a vulnerability'})} className={styles.warningCard}>
                <Translate id="community.where.security.text">Never in a public issue. Follow the security policy and report it privately.</Translate>
              </LinkCard>
            </Grid>
          </Container>
        </Section>

        <Section band="alt" labelledBy="first-pr-title">
          <Container className={styles.split}>
            <div className={styles.intro}>
              <SectionHeader
                id="first-pr-title"
                eyebrow={translate({id: 'community.pr.eyebrow', message: 'Contributing'})}
                title={translate({id: 'community.pr.title', message: 'Your first pull request'})}
                lead={translate({
                  id: 'community.pr.lead',
                  message: 'ReleaseFlow grows in small, reviewable slices, so small and focused changes are the easiest to land. Browse the issues by label:',
                })}
              />
              <ul className={styles.labels} aria-label={translate({id: 'community.pr.labels', message: 'Issue labels'})}>
                {LABELS.map((label) => (
                  <li key={label}>
                    <Link to={`${repository}/labels/${label}`} className={styles.label}>
                      {label}
                      <ExternalMark size={11} />
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
            <ol className={styles.steps}>
              <Step number={1} title={translate({id: 'community.pr.step1.title', message: 'Agree on the change first'})}>
                <p>
                  <Translate id="community.pr.step1.text">
                    Pick or open an issue. For anything bigger than a fix, talk the approach through in Discussions before you
                    write it.
                  </Translate>
                </p>
              </Step>
              <Step number={2} title={translate({id: 'community.pr.step2.title', message: 'Fork, then branch from develop'})}>
                <p>
                  <Translate id="community.pr.step2.text" values={{develop: <code>develop</code>, fix: <code>fix/…</code>, feature: <code>feature/…</code>}}>
                    {'Work on a {fix} or {feature} branch cut from {develop}, the integration branch.'}
                  </Translate>
                </p>
                <div className={styles.code}>
                  <CodeBlock language="bash">{branchCommands}</CodeBlock>
                </div>
              </Step>
              <Step number={3} title={translate({id: 'community.pr.step3.title', message: 'Build and test locally'})}>
                <p>
                  <Translate id="community.pr.step3.text" values={{verify: <code>./mvnw verify</code>, e2e: <code>npm run e2e</code>, docs: <code>npm --prefix website run build</code>}}>
                    {'You need JDK 21 and Docker, which runs PostgreSQL for the tests. Run {verify}, {e2e} when a page changed, and {docs} when a guide changed.'}
                  </Translate>
                </p>
              </Step>
              <Step number={4} title={translate({id: 'community.pr.step4.title', message: 'Open a pull request to develop'})}>
                <p>
                  <Translate id="community.pr.step4.text" values={{example: <code>fix(webhook): reject repository identity mismatch</code>}}>
                    {'Title it as a Conventional Commit, such as {example}. CI, CodeQL, Dependency Review and Secret Scan run on it, and a behaviour change adds its line to the changelog under Unreleased.'}
                  </Translate>
                </p>
              </Step>
              <Step number={5} title={translate({id: 'community.pr.step5.title', message: 'Review and merge'})}>
                <p>
                  <Translate id="community.pr.step5.text">
                    The maintainer reviews it and squashes it into develop as one commit. Your contribution is licensed under
                    Apache 2.0, with no separate agreement to sign.
                  </Translate>
                </p>
              </Step>
            </ol>
          </Container>
        </Section>

        <Section labelledBy="open-title">
          <Container className={styles.stack}>
            <SectionHeader
              id="open-title"
              eyebrow={translate({id: 'community.open.eyebrow', message: 'How the project runs'})}
              title={translate({id: 'community.open.title', message: 'Everything happens where you can see it.'})}
            />
            <Grid min={240}>
              <LinkCard to={repository} icon="code" title={translate({id: 'community.open.code.title', message: 'Code'})}>
                <Translate id="community.open.code.text">Apache 2.0, one codebase, and no closed enterprise edition.</Translate>
              </LinkCard>
              <LinkCard to="/docs/adr" icon="book" title={translate({id: 'community.open.decisions.title', message: 'Decisions'})}>
                <Translate id="community.open.decisions.text">Every significant decision is recorded as a numbered decision record.</Translate>
              </LinkCard>
              <LinkCard to="/changelog" icon="package" title={translate({id: 'community.open.releases.title', message: 'Releases'})}>
                <Translate id="community.open.releases.text">Each release is a tag with checksums, an SBOM, provenance and a changelog entry.</Translate>
              </LinkCard>
              <LinkCard to="/security" icon="lock" title={translate({id: 'community.open.security.title', message: 'Security'})}>
                <Translate id="community.open.security.text">Reports are handled privately; the fix, the advisory and the credit go out together.</Translate>
              </LinkCard>
            </Grid>
          </Container>
        </Section>

        <Section band="alt" labelledBy="maintainers-title">
          <Container className={styles.stack}>
            <Heading as="h2" id="maintainers-title" className={styles.h2}>
              <Translate id="community.maintainers.title">Maintainer</Translate>
            </Heading>
            <div className={styles.maintainer}>
              <span className={styles.avatar} aria-hidden="true">
                LH
              </span>
              <div>
                <p className={styles.maintainerName}>Luong Tran Chu Hoang</p>
                <p className={styles.maintainerRole}>
                  <Translate id="community.maintainers.role">Creator and maintainer of this personal project</Translate>
                </p>
                <Link to="https://github.com/hoangluongtran0309" className={styles.maintainerLink}>
                  @hoangluongtran0309
                  <ExternalMark size={11} />
                </Link>
              </div>
            </div>
          </Container>
        </Section>

        <section className={styles.conduct} aria-labelledby="conduct-title">
          <Container className={styles.conductInner}>
            <div className={styles.conductCopy}>
              <Heading as="h2" id="conduct-title" className={styles.h2}>
                <Translate id="community.conduct.title">Code of Conduct</Translate>
              </Heading>
              <p>
                <Translate
                  id="community.conduct.text"
                  values={{contact: <a href={`mailto:${conductContact}`}>{conductContact}</a>}}>
                  {'ReleaseFlow follows the Contributor Covenant 2.1 in issues, pull requests, discussions and every other project space. Report unacceptable behaviour privately to {contact}; the reporter’s identity is kept private.'}
                </Translate>
              </p>
            </div>
            <ButtonLink to={`${repository}/blob/main/CODE_OF_CONDUCT.md`} variant="ghost">
              <Translate id="community.conduct.read">Read the Code of Conduct</Translate>
            </ButtonLink>
          </Container>
        </section>
      </main>
    </Layout>
  );
}
