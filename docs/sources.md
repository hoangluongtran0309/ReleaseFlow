# Sources

A Project collects changes from one or more sources: GitHub repositories, GitLab
projects, Linear teams, and Jira Cloud projects. GitHub, GitLab, and Linear deliver
signed webhooks; Jira is polled. Every change is recorded once, by its source and the
source's own ID for it, whether it arrived by webhook, poll, or [history
import](#history-import).

## Connect a source

Administrators connect sources on the **Projects** page, or through REST:

```text
GET  /api/projects/{projectId}/sources                       (every member)
POST /api/projects/{projectId}/sources                       (administrator; 201)
PUT  /api/projects/{projectId}/sources/{sourceId}/token      (administrator; 204)
```

The body of `POST` depends on the type:

| Type | Body | What comes back |
| --- | --- | --- |
| GitHub | `{"type": "GITHUB", "owner", "repository"}` | The webhook path and a signing secret, **shown once** |
| GitLab | `{"type": "GITLAB", "apiBaseUrl", "projectPath", "webhookAuthMode"}` | The webhook path and a signing secret, **shown once**; with signed headers, also `signingToken`, the same secret in the form GitLab's signing token takes |
| Linear | `{"type": "LINEAR", "teamId", "webhookSecret", "apiToken"}` | The webhook path; Linear mints the secret, so you supply it and nothing secret is returned |
| Jira | `{"type": "JIRA", "siteUrl", "projectKey", "accountEmail", "apiToken"}` | Nothing secret; Jira has no webhook |

Save a returned secret immediately: it is stored encrypted with AES-256-GCM and never
shown again. Repository owners, repository names, and GitLab project paths are
canonicalized to lowercase. A repository or project can be connected to one Project per
Organization, while different Organizations may connect the same one, and the same name
on two providers is two separate sources. A source cannot be disconnected or replaced yet.

An unknown source returns `404 source_not_found`, and one already connected returns
`409 github_repository_already_connected`, `409 gitlab_project_already_connected`,
`409 linear_team_already_connected`, or `409 jira_project_already_connected`. Members get
`403` for the administrator endpoints.

## Receive GitHub webhooks

In the GitHub repository, open **Settings → Webhooks → Add webhook** and enter:

- **Payload URL**: the public ReleaseFlow address followed by the webhook path
  shown for the Project, for example
  `https://releaseflow.example.com/webhooks/github/<webhook-id>`;
- **Content type**: `application/json`;
- **Secret**: the signing secret saved when the repository was connected;
- **Events**: *Let me select individual events* → *Pull requests*.

`POST /webhooks/github/{webhookId}` needs no session or CSRF token. ReleaseFlow
verifies `X-Hub-Signature-256` with that integration's own secret before it
reads the payload, and takes the Organization and Project from the integration,
never from the payload. It answers:

| Delivery | Response |
| --- | --- |
| Larger than 1 MiB | `413` `webhook_payload_too_large` |
| Unknown webhook ID, missing or wrong signature | `401` `webhook_signature_invalid` |
| `repository.full_name` is not the configured repository | `422` `webhook_repository_mismatch` |
| Verified delivery with invalid JSON, no `X-GitHub-Event`, or a merged pull request missing fields or `X-GitHub-Delivery` | `400` `webhook_payload_malformed` |
| `ping` | `200` `{"outcome":"pong"}` |
| Merged pull request (`closed` with `merged: true`) | `200` `recorded`, or `duplicate` when already recorded |
| Any other event or action | `200` `ignored` |

Each merged pull request is stored once per repository, so GitHub redeliveries
are harmless, and two repositories of one Project may both have a pull request #12.
The Projects page shows the time of the last accepted delivery,
which appears as soon as GitHub sends its initial `ping`.

To exercise the endpoint locally without GitHub, sign the exact bytes you send:

```bash
WEBHOOK_PATH=/webhooks/github/replace-with-the-webhook-id
WEBHOOK_SECRET='replace-with-the-saved-secret'
BODY='{"zen":"Keep it logically awesome.","repository":{"full_name":"owner/repository"}}'
SIGNATURE="sha256=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" | awk '{print $NF}')"
curl -sS -X POST "http://localhost:8080$WEBHOOK_PATH" \
  -H 'Content-Type: application/json' \
  -H 'X-GitHub-Event: ping' \
  -H "X-GitHub-Delivery: $(uuidgen)" \
  -H "X-Hub-Signature-256: $SIGNATURE" \
  --data-binary "$BODY"
```

## Receive GitLab webhooks

In the GitLab project, open **Settings → Webhooks → Add new webhook** and enter:

- **URL**: the public ReleaseFlow address followed by the webhook path shown for
  the source, for example
  `https://releaseflow.example.com/webhooks/gitlab/<webhook-id>`;
- with **signed headers** (the default): the **signing token** shown when the
  project was connected in GitLab's **Signing token** field (`signing_token` in the
  hooks API), and **Secret token** left empty;
- with a **secret token header**: the saved secret in **Secret token**;
- **Trigger**: *Merge request events*.

The signing token is the secret written the way Standard Webhooks, and so GitLab,
expects it: `whsec_` followed by the standard, padded Base64 of the secret's 32 bytes.
The secret itself is unpadded Base64url of the same bytes, which GitLab refuses as a
signing token. Both are shown once, on the page and in the `signingToken` field of
the response that connects the source. To write the signing token from a saved
secret:

```bash
printf 'whsec_%s\n' "$(printf '%s=' "$WEBHOOK_SECRET" | tr '_-' '/+' | base64 -d | base64 -w0)"
```

`POST /webhooks/gitlab/{webhookId}` needs no session or CSRF token, and
ReleaseFlow proves the delivery with that source's own secret before it reads
the payload. Which check applies is chosen when the source is connected:

- **Signed headers (Standard Webhooks)**, the default: `webhook-id`,
  `webhook-timestamp`, and `webhook-signature` must all be present, the
  signature must be `v1,` followed by the Base64 HMAC-SHA256 of
  `id.timestamp.body` under the secret — any one of several space-separated
  candidates may match — and the timestamp must be within five minutes of
  ReleaseFlow's clock.
- **Secret token header**: `X-Gitlab-Token` must equal the secret.

The Organization and Project come from the source, never from the payload:

| Delivery | Response |
| --- | --- |
| Larger than 1 MiB | `413` `webhook_payload_too_large` |
| Unknown webhook ID, a source of another type, missing, stale, or wrong proof | `401` `webhook_signature_invalid` |
| `project.path_with_namespace` is not the connected project | `422` `webhook_repository_mismatch` |
| Proven delivery with invalid JSON, or a merged merge request missing fields | `400` `webhook_payload_malformed` |
| Merged merge request (`object_kind: merge_request` with `action: merge`) | `200` `recorded`, or `duplicate` when already recorded |
| Any other event or action | `200` `ignored` |

Each merge request is stored once per source, so redeliveries are harmless.
GitLab identifies a delivery only in its signing mode, so a change records
`X-Gitlab-Event-UUID` when it is a GUID and nothing otherwise.

Only instances named in `RELEASEFLOW_GITLAB_ALLOWED_HOSTS` (default
`gitlab.com`) can be connected. An entry is `host` or `host:port`, which means
HTTPS, or a full origin such as `http://gitlab.internal:8080`; writing the
scheme is the only way to accept an instance reached without TLS. A URL with
credentials, a query, or a fragment is refused with
`400 gitlab_base_url_invalid`, and any other host with
`400 gitlab_host_not_allowed`. ReleaseFlow never follows a redirect from a
GitLab instance.

To exercise the endpoint locally without GitLab, sign the exact bytes you send:

```bash
WEBHOOK_PATH=/webhooks/gitlab/replace-with-the-webhook-id
SIGNING_TOKEN='whsec_replace-with-the-signing-token'
BODY='{"object_kind":"push"}'
DELIVERY_ID="$(uuidgen)"
TIMESTAMP="$(date +%s)"
SIGNATURE="v1,$(printf '%s' "$DELIVERY_ID.$TIMESTAMP.$BODY" \
  | openssl dgst -sha256 -mac HMAC -macopt "hexkey:$(printf '%s' "${SIGNING_TOKEN#whsec_}" \
      | base64 -d | xxd -p -c 256)" -binary | base64)"
curl -sS -X POST "http://localhost:8080$WEBHOOK_PATH" \
  -H 'Content-Type: application/json' \
  -H "webhook-id: $DELIVERY_ID" \
  -H "webhook-timestamp: $TIMESTAMP" \
  -H "webhook-signature: $SIGNATURE" \
  --data-binary "$BODY"
```

## Receive Linear webhooks

Linear mints the signing secret itself, so create the webhook there first: under
**Settings → API → Webhooks**, set the URL to your ReleaseFlow address followed
by the path ReleaseFlow shows for the source, and enable the **Issues** event.
Copy the secret Linear displays and paste it, with an API key, when connecting
the team in ReleaseFlow.

`POST /webhooks/linear/{webhookId}` needs no session or CSRF token. A delivery
proves itself with four things, all of which must hold:

- a `Linear-Delivery` header;
- a `createdAt` within `RELEASEFLOW_LINEAR_WEBHOOK_CLOCK_SKEW` (60 seconds) of
  ReleaseFlow's clock, in ISO-8601 or as epoch seconds or milliseconds;
- an `organizationId` equal to the workspace the team belongs to, which
  ReleaseFlow learned when the team was connected;
- a hex HMAC-SHA256 of the raw body in `Linear-Signature`.

| Delivery | Response |
| --- | --- |
| Larger than 1 MiB | `413` `webhook_payload_too_large` |
| Unknown webhook ID, a source of another type, a missing header, a stale stamp, another workspace, or a wrong signature | `401` `webhook_signature_invalid` |
| A body that is not a JSON object, even with a correct signature: the stamp and the workspace are read from the body, so it can never be proven | `401` `webhook_signature_invalid` |
| An issue of a team other than the connected one | `422` `webhook_repository_mismatch` |
| A proven delivery whose issue is missing fields | `400` `webhook_payload_malformed` |
| An issue that has just moved into a completed state | `200` `recorded`, or `duplicate` when already recorded |
| Any other event, action, or state change | `200` `ignored` |

An issue counts only when it really moved into being done: `type` `Issue`,
`action` `update`, an `updatedFrom` that carries a state, a new state of
`completed`, and an old state that is not. Editing an issue that was already
completed changes nothing.

Linear names no delivery ReleaseFlow can record as a GUID, so a Linear change
carries no delivery ID. Each issue is still stored once per source, by its UUID,
so redeliveries are harmless.

## Poll Jira

Jira needs nothing configured on its side. Connect a Jira Cloud project with its
site (`https://your-site.atlassian.net`), its project key (`APP`), the email of
the account ReleaseFlow signs in as, and an
[Atlassian API token](https://id.atlassian.com/manage-profile/security/api-tokens)
for that account that can browse the project. ReleaseFlow asks Jira to confirm
them before storing anything, and answers `400 jira_site_invalid` for a site that
is not an HTTPS `atlassian.net` address with no port, credentials, query, or
fragment, `400 jira_token_rejected` when Jira refuses the account and token, and
`503 jira_unavailable` when it cannot be reached.

From then on, ReleaseFlow reads the project every
`RELEASEFLOW_JIRA_POLL_INTERVAL` (five minutes). Each read asks for the issues in
a Done status category that were updated since the previous read, reaching back
`RELEASEFLOW_JIRA_POLL_OVERLAP` (ten minutes) so nothing at the edge is lost, and
records each issue that was resolved in that window once. Issues resolved before
the project was connected are never read. A change from Jira:

- is numbered after its key — `APP-123` is `#123` — and titled
  `APP-123: summary`, with the reporter as its author and
  `{site}/browse/APP-123` as its link;
- has no changed files, so it is checked with the same sensitive-word scan as a
  Linear issue;
- starts with its key, so the Conventional Commit title rule never matches it:
  without AI, a Jira change is Unknown and needs review.

A read that fails keeps its place and tries again, and the source shows
**Connection failing** until one succeeds. A failure Jira may get over, such as a
rate limit or an outage, is tried up to five times, after the wait Jira asked for (at
most an hour) or after 2, 4, 8 … seconds (at most five minutes). A read that still
fails, or that Jira refused outright, such as a token it rejected, books the next read
**15 minutes** later rather than after `RELEASEFLOW_JIRA_POLL_INTERVAL`. That wait is
fixed in ReleaseFlow, not configured, and neither saving a new token nor restarting
brings the booked read forward. The Change Inbox shows each Jira project's last and next
read; a Jira project has no history import.

### Linked Jira issues

When a Project has a Jira source, each GitHub or GitLab change is checked for
that project's issue keys in its title, description, target branch, and up to
250 commit messages. At most ten issues are read from Jira and shown under
**Linked issues** on the change, given to the AI as untrusted evidence, and
available to audience templates:

```mustache
{{#linkedIssues}} [{{key}}]({{url}}) {{title}} ({{type}}, {{status}}){{/linkedIssues}}
```

Commit messages are only searched for keys; they are never stored. When commits
could not be read, or Jira would not show an issue the change mentions, the
change is retried and then marked **Linked issues incomplete**, which forces
review. A key followed by a hyphen, as in `feature/APP-3-export`, is not taken
as a mention.

## Access tokens

With an access token, ReleaseFlow lists the files each merged pull or merge
request touched, so changes to sensitive files are always reviewed. Without one, every
new GitHub or GitLab change needs review, because nothing can be ruled out. Linear and
Jira need their token from the moment they are connected.

For GitHub, create a fine-grained personal access token limited to the
repository, with **Pull requests: Read-only** permission (a classic token needs
`repo` for a private repository). For GitLab, create a project access token with
the `read_api` scope. For Linear, create an API key that can read the team —
that one is required when the team is connected, because confirming it is also
how ReleaseFlow learns the workspace. For Jira, the API token is required too,
and always belongs to the account email the source was connected with. An
administrator enters it under the
source in the Project's card on the Projects page, or calls:

```text
PUT /api/projects/{projectId}/sources/{sourceId}/token {"token"}   (administrator; 204)
```

ReleaseFlow first asks the provider to confirm the token — GitHub for the
repository's pull requests, GitLab for the project, Linear for the team, which it
accepts only while the key still reaches the same workspace, Jira for the project
— then stores it
encrypted with AES-256-GCM. The token is never returned; the source only reports
`accessTokenConfigured` and `accessTokenUpdatedAt`. Sending a new token replaces
the old one. Errors are `400 github_token_rejected` or `400 gitlab_token_rejected`
or `400 linear_token_rejected` or `400 jira_token_rejected` when the provider
refuses the token, `503 github_unavailable`, `503 gitlab_unavailable`, or
`503 jira_unavailable` when it cannot be reached,
`404 source_not_found`, and `403` for members.

GitLab's changed files come from the merge request's diffs, at most ten pages of
a hundred. A diff GitLab reports as collapsed, too large, or truncated makes the
whole list unavailable rather than short, so a file that was never seen is never
declared safe.

Linear has no changed files at all, which is not the same as failing to list
them: instead of forcing review for ever, ReleaseFlow reads the issue's own title
and description for `breaking change`, `migration`, `security`, `auth`,
`credential`, `password`, and `encryption`, and each match becomes a
**Sensitive word** trigger that forces review. Linear's token is also what lets
ReleaseFlow read the issue back, so a change carries the issue's current wording
rather than whatever it said the instant it was completed.

## History import

Webhooks bring in the pull and merge requests merged after a source is
connected. To add the ones merged before, an administrator opens the **Source
sync** panel of the Change Inbox and chooses **Import last 90 days** for a
source with an access token, or calls the REST endpoints below. Only a source
whose provider keeps a history ReleaseFlow can read offers it; Linear and Jira
do not, and asking anyway answers `409 source_import_not_supported`. A background
worker reads the source's history 100 items per page — GitHub's closed pull
requests, most recently updated first, or GitLab's merged merge requests updated
after the window's start, least recently updated first — and records every one
merged in the last 90 days through the same processing as a webhook delivery:
its changed files are listed and, when AI is configured, it is classified once.
An item already recorded, by webhook or by an earlier import, is skipped, so
imports and webhooks never duplicate each other. Imported changes carry an
**Imported** badge.

A GitHub import stops at the first pull request updated before its window, and
either import stops after 500 new changes as **Stopped at the limit** with its
position saved; **Resume** continues from there with a fresh count, and so does
resuming a failed import. When the provider rate-limits the import, it waits as
long as the provider asks (`Retry-After` or the rate-limit reset, at most an
hour); other temporary failures wait with growing delays. After five attempts,
or at once when the provider refuses the token, the import fails, and a refused
token marks the source as **Connection failing** until a new token is saved.
Only one import of a source runs at a time.

```text
GET  /api/projects/{projectId}/imports                              (every member)
POST /api/projects/{projectId}/sources/{sourceId}/imports           (administrator; 202)
POST /api/projects/{projectId}/sources/{sourceId}/imports/resume    (administrator; 202)
```

Each import reports `status` (`PENDING`, `RUNNING`, `RETRY_SCHEDULED`,
`COMPLETED`, `PARTIAL`, or `FAILED`), `scannedCount`, `importedCount`, the
window, `lastSyncAt`, `lastErrorCode`, and `canResumeImport`. A polled source
reports `polled` and `nextPollAt`, and its latest job is its latest poll. Errors are
`409 source_token_missing`, `409 source_sync_in_progress`,
`409 source_import_not_resumable`, and `404 source_not_found`. See
[ADR-0016](adr/0016-integration-sources-and-history-import.md).

See also [ADR-0017](adr/0017-gitlab-sources.md) for the GitLab source and
[ADR-0018](adr/0018-linear-sources.md) for the Linear source, which has no
history to import, and [ADR-0019](adr/0019-jira-sources-and-linked-context.md)
for Jira polling and linked issues.

## What ReleaseFlow asks each provider

Connecting a GitHub or GitLab source makes no call at all; the repository is not
validated until a token is saved. After that, every call happens outside the webhook
request, in a background worker, or when an administrator saves a token:

| Provider | Calls |
| --- | --- |
| GitHub | Confirms a token against the repository's pull requests; lists a merged pull request's changed files and, when the Project has a Jira source, up to 250 of its commit messages; reads closed pull requests for a history import; reads and creates releases for a [GitHub Release action](automation.md#actions) |
| GitLab | Confirms a token against the project; lists a merge request's diffs and, when the Project has a Jira source, its commit messages; reads merged merge requests for a history import |
| Linear | Confirms the API key and learns the team's workspace when the team is connected; reads a completed issue back |
| Jira | Confirms the project when it is connected; searches for issues that moved into Done on every poll; reads the issues a code change mentions |

Timeouts, API addresses, and polling intervals are listed in
[Configuration](configuration.md#sources).
