# Automation

Quy tắc gửi một release note đã publish tới nơi người đọc của nó vẫn thường xem, và changelog
công khai publish nó để bất kỳ ai cũng đọc được.

## Quy tắc {#rules}

Một quy tắc gửi release note đã publish tới nơi người đọc của nó vẫn thường xem. Mở
**Automation** trong thanh bên, mục chỉ quản trị viên mới thấy. Một quy tắc nêu rõ điều gì khiến
nó chạy, project nó theo dõi (hoặc mọi project), và các action nó thực hiện theo thứ tự. Mỗi
action nêu audience và ngôn ngữ của release note mà nó gửi.

Có năm điều có thể khiến một quy tắc chạy:

| Chạy khi | Cần gì | Điều gì xảy ra |
| --- | --- | --- |
| Release được publish | Không cần gì | Mỗi quy tắc khớp chạy một lần cho mỗi release |
| Chạy bằng tay | Một release đã publish và một request ID | Cùng request ID là cùng một lần chạy, không bao giờ gửi lần thứ hai |
| Theo lịch | Một release đã publish, một biểu thức cron sáu trường và một múi giờ IANA | Release được gửi lại theo lịch đó. Những lần chạy bị lỡ trong lúc ReleaseFlow ngừng hoạt động gộp thành một lần chạy bù |
| Trước release dự kiến | Thời gian báo trước từ 0 đến 365 ngày | Một release đã duyệt có thời điểm dự kiến được thông báo trước chừng ấy thời gian. Dời lịch release sẽ có thêm một lần nhắc |
| Do hệ thống khác gọi | Không cần gì; ReleaseFlow tạo đường dẫn và secret | Một lời gọi có chữ ký nêu một release đã publish và bắt đầu một lần chạy |

Quy tắc theo lịch và quy tắc nhắc trước chỉ nhận những action chỉ để thông báo cho mọi người —
Slack, email và Microsoft Teams: không có ai theo dõi khi chúng chạy. Quy tắc theo lịch lấy
project từ release mà nó lặp lại. **Xem lần chạy tới** trên trang, hoặc
`POST /api/automation/rules/cron-preview`, cho biết khi nào một biểu thức sẽ chạy lần tới,
trước khi quy tắc nào lưu nó.

Viết quy tắc trước; quy tắc mới luôn ở trạng thái tắt. Bật nó là lúc ReleaseFlow kiểm tra rằng
các lần gửi có thể thực hiện được: project và mọi audience thuộc Organization của bạn, ngôn ngữ
là một ngôn ngữ Organization của bạn viết release note, project của action GitHub Release có
đúng một nguồn GitHub, hệ thống triển khai có khả năng thực hiện action đó, và cấu hình cùng
secret đều dùng được. Một quy tắc bị từ chối sẽ cho biết điều nào trong số đó không đạt.

## Action {#actions}

Có tám loại action:

| Action | Cần gì | Làm gì |
| --- | --- | --- |
| GitHub Release | Nguồn GitHub của chính project và access token của nó | Publish release note làm nội dung release của tag phiên bản, có đánh dấu trong body để lần lặp lại nhận ra phần việc của chính nó |
| Slack | Một incoming webhook URL, cũng là secret của action | Đăng release note vào channel bằng định dạng mrkdwn của Slack, tối đa 39.000 ký tự |
| Email | `RELEASEFLOW_AUTOMATION_EMAIL_FROM` và một SMTP server | Gửi release note tới từ 1 đến 100 địa chỉ |
| Changelog công khai | Không cần gì | Publish release note trên trang changelog và RSS feed của chính bạn |
| Notion | ID của trang cha và một integration token | Tạo release note thành một trang con có tiêu đề `Release <version>`, tối đa 450.000 byte |
| Confluence | Một site Cloud, email của tài khoản, ID số của space, trang cha không bắt buộc, và một API token | Tạo một trang có tiêu đề `Release <version>` trong space đó, tối đa 2.000.000 byte |
| Microsoft Teams | URL callback của Workflows, cũng là secret của action | Đăng release note vào chat hoặc channel mà flow nhắm tới, request tối đa 28 KiB |
| Zendesk | Một subdomain, client ID và secret OAuth, ID số của section, và user segment không bắt buộc | Publish release note thành một bài viết Help Center bằng ngôn ngữ của action, tối đa 1.000.000 byte |

Một Slack webhook URL phải trỏ tới một origin mà hệ thống triển khai cho phép — `hooks.slack.com`
hoặc `hooks.slack-gov.com`, trừ khi `RELEASEFLOW_SLACK_ALLOWED_HOSTS` quy định khác — và được
kiểm tra lại trước mỗi lần gửi. Một site Confluence được kiểm tra theo cách tương tự, nhưng dựa
trên hình dạng thay vì danh sách: nó phải đúng một nhãn ngay dưới `atlassian.net`, qua HTTPS,
không có gì sau host. ID trang Notion là 32 ký tự thập lục phân mà Notion hiển thị, có hoặc
không có dấu gạch nối. Callback Microsoft Teams phải là URL Workflows dạng HTTPS mà flow hiển thị
— một nhãn ngay dưới `environment.api.powerplatform.com`, đường dẫn trigger của Workflows, và
chữ ký `sig` của chính nó — còn subdomain Zendesk là một nhãn, không phải URL, nên help centre
duy nhất một action truy cập được là của chính nó. Trang Confluence mang một comment HTML ghi tên
lần chạy đã tạo ra nó; không có gì đọc lại comment đó, nên một lần gửi mà Notion hoặc Confluence
không bao giờ xác nhận sẽ giữ trạng thái không rõ kết quả cho tới khi một người chấp nhận có thêm
trang thứ hai. Secret chỉ ghi được: một khi đã lưu, không phản hồi, trang hay log nào lặp lại nó,
và để trống trường này khi sửa sẽ giữ nguyên secret đang có.

## Quy tắc do hệ thống khác gọi {#a-rule-another-system-calls}

Lưu một quy tắc chạy khi có hệ thống khác gọi sẽ tạo cho nó một đường dẫn và một secret 256 bit,
cả hai chỉ hiện một lần. **Xoay secret** thay secret và giữ nguyên đường dẫn; secret cũ ngừng
hoạt động ngay lập tức.

Một lời gọi là một `POST` tới đường dẫn đó với `{"releaseId": "…"}` và ba header:

| Header | Chứa gì |
| --- | --- |
| `X-ReleaseFlow-Timestamp` | Số giây tính từ epoch, lệch không quá năm phút so với hiện tại |
| `X-ReleaseFlow-Delivery` | Một UUID, gửi lại y nguyên khi thử lại để không có gì bị gửi hai lần |
| `X-ReleaseFlow-Signature-256` | `sha256=` và HMAC-SHA256 dạng hex như dưới đây |

Chữ ký bao gồm timestamp, delivery, phương thức viết hoa, đường dẫn request, và SHA-256 dạng hex
của body, mỗi thứ một dòng:

```bash
BODY='{"releaseId":"<release>"}'
TS=$(date +%s)
DELIVERY=$(uuidgen)
PATH_='/webhooks/automation/<webhook>'
DIGEST=$(printf '%s' "$BODY" | openssl dgst -sha256 -hex | cut -d' ' -f2)
SIGNATURE=$(printf '%s\n%s\nPOST\n%s\n%s' "$TS" "$DELIVERY" "$PATH_" "$DIGEST" \
  | openssl dgst -sha256 -hmac "$SECRET" -hex | cut -d' ' -f2)
curl -X POST "http://localhost:8080$PATH_" \
  -H "X-ReleaseFlow-Timestamp: $TS" \
  -H "X-ReleaseFlow-Delivery: $DELIVERY" \
  -H "X-ReleaseFlow-Signature-256: sha256=$SIGNATURE" \
  -H 'Content-Type: application/json' -d "$BODY"
```

Phản hồi là `202` với `{"runId", "status", "statusPath"}`. Một lời gọi `GET` có chữ ký tới
`statusPath` đó cho biết lần chạy kết thúc ra sao. Organization là của quy tắc, không bao giờ
lấy từ body. Body lớn hơn 64 KiB trả về `413`; đường dẫn không tồn tại, quy tắc đang tắt, thời
điểm lệch quá năm phút, và chữ ký sai đều trả về `401`.

## Lần chạy {#runs}

Publish một release chỉ ghi nhận rằng việc đó đã xảy ra, không làm gì thêm, nên một quy tắc
không ai thực hiện được không bao giờ cản trở một release. Trong vòng một giây, worker tạo một
lần chạy cho mỗi quy tắc khớp và đi qua lần lượt các action của nó. Lịch sử chạy cho thấy từng
lần gửi và kết quả của nó. Action đầu tiên thất bại sẽ chặn các action phía sau, và **Chạy lại**
chỉ lặp lại đúng action đó. Một action mà không ai xác nhận được kết quả — kết nối bị ngắt sau
khi request đã đi, hoặc worker dừng trong năm phút — được ghi là không rõ kết quả và không bao
giờ tự gửi lại; lặp lại nó sẽ yêu cầu bạn xác nhận rằng lần gửi có thể xảy ra hai lần. **Huỷ**
để action đang thực hiện ghi lại kết quả của nó và dừng các action còn lại.

## REST {#rest}

REST client làm được những việc tương tự, gửi cookie phiên đăng nhập và CSRF token của phiên đã
đăng nhập kèm mọi thao tác ghi. Đăng nhập như [REST API](api.md#sessions-and-csrf) hướng dẫn,
việc này đặt sẵn `BASE`, `JAR` và `TOKEN`:

```bash
# BASE, JAR, and TOKEN as set in REST API → Sessions and CSRF
curl -s -b "$JAR" -H "X-CSRF-TOKEN: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Announce","triggerType":"RELEASE_PUBLISHED","projectId":"<project>",
       "actions":[{"actionType":"SLACK","audienceId":"<audience>","language":"en",
                   "secret":"https://hooks.slack.com/services/T0/B0/secret"}]}' \
  "$BASE/api/automation/rules"
curl -s -b "$JAR" -H "X-CSRF-TOKEN: $TOKEN" -X POST "$BASE/api/automation/rules/<rule>/enable"
curl -s -b "$JAR" -H "X-CSRF-TOKEN: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"releaseId":"<release>","requestId":"<uuid you choose>"}' \
  "$BASE/api/automation/rules/<rule>/execute"
curl -s -b "$JAR" "$BASE/api/automation/runs?page=0&size=20"
curl -s -b "$JAR" -H "X-CSRF-TOKEN: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"confirmUnknown":true}' "$BASE/api/automation/runs/<run>/retry"
```

Tất cả endpoint, đều chỉ dành cho quản trị viên:

```text
GET    /api/automation/rules
POST   /api/automation/rules                                   (201; a webhook rule's path and secret are in this response only)
GET    /api/automation/rules/{ruleId}
PUT    /api/automation/rules/{ruleId}                          (an empty secret keeps the stored one)
DELETE /api/automation/rules/{ruleId}                          (archives it)
POST   /api/automation/rules/{ruleId}/enable
POST   /api/automation/rules/{ruleId}/disable
POST   /api/automation/rules/{ruleId}/execute                  {"releaseId", "requestId"} (202)
POST   /api/automation/rules/{ruleId}/webhook-secret/rotate    (the new secret, once)
POST   /api/automation/rules/cron-preview                      {"cronExpression", "cronTimeZone"}
GET    /api/automation/runs?page=0&size=20
GET    /api/automation/runs/{runId}
POST   /api/automation/runs/{runId}/retry                      {"confirmUnknown"}
POST   /api/automation/runs/{runId}/cancel
```

Một quy tắc là `{"name", "triggerType", "projectId", "releaseId", "cronExpression",
"cronTimeZone", "daysBefore", "actions"}`, chỉ gồm các trường mà trigger của nó dùng. Một action
là `{"actionType", "audienceId", "language", "secret"}` cộng các trường riêng của nó:
`recipients` cho email, `parentPageId` cho Notion, `siteUrl`, `email`, `spaceId` và
`parentPageId` cho Confluence, và `subdomain`, `clientId`, `sectionId` và `userSegmentId` cho
Zendesk. Khi sửa một quy tắc, action đã có sẵn gửi kèm `id` của nó; chỉ khi đó một `secret` để
trống mới giữ lại secret đã lưu, còn action gửi đi mà không có `id` là một action mới và cần
secret của nó một lần nữa.

`POST /{id}/execute` trả về `202` kèm lần chạy. Request ID do bạn chọn, và là lời cam kết rằng một
lần lặp lại là cùng một lần chạy chứ không phải một lần gửi thứ hai.
`DELETE /api/automation/rules/{id}` lưu trữ một quy tắc, việc này huỷ các lần chạy chưa xong của
nó và giải phóng tên của nó. `GET /api/automation/runs` trả về
`{"items", "page", "size", "total"}`, với `size` từ 1 đến 100.

Lời từ chối của action changelog công khai là `public_changelog_conflict`, được ghi trên action
chứ không trả về cho ai; xem [Changelog công khai](#public-changelog). Tương tự, một action mà
release của nó không có release note sẵn sàng cho audience và ngôn ngữ của action sẽ thất bại với
`release_note_missing` khi worker tới lượt nó; release vẫn được publish dù thế nào.

Các lời từ chối mang mã cố định:

| Trạng thái | Mã |
| --- | --- |
| `404` | `automation_rule_not_found`, `automation_run_not_found` — kể cả với quy tắc hoặc lần chạy của Organization khác, không bao giờ là `403`, và với quy tắc đã lưu trữ: `GET`, `PUT`, bật, tắt và `DELETE` một quy tắc như vậy đều trả `404 automation_rule_not_found` |
| `409` | `automation_rule_name_taken`, `automation_rule_unavailable`, `automation_github_source_missing`, `automation_email_not_configured`, `automation_run_not_retryable`, `automation_unknown_needs_confirmation`, `automation_release_not_published`, `automation_scheduled_action_unsupported`, `automation_webhook_rule_required` |
| `400`, quy tắc | `automation_action_required`, `automation_project_not_found`, `automation_audience_not_found`, `automation_language_not_configured`, `automation_secret_not_allowed`, `automation_request_id_required`, `invalid_automation_run_page` |
| `400`, lịch | `automation_cron_invalid`, `automation_cron_time_zone_invalid`, `automation_cron_no_occurrence`, `automation_cron_release_required`, `automation_reminder_days_invalid` |
| `400`, Slack và email | `automation_slack_webhook_required`, `automation_slack_webhook_invalid`, `automation_email_recipients_required`, `automation_email_recipient_invalid` |
| `400`, Notion và Confluence | `automation_notion_parent_required`, `automation_notion_parent_invalid`, `automation_notion_token_required`, `automation_confluence_site_required`, `automation_confluence_site_invalid`, `automation_confluence_email_required`, `automation_confluence_email_invalid`, `automation_confluence_space_required`, `automation_confluence_space_invalid`, `automation_confluence_parent_invalid`, `automation_confluence_token_required` |
| `400`, Teams và Zendesk | `automation_teams_webhook_required`, `automation_teams_webhook_invalid`, `automation_zendesk_subdomain_required`, `automation_zendesk_subdomain_invalid`, `automation_zendesk_client_id_required`, `automation_zendesk_client_secret_required`, `automation_zendesk_section_required`, `automation_zendesk_section_invalid`, `automation_zendesk_user_segment_invalid` |

Các đường dẫn webhook có chữ ký không dùng phiên đăng nhập, và từ chối với
`webhook_signature_invalid` (`401`), `webhook_payload_too_large` (`413`) hoặc
`webhook_payload_malformed` (`400`). Xem [ADR-0020](adr/0020-automation-rules-and-runs.md) và
[ADR-0021](adr/0021-scheduled-and-signed-automation-triggers.md).

## Changelog công khai {#public-changelog}

Một quy tắc có action **Changelog công khai** publish release note trên những trang do chính
ReleaseFlow phục vụ, cho những người không có tài khoản:

| Địa chỉ | Là gì |
| --- | --- |
| `/changelog/{slug}` | Các release note của Organization, mới nhất trước, cache trong năm phút |
| `/changelog/{slug}/releases/{entry}` | Một release note, cố định vĩnh viễn, cache trong một ngày kèm ETag |
| `/changelog/{slug}/rss.xml` | RSS 2.0 với 50 release note mới nhất |

`{slug}` là địa chỉ của Organization: một nhãn DNS, tạo từ tên của nó khi bạn đăng ký. Thẻ
**Changelog công khai** trên trang Project hiển thị nó và cho phép quản trị viên đổi nó — việc
này dời changelog đi chỗ khác, nên các liên kết đã chia sẻ sẽ không còn dùng được.
`GET`/`PUT /api/organization/slug` làm việc tương tự, và trả về `400 organization_slug_invalid`
hoặc `409 organization_slug_taken`.

Mỗi mục là một bản chụp: tên Organization, project, phiên bản, audience, và release note đúng như
lúc được publish. Sau đó không có gì sửa hay xoá được nó, kể cả SQL trực tiếp. Publish lại đúng
release note đó không tốn gì — action tìm thấy mục của chính nó và thành công — còn một quy tắc
định publish nội dung *khác* cho một release, audience và ngôn ngữ đã công khai sẽ thất bại với
`public_changelog_conflict`, thay vì viết đè lên thứ mà ai đó có thể đã đọc. Action changelog
công khai không cần secret hay cấu hình, và không dùng được cho quy tắc chạy theo lịch.

Một release được publish cho một audience bằng nhiều ngôn ngữ, mỗi ngôn ngữ một action, là một
thẻ duy nhất trên `/changelog/{slug}`: thẻ mở bằng ngôn ngữ được publish trước, và liệt kê mọi
ngôn ngữ dưới dạng liên kết gọi bằng chính ngôn ngữ đó, như *English* và *Tiếng Việt*, kèm
`hreflang` và `lang`. Trang riêng của một mục ghi rõ ngôn ngữ của nó. Trang đó không liệt kê các
ngôn ngữ khác: nó được cache dạng bất biến, nên một ngôn ngữ publish sau sẽ không có mặt trên đó.
Phần khung quanh một release note theo ngôn ngữ giao diện của người đọc, nên `<html lang>` là
ngôn ngữ đó, còn bản thân release note mang ngôn ngữ của mục.

Đặt `RELEASEFLOW_PUBLIC_BASE_URL` là địa chỉ dùng để truy cập hệ thống triển khai này (mặc định
`http://localhost:8080`); mọi liên kết công khai — URL chuẩn, các mục RSS, địa chỉ mà một lần chạy
báo lại — đều được dựng từ nó, không bao giờ từ header `Host` của request.

Đặt thêm `RELEASEFLOW_PUBLIC_CHANGELOG_BASE_DOMAIN` sẽ phục vụ mỗi Organization tại
`https://{slug}.{that domain}/`, với `/releases/{entry}` và `/rss.xml` bên dưới. Chỉ GET được
định tuyến theo cách đó, chỉ cho đúng một nhãn ngay dưới domain, và bạn tự lo wildcard DNS cùng
TLS. Để trống thì changelog chỉ trả lời theo đường dẫn.
