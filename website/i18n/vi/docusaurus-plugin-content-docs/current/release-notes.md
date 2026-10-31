# Release note

Một release gom các thay đổi đã review. Khi release được duyệt, ReleaseFlow viết một release
note cho mỗi audience ở mỗi ngôn ngữ release note; khi release được publish, các release note
đó được cố định lại.

## Audience {#audiences}

Mỗi release đã duyệt có một release note cho mỗi **audience**. Quản trị viên quản lý audience ở
trang **Audience** hoặc qua `/api/audiences`. Mỗi Organization bắt đầu với bốn audience mẫu,
được đặt tên theo ngôn ngữ đầu ra của nó (tiếng Anh hoặc tiếng Việt):

| Mã | Viết cho | Template | Phần cuối |
| --- | --- | --- | --- |
| `operator` | rủi ro vận hành, cách rollback, và cách sản phẩm được triển khai và chạy | dòng tiêu đề kèm liên kết pull request và narrative, rồi vì sao, chi tiết, giới hạn, và bước chuyển đổi dưới nhãn theo loại của nó | liên kết so sánh và hướng dẫn nâng cấp |
| `contributor` | chi tiết triển khai và thay đổi mã nguồn | như operator, nhưng là chi tiết triển khai thay cho chi tiết chung | như operator |
| `end_user` | ngôn ngữ dễ hiểu, những gì người dùng nhận thấy | dòng tiêu đề và narrative, cùng bước chuyển đổi chỉ khi nó bắt buộc | không có |
| `support` | những gì khách hàng có thể hỏi, và nên trả lời họ thế nào | dòng tiêu đề kèm liên kết và narrative, bước chuyển đổi dưới nhãn theo loại của nó, và giới hạn | không có |

![Trang Audiences: ba preset và các audience do administrator thêm, một audience đang mở trong trình sửa](assets/release-notes/rn-1-audiences.webp)

Một audience có một **mã** (chữ thường, chữ số và dấu gạch dưới, bắt đầu bằng chữ cái; cố định
sau khi tạo), một **tên hiển thị**, một **ý định truyền đạt** mà AI làm theo khi viết narrative
cho audience đó, một **template** cho mỗi thay đổi, và một [phần cuối](#footer) cho cả note. Ý
định của mỗi audience mẫu kết thúc bằng câu nói khi nào
một thay đổi không liên quan tới audience đó: với người dùng cuối, đó là các thay đổi về build,
CI, dependency, test và nội bộ mà họ không thấy được. Ý định của operator chỉ yêu cầu nêu điều cần
theo dõi sau khi triển khai khi chính thay đổi hoặc [ngữ cảnh Project](changes.md#project-context)
nêu ra. Template là
[Mustache](https://mustache.github.io/mustache.5.html) và dựng một thay đổi thành Markdown với
các biến `whatChanged`, `whyChanged`, `technicalDetail`, `migrationStep`, `migrationKind`,
`limitations`, `narrative`, `headline`, `relevance`, `brief`, `reference`, `pullRequestNumber`,
`pullRequestUrl` và `linkedIssues` — một danh sách mà mỗi phần
tử có `key`, `title`, `type`, `status` và `url`, được escape như mọi văn bản khác lấy từ công cụ
theo dõi issue, đúng như khi được đọc lúc thay đổi được ghi nhận
([Issue Jira liên kết](sources.md#linked-jira-issues)). `migrationKind` là `required`,
`conditional`, `recommended`, hoặc `unspecified` cho bước được ghi trước khi có loại, và để trống
khi không có bước (xem [Phân loại bằng AI](changes.md#ai-classification-and-summaries)). Vì
Mustache không so sánh được giá trị, các section `migrationRequired`, `migrationConditional`,
`migrationRecommended` và `migrationUnspecified` mỗi cái đúng với một loại. `headline` là dòng
tiêu đề một dòng của audience, hoặc `whatChanged` khi không có, nên luôn có giá trị; `relevance` là
`brief` hoặc `full`, và section `brief` đúng với một thay đổi chỉ cần nêu ngắn gọn (xem
[Mỗi audience đọc gì](#what-each-audience-reads)). Các giá trị của bản tóm tắt, `headline` và
`narrative` giữ nguyên Markdown, nhưng mọi HTML thô trong đó đều được escape, nên một
placeholder như `<id>` hiện đúng như đã viết ở mọi nơi note được đọc. `reference` là cách nguồn của thay đổi
gọi tên nó: `#12` cho pull request GitHub,
`!12` cho merge request GitLab, và key của issue, như `APP-12` hay `ENG-12`, cho Jira và Linear;
`pullRequestNumber` là con số trần, bất kể nguồn nào. Một section như
`{{#narrative}} — {{.}}{{/narrative}}` biến mất khi giá trị rỗng. **Xem thử** dựng một thay đổi
mẫu. Template được kiểm tra khi lưu: nó phải biên dịch được, chỉ dùng các biến trên, và dùng
`{{narrative}}` thay vì gọi tên một audience khác.

Các template đi kèm mở đầu bằng `**{{headline}}**`, và các template chi tiết liên kết một thay
đổi bằng `[{{reference}}]({{pullRequestUrl}})`. Template của operator, contributor và support in
giới hạn của một thay đổi trên một dòng riêng, **Giới hạn** (**Limitations**), để những gì thay đổi
chưa làm không bị lẫn vào phần chi tiết; template của người dùng cuối không in chúng. Chúng ghi nhãn bước chuyển đổi theo loại của nó:
**Bước bắt buộc**, **Nếu áp dụng cho bạn**, **Bước khuyến nghị**, hoặc nhãn trung tính **Cách
chuyển đổi** cho bước không ghi rõ loại, nên một bước không ai gọi là bắt buộc sẽ không bao giờ
được trình bày như vậy. Các dòng chi tiết nằm trong `{{^brief}}`, nên một thay đổi ngắn gọn vẫn giữ
dòng tiêu đề, narrative, và bước bắt buộc, vốn luôn được in dù mức liên quan là gì. Audience tạo
trước khi có các template này vẫn giữ template của mình cho tới khi preset được đặt lại, và một
Organization tạo trước khi có preset `support` có thể thêm audience mã `support` rồi đặt lại nó về
preset.

### Mỗi audience đọc gì {#what-each-audience-reads}

Với từng audience, AI cho biết thay đổi liên quan tới audience đó tới mức nào: `none` (người đọc
này sẽ không nhận ra và cũng không cần nó), `brief` (chỉ dòng tiêu đề là đủ), hoặc `full`. AI cũng
viết một **dòng tiêu đề**, một dòng tối đa 20 từ, bên cạnh narrative. Dòng tiêu đề dài hơn, kéo
qua nhiều dòng, hoặc nhận xét về bằng chứng còn thiếu sẽ bị bỏ, và mọi câu trong narrative nhận
xét như vậy ("not provided", "not specified") cũng bị bỏ. Mức liên quan không xác định được coi là
`full`.

Release note bỏ qua một thay đổi có mức `none` với audience của nó, trừ khi thay đổi đó phá vỡ
tương thích: thay đổi phá vỡ tương thích có mặt trong mọi note, và `none` trên nó được coi là
`brief`. Thay đổi không có narrative cho audience (không có AI, AI lỗi, hoặc audience được thêm
sau) được đưa vào đầy đủ.

Một **thay đổi bảo mật** được liệt kê trong mục **🔒 Bảo mật**, ngay sau các thay đổi phá vỡ
tương thích, bất kể category của nó: một bản sửa lỗi bảo mật vẫn là Fix ở mọi nơi khác. Thay đổi
vừa phá vỡ tương thích vừa bảo mật chỉ được liệt kê một lần, trong mục phá vỡ tương thích, và mở
đầu bằng **🔒 Bảo mật:** bất kể template của audience in gì. Thay đổi bảo mật có mặt trong note
của operator, contributor và support kể cả khi nó là `none` với họ, và khi đó được coi là `brief`;
với người dùng cuối hoặc một audience tự tạo, nó theo mức liên quan như mọi thay đổi khác, trừ khi
nó cũng phá vỡ tương thích. Xem
[ADR-0037](adr/0037-security-changes-and-release-note-structure.md).

Các con số và cảnh báo phá vỡ tương thích mô tả đúng những gì note hiển thị, và mỗi thay đổi chỉ
được đếm một lần, trong mục mà nó được liệt kê. Audience không liên quan tới bất cứ thay đổi nào trong release vẫn có note của mình: tiêu đề,
tóm tắt release, và một dòng nói rằng release này không có gì liên quan tới họ. Việc duyệt vẫn viết
một note cho mỗi audience và ngôn ngữ, và một hành động automation giao note đó như mọi note khác.
Xem [ADR-0035](adr/0035-evidence-bound-notes-and-audience-relevance.md).

![Sửa template của một audience rồi lưu lại](assets/release-notes/rn-2-template.webp)

### Phần cuối {#footer}

**Phần cuối** (footer) của một audience là một template Mustache được dựng một lần, sau mục cuối
cùng của note (hoặc sau dòng nói rằng release không có gì liên quan tới audience), từ chính các
giá trị của release chứ không phải của một thay đổi:

| Biến | Giá trị |
| --- | --- |
| `version` | phiên bản của release |
| `previousVersion` | phiên bản của [release trước](#releases), hoặc rỗng |
| `compareUrl` | trang so sánh phiên bản trước với phiên bản này trên code host, hoặc rỗng |
| `upgradeGuideUrl` | hướng dẫn nâng cấp của release, hoặc rỗng |
| `releaseUrl` | trang của phiên bản này trên code host, hoặc rỗng |

Giá trị rỗng được coi là false, nên một dòng nằm trong `{{#compareUrl}}…{{/compareUrl}}` biến mất
khi release không có trang so sánh, và `{{^previousVersion}}…{{/previousVersion}}` chỉ hiện với
release đầu tiên. Bên trong một section, `{{.}}` là giá trị của chính section đó và mọi biến khác
của phần cuối vẫn giữ giá trị của nó. Phần cuối chỉ dùng năm biến này; các biến của một thay đổi,
như `whatChanged`, không có trong nó, và các biến của phần cuối không có trong template của thay
đổi. Phần cuối để trống không thêm gì. Phần cuối được kiểm tra khi lưu, như template, và **Xem
thử** dựng thay đổi mẫu rồi tới phần cuối mẫu.

`compareUrl` và `releaseUrl` lấy từ code host duy nhất của Project, theo toạ độ đã dùng khi kết
nối nó; ReleaseFlow không bao giờ hỏi nhà cung cấp, nên việc dựng phần cuối không bao giờ gọi ra
mạng, và một liên kết có thể nhắc tới một tag chưa tồn tại.

| Nguồn | `compareUrl` | `releaseUrl` |
| --- | --- | --- |
| GitHub | `https://github.com/{owner}/{repository}/compare/{previousVersion}...{version}` | `https://github.com/{owner}/{repository}/releases/tag/{version}` |
| GitLab | `{địa chỉ GitLab}/{đường dẫn project}/-/compare/{previousVersion}...{version}` | `{địa chỉ GitLab}/{đường dẫn project}/-/releases/{version}` |

Project không có nguồn GitHub hay GitLab nào, hoặc có nhiều hơn một, sẽ không có liên kết nào;
nguồn Linear và Jira không được tính. `compareUrl` rỗng khi không có release trước. Phiên bản của
mỗi release được dùng đúng như đã viết làm tên tag: ReleaseFlow không thêm hay bỏ chữ `v`, và
không có thiết lập tiền tố tag. Khi tag của một Project không trùng với phiên bản của nó (tag
`v1.2.0` cho phiên bản `1.2.0`, hay tiền tố `server/`), hãy tự viết liên kết trong phần cuối thay
vì dùng hai biến này.

Phần cuối của operator và contributor có hai dòng, mỗi dòng chỉ hiện khi có liên kết của nó:
**So sánh** (`Compare`) liên kết `previousVersion...version` tới trang so sánh, và **Hướng dẫn
nâng cấp** (`Upgrade guide`) liên kết **Đọc trước khi nâng cấp** tới hướng dẫn. Không dòng nào
liên kết `releaseUrl`: một note đã publish thường chính là trang GitHub release, nơi một liên kết
cài đặt sẽ trỏ về đúng trang đang mở. Một note giữ phần cuối mà nó được duyệt cùng, như template
của nó.

### Nâng cấp template của audience tự tạo {#upgrading-custom-audience-templates}

Audience tạo trước 1.1.0 giữ nguyên template của mình; không có gì viết lại chúng. Audience mẫu
operator và contributor được nhận phần cuối mới ở mọi ngôn ngữ của chúng, vì trước đó chưa có, còn
audience người dùng cuối và support giữ phần cuối trống. Một audience tự tạo, như audience cho DBA,
bắt đầu với phần cuối trống. Để thêm dòng giới hạn và phần cuối cho nó, thêm dòng này vào template
của thay đổi, ở chỗ các dòng chi tiết khác (trong `{{^brief}}` nếu template có):

```text
{{#limitations}}
  - Giới hạn: {{.}}
{{/limitations}}
```

rồi viết phần cuối của nó, trên trang Audience hoặc qua `footerTemplateBody`:

```text
{{#compareUrl}}
**So sánh:** [{{previousVersion}}...{{version}}]({{compareUrl}})
{{/compareUrl}}

{{#upgradeGuideUrl}}
**Hướng dẫn nâng cấp:** [Đọc trước khi nâng cấp]({{upgradeGuideUrl}})
{{/upgradeGuideUrl}}
```

Với template tiếng Anh, các nhãn là `Limitations`, `Compare`, `Upgrade guide` và `Read before
upgrading`. Một ngôn ngữ release note khác ngôn ngữ chính có template và phần cuối riêng trong mục
**Template theo ngôn ngữ**. **Đặt lại về preset** trả cho audience mẫu phần cuối đi kèm ở mọi ngôn
ngữ.

Một Organization giữ từ 1 đến 20 audience. Audience không xoá được khi còn thứ gì đó nhắc tới
nó: một release note, một hành động của quy tắc automation, hoặc lịch sử chạy automation. Quy
tắc đang bật hay đã tắt có thể được đổi sang audience khác trước. Quy tắc đã lưu trữ thì không
sửa được, và lịch sử chạy giữ audience mà nó đã giao tới, nên cả hai giữ audience đó mãi mãi;
lời từ chối nói rõ thứ nào đang giữ nó. Một audience mẫu có thể được đặt lại về phiên bản đi
kèm, theo ngôn ngữ đầu ra hiện tại. Xem [ADR-0011](adr/0011-audience-release-notes.md).

Mọi endpoint audience chỉ dành cho quản trị viên:

```text
GET    /api/audiences
POST   /api/audiences                          {"code", "displayName", "communicationIntent", "templateBody", "footerTemplateBody"}
GET    /api/audiences/{audienceId}
PUT    /api/audiences/{audienceId}             {"displayName", "communicationIntent", "templateBody", "footerTemplateBody"}
PUT    /api/audiences/{audienceId}/templates/{language}   {"templateBody", "footerTemplateBody"}
DELETE /api/audiences/{audienceId}
POST   /api/audiences/{audienceId}/reset-to-preset
POST   /api/audiences/preview                  {"templateBody", "footerTemplateBody"} -> {"markdown", "html"}
```

Một audience trả về `footerTemplateBody` cạnh `templateBody`, và `footerTemplateVariants` cạnh
`templateVariants`, mỗi cái theo khoá ngôn ngữ. Request bỏ `footerTemplateBody` sẽ giữ phần cuối
mà audience hay ngôn ngữ đó đang có, và audience mới khi đó không có phần cuối, nên các client viết
trước khi có phần cuối vẫn chạy được.

| Tình huống | Phản hồi |
| --- | --- |
| Thành viên, không phải quản trị viên | `403` |
| Thiếu trường, hoặc mã không khớp `[a-z][a-z0-9_]*` | `400 validation_failed` |
| Template hoặc phần cuối không biên dịch được hoặc dùng biến không tồn tại | `400 template_invalid` |
| Template gọi tới `narratives.<code>` | `400 template_narratives_path` |
| Mã đã được dùng | `409 audience_code_taken` |
| Organization đã có 20 audience | `409 audience_limit` |
| Xoá audience cuối cùng | `409 audience_last` |
| Xoá một audience đã có release note, đang được hành động của một quy tắc automation giao tới (bật, tắt hay đã lưu trữ), hoặc có trong lịch sử chạy | `409 audience_in_use`; `detail` nói rõ lý do nào |
| Đặt lại một audience do nhóm của bạn tự tạo | `409 audience_not_preset` |
| Template cho một ngôn ngữ không nằm trong các ngôn ngữ release note | `400 template_language_not_targeted` |
| Audience không tồn tại hoặc thuộc Organization khác | `404 audience_not_found` |

## Ngôn ngữ release note {#release-note-languages}

Mặc định mọi release note được viết bằng ngôn ngữ đầu ra của Organization. Quản trị viên có thể
chọn tối đa năm **ngôn ngữ release note** ở đầu trang Audience, hoặc gọi
`PUT /api/organization/release-languages` với `{"targetLanguages": ["en", "vi"]}`; mọi thành
viên đọc được chúng bằng `GET`. Tag được chuẩn hoá và bỏ phần lặp; danh sách rỗng, nhiều hơn năm
ngôn ngữ, hoặc tag không hợp lệ trả về `400 invalid_release_languages`. Khi đó duyệt một
release sẽ viết một release note cho mỗi audience và mỗi ngôn ngữ, nên bốn audience bằng tiếng
Anh và tiếng Việt cho ra tám release note.

Mỗi audience có template riêng cho từng ngôn ngữ release note mà ngôn ngữ đầu ra không bao phủ,
được tạo khi thêm ngôn ngữ hoặc thêm audience: một audience mẫu bắt đầu từ template đi kèm của
nó bằng ngôn ngữ đó, với các nhãn đã dịch, còn mọi audience khác bắt đầu từ bản sao template
chính của nó. Sửa chúng trong mục **Template theo ngôn ngữ** trên trang của audience, hoặc gọi
`PUT /api/audiences/{audienceId}/templates/{language}`. Đặt lại một audience mẫu cũng đặt lại
các template này.

Bản tóm tắt, dòng tiêu đề và narrative của một thay đổi được viết bằng ngôn ngữ mà AI (hoặc người
viết) đã dùng; mức liên quan không phải văn bản nên không bao giờ được dịch. Với một release note bằng ngôn ngữ khác, chúng được dịch bằng
[DeepL](https://developers.deepl.com/docs) sau khi duyệt, không bao giờ trong lúc duyệt:

```sh
export RELEASEFLOW_TRANSLATION_PROVIDER=deepl   # the default is disabled
export RELEASEFLOW_DEEPL_API_KEY=...            # a key ending in :fx uses the free API
# RELEASEFLOW_DEEPL_BASE_URL overrides the host chosen from the key.
```

Các bản dịch được xếp hàng theo từng thay đổi và từng ngôn ngữ, và một worker chạy nền gửi chúng
tới DeepL theo lô. Văn bản đã dịch được cache cho Organization, nên cùng một văn bản không bao
giờ bị gửi hai lần, và một bản tóm tắt không đổi sẽ không bao giờ bị dịch lại, kể cả khi release
quay về nháp rồi được duyệt lại. Bị giới hạn tốc độ, lỗi phía server hoặc lỗi mạng được thử lại
tối đa năm lần với khoảng chờ tăng dần; mọi lỗi khác là lỗi cuối cùng. Tiêu đề pull request dùng
thay cho bản tóm tắt bị thiếu thì không được dịch.

DeepL cũng nhận [ngữ cảnh](changes.md#project-context) của Project làm `context`, phần này giúp
dịch đúng hơn và không bị dịch hay tính phí. Bản dịch được cache theo cả ngữ cảnh: sửa ngữ cảnh
thì lần duyệt tiếp theo sẽ dịch lại, còn khi ngữ cảnh không đổi thì cùng một văn bản vẫn không
bao giờ bị gửi hai lần.

**Tóm tắt** của chính release, dòng nằm dưới tiêu đề của release note, cũng được dịch theo cách
đó. Tóm tắt do người viết, bằng ngôn ngữ đầu ra của Organization, nên release note bằng ngôn
ngữ đó in nguyên văn, còn release note bằng mọi ngôn ngữ khác chờ bản dịch như nội dung của một
thay đổi. Bản dịch không bao giờ thay thứ người đã gõ: release vẫn giữ tóm tắt của nó, chỉ có
release note bằng ngôn ngữ khác đọc văn bản đã dịch. Release không có tóm tắt thì không có gì
để dịch.

Cho tới khi có đủ bản dịch, release note hiển thị văn bản chưa dịch và ở trạng thái **Đang
dịch**; trang release tự cập nhật khi bản dịch về tới. Một release note dịch thất bại, hoặc cần
dịch trong khi nhà cung cấp dịch đang tắt, ở trạng thái **Chưa dịch**. **Chạy lại bản dịch** sẽ
chạy lại những bản dịch thất bại, và sửa tay một release note như vậy sẽ khiến nó sẵn sàng.
Release chỉ được publish khi mọi release note đều sẵn sàng: nếu không, publish trả về
`409 translations_not_ready`, và cơ sở dữ liệu cũng từ chối. Sửa một bản tóm tắt sau khi duyệt
sẽ dịch văn bản mới. Xem [ADR-0015](adr/0015-multilingual-release-notes.md).

![Vài giây sau khi duyệt: note tiếng Việt đang dịch và vẫn hiện văn bản chưa dịch](assets/release-notes/rn-3-translating.webp)

![Cùng note đó khi bản dịch đã về: tóm tắt release và mọi thay đổi đều bằng tiếng Việt](assets/release-notes/rn-4-translated.webp)

```text
GET  /api/organization/release-languages
PUT  /api/organization/release-languages                               {"targetLanguages"}
POST /api/projects/{projectId}/releases/{releaseId}/translations/retry
```

### Bảng thuật ngữ {#glossary}

Dịch máy biến thuật ngữ sản phẩm thành những từ mang nghĩa khác: khi không có gì hỗ trợ, DeepL
dịch "console" của dbvault thành *giao diện dòng lệnh*, "image" thành *hình ảnh*, và "pull
request" thành *yêu cầu kéo*. **Bảng thuật ngữ** của Organization, trên trang **Bảng thuật
ngữ**, sửa điều đó cho mọi note được dịch. Bảng có hai loại mục:

- **thuật ngữ giữ nguyên** luôn ở dạng tiếng Anh trong mọi ngôn ngữ: `CLI`, `console`, `image`,
  `pull request`. DeepL vẫn viết câu quanh nó và có thể đổi dạng cho hợp ngôn ngữ, nên
  "images" của dbvault thành *các image*: thuật ngữ giữ nguyên không phải là chữ đóng băng;
- **bản dịch cố định** luôn dịch thành cùng một cụm từ giữa hai ngôn ngữ, ghi bằng ngôn ngữ
  chính: `en → vi`, "storage" → *lưu trữ*. Mục này được ưu tiên hơn thuật ngữ giữ nguyên có cùng
  chữ, chỉ với hai ngôn ngữ của nó.

Quản trị viên sửa bảng và mọi thành viên đều xem được. Không có gì được tự thêm: trang gợi ý
những thuật ngữ hay gặp trong release note phần mềm, và chỉ những thuật ngữ quản trị viên chọn
mới được giữ. Một thuật ngữ dài 1 đến 100 ký tự trên một dòng, bảng có tối đa 500 mục, và mỗi mục
phải khác nhau chính xác từng ký tự: "Image" và "image" là hai mục. Mục viết thường thường
cũng giữ được từ viết hoa ở đầu câu, nên chỉ thêm dạng khác cho từ mà DeepL vẫn đổi.

Bảng thuật ngữ định hướng DeepL chứ không ràng buộc từng chữ. DeepL vẫn có thể dịch một thuật ngữ
giữ nguyên nằm trong một cụm dài hơn: dù đã giữ nguyên "image", "secret" và "container", note của
dbvault vẫn bị dịch "container images", "emulator image" và "repository secrets", và một lần cả
"The console" ở đầu câu. Khi DeepL dịch một thuật ngữ nằm trong một cụm, thêm cả cụm đó thành một
thuật ngữ giữ nguyên riêng (`container images`, `repository secrets`).

Mỗi cặp ngôn ngữ mà Organization dịch là một từ điển trong một glossary của DeepL, với phiên bản
là mã băm của nội dung được gửi. Lúc duyệt chỉ ghi lại rằng cặp đó cần được đồng bộ, và một job
nền đồng bộ nó lên DeepL ngoài mọi transaction, thử lại tối đa năm lần khi gặp giới hạn tần suất,
lỗi máy chủ hoặc lỗi mạng. Bản dịch của cặp đó chờ lần đồng bộ ấy, rồi được gửi kèm bảng thuật
ngữ bên cạnh ngữ cảnh của Project. Phiên bản bảng thuật ngữ là một phần của khoá cache bản dịch,
nên:

- một lần sửa sẽ dịch lại ở lần duyệt tới, kể cả tóm tắt của chính release;
- một lần sửa không làm đổi từ điển, như xoá một thuật ngữ rồi thêm lại, không dịch lại gì;
- một Organization không có bảng thuật ngữ gửi đúng những yêu cầu như trước.

Bảng thuật ngữ không bao giờ giữ một release lại. Cặp mà DeepL không hỗ trợ bảng thuật ngữ, hoặc
đồng bộ thất bại, được dịch mà không có nó, và với mỗi ngôn ngữ, trang release cho biết bảng
thuật ngữ **Đã gửi kèm mọi bản dịch**, vẫn đang chờ, hay **Chưa áp dụng**, và vì sao: cặp không được
hỗ trợ, đồng bộ thất bại, hoặc DeepL trả lời bằng ngôn ngữ nguồn khi dùng bảng thuật ngữ. **Đã gửi
kèm mọi bản dịch** nghĩa là bảng thuật ngữ đã được gửi cùng mọi bản dịch sang ngôn ngữ đó, không
phải DeepL đã giữ đúng mọi thuật ngữ; đọc lại note, và thêm cụm mà DeepL đã dịch thành một thuật
ngữ giữ nguyên riêng. **Chạy lại bản
dịch** sẽ thử đồng bộ thất bại thêm lần nữa. Bảng thuật ngữ không bao giờ được gửi cho AI.

Bản dịch trả về bằng ngôn ngữ nguồn không được lưu như một bản dịch. Với note tiếng Việt, một câu
còn ít nhất ba từ cần dịch mà không có dấu tiếng Việt nào sẽ bị coi là chưa dịch; với một ngôn ngữ
dùng chữ viết khác, như tiếng Nhật hay tiếng Nga, một câu không có ký tự nào của chữ viết đó cũng
vậy. Thuật ngữ giữ nguyên, mã, URL, đường dẫn và những từ như `MongoDB` không được tính. Câu trả
lời không đạt được dịch lại một lần không có bảng thuật ngữ, và nếu vẫn không đạt thì note ở
trạng thái **Chưa dịch**, như mọi bản dịch thất bại. Các ngôn ngữ chữ Latin khác chưa có bước kiểm
tra này. Xem [ADR-0036](adr/0036-translation-glossary.md).

```text
GET    /api/glossary
GET    /api/glossary/suggestions
POST   /api/glossary/kept-terms                    {"terms": ["console", "CLI"]}
DELETE /api/glossary/kept-terms/{termId}
POST   /api/glossary/translations                  {"sourceLanguage", "targetLanguage", "sourceTerm", "targetTerm"}
DELETE /api/glossary/translations/{translationId}
```

`POST /api/glossary/kept-terms` thêm tất cả thuật ngữ hoặc không thêm gì. `GET /api/glossary`
trả về `keptTerms`, `translations`, `entryLimit` và `pairs`: với mỗi cặp ngôn ngữ mà Organization
dịch, `entryCount` và `status` là `empty`, `not_synced`, `pending`, `synced`, `outdated` (đã sửa
từ lần đồng bộ trước), `unsupported`, hoặc `failed` kèm `error`.

| Tình huống | Phản hồi |
| --- | --- |
| Thuật ngữ trống, quá dài hoặc nhiều dòng, ngôn ngữ không hợp lệ, hoặc hai ngôn ngữ giống nhau | `400 validation_failed` |
| Thuật ngữ bảng đã có, ở dạng giữ nguyên hoặc cho cặp đó | `409 glossary_term_taken` |
| Quá 500 mục | `409 glossary_limit` |
| Mục không tồn tại hoặc thuộc Organization khác | `404 glossary_term_not_found` |

**DeepL API Free chỉ có một bảng thuật ngữ.** Mỗi Organization dùng một glossary của tài khoản
DeepL, và tài khoản API Free chỉ có số glossary mà gói cho phép, là một khi được thử. Với key
như vậy, Organization đồng bộ đầu tiên được dùng glossary; lần đồng bộ của mọi Organization khác
thất bại ngay với `quota_exceeded` (mã `456` của DeepL, không bao giờ thử lại), trang release của
nó cho biết bảng thuật ngữ chưa được áp dụng, và note của nó được dịch mà không có bảng thuật ngữ.
ReleaseFlow không bao giờ xoá glossary trong DeepL.

## Release {#releases}

Mở **Release** để chuẩn bị các release của một Project. Trang này tạo release với một phiên bản
(tối đa 50 ký tự), một bản tóm tắt không bắt buộc, một thời điểm phát hành dự kiến theo UTC
không bắt buộc, một **hướng dẫn nâng cấp** không bắt buộc, và **release trước**, đồng thời liệt kê các release của Project kèm bộ lọc theo trạng thái và số
lượng. Một Project có thể chuẩn bị nhiều release cùng lúc, và mỗi thay đổi thuộc về tối đa một
release.

![Các release của một Project theo trạng thái: bốn release đã publish, không có release nào ở nháp, review hay đã duyệt](assets/release-notes/rn-5-releases.webp)

**Hướng dẫn nâng cấp** là một địa chỉ `http://` hoặc `https://` đầy đủ, tối đa 2.000 ký tự, không
có tên người dùng hay mật khẩu; query và fragment đều được phép. Khi release có thay đổi phá vỡ
tương thích, cảnh báo trong mọi note sẽ liên kết **đọc hướng dẫn nâng cấp** tới địa chỉ này, và
[phần cuối](#footer) cũng có thể liên kết tới nó. **Release trước** là một release đã publish của
cùng Project mà các note so sánh release này với nó. Release mới theo release được publish gần
nhất của Project, trừ khi một người chọn release khác hoặc không chọn release nào; lựa chọn đó
giữ nguyên khi có release khác được publish sau. Cả hai đều sửa được khi release còn là nháp.

Một release đi qua bốn bước, hiển thị ở đầu trang của nó:

1. **Nháp.** Thêm những thay đổi đã xử lý xong, từng cái một hoặc bằng **Thêm tất cả**. Thay đổi
   còn cần review vẫn thêm được; phần review của release sẽ quyết định nó. Thay đổi mà quản trị
   viên đã [loại khỏi release](changes.md#excluding-a-change-from-releases) không được đề xuất. Sửa phiên bản và bản
   tóm tắt, bỏ bớt thay đổi, rồi bấm **Yêu cầu review**, việc này cần ít nhất một thay đổi.
2. **Đang review.** Danh sách thay đổi được cố định, chỉ trừ việc từ chối. Trên tab **Review**,
   mỗi thay đổi nhận một quyết định: **Duyệt đúng như hiển thị** xác nhận category, cờ phá vỡ
   tương thích và cờ bảo mật của nó, **Sửa phân loại** sửa lại chúng, và **Từ chối** bỏ thay đổi khỏi release
   để nó dùng được cho release khác. Duyệt và sửa đều ghi người review lên chính thay đổi, giống
   hệt một lần review trong Hộp thay đổi, kèm ghi chú không bắt buộc. Thay đổi Unknown bắt buộc
   phải sửa. **Duyệt release** dùng được khi mọi thay đổi đã có quyết định. Một thay đổi mà tiêu
   đề, sau khi bỏ tiền tố Conventional Commit và key issue, còn dưới ba từ (`fix`, `update deps`)
   được đánh dấu **Cần viết tóm tắt**, trừ khi tóm tắt do người viết, hoặc AI viết nó từ một mô tả
   có ít nhất mười từ (không tính chú thích HTML) mà AI không đánh giá là thiếu ngữ cảnh. Tiêu đề
   ngắn như vậy không tự đứng được trong note, và AI không có gì khác thì chỉ nhắc lại được tiêu
   đề, nên việc duyệt phải chờ một người viết tóm tắt.
3. **Đã duyệt.** Người duyệt và thời điểm được ghi lại, và một release note được viết từ
   template cho mỗi audience ở mỗi ngôn ngữ release note (xem
   [Ngôn ngữ release note](#release-note-languages)). Kiểm tra tab của từng release note, sửa
   Markdown của một release note nếu cần (release note đó chuyển thành **Thủ công** và không còn
   theo template nữa), rồi chọn **Publish release này**.
4. **Đã publish.** Xem [Publish](#publishing).

![Một release đang review ngay sau khi thêm mọi thay đổi có sẵn](assets/release-notes/rn-6-add-all.webp)

Mọi thành viên đều làm được mọi bước, nên một người có thể tự chuẩn bị, review, duyệt và
publish cùng một release. ReleaseFlow ghi lại ai đã review từng thay đổi, ai đã duyệt và ai đã
publish, nhưng không yêu cầu hai người khác nhau.

Trước khi publish, một release có thể được đặt lịch hoặc bỏ lịch, đưa về nháp (từ trạng thái
đang review hoặc đã duyệt; việc này xoá các quyết định và lần duyệt nhưng không xoá các lần
review trên thay đổi của nó), hoặc bị huỷ bỏ (các thay đổi của nó lại dùng được). Đưa về nháp
cũng xoá các release note; duyệt lại sẽ viết release note mới.

![Thời điểm phát hành dự kiến của một release, theo UTC](assets/release-notes/rn-8-planned-time.webp)

Trước khi duyệt, phần xem trước cho thấy release note của mỗi audience sẽ viết gì. Một release
note bắt đầu bằng tiêu đề và bản tóm tắt của release và phần tổng quan **Có gì mới** kèm số
lượng, cảnh báo về thay đổi phá vỡ tương thích, rồi liệt kê thay đổi phá vỡ tương thích trước,
sau đó là thay đổi bảo mật, tính năng, sửa lỗi, hiệu năng, tài liệu và bảo trì, và kết thúc bằng
phần cuối của audience. Mỗi thay đổi được template của
audience viết ra từ bản tóm tắt trung lập và narrative của audience đó; khi không có bản tóm
tắt, tiêu đề pull request được dùng thay, sau khi bỏ tiền tố Conventional Commit. Các nhãn là
tiếng Anh hoặc tiếng Việt, theo ngôn ngữ đầu ra.

![Các note của một release, mỗi audience và ngôn ngữ một note; note operator đã được sửa tay và được đánh dấu là viết tay](assets/release-notes/rn-7-manual-note.webp)

Trong lúc review và sau khi duyệt, **Sửa tóm tắt** trên tab Review cho phép một người viết hoặc
sửa bản tóm tắt của một thay đổi, gồm cả loại bước chuyển đổi và giới hạn, và mức liên quan,
dòng tiêu đề, narrative của từng audience. Việc này dùng được dù AI đã viết hay chưa, và AI không
bao giờ thay thế điều một người đã chọn. Qua API, narrative của mỗi audience là
`{"relevance": "NONE" | "BRIEF" | "FULL", "headline", "body"}` hoặc, như bên gọi viết trước khi có
mức liên quan gửi, một chuỗi trơn, tức narrative đầy đủ không có dòng tiêu đề. Narrative `FULL` mà
không có cả dòng tiêu đề lẫn nội dung sẽ không được giữ, vì nó không nói gì hơn việc không có
narrative; mức liên quan không xác định trả về `400 malformed_request`. Request không nêu `migrationKind`,
như bên gọi viết trước khi có trường này gửi, sẽ ghi `UNSPECIFIED` khi có bước và `NONE` khi không
có; loại không khớp với bước (`NONE` mà có bước, loại khác mà không có) trả về
`400 validation_failed`. Thay đổi ghi lại người đã viết, và các release note của một release đã duyệt mà
vẫn theo template sẽ được dựng lại. Release note phản ánh các thay đổi tại thời điểm duyệt: để
lấy một phân loại được sửa sau đó trong Hộp thay đổi, đưa release về nháp rồi duyệt lại. Xem
[ADR-0010](adr/0010-release-review-lifecycle.md) và
[ADR-0011](adr/0011-audience-release-notes.md).

REST client tuân theo cùng các quy tắc:

```text
GET    /api/projects/{projectId}/releases
POST   /api/projects/{projectId}/releases                                {"version", "summary", "plannedReleaseAt",
                                                                          "upgradeGuideUrl", "previousReleaseId"}
GET    /api/projects/{projectId}/releases/{releaseId}
PUT    /api/projects/{projectId}/releases/{releaseId}                    {"version", "summary", "upgradeGuideUrl", "previousReleaseId"}
DELETE /api/projects/{projectId}/releases/{releaseId}
PUT    /api/projects/{projectId}/releases/{releaseId}/schedule           {"plannedReleaseAt"} (null clears it)
GET    /api/projects/{projectId}/releases/{releaseId}/available-changes
POST   /api/projects/{projectId}/releases/{releaseId}/changes            {"changeIds": [...]} or {"allAvailable": true}
DELETE /api/projects/{projectId}/releases/{releaseId}/changes/{changeId} (remove, or reject during review)
POST   /api/projects/{projectId}/releases/{releaseId}/request-review
PUT    /api/projects/{projectId}/releases/{releaseId}/changes/{changeId}/decision
                                                                         {"action": "APPROVE" | "EDIT", "category", "breaking", "security", "note"}
POST   /api/projects/{projectId}/releases/{releaseId}/approve
POST   /api/projects/{projectId}/releases/{releaseId}/return-to-draft
POST   /api/projects/{projectId}/releases/{releaseId}/publish
POST   /api/projects/{projectId}/releases/{releaseId}/translations/retry
PUT    /api/projects/{projectId}/releases/{releaseId}/changes/{changeId}/summary
                                                                         {"whatChanged", "whyChanged", "technicalDetail", "migrationStep",
                                                                          "migrationKind", "limitations",
                                                                          "narratives": {"<code>": {"relevance", "headline", "body"} or "..."}}
GET    /api/projects/{projectId}/releases/{releaseId}/note-previews
GET    /api/projects/{projectId}/releases/{releaseId}/notes
PUT    /api/projects/{projectId}/releases/{releaseId}/notes/{noteId}     {"content"}
GET    /api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/download
GET    /api/projects/{projectId}/release-assignments
```

`plannedReleaseAt` là một thời điểm ISO-8601 như `2026-10-01T09:00:00Z`; giá trị không kèm độ
lệch múi giờ được hiểu là UTC. Request tạo release bỏ `previousReleaseId` sẽ theo release được
publish gần nhất, còn request cập nhật bỏ `upgradeGuideUrl` hoặc `previousReleaseId` sẽ giữ giá trị
đó; `null` tường minh xoá giá trị. Quyết định bỏ `security` sẽ xác nhận cờ hiện tại của thay đổi,
như bên gọi viết trước khi có trường này gửi; ngoài trường hợp đó, `APPROVE` phải gửi đúng cờ mà
thay đổi đang có. Một release trả về `upgradeGuideUrl`, `previousReleaseId`, `previousVersion`, `changes`, `decisions` (`changeId`, `action`,
`reviewerName`, `note`, `decidedAt`), `reviewedCount`, `plannedReleaseAt`, `approvedAt`,
`approverName`, và `notes` (`id`, `audienceCode`, `audienceName`, `language`, `content`,
`autoRerender`, `lastEditorName`, `updatedAt`, `translationStatus`: `READY`, `PENDING` hoặc
`FAILED`, và `glossaryStatus`: `NONE`, `PENDING`, `APPLIED` hoặc `NOT_APPLIED` kèm
`glossaryReason`), và `glossaries`: mỗi ngôn ngữ note có [bảng thuật ngữ](#glossary) một mục, gồm
`language`, `status` và `reason`. Tệp tải về có kiểu `text/markdown`, tên là
`<version>-<audience code>-<language>.md`. `release-assignments` liệt kê release (theo ID,
phiên bản và trạng thái) mà mỗi thay đổi của Project thuộc về. Các lỗi:

| Tình huống | Phản hồi |
| --- | --- |
| Thao tác không phù hợp với trạng thái của release | `409 release_status_conflict` |
| Yêu cầu review hoặc duyệt khi không có thay đổi nào | `409 release_empty` |
| Duyệt trước khi mọi thay đổi có quyết định | `409 release_review_incomplete` |
| Duyệt trong khi một thay đổi có tiêu đề dưới ba từ chưa có tóm tắt do người viết, và mô tả quá ít cho tóm tắt của AI; `detail` nêu tên các thay đổi đó | `409 change_summary_required` |
| Gửi `APPROVE` với phân loại khác phân loại hiện tại của thay đổi, kể cả cờ bảo mật | `409 classification_changed` |
| Hướng dẫn nâng cấp không phải địa chỉ `http://` hoặc `https://` đầy đủ, có tên người dùng hay mật khẩu, hoặc dài hơn 2.000 ký tự | `400 invalid_upgrade_guide_url` |
| Release trước không phải một release đã publish của Project này | `400 invalid_previous_release` |
| `unknown` hoặc một category không được hỗ trợ trong quyết định | `400 invalid_change_review` |
| `APPROVE` một thay đổi Unknown, dù có nêu category nào: approve giữ nguyên phân loại hiện tại, nên thay đổi Unknown phải dùng `EDIT` kèm một category | `400 invalid_change_review` |
| Thời điểm phát hành dự kiến không đọc được hoặc đã qua | `400 invalid_release_schedule` |
| Thay đổi đang xử lý hoặc đã thuộc một release khác | `409 change_not_releasable` |
| Thay đổi đã bị loại khỏi release | `409 change_excluded` |
| Phiên bản đã được dùng trong Project này | `409 release_version_taken` |
| Sửa bản tóm tắt hoặc release note ở trạng thái không cho phép | `409 release_status_conflict` |
| Template của audience không dựng được lúc duyệt | `409 release_note_render_failed` |
| Publish một release đã duyệt nhưng không có release note | `409 release_notes_missing` |
| Publish khi một release note vẫn đang dịch hoặc không dịch được | `409 translations_not_ready` |
| Bản tóm tắt hoặc release note rỗng, hoặc một trường vượt giới hạn | `400 validation_failed` |
| Thay đổi không tồn tại hoặc thuộc Organization khác | `404 change_not_found` |
| Release không tồn tại hoặc thuộc Organization khác | `404 release_not_found` |
| Release note không tồn tại trong release này | `404 release_note_not_found` |

## Publish {#publishing}

Trên trang của một release đã duyệt, mở **Publish release này**. Publish sẽ cố định release note
của mọi audience, cùng với người publish và thời điểm publish. Sau đó trang chỉ còn để đọc. Nó
hiển thị người đã duyệt và người đã publish release, và có một tab cho mỗi audience và ngôn ngữ
với release note đã dựng, Markdown của nó, nút **Sao chép**, và liên kết **Tải về** để dùng cho
GitHub Releases hoặc một changelog. Tệp tải về có tên `<version>-<audience>-<language>.md`.

![Một release đã publish: chọn note Contributor · vi và tải nó về dưới dạng Markdown](assets/release-notes/rn-9-publish.webp)

Một release đã publish không sửa, đặt lịch, review, huỷ bỏ, gỡ publish hay đổi nội dung được, và
các release note của nó cũng vậy. Các thay đổi của nó vẫn sửa được trong Hộp thay đổi, nhưng
release note đã publish giữ nguyên những gì đã publish. Trigger của PostgreSQL bảo đảm điều này
kể cả khi chạy SQL trực tiếp. Xem
[ADR-0005](adr/0005-immutable-release-note-snapshots.md) và
[ADR-0011](adr/0011-audience-release-notes.md).

REST client gọi `POST /api/projects/{projectId}/releases/{releaseId}/publish`. Lời gọi trả về
release với `status`, `publishedAt`, `publisherName` và `notes`. Trước khi publish, `preview`
nhóm các thay đổi hiện có của release vào các section của release note; sau khi publish,
`preview` rỗng và các release note nằm trong `notes`. `markdown` là null, trừ release được publish
trước Flyway `V13`. Publish một release
chưa duyệt trả về `409 release_status_conflict`; mọi thao tác trên một release đã publish trả về
`409 release_published`.
