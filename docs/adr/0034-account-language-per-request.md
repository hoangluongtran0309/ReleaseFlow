# ADR-0034: The account's interface language is read on each request

- Status: Accepted
- Date: 2026-10-23
- Amends: [ADR-0027](0027-ui-localization.md), which read it from a snapshot in the session

## Context

ADR-0027 put the account's language in `ReleaseFlowPrincipal`, a snapshot taken when the
session began, so that resolving a locale would never cost a query. A change made in the
same browser shows at once, because it also writes the `releaseflow_lang` cookie, which
outranks the account. A change made anywhere else did not: a choice saved through
`PUT /api/me/ui-locale`, or from a second browser, reached a session that was already
signed in only when that session signed in again, although the guide describes the
language as picked per request.

## Decision

- The account step reads `app_users.ui_locale` as it is now: one column, by primary key,
  through `UiLocaleService`. The principal no longer carries the language.
- It runs only when neither `?lang=` nor the cookie answered, for a signed-in person, and
  at most once per request: Spring MVC resolves the locale only when something asks for
  it, and the resolver keeps its answer on the request. A static resource, a webhook, and
  a public changelog page never reach it.
- Everything else in ADR-0027 stands: the order of the steps, the cookie that a change
  writes and that outranks the account, and `?lang=` never touching the account.

## Consequences

- A language chosen for the account reaches every session of that person on its next
  request, whatever chose it.
- A signed-in page whose browser has no cookie costs one indexed read more. The browser
  that made a choice has the cookie and skips it.
