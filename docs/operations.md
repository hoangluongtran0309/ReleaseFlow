# Operations

Running ReleaseFlow, watching it, and installing a published version. Every variable is
listed in [Configuration](configuration.md).

## Run with Docker Compose

`docker-compose.demo.yml` builds the application image from this checkout and runs
it with PostgreSQL 17, Prometheus, and Grafana for local evaluation; it is not a
production deployment. [Run the demo stack](getting-started.md#run-the-demo-stack) shows
how to start it, and the [Quickstart](getting-started.md#quickstart) runs a released
version without building it.

Compose refuses to start until `RELEASEFLOW_DB_PASSWORD`,
`RELEASEFLOW_CREDENTIAL_MASTER_KEY`, and `RELEASEFLOW_GRAFANA_PASSWORD` are set.
The application listens on `http://127.0.0.1:8080` (change it with
`RELEASEFLOW_HTTP_PORT`), the operations dashboard on `http://127.0.0.1:3000`
and Prometheus on `http://127.0.0.1:9090`. The database port and the
management port are not published. The container runs as UID `65534`, and its
health check calls the management port.
`docker compose -f docker-compose.demo.yml down --volumes` removes the demo data.

## Health and metrics

Actuator answers on its own port — 8081 on loopback by default, set with
`RELEASEFLOW_MANAGEMENT_PORT` and `RELEASEFLOW_MANAGEMENT_ADDRESS` — and serves
exactly two things there: `GET /actuator/health`, without component details, and
`GET /actuator/prometheus`. Everything else is refused, and the application port
serves none of it. Keep that port private; it is not a product endpoint.

Four metrics answer the operational questions, and every label comes from a
finite set:

| Metric | Labels | What it answers |
| --- | --- | --- |
| `releaseflow_classification_completed_total` | `needs_human_review` | How much is classified, and how much of it still needs a person |
| `releaseflow_classification_collect_to_complete_seconds` | — | How long a change waits between arriving and being classified |
| `releaseflow_classification_provider_requests_total` | `provider`, `outcome` | How often the AI refuses or cannot be read |
| `releaseflow_automation_action_executions_total` | `trigger`, `outcome` | How deliveries end, with unknown kept apart from failed |

**No label ever carries an Organization, Project, release, rule, run, action,
model, external reference, or error text.** Metrics are a deployment-wide view; a
question about one Organization is a product feature, not a label. Every series
exists from startup at zero, so a panel reads zero rather than "no data".

The demo stack provisions a *ReleaseFlow Operations* dashboard in Grafana with seven
panels: changes needing human review, classification throughput, time from a change
arriving to its classification, AI provider error rate, action failure rate by trigger,
deliveries nobody could confirm, and whether Prometheus can reach the application.
Prometheus keeps seven days of history. Alerting,
durable storage, a retention policy, and access control are a real deployment's
own to bring. See [ADR-0026](adr/0026-deployment-observability.md).

## Container image

The `Dockerfile` builds the JAR in a JDK stage and copies it into a Temurin 21 JRE image
that runs as UID/GID `65534`; its health check calls the management port. Build it on
its own with `docker build .`. The image reads the same `RELEASEFLOW_*` environment
variables as `./mvnw spring-boot:run`, and needs a PostgreSQL database it can reach.
Serve it behind HTTPS and set `RELEASEFLOW_SESSION_COOKIE_SECURE=true` and
`RELEASEFLOW_PUBLIC_BASE_URL` to the public address.

## Deploying to production

The quickstart and the demo stack run on one machine for evaluation. A deployment other
people rely on needs the following, and ReleaseFlow provides none of it itself.

**HTTPS and a reverse proxy.** Terminate TLS at a reverse proxy in front of the
application port and set:

```bash
RELEASEFLOW_PUBLIC_BASE_URL=https://releaseflow.example.com
RELEASEFLOW_SESSION_COOKIE_SECURE=true
SERVER_FORWARD_HEADERS_STRATEGY=native
```

The last one is Spring Boot's own setting: it lets the application read the proxy's
`X-Forwarded-Proto` and `X-Forwarded-Host`. Without it, behind a proxy that terminates
TLS, a redirect such as the one to the sign-in page points at `http://`, and the
interface-language and remembered-Project cookies are not marked `Secure`. The embedded
Tomcat accepts these headers only from private and loopback addresses; a proxy anywhere
else is named with `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`. Neither Compose file
forwards these variables, so add them to the `app` service's `environment` if you build
on one.

**What the internet reaches.** Providers need `/webhooks/`, and anonymous readers need
`/changelog/` if you publish a public changelog. Your own people need the rest, which can
stay behind a VPN or an allowlist. Once your Organization exists, refuse `/register` and
`POST /api/registrations` at the proxy, because
[registration stays open](workspace.md#registration). Never route the management port
through the proxy.

**Signing in.** ReleaseFlow does not limit failed sign-in attempts, so rate-limit
`POST /login` at the proxy. Sessions live in the application's memory: a restart signs
everybody out.

**One instance.** The background workers claim their work with `FOR UPDATE SKIP LOCKED`
and are written to run in every instance
([ADR-0008](adr/0008-durable-change-processing.md)), but sessions are not shared between
instances and nothing in CI runs more than one. Run a single instance unless you have
tested more behind sticky sessions.

**The database.** Every test and both Compose stacks run PostgreSQL 17. Give ReleaseFlow
a database of its own, owned by the user it connects as: Flyway creates the tables,
constraints, and triggers when the application starts.

**Backups.** Back up the database and `RELEASEFLOW_CREDENTIAL_MASTER_KEY` together, and
store them apart. A database restored without its key still starts, but every stored
secret is unreadable. Access tokens and action secrets can be entered again; a source's
webhook secret cannot, because a source cannot be replaced yet, so its every delivery is
refused with `401 webhook_signature_invalid` and the log says
`Could not decrypt the webhook secret of source …`. Restore a whole dump into an empty
database:

```bash
pg_dump --format=custom --file=releaseflow.dump releaseflow
createdb restored && pg_restore --dbname=restored --no-owner releaseflow.dump
```

`pg_restore` creates the triggers that freeze published releases after it has loaded the
data. Restoring the data alone into a database the application has already migrated is
refused by those triggers for every release past its draft.

**Upgrades.** A newer version migrates the schema forward when it starts, and no
migration can be undone. Take a backup first; going back means restoring that backup
with the older version, never starting the older version against the migrated database.

## Published artifacts

ReleaseFlow's own releases, as opposed to the ones it writes notes for, listed on the
[Releases page](https://github.com/hoangluongtran0309/ReleaseFlow/releases). This
section describes what the `Release` workflow publishes for each tag.

A release is a tag, and the tag is the version: `pom.xml` holds only the
`0.0.0-SNAPSHOT` placeholder in `${revision}`, and the `Release` workflow builds the tagged commit with `-Drevision=<version>`.
Pushing `v<version>` runs that workflow, which refuses a tag that is not an
ancestor of `main`, or one that `CHANGELOG.md` has no dated entry for — so a
mistaken tag fails rather than ships. It then runs the whole suite again, checks
the JAR carries the version, builds and scans the image, and publishes:

- `releaseflow-<version>.jar`, the executable JAR;
- `releaseflow-<version>-sbom.cdx.json`, a CycloneDX SBOM of the image, which
  lists the Java dependencies and the base image's packages together;
- `compose.yaml` and `quickstart.sh`, the [Quickstart](getting-started.md#quickstart) bundle, with
  the image pinned to the digest this release pushed;
- `SHA256SUMS`, `LICENSE` and `NOTICE`;
- the image on GHCR, tagged `<version>`, `<major>.<minor>`, `<major>` and
  `latest`;
- a provenance attestation for the image, the JAR, and the quickstart bundle;
- a GitHub release whose notes are the version's `CHANGELOG.md` entry, followed by the
  image digest, the quickstart commands, and how to verify the downloads.

A `v<version>-rc.<n>` tag on `release/<version>` or `hotfix/<version>` publishes a
release candidate the same way, marked as a pre-release, with the image tagged only
`<version>-rc.<n>` so nothing that follows `latest` moves to it. The workflow can also
be started by hand for a tag that already exists.

To cut a release, see [Releasing](../CONTRIBUTING.md#releasing): two commands,
`scripts/release.sh start <version>` and, once its pull request is merged,
`scripts/release.sh finish <version>`.

Check what you downloaded:

```bash
sha256sum --check SHA256SUMS
gh attestation verify releaseflow-<version>.jar \
    --repo hoangluongtran0309/ReleaseFlow
```

Pin a deployment to the digest rather than to a moving tag:

```bash
docker pull ghcr.io/hoangluongtran0309/releaseflow@sha256:<digest>
```
