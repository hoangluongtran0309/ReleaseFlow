# Nguồn

Một Project thu thập thay đổi từ một hoặc nhiều nguồn: repository GitHub, project GitLab,
team Linear và project Jira Cloud. GitHub, GitLab và Linear gửi webhook có chữ ký; Jira được
đọc theo lịch. Mỗi thay đổi chỉ được ghi một lần, theo nguồn của nó và ID mà chính nguồn đó
đặt cho nó, dù nó đến qua webhook, qua lượt đọc theo lịch hay qua
[import lịch sử](#history-import).

## Kết nối một nguồn {#connect-a-source}

Quản trị viên kết nối nguồn ở trang **Project**, hoặc qua REST:

```text
GET  /api/projects/{projectId}/sources                       (every member)
POST /api/projects/{projectId}/sources                       (administrator; 201)
PUT  /api/projects/{projectId}/sources/{sourceId}/token      (administrator; 204)
```

Body của `POST` tuỳ theo loại nguồn:

| Loại | Body | Kết quả trả về |
| --- | --- | --- |
| GitHub | `{"type": "GITHUB", "owner", "repository"}` | Đường dẫn webhook và một signing secret, **chỉ hiện một lần** |
| GitLab | `{"type": "GITLAB", "apiBaseUrl", "projectPath", "webhookAuthMode"}` | Đường dẫn webhook và một signing secret, **chỉ hiện một lần**; với header có chữ ký, kèm `signingToken`, cũng là secret đó ở dạng signing token của GitLab nhận |
| Linear | `{"type": "LINEAR", "teamId", "webhookSecret", "apiToken"}` | Đường dẫn webhook; Linear tự tạo secret, nên bạn nhập nó vào và không có gì bí mật được trả về |
| Jira | `{"type": "JIRA", "siteUrl", "projectKey", "accountEmail", "apiToken"}` | Không có gì bí mật; Jira không dùng webhook |

Lưu secret được trả về ngay lập tức: nó được lưu mã hoá bằng AES-256-GCM và không bao giờ hiện
lại. Tên chủ repository, tên repository và đường dẫn project GitLab được chuẩn hoá về chữ
thường. Trong một Organization, mỗi repository hoặc project chỉ kết nối được với một Project,
nhưng các Organization khác nhau có thể kết nối cùng một repository, và cùng một tên trên hai
nhà cung cấp là hai nguồn riêng biệt. Hiện chưa ngắt kết nối hay thay thế được một nguồn.

Nguồn không tồn tại trả về `404 source_not_found`, và nguồn đã được kết nối trả về
`409 github_repository_already_connected`, `409 gitlab_project_already_connected`,
`409 linear_team_already_connected` hoặc `409 jira_project_already_connected`. Thành viên gọi
endpoint dành cho quản trị viên nhận `403`.

## Nhận webhook từ GitHub {#receive-github-webhooks}

Trong repository GitHub, mở **Settings → Webhooks → Add webhook** và nhập:

- **Payload URL**: địa chỉ công khai của ReleaseFlow, nối thêm đường dẫn webhook hiển thị cho
  Project, ví dụ `https://releaseflow.example.com/webhooks/github/<webhook-id>`;
- **Content type**: `application/json`;
- **Secret**: signing secret đã lưu khi kết nối repository;
- **Events**: *Let me select individual events* → *Pull requests*.

`POST /webhooks/github/{webhookId}` không cần phiên đăng nhập hay CSRF token. ReleaseFlow
kiểm tra `X-Hub-Signature-256` bằng chính secret của kết nối đó trước khi đọc payload, và lấy
Organization cùng Project từ kết nối, không bao giờ từ payload. Các phản hồi:

| Lần gửi | Phản hồi |
| --- | --- |
| Lớn hơn 1 MiB | `413` `webhook_payload_too_large` |
| Webhook ID không tồn tại, thiếu chữ ký hoặc chữ ký sai | `401` `webhook_signature_invalid` |
| `repository.full_name` không phải repository đã cấu hình | `422` `webhook_repository_mismatch` |
| Lần gửi đã xác minh nhưng JSON không hợp lệ, thiếu `X-GitHub-Event`, hoặc là pull request đã merge nhưng thiếu trường hay thiếu `X-GitHub-Delivery` | `400` `webhook_payload_malformed` |
| `ping` | `200` `{"outcome":"pong"}` |
| Pull request đã merge (`closed` với `merged: true`) | `200` `recorded`, hoặc `duplicate` nếu đã ghi trước đó |
| Mọi sự kiện hoặc hành động khác | `200` `ignored` |

Mỗi pull request đã merge chỉ được lưu một lần cho mỗi repository, nên GitHub gửi lại cũng
không sao, và hai repository của cùng một Project đều có thể có pull request #12. Trang Project
hiển thị thời điểm của lần gửi được chấp nhận gần nhất; thời điểm này xuất hiện ngay khi GitHub
gửi `ping` đầu tiên.

Để thử endpoint trên máy mà không cần GitHub, ký đúng chuỗi byte bạn gửi đi:

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

## Nhận webhook từ GitLab {#receive-gitlab-webhooks}

Trong project GitLab, mở **Settings → Webhooks → Add new webhook** và nhập:

- **URL**: địa chỉ công khai của ReleaseFlow, nối thêm đường dẫn webhook hiển thị cho nguồn, ví
  dụ `https://releaseflow.example.com/webhooks/gitlab/<webhook-id>`;
- với **header có chữ ký** (mặc định): **signing token** hiển thị khi kết nối project, dán vào
  trường **Signing token** của GitLab (`signing_token` trong hooks API), và để trống **Secret
  token**;
- với **header secret token**: secret đã lưu, dán vào **Secret token**;
- **Trigger**: *Merge request events*.

Signing token là secret được viết theo dạng mà Standard Webhooks, và do đó GitLab, yêu cầu:
`whsec_` theo sau là Base64 chuẩn, có padding, của 32 byte của secret. Bản thân secret là
Base64url không padding của cùng các byte đó, dạng mà GitLab từ chối khi dùng làm signing token.
Cả hai chỉ hiện một lần, trên trang và trong trường `signingToken` của response kết nối nguồn.
Để viết signing token từ một secret đã lưu:

```bash
printf 'whsec_%s\n' "$(printf '%s=' "$WEBHOOK_SECRET" | tr '_-' '/+' | base64 -d | base64 -w0)"
```

`POST /webhooks/gitlab/{webhookId}` không cần phiên đăng nhập hay CSRF token, và ReleaseFlow
xác minh lần gửi bằng chính secret của nguồn đó trước khi đọc payload. Cách kiểm tra nào được
áp dụng là do bạn chọn khi kết nối nguồn:

- **Header có chữ ký (Standard Webhooks)**, mặc định: phải có đủ `webhook-id`,
  `webhook-timestamp` và `webhook-signature`; chữ ký phải là `v1,` theo sau là HMAC-SHA256
  dạng Base64 của `id.timestamp.body` với secret — chỉ cần một trong nhiều ứng viên cách nhau
  bởi dấu cách khớp là đủ — và timestamp phải lệch không quá năm phút so với đồng hồ của
  ReleaseFlow.
- **Header secret token**: `X-Gitlab-Token` phải bằng đúng secret.

Organization và Project lấy từ nguồn, không bao giờ từ payload:

| Lần gửi | Phản hồi |
| --- | --- |
| Lớn hơn 1 MiB | `413` `webhook_payload_too_large` |
| Webhook ID không tồn tại, nguồn thuộc loại khác, thiếu, quá hạn hoặc sai bằng chứng | `401` `webhook_signature_invalid` |
| `project.path_with_namespace` không phải project đã kết nối | `422` `webhook_repository_mismatch` |
| Lần gửi đã xác minh nhưng JSON không hợp lệ, hoặc merge request đã merge nhưng thiếu trường | `400` `webhook_payload_malformed` |
| Merge request đã merge (`object_kind: merge_request` với `action: merge`) | `200` `recorded`, hoặc `duplicate` nếu đã ghi trước đó |
| Mọi sự kiện hoặc hành động khác | `200` `ignored` |

Mỗi merge request chỉ được lưu một lần cho mỗi nguồn, nên gửi lại cũng không sao. GitLab chỉ
đặt định danh cho lần gửi khi dùng chế độ ký, nên một thay đổi ghi lại `X-Gitlab-Event-UUID`
khi nó là GUID, và không ghi gì trong trường hợp còn lại.

Chỉ kết nối được những instance có tên trong `RELEASEFLOW_GITLAB_ALLOWED_HOSTS` (mặc định
`gitlab.com`). Mỗi mục là `host` hoặc `host:port`, nghĩa là HTTPS, hoặc một origin đầy đủ như
`http://gitlab.internal:8080`; ghi rõ scheme là cách duy nhất để chấp nhận một instance truy
cập không qua TLS. URL có thông tin đăng nhập, query hoặc fragment bị từ chối với
`400 gitlab_base_url_invalid`, và mọi host khác bị từ chối với `400 gitlab_host_not_allowed`.
ReleaseFlow không bao giờ đi theo chuyển hướng từ một instance GitLab.

Để thử endpoint trên máy mà không cần GitLab, ký đúng chuỗi byte bạn gửi đi:

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

## Nhận webhook từ Linear {#receive-linear-webhooks}

Linear tự tạo signing secret, nên tạo webhook bên Linear trước: trong
**Settings → API → Webhooks**, đặt URL là địa chỉ ReleaseFlow của bạn nối thêm đường dẫn
ReleaseFlow hiển thị cho nguồn, và bật sự kiện **Issues**. Sao chép secret Linear hiển thị
rồi dán nó, cùng một API key, khi kết nối team trong ReleaseFlow.

`POST /webhooks/linear/{webhookId}` không cần phiên đăng nhập hay CSRF token. Một lần gửi tự
chứng minh bằng bốn điều, tất cả đều phải đúng:

- có header `Linear-Delivery`;
- `createdAt` lệch không quá `RELEASEFLOW_LINEAR_WEBHOOK_CLOCK_SKEW` (60 giây) so với đồng hồ
  của ReleaseFlow, ở dạng ISO-8601 hoặc số giây hay mili giây tính từ epoch;
- `organizationId` bằng đúng workspace mà team thuộc về, thông tin ReleaseFlow biết được khi
  kết nối team;
- `Linear-Signature` chứa HMAC-SHA256 dạng hex của body thô.

| Lần gửi | Phản hồi |
| --- | --- |
| Lớn hơn 1 MiB | `413` `webhook_payload_too_large` |
| Webhook ID không tồn tại, nguồn thuộc loại khác, thiếu header, dấu thời gian quá hạn, workspace khác, hoặc chữ ký sai | `401` `webhook_signature_invalid` |
| Body không phải một JSON object, kể cả khi chữ ký đúng: dấu thời gian và workspace được đọc từ body, nên lần gửi đó không bao giờ được xác minh | `401` `webhook_signature_invalid` |
| Issue của một team khác team đã kết nối | `422` `webhook_repository_mismatch` |
| Lần gửi đã xác minh nhưng issue thiếu trường | `400` `webhook_payload_malformed` |
| Issue vừa chuyển sang trạng thái hoàn thành | `200` `recorded`, hoặc `duplicate` nếu đã ghi trước đó |
| Mọi sự kiện, hành động hoặc thay đổi trạng thái khác | `200` `ignored` |

Một issue chỉ được tính khi nó thật sự chuyển sang hoàn thành: `type` là `Issue`, `action` là
`update`, `updatedFrom` có chứa trạng thái, trạng thái mới là `completed` và trạng thái cũ thì
không. Sửa một issue đã hoàn thành từ trước không thay đổi gì.

Linear không đặt cho lần gửi một định danh mà ReleaseFlow có thể ghi lại dưới dạng GUID, nên
thay đổi từ Linear không có delivery ID. Mỗi issue vẫn chỉ được lưu một lần cho mỗi nguồn,
theo UUID của nó, nên gửi lại cũng không sao.

## Đọc Jira theo lịch {#poll-jira}

Phía Jira không cần cấu hình gì. Kết nối một project Jira Cloud bằng site của nó
(`https://your-site.atlassian.net`), project key (`APP`), email của tài khoản ReleaseFlow dùng
để đăng nhập, và một
[Atlassian API token](https://id.atlassian.com/manage-profile/security/api-tokens) của tài
khoản đó, có quyền xem project. ReleaseFlow nhờ Jira xác nhận các thông tin này trước khi lưu
bất cứ thứ gì, và trả về `400 jira_site_invalid` cho site không phải địa chỉ HTTPS
`atlassian.net` không kèm cổng, thông tin đăng nhập, query hay fragment;
`400 jira_token_rejected` khi Jira từ chối tài khoản và token; và `503 jira_unavailable` khi
không kết nối được Jira.

Từ đó trở đi, ReleaseFlow đọc project sau mỗi `RELEASEFLOW_JIRA_POLL_INTERVAL` (năm phút). Mỗi
lượt đọc lấy các issue thuộc nhóm trạng thái Done được cập nhật kể từ lượt đọc trước, lùi lại
thêm `RELEASEFLOW_JIRA_POLL_OVERLAP` (mười phút) để không sót issue nào ở ranh giới, và ghi
lại một lần mỗi issue được giải quyết trong khoảng đó. Issue được giải quyết trước khi kết nối
project sẽ không bao giờ được đọc. Một thay đổi từ Jira:

- được đánh số theo key — `APP-123` là `#123` — và có tiêu đề `APP-123: summary`, với người
  báo cáo là tác giả và `{site}/browse/APP-123` là liên kết;
- không có tệp thay đổi, nên được kiểm tra bằng cùng cách quét từ nhạy cảm như một issue
  Linear;
- bắt đầu bằng key, nên quy tắc tiêu đề Conventional Commit không bao giờ khớp với nó: khi không
  có AI, một thay đổi từ Jira là Unknown và cần review.

Một lượt đọc thất bại vẫn giữ nguyên vị trí và thử lại, và nguồn hiển thị **Kết nối đang lỗi**
cho tới khi có một lượt thành công. Một lỗi mà Jira có thể tự vượt qua, như giới hạn tốc độ hay
sự cố, được thử lại tối đa năm lần, sau khoảng chờ Jira yêu cầu (tối đa một giờ) hoặc sau 2, 4,
8 … giây (tối đa năm phút). Một lượt đọc vẫn thất bại, hoặc bị Jira từ chối thẳng, chẳng hạn
token bị từ chối, sẽ đặt lượt đọc kế tiếp sau **15 phút** chứ không theo
`RELEASEFLOW_JIRA_POLL_INTERVAL`. Khoảng chờ này cố định trong ReleaseFlow, không cấu hình được,
và việc lưu token mới hay khởi động lại đều không kéo lượt đọc đã đặt về sớm hơn. Hộp thay đổi
hiển thị lượt đọc gần nhất và lượt đọc tiếp theo của mỗi project Jira; project Jira không có
import lịch sử.

### Issue Jira liên kết {#linked-jira-issues}

Khi một Project có nguồn Jira, mỗi thay đổi từ GitHub hoặc GitLab được kiểm tra xem có chứa
issue key của project đó trong tiêu đề, mô tả, nhánh đích và tối đa 250 commit message hay
không. Tối đa mười issue được đọc từ Jira và hiển thị trong mục **Issue liên kết** của thay
đổi, được đưa cho AI như bằng chứng không tin cậy, và dùng được trong template của audience:

```mustache
{{#linkedIssues}} [{{key}}]({{url}}) {{title}} ({{type}}, {{status}}){{/linkedIssues}}
```

Commit message chỉ được dùng để tìm key; chúng không bao giờ được lưu lại. Khi không đọc được
commit, hoặc Jira không cho xem một issue mà thay đổi có nhắc tới, thay đổi được thử lại rồi
đánh dấu **Danh sách issue liên kết chưa đầy đủ**, khiến nó bắt buộc phải review. Một key theo
sau là dấu gạch nối, như trong `feature/APP-3-export`, không được coi là có nhắc tới.

## Access token {#access-tokens}

Có access token, ReleaseFlow liệt kê được các tệp mà mỗi pull request hoặc merge request đã
merge chạm vào, nhờ vậy thay đổi vào tệp nhạy cảm luôn được review. Không có token, mọi thay đổi
mới từ GitHub hoặc GitLab đều cần review, vì không loại trừ được điều gì. Linear và Jira cần
token ngay từ lúc kết nối.

Với GitHub, tạo một fine-grained personal access token chỉ giới hạn trong repository, với quyền
**Pull requests: Read-only** (token kiểu classic cần `repo` cho repository private). Với GitLab,
tạo một project access token với scope `read_api`. Với Linear, tạo một API key đọc được team —
key này bắt buộc khi kết nối team, vì việc xác nhận nó cũng là cách ReleaseFlow biết workspace.
Với Jira, API token cũng bắt buộc, và luôn thuộc về email tài khoản đã dùng để kết nối nguồn.
Quản trị viên nhập token dưới nguồn tương ứng trong thẻ của Project trên trang Project, hoặc
gọi:

```text
PUT /api/projects/{projectId}/sources/{sourceId}/token {"token"}   (administrator; 204)
```

ReleaseFlow trước hết nhờ nhà cung cấp xác nhận token — GitHub với các pull request của
repository, GitLab với project, Linear với team (chỉ chấp nhận khi key vẫn truy cập đúng
workspace cũ), Jira với project — rồi mới lưu nó dưới dạng mã hoá bằng AES-256-GCM. Token không
bao giờ được trả về; nguồn chỉ báo `accessTokenConfigured` và `accessTokenUpdatedAt`. Gửi token
mới sẽ thay token cũ. Các lỗi là `400 github_token_rejected`, `400 gitlab_token_rejected`,
`400 linear_token_rejected` hoặc `400 jira_token_rejected` khi nhà cung cấp từ chối token;
`503 github_unavailable`, `503 gitlab_unavailable` hoặc `503 jira_unavailable` khi không kết nối
được; `404 source_not_found`; và `403` với thành viên.

Danh sách tệp thay đổi của GitLab lấy từ các diff của merge request, tối đa mười trang, mỗi
trang một trăm. Một diff mà GitLab báo là bị thu gọn, quá lớn hoặc bị cắt khiến cả danh sách
được coi là không có, chứ không phải là danh sách ngắn hơn, nhờ vậy một tệp chưa từng được thấy
sẽ không bao giờ bị coi là an toàn.

Linear hoàn toàn không có tệp thay đổi, và điều đó khác với việc không liệt kê được tệp: thay vì
bắt review mãi mãi, ReleaseFlow đọc tiêu đề và mô tả của chính issue để tìm `breaking change`,
`migration`, `security`, `auth`, `credential`, `password` và `encryption`, và mỗi lần khớp trở
thành một trigger **Từ nhạy cảm** bắt buộc phải review. Token của Linear cũng là thứ cho phép
ReleaseFlow đọc lại issue, nên thay đổi mang nội dung hiện tại của issue chứ không phải nội
dung ngay lúc nó vừa hoàn thành.

## Import lịch sử {#history-import}

Webhook chỉ mang về những pull request và merge request được merge sau khi nguồn được kết nối.
Để thêm những cái đã merge trước đó, quản trị viên mở bảng **Đồng bộ nguồn** trong Hộp thay đổi
và chọn **Import 90 ngày gần nhất** cho một nguồn đã có access token, hoặc gọi các endpoint
REST bên dưới. Chỉ những nguồn có nhà cung cấp lưu lịch sử mà ReleaseFlow đọc được mới có tuỳ
chọn này; Linear và Jira thì không, và cố yêu cầu sẽ nhận `409 source_import_not_supported`.
Một worker chạy nền đọc lịch sử của nguồn, mỗi trang 100 mục — với GitHub là các pull request
đã đóng, cập nhật gần nhất trước; với GitLab là các merge request đã merge được cập nhật sau
thời điểm bắt đầu khoảng thời gian, cập nhật cũ nhất trước — và ghi lại mọi mục được merge
trong 90 ngày qua qua cùng quy trình xử lý như một lần gửi webhook: tệp thay đổi được liệt kê
và, khi đã cấu hình AI, thay đổi được phân loại một lần. Mục nào đã được ghi trước đó, qua
webhook hay qua một lần import trước, sẽ được bỏ qua, nên import và webhook không bao giờ tạo
bản trùng của nhau. Thay đổi được import mang nhãn **Đã import**.

Import từ GitHub dừng ở pull request đầu tiên được cập nhật trước khoảng thời gian của nó, và cả
hai loại import đều dừng sau 500 thay đổi mới với trạng thái **Dừng ở giới hạn**, vị trí được
lưu lại; **Chạy tiếp** sẽ tiếp tục từ đó với bộ đếm mới, và chạy tiếp một lần import thất bại
cũng vậy. Khi nhà cung cấp giới hạn tốc độ, lần import chờ đúng thời gian nhà cung cấp yêu cầu
(`Retry-After` hoặc thời điểm reset giới hạn, tối đa một giờ); các lỗi tạm thời khác thì chờ
với khoảng chờ tăng dần. Sau năm lần thử, hoặc ngay lập tức khi nhà cung cấp từ chối token,
lần import thất bại, và token bị từ chối khiến nguồn hiện **Kết nối đang lỗi** cho tới khi lưu
token mới. Mỗi nguồn chỉ chạy một lần import tại một thời điểm.

```text
GET  /api/projects/{projectId}/imports                              (every member)
POST /api/projects/{projectId}/sources/{sourceId}/imports           (administrator; 202)
POST /api/projects/{projectId}/sources/{sourceId}/imports/resume    (administrator; 202)
```

Mỗi lần import báo `status` (`PENDING`, `RUNNING`, `RETRY_SCHEDULED`, `COMPLETED`, `PARTIAL`
hoặc `FAILED`), `scannedCount`, `importedCount`, khoảng thời gian, `lastSyncAt`,
`lastErrorCode` và `canResumeImport`. Nguồn đọc theo lịch báo `polled` và `nextPollAt`, và job
gần nhất của nó là lượt đọc gần nhất. Các lỗi là `409 source_token_missing`,
`409 source_sync_in_progress`, `409 source_import_not_resumable` và `404 source_not_found`. Xem
[ADR-0016](adr/0016-integration-sources-and-history-import.md).

Xem thêm [ADR-0017](adr/0017-gitlab-sources.md) về nguồn GitLab,
[ADR-0018](adr/0018-linear-sources.md) về nguồn Linear, vốn không có lịch sử để import, và
[ADR-0019](adr/0019-jira-sources-and-linked-context.md) về việc đọc Jira theo lịch và issue
liên kết.

## ReleaseFlow gọi gì tới mỗi nhà cung cấp {#what-releaseflow-asks-each-provider}

Kết nối một nguồn GitHub hoặc GitLab không gọi gì cả; repository chưa được kiểm tra cho tới khi
lưu token. Sau đó, mọi lời gọi đều diễn ra bên ngoài request webhook, trong một worker chạy nền,
hoặc khi quản trị viên lưu token:

| Nhà cung cấp | Các lời gọi |
| --- | --- |
| GitHub | Xác nhận token với các pull request của repository; liệt kê tệp thay đổi của một pull request đã merge và, khi Project có nguồn Jira, tối đa 250 commit message của nó; đọc các pull request đã đóng khi import lịch sử; đọc và tạo release cho một [action GitHub Release](automation.md#actions) |
| GitLab | Xác nhận token với project; liệt kê diff của một merge request và, khi Project có nguồn Jira, các commit message của nó; đọc các merge request đã merge khi import lịch sử |
| Linear | Xác nhận API key và biết workspace của team khi kết nối team; đọc lại một issue đã hoàn thành |
| Jira | Xác nhận project khi kết nối; tìm các issue vừa chuyển sang Done ở mỗi lượt đọc; đọc các issue mà một thay đổi mã nguồn nhắc tới |

Thời gian chờ, địa chỉ API và chu kỳ đọc theo lịch được liệt kê trong
[Cấu hình](configuration.md#sources).
