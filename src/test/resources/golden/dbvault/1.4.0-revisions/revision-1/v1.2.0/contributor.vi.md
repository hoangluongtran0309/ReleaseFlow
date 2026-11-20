# Bản phát hành v1.2.0

Xem trước thời gian lưu trữ, thời gian theo múi giờ của bạn và giới hạn số lần đăng nhập thất bại. Đường dẫn API restore-verification cũ đã bị loại bỏ: hãy nâng cấp dbvault-CLI cùng với máy chủ.

## Có gì mới

Bản phát hành này gồm 14 thay đổi: 1 thay đổi phá vỡ, 1 thay đổi bảo mật, 4 tính năng mới, 4 lỗi được sửa, 1 cập nhật tài liệu và 3 thay đổi bảo trì.

> ⚠️ 1 thay đổi phá vỡ cần được xử lý trước khi nâng cấp: [đọc hướng dẫn nâng cấp](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md).

## ⚠️ Thay đổi phá vỡ

- **Đã loại bỏ GET /api/v1/verifications/{id}; hãy sử dụng đường dẫn trong phạm vi sao lưu** ([#58](https://github.com/hoangtulangtoc/dbvault/pull/58)) — Điểm cuối GET /api/v1/verifications/{id} đã bị loại bỏ và trả về mã trạng thái 404. Thay vào đó, hãy sử dụng GET /api/v1/backups/{backupId}/verifications/{id}. Điểm cuối POST đã trả về thông tin Location chính xác. Hãy cập nhật bất kỳ mã nguồn hoặc tập lệnh nào đang sử dụng đường dẫn cũ. Xem tệp docs/api.md và hướng dẫn nâng cấp lên phiên bản 1.2.
  - Lý do: Để đơn giản hóa API và tránh phải duy trì hai đường dẫn cho cùng một lần xác minh, vì chỉ có dbvault-CLI trước đây sử dụng đường dẫn cũ và hiện nay đã chuyển sang sử dụng đường dẫn mới.
  - Cách triển khai: Điểm cuối cũ GET /api/v1/verifications/{id} hiện không còn khả dụng và trả về mã trạng thái 404. Điểm cuối POST để tạo các xác minh đã trả về tiêu đề Location trỏ đến đường dẫn mới. Tài liệu và hướng dẫn nâng cấp đã được cập nhật tương ứng.
  - Giới hạn: Không có giai đoạn chuyển tiếp cho endpoint cũ; nó sẽ bị xóa ngay lập tức.
  - Bước bắt buộc: Hãy nâng cấp dbvault-CLI lên phiên bản mới nhất cùng lúc với máy chủ, vì các phiên bản cũ sử dụng endpoint đã bị loại bỏ.

## 🔒 Bảo mật

- **Đã thêm tính năng giới hạn số lần đăng nhập: 10 lần thất bại cho mỗi địa chỉ trong vòng 15 phút sẽ kích hoạt tính năng khóa tài khoản** ([#60](https://github.com/hoangtulangtoc/dbvault/pull/60)) — Một bộ giới hạn tốc độ mới trong bộ nhớ sẽ chặn các nỗ lực đăng nhập từ một địa chỉ sau 10 lần thất bại trong vòng 15 phút. Các giới hạn này có thể được cấu hình thông qua DBVAULT_SIGN_IN_MAX_FAILURES và DBVAULT_SIGN_IN_LOCK. Cơ chế này được chia sẻ giữa console và API, và sẽ được đặt lại sau khi đăng nhập thành công. Các bài kiểm tra đã được thêm vào SignInThrottleTest, SecurityConfigTest và ApiSecurityTest.
  - Lý do: Để ngăn chặn các lần đăng nhập thất bại lặp đi lặp lại từ cùng một địa chỉ, giảm thiểu rủi ro từ các cuộc tấn công đoán mật khẩu.
  - Cách triển khai: Sau 10 lần đăng nhập thất bại từ một địa chỉ trong vòng 15 phút, các lần thử tiếp theo từ địa chỉ đó sẽ bị chặn trong 15 phút. Console hiển thị thông báo lỗi, và API trả về mã trạng thái HTTP 429 kèm theo trường Retry-After. Giới hạn và thời gian khóa có thể được cấu hình bằng DBVAULT_SIGN_IN_MAX_FAILURES và DBVAULT_SIGN_IN_LOCK. Số lần thử sẽ được đặt lại khi đăng nhập thành công. Cơ chế này hoạt động trong bộ nhớ, do đó việc khởi động lại máy chủ sẽ xóa số lần thử. Nếu máy chủ proxy không chuyển tiếp địa chỉ của các máy khách, tất cả các máy khách có thể bị khóa cùng lúc.
  - Giới hạn: Khi sử dụng proxy không chuyển tiếp địa chỉ khách hàng, tất cả khách hàng sẽ chia sẻ một địa chỉ và bị khóa truy cập cùng lúc. Các số liệu thống kê được lưu trữ trong bộ nhớ, do đó việc khởi động lại hệ thống sẽ làm mất các số liệu này.
  - Bước khuyến nghị: Chúng tôi cũng khuyến nghị áp dụng giới hạn tốc độ (rate-limiting) cho yêu cầu POST /login tại máy chủ proxy ngược của bạn.

## ✨ Tính năng mới

- **Đã thêm tùy chọn --limit vào các bản sao lưu dbvault để giới hạn kết quả đầu ra chỉ hiển thị các bản sao lưu mới nhất** ([!10](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/10)) — Lệnh sao lưu dbvault hiện hỗ trợ tùy chọn --limit \<COUNT> để chỉ hiển thị \<COUNT> bản sao lưu mới nhất cho một đích, trong cả định dạng bảng và JSON. Tùy chọn này yêu cầu giá trị từ 1 trở lên; giá trị 0 sẽ gây ra lỗi sử dụng (exit code 2). Các bài kiểm tra và tài liệu đã được cập nhật tương ứng.
  - Lý do: Để giúp người dùng tránh phải lật qua các trang lịch sử sao lưu dài và nhanh chóng truy cập các bản sao lưu gần đây nhất.
  - Cách triển khai: Tùy chọn --limit chấp nhận giá trị từ 1 trở lên và giới hạn kết quả đầu ra ở số lượng bản sao lưu mới nhất tương ứng cho một đích cụ thể, trong cả chế độ đầu ra dạng bảng và JSON. Sử dụng --limit 0 sẽ dẫn đến lỗi sử dụng (exit code 2). Tệp README đã được cập nhật để ghi chú về tùy chọn này.
  - Giới hạn: Tùy chọn --limit không chấp nhận giá trị 0 hoặc số âm; việc sử dụng các giá trị này sẽ dẫn đến lỗi sử dụng.

- **Đã thêm endpoint xem trước thời gian lưu trữ và giao diện người dùng để mô phỏng việc xóa bản sao lưu** ([#56](https://github.com/hoangtulangtoc/dbvault/pull/56)) — Nút "Xem trước" đã được thêm vào biểu mẫu lưu trữ, gọi `POST /retention/{targetId}/preview` để mô phỏng các bản sao lưu nào sẽ bị xóa theo chính sách hiện tại. Logic này khớp với các lần chạy lưu trữ thực tế, và các bài kiểm tra mới đã bao quát điều này trong `ManageBackupRetentionServiceTest` và `BackupRetentionControllerTest`. Không có thay đổi trạng thái nào xảy ra cho đến khi nhấn "Lưu".
  - Lý do: Để giúp người dùng hiểu rõ tác động của chính sách lưu trữ trước khi áp dụng, từ đó giảm thiểu rủi ro mất dữ liệu do sơ suất.
  - Cách triển khai: Biểu mẫu lưu trữ hiện bao gồm nút Xem trước, nút này sẽ gửi số lượng đã nhập đến endpoint `POST /retention/{targetId}/preview`. Phản hồi hiển thị số lượng bản sao lưu sẽ bị xóa và thời gian bắt đầu của chúng, phù hợp với logic của một lần chạy lưu trữ thực tế. Không có thay đổi nào được lưu hoặc bản sao lưu nào bị xóa cho đến khi nhấn nút Lưu. Tính năng này hỗ trợ cả tiếng Anh và tiếng Việt.
  - Giới hạn: Chế độ xem trước chỉ mô phỏng việc xóa; các thay đổi thực tế chỉ diễn ra khi nhấn nút Lưu.

- **API xác minh khôi phục hiện được lồng ghép trong phần sao lưu** ([#57](https://github.com/hoangtulangtoc/dbvault/pull/57)) — Hãy thử thực hiện yêu cầu `GET /api/v1/backups/{backupId}/verifications/{id}`; trường `Location` của yêu cầu POST đã trỏ đến địa chỉ này. Yêu cầu cũ `GET /api/v1/verifications/{id}` đã bị loại bỏ trong bản phát hành này, xem thay đổi gây gián đoạn.
  - Lý do: Nó nhóm từng quá trình xác minh dưới bản sao lưu mà nó xác minh, đường dẫn này sẽ được giữ lại sau khi đường dẫn cũ bị xóa trong bản phát hành này (xem thay đổi gây gián đoạn).
  - Cách triển khai: Endpoint mới là `GET /api/v1/backups/{backupId}/verifications/{id}`, và thuộc tính `Location` của một quá trình xác minh mới sẽ trỏ đến đó. Nó trả về mã trạng thái `404` khi có nỗ lực truy cập một bản sao lưu khác.
  - Bước khuyến nghị: Hãy cập nhật bất kỳ tích hợp hoặc tập lệnh nào đọc các nỗ lực xác minh khôi phục để sử dụng endpoint mới; endpoint cũ đã bị loại bỏ trong bản phát hành này.

- **Tính năng chọn múi giờ đã được thêm vào giao diện người dùng (UI) của console; thời gian được hiển thị theo múi giờ đã chọn** ([#61](https://github.com/hoangtulangtoc/dbvault/pull/61)) — Một công cụ chọn múi giờ hiện đã có trong thanh bên. Múi giờ được chọn sẽ được lưu trong cookie trình duyệt và áp dụng cho tất cả các hiển thị thời gian trong giao diện người dùng Console. Việc triển khai này không ảnh hưởng đến logic backend, API, CLI hoặc nhật ký, những thành phần này vẫn tiếp tục sử dụng UTC. Các lịch trình vẫn giữ nguyên múi giờ riêng của chúng.
  - Lý do: Thay đổi này nhằm đáp ứng nhu cầu của các nhà điều hành ở các múi giờ khác nhau trong việc xem thời gian theo múi giờ địa phương của họ, giúp tránh phải chuyển đổi thủ công.
  - Cách triển khai: Một tùy chọn 'Múi giờ' mới xuất hiện trong thanh bên; việc chọn một múi giờ và áp dụng nó sẽ cập nhật tất cả các thời gian hiển thị (sao lưu, khôi phục, xác minh, các lần chạy lưu trữ, kiểm tra kết nối) theo múi giờ đó, kèm theo tên múi giờ được hiển thị. Lựa chọn này được lưu trong cookie trình duyệt và cũng có thể được thiết lập thông qua tham số URL ?tz=\<ZONE>. Cài đặt này chỉ áp dụng cho trình duyệt cụ thể và không liên quan đến tài khoản người dùng. Các lịch trình vẫn giữ múi giờ riêng của chúng, trong khi API HTTP, CLI và nhật ký vẫn sử dụng múi giờ UTC. Các nhãn có sẵn bằng tiếng Anh và tiếng Việt.

## 🐛 Sửa lỗi

- **Lệnh 'verify' hiện kiểm tra trạng thái xác minh tại đường dẫn API dành riêng cho sao lưu** ([!11](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/11)) — CLI hiện sử dụng 'GET /api/v1/backups/\<BACKUP-id>/verifications/\<VERIFICATION-id>' để kiểm tra định kỳ, phù hợp với thay đổi API của máy chủ trong phiên bản 1.2.0. Các bài kiểm tra dự kiến đường dẫn cũ sẽ trả về mã trạng thái 404. Hãy đảm bảo tính tương thích bằng cách nâng cấp cả CLI và máy chủ.
  - Lý do: Máy chủ đã loại bỏ đường dẫn API xác minh cũ, do đó CLI cần tuân theo đường dẫn mới để duy trì tính tương thích.
  - Cách triển khai: Lệnh 'verify' hiện yêu cầu 'GET /api/v1/backups/\<BACKUP-id>/verifications/\<VERIFICATION-id>' thay vì đường dẫn xác minh toàn cục cũ. CLI sẽ thoát với mã lỗi 4 nếu được sử dụng trên máy chủ phiên bản trước 1.2.0.
  - Giới hạn: Phiên bản CLI này không tương thích với máy chủ dbvault 1.1 hoặc các phiên bản cũ hơn về cơ chế kiểm tra xác thực định kỳ.
  - Bước bắt buộc: Hãy nâng cấp cả máy chủ dbvault lên phiên bản 1.2.0 trở lên và phiên bản CLI này cùng lúc để đảm bảo lệnh 'verify' hoạt động bình thường.

- **Lệnh 'dbvault verify' sẽ kết thúc với lỗi sử dụng nếu giá trị --timeout bằng 0 hoặc âm.** ([!12](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/12)) — CLI hiện kiểm tra xem --timeout <= 0 trước khi kết nối với máy chủ. Nó sẽ thoát với exit code 2 và thông báo lỗi nếu giá trị không hợp lệ. Các bài kiểm tra đã được cập nhật: 'aTimeoutOfZeroOrLessIsAUsageErrorAndStartsNothing' bao gồm trường hợp này, và 'verifyGivesUpAfterItsTimeout' sử dụng thời gian chờ dương.
  - Lý do: Trước đây, thời gian chờ bằng 0 hoặc âm sẽ khởi động quá trình xác minh trên máy chủ và ngay lập tức từ bỏ, khiến một quá trình xác minh không cần thiết vẫn tiếp tục chạy.
  - Cách triển khai: Nếu --timeout được đặt thành 0 hoặc một khoảng thời gian âm, 'dbvault verify' sẽ thoát với exit code 2 và hiển thị thông báo lỗi. Quá trình xác minh sẽ không được bắt đầu trên máy chủ. Thời gian chờ dương và hành vi mặc định không thay đổi.

- **Các giá trị nằm ngay dưới ngưỡng đơn vị sẽ được làm tròn lên đơn vị tiếp theo** ([#62](https://github.com/hoangtulangtoc/dbvault/pull/62)) — `ByteSizes.readable` không còn hiển thị các giá trị như 1024.0 KB nữa; hàm này đã chuyển đơn vị từ 1023.95.

- **Danh sách khôi phục làm rõ các bản sao lưu nguồn đã xóa trong giao diện người dùng** ([#63](https://github.com/hoangtulangtoc/dbvault/pull/63)) — Danh sách Khôi phục hiện kiểm tra xem bản sao lưu nguồn có tồn tại hay không; nếu không, nó sẽ hiển thị một thông báo được bản địa hóa ('Bản sao lưu đã bị xóa kể từ') thay vì dấu gạch ngang. Phạm vi kiểm thử được mô tả trong RestoreControllerTest.theListSaysWhenTheBackupARestoreCameFromWasDeleted.
  - Lý do: Trước đây, một bản sao lưu nguồn đã bị xóa chỉ hiển thị dấu gạch ngang, trông giống như dữ liệu bị thiếu và gây nhầm lẫn cho người dùng; điều này giúp làm rõ tình huống.
  - Cách triển khai: Cột “Từ bản sao lưu” trong danh sách “Khôi phục” sẽ kiểm tra xem bản sao lưu nguồn còn tồn tại hay không; nếu không, nó sẽ hiển thị một thông báo cho biết bản sao lưu đã bị xóa, kèm theo bản dịch sang tiếng Anh và tiếng Việt.

## 📚 Tài liệu

- **Hướng dẫn nâng cấp lên phiên bản 1.2: tài liệu hướng dẫn việc loại bỏ endpoint xác minh** ([#59](https://github.com/hoangtulangtoc/dbvault/pull/59)) — Tài liệu hiện đã bao gồm hướng dẫn nâng cấp lên phiên bản 1.2, nêu chi tiết việc loại bỏ GET /api/v1/verifications/{id}, những đối tượng bị ảnh hưởng, thứ tự nâng cấp và các bước khôi phục. Không có thay đổi về mã nguồn, chỉ có tài liệu.
  - Lý do: Để thông báo cho người dùng và nhà phát triển về quy trình nâng cấp lên phiên bản 1.2 và tác động của việc loại bỏ endpoint API.
  - Cách triển khai: Hướng dẫn này nằm tại tệp docs/upgrading-to-1.2.md, được liên kết từ các tệp docs/README.md và docs/api.md. Hướng dẫn này giải thích những ai sẽ bị ảnh hưởng bởi endpoint bị loại bỏ, thứ tự nâng cấp được khuyến nghị và các bước khôi phục.

## 🔧 Bảo trì

- **MongoBackupRestoreIT bị bỏ qua trên GitHub Actions CI cho đến khi lỗi DBV-45 được khắc phục** ([#55](https://github.com/hoangtulangtoc/dbvault/pull/55)) — Bài kiểm thử tích hợp MongoBackupRestoreIT sẽ không chạy trên GitHub Actions (khi GITHUB_ACTIONS=true) do các lỗi container xảy ra thường xuyên. Bài kiểm thử vẫn chạy được trên máy cục bộ. Mã sản xuất không bị ảnh hưởng. Xem DBV-45 để biết thông tin về quá trình điều tra đang diễn ra.
  - Lý do: Bài kiểm thử thường xuyên thất bại trên CI do container MongoDB dừng hoạt động bất ngờ, gây cản trở việc hợp nhất các pull request.
  - Cách triển khai: Việc kiểm tra sẽ được bỏ qua có điều kiện khi biến môi trường GITHUB_ACTIONS được đặt thành true; nó vẫn chạy cục bộ cho các nhà phát triển. Không có mã sản xuất hoặc logic sao lưu/khôi phục nào bị ảnh hưởng.
  - Giới hạn: Thay đổi này không giải quyết vấn đề cơ bản liên quan đến container MongoDB trên CI; nó chỉ giúp CI hoạt động trở lại bằng cách bỏ qua bài kiểm tra.

- **Phụ thuộc BOM của AWS SDK đã được cập nhật lên phiên bản 2.57.6** ([#52](https://github.com/hoangtulangtoc/dbvault/pull/52)) — Bản dựng hiện sử dụng phiên bản 2.57.6 của software.amazon.awssdk:bom. Vui lòng kiểm tra các vấn đề tương thích nếu bạn sử dụng trực tiếp các mô-đun AWS SDK.

- **Khóa truy cập MinIO trên môi trường staging đã được xoay vòng và các secrets đã được cập nhật** ([OPS-15](https://linear.app/dbvault/issue/OPS-15/rotate-the-minio-access-key-of-the-staging-dbvault)) — Khóa truy cập MinIO cho môi trường staging đã được thay đổi, kèm theo các cập nhật đối với các biến môi trường và secrets CI. Không có thay đổi nào được thực hiện đối với mã nguồn; dbvault tiếp tục đọc thông tin đăng nhập từ các biến môi trường.

**Hướng dẫn nâng cấp:** [Đọc trước khi nâng cấp](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md)
