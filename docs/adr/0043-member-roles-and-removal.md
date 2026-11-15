# ADR-0043: Changing a member's role and removing a member

- Status: Accepted
- Date: 2026-11-12
- Amends: [ADR-0006](0006-organization-roles-and-invitations.md), which provided neither
  and left ending a changed member's sessions to a later decision
- Refers to: [ADR-0034](0034-account-language-per-request.md), whose per-request account
  read is the precedent for the one this adds

## Context

An Organization can invite people but cannot change anyone's role or remove anyone. The
members API has members and invitations only, and `docs/implementation-status.md` lists
both as deferred. A team that adopts ReleaseFlow cannot offboard someone who leaves.

Four facts of the current model decide most of what follows:

- **An account is one membership.** `app_users` has one `organization_id`, and its email
  is unique across the deployment (`app_users_email_unique`, ADR-0006: "an email that
  already has an account cannot be invited"). No account belongs to two Organizations
  today: the same person in two Organizations would need two addresses. So the row *is*
  the membership, and there is no separate account to keep.
- **The row cannot go.** Twenty-one composite foreign keys point at
  `app_users (id, organization_id)` — reviews, approvals, publications, note edits,
  exclusions, category and duplicate decisions, glossary terms, Project context, sensitive
  paths, imports, automation runs, accepted statuses at review, fold decisions,
  invitations, and the source a person disconnected — and none cascades. Every one of
  them but three also keeps the person's name as it was (`reviewer_name`,
  `approver_name`, `publisher_name`, `decider_name`, `accepter_name`, and the rest), so a
  page that names a past act reads the snapshot, not the account. The three without a
  snapshot are an invitation's creator and acceptor and a source's `disconnected_by`; the
  Members page reads the creator's name from the row.
- **A session is a snapshot.** Sign-in stores a `ReleaseFlowPrincipal` — user,
  Organization, name, email, and role — in the HTTP session, in the application's memory.
  Every authenticated request, the API's included, is authorised from that snapshot.
  ADR-0006 already noted that a role change would leave the snapshot stale.
- **The session is the only way to act as a member.** Checked in the code as it stands:
  form sign-in is the only authentication; there is no remember-me cookie, no API token,
  no basic or bearer authentication, and no streaming, asynchronous, or WebSocket endpoint
  that outlives the request it answers. The other three filter chains — `/webhooks/**`,
  `/changelog/**`, and the management port — are stateless and authenticate no member: a
  webhook proves a source's or a rule's secret, and the changelog and health are
  anonymous.
- **Invitations carry no role.** Only an administrator invites, and accepting an invitation
  always makes a `MEMBER` (ADR-0006).
- **Two administrators can act at once.** Under `READ COMMITTED`, a check "is another
  administrator left?" made by two transactions that each demote the other passes in both,
  and both commit: the Organization is left with none.

## Decision

### The two operations

- **Change a role.** `PUT /api/members/{memberId}/role {"role": "ADMIN" | "MEMBER"}`
  answers the member's view; on the Members page, a role select and **Change role** beside
  each member. Setting the role a member already has changes nothing and records nothing.
  Demoting an administrator also revokes the pending invitations they made, in the same
  transaction: only administrators invite, so an invitation the person sent would otherwise
  outlive the decision that took that power away. Promoting revokes nothing.
- **Remove a member.** `DELETE /api/members/{memberId}` answers `204`; on the Members page,
  **Remove** opens a confirmation in place that says what removal does, and a second
  button does it.
- **Administrators only**, by URL rule (`/members/**` and `/api/members/**`) and again in
  the service, as for invitations. A member of another Organization, a removed member, and
  an unknown ID are all `404 member_not_found`: a lookup always carries the principal's
  Organization.
- **An administrator may change or remove themselves**, as long as another administrator
  remains. Their own session then ends like anyone's.
- **Every failure has a code** and is written in both interface languages:
  `404 member_not_found`, `409 member_last_administrator`, and `400 validation_failed` for
  a role that is neither value.

### The last administrator, in the database

- **The service locks first, then counts.** Both operations start by taking
  `SELECT … FROM organizations WHERE id = ? FOR UPDATE` on the principal's Organization,
  then count its active administrators, and refuse with `409 member_last_administrator`
  when the change would leave none. The lock serialises every role change and removal of
  one Organization, so the second of two concurrent changes counts after the first has
  committed.
- **A deferred constraint trigger is the backstop.** `app_users_keep_an_administrator` is
  a `CONSTRAINT TRIGGER … AFTER UPDATE … DEFERRABLE INITIALLY DEFERRED FOR EACH ROW`,
  firing only when `role` or `removed_at` actually changed. At commit it takes the same
  Organization row lock, then checks, in a statement whose snapshot is taken after the lock
  is granted, that an active administrator remains, and raises otherwise. Two transactions
  that bypass the service, or a code path that forgets the lock, still cannot both commit:
  the second waits for the first's lock, then sees its commit and fails. The service maps
  that failure to the same `409`. Rows are never deleted by the product, so the trigger
  watches updates only, and an Organization is created with its administrator in one
  transaction.

### What removal does

- **The row stays, as the record of who the person was there.** Removal sets
  `removed_at` and `removed_by` and keeps the row's ID, name, email, role at removal, and
  Organization, so every foreign key and every name in the history still resolves. It
  clears `password_hash` (nullable from now on, and null exactly when removed), so nothing
  that could sign in is left.
- **The address is free again.** `app_users_email_unique` becomes a unique index over
  members who are not removed. A removed person can therefore register an Organization of
  their own, accept another Organization's invitation, or be invited back.
- **A person invited back is a new member.** Accepting the invitation creates a new row
  with a new ID, role `MEMBER`, and the name they give then. Everything they did before
  stays with the removed member, under the name recorded at the time, and the two are not
  joined: the Members page lists the new member, and the removed one under removed
  members. Sign-in, registration, and
  invitation acceptance look up members who are not removed. Making one account belong
  to several Organizations would split the account from the membership; that is a larger
  change, and this ADR does not make it.
- **Their pending invitations are revoked** in the same transaction: an offer to join should
  not outlive the person who made it, and a leaving administrator's last invitation is
  exactly the access an offboarding is meant to close. The remaining administrators can
  invite the same address again.
- **Nothing else is theirs to take away.** Everything a member made in ReleaseFlow —
  releases, reviews, notes, rules, glossary terms, imports under way — belongs to the
  Organization and stays as it is. No rule runs as a person, and no job stops because its
  requester left.

### Ending sessions, on every request path

- **Each account carries a session version.** `app_users.session_version` starts at zero
  and goes up by one with every role change and with removal, in the same transaction.
  Sign-in copies it into the principal.
- **Every authenticated request compares it.** `MembershipSessionFilter` runs in the main
  filter chain after the security context is loaded and before authorisation. For a
  `ReleaseFlowPrincipal` it reads `session_version` and `removed_at` of the row by ID and
  Organization — one indexed read — and, when the row is removed or its version is not the
  principal's, invalidates the HTTP session and clears the context. The request then
  continues as anonymous, so the existing entry points answer it: `401
  authentication_required` as Problem Details for anything under `/api`, and a redirect to
  the sign-in page for a page, which says the person's access changed.
- **Sessions are not found; each one is refused the next time it is used.** The filter
  sees every request a session can make, the API's included, so no session of a changed
  member is honoured again, whichever browser or client holds it and however many there
  are. A session nobody uses again holds no authority and expires with the session
  timeout. A session registry was considered and set aside: it would only free memory
  sooner, it works only within one instance, and it would still need this check for the
  requests already past it.
- **A demoted member signs in again** and gets a session with the new role; a removed one
  cannot sign in. A request already being served when a change commits finishes with the
  authority it started with; the one after it is refused.
- **Secrets a person saw are not theirs, and the filter cannot see them.** A removed or
  demoted administrator may have seen a source's webhook secret, an automation rule's call
  secret, or an access token they typed. Those prove a source or a rule, not a member, so
  ending sessions does not stop them. The Members page and the guide say so at removal and
  demotion, and point to rotating each one: a source's secret (ADR-0042), a rule's secret
  (ADR-0021), and the token at its provider. Nothing tracks which secrets a person saw,
  because ReleaseFlow shows a secret once and keeps no record of who looked.

### Who changed what, and when

- **A history table.** `membership_changes` records each change: the Organization, the
  member, the kind (`ROLE_CHANGED` or `REMOVED`), the role before and the role after (none
  after a removal), the number of invitations the change revoked, who made the change — a
  composite foreign key and the name they had then — and when. Rows are only ever
  inserted.
- **Shown on the Members page**: removed members, each with who removed them and when, and
  the Organization's recent membership changes. `GET /api/members/changes` returns the same
  history, newest first.

### Cost

- No AI request, no DeepL request, and no call to any provider. Each operation is one
  short transaction. Every authenticated request costs one indexed read more, which
  ADR-0034 accepted for the same kind of freshness.

### Schema

- `V38` adds `removed_at`, `removed_by` (composite foreign key), and `session_version` to
  `app_users`, makes `password_hash` nullable with a check tying it to `removed_at`,
  replaces `app_users_email_unique` with a unique index over members not removed, adds
  `membership_changes`, and creates the constraint trigger. No existing row changes.

## Consequences

- An Organization can offboard a person without losing a word of what they did, and the
  person's sessions stop on their next request, through the pages and the API alike.
- An Organization always keeps an administrator, even under concurrent changes and even
  against a write that skips the service.
- Every authenticated request reads one row more.
- A person who is removed and invited back is a new member: their earlier acts keep the
  name and the ID they had.
- Offboarding an administrator who handled secrets also means rotating them; the page
  says which kinds, and ReleaseFlow cannot say which ones.

## Not decided here

- **An account that spans several Organizations.** One account is one membership, as
  today; splitting the account from its memberships, so one sign-in reaches several
  Organizations, is a decision of its own.
- Transferring what a person made to someone else, and erasing a person's name from the
  history.
- Rotation reminders for the secrets a removed administrator may have seen.
