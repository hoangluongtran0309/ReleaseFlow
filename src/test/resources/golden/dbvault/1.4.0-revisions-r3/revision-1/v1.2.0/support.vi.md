# Bản phát hành v1.2.0

Xem trước thời gian lưu trữ, thời gian theo múi giờ của bạn và giới hạn số lần đăng nhập thất bại. Đường dẫn API restore-verification cũ đã bị loại bỏ: hãy nâng cấp dbvault-CLI cùng với máy chủ.

## Có gì mới

Bản phát hành này gồm 11 thay đổi: 1 thay đổi phá vỡ, 1 thay đổi bảo mật, 4 tính năng mới, 4 lỗi được sửa và 1 cập nhật tài liệu.

> ⚠️ 1 thay đổi phá vỡ cần được xử lý trước khi nâng cấp: [đọc hướng dẫn nâng cấp](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md).

## ⚠️ Thay đổi phá vỡ

- **Endpoint xác minh đã bị loại bỏ; cần nâng cấp CLI cùng với máy chủ** ([#58](https://github.com/hoangtulangtoc/dbvault/pull/58)) — Khách hàng phải nâng cấp dbvault-cli cùng lúc với máy chủ, vì endpoint xác minh cũ đã bị loại bỏ và các phiên bản CLI cũ sẽ không hoạt động. Hãy khuyên khách hàng sử dụng endpoint mới hoặc tuân theo tiêu đề Location từ yêu cầu POST.
  - Bước bắt buộc: Hãy nâng cấp dbvault-CLI lên phiên bản mới nhất cùng lúc với máy chủ, vì các phiên bản cũ sử dụng endpoint đã bị loại bỏ.
  - Giới hạn: Không có giai đoạn chuyển tiếp cho endpoint cũ; nó sẽ bị xóa ngay lập tức.

## 🔒 Bảo mật

- **Khóa tài khoản sau nhiều lần đăng nhập thất bại: nên thông báo gì cho khách hàng** ([#60](https://github.com/hoangtulangtoc/dbvault/pull/60)) — Nếu khách hàng bị khóa tài khoản sau quá nhiều lần đăng nhập thất bại, hãy giải thích rằng hệ thống sẽ chặn các lần thử tiếp theo từ địa chỉ IP của họ trong 15 phút. Hãy khuyên họ kiểm tra cài đặt proxy nếu nhiều người dùng bị ảnh hưởng, và đề xuất cấu hình các biến môi trường mới hoặc thêm giới hạn tốc độ tại proxy ngược.
  - Bước khuyến nghị: Chúng tôi cũng khuyến nghị áp dụng giới hạn tốc độ (rate-limiting) cho yêu cầu POST /login tại máy chủ proxy ngược của bạn.
  - Giới hạn: Khi sử dụng proxy không chuyển tiếp địa chỉ khách hàng, tất cả khách hàng sẽ chia sẻ một địa chỉ và bị khóa truy cập cùng lúc. Các số liệu thống kê được lưu trữ trong bộ nhớ, do đó việc khởi động lại hệ thống sẽ làm mất các số liệu này.

## ✨ Tính năng mới

- **Các bản sao lưu dbvault hiện hỗ trợ tùy chọn --limit để chỉ hiển thị các bản sao lưu mới nhất** ([!10](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/10)) — Khuyên khách hàng nên sử dụng tùy chọn --limit khi sao lưu bằng dbvault nếu họ chỉ muốn xem các bản sao lưu mới nhất cho một đích sao lưu. Điều này giúp tránh kết quả đầu ra dài dòng khi có nhiều bản sao lưu tồn tại.

- **Biểu mẫu chính sách lưu trữ hiện cho phép xem trước các mục sẽ bị xóa trước khi lưu** ([#56](https://github.com/hoangtulangtoc/dbvault/pull/56)) — Khách hàng hiện có thể sử dụng nút Xem trước trong biểu mẫu chính sách lưu trữ để xem những bản sao lưu nào sẽ bị xóa theo chính sách trước khi lưu. Hãy thông báo cho họ rằng không có bản sao lưu nào bị xóa cho đến khi nhấn nút Lưu.
  - Giới hạn: Chế độ xem trước chỉ mô phỏng việc xóa; các thay đổi thực tế chỉ diễn ra khi nhấn nút Lưu.

- **Điểm endpoint xác minh khôi phục đã thay đổi** ([#57](https://github.com/hoangtulangtoc/dbvault/pull/57)) — Các lần thử xác minh khôi phục hiện được đọc trong bản sao lưu tương ứng. Đường dẫn cũ đã bị loại bỏ trong bản phát hành này; xem phần thay đổi gây gián đoạn để biết những việc khách hàng cần thực hiện.

- **Console hiện cho phép người dùng chọn múi giờ để hiển thị thời gian** ([#61](https://github.com/hoangtulangtoc/dbvault/pull/61)) — Người dùng có thể chọn múi giờ trong thanh bên, và tất cả thời gian trong console sẽ được hiển thị theo múi giờ đó. Điều này giúp tránh nhầm lẫn cho người dùng ở ngoài múi giờ UTC. Cài đặt này chỉ áp dụng cho trình duyệt cụ thể và không ảnh hưởng đến API, CLI hoặc nhật ký.

## 🐛 Sửa lỗi

- **Lệnh 'verify' sẽ thất bại với exit code 4 nếu phiên bản máy chủ không phải là 1.2.0 trở lên** ([!11](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/11)) — Nếu khách hàng báo cáo lỗi 'verify', hãy kiểm tra xem cả CLI và máy chủ đều đang chạy phiên bản 1.2.0 trở lên. Khuyến nghị nâng cấp cả hai cùng lúc; không có thay đổi nào trên dòng lệnh được hiển thị.
  - Bước bắt buộc: Hãy nâng cấp cả máy chủ dbvault lên phiên bản 1.2.0 trở lên và phiên bản CLI này cùng lúc để đảm bảo lệnh 'verify' hoạt động bình thường.
  - Giới hạn: Phiên bản CLI này không tương thích với máy chủ dbvault 1.1 hoặc các phiên bản cũ hơn về cơ chế kiểm tra xác thực định kỳ.

- **Lệnh 'dbvault verify' chặn các giá trị --timeout bằng 0 hoặc âm** ([!12](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/12)) — Nếu khách hàng báo cáo lỗi với lệnh 'dbvault verify' và giá trị --timeout bằng 0 hoặc âm, hãy giải thích rằng đây hiện là lỗi sử dụng và quá trình xác minh sẽ không được khởi chạy. Hãy khuyên họ sử dụng giá trị thời gian chờ dương.

- **Kích thước bản sao lưu không còn hiển thị là 1024,0 KB** ([#62](https://github.com/hoangtulangtoc/dbvault/pull/62)) — Nếu khách hàng hỏi về kích thước như 1024,0 KB, giờ đây nó sẽ hiển thị là 1,0 MB; không có gì thay đổi về bản sao lưu.

- **Danh sách khôi phục hiện giải thích về các bản sao lưu nguồn đã bị xóa** ([#63](https://github.com/hoangtulangtoc/dbvault/pull/63)) — Nếu khách hàng hỏi về dấu gạch ngang trong danh sách Khôi phục, hãy giải thích rằng hiện tại nó hiển thị 'Bản sao lưu đã bị xóa kể từ' khi bản sao lưu nguồn bị xóa. Không cần thực hiện bất kỳ thao tác nào.

## 📚 Tài liệu

- **Hướng dẫn nâng cấp mới cho phiên bản 1.2 nêu chi tiết tác động của các thay đổi API** ([#59](https://github.com/hoangtulangtoc/dbvault/pull/59)) — Bộ phận hỗ trợ có thể tham khảo hướng dẫn mới để biết thông tin về endpoint xác minh đã bị loại bỏ và các bước nâng cấp lên phiên bản 1.2 dành cho người dùng bị ảnh hưởng.
