# Bản phát hành v1.2.0

Xem trước thời gian lưu trữ, thời gian theo múi giờ của bạn và giới hạn số lần đăng nhập thất bại. Đường dẫn API restore-verification cũ đã bị loại bỏ: hãy nâng cấp dbvault-cli cùng với máy chủ.

## Có gì mới

Bản phát hành này gồm 9 thay đổi: 1 thay đổi phá vỡ, 1 thay đổi bảo mật, 3 tính năng mới và 4 lỗi được sửa.

> ⚠️ 1 thay đổi phá vỡ cần được xử lý trước khi nâng cấp: [đọc hướng dẫn nâng cấp](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md).

## ⚠️ Thay đổi phá vỡ

- **Endpoint API GET /api/v1/verifications/{id} đã bị xóa và hiện trả về mã trạng thái 404; thay vào đó, hãy sử dụng GET /api/v1/backups/{backupId}/verifications/{id}.**
  - Bước bắt buộc: Hãy nâng cấp dbvault-cli lên phiên bản mới nhất cùng lúc với máy chủ, vì các phiên bản cũ sử dụng endpoint đã bị loại bỏ.

## 🔒 Bảo mật

- **Quá nhiều lần đăng nhập thất bại sẽ tạm thời chặn địa chỉ của bạn** — Nếu bạn nhập sai mật khẩu quá nhiều lần, bạn sẽ phải đợi 15 phút trước khi thử lại. Điều này giúp bảo vệ tài khoản của bạn.

## ✨ Tính năng mới

- **Chỉ xem các bản sao lưu mới nhất với tùy chọn --limit mới** — Giờ đây, bạn có thể sử dụng tùy chọn --limit để chỉ hiển thị các bản sao lưu mới nhất của một cơ sở dữ liệu, giúp việc tìm kiếm các bản sao lưu gần đây trở nên dễ dàng hơn.

- **Xem các bản sao lưu nào sẽ bị xóa trước khi lưu các thay đổi về thời gian lưu trữ** — Giờ đây, bạn có thể xem trước các bản sao lưu mà quy tắc lưu giữ sẽ xóa trước khi lưu quy tắc đó. Không có bản sao lưu nào bị xóa cho đến khi bạn xác nhận thay đổi.

- **Xem thời gian theo múi giờ của bạn trên console** — Giờ đây, bạn có thể chọn múi giờ của mình ở thanh bên, và tất cả thời gian trong Console sẽ được hiển thị theo múi giờ đó. Điều này giúp việc đọc thời gian sao lưu và khôi phục trở nên dễ dàng hơn.

## 🐛 Sửa lỗi

- **Lệnh 'verify' hiện yêu cầu phiên bản máy chủ mới hơn** — Để sử dụng lệnh 'verify', cả CLI và máy chủ đều phải được cập nhật lên phiên bản 1.2.0 trở lên. Cách sử dụng và kết quả đầu ra của lệnh này vẫn giữ nguyên.
  - Bước bắt buộc: Hãy nâng cấp cả máy chủ dbvault lên phiên bản 1.2.0 trở lên và phiên bản CLI này cùng lúc để đảm bảo lệnh 'verify' hoạt động bình thường.

- **Lỗi hiển thị khi giá trị --timeout bằng 0 hoặc âm trong lệnh 'dbvault verify'** — Nếu bạn đặt --timeout thành 0 hoặc một giá trị âm, bạn sẽ thấy thông báo lỗi và quá trình xác minh sẽ không bắt đầu.

- **Kích thước bản sao lưu không còn hiển thị là 1024,0 KB** — Một bản sao lưu có dung lượng dưới 1 MB hiện được hiển thị là 1,0 MB.

- **Trang Khôi phục hiện hiển thị thời điểm bản sao lưu bị xóa** — Nếu bản sao lưu của một tác vụ khôi phục đã bị xóa, bạn sẽ thấy thông báo 'Bản sao lưu đã bị xóa kể từ' thay vì dấu gạch ngang. Điều này giúp bạn hiểu lý do tại sao bản sao lưu đó bị thiếu.
