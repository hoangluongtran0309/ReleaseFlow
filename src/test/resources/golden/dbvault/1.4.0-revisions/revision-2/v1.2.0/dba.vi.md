# Bản phát hành v1.2.0

Xem trước thời gian lưu trữ, thời gian theo múi giờ của bạn và giới hạn số lần đăng nhập thất bại. Đường dẫn API restore-verification cũ đã bị loại bỏ: hãy nâng cấp dbvault-cli cùng với máy chủ.

## Có gì mới

Bản phát hành này gồm 7 thay đổi: 1 thay đổi phá vỡ, 1 thay đổi bảo mật, 3 tính năng mới và 2 lỗi được sửa.

> ⚠️ 1 thay đổi phá vỡ cần được xử lý trước khi nâng cấp: [đọc hướng dẫn nâng cấp](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md).

## ⚠️ Thay đổi phá vỡ

- **Đường dẫn API xác minh đã thay đổi; cần nâng cấp CLI** ([#58](https://github.com/hoangtulangtoc/dbvault/pull/58)) — Endpoint xác minh đã thay đổi, yêu cầu dbvault-cli phải được nâng cấp cùng với máy chủ. Điều này không ảnh hưởng đến dữ liệu đã lưu trữ hoặc hành vi khôi phục, nhưng CLI phiên bản 1.1 trở về trước sẽ không hoạt động với máy chủ mới.
  - Bước bắt buộc: Hãy nâng cấp dbvault-cli lên phiên bản mới nhất cùng lúc với máy chủ, vì các phiên bản cũ sử dụng endpoint đã bị loại bỏ.

## 🔒 Bảo mật

- **Việc khóa đăng nhập sau nhiều lần nhập sai mật khẩu có thể ảnh hưởng đến quyền truy cập** ([#60](https://github.com/hoangtulangtoc/dbvault/pull/60)) — Sau 10 lần đăng nhập thất bại từ cùng một địa chỉ trong vòng 15 phút, các lần thử tiếp theo sẽ bị chặn trong 15 phút. Không có thay đổi nào đối với dữ liệu đã lưu trữ hoặc cấu trúc cơ sở dữ liệu, nhưng các quản trị viên cơ sở dữ liệu (DBA) nên đảm bảo các máy chủ proxy chuyển tiếp địa chỉ khách hàng để tránh việc bị khóa tài khoản do vô tình.

## ✨ Tính năng mới

- **dbvault-cli hiện có thể liệt kê chỉ các bản sao lưu mới nhất bằng tùy chọn --limit** ([!10](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/10)) — Giờ đây, bạn có thể sử dụng tùy chọn --limit với các bản sao lưu dbvault để nhanh chóng xem các bản sao lưu gần đây nhất cho một đích, giúp giảm kích thước đầu ra khi xem lại lịch sử sao lưu. Không có thay đổi nào đối với dữ liệu được lưu trữ hoặc hành vi khôi phục.

- **Xem trước các thao tác xóa bản sao lưu trước khi lưu các thay đổi về chính sách lưu trữ** ([#56](https://github.com/hoangtulangtoc/dbvault/pull/56)) — Các quản trị viên cơ sở dữ liệu (DBA) giờ đây có thể xem trước các bản sao lưu mà chính sách lưu giữ sẽ xóa trước khi áp dụng các thay đổi. Điều này giúp tránh mất dữ liệu ngoài ý muốn; không có bản sao lưu nào bị xóa cho đến khi nhấn nút Lưu.

- **Thời gian trên console có thể được hiển thị theo múi giờ đã chọn** ([#61](https://github.com/hoangtulangtoc/dbvault/pull/61)) — Giờ đây, bạn có thể thiết lập Console để hiển thị thời gian theo múi giờ địa phương của mình, giúp việc hiểu thời gian sao lưu và khôi phục trở nên dễ dàng hơn. Điều này không ảnh hưởng đến dữ liệu đã lưu trữ, lịch trình hoặc nhật ký, những thông tin này vẫn được duy trì theo múi giờ UTC.

## 🐛 Sửa lỗi

- **Lệnh 'verify' yêu cầu máy chủ dbvault phiên bản 1.2.0 trở lên để thực hiện quá trình kiểm tra định kỳ** ([!11](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/11)) — Sau khi nâng cấp, hãy đảm bảo cả CLI và máy chủ đều ở phiên bản 1.2.0 trở lên để chức năng xác minh hoạt động. Không có thay đổi nào đối với dữ liệu đã lưu trữ hoặc hành vi khôi phục.
  - Bước bắt buộc: Hãy nâng cấp cả máy chủ dbvault lên phiên bản 1.2.0 trở lên và phiên bản CLI này cùng lúc để đảm bảo lệnh 'verify' hoạt động bình thường.

- **Danh sách khôi phục làm rõ các bản sao lưu đã bị xóa** ([#63](https://github.com/hoangtulangtoc/dbvault/pull/63)) — Khi xem các bản khôi phục, nếu bản sao lưu nguồn đã bị xóa, giao diện người dùng (UI) hiện hiển thị 'Bản sao lưu đã bị xóa kể từ' thay vì dấu gạch ngang. Không có thay đổi nào đối với dữ liệu được lưu trữ hoặc hành vi khôi phục.

**Hướng dẫn nâng cấp:** [Đọc trước khi nâng cấp](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md)
