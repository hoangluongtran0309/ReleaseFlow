# Contributing to ReleaseFlow

ReleaseFlow is developed as small, reviewable vertical slices. Every change
must preserve the product invariants documented in `AGENTS.md` and keep the
documentation aligned with the code.

## Development setup

- JDK 21, which `./mvnw` finds through `JAVA_HOME` (or, without it, the `javac` on
  `PATH`).
- Docker, because the integration tests run PostgreSQL through Testcontainers; an
  in-memory database is not a substitute.
- PostgreSQL to run the application itself (see
  [Run from source](README.md#run-from-source)).
- No system Maven or Node.js: the Maven Wrapper downloads Maven, and the build
  downloads a pinned Node.js and npm into `node/` on first use, so the first build
  needs network access. Never commit `node/` or `node_modules/`.

While changing templates or styles, rebuild the stylesheet on every save in a second
terminal:

```bash
PATH="$PWD/node:$PATH" ./node/npm run watch
```

Every word a person reads comes from `src/main/resources/messages/ui.properties`. A new
page, message, or failure adds its key there **and** to `ui_vi.properties`; a test fails
when the two disagree. [Messages](#messages) says how to write one.

## Messages

`MessageBundleTest` checks that both languages have the same keys and that each sentence
fills in the same `{n}` placeholders. It also checks that every key the code names is
translated. `NoInlineSentenceTest` and `FormConventionTest` guard the rules below.

**Keys.** `ui.<page>.<thing>` for page wording, `error.<problem_code>[.<variant>]` for a
failure, `validation.<field>.<rule>` for a constraint, and `typeMismatch.<type>` for a
value that does not convert. Problem Details titles are `error.<problem_code>.title`.

**Failures carry a key.** An exception a person can read extends `LocalizedException`
with a key and the values its sentence fills in, never a finished sentence. A controller
passes those on and never renders them:

| Failure | Shown as |
|---|---|
| One field is wrong | `bindingResult.rejectValue(field, key, args, null)`, written under that field, status 400 |
| The form as a whole was refused | `bindingResult.reject(key, args, null)` in the form's summary, or `UiNotice.error(...)` as `pageNotice`; status 400 or 409 |
| An action succeeded | `UiNotice.flash(redirect, UiNotice.success(key))`, then a redirect (PRG) |
| What the address names does not exist | a `ResourceNotFoundException`, which becomes the 404 page |
| Anything else | logged, and the 500 page with no details |

**Severity.** `success`, `info`, `warning`, and `error`, each drawn only by
`fragments/alert` or `fragments/toast`. An error is announced with `role="alert"`, the
others with `role="status"`.

**Forms.** The server alone decides what is valid. A form carries `novalidate`, keeps
`required` for assistive technology (CSS adds the `*`), and shows each error through
`fragments/form :: fieldError` with `aria-invalid` and `aria-describedby`. A form with more
than one field starts with `fragments/form :: errorSummary`. Never call
`setCustomValidity`.

**English.** Whole sentences, ending with a full stop, that say what to do next: "Enter a
whole number.", not "Invalid input". Put a value the person typed in quotes:
`The pattern "{0}" is not a valid glob.` In a message with placeholders a literal
apostrophe is written `''`.

**Vietnamese.** Translate the meaning, not the words, and keep the same placeholders.

- A success says what was done: "Đã lưu quy tắc."
- A missing thing: "Không tìm thấy release."
- A request after a refusal, warning, or validation error: "Vui lòng nhập tên."
- Guidance copy (subtitles, empty states, setup steps) uses the plain imperative without
  "Hãy": "Tạo release đầu tiên ở trên."

**Glossary.** Product terms stay in English in both languages: Organization, Project,
Release, Release note, Audience, Change Inbox, Category, Webhook, Access token, Template,
Automation, Action, Publish, Review. Everything else is translated the same way every
time:

| English | Tiếng Việt |
|---|---|
| change | thay đổi |
| rule | quy tắc |
| source | nguồn |
| catalog | danh mục |
| draft | nháp |
| approve | duyệt |
| pattern | mẫu |
| form | biểu mẫu |

## Tests

```bash
./mvnw verify      # unit and PostgreSQL integration tests, then packaging
```

The browser suite drives the demo Compose stack, so `.env` must be filled in first (see
[Run the demo stack](README.md#run-the-demo-stack)):

```bash
npm ci
npm run e2e:install   # downloads Chromium, once
npm run e2e
```

It brings the stack up itself and checks the release pipeline, invitations, and every
page, at 360 px in the light theme and 1440 px in the dark one, and **fails on any WCAG 2
A/AA, 2.1 A/AA or 2.2 AA violation Axe can see**. Point it at an application already
running with `RELEASEFLOW_E2E_BASE_URL`. A new or changed page keeps it green.

## Documentation

The guides in `docs/` are also a website, built with Docusaurus from `website/`, which has
its own `package.json` (see [ADR-0032](docs/adr/0032-documentation-site.md)). The site
reads each guide in place, so write plain Markdown that reads well on GitHub: no front
matter, no MDX.

```bash
npm --prefix website ci
npm --prefix website start                   # English, reloading on every save
npm --prefix website start -- --locale vi    # Vietnamese
npm --prefix website run build               # both languages, as CI builds them
```

- A new guide is placed in `website/sidebars.js`. A new decision record is listed there by
  its number without being added.
- The build fails on a broken link, anchor, or Markdown file link. Link to another guide by
  its file (`sources.md#history-import`), never by its address on the site. A link to a file
  outside `docs/`, such as `CONTRIBUTING.md`, is pointed at that file on `main`.
- English in `docs/` is the source. A Vietnamese page sits at the same path under
  `website/i18n/vi/docusaurus-plugin-content-docs/current/`, follows [Messages](#messages)
  for voice and the glossary, and changes in the same pull request as its English page. A
  page with no translation shows the English one. A translated heading keeps the English
  page's anchor, as in `## Nguồn {#sources}`, so links from other pages still land. The
  architecture, the implementation status, and the decision records stay in English.
- A screenshot sits on its own line under the paragraph it shows, as
  `![what it shows](assets/<guide>/<name>.webp)`, with the file in `docs/assets/<guide>/`.
  The Vietnamese page uses the same line, and the file at the same path under its own
  `assets/` shows the Vietnamese interface, or the English picture again when there is
  none. Pictures for the site's own pages live in `website/static/media/`. Use WebP: a
  still under 250 KB, an animation under 2.5 MB and 12 seconds. A missing image fails the
  build, like a broken link.
- The site's own wording — navbar, footer, sidebar, and landing page — is translated in
  `website/i18n/vi/`. `code.json` holds only the site's own strings and the few the theme
  does not translate itself.

The `Docs` workflow publishes the site from `main`, so a documentation fix appears with the
next release.

## Branch model

- `main` contains reviewed, releasable code. Direct development does not happen
  on this branch.
- `develop` is the integration branch for the next releasable state.
- `feature/<kebab-case-name>` branches from `develop` for one vertical slice or
  one independently reviewable capability.
- `fix/<kebab-case-name>` branches from `develop` for non-production defects.
- `release/<version>` branches from `develop` only when a real release is being
  stabilized.
- `hotfix/<version>` branches from `main` only for an urgent production fix and
  is merged back into both `main` and `develop`.

Do not create speculative feature or release branches. Create the next branch
only when its work begins, and delete short-lived branches after merge.

## Commit convention

Commits follow Conventional Commits:

```text
<type>(<scope>): <imperative summary>
```

Use one of these types:

- `feat`: user-visible capability;
- `fix`: defect correction;
- `docs`: documentation-only change;
- `test`: test-only change;
- `refactor`: behavior-preserving code change;
- `build`: build system or dependency change;
- `ci`: continuous-integration change;
- `chore`: repository maintenance not covered above.

Keep the summary lowercase, imperative, and without a trailing period. Add a
body when the reason or trade-off is not evident. Use `BREAKING CHANGE:` in the
footer only for an intentional incompatible contract change.

Examples:

```text
feat(account): register organization owner
fix(webhook): reject repository identity mismatch
docs(architecture): record tenant isolation decision
```

## Pull request quality gate

Before requesting review:

1. Rebase the branch on the current `develop` branch.
2. Run `./mvnw verify` with JDK 21, `npm run e2e` when a page changed, and
   `npm --prefix website run build` when a guide changed.
3. Review the complete diff for credentials, generated files, and unrelated
   changes.
4. When behavior changes, add an entry under `[Unreleased]` in `CHANGELOG.md` and
   update the guide in `docs/` that describes it, `docs/architecture.md`, and
   `docs/implementation-status.md`; record a new decision as the next ADR in
   `docs/adr/` and list it in [the index](docs/adr/README.md).
5. Squash a pull request into `develop`, so each slice lands as one commit that
   follows the commit convention. **Merge a `release/*` or `hotfix/*` pull
   request into `main`, and the `chore/back-merge-*` pull request into
   `develop`, with a merge commit instead**: squashing or rebasing would give
   `main` a commit that shares no ancestry with `develop`, and the two branches
   would diverge for good — the back-merge that `scripts/release.sh finish`
   opens would conflict, and the tag would point at a commit `develop` has never
   seen.

GitHub Actions enforces the automated part of this gate on every pull request
to `develop` and `main`: the pull request title must follow the commit
convention above, and the workflows below must pass. Keep new actions pinned to a
commit SHA and new images to a digest.

By contributing, you agree that your contribution is licensed under the
[Apache License 2.0](LICENSE), the same licence as the rest of the project. There
is no separate contributor agreement to sign.

Merges into `main` represent a reviewed release decision.

## Continuous integration

| Workflow | Runs on | Checks |
| --- | --- | --- |
| `CI` | push and pull request to `develop` and `main` | Conventional pull request title (pull requests only), actionlint, ShellCheck, a whole release run against a local remote, the quickstart under bash 3.2, `npm audit --audit-level=high`, the browser and accessibility suite, `./mvnw clean verify`, and a check that no version override has outlived its reason |
| `CodeQL` | the same, and weekly | Java and JavaScript analysis |
| `Dependency Review` | pull requests only | Fails pull requests that add high or critical vulnerabilities |
| `Secret Scan` | push and pull request, and weekly | Gitleaks over the complete Git history |
| `Container` | push and pull request | Image build, Trivy high/critical scan, Compose smoke test, the management port proven private, all four metric families reaching Prometheus, the Grafana dashboard provisioned, and the quickstart bundle run against the image |
| `Docs` | push to `main`, and pull request to `develop` and `main` | The documentation site's dependency audit (`scripts/ci/audit-website.sh`), a build in both languages that fails on any broken link, and, from `main` only, publication to GitHub Pages |
| `Release` | a `v<version>` tag | See [Releasing](#releasing) |

Dependabot proposes weekly updates to `develop`, except a new major version of the Java
runtime image or of PostgreSQL, which is a decision of its own. The pinned tools in `scripts/ci/`
verify their checksums, so the scans can be reproduced locally, for example:

```bash
./scripts/ci/install-gitleaks.sh "$HOME/.local/bin"
gitleaks git . --redact
```

See [ADR-0007](docs/adr/0007-ci-and-container-supply-chain.md) and
[ADR-0026](docs/adr/0026-deployment-observability.md).

## Dependencies

A build must resolve the same artifacts today and in six months, so every version
is exact and every change to one is a reviewed pull request (Dependabot opens
them weekly).

- Leave the version out of anything the Spring Boot parent manages; its BOM keeps
  those versions tested together.
- Give anything else an exact version through a property in `<properties>`, never
  a range, `LATEST`, `RELEASE`, or a SNAPSHOT.
- If a transitive dependency arrives with a range, exclude it when nothing uses
  it, or pin it in `<dependencyManagement>` when something does.
- To pull in a fix the parent does not manage yet, override its version property
  with a comment naming the CVE, and add the property to
  `scripts/ci/check-version-overrides.sh`, which fails CI once the parent catches
  up so the override is removed.
- The Maven wrapper is the `bin` type: `mvnw` downloads `maven-wrapper.jar`
  (never committed) and checks it against `wrapperSha256Sum`, and the jar
  downloads Maven and checks it against `distributionSha256Sum`. Changing either
  version in `.mvn/wrapper/maven-wrapper.properties` means changing its checksum
  to the SHA-256 of the new file, checked against the checksum Maven Central
  publishes beside it. (The script-only wrapper cannot do this: without `unzip`,
  as in the Docker build, it switches to the `.tar.gz`, which no single checksum
  matches.)

`maven-enforcer-plugin` runs these rules at the start of every build, so
`./mvnw verify` reports a violation before anything compiles.

The documentation site's npm dependencies are exact in `website/package.json` too. A
transitive advisory that a release fixes is pinned to that release through `overrides`,
removed once Docusaurus requires the fix itself. One that no release fixes is listed, with
the reason it cannot reach a static site, in `scripts/ci/audit-website.sh` and in the
Dependency Review workflow's `allow-ghsas`; the script fails once npm stops reporting it.

## Releasing

A release is a tag, and nothing else is a release. The tag is also the version:
`pom.xml` says `${revision}`, which is `0.0.0-SNAPSHOT` for every local and CI
build, and the `Release` workflow builds the tagged commit with
`-Drevision=<version>`. **Never write a version into the POM, the Dockerfile, or
any other file.** Write what changed under `## [Unreleased]` in `CHANGELOG.md` as
part of each pull request; that section becomes the release notes.

Cutting a release is two commands, with [`gh`](https://cli.github.com) signed in:

```bash
scripts/release.sh start 1.2.0     # release/1.2.0 from develop, CHANGELOG dated, PR into main
# let the gates run, then merge the pull request with a merge commit
scripts/release.sh finish 1.2.0    # tags main v1.2.0, which publishes; PR merging main back
```

- `start` checks that the working tree is clean, that the version is
  `MAJOR.MINOR.PATCH`, newer than the last release, and not tagged yet, and that
  `[Unreleased]` lists something. It branches `release/<version>` from
  `origin/develop`, turns `[Unreleased]` into `[<version>] - <today>` with a new
  empty `[Unreleased]` above it, commits `chore(release): prepare <version>`,
  pushes, and opens the pull request into `main`. The date is the day the release
  is prepared; correct it in the pull request if the release waits.
- `finish` checks that `main` now has the dated entry, tags `origin/main` as
  `v<version>`, pushes the tag — that is what publishes — and opens
  `chore/back-merge-<version>` into `develop`. Merge that one with a merge commit
  too. Nothing needs bumping afterwards.
- `scripts/release.sh rc <version>` tags the tip of `release/<version>` (or
  `hotfix/<version>`) as the next `v<version>-rc.<n>`, which publishes a pre-release
  to test before merging.
- A hotfix starts from `main`: `scripts/release.sh start 1.2.1 --hotfix` creates
  `hotfix/1.2.1`. When `[Unreleased]` is still empty it stops there; commit the fix
  with its entry under `[Unreleased]`, run the same command again, then `finish` as
  usual.
- Add `--dry-run` to any of them to see every check and change without making
  one. The remote is `origin` unless `RELEASE_REMOTE` names another.

Pick the version by [Semantic Versioning](https://semver.org/): a `BREAKING
CHANGE` since the last release raises the major version, a `feat` the minor, and
only `fix` and the rest the patch.

Pushing the tag is what publishes. The `Release` workflow refuses a tag that is
not an ancestor of `main` (a release candidate must be on its `release/` or
`hotfix/` branch instead), or one that `CHANGELOG.md` has no dated entry for, so a
tag pushed by mistake fails rather than ships. It then runs the whole suite again
with the tag's version, checks the JAR's manifest carries it, builds and scans the
image, and publishes the JAR, a CycloneDX SBOM, the quickstart bundle
(`compose.yaml` pinned to the pushed digest, and `quickstart.sh`), `SHA256SUMS`,
the licence and the notice, the image on GHCR tagged `X.Y.Z`/`X.Y`/`X`/`latest`
(only `X.Y.Z-rc.N` for a release candidate), a provenance attestation for the
image, the JAR, and the quickstart bundle, and a GitHub release whose notes are the
version's `CHANGELOG.md` entry. [Operations](docs/operations.md#published-artifacts)
lists the artifacts and how to verify them. See
[ADR-0030](docs/adr/0030-release-automation.md).
