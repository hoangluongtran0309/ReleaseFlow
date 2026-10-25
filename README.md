<h1 align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset=".github/assets/logo-dark.svg">
    <img alt="ReleaseFlow" src=".github/assets/logo-light.svg" width="344">
  </picture>
</h1>

<p align="center">
  <strong>Turn merged pull requests and finished issues into release notes that a person has
  reviewed, written for each audience, and delivered where they read them.</strong>
</p>

<p align="center">
  <a href="https://github.com/hoangluongtran0309/ReleaseFlow/actions/workflows/ci.yml"><img alt="CI" src="https://img.shields.io/github/actions/workflow/status/hoangluongtran0309/ReleaseFlow/ci.yml?branch=main&label=CI&logo=githubactions&logoColor=white"></a>
  <a href="https://github.com/hoangluongtran0309/ReleaseFlow/actions/workflows/codeql.yml"><img alt="CodeQL" src="https://img.shields.io/github/actions/workflow/status/hoangluongtran0309/ReleaseFlow/codeql.yml?branch=main&label=CodeQL&logo=github&logoColor=white"></a>
  <a href="LICENSE"><img alt="Licence: Apache 2.0" src="https://img.shields.io/badge/licence-Apache%202.0-blue"></a>
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white">
  <img alt="Spring Boot 4" src="https://img.shields.io/badge/Spring%20Boot-4-6DB33F?logo=springboot&logoColor=white">
  <img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white">
  <a href="https://www.conventionalcommits.org/en/v1.0.0/"><img alt="Conventional Commits" src="https://img.shields.io/badge/Conventional%20Commits-1.0.0-FE5196?logo=conventionalcommits&logoColor=white"></a>
</p>

<p align="center">
  <a href="#quickstart">Quickstart</a> ·
  <a href="#documentation">Documentation</a> ·
  <a href="docs/api.md">REST API</a> ·
  <a href="CONTRIBUTING.md">Contributing</a> ·
  <a href="CHANGELOG.md">Changelog</a>
</p>

Release notes are usually written by hand on release day: someone reads every merged
pull request, guesses which ones matter, and writes one note that is too technical for
users and too vague for operators. ReleaseFlow collects the changes as they land,
classifies them, lets AI draft the summaries, and keeps a person in charge of what goes
out.

> **Releases:** every published version and its artifacts are listed on the
> [Releases page](https://github.com/hoangluongtran0309/ReleaseFlow/releases), and the
> [changelog](CHANGELOG.md) says what each one changed. What is not released yet is
> recorded under `[Unreleased]`; to try it, run ReleaseFlow
> [from source](#run-from-source) or with the [demo stack](#run-the-demo-stack).

## About ReleaseFlow

ReleaseFlow is named for the path every change takes
through it: from the moment a pull request is merged or an issue is finished, through
classification and a person's review, to a note written for each reader and delivered to
the place that reader already looks. Each step is recorded, so a published note always
shows who reviewed what went into it.

It is meant for teams that ship to more than one kind of reader — the people who operate
the software, the people who build on it, and the people who use it — and who want those
notes written from the same reviewed facts instead of rewritten by hand three times.

## How it works

1. **Collect.** Merged pull requests from GitHub, merged merge requests from GitLab, and
   completed issues from Linear and Jira arrive by signed webhook or a scheduled poll.
   GitHub and GitLab can also import the last 90 days.
2. **Classify.** Explainable rules sort every change into a category. Optional AI
   (OpenAI, Anthropic, or DeepSeek) writes a neutral summary and suggests a category,
   but the rules always win, and breaking, unrecognized, or sensitive-file changes always
   wait for a person.
3. **Review.** A release moves from draft through a decision on every change to
   approval. Changes with too little context, or that look like earlier ones, are flagged
   with the evidence.
4. **Write.** One note per audience (operators, contributors, end users, or your own),
   from a Mustache template, in up to five languages translated by DeepL.
5. **Publish.** Published notes are frozen, then delivered by automation rules to GitHub
   Releases, Slack, email, Notion, Confluence, Microsoft Teams, Zendesk, or a public
   changelog with an RSS feed.

It is self-hosted: one Java 21 / Spring Boot application and PostgreSQL, with an
English or Vietnamese interface.

## How it compares

Tools such as [release-drafter](https://github.com/release-drafter/release-drafter),
[git-cliff](https://github.com/orhun/git-cliff), and
[semantic-release](https://github.com/semantic-release/semantic-release) generate one
changelog from commits or pull request labels, inside your repository or CI. They are
simpler to adopt, and if one technical changelog is all you need, use one of them.

ReleaseFlow is for the step after that: changes from several systems (GitHub, GitLab,
Linear, Jira) in one release, a review and approval before anything is published,
separate notes for separate readers in several languages, and delivery beyond the
repository. The price is a server and a database to run.

## Quickstart

The quickest way to try a released version: the published image and PostgreSQL on one
machine, with nothing to build and no repository to clone. Every release attaches the
bundle, so this works from the first published release on; until then, use the
[demo stack](#run-the-demo-stack).

You need Docker Engine or Docker Desktop with Compose v2, and `openssl`, on Linux or
macOS. On Windows, run it inside WSL 2. The image is amd64 only, so Apple silicon runs it
under emulation; turn on Docker Desktop's Rosetta setting if it starts slowly.

```bash
mkdir releaseflow && cd releaseflow
base=https://github.com/hoangluongtran0309/ReleaseFlow/releases/latest/download
curl -fsSL --remote-name-all "$base/compose.yaml" "$base/quickstart.sh" "$base/SHA256SUMS"
sha256sum --check --ignore-missing SHA256SUMS   # macOS: shasum -a 256 --check --ignore-missing SHA256SUMS
bash quickstart.sh
```

Then open `http://localhost:8080/register` to create the first administrator. To use
another port, choose it on the first run: `RELEASEFLOW_HTTP_PORT=18080 bash quickstart.sh`.

**Keep the `.env` the script writes beside it.** The database was created with its
password, and its key encrypts every stored webhook secret and access token. What the
script checks, how to turn on AI, the everyday Compose commands, and how to upgrade are in
[Getting started](docs/getting-started.md#quickstart). The bundle is for one machine, not
production: TLS, backups, and secret storage are a real deployment's own to bring, and
[Deploying to production](docs/operations.md#deploying-to-production) lists what that takes.

## Run from source

You need JDK 21 (which `./mvnw` finds through `JAVA_HOME`, or the `javac` on `PATH`) and
a dedicated PostgreSQL database. Maven and Node.js come with the build: the Maven Wrapper
downloads Maven, and the build downloads a pinned Node.js into `node/` on first use.

```bash
export RELEASEFLOW_DB_URL=jdbc:postgresql://localhost:5432/releaseflow
export RELEASEFLOW_DB_USERNAME=releaseflow
export RELEASEFLOW_DB_PASSWORD='replace-with-a-local-secret'
export RELEASEFLOW_CREDENTIAL_MASTER_KEY="$(openssl rand -base64 32)"   # keep this value
./mvnw spring-boot:run
```

Open `http://localhost:8080/register` to create the first Organization and its
administrator. Flyway creates the schema on startup. Everything else — AI, translation,
email, provider timeouts, the public changelog address — is optional and listed in
[Configuration](docs/configuration.md).

## Run the demo stack

`docker-compose.demo.yml` builds the image from this checkout and runs it with
PostgreSQL 17, Prometheus, and Grafana, for local evaluation only:

```bash
cp .env.example .env
# Replace every REPLACE_ME value. Generate the master key with:
#   openssl rand -base64 32
docker compose -f docker-compose.demo.yml up --build
```

The application is on `http://127.0.0.1:8080`, Grafana on `http://127.0.0.1:3000`, and
Prometheus on `http://127.0.0.1:9090`. See [Operations](docs/operations.md).

## Documentation

| Guide | What it covers |
| --- | --- |
| [Getting started](docs/getting-started.md) | The quickstart, running from source, the demo stack, and the first steps after signing in |
| [Workspace](docs/workspace.md) | Registration, roles and invitations, the overview, interface and output languages |
| [Sources](docs/sources.md) | Connecting GitHub, GitLab, Linear, and Jira; webhooks, polling, access tokens, and history import |
| [Changes and review](docs/changes.md) | Processing, classification rules, sensitive paths, AI, review signals, human review, categories |
| [Release notes](docs/release-notes.md) | Audiences, release note languages and translation, the release lifecycle, publishing |
| [Automation](docs/automation.md) | Rules, triggers, actions, signed calls, runs, and the public changelog |
| [REST API](docs/api.md) | Sessions and CSRF, every endpoint, and every error code |
| [Configuration](docs/configuration.md) | Every `RELEASEFLOW_*` environment variable and its default |
| [Operations](docs/operations.md) | The demo stack, health and metrics, the container image, published artifacts |
| [Architecture](docs/architecture.md) | How the application is built, and the invariants it keeps |
| [Implementation status](docs/implementation-status.md) | What is implemented, by slice, and what is deliberately deferred |
| [Decision records](docs/adr/README.md) | ADR-0001 to ADR-0034 |

## Feedback and contributing

ReleaseFlow is a personal project, and feedback is the most useful contribution right
now: how your team writes release notes today, which sources or destinations are
missing, and anything confusing in this documentation.

- Ask a question or share an idea in
  [Discussions](https://github.com/hoangluongtran0309/ReleaseFlow/discussions).
- Report a bug or request a feature through
  [Issues](https://github.com/hoangluongtran0309/ReleaseFlow/issues/new/choose).
- Read the [contributor guide](CONTRIBUTING.md) for the development setup, tests,
  branches, and commit convention. Everyone taking part is expected to follow the
  [code of conduct](CODE_OF_CONDUCT.md).

## Security

ReleaseFlow holds webhook signing secrets, provider access tokens, and the
credentials its automation actions deliver with, and it answers unauthenticated
webhooks. If you find a weakness, please report it privately rather than in an
issue: see the [security policy](SECURITY.md).

## Licence

ReleaseFlow is one Maven module under the [Apache License 2.0](LICENSE)
([ADR-0029](docs/adr/0029-one-module-and-the-apache-licence.md)). You may use,
modify and redistribute it, including commercially, provided you keep the licence
and the [notice](NOTICE) and state what you changed.

There is no open-core or enterprise split: templates, automation, and distribution
sit beside every other capability, under the same licence as the rest.

Copyright 2026 Luong Tran Chu Hoang.

ReleaseFlow was developed privately before 1.0.0. Its public history starts at the 1.0.0
release; earlier development history was not kept.
