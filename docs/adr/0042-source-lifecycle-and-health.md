# ADR-0042: Disconnecting, reconnecting, and rotating a source, and showing its health

- Status: Accepted
- Date: 2026-11-11
- Amends: [ADR-0002](0002-per-integration-webhook-credentials.md), whose secret rotation and
  replacement were left to a later workflow; [ADR-0016](0016-integration-sources-and-history-import.md),
  for when a repository belongs to one Project and when an import may start;
  [ADR-0017](0017-gitlab-sources.md) and [ADR-0018](0018-linear-sources.md), for rotating the
  secret each one was connected with; and [ADR-0019](0019-jira-sources-and-linked-context.md),
  for when a poll is booked and whose account a Jira token belongs to
- Refers to: [ADR-0020](0020-automation-rules-and-runs.md), whose GitHub Release action
  borrows a source's token

## Context

A source stays connected for good. `docs/implementation-status.md` defers disconnecting or
replacing one, rotating a Linear signing secret without reconnecting, and rotation
reminders, and ADR-0002 left secret rotation and replacement to "explicit future
workflows". A team that moves a repository, loses a token, or suspects a leaked webhook
secret can only create a new Project, and loses the old one's history in the process.

It also cannot tell when a source has gone quiet. On 2026-11-01 the dbvault lab's webhooks
pointed at a dead tunnel address from about 02:30 to 15:40 UTC, about 13 hours, and
ReleaseFlow showed nothing: the Projects page has each source's last verified delivery,
but nothing marks it as old, nothing outside that page shows it, and a delivery whose
signature fails is answered `401 webhook_signature_invalid` and recorded nowhere. With a
dead address nothing arrives at all, so silence is the only symptom ReleaseFlow can see.

Four facts of the current model decide most of what follows:

- **A change is identified by its source.** `ChangeIntake` refuses a change whose
  `(source_id, external_id)` it already holds, and `changes_source_external_unique` makes
  that the rule under concurrency too. A new source ID for the same repository is
  therefore a new identity: re-running the 90-day import on it records every pull request
  the old source already recorded, a second time.
- **Changes point at their source.** `changes_source_type_matches` is a foreign key to
  `integration_sources (id, source_type)`, so deleting a source would orphan or delete its
  changes, the releases that contain them, and the folding and duplicate evidence about
  them.
- **Ciphertexts are bound to their source.** A webhook secret's authenticated data is the
  Organization, Project, and source IDs with the repository owner and name for GitHub, or
  the type and external key for the others; a token's adds a purpose line. Rewriting any
  of those columns makes the secret and the token stop decrypting. `webhook_id`,
  `webhook_auth_mode`, `api_base_url`, `external_workspace_key`, `credential_identity`,
  and the schedule are not bound.
- **The token already rotates.** `PUT /api/projects/{projectId}/sources/{sourceId}/token`
  has the provider confirm a new token before it replaces the old one, for all four
  types, and puts a failing source back in order. What is missing is the rest of the
  lifecycle around it.

The providers set the limits of a rotation, because a hook is edited by a person, by hand,
after ReleaseFlow has already changed its side:

- GitHub does not redeliver a refused delivery on its own. A person can redeliver one from
  the hook's **Recent deliveries** for three days.
- GitLab lists a hook's **Recent events** for two days, each with **Resend Request**, and
  disables a hook that fails four times in a row, for a minute at first and up to a day,
  and for good after 40 failures.
- GitLab clears a hook's **secret token** (`token`, the `X-Gitlab-Token` mode) when its
  URL changes, a security fix of its own
  ([gitlab-org/gitlab#382260](https://gitlab.com/gitlab-org/gitlab/-/issues/382260); the
  warning in the form came with
  [!139865](https://gitlab.com/gitlab-org/gitlab/-/merge_requests/139865)). Its **signing
  token** behaved differently on the dbvault lab: a URL-only edit through the hooks API on
  2026-10-20 left it in place, and the hook still reported `signing_token_present: true`
  when it was next read, on 2026-11-01.
- GitLab's signing token is set on the lab through the hooks API, `url` and
  `signing_token` in one `PUT`, in the Standard Webhooks form `whsec_` followed by the
  standard Base64 of ReleaseFlow's secret; GitLab refuses the secret as ReleaseFlow writes
  it (lab TESTLOG SRC-6, FINDINGS #1). Its documentation describes the form's **Generate
  signing token**, which makes a token in GitLab instead, so what the form accepts typed
  in is not something to depend on.
- Linear retries a refused delivery three times, the last about six hours later. Its
  documentation shows the signing secret on the webhook's detail page and says nothing of
  regenerating it.

## Decision

### Disconnecting

- **An administrator disconnects a source; its row stays.** `POST
  /api/projects/{projectId}/sources/{sourceId}/disconnect` writes `disconnected_at` and
  `disconnected_by` in one short transaction. The row, its type, its external key, and
  every bound column are kept, so every change it recorded keeps its source, its
  history, its reviews, its releases, and its evidence, and the Change Inbox still filters
  by it. Nothing is called at the provider: ReleaseFlow cannot delete a GitHub hook or
  revoke a token it was only lent, so the confirmation page and the guide say to delete
  the hook and revoke the token there.
- **What it destroys.** The same transaction sets `webhook_id`, `secret_nonce`,
  `secret_ciphertext`, `token_nonce`, `token_ciphertext`, and `next_poll_at` to null. The
  live row then holds nothing that decrypts to a credential; older row versions and
  backups still hold the ciphertexts until vacuum and backup expiry remove them, which is
  why the provider-side revocation is part of the guide rather than an optional extra.
- **Its webhook path answers like an unknown one.** With no `webhook_id`, the verifier
  finds no source, so a delivery is answered `401 webhook_signature_invalid`, exactly as
  for a path that never existed, and is recorded nowhere: not as a delivery and not as a
  signature failure.
- **Polls and imports stop.** With no `next_poll_at`, `JiraPollScheduler` never books a
  poll. The same transaction ends the source's pending or retrying sync job as `FAILED`
  with `source_disconnected`. A job already running reads its credentials before each
  provider call, finds none, and ends the same way, so no page is read after the
  disconnect. A page fetched just before it may still be recorded.
- **Work that needed the token behaves as if the source had none.** A change still being
  processed is completed with its files unavailable (`NO_ACCESS_TOKEN`), which forces
  review, as for any source without a token. The lookups of other capabilities —
  `SourceAccess.findByType` for linked context and issue reads at review, `findSole` for
  the GitHub Release action, the code host behind a note's compare and release links, and
  the overview's "a source is connected" — count connected sources only. A linked issue
  whose tracker is disconnected keeps its recorded status, as an issue that could not be
  read already does (ADR-0041).
- **Disconnecting is final for that connection, and refused twice.** Disconnecting a
  disconnected source, or setting its token or secret, or importing into it, answers
  `409 source_disconnected`.

### Reconnecting

- **The same repository, project, or team in the same Project is the same source.** `POST
  .../sources/{sourceId}/reconnect` brings a disconnected source back under its own ID, so
  `ChangeIntake` keeps recognising every change it recorded: a history import after the
  reconnect records only what is new, and a redelivery of an old pull request is a
  `duplicate`. `ChangeIntake` and `changes_source_external_unique` do not change.
- **Reconnecting never changes what the source is.** Type, external key, instance,
  workspace, and Project stay as they were, so no bound column is touched; the new
  credentials are encrypted afresh under the same authenticated data. What a reconnect
  takes is what connecting that type takes, minus what identifies it:

  | Type | Reconnect takes | ReleaseFlow makes | Provider calls |
  | --- | --- | --- | --- |
  | GitHub | an optional token | a new webhook ID and secret, shown once | one token check, only if a token is given |
  | GitLab | an optional token, and for a signed source an optional signing token GitLab generated | a new webhook ID, and a secret shown once in the form its mode needs unless one was pasted | one project check, only if a token is given |
  | Linear | the pasted signing secret and an API key | a new webhook ID | one `team(id)` query, which must still answer the stored workspace |
  | Jira | the account email and an API token | the next poll, one interval later | one project check |

  A new webhook ID means the old path stays dead: a hook nobody updated cannot start
  delivering again, and a hook pointed at the new path proves itself with the new secret.
- **A Jira source picks up where it stopped.** Its cursor stays where its last completed
  poll left it, moved forward to no earlier than 90 days ago, the window an import reads.
  The first poll after a reconnect therefore reads what was completed while it was
  disconnected, and intake drops what it already holds.
- **Connecting the key again is refused in favour of reconnecting.** Connecting a
  repository, project, or team that the same Project holds disconnected answers
  `409 source_reconnect_required` and names the source, rather than creating a second
  identity for it. `integration_sources_external_unique` becomes a unique index over
  connected sources only, beside a full unique index on Organization, Project, type, and
  key, so a key is connected in at most one Project of the Organization and held at most
  once by each Project.
- **Another Project may take the key.** Once a source is disconnected, a different
  Project may connect the same repository. That is a new source with its own ID: the old
  Project keeps its changes, and the new one starts its own record, which its own import
  may fill. The two Projects do not share changes, because a change belongs to one
  Project.
- **A renamed or moved repository is a new source.** ReleaseFlow cannot prove that two
  names are one repository without following a redirect, which no client does, and
  treating them as one on a person's word would silently drop real changes of an
  unrelated repository with the same numbers. Replacing a source therefore means
  disconnecting it and connecting the new name; that new source's import records again
  what the old one recorded under the old name, as new changes, which the possible
  duplicate check (ADR-0013) and exclusion (ADR-0033) are there to handle. A renamed
  GitHub repository already announces itself: its deliveries are refused as
  `webhook_repository_mismatch`, which never counts as a delivery, so the source turns
  quiet below.

### Rotating a webhook secret

- **The path stays; the old secret stops at once.** Two ways in, by who makes the secret:
  - `POST .../sources/{sourceId}/webhook-secret` generates one, shown once in a `no-store`
    response: GitHub, and GitLab in either mode. For a signed GitLab source the response
    also carries it as `signingToken`, in the `whsec_` form GitLab's API takes, with the
    request to send it.
  - `PUT .../webhook-secret {"webhookSecret"}` stores one the provider made, never echoed:
    Linear's signing secret, or a signing token GitLab generated for a signed GitLab
    source, which must be `whsec_` followed by Base64 or it is refused with `400`.
  - A secret-token GitLab source and a GitHub source only generate, and answer a `PUT`
    with `409 source_secret_generated`; Linear only pastes, and answers a `POST` with
    `409 source_secret_pasted`; Jira has no secret and answers both with
    `409 source_has_no_webhook`.

  Either way the new ciphertext replaces the old under the same authenticated data,
  `secret_updated_at` is set, and the next delivery signed with the old secret is
  refused. ReleaseFlow does not depend on which of GitLab's forms accepts a typed value:
  both ways reach the same verifier, which already reads a `whsec_` secret.
- **Why at once, not after an overlap.**
  - A secret is rotated because someone else may know it. During an overlap, whoever
    holds the old one can still put a merged pull request of their choosing into the
    Change Inbox. A delivery that is missed can be recovered; a forged one is
    indistinguishable from a real one once it is in.
  - A missed delivery is recoverable and, with this ADR, visible. The refusal is counted
    with its time (below), so the gap is on the page. GitHub keeps the refused delivery
    for three days and GitLab for two, each with a button to send it again; and the
    90-day import of either reads the gap through the same intake and records nothing
    twice.
  - An overlap needs a second secret per source, an expiry, and verifiers that try two,
    and a timed overlap only moves the problem: a person who takes longer than the
    window loses deliveries anyway, and one who never updates the hook leaves the old
    secret alive until it expires.
  - It is the rule a signed automation trigger already follows (ADR-0021), so a person
    meets one behaviour.
- **What the page says, for each provider.** The page that shows the new secret says
  exactly where it goes and in which order, and the guide repeats it:
  - **GitHub:** the repository's **Settings → Webhooks →** the hook **→ Edit**, paste into
    **Secret**, **Update webhook**; the Payload URL does not change. Then **Recent
    deliveries**, and **Redeliver** each delivery refused since the rotation.
  - **GitLab, signed headers:** either send ReleaseFlow's value with the request the page
    shows, `PUT /api/v4/projects/<project>/hooks/<hook>` with `url` and `signing_token`
    together, or select **Generate signing token** on the hook in GitLab and paste what
    GitLab shows once into ReleaseFlow.
  - **GitLab, secret token header:** the project's **Settings → Webhooks →** the hook
    **→ Edit**, paste into **Secret token**, and save; through the hooks API, `url` and
    `token` in one `PUT`.
  - **GitLab, either mode:** GitLab clears the secret token whenever the URL changes, and
    the lab saw the signing token kept, but the guide does not lean on the difference:
    whenever the URL is edited — a new tunnel address, say — the token is entered again in
    the same save. A token cleared anyway can only be replaced by rotating again, because
    ReleaseFlow showed it once. Then **Recent events**, **Resend Request** for each refused
    event, and, if four refusals in a row made GitLab disable the hook, enable it again.
  - **Linear:** because Linear documents no way to regenerate a signing secret, the page
    says to create a second webhook in Linear with the same URL and the **Issues** event,
    copy its signing secret from its detail page, paste it into ReleaseFlow, and then
    delete the old webhook. While both exist every event is sent twice, at least one copy
    verifies before and after the paste, and intake keeps one, so nothing is missed; the
    copies that fail are counted, and Linear's own retries cover a slow paste.
- **The rotation itself calls no provider.** A generated secret is made locally, and a
  pasted one cannot be confirmed by anyone but the next delivery. ReleaseFlow never sends
  the hooks API request itself: it holds no token that may edit a hook.

### Rotating a token

- **The existing replacement is the rotation, for all four types.** It keeps the source,
  its path, and its secret; the provider confirms the new token first; the old one is
  never used again once the new one is stored, though a call already under way finishes
  with it. ReleaseFlow cannot revoke the old token, so the form and the guide say to
  revoke it at the provider once the new one is saved.
- **A Jira token may change account.** A Jira token belongs to the account it was made
  by, and that person may leave. `PUT .../token` therefore takes an optional
  `accountEmail` for a Jira source, confirmed together with the token by the same one
  project check, and written to `credential_identity`, which no ciphertext is bound to.
- **No reminders.** Nothing reminds anyone to rotate. The page already shows when the
  token and now the secret were last set, and reminders stay deferred.

### The GitHub Release action

- **A disconnected source lends nothing.** `findSole` counts connected GitHub sources
  only, so enabling a rule with a GitHub Release action is refused as
  `automation_github_source_missing` while the Project has none connected, as it is now
  when the Project has none at all.
- **A run says which repository it was for, and why it failed.** A run records the
  repository of the Project's one connected GitHub source; when none is connected but
  exactly one disconnected GitHub source remains, it records that one's. When the
  recorded repository's source is disconnected at the time the action runs, the action
  fails as `FAILED` with `github_source_disconnected` before any call — nothing was
  written, so it is not `UNKNOWN` — and the run stops there, as after any failed action.
  Reconnecting the same repository keeps its name, so retrying that action then
  succeeds without a new run.
- **Disconnecting does not disable rules.** A suspected leak must be stoppable at once,
  and disabling a rule cancels runs whose other actions have nothing to do with GitHub.
  Instead, the disconnect confirmation lists the enabled rules of that Project with a
  GitHub Release action, and the Automation page marks each such rule until the Project
  has one connected GitHub source again.

### Source health

- **Recorded on the source, never in a log of requests.** Three columns:
  `secret_updated_at`, `signature_failure_count`, and `last_signature_failure_at`.
  `last_delivery_at` already exists and keeps its meaning: the last delivery that proved
  itself and was accepted.
- **A signature failure is counted, nothing more.** When a path resolves to a connected
  source of the right type and its proof fails — a missing or wrong signature, a stale
  stamp, another workspace — the failure is added to the source's count and its time
  noted, after the verifier has answered and outside any transaction of the request. The payload, the
  headers, the secret, the caller's address, and the reason are not stored or logged.
  The answer is the same `401` as before, so a refusal still says nothing about what
  exists, and an unknown or disconnected path records nothing, because there is no
  source to record it on. The count restarts at zero whenever the secret is set, by
  connecting, rotating, or reconnecting, so it reads as "refused since this secret". A
  body over the limit is refused before any source is looked up and is not counted. A
  caller who knows a path can raise its count; it is evidence on a page, writes no new
  row, and never changes what is accepted.
- **A flood is bounded by coalescing the write.** ReleaseFlow has no inbound rate limit:
  a webhook request is bounded only by the 1 MiB body limit, checked first, and by
  whatever the deployment's proxy enforces. So the count is kept exact without one write
  per request. The first failure on a source writes at once; later ones add to a pending
  count held in memory for that source, and a drain once a second writes each pending
  count in one `UPDATE` (`count = count + pending`, the later time) — at most about one
  `UPDATE` per source per second, however many forged deliveries arrive. The pending map
  holds an entry only for a source that resolved, so it is as large as the sources being
  refused, never as large as the requests. Several instances each coalesce their own, and
  the additions still sum exactly. Each pending count names the secret it was refused
  under, and its `UPDATE` applies only while `secret_updated_at` is unchanged, so failures
  against a secret just replaced never land on the new one's count. A drain runs on
  shutdown; only a process that dies outright loses what it had not written, at most
  about a second of failures. The request's own cost — one lookup by webhook ID, one
  decryption, one HMAC — is what it is today; a deployment that expects floods limits
  `/webhooks/**` at its proxy, as the operations guide will say.
- **A quiet source is flagged against its own rhythm.** At read time, from the changes
  that source delivered by webhook in the last 90 days, ReleaseFlow takes the gaps between
  consecutive arrivals and their 90th percentile, with a floor of one hour. A connected,
  delivered source is **quiet** when its last delivery, or its secret's last setting if it
  has had none since, is older than that. Fewer than ten gaps are not enough to judge,
  and then nothing is flagged. Each source is measured alone: a dead address silences
  every source behind it at once, so the busy ones show it, and a rarely used source in a
  busy Project is not flagged for being rare. Every accepted delivery
  moves `last_delivery_at`, and a hook sends many more events than merges, so comparing
  any delivery with the gaps between merges leans towards not flagging. The flag states
  its figures — quiet for 14 hours, where this source usually delivers within 6 — so a
  person can judge it. It is evidence on a page: no job computes it, nothing is sent
  anywhere, and it never stops a source, a release, or a rule.
- **A polled source has its own health.** A Jira poll succeeds when nothing was done, so
  its silence means nothing; it keeps its last and next read and its error, and is
  marked **overdue** when its next read is more than one poll interval in the past.
- **Shown where people look.** The Change Inbox's Source sync panel lists every source of
  the Project — today it lists only polled and importable ones, so Linear is missing —
  each with its last delivery or read, its signature failures since the secret was set
  with the time of the last one, and the quiet or overdue flag; a disconnected source is
  listed as disconnected, with its time. The overview shows the selected Project's
  sources the same way, compactly, beside the figures it already has: it is information,
  not a step, so the one next step and the order that picks it do not change. The
  Projects page shows the same, and the API's source view carries the same fields.

### The binding invariant

- No action in this ADR rewrites `organization_id`, `project_id`, `id`, `source_type`,
  `external_project_key`, `repository_owner`, or `repository_name`. Rotation and reconnect
  write a new nonce and ciphertext under the same authenticated data; disconnect writes
  nulls. A different repository is a different source, never an edited one. The mapping
  marks every bound column as not updatable, and a test proves that each action leaves
  them as they were and that the stored secret and token still decrypt.

### Cost

- **No AI and no DeepL request** comes from any action here: disconnecting,
  reconnecting, rotating, counting, and flagging classify and translate nothing. A change
  recorded for the first time after a reconnect — by a delivery, a poll, or an import a
  person starts — is processed like any new change, AI included when it is configured; a
  change already recorded is a `duplicate` and costs nothing.
- **Provider requests:** disconnecting, rotating a secret, counting a failure, and
  computing health call no provider; nor does ReleaseFlow send the GitLab hooks request
  it shows. Reconnecting and rotating a token make the one
  confirmation call in the table above (none for GitHub or GitLab without a token). A
  reconnected Jira source's first poll reads, page by page, what was completed while it
  was disconnected, within 90 days.

### Schema

- `V37` adds `disconnected_at`, `disconnected_by`, `secret_updated_at` (set to
  `created_at` for existing rows), `signature_failure_count` (zero), and
  `last_signature_failure_at`; replaces `integration_sources_external_unique` with the two
  indexes above; and restates the presence checks so that a connected source has exactly
  what its type needs, as now, and a disconnected one has no webhook ID, secret, token,
  or next poll. No existing row changes, so every ciphertext still decrypts.

## Consequences

- A source can be disconnected in an emergency without losing a single change, and
  brought back without duplicating any: the same ID is what keeps `ChangeIntake` honest.
- A leaked webhook secret can be replaced in place, with the path unchanged. The price is
  a gap between ReleaseFlow's save and the provider's, which the page shows and each
  provider's own resend, or the import, fills.
- A source that stops delivering is visible within about one of its usual gaps,
  wherever people look, and a hook pointed at ReleaseFlow with the wrong secret is
  visible at once. Neither is an alert: nothing is sent, and a source with no rhythm yet
  is never flagged. A source whose overnight gaps are long flags a dead address only
  after an unusually long silence, and a source with fewer than ten delivered changes in
  90 days is never flagged at all.
- A repository that was renamed is a new source whose import repeats the old one's
  changes; ReleaseFlow does not guess that two names are one repository.
- A flood of refused deliveries on a known path costs about one row update per source per
  second, and an exact count. Rotation reminders, alerts on
  a quiet source, and deleting a source with its changes remain deferred.
