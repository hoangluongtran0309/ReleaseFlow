# Configuration

ReleaseFlow reads its configuration from `RELEASEFLOW_*` environment variables, which
`src/main/resources/application.properties` maps onto Spring properties. The JAR, the
container image, and `./mvnw spring-boot:run` all read the same variables. Durations are
ISO-8601 (`PT10S`, `P7D`), and lists are comma-separated.

Only the first table is required. Every other variable has a default that works for a
single local deployment.

## Required

| Variable | What it is |
| --- | --- |
| `RELEASEFLOW_DB_URL` | JDBC URL of a dedicated PostgreSQL database, for example `jdbc:postgresql://localhost:5432/releaseflow` |
| `RELEASEFLOW_DB_USERNAME` | Database user |
| `RELEASEFLOW_DB_PASSWORD` | Database password |
| `RELEASEFLOW_CREDENTIAL_MASTER_KEY` | Base64 of exactly 32 random bytes (`openssl rand -base64 32`). It encrypts every stored webhook secret, access token, and action secret. Startup fails without it, and losing it makes every stored secret unreadable |

Never commit or share these values.

## Web and session

| Variable | Default | What it does |
| --- | --- | --- |
| `RELEASEFLOW_SESSION_COOKIE_SECURE` | `false` | Marks the session cookie `Secure`; set it to `true` whenever the application is served over HTTPS |
| `RELEASEFLOW_INVITATION_TTL` | `P7D` | How long an invitation link stays usable |
| `RELEASEFLOW_UI_LANGUAGES` | `en,vi` | Interface languages offered; the first is the fallback, and `?lang=` accepts only these. See [Interface language](workspace.md#interface-language) |
| `RELEASEFLOW_OUTPUT_LANGUAGES` | `en,vi` | Output languages suggested in forms. Any valid language tag is still accepted. See [Output language](workspace.md#output-language) |
| `RELEASEFLOW_PUBLIC_BASE_URL` | `http://localhost:8080` | The address this deployment is reached at. Every absolute public link is built from it, never from a request |
| `RELEASEFLOW_PUBLIC_CHANGELOG_BASE_DOMAIN` | (empty) | When set, also serves each Organization's changelog at `https://{slug}.{domain}/`. See [Public changelog](automation.md#public-changelog) |

## Sources

| Variable | Default | What it does |
| --- | --- | --- |
| `RELEASEFLOW_WEBHOOK_MAX_BODY_BYTES` | `1048576` | The largest delivery any provider may send |
| `RELEASEFLOW_GITHUB_API_BASE_URL` | `https://api.github.com` | Where GitHub is called |
| `RELEASEFLOW_GITHUB_TIMEOUT` | `PT10S` | Connect and read timeout for GitHub |
| `RELEASEFLOW_GITLAB_ALLOWED_HOSTS` | `gitlab.com` | The GitLab instances a source may name: `host`, `host:port` (HTTPS), or a full origin such as `http://gitlab.internal:8080` |
| `RELEASEFLOW_GITLAB_TIMEOUT` | `PT5S` | Connect and read timeout for GitLab |
| `RELEASEFLOW_GITLAB_WEBHOOK_CLOCK_SKEW` | `PT5M` | How stale a signed GitLab delivery may be |
| `RELEASEFLOW_LINEAR_API_BASE_URL` | `https://api.linear.app` | Where Linear's GraphQL API is called |
| `RELEASEFLOW_LINEAR_TIMEOUT` | `PT5S` | Connect and read timeout for Linear |
| `RELEASEFLOW_LINEAR_WEBHOOK_CLOCK_SKEW` | `PT60S` | How stale a signed Linear delivery may be |
| `RELEASEFLOW_JIRA_TIMEOUT` | `PT10S` | Connect and read timeout for Jira |
| `RELEASEFLOW_JIRA_POLL_INTERVAL` | `PT5M` | How often each Jira project is read |
| `RELEASEFLOW_JIRA_POLL_OVERLAP` | `PT10M` | How far each read reaches back before the previous one |
| `RELEASEFLOW_JIRA_DESCRIPTION_MAX_CHARACTERS` | `8000` | The longest Jira description kept as evidence |
| `RELEASEFLOW_JIRA_API_BASE_URL` | (empty) | Local testing only: send every Jira call here instead of the source's own site |

See [Sources](sources.md).

## Classification

| Variable | Default | What it does |
| --- | --- | --- |
| `RELEASEFLOW_SENSITIVE_PATHS` | see below | Replaces the sensitive-path baseline. An invalid pattern or an empty list stops startup |
| `RELEASEFLOW_CONTEXT_THRESHOLD` | `60` | Context score (0–100) below which a change needs review |
| `RELEASEFLOW_DUPLICATE_THRESHOLD` | `0.82` | Similarity (0–1) at which two changes are recorded as possible duplicates |

The default sensitive-path baseline is:

```text
**/security/**,**/auth/**,**/*Security*,**/*Auth*,**/*Credential*,**/*Password*,
**/db/migration/**,**/*.sql,**/pom.xml,**/package.json,**/package-lock.json,
.github/workflows/**,**/Dockerfile,**/application*.properties,**/*.env*
```

See [Changes and review](changes.md).

## AI

AI is optional. Choose one provider and give it a key and a model. There is no default
model; pick one that supports structured JSON output and a temperature. Every
classification request asks for temperature `0`, a fixed value that keeps one change's
answer as stable as the model allows, so a model that accepts only its default
temperature, such as an OpenAI reasoning model, fails every request (see
[ADR-0035](adr/0035-evidence-bound-notes-and-audience-relevance.md)).

| Variable | Default | What it does |
| --- | --- | --- |
| `RELEASEFLOW_AI_PROVIDER` | (empty: no AI) | `openai`, `anthropic`, or `deepseek` |
| `RELEASEFLOW_AI_TIMEOUT` | `PT60S` | Bound on each AI request |
| `RELEASEFLOW_OPENAI_API_KEY`, `RELEASEFLOW_OPENAI_MODEL` | (empty) | OpenAI key and model |
| `RELEASEFLOW_OPENAI_BASE_URL` | `https://api.openai.com/v1` | OpenAI API address |
| `RELEASEFLOW_ANTHROPIC_API_KEY`, `RELEASEFLOW_ANTHROPIC_MODEL` | (empty) | Anthropic key and model |
| `RELEASEFLOW_ANTHROPIC_BASE_URL` | `https://api.anthropic.com` | Anthropic API address |
| `RELEASEFLOW_DEEPSEEK_API_KEY`, `RELEASEFLOW_DEEPSEEK_MODEL` | (empty) | DeepSeek key and model |
| `RELEASEFLOW_DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | DeepSeek API address |

Startup fails when the selected provider lacks its key or model, when the provider name is
unknown, or when a provider's key or model is set while `RELEASEFLOW_AI_PROVIDER` is
empty. See [AI classification and summaries](changes.md#ai-classification-and-summaries).

## Translation

| Variable | Default | What it does |
| --- | --- | --- |
| `RELEASEFLOW_TRANSLATION_PROVIDER` | `disabled` | `deepl` turns on translation of release notes. A value other than `disabled` or `deepl` stops startup, and so does `disabled` while `RELEASEFLOW_DEEPL_API_KEY` is set |
| `RELEASEFLOW_TRANSLATION_TIMEOUT` | `PT30S` | Bound on each translation request |
| `RELEASEFLOW_DEEPL_API_KEY` | (empty) | DeepL key; a key ending in `:fx` uses the free API. Required with `deepl`, and a key set while the provider is not `deepl` stops startup, so turning translation off means clearing the key too. Each Organization with a [glossary](release-notes.md#glossary) uses one of the account's DeepL glossaries, and an API Free plan holds one |
| `RELEASEFLOW_DEEPL_BASE_URL` | (empty) | Overrides the host chosen from the key |

See [Release note languages](release-notes.md#release-note-languages).

## Automation

| Variable | Default | What it does |
| --- | --- | --- |
| `RELEASEFLOW_AUTOMATION_TIMEOUT` | `PT10S` | Bound on every delivery that leaves the application |
| `RELEASEFLOW_AUTOMATION_EMAIL_FROM` | (empty) | Sender address. Without it and an SMTP host, an email action cannot be enabled |
| `RELEASEFLOW_SMTP_HOST` | (empty) | SMTP server |
| `RELEASEFLOW_SMTP_PORT` | `587` | SMTP port |
| `RELEASEFLOW_SMTP_USERNAME`, `RELEASEFLOW_SMTP_PASSWORD` | (empty) | SMTP credentials |
| `RELEASEFLOW_SMTP_AUTH` | `false` | Use SMTP authentication |
| `RELEASEFLOW_SMTP_STARTTLS` | `false` | Use STARTTLS |
| `RELEASEFLOW_SLACK_ALLOWED_HOSTS` | `hooks.slack.com,hooks.slack-gov.com` | The only origins a Slack webhook URL may name |
| `RELEASEFLOW_NOTION_API_BASE_URL` | `https://api.notion.com` | Where Notion is called |
| `RELEASEFLOW_NOTION_VERSION` | `2026-03-11` | The Notion API version pages are written against. Change it only together with the body the application sends |
| `RELEASEFLOW_CONFLUENCE_API_BASE_URL` | (empty) | Local testing only: stand in for Confluence Cloud. The site is still checked, and recorded links still point at it |
| `RELEASEFLOW_TEAMS_API_BASE_URL` | (empty) | Local testing only: stand in for the Power Platform. The callback's path and signature are kept |
| `RELEASEFLOW_ZENDESK_API_BASE_URL` | (empty) | Local testing only: stand in for `{subdomain}.zendesk.com`. The subdomain is still checked |
| `RELEASEFLOW_AUTOMATION_TRIGGER_DELAY` | `PT15S` | How often ReleaseFlow looks for a schedule or a reminder that has come due |
| `RELEASEFLOW_AUTOMATION_TRIGGER_BATCH_SIZE` | `20` | How many firings one of those passes may book |
| `RELEASEFLOW_AUTOMATION_REMINDER_LOOKAHEAD` | `PT15S` | How far ahead a reminder with no notice at all looks |
| `RELEASEFLOW_AUTOMATION_WEBHOOK_CLOCK_SKEW` | `PT5M` | How far a signed call's timestamp may be from now |
| `RELEASEFLOW_AUTOMATION_WEBHOOK_MAX_BODY_BYTES` | `65536` | The largest signed call body |

A Notion, Confluence, Microsoft Teams, or Zendesk action needs nothing from the deployment
beyond these defaults: its token, callback URL, or client secret is entered for each action.
See [Automation](automation.md).

## Management port

| Variable | Default | What it does |
| --- | --- | --- |
| `RELEASEFLOW_MANAGEMENT_PORT` | `8081` | Port of the Actuator health and Prometheus endpoints |
| `RELEASEFLOW_MANAGEMENT_ADDRESS` | `127.0.0.1` | Address that port binds to; move it only onto a private network |
| `RELEASEFLOW_MANAGEMENT_HEALTH_MAIL` | `false` | Include the SMTP server in health, for a deployment that cannot work without mail |

See [Operations](operations.md#health-and-metrics).

## Docker Compose and the quickstart

These variables are read by `docker-compose.demo.yml` and the quickstart's `compose.yaml`,
not by the application.

| Variable | Default | Used by | What it does |
| --- | --- | --- | --- |
| `RELEASEFLOW_HTTP_PORT` | `8080` | both | Host port of the application, published on `127.0.0.1` |
| `RELEASEFLOW_DB_NAME` | `releaseflow` | demo | Database name |
| `RELEASEFLOW_GRAFANA_USER` | `admin` | demo | Grafana administrator |
| `RELEASEFLOW_GRAFANA_PASSWORD` | (required) | demo | Grafana administrator password |
| `RELEASEFLOW_GRAFANA_PORT` | `3000` | demo | Host port of Grafana |
| `RELEASEFLOW_PROMETHEUS_PORT` | `9090` | demo | Host port of Prometheus |
| `RELEASEFLOW_IMAGE` | (written by the release) | quickstart | The image, pinned to the digest a release pushed |

Both Compose files forward only the variables listed in [`.env.example`](../.env.example)
to the application: the database, the master key, the session cookie, the public
changelog, AI (OpenAI, Anthropic, and DeepSeek keys and models), DeepL, and SMTP.
Anything else keeps its application default inside those stacks; to change it, add it
to the `app` service's `environment` in the Compose file.
Forwarding an empty value instead would stop the application, which is why the
sensitive paths, the review thresholds, and the interface languages are left out. The demo
stack also sets `RELEASEFLOW_MANAGEMENT_ADDRESS=0.0.0.0` inside its private network so
Prometheus can scrape the application, and publishes nothing on that port.

## Build and test only

| Variable | Used by | What it does |
| --- | --- | --- |
| `RELEASEFLOW_VERSION`, `RELEASEFLOW_REVISION` | `Dockerfile` build arguments | The version and commit stamped into the image by the `Release` workflow |
| `RELEASEFLOW_E2E_BASE_URL` | Playwright | Points the browser suite at an application that is already running |
