# Workspace

Đăng ký, thành viên và vai trò của họ, trang tổng quan mỗi người thấy khi đăng nhập, và
hai ngôn ngữ mà ReleaseFlow luôn tách riêng: ngôn ngữ một người đọc giao diện, và ngôn ngữ
ReleaseFlow dùng để viết.

## Đăng ký {#registration}

Mở `/register` để tạo một Organization cùng tài khoản đầu tiên của nó, tài khoản này là
**quản trị viên**. REST client gọi `POST /api/registrations` với
`{"organizationName", "displayName", "email", "password", "outputLanguage"}` sau khi lấy
CSRF token (xem [REST API](api.md#sessions-and-csrf)); `outputLanguage` không bắt buộc và
mặc định là tiếng Anh. Mỗi địa chỉ email chỉ thuộc về một tài khoản trên toàn bộ hệ thống
triển khai, nên đăng ký lại cùng một email sẽ trả về `409 email_already_registered`.
Việc đăng ký cũng tạo sẵn [danh mục category](changes.md#categories) của Organization,
ba [audience](release-notes.md#audiences) mẫu, và địa chỉ changelog công khai.

Mật khẩu dài ít nhất 12 ký tự và tối đa 72 byte UTF-8, giới hạn mà BCrypt đọc được, và chỉ
được lưu dưới dạng mã băm BCrypt. Quy tắc này cũng áp dụng khi chấp nhận lời mời.

**Đăng ký luôn mở.** Bất kỳ ai truy cập được `/register` hoặc `POST /api/registrations` đều
tạo được thêm một Organization trên hệ thống của bạn, và không có cấu hình nào để tắt việc
này. Các Organization tách biệt hoàn toàn với nhau, nhưng một hệ thống mà internet truy cập
được — điều webhook cần — nên chặn cả hai đường dẫn này ở reverse proxy sau khi Organization
của bạn đã được tạo. Xem [Triển khai production](operations.md#deploying-to-production).

## Thành viên và lời mời {#members-and-invitations}

Một Organization có hai vai trò. **Quản trị viên** mở trang **Thành viên** để mời đồng
nghiệp qua email, và chỉ quản trị viên mới được:

- kết nối nguồn, đặt access token cho nguồn, và bắt đầu import lịch sử;
- thêm sensitive path cho một Project;
- quản lý danh mục category và quyết định với những gì AI đề xuất;
- quản lý audience và chọn các ngôn ngữ của release note;
- đổi ngôn ngữ đầu ra và địa chỉ changelog công khai;
- viết và chạy quy tắc automation.

**Thành viên** làm phần việc còn lại: tạo Project, review thay đổi, yêu cầu AI phân loại,
quyết định với các thay đổi có thể trùng lặp, và chuẩn bị, duyệt, publish release. Thành
viên đọc được nguồn, các lần import và sensitive path của một Project, danh mục category
và các ngôn ngữ release note, nhưng không vào được trang Thành viên, Audience và
Automation.

Liên kết mời có dạng `/accept-invite#token=…` và chỉ hiện một lần. ReleaseFlow chỉ lưu bản
băm của nó. Liên kết hết hạn sau bảy ngày (đặt `RELEASEFLOW_INVITATION_TTL`, ví dụ `P3D`,
để đổi thời hạn), chỉ dùng được một lần, và có thể cấp lại hoặc thu hồi ở trang Thành
viên. Người được mời xác nhận Organization, chọn tên và mật khẩu, rồi đăng nhập với vai
trò thành viên. Hiện chưa đổi được vai trò và chưa xoá được thành viên.

REST client tuân theo cùng các quy tắc:

```text
GET    /api/members                         (administrator)
GET    /api/invitations                     (administrator)
POST   /api/invitations {"email"}           (administrator; 201, returns acceptancePath once)
POST   /api/invitations/{id}/reissue        (administrator)
DELETE /api/invitations/{id}                (administrator)
POST   /api/public/invitations/inspect {"token"}
POST   /api/public/invitations/accept  {"token", "displayName", "password"}
```

Các endpoint công khai cần CSRF token lấy từ `GET /api/csrf`. Mọi token không dùng được
đều trả về `400 invitation_invalid`. Các lỗi khác là `409 invitation_already_pending`,
`409 invitation_email_unavailable` (email đã có tài khoản), `404 invitation_not_found`, và
`403 access_denied` khi thành viên gọi endpoint dành cho quản trị viên.

## Tổng quan workspace {#workspace-overview}

Sau khi đăng nhập, bạn thấy trang tổng quan của một Project: có bao nhiêu thay đổi, bao
nhiêu thay đổi cần review, bao nhiêu thay đổi phá vỡ tương thích, và có bao nhiêu release
— mỗi con số là một liên kết tới trang xử lý được nó. Bên cạnh là **một** bước tiếp theo
chưa xong, theo thứ tự cố định: tạo project, kết nối nguồn, chờ thay đổi đầu tiên, gom thay
đổi vào một release, hoàn tất review, publish release đã duyệt, làm tiếp bản nháp, hoặc
không còn gì phải chờ. Một workspace có bản nháp đang mở mà chưa có thay đổi nào vẫn được
coi là đang chờ thay đổi đầu tiên, vì chưa có gì để đưa vào bản nháp.

Thanh tiêu đề và menu trên di động dùng để chuyển giữa các Project. ReleaseFlow nhớ Project
gần nhất trong cookie `releaseflow_project`, nên một trang mở ra không kèm `?project=` sẽ
mở đúng Project đó thay vì Project nào tình cờ đứng đầu. `projectId` trong URL của tài
nguyên được ưu tiên hơn tham số query, và query được ưu tiên hơn cookie. Chuyển Project từ
trang thay đổi hoặc trang release vẫn giữ nguyên chức năng đang xem; chuyển từ trang chi
tiết một release sẽ mở danh sách release của Project được chọn, và chuyển từ một trang quản
trị Organization sẽ mở trang tổng quan của Project được chọn. Cookie này là `HttpOnly` và
không mang quyền hạn nào: Project vẫn được tra cứu trong Organization của bạn, nên query
hoặc cookie trỏ tới Project của người khác sẽ bị trang xử lý yêu cầu bỏ qua hoặc từ chối.

Menu trên di động là một vùng mở rộng thao tác được bằng bàn phím: khi mở, focus chuyển vào
trong; `Escape` đóng nó lại, và focus quay về nút đã mở nó. Khi server kiểm tra biểu mẫu,
mỗi lỗi được gắn với đúng trường của nó và focus chuyển tới trường sai đầu tiên. Các vùng
mở rộng và nút điều khiển có vùng chạm tối thiểu 44 px; những thao tác huỷ công việc, vô
hiệu hoá một secret hoặc lưu trữ một quy tắc đều cho thấy tác động của chúng trước lần bấm
xác nhận cuối cùng.

Logo chữ đầy đủ của ReleaseFlow đổi theo theme sáng hoặc tối đã chọn trên các trang
workspace và tài khoản. Phần đầu trang changelog công khai chỉ giữ biểu tượng thu gọn bên
cạnh tên Organization, để Organization luôn là danh tính chính.

Một địa chỉ gõ sai trả về cho trình duyệt một trang thay vì JSON thô, và một địa chỉ vai trò
của bạn không được vào sẽ nói rõ như vậy mà không để lộ nội dung. Biểu mẫu gửi sau khi phiên
đăng nhập đã hết sẽ đưa bạn về trang đăng nhập, còn biểu mẫu cũ hơn lần đăng nhập gần nhất
sẽ bị báo là đã cũ; cả hai trường hợp đều không lưu gì. REST client, và mọi thứ dưới `/api`,
vẫn nhận `application/problem+json` như trước. Một địa chỉ trỏ tới thứ mà Organization của
bạn không có — một Project, một release, một quy tắc — trả về trang không tìm thấy kèm một
dòng cho biết thứ gì bị thiếu.

Kết quả của một biểu mẫu đã lưu được báo một lần, sau khi chuyển hướng, trong một thông báo
nhỏ bạn tự đóng; tải lại trang sẽ không báo lại. Thông báo có bốn loại — thành công, thông
tin, cảnh báo và lỗi — mỗi loại có màu và biểu tượng riêng. Trình duyệt không bao giờ tự
chặn biểu mẫu bằng bong bóng thông báo của nó: server kiểm tra mọi trường, viết từng lỗi
ngay dưới trường liên quan bằng ngôn ngữ của bạn, và với biểu mẫu nhiều hơn một trường thì
tóm tắt các lỗi ở phía trên. Trường bắt buộc được đánh dấu `*`.

## Ngôn ngữ giao diện {#interface-language}

Mỗi người đọc ReleaseFlow bằng tiếng Anh hoặc tiếng Việt. Ngôn ngữ được chọn theo từng yêu
cầu, theo thứ tự sau:

1. tham số `?lang=` trên một yêu cầu GET, ví dụ `/login?lang=vi`;
2. cookie `releaseflow_lang`;
3. ngôn ngữ đã lưu trong tài khoản (`app_users.ui_locale`);
4. `Accept-Language`, thu hẹp từ vùng về ngôn ngữ, nên `vi-VN` sẽ được hiểu là `vi`;
5. mục đầu tiên của `RELEASEFLOW_UI_LANGUAGES` (mặc định `en,vi`).

Bước nào chỉ định một ngôn ngữ mà hệ thống này không có sẽ được bỏ qua để xét bước tiếp
theo. Một giá trị `?lang=` được hỗ trợ sẽ được ghi vào cookie, nên một liên kết có thể mở
ReleaseFlow bằng một ngôn ngữ và các trang sau đó giữ nguyên ngôn ngữ ấy; lựa chọn trong tài
khoản không bị đụng tới, và một giá trị không được hỗ trợ không thay đổi gì. Trên trang
changelog công khai, vốn được cache công khai, `?lang=` chỉ đổi riêng trang đó và không đặt
cookie. Bộ chọn ngôn ngữ nằm trong menu người dùng; chọn một ngôn ngữ sẽ lưu nó vào tài khoản
và ghi cookie, mà cookie được ưu tiên hơn tài khoản, nên thay đổi hiện ra ngay. Cookie là
`HttpOnly`, có hiệu lực một năm, và là `Secure` khi yêu cầu dùng HTTPS.

```text
GET /api/me/ui-locale                  (any member)
PUT /api/me/ui-locale {"uiLocale"}     (the same member)
```

Cả hai đều trả về `{uiLocale, supported}`. `uiLocale` là null khi để trình duyệt quyết định;
đó cũng là trạng thái của tài khoản mới và là trạng thái quay về khi gửi `null`. Một ngôn ngữ
hệ thống này không có sẽ trả về `400 ui_locale_invalid`.

Trang changelog công khai theo cùng thứ tự, trừ bước tài khoản: đọc changelog vẫn không tạo
phiên đăng nhập. Các trang này khai báo `Vary: Accept-Language, Cookie`, vì release note bên
trong giống nhau với mọi người còn phần khung trang thì không. RSS feed là một tài liệu dùng
chung và luôn bằng tiếng Anh.

Nội dung trang, thông báo của biểu mẫu, và `title` cùng `detail` của mọi phản hồi
`application/problem+json` được viết bằng ngôn ngữ này. **`code` của một lỗi không bao giờ
thay đổi**, nên bên gọi so khớp theo nó không bị ảnh hưởng. Bằng chứng đã ghi lại không phải
là chữ của giao diện và giữ nguyên như lúc ghi: một lỗi AI đã lưu, lý do phân loại, và tên
riêng của nhà cung cấp đọc giống nhau ở mọi ngôn ngữ.

Đây không phải ngôn ngữ của bất cứ thứ gì ReleaseFlow viết ra. Release note và bản tóm tắt
của AI theo ngôn ngữ đầu ra của Organization và các ngôn ngữ release note, mô tả bên dưới,
bất kể người duyệt chúng đang đọc giao diện bằng ngôn ngữ nào.

## Ngôn ngữ đầu ra {#output-language}

Mỗi Organization viết bản tóm tắt AI bằng một ngôn ngữ đầu ra, là một language tag như `en`,
`vi` hoặc `pt-BR`. Ngôn ngữ này được chọn lúc đăng ký (`outputLanguage`, mặc định tiếng Anh)
và hiện trên trang Project, nơi quản trị viên có thể đổi nó. Thay đổi chỉ áp dụng cho các
thay đổi được phân loại sau đó.

```text
GET /api/organization/output-language                     (any member)
PUT /api/organization/output-language {"outputLanguage"}  (administrator)
```

Cả hai đều trả về `{outputLanguage, displayName, supported}`. Tag được chuẩn hoá (`vi_VN`
thành `vi-VN`); mọi tag có phần ngôn ngữ là mã ISO 639 đều được chấp nhận, và tag không hợp
lệ trả về `400 output_language_invalid`. `RELEASEFLOW_OUTPUT_LANGUAGES` (mặc định `en,vi`)
liệt kê các gợi ý hiển thị trong biểu mẫu.
