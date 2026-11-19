# ADR-0018: Linear sources

- Status: Accepted
- Date: 2026-09-16
- Amended: 2026-11-19, for when a delivery is fresh and what a refusal logs; see
  [the amendment](#amendment-2026-11-19-when-a-linear-delivery-is-fresh)

## Context

ADR-0017 made GitLab the second source type behind two provider seams. Linear is
the third, and the first that is not a code host. That breaks three assumptions
the model had carried since V3:

- a change always has a merge commit and a target branch;
- a change without a file list is a change whose file list *failed to arrive*,
  which always forces review;
- every source type has a history ReleaseFlow can import.

Linear also inverts the credential flow: it mints the webhook signing secret
itself, and shows it once, so an administrator can only paste it in.

## Decision

- **One enrichment seam, not two half-fitting ones.** S13's
  `ChangedFileCollector` becomes `SourceEnricher`, whose `Enrichment` carries
  both the files a provider could list and the change with anything the provider
  restated. GitHub and GitLab list files and restate nothing; Linear lists no
  files and restates the issue. The worker classifies and prompts the AI from the
  restated change, so an issue edited between being completed and being read back
  is judged as it now reads.
- **A completed issue is a change.** Only `type: Issue` with `action: update`,
  an `updatedFrom` carrying a state, a new state of `completed`, and an old state
  that is not `completed` — a real transition into done, not an edit to something
  already done.
- **Linear's own number is the change's number.** `data.number` (the 123 in
  `ENG-123`) goes into `pull_request_number` and `data.id` into `external_id`.
  Release notes, previews, sorts, and the seeded audience templates therefore
  need no change at all: `#123` links to the issue and reads correctly.
- **A change records its source's type.** `changes.source_type` is tied to the
  source by the composite foreign key `(source_id, source_type)`, so it can never
  disagree, and `changes_commit_matches_type` then states the rule the model used
  to state with `NOT NULL`: a GitHub or GitLab change must have a merge commit
  and a target branch, and a Linear change must have neither.
- **Not supported is not unavailable.** `ChangedFileStatus.NOT_SUPPORTED` says
  the provider has no such notion, and the rules answer it with the keyword scan
  the old project used — `breaking change`, `migration`, `security`, `auth`,
  `credential`, `password`, `encryption`, matched as lowercase substrings of the
  title and description, one `SENSITIVE_KEYWORD` trigger per hit. A Linear change
  with none of them does **not** need review. `UNAVAILABLE` still forces it.
- **The secret is pasted, the token is required.** Linear generates the signing
  secret, so `POST .../sources` takes it write-only and never echoes it. The API
  key is required too, because confirming the team with GraphQL `team(id)` is
  also how ReleaseFlow learns the workspace it checks on every delivery. Both
  calls happen before anything is written, so `create` holds no transaction and
  each branch writes in one short transaction of its own.
- **A delivery proves itself with four things**: a `Linear-Delivery` header, a
  `createdAt` within 60 seconds of the clock (a numeric value above 1e10 is
  milliseconds), an `organizationId` equal to the stored workspace, and a hex
  HMAC-SHA256 of the raw body in `Linear-Signature`. Unlike the other verifiers
  this one reads the body, because Linear puts the timestamp and the workspace
  inside it; an unreadable body is one more way to fail, and every failure is the
  same `401`. (Amended on 2026-11-19: the stamp is `webhookTimestamp`, not `createdAt`,
  and the log names the proof that failed; see the end.)
- **No history, and the product says so.** `SourceType.supportsHistoryImport()`
  is false for Linear, the history registry demands a reader only for types where
  it is true, `SourceImportService` answers `409 source_import_not_supported`, and
  the Change Inbox does not list the source in its History import panel.

## Consequences

- An issue tracker and a code host can feed one Project, and both reach the same
  intake, classification, AI, review, and release notes.
- A Linear change has no changed files to show and no merge commit or branch, so
  the Change Inbox omits those fields rather than showing them empty.
- The keyword scan is deliberately weaker than the path rules. It is what a
  source that cannot report files allows, and it is never used for one that can.
- Linear's webhook secret cannot be rotated from ReleaseFlow: rotating it in
  Linear means reconnecting the source. Disconnecting and replacing a source is
  still deferred.
- Jira, the last planned source, will also be `NOT_SUPPORTED` for files and will
  reuse the keyword scan; it needs polling rather than a webhook, which is S15.

## Amendment, 2026-11-19: when a Linear delivery is fresh

- Status: Accepted
- Tracked by: [#94](https://github.com/hoangluongtran0309/ReleaseFlow/issues/94)
- Settles: the question the [2026-11-16 amendment of ADR-0042](0042-source-lifecycle-and-health.md#amendment-2026-11-16-linear-rotation-and-reconnect)
  left open

The decision above stands except for one of its four proofs, which time a delivery's
freshness is measured from, and for what a refusal leaves in the log.

**What was found.** The decision checks the body's `createdAt`. Linear's
[webhook documentation](https://linear.app/developers/webhooks) defines that field as
"the date and time that the action took place", and `webhookTimestamp` as the "UNIX
timestamp when the webhook was sent". It recommends checking the second: "verify it's
within a minute of the time your system sees it to guard against replay attacks". So the
check measures how long ago the issue changed, not how long ago the delivery was sent. A
delivery Linear sends more than 60 seconds after the action is refused, however fresh it
is, and it is counted as a signature failure.

On the dbvault lab, on 1.3.0-rc.1, on 2026-11-16, OPS-19 moved to Done at 14:52:10Z.
Sixty-five seconds later one delivery was refused and counted, and nothing was recorded.
ReleaseFlow keeps no body, so that the delivery carried OPS-19's completion is inferred
from the timing, not shown. Nothing recovers it. Linear sent no retry of a refused
delivery at the 1-minute, 1-hour, or 6-hour mark (ADR-0042's amendment), and Linear's
history cannot be imported. The issue is recorded only if it is completed again.

**Decision.**

- **Freshness is measured from `webhookTimestamp`.** A delivery is fresh when that stamp
  is within `RELEASEFLOW_LINEAR_WEBHOOK_CLOCK_SKEW` of ReleaseFlow's clock, either way, as
  `createdAt` had to be. It is read as the decision above reads a stamp: a number above
  1e10 is milliseconds, which is what Linear documents, a smaller number is seconds, and
  a string is an ISO-8601 instant.
- **`createdAt` is no longer one of the proofs.** It stays in the signed body, and
  nothing reads it to decide whether a delivery is accepted.
- **A delivery without a `webhookTimestamp` that can be read is refused**, as one
  without `createdAt` is today. There is no fallback to `createdAt`: a fallback would
  be the very check that refuses late deliveries, and Linear documents the field on
  every delivery.
- **The other three proofs stay as they are:** the `Linear-Delivery` header, an
  `organizationId` equal to the stored workspace, and a hex HMAC-SHA256 of the raw body
  in `Linear-Signature`. Every failure is still the same `401`, counted on the source.
- **The setting keeps its name and its default of 60 seconds**, which is Linear's own
  recommendation. It now says how far a delivery's `webhookTimestamp` may be from the
  clock.
- **The stamp is read leniently on purpose.** It is inside the signed body, so reading
  seconds or an ISO-8601 instant as well as milliseconds weakens nothing, while a wrong
  refusal is lost for good: no retry comes and nothing can be imported.
- **The server log names the proof that failed**: no `Linear-Delivery` header, no
  `Linear-Signature` header, a signature that does not match the body, a body that is not
  a JSON object, no `webhookTimestamp` that can be read, a `webhookTimestamp` outside the
  skew (with its offset from the clock in whole seconds), an `organizationId` that is not
  the source's workspace, or a stored secret that cannot be decrypted. A line names the
  source's ID and that reason, and never the body, a header's value, the secret, or the
  caller. The response stays the same `401`, and the source's page keeps its one count.
  That is how an administrator tells a skewed clock from a wrong secret.
- **At most one line per source and reason a minute.** A refusal inside the minute is
  only counted, and the next line for that source and reason says how many were left out
  since the one before. When refusals stop, a sweep every ten seconds writes that count
  once the minute is over, so it is never held back for long, and then forgets the entry.
  Memory is one entry per source and reason refused in the last two minutes, never one
  per request, and a source that is disconnected, or no longer refused, leaves nothing
  behind. Every stale stamp shares one reason, whatever its offset.
- **The signature is checked before anything the body says**, so a stale stamp or another
  workspace is reported only for a body the source's secret signed. The order changes no
  answer: every refusal is still the same.

**A replay is still refused.** `webhookTimestamp` is inside the signed body, so it
cannot be changed without the secret. A captured delivery sent again after the skew is
stale. One sent again within the skew carries the same issue, and intake records that
change once, as it does today.

**Consequences.**

- A first delivery that Linear sends late, but that arrives within 60 seconds of being
  sent, is accepted. That is most likely the loss the lab saw. It cannot be confirmed:
  the refused body was not kept, and Linear's delay cannot be caused on demand.
- A delivery refused for any other reason is still lost: a rotation gap, an outage, a
  restart, a deploy, or a proxy error. If Linear does retry such a delivery, the retry
  is a new send with its own `webhookTimestamp` and signature, so it would verify, and
  intake would keep one change. Nothing here relies on that, because no retry has been
  seen.
- Fewer good deliveries are counted as signature failures. A delivery that is stale by
  ReleaseFlow's clock is still counted, so a host clock more than 60 seconds off refuses
  every Linear delivery, as it did before.
- A flood on a known path writes at most nine lines a minute for its source, one per
  reason. An unknown path still writes none, as it records nothing.
- No migration, stored value, response, or setting name changes. The tests send a
  delivery whose `createdAt` is older than the skew and whose `webhookTimestamp` is
  fresh, which is accepted, and deliveries with a stale, missing, or altered
  `webhookTimestamp`, each refused, counted, and named in the log without the body or the
  secret. A test of the log on its own moves a clock to show the minute, the count left
  out, the sweep, the entries it forgets, and an exact count under concurrent refusals.
- `docs/sources.md` and its Vietnamese translation state the new proof and drop the
  known-issue note on the stale `createdAt`. `docs/configuration.md` describes the
  setting's new meaning. ADR-0042's open question is marked settled here.
- Before the release, the lab confirms that a real Linear delivery carries
  `webhookTimestamp` in milliseconds and verifies. #94 also asked to confirm what Linear
  sends on a retry. That check is dropped: no retry came.
