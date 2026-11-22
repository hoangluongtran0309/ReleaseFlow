# Bản phát hành v1.2.0

Xem trước thời gian lưu trữ, thời gian theo múi giờ của bạn và giới hạn số lần đăng nhập thất bại. Đường dẫn API restore-verification cũ đã bị loại bỏ: hãy nâng cấp dbvault-cli cùng với máy chủ.

## Có gì mới

Bản phát hành này gồm 11 thay đổi: 1 thay đổi phá vỡ, 1 thay đổi bảo mật, 4 tính năng mới, 4 lỗi được sửa và 1 cập nhật tài liệu.

> ⚠️ 1 thay đổi phá vỡ cần được xử lý trước khi nâng cấp: [đọc hướng dẫn nâng cấp](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md).

## ⚠️ Thay đổi phá vỡ

- **Điểm endpoint xác minh đã bị xóa; hãy cập nhật CLI và các tập lệnh theo đường dẫn mới** ([#58](https://github.com/hoangtulangtoc/dbvault/pull/58)) — Điểm cuối GET /api/v1/verifications/{id} đã bị xóa và hiện trả về mã trạng thái 404. Khách hàng sử dụng dbvault-cli phải nâng cấp công cụ này cùng với máy chủ, vì các phiên bản CLI cũ sử dụng đường dẫn cũ. Các tập lệnh nên tuân theo tiêu đề Location từ POST hoặc chuyển sang endpoint mới.
  - Khách hàng cần làm: Hãy nâng cấp dbvault-cli lên phiên bản mới nhất cùng lúc với máy chủ, vì các phiên bản cũ sử dụng endpoint đã bị loại bỏ.
  - Giới hạn: Không có giai đoạn chuyển tiếp cho endpoint cũ; nó sẽ bị xóa ngay lập tức.

## 🔒 Bảo mật

- **Các lần đăng nhập hiện bị giới hạn tần suất theo địa chỉ sau nhiều lần thất bại liên tiếp** ([#60](https://github.com/hoangtulangtoc/dbvault/pull/60)) — Sau 10 lần đăng nhập thất bại từ một địa chỉ trong vòng 15 phút, dbvault sẽ chặn các lần thử tiếp theo từ địa chỉ đó trong 15 phút. Khách hàng sẽ thấy thông báo lỗi hoặc mã trạng thái HTTP 429. Hãy khuyên khách hàng kiểm tra cấu hình mạng của họ nếu họ bị khóa tài khoản một cách bất ngờ, đặc biệt là khi đang sử dụng proxy. Đề xuất cấu hình các biến môi trường mới và thêm tính năng giới hạn tốc độ tại proxy ngược.
  - Nên khuyên khách hàng: Chúng tôi cũng khuyến nghị áp dụng giới hạn tốc độ (rate-limiting) cho yêu cầu POST /login tại máy chủ proxy ngược của bạn.
  - Giới hạn: Khi sử dụng proxy không chuyển tiếp địa chỉ khách hàng, tất cả khách hàng sẽ chia sẻ một địa chỉ và bị khóa truy cập cùng lúc. Các số liệu thống kê được lưu trữ trong bộ nhớ, do đó việc khởi động lại hệ thống sẽ làm mất các số liệu này.

## ✨ Tính năng mới

- **Tùy chọn --limit mới để chỉ liệt kê các bản sao lưu mới nhất trong dbvault-cli** ([!10](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/10)) — Khách hàng hiện có thể sử dụng tùy chọn --limit với các bản sao lưu dbvault để chỉ xem các bản sao lưu gần đây nhất cho một đích sao lưu, giúp dễ dàng tìm thấy các bản sao lưu gần đây mà không cần cuộn qua lịch sử dài.

- **Biểu mẫu chính sách lưu trữ hiện cho phép xem trước các bản sao lưu sẽ bị xóa** ([#56](https://github.com/hoangtulangtoc/dbvault/pull/56)) — Bộ phận hỗ trợ có thể thông báo cho khách hàng rằng biểu mẫu chính sách lưu trữ hiện đã có nút Xem trước. Tính năng này cho phép người dùng xem những bản sao lưu nào sẽ bị xóa theo chính sách trước khi lưu, giúp ngăn ngừa việc mất dữ liệu do vô ý. Không có bản sao lưu nào bị xóa cho đến khi nhấn nút Lưu.
  - Giới hạn: Chế độ xem trước chỉ mô phỏng việc xóa; các thay đổi thực tế chỉ diễn ra khi nhấn nút Lưu.

- **Đường dẫn API mới cho các lần thử xác minh khôi phục** ([#57](https://github.com/hoangtulangtoc/dbvault/pull/57)) — Các lần thử xác minh khôi phục hiện được đọc trong bản sao lưu tương ứng. Đường dẫn cũ đã bị loại bỏ trong bản phát hành này; xem phần thay đổi gây gián đoạn để biết những việc khách hàng cần thực hiện.

- **Console hiện hỗ trợ múi giờ do người dùng chọn để hiển thị thời gian** ([#61](https://github.com/hoangtulangtoc/dbvault/pull/61)) — Khách hàng hiện có thể chọn múi giờ ưa thích của mình trong thanh bên của Console, và tất cả thời gian sẽ được hiển thị tương ứng. Điều này giúp người dùng tránh phải chuyển đổi thời gian thủ công. Cài đặt này chỉ áp dụng cho trình duyệt cụ thể và không ảnh hưởng đến API, CLI hoặc nhật ký, vốn vẫn sử dụng múi giờ UTC.

## 🐛 Sửa lỗi

- **Lệnh 'verify' của dbvault-cli hiện yêu cầu máy chủ phiên bản 1.2.0 trở lên để thực hiện quá trình kiểm tra định kỳ.** ([!11](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/11)) — Khách hàng sử dụng lệnh 'verify' phải nâng cấp cả CLI và máy chủ lên phiên bản 1.2.0 trở lên. Nếu máy chủ không được nâng cấp, lệnh 'verify' sẽ thất bại với exit code 4. Không có thay đổi nào được hiển thị trong các tùy chọn hoặc kết quả đầu ra của lệnh.
  - Khách hàng cần làm: Hãy nâng cấp cả máy chủ dbvault lên phiên bản 1.2.0 trở lên và phiên bản CLI này cùng lúc để đảm bảo lệnh 'verify' hoạt động bình thường.
  - Giới hạn: Phiên bản CLI này không tương thích với máy chủ dbvault 1.1 hoặc các phiên bản cũ hơn về cơ chế kiểm tra xác thực định kỳ.

- **Lệnh 'dbvault verify' hiện từ chối các giá trị --timeout bằng 0 hoặc âm** ([!12](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/12)) — Khách hàng sử dụng 'dbvault verify' với giá trị --timeout bằng 0 hoặc âm giờ đây sẽ thấy thông báo lỗi và quá trình xác minh sẽ không được bắt đầu. Không cần thực hiện bất kỳ hành động nào trừ khi họ đã sử dụng các giá trị đó.

- **Kích thước bản sao lưu không còn hiển thị là 1024,0 KB** ([#62](https://github.com/hoangtulangtoc/dbvault/pull/62)) — Kích thước trước đây là dưới 1 MB nay hiển thị là 1,0 MB; không có thay đổi nào về bản sao lưu.

- **Danh sách khôi phục hiện hiển thị thời điểm bản sao lưu nguồn bị xóa** ([#63](https://github.com/hoangtulangtoc/dbvault/pull/63)) — Giờ đây, khách hàng sẽ thấy thông báo 'Bản sao lưu đã bị xóa kể từ' nếu bản sao lưu nguồn của quá trình khôi phục đã bị xóa, thay vì dấu gạch ngang. Điều này làm rõ rằng bản sao lưu đã bị xóa và không phải là lỗi tải. Không cần thực hiện bất kỳ hành động nào.

## 📚 Tài liệu

- **Hướng dẫn nâng cấp lên phiên bản 1.2 đã được bổ sung vào tài liệu** ([#59](https://github.com/hoangtulangtoc/dbvault/pull/59)) — Một hướng dẫn mới giải thích cách nâng cấp lên phiên bản 1.2, bao gồm việc loại bỏ một endpoint cụ thể. Khách hàng đang sử dụng các tập lệnh hoặc tính năng xác minh dbvault-cli 1.1 có thể bị ảnh hưởng.

**Hướng dẫn nâng cấp:** [Đọc trước khi nâng cấp](https://github.com/hoangtulangtoc/dbvault/blob/v1.2.0/docs/upgrading-to-1.2.md)
