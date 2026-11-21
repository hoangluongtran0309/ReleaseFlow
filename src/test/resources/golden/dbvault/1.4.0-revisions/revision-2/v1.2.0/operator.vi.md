# Bản phát hành v1.2.0

Xem trước thời gian lưu trữ, thời gian theo múi giờ của bạn và giới hạn số lần đăng nhập thất bại. Đường dẫn API restore-verification cũ đã bị loại bỏ: hãy nâng cấp dbvault-cli cùng với máy chủ.

## Có gì mới

Bản phát hành này gồm 11 thay đổi: 1 thay đổi phá vỡ, 1 thay đổi bảo mật, 4 tính năng mới, 4 lỗi được sửa và 1 thay đổi bảo trì.

> ⚠️ 1 thay đổi phá vỡ cần được xử lý trước khi nâng cấp: [đọc hướng dẫn nâng cấp](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md).

## ⚠️ Thay đổi phá vỡ

- **Thay đổi gây ảnh hưởng đến API: endpoint xác minh đã bị loại bỏ; yêu cầu nâng cấp CLI** ([#58](https://github.com/hoangtulangtoc/dbvault/pull/58)) — Sau khi nâng cấp máy chủ, dbvault-cli cũng phải được nâng cấp, vì các phiên bản cũ sử dụng một endpoint API đã bị loại bỏ. Endpoint cũ hiện trả về mã trạng thái 404. Không cần thay đổi cấu hình, nhưng CLI và máy chủ phải được nâng cấp cùng nhau.
  - Lý do: Để đơn giản hóa API và tránh phải duy trì hai đường dẫn cho cùng một lần xác minh, vì chỉ có dbvault-cli trước đây sử dụng đường dẫn cũ và hiện nay đã chuyển sang sử dụng đường dẫn mới.
  - Chi tiết: Điểm cuối cũ GET /api/v1/verifications/{id} hiện không còn khả dụng và trả về mã trạng thái 404. Điểm cuối POST để tạo các xác minh đã trả về tiêu đề Location trỏ đến đường dẫn mới. Tài liệu và hướng dẫn nâng cấp đã được cập nhật tương ứng.
  - Giới hạn: Không có giai đoạn chuyển tiếp cho endpoint cũ; nó sẽ bị xóa ngay lập tức.
  - Bước bắt buộc: Hãy nâng cấp dbvault-cli lên phiên bản mới nhất cùng lúc với máy chủ, vì các phiên bản cũ sử dụng endpoint đã bị loại bỏ.

## 🔒 Bảo mật

- **Giới hạn tần suất đăng nhập theo địa chỉ; kiểm tra cấu hình proxy và các biến môi trường** ([#60](https://github.com/hoangtulangtoc/dbvault/pull/60)) — Sau 10 lần đăng nhập thất bại từ một địa chỉ trong vòng 15 phút, địa chỉ đó sẽ bị chặn trong 15 phút. Cấu hình các biến môi trường DBVAULT_SIGN_IN_MAX_FAILURES và DBVAULT_SIGN_IN_LOCK theo nhu cầu. Đảm bảo proxy ngược chuyển tiếp địa chỉ khách hàng để tránh chặn toàn bộ người dùng. Các số liệu thống kê được lưu trữ trong bộ nhớ và sẽ được đặt lại khi máy chủ khởi động lại.
  - Lý do: Để ngăn chặn các lần đăng nhập thất bại lặp đi lặp lại từ cùng một địa chỉ, giảm thiểu rủi ro từ các cuộc tấn công đoán mật khẩu.
  - Chi tiết: Sau 10 lần đăng nhập thất bại từ một địa chỉ trong vòng 15 phút, các lần thử tiếp theo từ địa chỉ đó sẽ bị chặn trong 15 phút. Console hiển thị thông báo lỗi, và API trả về mã trạng thái HTTP 429 kèm theo trường Retry-After. Giới hạn và thời gian khóa có thể được cấu hình bằng DBVAULT_SIGN_IN_MAX_FAILURES và DBVAULT_SIGN_IN_LOCK. Số lần thử sẽ được đặt lại khi đăng nhập thành công. Cơ chế này hoạt động trong bộ nhớ, do đó việc khởi động lại máy chủ sẽ xóa số lần thử. Nếu máy chủ proxy không chuyển tiếp địa chỉ của các máy khách, tất cả các máy khách có thể bị khóa cùng lúc.
  - Giới hạn: Khi sử dụng proxy không chuyển tiếp địa chỉ khách hàng, tất cả khách hàng sẽ chia sẻ một địa chỉ và bị khóa truy cập cùng lúc. Các số liệu thống kê được lưu trữ trong bộ nhớ, do đó việc khởi động lại hệ thống sẽ làm mất các số liệu này.
  - Bước khuyến nghị: Chúng tôi cũng khuyến nghị áp dụng giới hạn tốc độ (rate-limiting) cho yêu cầu POST /login tại máy chủ proxy ngược của bạn.

## ✨ Tính năng mới

- **dbvault backups hỗ trợ tùy chọn --limit để giới hạn kết quả hiển thị chỉ bao gồm các bản sao lưu mới nhất** ([!10](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/10)) — Người vận hành hiện có thể sử dụng tùy chọn --limit với các bản sao lưu dbvault để chỉ hiển thị các bản sao lưu mới nhất cho một đích, giúp đơn giản hóa các tập lệnh và kiểm tra thủ công. Không cần thay đổi gì về triển khai hoặc cấu hình.
  - Lý do: Để giúp người dùng tránh phải lật qua các trang lịch sử sao lưu dài và nhanh chóng truy cập các bản sao lưu gần đây nhất.
  - Chi tiết: Tùy chọn --limit chấp nhận giá trị từ 1 trở lên và giới hạn kết quả đầu ra ở số lượng bản sao lưu mới nhất tương ứng cho một đích cụ thể, trong cả chế độ đầu ra dạng bảng và JSON. Sử dụng --limit 0 sẽ dẫn đến lỗi sử dụng (exit code 2). Tệp README đã được cập nhật để ghi chú về tùy chọn này.
  - Giới hạn: Tùy chọn --limit không chấp nhận giá trị 0 hoặc số âm; việc sử dụng các giá trị này sẽ dẫn đến lỗi sử dụng.

- **Chức năng xem trước chính sách lưu trữ hiển thị các bản sao lưu có thể bị xóa trước khi lưu** ([#56](https://github.com/hoangtulangtoc/dbvault/pull/56)) — Người vận hành có thể xem trước các bản sao lưu mà chính sách lưu giữ sẽ xóa trước khi lưu các thay đổi. Không cần thực hiện bất kỳ bước vận hành nào; không có bản sao lưu nào bị xóa cho đến khi nhấn nút Lưu.

- **Endpoint API cho các nỗ lực xác minh khôi phục đã được cập nhật** ([#57](https://github.com/hoangtulangtoc/dbvault/pull/57)) — Các tập lệnh đọc các nỗ lực xác minh khôi phục nên sử dụng đường dẫn mới trong thư mục sao lưu; đường dẫn cũ đã bị xóa trong bản phát hành này.

- **Người vận hành có thể chọn múi giờ cho tất cả các thời gian hiển thị trên console** ([#61](https://github.com/hoangtulangtoc/dbvault/pull/61)) — Giờ đây, bạn có thể chọn múi giờ ưa thích của mình trong thanh bên, và tất cả thời gian trong Console sẽ được hiển thị theo múi giờ đó, kèm theo nhãn tương ứng. Cài đặt này chỉ áp dụng cho trình duyệt cụ thể và không ảnh hưởng đến lịch trình, API, CLI hoặc nhật ký, những thành phần này vẫn sử dụng múi giờ UTC.
  - Lý do: Thay đổi này nhằm đáp ứng nhu cầu của các nhà điều hành ở các múi giờ khác nhau trong việc xem thời gian theo múi giờ địa phương của họ, giúp tránh phải chuyển đổi thủ công.
  - Chi tiết: Một tùy chọn 'Múi giờ' mới xuất hiện trong thanh bên; việc chọn một múi giờ và áp dụng nó sẽ cập nhật tất cả các thời gian hiển thị (sao lưu, khôi phục, xác minh, các lần chạy lưu trữ, kiểm tra kết nối) theo múi giờ đó, kèm theo tên múi giờ được hiển thị. Lựa chọn này được lưu trong cookie trình duyệt và cũng có thể được thiết lập thông qua tham số URL ?tz=\<zone>. Cài đặt này chỉ áp dụng cho trình duyệt cụ thể và không liên quan đến tài khoản người dùng. Các lịch trình vẫn giữ múi giờ riêng của chúng, trong khi API HTTP, CLI và nhật ký vẫn sử dụng múi giờ UTC. Các nhãn có sẵn bằng tiếng Anh và tiếng Việt.

## 🐛 Sửa lỗi

- **Lệnh 'verify' trong CLI yêu cầu máy chủ phiên bản 1.2.0 trở lên; hãy nâng cấp cả hai cùng lúc** ([!11](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/11)) — Các triển khai phải nâng cấp cả máy chủ dbvault và CLI lên phiên bản 1.2.0 trở lên để tính năng kiểm tra xác thực hoạt động. Việc sử dụng CLI với máy chủ phiên bản cũ hơn sẽ khiến lệnh 'verify' kết thúc với mã lỗi 4.
  - Lý do: Máy chủ đã loại bỏ đường dẫn API xác minh cũ, do đó CLI cần tuân theo đường dẫn mới để duy trì tính tương thích.
  - Chi tiết: Lệnh 'verify' hiện yêu cầu 'GET /api/v1/backups/\<backup-id>/verifications/\<verification-id>' thay vì đường dẫn xác minh toàn cục cũ. CLI sẽ thoát với mã lỗi 4 nếu được sử dụng trên máy chủ phiên bản trước 1.2.0.
  - Giới hạn: Phiên bản CLI này không tương thích với máy chủ dbvault 1.1 hoặc các phiên bản cũ hơn về cơ chế kiểm tra xác thực định kỳ.
  - Bước bắt buộc: Hãy nâng cấp cả máy chủ dbvault lên phiên bản 1.2.0 trở lên và phiên bản CLI này cùng lúc để đảm bảo lệnh 'verify' hoạt động bình thường.

- **Lệnh `dbvault verify` sẽ từ chối giá trị --timeout bằng 0 hoặc âm với exit code 2** ([!12](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/12)) — Một tập lệnh truyền tham số `--timeout PT0S` hoặc thời lượng âm trước đây sẽ bắt đầu quá trình xác minh và trả về exit code 9; nay sẽ trả về exit code 2 (lỗi sử dụng) và không có quá trình nào được khởi chạy trên máy chủ.

- **Kích thước bản sao lưu không còn hiển thị là 1024,0 KB** ([#62](https://github.com/hoangtulangtoc/dbvault/pull/62)) — Một kích thước nhỏ hơn 1 MiB hiện được hiển thị là 1,0 MB trên Console.

- **Danh sách khôi phục hiện hiển thị các bản sao lưu nguồn đã bị xóa** ([#63](https://github.com/hoangtulangtoc/dbvault/pull/63)) — Giao diện người dùng (UI) hiện hiển thị rõ ràng khi bản sao lưu nguồn của quá trình khôi phục đã bị xóa. Không cần thực hiện bất kỳ thay đổi hoặc thao tác vận hành nào.

## 🔧 Bảo trì

- **Thông tin đăng nhập MinIO trong môi trường staging được thay đổi định kỳ** ([OPS-15](https://linear.app/dbvault/issue/OPS-15/rotate-the-minio-access-key-of-the-staging-dbvault)) — Khóa truy cập MinIO cho môi trường staging đã được xoay vòng và các secrets đã được cập nhật. Không cần thực hiện bất kỳ thay đổi nào về triển khai hoặc cấu hình đối với môi trường sản xuất hoặc môi trường người dùng.

**Hướng dẫn nâng cấp:** [Đọc trước khi nâng cấp](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md)
