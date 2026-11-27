# Vận hành

Chạy ReleaseFlow, theo dõi nó, và cài đặt một phiên bản đã phát hành. Mọi biến được liệt kê
trong [Cấu hình](configuration.md).

## Chạy bằng Docker Compose {#run-with-docker-compose}

`docker-compose.demo.yml` build image của ứng dụng từ bản checkout này và chạy nó cùng
PostgreSQL 17, Prometheus và Grafana để đánh giá trên máy cục bộ; đây không phải một hệ thống
triển khai production. [Chạy demo stack](getting-started.md#run-the-demo-stack) hướng dẫn cách
khởi động nó, còn [Quickstart](getting-started.md#quickstart) chạy một phiên bản đã phát hành
mà không phải build.

Compose từ chối khởi động cho tới khi `RELEASEFLOW_DB_PASSWORD`,
`RELEASEFLOW_CREDENTIAL_MASTER_KEY` và `RELEASEFLOW_GRAFANA_PASSWORD` được đặt. Ứng dụng lắng
nghe trên `http://127.0.0.1:8080` (đổi bằng `RELEASEFLOW_HTTP_PORT`), dashboard vận hành trên
`http://127.0.0.1:3000` và Prometheus trên `http://127.0.0.1:9090`. Cổng cơ sở dữ liệu và cổng
quản trị không được mở ra ngoài. Container chạy với UID `65534`, và health check của nó gọi cổng
quản trị. `docker compose -f docker-compose.demo.yml down --volumes` xoá dữ liệu demo.

## Health và số liệu {#health-and-metrics}

Actuator trả lời trên cổng riêng của nó — mặc định là 8081 trên loopback, đặt bằng
`RELEASEFLOW_MANAGEMENT_PORT` và `RELEASEFLOW_MANAGEMENT_ADDRESS` — và chỉ phục vụ đúng hai thứ
ở đó: `GET /actuator/health`, không kèm chi tiết từng thành phần, và `GET /actuator/prometheus`.
Mọi thứ khác đều bị từ chối, và cổng của ứng dụng không phục vụ thứ nào trong số đó. Giữ cổng này
ở chế độ riêng tư; nó không phải endpoint của sản phẩm.

Bốn số liệu trả lời các câu hỏi vận hành, và mọi label đều lấy từ một tập hữu hạn:

| Số liệu | Label | Trả lời câu hỏi gì |
| --- | --- | --- |
| `releaseflow_classification_completed_total` | `needs_human_review` | Bao nhiêu thay đổi đã được phân loại, và bao nhiêu trong số đó vẫn cần một người |
| `releaseflow_classification_collect_to_complete_seconds` | — | Một thay đổi chờ bao lâu từ lúc đến tới lúc được phân loại |
| `releaseflow_classification_provider_requests_total` | `provider`, `outcome` | AI từ chối hoặc trả về thứ không đọc được thường xuyên tới mức nào |
| `releaseflow_automation_action_executions_total` | `trigger`, `outcome` | Các lần gửi kết thúc ra sao, với không rõ kết quả được tách riêng khỏi thất bại; `trigger` là `release_published`, `manual`, `scheduled_cron`, `upcoming_release_reminder`, `external_webhook` hoặc `correction` |

**Không label nào mang Organization, Project, release, quy tắc, lần chạy, action, model, tham
chiếu bên ngoài hay nội dung lỗi.** Số liệu là góc nhìn trên toàn hệ thống triển khai; một câu hỏi
về riêng một Organization là tính năng của sản phẩm, không phải một label. Mọi chuỗi số liệu đều
tồn tại từ lúc khởi động với giá trị 0, nên một panel hiển thị 0 thay vì "no data".

Demo stack tạo sẵn dashboard *ReleaseFlow Operations* trong Grafana với bảy panel: thay đổi cần
người review, thông lượng phân loại, thời gian từ lúc một thay đổi đến tới lúc được phân loại, tỉ
lệ lỗi của nhà cung cấp AI, tỉ lệ action thất bại theo trigger, các lần gửi không ai xác nhận
được, và Prometheus có kết nối được tới ứng dụng hay không. Prometheus giữ lịch sử bảy ngày. Cảnh
báo, lưu trữ bền vững, chính sách lưu giữ dữ liệu và kiểm soát truy cập là phần việc của một hệ
thống triển khai thật. Xem [ADR-0026](adr/0026-deployment-observability.md).

![Dashboard ReleaseFlow Operations trong Grafana, trong 24 giờ](assets/operations/o-1-grafana.webp)

## Container image {#container-image}

`Dockerfile` build JAR trong một stage JDK rồi chép nó vào một image Temurin 21 JRE chạy với
UID/GID `65534`; health check của nó gọi cổng quản trị. Build riêng image bằng `docker build .`.
Image đọc cùng các biến môi trường `RELEASEFLOW_*` như `./mvnw spring-boot:run`, và cần một cơ
sở dữ liệu PostgreSQL mà nó kết nối được. Phục vụ nó sau HTTPS, đặt
`RELEASEFLOW_SESSION_COOKIE_SECURE=true` và đặt `RELEASEFLOW_PUBLIC_BASE_URL` là địa chỉ công
khai.

## Triển khai production {#deploying-to-production}

Quickstart và demo stack chạy trên một máy để đánh giá. Một hệ thống mà người khác dựa vào cần
những thứ dưới đây, và ReleaseFlow không tự cung cấp thứ nào trong số đó.

**HTTPS và reverse proxy.** Kết thúc TLS ở một reverse proxy đặt trước cổng của ứng dụng và đặt:

```bash
RELEASEFLOW_PUBLIC_BASE_URL=https://releaseflow.example.com
RELEASEFLOW_SESSION_COOKIE_SECURE=true
SERVER_REQUEST_HEADER_FORWARDED_STRATEGY=web-server
```

Cấu hình proxy ghi đè `X-Forwarded-Proto` và `X-Forwarded-Host` bằng giao thức và tên
máy chủ công khai. Tomcat dùng chúng để đánh dấu cookie là `Secure` và cho mọi địa chỉ
tuyệt đối nó tạo ra. Redirect là tương đối, nên trình duyệt hiểu chúng theo địa chỉ công
khai. Triển khai ReleaseFlow tại đường dẫn gốc của địa chỉ này: ReleaseFlow không hỗ trợ
triển khai dưới một đường dẫn con bằng `X-Forwarded-Prefix`.

Theo mặc định, Tomcat tin cậy proxy ở địa chỉ mạng riêng và loopback. Nếu proxy chạy
ở nơi khác, cấu hình `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` cho địa chỉ của proxy.
Với Compose, thêm biến cấu hình forwarded header vào `environment` của service `app`.
Biến cũ `SERVER_FORWARD_HEADERS_STRATEGY` đã bị deprecated nhưng vẫn hoạt động và không ghi
cảnh báo nào vào log, nên hãy chuyển sang tên mới ngay: bỏ biến cũ trước khi đặt biến thay
thế, vì ứng dụng dừng khi khởi động nếu cả hai biến cùng được đặt.

**Những gì internet truy cập được.** Nhà cung cấp cần `/webhooks/`, và người đọc ẩn danh cần
`/changelog/` nếu bạn publish changelog công khai. Người trong nhóm cần phần còn lại, phần này có
thể nằm sau VPN hoặc danh sách IP cho phép. Sau khi Organization của bạn đã được tạo, chặn
`/register` và `POST /api/registrations` ở proxy, vì [đăng ký luôn mở](workspace.md#registration).
Không bao giờ định tuyến cổng quản trị qua proxy.

**Webhook.** ReleaseFlow cũng không giới hạn tần suất yêu cầu đến: một request webhook chỉ bị
giới hạn bởi giới hạn body 1 MiB, được kiểm tra trước tiên. Một lần gửi bị từ chối tới đường dẫn
của một nguồn đã biết được đếm trên trang của nguồn, gộp lại thành khoảng một lần cập nhật dòng
mỗi nguồn mỗi giây dù có bao nhiêu lần gửi
([ADR-0042](adr/0042-source-lifecycle-and-health.md)), nhưng mỗi lần vẫn tốn một lần tra cứu và một
lần kiểm tra chữ ký. Nếu dự kiến có lượng lớn, giới hạn `/webhooks/**` ở proxy.

**Đăng nhập.** ReleaseFlow không giới hạn số lần đăng nhập sai, nên giới hạn tần suất
`POST /login` ở proxy. Phiên đăng nhập nằm trong bộ nhớ của ứng dụng: khởi động lại sẽ đăng xuất
mọi người.

**Một instance.** Các worker nền nhận việc bằng `FOR UPDATE SKIP LOCKED` và được viết để chạy
trong mọi instance ([ADR-0008](adr/0008-durable-change-processing.md)), nhưng phiên đăng nhập
không được chia sẻ giữa các instance và CI không chạy nhiều hơn một instance. Chỉ chạy một
instance, trừ khi bạn đã tự kiểm thử nhiều instance sau sticky session.

**Cơ sở dữ liệu.** Mọi bài test và cả hai stack Compose đều chạy PostgreSQL 17. Cấp cho
ReleaseFlow một cơ sở dữ liệu riêng, do chính user mà nó kết nối sở hữu: Flyway tạo bảng,
ràng buộc và trigger khi ứng dụng khởi động.

**Sao lưu.** Sao lưu cơ sở dữ liệu cùng với `RELEASEFLOW_CREDENTIAL_MASTER_KEY`, và cất chúng ở
hai nơi khác nhau. Một cơ sở dữ liệu được khôi phục mà thiếu key vẫn khởi động được, nhưng mọi
secret đã lưu đều không đọc được. Access token và secret của action có thể nhập lại. Webhook
secret của một nguồn khiến mọi lần gửi tới nguồn đó bị từ chối với
`401 webhook_signature_invalid`, và log ghi `Could not decrypt the webhook secret of source …`,
cho tới khi nó được [đổi](sources.md#rotate-a-webhook-secret) và secret mới được đưa cho nhà cung
cấp. Khôi phục toàn bộ bản dump vào một cơ sở dữ
liệu trống:

```bash
pg_dump --format=custom --file=releaseflow.dump releaseflow
createdb restored && pg_restore --dbname=restored --no-owner releaseflow.dump
```

`pg_restore` tạo các trigger đóng băng release đã publish sau khi đã nạp xong dữ liệu. Chỉ khôi
phục dữ liệu vào một cơ sở dữ liệu mà ứng dụng đã migrate sẵn sẽ bị chính các trigger đó từ chối
với mọi release đã qua bước nháp.

**Nâng cấp.** Một phiên bản mới hơn migrate schema lên khi khởi động, và không migration nào có
thể hoàn tác. Sao lưu trước; quay lại nghĩa là khôi phục bản sao lưu đó với phiên bản cũ, không
bao giờ chạy phiên bản cũ trên cơ sở dữ liệu đã được migrate.

### Nâng cấp khi dùng model suy luận {#upgrading-with-a-reasoning-model}

Từ 1.1.0, mọi request phân loại dùng cùng một temperature, là `0` trừ khi
`RELEASEFLOW_AI_TEMPERATURE` đặt giá trị khác
([ADR-0035](adr/0035-evidence-bound-notes-and-audience-relevance.md),
[ADR-0038](adr/0038-configurable-ai-temperature.md)); 1.0 không gửi temperature. Model chỉ nhận
temperature mặc định từ chối request có ghi temperature: các model suy luận của OpenAI đều như
vậy, và các model Claude ra mắt sau Opus 4.6 cũng thế cho đến khi request tới Anthropic thôi gửi
temperature (xem [Nâng cấp khi dùng Anthropic](#upgrading-with-anthropic)).

**Không cần làm gì.** Khi nhà cung cấp từ chối temperature, ReleaseFlow gửi lại đúng request đó
một lần nữa nhưng không có temperature, và không gửi temperature trong mọi request sau đó cho đến
khi khởi động lại ([ADR-0039](adr/0039-ai-temperature-fallback.md)). Log ghi điều này một lần:

```text
OpenAI model o4-mini refused the temperature, so classification requests leave it out until ReleaseFlow restarts. Set RELEASEFLOW_AI_TEMPERATURE=default to avoid the first refusal.
```

Mỗi lần khởi động tốn một request bị từ chối. Để bỏ qua request đó, đặt:

```bash
RELEASEFLOW_AI_TEMPERATURE=default
```

Khi đó request không gửi temperature ngay từ request đầu tiên, như mọi request trước đây. Với
Docker Compose, đặt biến này trong file `.env` cạnh file Compose, file này sẽ chuyển nó vào ứng
dụng, và gửi `0` khi biến không được đặt. Bản triển khai có model nhận temperature thì gửi `0`
và không gặp lần từ chối nào.

### Nâng cấp khi dùng Anthropic {#upgrading-with-anthropic}

Từ 1.3.0, ReleaseFlow gọi Claude với phiên bản API `2026-10-21` của Anthropic, vốn không nhận
temperature ([ADR-0045](adr/0045-anthropic-api-version-2026-10-21.md)). Với bản triển khai có
`RELEASEFLOW_AI_PROVIDER=anthropic`:

- **Không cần đổi cấu hình nào.** Request không mang temperature, dù
  `RELEASEFLOW_AI_TEMPERATURE` đặt gì. Một số khác `0` được ghi một cảnh báo khi khởi động và có
  thể bỏ đi:

  ```text
  RELEASEFLOW_AI_TEMPERATURE is ignored for anthropic: Anthropic API version 2026-10-21 takes no temperature, so Claude samples with its default. Remove the setting to silence this warning.
  ```

- **Với Claude 4.7 trở về sau, việc phân loại vẫn như trước.** Request đầu tiên sau mỗi lần khởi
  động không còn bị từ chối vì temperature, nên cảnh báo đi kèm cũng không còn.
- **Với Claude 4.6 trở về trước** (như Sonnet 4.6, Opus 4.6 hoặc Haiku 4.5), việc phân loại chạy ở
  temperature mặc định của model, `1.0`, thay vì `0`, nên các note dao động giữa các lần chạy
  nhiều hơn ở 1.2: một thay đổi được phân loại lại có thể được viết khác đi, hoặc, khi bằng chứng
  còn mỏng, nhận Category khác. Không cấu hình nào đưa `0` trở lại với Anthropic; OpenAI và
  DeepSeek vẫn nhận giá trị này. Quy tắc, Review bắt buộc và một request cho mỗi thay đổi không
  đổi.
- **API key được gửi trong `Authorization: Bearer <key>`** thay vì `x-api-key`, kèm
  `anthropic-version: 2026-10-21`. Proxy hoặc gateway ở `RELEASEFLOW_ANTHROPIC_BASE_URL` đọc hoặc
  đặt `x-api-key`, hoặc kiểm tra `anthropic-version`, phải chấp nhận các header này trước khi nâng
  cấp; gateway chuyển tiếp tới Amazon Bedrock chỉ nhận `2026-10-21` với Claude 4.7 trở về sau.
  Bản thân Claude API nhận cả hai header.

SDK của Anthropic trong JAR và image giờ theo Apache License 2.0 thay vì MIT. SDK không kèm file
`NOTICE`, và các file giấy phép của nó vẫn nằm trong các jar của nó.

### Nâng cấp lên phiên bản có bảng thuật ngữ {#upgrading-to-the-glossary}

Phiên bản thêm [bảng thuật ngữ](release-notes.md#glossary) (Flyway `V33`) không thay đổi gì cho
đến khi quản trị viên thêm thuật ngữ: Organization chưa có bảng thuật ngữ gửi DeepL đúng những yêu
cầu như trước, và không note nào bị dịch lại. Để giữ thuật ngữ sau khi nâng cấp:

1. Mở **Bảng thuật ngữ**, chọn những thuật ngữ gợi ý mà người đọc của bạn giữ bằng tiếng Anh, và
   thêm những thuật ngữ khác, mỗi mục viết đúng như cần hiển thị. Thêm bản dịch cố định cho thuật
   ngữ luôn phải dịch thành cùng một cụm từ trong một ngôn ngữ.
2. Đưa một release đã duyệt về nháp rồi duyệt lại, hoặc duyệt release tiếp theo: bản dịch tạo từ
   lúc đó dùng bảng thuật ngữ. Note đã publish không bao giờ thay đổi.
3. Xem trang release: mỗi ngôn ngữ cho biết bảng thuật ngữ có ở trạng thái **Đã gửi kèm mọi bản
   dịch** hay không. Trạng thái đó nghĩa là DeepL đã nhận bảng thuật ngữ, không phải mọi thuật ngữ
   đều được giữ, nên đọc lại note, và thêm cụm mà DeepL đã dịch, như `container images`, thành một
   thuật ngữ giữ nguyên riêng.

**Với DeepL API Free, chỉ một Organization dùng được bảng thuật ngữ.** ReleaseFlow dùng một
glossary của DeepL cho mỗi Organization, và tài khoản API Free chỉ có số glossary mà gói cho phép:
là một khi được thử. Organization đồng bộ đầu tiên dùng nó; lần đồng bộ của mọi Organization khác
thất bại với `quota_exceeded` và note của chúng được dịch mà không có bảng thuật ngữ, như trang
release và trang Bảng thuật ngữ của chúng cho biết. Một bản cài đặt phục vụ nhiều Organization có
bảng thuật ngữ cần gói DeepL có đủ số glossary đó. Glossary do công cụ khác tạo trong tài khoản
cũng chiếm một chỗ.

## Artifact đã phát hành {#published-artifacts}

Đây là các bản phát hành của chính ReleaseFlow, khác với những release mà nó viết release note,
được liệt kê trên [trang Releases](https://github.com/hoangluongtran0309/ReleaseFlow/releases).
Phần này mô tả những gì workflow `Release` publish cho mỗi tag.

Một bản phát hành là một tag, và tag chính là phiên bản: `pom.xml` chỉ giữ giá trị giữ chỗ
`0.0.0-SNAPSHOT` trong `${revision}`, và workflow `Release` build commit được tag với
`-Drevision=<version>`. Push `v<version>` sẽ chạy workflow đó; workflow từ chối một tag không
phải tổ tiên của `main`, hoặc một tag mà `CHANGELOG.md` không có mục kèm ngày tương ứng — nên một
tag nhầm sẽ thất bại thay vì được phát hành. Sau đó nó chạy lại toàn bộ bộ test, kiểm tra JAR có
mang đúng phiên bản, build và quét image, rồi publish:

- `releaseflow-<version>.jar`, JAR chạy được;
- `releaseflow-<version>-sbom.cdx.json`, SBOM CycloneDX của image, liệt kê cùng lúc các phụ thuộc
  Java và các gói của base image;
- `compose.yaml` và `quickstart.sh`, bộ [Quickstart](getting-started.md#quickstart), với image
  được ghim theo digest mà bản phát hành này đã push;
- `SHA256SUMS`, `LICENSE` và `NOTICE`;
- image trên GHCR, gắn tag `<version>`, `<major>.<minor>`, `<major>` và `latest`;
- một provenance attestation cho image, JAR và bộ quickstart;
- một GitHub release với nội dung là mục `CHANGELOG.md` của phiên bản, theo sau là digest của
  image, các lệnh quickstart, và cách kiểm tra các tệp tải về.

Một tag `v<version>-rc.<n>` trên `release/<version>` hoặc `hotfix/<version>` publish một release
candidate theo cùng cách, được đánh dấu là pre-release, với image chỉ gắn tag
`<version>-rc.<n>` để không có gì đang theo `latest` bị chuyển sang nó. Workflow cũng có thể được
chạy bằng tay cho một tag đã có sẵn.

Để phát hành một phiên bản, xem [Releasing](../CONTRIBUTING.md#releasing): hai lệnh,
`scripts/release.sh start <version>` và, sau khi pull request của nó được merge,
`scripts/release.sh finish <version>`.

Kiểm tra những gì bạn đã tải về:

```bash
sha256sum --check SHA256SUMS
gh attestation verify releaseflow-<version>.jar \
    --repo hoangluongtran0309/ReleaseFlow
```

Ghim một hệ thống triển khai theo digest thay vì theo một tag có thể thay đổi:

```bash
docker pull ghcr.io/hoangluongtran0309/releaseflow@sha256:<digest>
```
