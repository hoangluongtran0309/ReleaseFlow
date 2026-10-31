# REST API

REST API làm được mọi việc mà các trang làm được, thông qua cùng các service của ứng dụng. Trang
này trình bày các quy ước và liệt kê mọi endpoint; mỗi hướng dẫn được liên kết bên dưới mô tả
đầy đủ body và hành vi của các endpoint thuộc về nó.

## Phiên đăng nhập và CSRF {#sessions-and-csrf}

REST client dùng cùng chính sách phiên đăng nhập và CSRF như các trang do server dựng. Mọi thao
tác ghi đều cần CSRF token, và bảo vệ CSRF không bao giờ bị tắt: những đường dẫn duy nhất được
miễn là các endpoint webhook có chữ ký dưới `/webhooks/`, vốn không mang cookie và thay vào đó tự
chứng minh bằng một HMAC trên request. Xem [ADR-0023](adr/0023-scoped-csrf-exemptions.md).

Không có API token: một script đăng nhập với tư cách một người, bằng email và mật khẩu của
người đó, rồi giữ cookie phiên đăng nhập.

1. Gọi `GET /api/csrf` và giữ cookie phiên đăng nhập mà nó đặt. Nó trả về
   `{"headerName": "X-CSRF-TOKEN", "parameterName": "_csrf", "token"}`.
2. Gửi token trong header đó kèm mọi `POST`, `PUT` và `DELETE`, ví dụ
   `POST /api/registrations`.
3. Đăng nhập qua `POST /login` với các trường biểu mẫu `email`, `password` và token.
   Nó trả về một redirect, không bao giờ là JSON: tới `/` khi thành công, và tới
   `/login?error` khi thất bại.
4. **Lấy token mới.** Đăng nhập thay phiên đăng nhập và token của nó, nên token ở bước 1
   giờ bị từ chối với `403 access_denied`.
5. Đọc danh tính đang đăng nhập từ `GET /api/session`.

Với `curl` và `jq`:

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

![Chạy các lệnh trên với một deployment: CSRF token, đăng nhập, session và một Project mới](assets/api/api-1-session.gif)

Một phiên đăng nhập kết thúc sau 30 phút không có request nào, và khi ứng dụng khởi động lại;
sau đó, mọi lời gọi trả về `401 authentication_required` cho tới khi script đăng nhập lại.
`POST /logout` kết thúc phiên ngay lập tức. Đặt `RELEASEFLOW_SESSION_COOKIE_SECURE=true` mỗi
khi ứng dụng được phục vụ qua HTTPS.

Organization luôn lấy từ người đang đăng nhập, không bao giờ từ body của request. Tài nguyên của
Organization khác được báo là không tìm thấy, không bao giờ là bị cấm.

## Lỗi {#errors}

Mọi lỗi dưới `/api` đều là `application/problem+json` với một `code` cố định:

```json
{
  "title": "Release not found",
  "status": 404,
  "detail": "…",
  "code": "release_not_found"
}
```

`title` và `detail` được viết bằng [ngôn ngữ giao diện](workspace.md#interface-language) của
người đọc; **`code` không bao giờ thay đổi**, nên bên gọi cần so khớp theo nó. Lỗi kiểm tra dữ liệu có
thêm `errors`, một map từ tên trường tới thông báo.

| Trạng thái | Mã có thể đến từ bất kỳ endpoint nào |
| --- | --- |
| `400` | `validation_failed` (thiếu trường hoặc giá trị nằm ngoài khoảng cho phép), `malformed_request` (không đọc được body), `invalid_parameter` (một tham số trên đường dẫn hoặc query bị thiếu hoặc không chuyển đổi được, chẳng hạn một ID không phải UUID) |
| `401` | `authentication_required` (chưa có phiên đăng nhập) |
| `403` | `access_denied` (endpoint dành cho quản trị viên, hoặc CSRF token thiếu hay đã cũ) |
| `404` | `not_found` (không có địa chỉ đó) |
| `405` | `method_not_allowed` (địa chỉ không nhận phương thức đó) |
| `415` | `unsupported_media_type` (địa chỉ không đọc body thuộc kiểu đó) |
| `500` | `internal_error` (một lỗi ReleaseFlow không lường trước; body không bao giờ chứa exception gây ra nó) |

Một trạng thái mà không endpoint nào tự trả, chẳng hạn một `4xx` từ servlet container, là
`request_failed`.

## Độ ổn định {#stability}

ReleaseFlow chưa phát hành phiên bản nào. Phiên bản tuân theo Semantic Versioning, và một thay
đổi không tương thích ở một endpoint được ghi là `BREAKING CHANGE`, làm tăng phiên bản major
(xem [Releasing](../CONTRIBUTING.md#releasing)). `code` của một lỗi không bao giờ thay đổi. Một
bản phát hành minor chỉ thêm vào phản hồi: trường mới nằm bên cạnh trường mà bên gọi đang đọc
chứ không đổi hình dạng của nó. Ví dụ, một thay đổi vẫn giữ `audienceNarratives` là map từ mã
audience tới nội dung narrative, còn `audienceNarrativeDetails` bên cạnh cho biết `relevance`,
`headline` và `body` của từng narrative ([Mỗi audience đọc gì](release-notes.md#what-each-audience-reads));
Sửa tóm tắt nhận cả hai dạng trong `narratives`. Tương tự, một release có thêm `glossaries`, và
mỗi note có thêm `glossaryStatus` và `glossaryReason`, bên cạnh các trường vốn có
([Bảng thuật ngữ](release-notes.md#glossary)). Một thay đổi có thêm `security`, một release có
thêm `upgradeGuideUrl`, `previousReleaseId` và `previousVersion`, và một audience có thêm
`footerTemplateBody` và `footerTemplateVariants`; request bỏ các trường mới này vẫn chạy như trước
([Phần cuối](release-notes.md#footer)). API không có tiền tố phiên bản và không có tài liệu OpenAPI; trang này và các hướng dẫn chính là
phần mô tả của nó. Chỉ `GET /api/automation/runs` có phân trang; mọi danh sách khác, kể cả
Change Inbox, trả về toàn bộ trong một lần.

## Endpoint {#endpoints}

Vai trò: **công khai** không cần phiên đăng nhập; **thành viên** cần một người bất kỳ đã đăng
nhập; **quản trị viên** cần một quản trị viên.

### Tài khoản và Organization — [Workspace](workspace.md) {#account-and-organization--workspace}

| Phương thức và đường dẫn | Vai trò |
| --- | --- |
| `GET /api/status` | công khai |
| `GET /api/csrf` | công khai |
| `POST /api/registrations` | công khai |
| `GET /api/session` | thành viên |
| `GET /api/me/ui-locale`, `PUT /api/me/ui-locale` | thành viên |
| `GET /api/organization/output-language` | thành viên |
| `PUT /api/organization/output-language` | quản trị viên |
| `GET /api/organization/slug` | thành viên |
| `PUT /api/organization/slug` | quản trị viên |
| `GET /api/members` | quản trị viên |
| `GET /api/invitations`, `POST /api/invitations` | quản trị viên |
| `POST /api/invitations/{invitationId}/reissue` | quản trị viên |
| `DELETE /api/invitations/{invitationId}` | quản trị viên |
| `POST /api/public/invitations/inspect`, `POST /api/public/invitations/accept` | công khai |

### Project và nguồn — [Nguồn](sources.md) {#projects-and-sources--sources}

| Phương thức và đường dẫn | Vai trò |
| --- | --- |
| `GET /api/projects`, `POST /api/projects` | thành viên |
| `GET /api/projects/{projectId}/sources` | thành viên |
| `POST /api/projects/{projectId}/sources` | quản trị viên |
| `PUT /api/projects/{projectId}/sources/{sourceId}/token` | quản trị viên |
| `GET /api/projects/{projectId}/imports` | thành viên |
| `POST /api/projects/{projectId}/sources/{sourceId}/imports` | quản trị viên |
| `POST /api/projects/{projectId}/sources/{sourceId}/imports/resume` | quản trị viên |

### Thay đổi — [Thay đổi và review](changes.md) {#changes--changes-and-review}

| Phương thức và đường dẫn | Vai trò |
| --- | --- |
| `GET /api/projects/{projectId}/changes` | thành viên |
| `POST /api/projects/{projectId}/changes/{changeId}/review` | thành viên |
| `POST /api/projects/{projectId}/changes/{changeId}/exclusion` | quản trị viên |
| `DELETE /api/projects/{projectId}/changes/{changeId}/exclusion` | quản trị viên |
| `POST /api/projects/{projectId}/changes/{changeId}/ai-classification` | thành viên |
| `GET /api/projects/{projectId}/duplicate-candidates` | thành viên |
| `POST /api/projects/{projectId}/duplicate-candidates/{candidateId}/decision` | thành viên |
| `GET /api/projects/{projectId}/sensitive-paths` | thành viên |
| `PUT /api/projects/{projectId}/sensitive-paths` | quản trị viên |
| `GET /api/projects/{projectId}/context` | thành viên |
| `PUT /api/projects/{projectId}/context` | quản trị viên |
| `GET /api/categories` | thành viên |
| `POST /api/categories` | quản trị viên |
| `PUT /api/categories/{categoryId}`, `DELETE /api/categories/{categoryId}` | quản trị viên |
| `POST /api/categories/{categoryId}/unarchive` | quản trị viên |
| `GET /api/category-suggestions` | quản trị viên |
| `POST /api/category-suggestions/{suggestionId}/decision` | quản trị viên |

### Release và release note — [Release note](release-notes.md) {#releases-and-notes--release-notes}

| Phương thức và đường dẫn | Vai trò |
| --- | --- |
| `GET /api/projects/{projectId}/releases`, `POST /api/projects/{projectId}/releases` | thành viên |
| `GET`, `PUT`, `DELETE /api/projects/{projectId}/releases/{releaseId}` | thành viên |
| `PUT /api/projects/{projectId}/releases/{releaseId}/schedule` | thành viên |
| `GET /api/projects/{projectId}/releases/{releaseId}/available-changes` | thành viên |
| `POST /api/projects/{projectId}/releases/{releaseId}/changes` | thành viên |
| `DELETE /api/projects/{projectId}/releases/{releaseId}/changes/{changeId}` | thành viên |
| `POST /api/projects/{projectId}/releases/{releaseId}/request-review` | thành viên |
| `PUT /api/projects/{projectId}/releases/{releaseId}/changes/{changeId}/decision` | thành viên |
| `PUT /api/projects/{projectId}/releases/{releaseId}/changes/{changeId}/summary` | thành viên |
| `POST /api/projects/{projectId}/releases/{releaseId}/approve` | thành viên |
| `POST /api/projects/{projectId}/releases/{releaseId}/return-to-draft` | thành viên |
| `POST /api/projects/{projectId}/releases/{releaseId}/publish` | thành viên |
| `POST /api/projects/{projectId}/releases/{releaseId}/translations/retry` | thành viên |
| `GET /api/projects/{projectId}/releases/{releaseId}/note-previews` | thành viên |
| `GET /api/projects/{projectId}/releases/{releaseId}/notes` | thành viên |
| `PUT /api/projects/{projectId}/releases/{releaseId}/notes/{noteId}` | thành viên |
| `GET /api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/download` | thành viên |
| `GET /api/projects/{projectId}/release-assignments` | thành viên |
| `GET /api/audiences`, `POST /api/audiences` | quản trị viên |
| `GET`, `PUT`, `DELETE /api/audiences/{audienceId}` | quản trị viên |
| `PUT /api/audiences/{audienceId}/templates/{language}` | quản trị viên |
| `POST /api/audiences/{audienceId}/reset-to-preset` | quản trị viên |
| `POST /api/audiences/preview` | quản trị viên |
| `GET /api/organization/release-languages` | thành viên |
| `PUT /api/organization/release-languages` | quản trị viên |
| `GET /api/glossary`, `GET /api/glossary/suggestions` | thành viên |
| `POST /api/glossary/kept-terms`, `DELETE /api/glossary/kept-terms/{termId}` | quản trị viên |
| `POST /api/glossary/translations`, `DELETE /api/glossary/translations/{translationId}` | quản trị viên |

### Automation — [Automation](automation.md) {#automation--automation}

| Phương thức và đường dẫn | Vai trò |
| --- | --- |
| `GET /api/automation/rules`, `POST /api/automation/rules` | quản trị viên |
| `GET`, `PUT`, `DELETE /api/automation/rules/{ruleId}` | quản trị viên |
| `POST /api/automation/rules/{ruleId}/enable`, `POST /api/automation/rules/{ruleId}/disable` | quản trị viên |
| `POST /api/automation/rules/{ruleId}/execute` | quản trị viên |
| `POST /api/automation/rules/{ruleId}/webhook-secret/rotate` | quản trị viên |
| `POST /api/automation/rules/cron-preview` | quản trị viên |
| `GET /api/automation/runs`, `GET /api/automation/runs/{runId}` | quản trị viên |
| `POST /api/automation/runs/{runId}/retry`, `POST /api/automation/runs/{runId}/cancel` | quản trị viên |

### Webhook có chữ ký {#signed-webhooks}

Các endpoint này không cần phiên đăng nhập hay CSRF token; mỗi endpoint tự chứng minh bằng secret
của riêng nó. Mọi thứ khác dưới `/webhooks/` đều bị từ chối.

| Phương thức và đường dẫn | Bằng chứng | Hướng dẫn |
| --- | --- | --- |
| `POST /webhooks/github/{webhookId}` | `X-Hub-Signature-256` | [Nguồn](sources.md#receive-github-webhooks) |
| `POST /webhooks/gitlab/{webhookId}` | Header Standard Webhooks hoặc `X-Gitlab-Token` | [Nguồn](sources.md#receive-gitlab-webhooks) |
| `POST /webhooks/linear/{webhookId}` | `Linear-Signature`, delivery, dấu thời gian và workspace | [Nguồn](sources.md#receive-linear-webhooks) |
| `POST /webhooks/automation/{webhookId}` | `X-ReleaseFlow-Signature-256` | [Automation](automation.md#a-rule-another-system-calls) |
| `GET /webhooks/automation/{webhookId}/runs/{runId}` | `X-ReleaseFlow-Signature-256` | [Automation](automation.md#a-rule-another-system-calls) |

### Changelog công khai {#public-changelog}

Ẩn danh, chỉ `GET`, và không bao giờ được cấp phiên đăng nhập: `/changelog/{slug}`,
`/changelog/{slug}/releases/{entryId}` và `/changelog/{slug}/rss.xml`. Xem
[Changelog công khai](automation.md#public-changelog).

## Mã lỗi theo khu vực {#error-codes-by-area}

| Khu vực | Trạng thái | Mã |
| --- | --- | --- |
| Tài khoản | `400` | `output_language_invalid`, `ui_locale_invalid`, `invitation_invalid`, `organization_slug_invalid` |
| Tài khoản | `404` | `invitation_not_found` |
| Tài khoản | `409` | `email_already_registered`, `invitation_already_pending`, `invitation_email_unavailable`, `organization_slug_taken` |
| Nguồn | `400` | `gitlab_base_url_invalid`, `gitlab_host_not_allowed`, `jira_site_invalid`, `github_token_rejected`, `gitlab_token_rejected`, `linear_token_rejected`, `jira_token_rejected` |
| Nguồn | `404` | `project_not_found`, `source_not_found` |
| Nguồn | `409` | `github_repository_already_connected`, `gitlab_project_already_connected`, `linear_team_already_connected`, `jira_project_already_connected`, `source_token_missing`, `source_sync_in_progress`, `source_import_not_supported`, `source_import_not_resumable` |
| Nguồn | `503` | `github_unavailable`, `gitlab_unavailable`, `jira_unavailable` |
| Webhook | `400` | `webhook_payload_malformed` |
| Webhook | `401` | `webhook_signature_invalid` |
| Webhook | `413` | `webhook_payload_too_large` |
| Webhook | `422` | `webhook_repository_mismatch` |
| Thay đổi | `400` | `invalid_change_filter`, `invalid_change_review`, `invalid_sensitive_paths` |
| Thay đổi | `404` | `change_not_found`, `duplicate_candidate_not_found` |
| Thay đổi | `409` | `change_processing`, `change_not_eligible_for_ai`, `duplicate_candidate_decided`, `change_excluded`, `change_in_release` |
| Thay đổi | `502` | `ai_classification_failed` |
| Thay đổi | `503` | `ai_classification_unavailable` |
| Category | `404` | `category_not_found`, `category_suggestion_not_found` |
| Category | `409` | `category_code_taken`, `category_system`, `category_suggestion_decided` |
| Audience | `400` | `template_invalid`, `template_narratives_path`, `template_language_not_targeted`, `invalid_release_languages` |
| Audience | `404` | `audience_not_found` |
| Audience | `409` | `audience_code_taken`, `audience_limit`, `audience_last`, `audience_in_use`, `audience_not_preset` |
| Bảng thuật ngữ | `404` | `glossary_term_not_found` |
| Bảng thuật ngữ | `409` | `glossary_term_taken`, `glossary_limit` |
| Release | `400` | `invalid_release_schedule`, `invalid_upgrade_guide_url`, `invalid_previous_release` |
| Release | `404` | `release_not_found`, `release_note_not_found` |
| Release | `409` | `release_status_conflict`, `release_published`, `release_empty`, `release_review_incomplete`, `change_summary_required`, `release_version_taken`, `classification_changed`, `change_not_releasable`, `change_excluded`, `release_note_render_failed`, `release_notes_missing`, `translations_not_ready` |
| Automation | `400`, `404`, `409` | Xem [Automation](automation.md#rest) |
