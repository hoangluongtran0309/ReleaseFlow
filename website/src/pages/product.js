// The product tour: collect, classify, review, publish, then the optional providers.
// Behaviour described here comes from docs/sources.md, docs/changes.md,
// docs/release-notes.md and docs/automation.md; the change "Fix time zone offset in
// scheduled digests" and its notes are illustrations and are always labelled as examples.
// The video and screenshots are of a real release of a sample project, and say so.

import useBaseUrl from '@docusaurus/useBaseUrl';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Translate, {translate} from '@docusaurus/Translate';
import Heading from '@theme/Heading';
import Layout from '@theme/Layout';
import {
  Actions,
  ButtonLink,
  Container,
  CtaBand,
  Eyebrow,
  Grid,
  Hero,
  Icon,
  Lead,
  Pill,
  ScreenshotFrame,
  Section,
  SectionHeader,
  Tag,
  TextLink,
} from '@site/src/components/ui';
import styles from './product.module.css';

const repository = 'https://github.com/hoangluongtran0309/ReleaseFlow';
const exampleTitle = 'Fix time zone offset in scheduled digests';

function ExampleLabel({children}) {
  return <span className={styles.exampleLabel}>{children ?? translate({id: 'product.example', message: 'Example'})}</span>;
}

function Connector() {
  return (
    <span className={styles.connector} aria-hidden="true">
      <Icon name="arrow-down" size={18} />
    </span>
  );
}

function Checks({items}) {
  return (
    <ul className={styles.checks}>
      {items.map((item) => (
        <li key={item}>
          <Icon name="check" size={18} strokeWidth={2.2} />
          {item}
        </li>
      ))}
    </ul>
  );
}

function getSteps() {
  return [
    {
      icon: 'git-merge',
      title: translate({id: 'product.steps.collect.title', message: 'Collect'}),
      text: translate({
        id: 'product.steps.collect.text',
        message: 'Merged pull requests and finished issues from the tools your team already uses.',
      }),
      tags: ['GitHub', 'GitLab', 'Linear', 'Jira'],
    },
    {
      icon: 'sliders-vertical',
      title: translate({id: 'product.steps.classify.title', message: 'Classify'}),
      text: translate({
        id: 'product.steps.classify.text',
        message: 'Rules sort each change into a category and record which rule matched.',
      }),
      tags: [
        translate({id: 'product.steps.classify.tag.rules', message: 'Readable rules'}),
        translate({id: 'product.steps.classify.tag.ai', message: 'Optional AI'}),
      ],
    },
    {
      icon: 'eye',
      title: translate({id: 'product.steps.review.title', message: 'Review'}),
      text: translate({
        id: 'product.steps.review.text',
        message: 'A person decides on every change before the release is approved.',
      }),
      tags: ['Change Inbox', translate({id: 'product.steps.review.tag.release', message: 'Release review'})],
    },
    {
      icon: 'send',
      title: translate({id: 'product.steps.publish.title', message: 'Publish'}),
      text: translate({
        id: 'product.steps.publish.text',
        message: 'A note for each audience, frozen when the release is published.',
      }),
      tags: ['GitHub Releases', 'Slack', translate({id: 'product.steps.publish.tag.changelog', message: 'Public changelog'})],
    },
  ];
}

function HeroSection() {
  return (
    <Hero labelledBy="product-title">
      <Container className={styles.heroStack}>
        <div className={styles.heroCopy}>
          <Eyebrow className={styles.markEyebrow}>
            <img src={useBaseUrl('/favicon.svg')} alt="" width={24} height={24} />
            <Translate id="product.hero.eyebrow">Product</Translate>
          </Eyebrow>
          <Heading as="h1" id="product-title" className={styles.display}>
            <Translate id="product.hero.title">Every change reviewed before it is announced.</Translate>
          </Heading>
          <Lead className={styles.heroLead}>
            <Translate id="product.hero.lead">
              ReleaseFlow collects merged work from the tools your team already uses, classifies it with rules anyone can
              read, asks a person to decide, and publishes a note written for each audience.
            </Translate>
          </Lead>
          <Actions>
            <ButtonLink to="/self-host" arrow>
              <Translate id="product.hero.selfHost">Self-host ReleaseFlow</Translate>
            </ButtonLink>
            <ButtonLink to="/docs/getting-started#quickstart" variant="ghost">
              <Translate id="product.hero.quickstart">Read the quickstart</Translate>
            </ButtonLink>
          </Actions>
        </div>
        <ol className={styles.stepCards} aria-label={translate({id: 'product.steps.label', message: 'How ReleaseFlow works'})}>
          {getSteps().map((step, index) => (
            <li key={step.icon} className={styles.stepCard}>
              <div className={styles.stepTop}>
                <span className={styles.iconTile}>
                  <Icon name={step.icon} size={20} />
                </span>
                <span className={styles.stepNumber} aria-hidden="true">
                  {String(index + 1).padStart(2, '0')}
                </span>
              </div>
              <Heading as="h2" className={styles.stepTitle}>
                {step.title}
              </Heading>
              <p className={styles.stepText}>{step.text}</p>
              <div className={styles.tags}>
                {step.tags.map((tag) => (
                  <Tag key={tag}>{tag}</Tag>
                ))}
              </div>
            </li>
          ))}
        </ol>
      </Container>
    </Hero>
  );
}

function Walkthrough() {
  const {
    i18n: {currentLocale},
  } = useDocusaurusContext();
  const media = (file) => useBaseUrl(`/media/product/${file}`);
  return (
    <Section band="alt" labelledBy="walkthrough-title">
      <Container className={styles.walkthrough}>
        <SectionHeader
          id="walkthrough-title"
          align="center"
          eyebrow={translate({id: 'product.walkthrough.eyebrow', message: 'Walkthrough'})}
          title={translate({id: 'product.walkthrough.title', message: 'One release, from draft to the public changelog.'})}
          lead={translate({
            id: 'product.walkthrough.lead',
            message:
              'Fifty seconds of a real release of a sample project: created, reviewed, approved, published, read without signing in, and posted to Slack.',
          })}
        />
        <ScreenshotFrame
          caption={translate({
            id: 'product.walkthrough.caption',
            message: 'Release v1.1.0 of the sample project dbvault. Captions in English and Vietnamese.',
          })}>
          <video controls muted playsInline preload="metadata" poster={media('p-0.poster.webp')} width={1280} height={800}>
            <source src={media('p-0.mp4')} type="video/mp4" />
            <track kind="captions" srcLang="en" label="English" src={media('p-0.en.vtt')} default={currentLocale === 'en'} />
            <track kind="captions" srcLang="vi" label="Tiếng Việt" src={media('p-0.vi.vtt')} default={currentLocale === 'vi'} />
          </video>
        </ScreenshotFrame>
      </Container>
    </Section>
  );
}

/** A screenshot of the stage just described, under its words and its diagram. */
function StageScreenshot({file, localized, width, height, alt, caption}) {
  const {
    i18n: {currentLocale},
  } = useDocusaurusContext();
  const src = useBaseUrl(`/media/product/${localized ? `${file}.${currentLocale}` : file}.webp`);
  return (
    <div className={styles.stageScreenshot}>
      <ScreenshotFrame src={src} alt={alt} width={width} height={height} caption={caption} />
    </div>
  );
}

/** One stage of the tour: words on one side, a figure on the other, and a screenshot below. */
function DeepDive({band, reverse, id, eyebrow, title, body, checks, link, figure, screenshot, narrowCopy}) {
  return (
    <Section band={band} labelledBy={id} className={styles.deepDive}>
      <Container className={reverse ? `${styles.split} ${styles.reverse}` : styles.split}>
        <div className={narrowCopy ? `${styles.copy} ${styles.copyNarrow}` : styles.copy}>
          <SectionHeader id={id} eyebrow={eyebrow} title={title} lead={body} />
          <Checks items={checks} />
          {link}
        </div>
        {figure}
      </Container>
      {screenshot && <Container>{screenshot}</Container>}
    </Section>
  );
}

function CollectFigure() {
  const sources = [
    ['GitHub', translate({id: 'product.collect.figure.pullRequests', message: 'pull requests'})],
    ['GitLab', translate({id: 'product.collect.figure.mergeRequests', message: 'merge requests'})],
    ['Linear', translate({id: 'product.collect.figure.issues', message: 'issues'})],
    ['Jira', translate({id: 'product.collect.figure.issues', message: 'issues'})],
  ];
  return (
    <figure
      className={styles.figure}
      aria-label={translate({
        id: 'product.collect.figure.label',
        message: 'Diagram: changes from GitHub, GitLab, Linear and Jira become one kind of change in the Change Inbox.',
      })}>
      <p className={styles.figureLabel}>
        <Translate id="product.collect.figure.sources">Sources</Translate>
      </p>
      <div className={styles.sourceGrid}>
        {sources.map(([name, kind]) => (
          <div key={name} className={styles.box}>
            <span className={styles.boxName}>{name}</span>
            <span className={styles.meta}>{kind}</span>
          </div>
        ))}
      </div>
      <Connector />
      <div className={styles.result}>
        <div className={styles.resultHead}>
          <span className={styles.boxName}>
            <Translate id="product.collect.figure.change">Recorded change</Translate>
          </span>
          <ExampleLabel />
        </div>
        <dl className={styles.fields}>
          <dt>
            <Translate id="product.collect.figure.title">Title</Translate>
          </dt>
          <dd>{exampleTitle}</dd>
          <dt>
            <Translate id="product.collect.figure.source">Source</Translate>
          </dt>
          <dd>
            <Translate id="product.collect.figure.sourceValue">GitHub · pull request</Translate>
          </dd>
          <dt>
            <Translate id="product.collect.figure.labels">Labels</Translate>
          </dt>
          <dd className={styles.labels}>
            <Pill>bug</Pill>
            <Pill>digests</Pill>
          </dd>
        </dl>
      </div>
      <Connector />
      <div className={`${styles.box} ${styles.inboxRow}`}>
        <Icon name="inbox" size={18} />
        <span className={styles.boxName}>Change Inbox</span>
        <span className={styles.meta}>
          <Translate id="product.collect.figure.inbox">ready for classification and review</Translate>
        </span>
      </div>
    </figure>
  );
}

function ClassifyFigure() {
  return (
    <figure
      className={`${styles.figure} ${styles.figureRaised}`}
      aria-label={translate({
        id: 'product.classify.figure.label',
        message: 'Diagram: the label bug matches a rule that assigns the Fix category, and the reviewer sees why.',
      })}>
      <ExampleLabel />
      <div className={styles.box}>
        <span className={styles.meta}>
          <Translate id="product.classify.figure.change">Change</Translate>
        </span>
        <span className={styles.boxTitle}>{exampleTitle}</span>
        <span className={styles.meta}>
          <Translate id="product.classify.figure.changeMeta">GitHub pull request · labels: bug, digests</Translate>
        </span>
      </div>
      <Connector />
      <div className={`${styles.box} ${styles.ruleRow}`}>
        <div className={styles.ruleText}>
          <span className={styles.meta}>
            <Translate id="product.classify.figure.rule">Rule matched</Translate>
          </span>
          <span className={styles.boxTitle}>
            <Translate id="product.classify.figure.ruleValue" values={{label: <code>bug</code>}}>
              {'Label {label}'}
            </Translate>
          </span>
        </div>
        <span className={styles.meta}>
          <Translate id="product.classify.figure.assigns">assigns the category</Translate>
        </span>
        <Pill tone="fixed">Fix</Pill>
      </div>
      <Connector />
      <div className={styles.result}>
        <span className={styles.metaStrong}>
          <Translate id="product.classify.figure.shown">Shown to the reviewer</Translate>
        </span>
        <div className={styles.because}>
          <Pill tone="fixed">Fix</Pill>
          <span>
            <Translate id="product.classify.figure.because" values={{label: <strong>bug</strong>}}>
              {'because the pull request has the label {label}'}
            </Translate>
          </span>
        </div>
      </div>
    </figure>
  );
}

function Publish() {
  const audiences = [
    {
      code: 'contributor',
      name: translate({id: 'product.publish.contributor.name', message: 'Contributor'}),
      intent: translate({id: 'product.publish.contributor.intent', message: 'Implementation detail and code changes'}),
      destination: 'GitHub Releases',
      note: 'fix(digests): schedule weekly digests in the workspace time zone instead of UTC',
      mono: true,
    },
    {
      code: 'operator',
      name: translate({id: 'product.publish.operator.name', message: 'Operator'}),
      intent: translate({id: 'product.publish.operator.intent', message: 'Operational risk, rollback, what to watch'}),
      destination: 'Slack',
      note: translate({
        id: 'product.publish.operator.note',
        message: 'Digests now follow each workspace’s time zone. Nothing to migrate; watch the first scheduled run.',
      }),
    },
    {
      code: 'end_user',
      name: translate({id: 'product.publish.endUser.name', message: 'End user'}),
      intent: translate({id: 'product.publish.endUser.intent', message: 'Plain language, what people notice'}),
      destination: translate({id: 'product.steps.publish.tag.changelog', message: 'Public changelog'}),
      note: translate({
        id: 'product.publish.endUser.note',
        message: 'Scheduled digests now arrive at the time you chose, wherever your team works.',
      }),
    },
  ];
  return (
    <Section band="alt" labelledBy="publish-title" className={styles.deepDive}>
      <Container className={styles.publishStack}>
        <SectionHeader
          id="publish-title"
          align="center"
          eyebrow={translate({id: 'product.publish.eyebrow', message: '04 · Publish'})}
          title={translate({id: 'product.publish.title', message: 'One release, written for each audience.'})}
          lead={translate({
            id: 'product.publish.lead',
            message:
              'When a release is approved, ReleaseFlow writes one note per audience from that audience’s template. Publishing freezes them, and automation rules deliver each note where its readers already are.',
          })}
        />
        <Grid min={320}>
          {audiences.map((audience) => (
            <article key={audience.code} className={styles.audience}>
              <div className={styles.audienceHead}>
                <Heading as="h3" className={styles.audienceName}>
                  {audience.name}
                </Heading>
                <Tag>{audience.destination}</Tag>
              </div>
              <p className={styles.meta}>{audience.intent}</p>
              <ExampleLabel>{translate({id: 'product.publish.exampleNote', message: 'Example note'})}</ExampleLabel>
              <p className={audience.mono ? `${styles.note} ${styles.noteMono}` : styles.note}>{audience.note}</p>
            </article>
          ))}
        </Grid>
        <StageScreenshot
          file="p-4-operator"
          width={1600}
          height={1240}
          alt={translate({
            id: 'product.publish.screenshot.alt',
            message:
              'The published operator note of release v1.1.0, with a tab for every audience and language and the note as Markdown beside it',
          })}
          caption={translate({
            id: 'product.publish.screenshot.caption',
            message: 'The real thing: the operator note of a published release of the sample project.',
          })}
        />
        <p className={styles.lockLine}>
          <Icon name="lock" size={16} />
          <Translate id="product.publish.frozen">
            Published notes are frozen: nothing edits them afterwards, not even direct SQL.
          </Translate>
        </p>
      </Container>
    </Section>
  );
}

function OptionalProviders() {
  const providers = [
    {
      icon: 'cpu',
      title: translate({id: 'product.optional.ai.title', message: 'AI classification and summaries'}),
      text: translate({
        id: 'product.optional.ai.text',
        message:
          'OpenAI, Anthropic or DeepSeek suggest a category, a neutral summary and a narrative for each audience. The rules always win, and words a person wrote are never replaced.',
      }),
    },
    {
      icon: 'languages',
      title: translate({id: 'product.optional.translation.title', message: 'Translation with DeepL'}),
      text: translate({
        id: 'product.optional.translation.text',
        message: 'Translate each note into every language your Organization writes in. Publishing waits until every translation is ready.',
      }),
    },
    {
      icon: 'mail',
      title: translate({id: 'product.optional.email.title', message: 'Email'}),
      text: translate({
        id: 'product.optional.email.text',
        message: 'Send a published note to up to 100 addresses through your own SMTP server.',
      }),
    },
    {
      icon: 'send',
      title: translate({id: 'product.optional.delivery.title', message: 'Delivery'}),
      text: translate({
        id: 'product.optional.delivery.text',
        message: 'Post published notes to GitHub Releases, Slack, Microsoft Teams, Notion, Confluence or a Zendesk help centre.',
      }),
    },
  ];
  return (
    <Section labelledBy="optional-title" className={styles.deepDive}>
      <Container className={styles.splitTop}>
        <div className={styles.optionalIntro}>
          <SectionHeader
            id="optional-title"
            eyebrow={translate({id: 'product.optional.eyebrow', message: 'Optional providers'})}
            title={translate({id: 'product.optional.title', message: 'Extras stay off until you turn them on.'})}
            lead={translate({
              id: 'product.optional.lead',
              message:
                'ReleaseFlow works without any third-party service. AI, translation, email and delivery run only when you configure them, and the secrets your rules store are encrypted in your own database.',
            })}
          />
          <TextLink to="/self-host#topology">
            <Translate id="product.optional.link">How outbound traffic works</Translate>
          </TextLink>
        </div>
        <Grid min={260} className={styles.optionalGrid}>
          {providers.map((provider) => (
            <div key={provider.icon} className={styles.provider}>
              <div className={styles.providerHead}>
                <Icon name={provider.icon} size={22} strokeWidth={1.8} />
                <Pill>
                  <Translate id="product.optional.pill">Optional</Translate>
                </Pill>
              </div>
              <Heading as="h3" className={styles.providerTitle}>
                {provider.title}
              </Heading>
              <p className={styles.providerText}>{provider.text}</p>
            </div>
          ))}
        </Grid>
      </Container>
    </Section>
  );
}

export default function Product() {
  return (
    <Layout
      title={translate({id: 'product.meta.title', message: 'Product'})}
      description={translate({
        id: 'product.meta.description',
        message: 'How ReleaseFlow collects, classifies, reviews and publishes release notes for every audience.',
      })}>
      <main>
        <HeroSection />
        <Walkthrough />
        <DeepDive
          id="collect-title"
          eyebrow={translate({id: 'product.collect.eyebrow', message: '01 · Collect'})}
          title={translate({id: 'product.collect.title', message: 'Four sources, one list of changes.'})}
          body={translate({
            id: 'product.collect.lead',
            message:
              'GitHub, GitLab, Linear and Jira each describe work their own way. ReleaseFlow records merged pull requests, merged merge requests and completed issues as one kind of change, so review never depends on where the work happened.',
          })}
          checks={[
            translate({
              id: 'product.collect.check.delivery',
              message: 'GitHub, GitLab and Linear deliver by webhook; Jira is polled on a schedule',
            }),
            translate({
              id: 'product.collect.check.history',
              message: 'Import the last 90 days of GitHub or GitLab history, without duplicates',
            }),
            translate({
              id: 'product.collect.check.secrets',
              message: 'Webhook secrets and access tokens are encrypted at rest',
            }),
          ]}
          link={
            <TextLink to="/docs/sources#connect-a-source">
              <Translate id="product.collect.link">Connect your first source</Translate>
            </TextLink>
          }
          figure={<CollectFigure />}
          screenshot={
            <StageScreenshot
              file="p-1-sources"
              localized
              width={1608}
              height={1155}
              alt={translate({
                id: 'product.collect.screenshot.alt',
                message:
                  'Four source cards: a GitHub repository, a GitLab project, and a Linear team with their last delivery, and a Jira project with its last and next read',
              })}
              caption={translate({
                id: 'product.collect.screenshot.caption',
                message: 'The four sources of one Project of the sample workspace, as the Projects page shows them.',
              })}
            />
          }
        />
        <DeepDive
          band="alt"
          reverse
          id="classify-title"
          eyebrow={translate({id: 'product.classify.eyebrow', message: '02 · Classify'})}
          title={translate({id: 'product.classify.title', message: 'Rules you can read. Results you can check.'})}
          body={translate({
            id: 'product.classify.lead',
            message:
              'Classification starts with fixed rules: a Conventional Commit type in the title, a familiar label, or a change that only touches documentation. Each change keeps the rules that matched, so a reviewer can see why it landed where it did.',
          })}
          checks={[
            translate({
              id: 'product.classify.check.review',
              message: 'Breaking, Unknown and sensitive-file changes always need a person',
            }),
            translate({
              id: 'product.classify.check.ai',
              message: 'Optional AI fills in what the rules leave open; the rules always win',
            }),
            translate({
              id: 'product.classify.check.correct',
              message: 'A reviewer can correct the category or the breaking flag of any change',
            }),
          ]}
          link={
            <TextLink to="/docs/changes#classification-rules">
              <Translate id="product.classify.link">Read the classification rules</Translate>
            </TextLink>
          }
          figure={<ClassifyFigure />}
          screenshot={
            <StageScreenshot
              file="p-2-classify"
              localized
              width={1600}
              height={1000}
              alt={translate({
                id: 'product.classify.screenshot.alt',
                message:
                  'A pull request classified as Fix because of its title type, waiting for review because it changed six sensitive files, with its AI summary below',
              })}
              caption={translate({
                id: 'product.classify.screenshot.caption',
                message: 'A real change in the Change Inbox: why it is Fix, and which files sent it to review.',
              })}
            />
          }
        />
        <DeepDive
          narrowCopy
          id="review-title"
          eyebrow={translate({id: 'product.review.eyebrow', message: '03 · Review'})}
          title={translate({id: 'product.review.title', message: 'A person signs off on every release.'})}
          body={translate({
            id: 'product.review.lead',
            message:
              'Release review puts each decision in front of your team: approve a change as shown, correct its classification or summary, or reject it from the release. Then approve the release, check each audience’s note, and publish it.',
          })}
          checks={[
            translate({id: 'product.review.check.evidence', message: 'Review triggers and evidence beside every change'}),
            translate({
              id: 'product.review.check.decision',
              message: 'A release is approved only when every change has a decision',
            }),
          ]}
          link={
            <TextLink to="/docs/release-notes#releases">
              <Translate id="product.review.link">How a release moves to publication</Translate>
            </TextLink>
          }
          figure={
            <div className={styles.screenshotSlot}>
              <ScreenshotFrame
                src={useBaseUrl('/media/product/p-3-review.webp')}
                width={900}
                height={728}
                alt={translate({
                  id: 'product.review.screenshot',
                  message:
                    'A release review: one change approved as shown, one reclassified as Security with a note, one rejected',
                })}
                caption={translate({
                  id: 'product.review.caption',
                  message: 'Release review: decide on every change, then approve the release.',
                })}
              />
            </div>
          }
        />
        <Publish />
        <OptionalProviders />
        <CtaBand
          align="center"
          id="product-cta-title"
          title={translate({id: 'product.cta.title', message: 'See it on your own changes.'})}
          actions={
            <>
              <ButtonLink to="/self-host" arrow>
                <Translate id="product.hero.selfHost">Self-host ReleaseFlow</Translate>
              </ButtonLink>
              <ButtonLink to={repository} variant="ghost">
                <Translate id="product.cta.source">View the source</Translate>
              </ButtonLink>
            </>
          }>
          <Translate id="product.cta.text">
            Start ReleaseFlow with Docker Compose, connect one repository, and review your next release before anyone
            reads it.
          </Translate>
        </CtaBand>
      </main>
    </Layout>
  );
}
