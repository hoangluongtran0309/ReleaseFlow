# Cấu hình

ReleaseFlow đọc cấu hình từ các biến môi trường `RELEASEFLOW_*`, được
`src/main/resources/application.properties` ánh xạ sang thuộc tính của Spring. JAR, container
image và `./mvnw spring-boot:run` đều đọc cùng các biến này. Khoảng thời gian dùng định dạng
ISO-8601 (`PT10S`, `P7D`), và danh sách cách nhau bởi dấu phẩy.

Chỉ bảng đầu tiên là bắt buộc. Mọi biến khác đều có giá trị mặc định dùng được cho một hệ thống
triển khai đơn lẻ trên máy cục bộ.

## Bắt buộc {#required}

| Biến | Là gì |
| --- | --- |
| `RELEASEFLOW_DB_URL` | JDBC URL của một cơ sở dữ liệu PostgreSQL riêng, ví dụ `jdbc:postgresql://localhost:5432/releaseflow` |
| `RELEASEFLOW_DB_USERNAME` | Người dùng cơ sở dữ liệu |
| `RELEASEFLOW_DB_PASSWORD` | Mật khẩu cơ sở dữ liệu |
| `RELEASEFLOW_CREDENTIAL_MASTER_KEY` | Base64 của đúng 32 byte ngẫu nhiên (`openssl rand -base64 32`). Nó mã hoá mọi webhook secret, access token và secret của action đã lưu. Ứng dụng không khởi động được nếu thiếu nó, và mất nó thì mọi secret đã lưu đều không đọc được nữa |

Không bao giờ commit hay chia sẻ các giá trị này.

## Web và phiên đăng nhập {#web-and-session}

| Biến | Mặc định | Tác dụng |
| --- | --- | --- |
| `RELEASEFLOW_SESSION_COOKIE_SECURE` | `false` | Đánh dấu cookie phiên đăng nhập là `Secure`; đặt thành `true` mỗi khi ứng dụng được phục vụ qua HTTPS |
| `RELEASEFLOW_INVITATION_TTL` | `P7D` | Liên kết mời dùng được trong bao lâu |
| `RELEASEFLOW_UI_LANGUAGES` | `en,vi` | Các ngôn ngữ giao diện được cung cấp; ngôn ngữ đầu tiên là ngôn ngữ dự phòng, và `?lang=` chỉ nhận các ngôn ngữ này. Xem [Ngôn ngữ giao diện](workspace.md#interface-language) |
| `RELEASEFLOW_OUTPUT_LANGUAGES` | `en,vi` | Các ngôn ngữ đầu ra được gợi ý trong biểu mẫu. Mọi language tag hợp lệ vẫn được chấp nhận. Xem [Ngôn ngữ đầu ra](workspace.md#output-language) |
| `RELEASEFLOW_PUBLIC_BASE_URL` | `http://localhost:8080` | Địa chỉ dùng để truy cập hệ thống triển khai này. Mọi liên kết công khai tuyệt đối đều được dựng từ nó, không bao giờ từ request |
| `RELEASEFLOW_PUBLIC_CHANGELOG_BASE_DOMAIN` | (trống) | Khi được đặt, phục vụ thêm changelog của mỗi Organization tại `https://{slug}.{domain}/`. Xem [Changelog công khai](automation.md#public-changelog) |

## Nguồn {#sources}

| Biến | Mặc định | Tác dụng |
| --- | --- | --- |
| `RELEASEFLOW_WEBHOOK_MAX_BODY_BYTES` | `1048576` | Kích thước lớn nhất của một lần gửi từ bất kỳ nhà cung cấp nào |
| `RELEASEFLOW_GITHUB_API_BASE_URL` | `https://api.github.com` | Địa chỉ gọi GitHub |
| `RELEASEFLOW_GITHUB_TIMEOUT` | `PT10S` | Thời gian chờ kết nối và đọc với GitHub |
| `RELEASEFLOW_GITLAB_ALLOWED_HOSTS` | `gitlab.com` | Các instance GitLab mà một nguồn được phép trỏ tới: `host`, `host:port` (HTTPS), hoặc một origin đầy đủ như `http://gitlab.internal:8080` |
| `RELEASEFLOW_GITLAB_TIMEOUT` | `PT5S` | Thời gian chờ kết nối và đọc với GitLab |
| `RELEASEFLOW_GITLAB_WEBHOOK_CLOCK_SKEW` | `PT5M` | Một lần gửi có chữ ký từ GitLab được phép cũ tới mức nào |
| `RELEASEFLOW_LINEAR_API_BASE_URL` | `https://api.linear.app` | Địa chỉ gọi GraphQL API của Linear |
| `RELEASEFLOW_LINEAR_TIMEOUT` | `PT5S` | Thời gian chờ kết nối và đọc với Linear |
| `RELEASEFLOW_LINEAR_WEBHOOK_CLOCK_SKEW` | `PT60S` | Một lần gửi có chữ ký từ Linear được phép cũ tới mức nào |
| `RELEASEFLOW_JIRA_TIMEOUT` | `PT10S` | Thời gian chờ kết nối và đọc với Jira |
| `RELEASEFLOW_JIRA_POLL_INTERVAL` | `PT5M` | Bao lâu đọc mỗi project Jira một lần |
| `RELEASEFLOW_JIRA_POLL_OVERLAP` | `PT10M` | Mỗi lượt đọc lùi lại bao xa so với lượt đọc trước |
| `RELEASEFLOW_JIRA_DESCRIPTION_MAX_CHARACTERS` | `8000` | Độ dài tối đa của mô tả Jira được giữ làm bằng chứng |
| `RELEASEFLOW_JIRA_API_BASE_URL` | (trống) | Chỉ để thử nghiệm cục bộ: gửi mọi lời gọi Jira tới đây thay vì site của chính nguồn |

Xem [Nguồn](sources.md).

## Phân loại {#classification}

| Biến | Mặc định | Tác dụng |
| --- | --- | --- |
| `RELEASEFLOW_SENSITIVE_PATHS` | xem bên dưới | Thay danh sách sensitive path nền. Một mẫu không hợp lệ hoặc danh sách rỗng khiến ứng dụng không khởi động |
| `RELEASEFLOW_CONTEXT_THRESHOLD` | `60` | Điểm ngữ cảnh (0–100) mà dưới mức đó thay đổi cần review |
| `RELEASEFLOW_DUPLICATE_THRESHOLD` | `0.82` | Độ tương đồng (0–1) từ mức đó trở lên thì hai thay đổi được ghi nhận là có thể trùng lặp |

Danh sách sensitive path nền mặc định là:

```text
**/security/**,**/auth/**,**/*Security*,**/*Auth*,**/*Credential*,**/*Password*,
**/db/migration/**,**/*.sql,**/pom.xml,**/package.json,**/package-lock.json,
.github/workflows/**,**/Dockerfile,**/application*.properties,**/*.env*
```

Xem [Thay đổi và review](changes.md).

## AI {#ai}

AI không bắt buộc. Chọn một nhà cung cấp và cung cấp key cùng model. Không có model mặc định;
chọn một model hỗ trợ đầu ra JSON có cấu trúc.

| Biến | Mặc định | Tác dụng |
| --- | --- | --- |
| `RELEASEFLOW_AI_PROVIDER` | (trống: không dùng AI) | `openai`, `anthropic` hoặc `deepseek` |
| `RELEASEFLOW_AI_TIMEOUT` | `PT60S` | Thời gian tối đa cho mỗi request tới AI |
| `RELEASEFLOW_OPENAI_API_KEY`, `RELEASEFLOW_OPENAI_MODEL` | (trống) | Key và model của OpenAI |
| `RELEASEFLOW_OPENAI_BASE_URL` | `https://api.openai.com/v1` | Địa chỉ API của OpenAI |
| `RELEASEFLOW_ANTHROPIC_API_KEY`, `RELEASEFLOW_ANTHROPIC_MODEL` | (trống) | Key và model của Anthropic |
| `RELEASEFLOW_ANTHROPIC_BASE_URL` | `https://api.anthropic.com` | Địa chỉ API của Anthropic |
| `RELEASEFLOW_DEEPSEEK_API_KEY`, `RELEASEFLOW_DEEPSEEK_MODEL` | (trống) | Key và model của DeepSeek |
| `RELEASEFLOW_DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | Địa chỉ API của DeepSeek |

Ứng dụng không khởi động được khi nhà cung cấp đã chọn thiếu key hoặc model, khi tên nhà cung cấp
không hợp lệ, hoặc khi key hay model của một nhà cung cấp được đặt trong khi
`RELEASEFLOW_AI_PROVIDER` để trống. Xem
[Phân loại và tóm tắt bằng AI](changes.md#ai-classification-and-summaries).

## Dịch thuật {#translation}

| Biến | Mặc định | Tác dụng |
| --- | --- | --- |
| `RELEASEFLOW_TRANSLATION_PROVIDER` | `disabled` | `deepl` bật tính năng dịch release note. Một giá trị khác `disabled` và `deepl` làm ứng dụng không khởi động, và `disabled` cũng vậy khi `RELEASEFLOW_DEEPL_API_KEY` đang được đặt |
| `RELEASEFLOW_TRANSLATION_TIMEOUT` | `PT30S` | Thời gian tối đa cho mỗi request dịch |
| `RELEASEFLOW_DEEPL_API_KEY` | (trống) | Key của DeepL; key kết thúc bằng `:fx` dùng API miễn phí. Bắt buộc với `deepl`, và một key được đặt khi provider không phải `deepl` làm ứng dụng không khởi động, nên muốn tắt dịch thì phải xoá cả key |
| `RELEASEFLOW_DEEPL_BASE_URL` | (trống) | Ghi đè host được chọn dựa trên key |

Xem [Ngôn ngữ release note](release-notes.md#release-note-languages).

## Automation {#automation}

| Biến | Mặc định | Tác dụng |
| --- | --- | --- |
| `RELEASEFLOW_AUTOMATION_TIMEOUT` | `PT10S` | Thời gian tối đa cho mỗi lần gửi ra khỏi ứng dụng |
| `RELEASEFLOW_AUTOMATION_EMAIL_FROM` | (trống) | Địa chỉ người gửi. Thiếu nó và SMTP host thì không bật được action email |
| `RELEASEFLOW_SMTP_HOST` | (trống) | SMTP server |
| `RELEASEFLOW_SMTP_PORT` | `587` | Cổng SMTP |
| `RELEASEFLOW_SMTP_USERNAME`, `RELEASEFLOW_SMTP_PASSWORD` | (trống) | Thông tin đăng nhập SMTP |
| `RELEASEFLOW_SMTP_AUTH` | `false` | Dùng xác thực SMTP |
| `RELEASEFLOW_SMTP_STARTTLS` | `false` | Dùng STARTTLS |
| `RELEASEFLOW_SLACK_ALLOWED_HOSTS` | `hooks.slack.com,hooks.slack-gov.com` | Những origin duy nhất mà một Slack webhook URL được phép trỏ tới |
| `RELEASEFLOW_NOTION_API_BASE_URL` | `https://api.notion.com` | Địa chỉ gọi Notion |
| `RELEASEFLOW_NOTION_VERSION` | `2026-03-11` | Phiên bản Notion API dùng để viết trang. Chỉ đổi nó cùng lúc với body mà ứng dụng gửi đi |
| `RELEASEFLOW_CONFLUENCE_API_BASE_URL` | (trống) | Chỉ để thử nghiệm cục bộ: đứng thay cho Confluence Cloud. Site vẫn được kiểm tra, và liên kết được ghi lại vẫn trỏ tới site đó |
| `RELEASEFLOW_TEAMS_API_BASE_URL` | (trống) | Chỉ để thử nghiệm cục bộ: đứng thay cho Power Platform. Đường dẫn và chữ ký của callback được giữ nguyên |
| `RELEASEFLOW_ZENDESK_API_BASE_URL` | (trống) | Chỉ để thử nghiệm cục bộ: đứng thay cho `{subdomain}.zendesk.com`. Subdomain vẫn được kiểm tra |
| `RELEASEFLOW_AUTOMATION_TRIGGER_DELAY` | `PT15S` | Bao lâu ReleaseFlow tìm một lần các quy tắc theo lịch hoặc nhắc trước đã đến hạn |
| `RELEASEFLOW_AUTOMATION_TRIGGER_BATCH_SIZE` | `20` | Mỗi lượt tìm đó được đặt tối đa bao nhiêu lần chạy |
| `RELEASEFLOW_AUTOMATION_REMINDER_LOOKAHEAD` | `PT15S` | Một quy tắc nhắc trước không báo trước chút nào sẽ nhìn trước bao xa |
| `RELEASEFLOW_AUTOMATION_WEBHOOK_CLOCK_SKEW` | `PT5M` | Timestamp của một lời gọi có chữ ký được phép lệch bao xa so với hiện tại |
| `RELEASEFLOW_AUTOMATION_WEBHOOK_MAX_BODY_BYTES` | `65536` | Kích thước body lớn nhất của một lời gọi có chữ ký |

Action Notion, Confluence, Microsoft Teams hoặc Zendesk không cần gì thêm từ hệ thống triển khai
ngoài các giá trị mặc định này: token, URL callback hoặc client secret được nhập riêng cho từng
action. Xem [Automation](automation.md).

## Cổng quản trị {#management-port}

| Biến | Mặc định | Tác dụng |
| --- | --- | --- |
| `RELEASEFLOW_MANAGEMENT_PORT` | `8081` | Cổng của endpoint health Actuator và Prometheus |
| `RELEASEFLOW_MANAGEMENT_ADDRESS` | `127.0.0.1` | Địa chỉ mà cổng đó lắng nghe; chỉ chuyển nó sang một mạng riêng |
| `RELEASEFLOW_MANAGEMENT_HEALTH_MAIL` | `false` | Đưa SMTP server vào health, cho hệ thống triển khai không thể hoạt động thiếu email |

Xem [Vận hành](operations.md#health-and-metrics).

## Docker Compose và quickstart {#docker-compose-and-the-quickstart}

Các biến này được `docker-compose.demo.yml` và `compose.yaml` của quickstart đọc, không phải ứng
dụng.

| Biến | Mặc định | Dùng bởi | Tác dụng |
| --- | --- | --- | --- |
| `RELEASEFLOW_HTTP_PORT` | `8080` | cả hai | Cổng trên máy host của ứng dụng, mở trên `127.0.0.1` |
| `RELEASEFLOW_DB_NAME` | `releaseflow` | demo | Tên cơ sở dữ liệu |
| `RELEASEFLOW_GRAFANA_USER` | `admin` | demo | Tài khoản quản trị Grafana |
| `RELEASEFLOW_GRAFANA_PASSWORD` | (bắt buộc) | demo | Mật khẩu quản trị Grafana |
| `RELEASEFLOW_GRAFANA_PORT` | `3000` | demo | Cổng trên máy host của Grafana |
| `RELEASEFLOW_PROMETHEUS_PORT` | `9090` | demo | Cổng trên máy host của Prometheus |
| `RELEASEFLOW_IMAGE` | (do bản phát hành ghi vào) | quickstart | Image, được ghim theo digest mà bản phát hành đã push |

Cả hai file Compose chỉ chuyển cho ứng dụng những biến được liệt kê trong
[`.env.example`](../.env.example): cơ sở dữ liệu, master key, cookie phiên đăng nhập, changelog
công khai, AI (key và model của OpenAI, Anthropic và DeepSeek), DeepL, và SMTP.
Mọi biến khác giữ giá trị mặc định của ứng dụng trong các stack đó; để đổi, thêm nó
vào `environment` của service `app` trong file Compose. Chuyển một giá trị rỗng thay vào đó sẽ
khiến ứng dụng dừng, đó là lý do sensitive path, các ngưỡng review và các ngôn ngữ giao diện bị
để ngoài. Demo stack còn đặt `RELEASEFLOW_MANAGEMENT_ADDRESS=0.0.0.0` bên trong mạng riêng của
nó để Prometheus thu thập được số liệu từ ứng dụng, và không mở gì ra ngoài trên cổng đó.

## Chỉ dùng khi build và test {#build-and-test-only}

| Biến | Dùng bởi | Tác dụng |
| --- | --- | --- |
| `RELEASEFLOW_VERSION`, `RELEASEFLOW_REVISION` | Build argument của `Dockerfile` | Phiên bản và commit được workflow `Release` ghi vào image |
| `RELEASEFLOW_E2E_BASE_URL` | Playwright | Trỏ bộ test trình duyệt tới một ứng dụng đang chạy sẵn |
