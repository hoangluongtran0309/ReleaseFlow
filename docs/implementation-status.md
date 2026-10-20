# Implementation Status

## Implemented

Slices are grouped in the order their migrations were added. A migration is named once,
where it first appears; ADRs are linked from the slice they record.

### Foundation (V1–V3)

- One Java 21, Spring Boot 4.1.1 Maven application.
- JSON status endpoint and server-rendered home page.
- Organization plus administrator AppUser registration through REST and
  Thymeleaf.
- PostgreSQL persistence and Flyway `V1`, with Hibernate schema validation.
- Flyway `V2` for tenant-scoped Projects and GitHub integrations.
- Canonical unique emails, BCrypt password hashes, and atomic registration.
- Session authentication, tenant-bearing principal, form login, POST logout,
  and CSRF protection shared by REST and UI.
- Authenticated session identity and a public CSRF-token endpoint.
- Problem Details responses with stable codes for implemented API failures.
- Project creation/listing through shared REST and Thymeleaf application
  behavior.
- One canonical GitHub repository per Project, unique inside an Organization (V2;
  since V18 a Project may have several sources).
- Per-integration webhook IDs, one-time 256-bit secret reveal, and AES-256-GCM
  encrypted secret storage with tenant-bound authenticated data.
- PostgreSQL Testcontainers coverage including migration, constraints,
  cross-tenant lookup, negative security paths, REST, and UI.
- Workspace UI built with Tailwind CSS 4, DaisyUI 5, the Thymeleaf Layout
  Dialect, and Alpine.js, with a light/dark theme and a Maven-managed Node.js
  toolchain; the ReleaseFlow wordmark follows the resolved theme while the compact
  mark remains available for favicons and public changelog headers.
- Signed GitHub webhook intake: a sessionless endpoint per integration,
  constant-time HMAC-SHA256 verification before payload parsing, tenant
  identity from the verified integration, and repository identity checks.
- Flyway `V3` for normalized merged-pull-request changes, idempotent per
  Project and pull request number, and the last accepted delivery time.
- `ping` acknowledgement, ignored unrelated events, and a Projects page setup
  step showing the last verified delivery.

### Classification and review (V4–V6, V11)

- Deterministic classification of each recorded change from its title type,
  labels, and `BREAKING CHANGE` footer, with explainable reasons.
- Mandatory review for breaking and Unknown changes, also enforced by a
  database constraint; Flyway `V4` marks earlier changes Unknown.
- A per-Project Change Inbox through Thymeleaf and REST with category and
  review-status filters.
- Optional automatic AI classification ([ADR-0009](adr/0009-automatic-ai-classification.md)) with OpenAI, Anthropic, or
  DeepSeek behind one `AiChangeClassifier` interface: one request per change in
  the worker, a shared JSON contract with a neutral summary, rules and review
  triggers that always win, explicit FAILED states with a fallback trigger, no
  automatic retry, stale-claim completion without a second call, a manual retry
  for failures and older Unknown changes, and Flyway `V5` and `V11`.
- An Organization output language (BCP 47) chosen at registration, changed by
  administrators through Thymeleaf and REST, and used for AI summaries.
- Human review of any change through Thymeleaf and REST: the reviewer confirms
  or corrects the category and breaking flag, and the reviewer and time are
  recorded. Flyway `V6` adds the review columns, a same-tenant reviewer foreign
  key, and constraints so breaking, Unknown, and AI-suggested changes leave
  review only through a recorded review.

### Releases and publication (V7–V8)

- Release management through Thymeleaf and REST: several releases per
  Project, version and summary, an optional planned release time, processed
  changes added by hand or all at once, removal, discard until publication,
  and a grouped release note preview. Flyway `V7` enforces one release per
  change and same-Project, same-tenant membership.
- Immutable Release Note publication of an approved release: at first a snapshot of
  sections and Markdown (kept since V13 as the legacy note of releases published
  before audiences, which freeze their audience notes instead), the publisher and time, a read-only page with
  copyable Markdown, unique versions per Project, and Flyway `V8` triggers that
  reject any change to published releases, their changes, and their notes.

### Roles, supply chain, and processing (V9–V10)

- Administrator and member roles with single-use, hashed, expiring member
  invitations through Thymeleaf and REST, a Members page, and Flyway `V9`.
- A two-stage, digest-pinned, non-root container image whose health check calls
  the private management port, and a Docker Compose demo stack with PostgreSQL,
  Prometheus, and Grafana that requires its secrets to be supplied.
- GitHub Actions gates: Conventional PR titles, actionlint, npm audit, Maven
  verification, CodeQL, dependency review, a full-history Gitleaks scan, and
  a Trivy image scan with a Compose smoke test; weekly Dependabot updates.
- Optional, write-only GitHub access tokens set by administrators through
  Thymeleaf and REST, checked against the repository's pull requests before
  they are stored encrypted, with Flyway `V10`.
- A durable change processing queue: webhook intake records a `PROCESSING`
  change and a job; a scheduled worker claims jobs with `SKIP LOCKED`, lists
  changed files outside any transaction, retries transient provider failures,
  and recovers stale claims.
- Typed review triggers for sensitive paths and unavailable file lists, a
  configurable sensitive-path baseline, a documentation-only rule, and Flyway
  `V10` constraints that keep processing changes unsettled and triggered
  changes in review until a person reviews them.

### Release review lifecycle (V12)

- A release review lifecycle ([ADR-0010](adr/0010-release-review-lifecycle.md)): request review, `APPROVE` or `EDIT`
  decisions on each change that also review the change, rejection that removes
  a change, approval with the approver recorded, return to draft, status
  filters, a progress stepper, and a release-assignment endpoint. Flyway `V12`
  adds the statuses, schedule and approval columns, `release_change_reviews`,
  and triggers that fix changes and decisions after approval.
- Excluding a change from releases ([ADR-0033](adr/0033-change-exclusion.md)), with
  Flyway `V29`: an administrator records why a change will never ship, no release offers
  it until it is included again, and a trigger keeps it out of `release_changes`.

### Audiences, catalog, signals, and languages (V13–V17)

- Audiences and a release note per audience ([ADR-0011](adr/0011-audience-release-notes.md)), with Flyway `V13`:
  - administrator-managed audiences with fixed codes, communication intents,
    and validated Mustache templates, through Thymeleaf and REST;
  - three presets seeded at registration and by `V13`, in English or
    Vietnamese, which can be reset;
  - one to twenty audiences per Organization, and audiences that notes use
    cannot be deleted;
  - a narrative per audience in the single AI request, with a response schema
    built for each request;
  - one note per audience written at approval as a digest with English or
    Vietnamese labels;
  - note editing that makes a note manual, and summary and narrative editing
    that records the writer and renders automatic notes again;
  - live previews, Copy and Download, and server-side CommonMark rendering;
  - triggers that allow note writes only while approved and freeze notes on
    publication;
  - a read-only legacy note for releases published before `V13`.
- A category catalog per Organization ([ADR-0012](adr/0012-category-catalog.md)), with Flyway `V14`:
  - administrator-managed categories with fixed codes and six groups, which
    can be archived and restored;
  - the former fixed values seeded at registration and by the migration;
  - rules that resolve a group's preferred category;
  - AI that chooses among active codes;
  - AI proposals of new categories held for an administrator's decision in the
    Categories page, the Change Inbox, and REST;
  - category snapshots on changes;
  - release notes sectioned by group.
- Context sufficiency and possible duplicates ([ADR-0013](adr/0013-context-sufficiency-and-duplicates.md)), with Flyway `V15`:
  - an AI context score, lowered by fixed caps, with a review trigger below a
    configurable threshold;
  - an Inbox filter for insufficient context;
  - trigram and changed-file similarity against the Project's recent changes,
    with a trigger on the newer change;
  - evidence on both cards;
  - one-time decisions by any member through Thymeleaf and REST.
- Sensitive paths per Project ([ADR-0014](adr/0014-project-sensitive-paths.md)), with Flyway `V16`: administrators add
  glob patterns to the deployment's baseline through Thymeleaf and REST,
  members read them, and the worker and AI retries check changes against the
  baseline plus the Project's additions.
- Release notes in several languages ([ADR-0015](adr/0015-multilingual-release-notes.md)), with Flyway `V17`:
  - one to five release note languages per Organization, and one note per
    audience and language at approval;
  - per-language audience templates, made when a language or an audience is
    added;
  - DeepL translation of change summaries and narratives, and of the release's
    own summary (Flyway `V28`), through a durable queue after commit, with a
    per-Organization cache, bounded retries, and a manual retry;
  - notes that are ready, translating, or not translated, a polling release
    page, and publication held until every note is ready, in the service and
    in the database.

### Sources (V18–V21)

- Integration sources and history import ([ADR-0016](adr/0016-integration-sources-and-history-import.md)), with Flyway `V18`:
  - `github_integrations` renamed in place to `integration_sources`, keeping
    webhook paths and ciphertexts, with several GitHub repositories per
    Project and changes identified by source;
  - a project-scoped sources API replacing `/github-integration`;
  - a durable, resumable import of the last 90 days of merged pull requests,
    paged with a cursor, limited to 500 new changes per run, honouring
    `Retry-After`, sharing the webhook intake and processing, and shown in the
    Change Inbox.
- GitLab sources ([ADR-0017](adr/0017-gitlab-sources.md)), with Flyway `V19`:
  - a `source` package holding the provider-neutral vocabulary, and two ports —
    `SourceEnricher` (named `ChangedFileCollector` until ADR-0018) and
    `SourceHistoryReader` — with one implementation per source type; a registry fails
    at startup when a type has no enricher, or when a type that supports history
    import has no history reader;
  - GitLab projects named by their path, including subgroups, on an instance
    from the deployment's allowlist, refused unless it is a plain HTTP or HTTPS
    origin the allowlist names;
  - `POST /webhooks/gitlab/{webhookId}`, proven by Standard Webhooks headers
    within a five-minute skew or by `X-Gitlab-Token`, both in constant time, and
    a 1 MiB limit on any provider's delivery;
  - merged merge requests normalized into the same change, with the merge commit
    falling back to the squash and last commits and the merge time to the last
    update;
  - changed files from the merge request's diffs, ten pages at most, where a
    collapsed, too large, or truncated diff makes the whole list unavailable;
  - a 90-day history import that asks for the merge requests updated after the
    window's start, least recently updated first;
  - an access token confirmed against `GET /api/v4/projects/{key}` before it is
    stored, with GitLab's own error codes.
- Linear sources ([ADR-0018](adr/0018-linear-sources.md)), with Flyway `V20`:
  - a Linear team connected with the signing secret Linear minted and an API key,
    both write-only, confirmed with GraphQL `team(id)` before anything is stored,
    which is also how the workspace every delivery is checked against is learned;
  - `POST /webhooks/linear/{webhookId}`, proven by a delivery header, a stamp
    within 60 seconds, the workspace, and a hex HMAC of the raw body;
  - only an issue that really moves into a completed state recorded, by its UUID,
    with the number a person sees kept for release notes;
  - `ChangedFileStatus.NOT_SUPPORTED` and a `SENSITIVE_KEYWORD` trigger from the
    seven-word scan of a change's own title and description, so a source without
    files needs review only on a match;
  - the issue read back over GraphQL, so a change carries its current wording;
  - `changes.source_type`, tied to the source by a composite foreign key, which
    keeps a merge commit and a target branch on a code host's change and off an
    issue tracker's;
  - history import refused for a type that has none, in REST and in the UI.
- Jira sources and linked context ([ADR-0019](adr/0019-jira-sources-and-linked-context.md)), with Flyway `V21`, which reaches
  parity milestone M2 — every planned source exists:
  - a Jira Cloud project connected with its `atlassian.net` site, key, account
    email, and API token, confirmed with Jira before anything is stored, and
    with no webhook or secret;
  - polling as `JIRA_POLL` source sync jobs that nobody requests, scheduled from
    each source's `next_poll_at`, reading issues that moved into Done through
    the JQL search with a ten-minute overlap, and keeping the cursor on failure;
  - `SourceSyncWorker` and an opaque `SyncCursor`, so imports and polls share
    one claim, retry, and recovery path;
  - linked context for GitHub and GitLab changes: up to 250 commit messages read
    only to find keys, up to ten Jira issues fetched, six recorded statuses, and
    the `LINKED_CONTEXT_UNAVAILABLE` trigger;
  - linked issues in the AI prompt, the Change Inbox, and audience templates;
  - a Jira card on the Projects page and a poll row in the Change Inbox.

### Automation and the public changelog (V22–V26)

- Automation rules and runs ([ADR-0020](adr/0020-automation-rules-and-runs.md)), with Flyway `V22`:
  - a rule that fires when a release is published or when an administrator runs
    it, scoped to one project or to every project, holding an ordered list of
    actions, each naming the audience and language whose note it delivers;
  - enabling as the promise the deliveries can be made: project and audience
    ownership, a configured release language, one GitHub source for a GitHub
    Release action, a deployment that can carry the action out, and a usable
    configuration and secret;
  - publication that writes one outbox row and nothing else, so no rule can undo
    a release, and a worker that turns it into runs after the commit — once per
    rule and release, and once per manual request ID;
  - durable runs with `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `UNKNOWN`, and
    `CANCELLED`: the first action that does not succeed stops the rest, an action
    whose worker stopped becomes unknown after five minutes and is never repeated
    without a person confirming the duplicate, a retry resets only that action,
    and cancelling lets the action already running record its result;
  - GitHub Release deliveries that borrow the project's source token and mark
    their own release, Slack deliveries to an allowlisted incoming webhook, and
    email deliveries through the deployment's SMTP server;
  - action secrets encrypted and bound to their action, never read back;
  - an administrator-only `/api/automation/**` and `/automation`, with the run
    history as the first paged list in ReleaseFlow.
- Scheduled, reminding, and called-from-outside automation rules ([ADR-0021](adr/0021-scheduled-and-signed-automation-triggers.md)),
  with Flyway `V23`:
  - a six-field cron schedule in an IANA time zone that repeats one published
    release, taking that release's own project, with the next firing always
    booked from the present so downtime owes one catch-up run and not a queue of
    them, and a preview of the next firing before a rule keeps the schedule;
  - a reminder 0 to 365 days before an approved release's planned time, whose
    identity is that moment, so rescheduling a release earns exactly one more
    reminder and scanning the same state again earns none;
  - both restricted to the actions that only tell people something — Slack, email,
    and, since ADR-0025, Microsoft Teams — because nobody is watching when a rule
    ReleaseFlow set off goes off;
  - a trigger worker that only writes runs, claiming a due schedule with
    `FOR UPDATE SKIP LOCKED`, and a rule that is off or archived that can never
    be claimed;
  - a signed `POST /webhooks/automation/{webhookId}`, answering `202` with the
    run and a signed path to read it back: HMAC-SHA256 over the timestamp, the
    delivery, the method, the path, and the body's digest, compared in constant
    time within five minutes of skew, with the Organization taken from the rule
    and a body of at most 64 KiB;
  - a delivery identifier that makes a retried call the same run;
  - a webhook secret bound to its rule and path, shown once when the rule is
    written and once more when it is rotated, which keeps the path and stops the
    old secret at once.
- A public changelog ([ADR-0022](adr/0022-public-changelog.md)), with Flyway `V24`:
  - an Organization address of one DNS label, unique across the deployment, made from
    its name when it registers and moved only by an administrator, who is told that
    links already shared stop working;
  - a `PUBLIC_CHANGELOG` automation action that copies one audience's note, in one
    language, into an immutable public entry with the Organization, project, version,
    and audience as they read then;
  - a repeat that is free and a rewrite that is refused: one entry per release,
    audience, and language, and per action run, so retrying an action succeeds while
    different words for the same publication fail as `public_changelog_conflict`;
  - the only action that reaches no provider, and so the only one that opens a
    transaction of its own; it is refused on scheduled and reminder triggers, which take
    only the actions that tell people something (Slack, email, and, since ADR-0025,
    Microsoft Teams);
  - anonymous pages at `/changelog/{slug}`, a permanent entry with an ETag and a day's
    caching, and an RSS 2.0 feed of the 50 newest entries, all on their own security
    chain that creates no session and permits nothing but GET;
  - notes rendered by the same `MarkdownHtml` the application's own pages use, so raw
    HTML is escaped, link targets sanitized, and images become plain links;
  - absolute links built from `RELEASEFLOW_PUBLIC_BASE_URL`, never from a request, and
    optional `{slug}.{domain}` routing for deployments that provision wildcard DNS.
- Notion and Confluence automation actions ([ADR-0024](adr/0024-notion-and-confluence-actions.md)), with Flyway `V25`:
  - a `NOTION` action that files the note as a child page under a page an
    administrator names, titled `Release <version>`, with an integration token as its
    secret and the API version pinned by `RELEASEFLOW_NOTION_VERSION`; the page id is
    accepted as Notion writes one, with or without dashes;
  - a `CONFLUENCE` action that creates a page in a Cloud space from the note rendered
    by `MarkdownHtml`, authenticated by the account's email and an API token, with an
    `<!-- releaseflow-action:{id} -->` comment recording which run wrote it;
  - a Confluence site that must be exactly one label beneath `atlassian.net` over
    HTTPS, with no credentials, port, path, query, or fragment, checked when the action
    is written and again before every delivery, while Notion's own address stays
    deployment configuration no request can choose;
  - payloads bounded before any network call, 450,000 bytes for Notion and 2,000,000
    for Confluence;
  - a 4xx recorded as `FAILED`, a 5xx or a lost connection as `UNKNOWN`, and a redirect,
    which neither client follows, as `FAILED`; neither is ever retried on its own, and a
    repeat still needs a person to confirm the duplicate;
  - both refused on scheduled and reminder triggers, like every action other than Slack,
    email, and Microsoft Teams.
- Microsoft Teams and Zendesk automation actions ([ADR-0025](adr/0025-teams-and-zendesk-actions.md)), with Flyway `V26`:
  - a `MICROSOFT_TEAMS` action whose secret is the Workflows callback URL itself,
    accepted only as HTTPS on one label beneath `environment.api.powerplatform.com`,
    with the Workflows trigger path and its own `sig` signature, checked when written and
    again before every delivery, and never followed through a redirect because the URL is
    a credential;
  - a plain-text message of `Release <version>` and the note, with 28 KiB measured on the
    written request rather than on the message;
  - a `ZENDESK` action that takes a short-lived client-credentials token and then
    publishes a Help Center article from the same `MarkdownHtml` output, in the action's
    language, with `draft=false`, `notify_subscribers=false`, and a user segment only when
    one is configured;
  - a subdomain that is one DNS label rather than a URL, so the only help centre an action
    can reach is its own, and section and segment ids that are positive numbers;
  - every failure of the token call recorded as `FAILED`, including a 5xx, because nothing
    is published until the second call, while the article call keeps the usual mapping and
    a 2xx naming no article is `UNKNOWN`;
  - Microsoft Teams allowed on scheduled and reminder triggers, which now admit every
    action that only tells people something, while Zendesk is refused with the rest.

### Operations, interface, and distribution (V27 and no-migration slices)

- Deployment observability ([ADR-0026](adr/0026-deployment-observability.md)), with no migration:
  - Actuator on its own port, 8081 on loopback by default, exposing only `health`
    without component details and `prometheus`, behind a security chain matched with
    `EndpointRequest.toAnyEndpoint()` that denies everything else and creates no session;
  - four metrics whose labels all come from finite sets —
    `releaseflow.classification.completed` by `needs_human_review`,
    `releaseflow.classification.collect_to_complete` as a timer with service-level
    objectives from one second to ten minutes,
    `releaseflow.classification.provider.requests` by `provider` and `outcome`, and
    `releaseflow.automation.action.executions` by `trigger` and `outcome`;
  - no Organization, Project, release, rule, run, action, model, external reference, or
    error text anywhere in a label, enforced by building every series from enums at
    startup and asserted by both unit tests;
  - every series registered at zero when the application starts, so a panel reads zero
    rather than "no data" and CI can prove all four families are scraped;
  - a count that follows the write: a classification after its transaction returns, a
    delivery after its outcome is durable and only while this worker held the claim, and
    an abandoned action as `unknown` when recovery marks it so;
  - one decorator counting every provider request exactly once, for all three providers
    and for a person retrying by hand;
  - a Compose stack with Prometheus and Grafana on a network marked `internal: true`, the
    management port never published, seven days of history, and a *ReleaseFlow
    Operations* dashboard of seven panels;
  - a container workflow that proves the management port is private and that all four
    metric families reach Prometheus.
- UI localization ([ADR-0027](adr/0027-ui-localization.md), migration `V27`):
  - an English or Vietnamese interface, chosen per person, from one bundle per language
    holding every page, form message, and error explanation;
  - `UiLanguages`, which reads `RELEASEFLOW_UI_LANGUAGES` once at startup, narrows each
    entry to a primary subtag, and refuses a list that names no usable language;
  - `UiLocaleResolver`, which picks a request's language from a `?lang=` on a GET, then
    the `releaseflow_lang` cookie, then `app_users.ui_locale`, then `Accept-Language`,
    then the first configured language, narrowing each step and reading the account from
    the session's principal so resolving a locale costs no query, with
    `UiLanguageParameterInterceptor` keeping a supported `?lang=` in the cookie, never on
    the account, everywhere except the publicly cached changelog;
  - `GET|PUT /api/me/ui-locale` and a picker in the user menu; a change saves the account
    column and writes the cookie, which outranks it, and null means the browser decides;
  - `LocalizedException`, which every user-facing failure extends, carrying a bundle key
    and the values its sentence fills in, so a new failure that forgets its key does not
    compile;
  - localized `title` and `detail` on every Problem Details response, including the 401
    and 403 the security filter chain writes, with every error `code` unchanged;
  - localized Bean Validation, through a validator wired to a message source over the same
    bundle, and translated `typeMismatch` messages for a value a field cannot hold;
  - a public changelog whose chrome follows the reader while each published note keeps the
    language it was published in, still without a session or an account read;
  - `getLabelKey()` on the seven enums a page names and `messageKey()` on a review trigger,
    with the English label left where recorded evidence keeps it;
  - `app.js` with no wording left in it: components that show a label read it
    from a `data-` attribute the template rendered;
  - tests that hold the bundles to their promises — the two languages carry the same keys
    and fill in the same arguments, every key a template, a constraint, or a controller asks
    for exists, every pattern with a placeholder can be filled in, and no controller hands a
    page a sentence of its own — one that renders every page in Vietnamese and fails on
    any key that reaches the HTML, and template checks that every form taking input has
    `novalidate` and every bound field a place for its error.
- Parity UI and browser tests ([ADR-0028](adr/0028-parity-ui-and-browser-tests.md), no
  migration):
  - a workspace overview that reads the workspace — four figures, each linking to the page
    that can act on them, and the one next step that is unfinished, chosen in a fixed
    order by `NextStep.of(...)` and covered branch by branch by a unit test;
  - `OverviewService`, which owns no data and composes aggregate counts from the change,
    release and project capabilities;
  - Project switchers in the desktop header and mobile navigation, with the last Project
    remembered in an `HttpOnly` cookie that carries no authority: a resource path wins
    over a query, a query wins over the cookie, and every choice is checked against the
    current Organization;
  - error pages for a mistyped address, a page a role does not reach, a form that is out
    of date, and a fault, and a return to sign-in for a form sent after its session ended,
    with
    `UnknownAddressAdvice` answering a browser with a page and everybody else with the
    Problem Details they had, and `PageExceptionAdvice` answering a page that names
    something the Organization does not have with the same 404 page and the failure's
    own words;
  - a toast dismissed by hand rather than on a timer, as WCAG 2.2.1 asks;
  - one shared alert and toast in four severities (success, info, warning, error), each
    with its own colour and icon, that refuse any other severity;
  - `UiNotice`, a severity with a bundle key and its values, which a controller flashes
    across a redirect or puts on the page instead of a sentence, and binding errors that
    carry a failure's own key and values;
  - server-only form validation: `novalidate` on every form that takes input, a drawn
    `*` beside required fields, each error under its field, an error summary above a
    form of several fields, status 400 for a form sent back, automation failures placed
    on the rule field or action row they are about, and a planned release time typed
    only in part reported rather than taken as clearing the schedule;
  - one voice per language, written down with its glossary in `CONTRIBUTING.md`:
    whole English sentences that quote what a person typed, and Vietnamese that asks
    with "Vui lòng …" and keeps product terms in English;
  - tables that become one card per row below 640 px with each cell keeping its heading,
    a keyboard-contained mobile drawer with focus restoration, associated and focused
    field errors, and a 44 px touch-target floor for controls and disclosures;
  - explicit confirmations for automation operations that cancel work or invalidate a
    secret, a genuinely disabled audience-limit action, clipboard fallback announcements,
    and a bounded translation status that never leaves a permanent spinner;
  - Playwright with `@axe-core/playwright` against the demo Compose stack — the release
    pipeline, invitations, and every page in English and Vietnamese, at 360 px light and
    1440 px dark, failing on horizontal overflow, short disclosure targets, or any WCAG
    2 A/AA, 2.1 A/AA or 2.2 AA violation — as a fifth CI job;
  - the accessibility defects that gate found on its first run, all older than it: badge
    contrast in both themes, an `aria-label` on a `<label>`, `role="tablist"` around
    panels a tablist may not contain, and two colours that failed against their surfaces.
- Licence and project metadata ([ADR-0029](adr/0029-one-module-and-the-apache-licence.md), no migration): the Apache
  License 2.0 for the whole repository, copied into the JAR's `META-INF` beside a
  `NOTICE`; POM metadata naming the licence, the author, the project and its source; a
  security policy for reporting a weakness privately; and decision **D1 settled** — one
  Maven module, with the open-core/enterprise split abandoned rather than deferred.
- Release automation ([ADR-0030](adr/0030-release-automation.md), no migration): the
  version comes only from the tag — the POM says `${revision}` (`0.0.0-SNAPSHOT` for any
  other build) and the workflow builds with `-Drevision`. `scripts/release.sh start` and
  `finish` cut a release in two commands: a release branch whose `CHANGELOG.md` entry is
  dated, a pull request into `main`, the tag, and the back-merge; `rc` publishes a release
  candidate and `--hotfix` starts from `main`. A `Release` workflow triggered by a
  `v<version>` tag refuses a tag that is not an ancestor of `main` or has no dated
  `CHANGELOG.md` entry; then runs the whole suite again, checks the JAR's manifest carries
  the version, builds and scans the image, and publishes the JAR, a CycloneDX SBOM of the
  image, `SHA256SUMS`, the licence and the notice, the image on GHCR tagged
  `X.Y.Z`/`X.Y`/`X`/`latest`, a provenance attestation for the image and the JAR, and a
  GitHub release written from the `CHANGELOG.md` entry. CI runs a whole release of the
  scripts against a local remote.
- Quickstart bundle ([ADR-0031](adr/0031-quickstart-bundle.md), no migration): every
  release attaches `compose.yaml`, the released image pinned to
  its digest with PostgreSQL on loopback, and `quickstart.sh`, which checks for Docker,
  writes `.env` with new secrets on the first run and never overwrites it, ignores
  `RELEASEFLOW_*` and `COMPOSE_*` from the shell, refuses a database whose `.env` is gone,
  and waits for the application to be healthy. Both are in `SHA256SUMS` and the provenance
  attestation. CI lints the scripts with a pinned ShellCheck, runs the quickstart under
  bash 3.2, runs the bundle against the image each pull request builds, and checks the
  quickstart and the demo stack forward the same variables.
- Documentation site ([ADR-0032](adr/0032-documentation-site.md), no migration): a
  Docusaurus site in `website/` that reads the guides in `docs/` in place, with a landing
  page, an Introduction and a Getting started guide, browser-side search, both colour
  themes, and English and Vietnamese: the guides, Getting started, Configuration,
  Operations, and the REST API are translated, keeping each English anchor, while the
  architecture, the implementation status, and the decision records fall back to English.
  Links, anchors, and Markdown file links fail the build; a link to a file
  beside `docs/` points at it on `main`. A `Docs` workflow builds it on every pull request
  and publishes it to GitHub Pages from `main`, and `scripts/ci/audit-website.sh` audits its
  dependencies at high severity, allowing only listed advisories that no release fixes.

## In progress

- Nothing.

## Planned

Nothing is planned beyond the slices above. New work starts from the
deliberately deferred list below, one reviewed slice at a time.

## Deliberately deferred

- Role changes and member removal.
- Source providers other than GitHub, GitLab, Linear, and Jira Cloud (Jira
  Server or Data Center), Jira webhooks, and automatic AI retries.
- GitLab group-level webhooks, Linear projects and cycles, linked context from
  Linear or from more than one Jira source per Project, Jira issue types and
  statuses as classification signals, and queues or retries for work other than
  enrichment, imports, polls, translation, and the automation outbox and actions.
- Validating a repository with GitHub when it is connected, disconnecting or
  replacing a source, rotating a Linear signing secret without reconnecting, and
  secret or token rotation reminders.
- Interface languages other than English and Vietnamese, a language picker for
  anonymous pages (they follow `Accept-Language`), an RSS feed whose channel
  title and description follow the reader rather than staying English, localized
  dates and numbers beyond what the bundle's own patterns give, translation
  providers other than DeepL, and asynchronous note generation.
- Automation actions other than GitHub Releases, Slack, email, the public
  changelog, Notion, Confluence Cloud, Microsoft Teams, and Zendesk.
- Confluence Data Center, Confluence OAuth, and reading a Notion, Confluence, or
  Zendesk page back to recognise one a previous run wrote.
- Office 365 connectors, sovereign-cloud Teams endpoints, Graph bots, and
  Entra-authenticated Teams flows; Zendesk API tokens in place of OAuth.
- Client-rendered pages, JavaScript bundling, JavaScript unit tests (what is left
  in `app.js` only means anything in a page, which the browser suite drives),
  and publishing to any registry other than this repository's own on GHCR.
- A multi-architecture image (the published one is amd64 only, so Apple silicon runs the
  quickstart under emulation), a PowerShell quickstart, interactive quickstart setup, and
  quickstart TLS, backups, or upgrades that skip downloading the new bundle.
- Alerting, durable metric storage, a retention policy beyond the demo's seven
  days, and access control for the demo dashboards; per-Organization analytics,
  which need a product feature with an authorization story rather than a metric
  label.
- A release that collects changes from more than one Project.
- Change Inbox pagination and search.
- A dashboard beyond one Project at a time, and figures on it that are not counts.
- Review history, comments, reviewer roles, and bulk review.
- Item reordering.
- Unpublishing or correcting published release notes.
