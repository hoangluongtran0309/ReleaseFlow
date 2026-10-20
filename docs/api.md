# REST API

The REST API does everything the pages do, through the same application services. This
page covers the conventions and lists every endpoint; each guide linked below describes
its endpoints' bodies and behavior in full.

## Sessions and CSRF

REST clients use the same session and CSRF policy as the server-rendered pages. Every
write needs a CSRF token, and CSRF protection is never turned off: the only paths excused
are the signed webhook endpoints under `/webhooks/`, which carry no cookie and prove
themselves with an HMAC over the request instead. See
[ADR-0023](adr/0023-scoped-csrf-exemptions.md).

There are no API tokens: a script signs in as a person, with that person's email and
password, and keeps the session cookie.

1. `GET /api/csrf` and keep the session cookie it sets. It answers
   `{"headerName": "X-CSRF-TOKEN", "parameterName": "_csrf", "token"}`.
2. Send the token in that header with every `POST`, `PUT`, and `DELETE`, for example
   `POST /api/registrations`.
3. Sign in through `POST /login` with `email` and `password` form fields and the token.
   It answers with a redirect, never JSON: to `/` when it worked, and to `/login?error`
   when it did not.
4. **Fetch a new token.** Signing in replaces the session and its token, so the token
   from step 1 is now refused with `403 access_denied`.
5. Read the signed-in identity from `GET /api/session`.

With `curl` and `jq`:

```bash
BASE=http://localhost:8080
JAR=$(mktemp)
csrf() { curl -s -c "$JAR" -b "$JAR" "$BASE/api/csrf" | jq -r .token; }

curl -s -c "$JAR" -b "$JAR" -o /dev/null -w '%{redirect_url}\n' \
  -H "X-CSRF-TOKEN: $(csrf)" \
  --data-urlencode "email=$RELEASEFLOW_EMAIL" \
  --data-urlencode "password=$RELEASEFLOW_PASSWORD" \
  "$BASE/login"            # prints $BASE/ when it worked, $BASE/login?error otherwise

TOKEN=$(csrf)               # the signed-in session's token
curl -s -b "$JAR" "$BASE/api/session"
curl -s -b "$JAR" -c "$JAR" -H "X-CSRF-TOKEN: $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name": "Website"}' "$BASE/api/projects"
```

A session ends after 30 minutes without a request, and when the application restarts;
after that, every call answers `401 authentication_required` until the script signs in
again. `POST /logout` ends it at once. Set `RELEASEFLOW_SESSION_COOKIE_SECURE=true`
whenever the application is served over HTTPS.

The Organization always comes from the signed-in person, never from a request body. A
resource of another Organization is reported as not found, never as forbidden.

## Errors

Every failure under `/api` is `application/problem+json` with a stable `code`:

```json
{
  "title": "Release not found",
  "status": 404,
  "detail": "…",
  "code": "release_not_found"
}
```

`title` and `detail` are written in the reader's [interface
language](workspace.md#interface-language); **`code` never changes**, so match on it.
A validation failure adds `errors`, a map from field to message.

| Status | Codes that can come from any endpoint |
| --- | --- |
| `400` | `validation_failed` (a field is missing or out of range), `malformed_request` (the body cannot be read), `invalid_parameter` (a path or query parameter is missing or does not convert, such as an ID that is not a UUID) |
| `401` | `authentication_required` (no session) |
| `403` | `access_denied` (an administrator endpoint, or a missing or stale CSRF token) |
| `404` | `not_found` (no such address) |
| `405` | `method_not_allowed` (the address does not take that method) |
| `415` | `unsupported_media_type` (the address does not read a body of that type) |
| `500` | `internal_error` (a fault ReleaseFlow did not foresee; the body never carries the exception behind it) |

A status no endpoint gives itself, such as a `4xx` from the server container, is
`request_failed`.

## Stability

ReleaseFlow has not published a release yet. Versions follow Semantic Versioning, and an
incompatible change to an endpoint is recorded as a `BREAKING CHANGE` that raises the
major version (see [Releasing](../CONTRIBUTING.md#releasing)). An error's `code` never
changes. The API has no version prefix and no OpenAPI document; this page and the guides
are its description. Only `GET /api/automation/runs` is paged; every other list,
the Change Inbox included, returns everything at once.

## Endpoints

Roles: **public** needs no session; **member** needs any signed-in person;
**admin** needs an administrator.

### Account and Organization — [Workspace](workspace.md)

| Method and path | Role |
| --- | --- |
| `GET /api/status` | public |
| `GET /api/csrf` | public |
| `POST /api/registrations` | public |
| `GET /api/session` | member |
| `GET /api/me/ui-locale`, `PUT /api/me/ui-locale` | member |
| `GET /api/organization/output-language` | member |
| `PUT /api/organization/output-language` | admin |
| `GET /api/organization/slug` | member |
| `PUT /api/organization/slug` | admin |
| `GET /api/members` | admin |
| `GET /api/invitations`, `POST /api/invitations` | admin |
| `POST /api/invitations/{invitationId}/reissue` | admin |
| `DELETE /api/invitations/{invitationId}` | admin |
| `POST /api/public/invitations/inspect`, `POST /api/public/invitations/accept` | public |

### Projects and sources — [Sources](sources.md)

| Method and path | Role |
| --- | --- |
| `GET /api/projects`, `POST /api/projects` | member |
| `GET /api/projects/{projectId}/sources` | member |
| `POST /api/projects/{projectId}/sources` | admin |
| `PUT /api/projects/{projectId}/sources/{sourceId}/token` | admin |
| `GET /api/projects/{projectId}/imports` | member |
| `POST /api/projects/{projectId}/sources/{sourceId}/imports` | admin |
| `POST /api/projects/{projectId}/sources/{sourceId}/imports/resume` | admin |

### Changes — [Changes and review](changes.md)

| Method and path | Role |
| --- | --- |
| `GET /api/projects/{projectId}/changes` | member |
| `POST /api/projects/{projectId}/changes/{changeId}/review` | member |
| `POST /api/projects/{projectId}/changes/{changeId}/exclusion` | admin |
| `DELETE /api/projects/{projectId}/changes/{changeId}/exclusion` | admin |
| `POST /api/projects/{projectId}/changes/{changeId}/ai-classification` | member |
| `GET /api/projects/{projectId}/duplicate-candidates` | member |
| `POST /api/projects/{projectId}/duplicate-candidates/{candidateId}/decision` | member |
| `GET /api/projects/{projectId}/sensitive-paths` | member |
| `PUT /api/projects/{projectId}/sensitive-paths` | admin |
| `GET /api/categories` | member |
| `POST /api/categories` | admin |
| `PUT /api/categories/{categoryId}`, `DELETE /api/categories/{categoryId}` | admin |
| `POST /api/categories/{categoryId}/unarchive` | admin |
| `GET /api/category-suggestions` | admin |
| `POST /api/category-suggestions/{suggestionId}/decision` | admin |

### Releases and notes — [Release notes](release-notes.md)

| Method and path | Role |
| --- | --- |
| `GET /api/projects/{projectId}/releases`, `POST /api/projects/{projectId}/releases` | member |
| `GET`, `PUT`, `DELETE /api/projects/{projectId}/releases/{releaseId}` | member |
| `PUT /api/projects/{projectId}/releases/{releaseId}/schedule` | member |
| `GET /api/projects/{projectId}/releases/{releaseId}/available-changes` | member |
| `POST /api/projects/{projectId}/releases/{releaseId}/changes` | member |
| `DELETE /api/projects/{projectId}/releases/{releaseId}/changes/{changeId}` | member |
| `POST /api/projects/{projectId}/releases/{releaseId}/request-review` | member |
| `PUT /api/projects/{projectId}/releases/{releaseId}/changes/{changeId}/decision` | member |
| `PUT /api/projects/{projectId}/releases/{releaseId}/changes/{changeId}/summary` | member |
| `POST /api/projects/{projectId}/releases/{releaseId}/approve` | member |
| `POST /api/projects/{projectId}/releases/{releaseId}/return-to-draft` | member |
| `POST /api/projects/{projectId}/releases/{releaseId}/publish` | member |
| `POST /api/projects/{projectId}/releases/{releaseId}/translations/retry` | member |
| `GET /api/projects/{projectId}/releases/{releaseId}/note-previews` | member |
| `GET /api/projects/{projectId}/releases/{releaseId}/notes` | member |
| `PUT /api/projects/{projectId}/releases/{releaseId}/notes/{noteId}` | member |
| `GET /api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/download` | member |
| `GET /api/projects/{projectId}/release-assignments` | member |
| `GET /api/audiences`, `POST /api/audiences` | admin |
| `GET`, `PUT`, `DELETE /api/audiences/{audienceId}` | admin |
| `PUT /api/audiences/{audienceId}/templates/{language}` | admin |
| `POST /api/audiences/{audienceId}/reset-to-preset` | admin |
| `POST /api/audiences/preview` | admin |
| `GET /api/organization/release-languages` | member |
| `PUT /api/organization/release-languages` | admin |

### Automation — [Automation](automation.md)

| Method and path | Role |
| --- | --- |
| `GET /api/automation/rules`, `POST /api/automation/rules` | admin |
| `GET`, `PUT`, `DELETE /api/automation/rules/{ruleId}` | admin |
| `POST /api/automation/rules/{ruleId}/enable`, `POST /api/automation/rules/{ruleId}/disable` | admin |
| `POST /api/automation/rules/{ruleId}/execute` | admin |
| `POST /api/automation/rules/{ruleId}/webhook-secret/rotate` | admin |
| `POST /api/automation/rules/cron-preview` | admin |
| `GET /api/automation/runs`, `GET /api/automation/runs/{runId}` | admin |
| `POST /api/automation/runs/{runId}/retry`, `POST /api/automation/runs/{runId}/cancel` | admin |

### Signed webhooks

These take no session and no CSRF token; each proves itself with its own secret.
Anything else under `/webhooks/` is refused.

| Method and path | Proof | Guide |
| --- | --- | --- |
| `POST /webhooks/github/{webhookId}` | `X-Hub-Signature-256` | [Sources](sources.md#receive-github-webhooks) |
| `POST /webhooks/gitlab/{webhookId}` | Standard Webhooks headers or `X-Gitlab-Token` | [Sources](sources.md#receive-gitlab-webhooks) |
| `POST /webhooks/linear/{webhookId}` | `Linear-Signature`, delivery, stamp, and workspace | [Sources](sources.md#receive-linear-webhooks) |
| `POST /webhooks/automation/{webhookId}` | `X-ReleaseFlow-Signature-256` | [Automation](automation.md#a-rule-another-system-calls) |
| `GET /webhooks/automation/{webhookId}/runs/{runId}` | `X-ReleaseFlow-Signature-256` | [Automation](automation.md#a-rule-another-system-calls) |

### Public changelog

Anonymous, `GET` only, and never given a session: `/changelog/{slug}`,
`/changelog/{slug}/releases/{entryId}`, and `/changelog/{slug}/rss.xml`. See
[Public changelog](automation.md#public-changelog).

## Error codes by area

| Area | Status | Codes |
| --- | --- | --- |
| Account | `400` | `output_language_invalid`, `ui_locale_invalid`, `invitation_invalid`, `organization_slug_invalid` |
| Account | `404` | `invitation_not_found` |
| Account | `409` | `email_already_registered`, `invitation_already_pending`, `invitation_email_unavailable`, `organization_slug_taken` |
| Sources | `400` | `gitlab_base_url_invalid`, `gitlab_host_not_allowed`, `jira_site_invalid`, `github_token_rejected`, `gitlab_token_rejected`, `linear_token_rejected`, `jira_token_rejected` |
| Sources | `404` | `project_not_found`, `source_not_found` |
| Sources | `409` | `github_repository_already_connected`, `gitlab_project_already_connected`, `linear_team_already_connected`, `jira_project_already_connected`, `source_token_missing`, `source_sync_in_progress`, `source_import_not_supported`, `source_import_not_resumable` |
| Sources | `503` | `github_unavailable`, `gitlab_unavailable`, `jira_unavailable` |
| Webhooks | `400` | `webhook_payload_malformed` |
| Webhooks | `401` | `webhook_signature_invalid` |
| Webhooks | `413` | `webhook_payload_too_large` |
| Webhooks | `422` | `webhook_repository_mismatch` |
| Changes | `400` | `invalid_change_filter`, `invalid_change_review`, `invalid_sensitive_paths` |
| Changes | `404` | `change_not_found`, `duplicate_candidate_not_found` |
| Changes | `409` | `change_processing`, `change_not_eligible_for_ai`, `duplicate_candidate_decided`, `change_excluded`, `change_in_release` |
| Changes | `502` | `ai_classification_failed` |
| Changes | `503` | `ai_classification_unavailable` |
| Categories | `404` | `category_not_found`, `category_suggestion_not_found` |
| Categories | `409` | `category_code_taken`, `category_system`, `category_suggestion_decided` |
| Audiences | `400` | `template_invalid`, `template_narratives_path`, `template_language_not_targeted`, `invalid_release_languages` |
| Audiences | `404` | `audience_not_found` |
| Audiences | `409` | `audience_code_taken`, `audience_limit`, `audience_last`, `audience_in_use`, `audience_not_preset` |
| Releases | `400` | `invalid_release_schedule` |
| Releases | `404` | `release_not_found`, `release_note_not_found` |
| Releases | `409` | `release_status_conflict`, `release_published`, `release_empty`, `release_review_incomplete`, `release_version_taken`, `classification_changed`, `change_not_releasable`, `change_excluded`, `release_note_render_failed`, `release_notes_missing`, `translations_not_ready` |
| Automation | `400`, `404`, `409` | See [Automation](automation.md#rest) |
