# Bản phát hành v0.3.0

Sao lưu MongoDB, thông báo và xác minh khôi phục.

## Có gì mới

Bản phát hành này gồm 13 thay đổi: 3 thay đổi phá vỡ, 5 tính năng mới, 1 lỗi được sửa, 1 cập nhật tài liệu và 3 thay đổi bảo trì.

> ⚠️ 3 thay đổi phá vỡ cần được xử lý trước khi nâng cấp.

## ⚠️ Thay đổi phá vỡ

- **Một lệnh 'verify' mới đã được thêm vào CLI, cho phép người dùng bắt đầu và theo dõi quá trình xác minh khôi phục cho một bản sao lưu. Tất cả các lệnh hiện nay đều sử dụng endpoint API có phiên bản (/api/v1), ngừng hỗ trợ các đường dẫn không có phiên bản và các API máy chủ cũ hơn (0.2).** ([!5](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/5))
  - Cách chuyển đổi: Hãy cập nhật cả CLI và máy chủ lên phiên bản 0.3 hoặc mới hơn trước khi sử dụng. Điều chỉnh các tập lệnh hoặc tích hợp để thay thế các đường dẫn API không có phiên bản bằng '/api/v1'. Kiểm tra để đảm bảo rằng các máy chủ cũ (0.2) không còn được sử dụng nữa.

- **Các endpoint API HTTP hiện đã được phân phiên bản dưới /api/v1. Tất cả các đường dẫn /api/targets... trước đây hiện sẽ trả về phản hồi chuyển hướng 308 đến các endpoint tương đương mới /api/v1... và đã bị loại bỏ. Một endpoint mới để bắt đầu và theo dõi quá trình xác minh khôi phục bản sao lưu đã được thêm vào, và tài liệu API đã được cập nhật. Tính năng bảo vệ CSRF đã bị loại bỏ khỏi API (nhưng không phải trên console).** ([#34](https://github.com/hoangtulangtoc/dbvault/pull/34))
  - Cách chuyển đổi: Cập nhật tất cả các tập lệnh, tích hợp và máy khách để gọi trực tiếp các endpoint /api/v1; đảm bảo tính năng theo dõi chuyển hướng đã được bật hoặc cập nhật lên phiên bản máy khách tương thích trước khi nâng cấp.

- **Các endpoint '/api/targets' và '/api/targets/{id}/backups' cũ không có phiên bản đã bị xóa. Bộ điều khiển chuyển hướng liên quan và bài kiểm tra của nó cũng đã bị xóa. Tài liệu đã được cập nhật để phản ánh việc xóa bỏ thay vì chuyển hướng.** ([#35](https://github.com/hoangtulangtoc/dbvault/pull/35))
  - Cách chuyển đổi: Cập nhật tất cả các client hoặc skript để sử dụng các endpoint '/api/v1/...' thay vì các đường dẫn cũ '/api/...'. Sau khi thay đổi này, sẽ không thể quay lại các đường dẫn không có phiên bản.

## ✨ Tính năng mới

- **Đã thêm hỗ trợ sao lưu và khôi phục MongoDB như một engine thứ ba, sử dụng mongodump/mongorestore, đồng thời cập nhật xử lý xác thực/cơ sở dữ liệu, định dạng artifact và các yêu cầu về CLI/CI.** ([#31](https://github.com/hoangtulangtoc/dbvault/pull/31))
  - Cách chuyển đổi: Áp dụng bản cập nhật V11 để thêm cột cơ sở dữ liệu xác thực. Không cần thay đổi gì trừ khi sử dụng các đích cơ sở dữ liệu MongoDB.

- **Đã thêm các kênh thông báo (Slack, email, webhook chung) để cảnh báo người vận hành về các sự kiện sao lưu và khôi phục, kèm theo các trang quản lý mới và tính năng đăng ký theo từng đích.** ([#32](https://github.com/hoangtulangtoc/dbvault/pull/32))
  - Cách chuyển đổi: Sau khi nâng cấp, hãy xem xét và cấu hình các kênh thông báo cũng như đăng ký sự kiện cho từng đích theo ý muốn; cập nhật docker-compose với các tùy chọn SMTP nếu sử dụng email.

- **Đã thêm tính năng xác minh khôi phục cho các bản sao lưu, cho phép khôi phục bản sao lưu vào một cơ sở dữ liệu tạm thời, cách ly để xác thực mà không ảnh hưởng đến dữ liệu sản xuất.** ([#33](https://github.com/hoangtulangtoc/dbvault/pull/33))
  - Cách chuyển đổi: Không cần thực hiện bất kỳ hành động nào ngay lập tức khi nâng cấp. Để bắt đầu sử dụng tính năng này, hãy đặt DBVAULT_VERIFICATION_ENABLED=true và đảm bảo có thể truy cập được socket Docker, sau đó tùy chọn cập nhật các kênh thông báo. Theo mặc định, các đích cơ sở dữ liệu hoặc các bản sao lưu hiện có sẽ không bị ảnh hưởng.

- **Một lịch trực ca đã được thiết lập để nhận cảnh báo về các bản sao lưu theo lịch trình bị thất bại, với thông báo qua Slack, nâng cấp cảnh báo sau 30 phút và danh sách kiểm tra phản hồi ban đầu.** ([OPS-10](https://linear.app/dbvault/issue/OPS-10/set-up-an-on-call-rotation-for-backup-failures))

- **Một dashboard Grafana đã được tạo ra để hiển thị các chỉ số về các tác vụ sao lưu, chẳng hạn như thời lượng và kích thước sao lưu cho mỗi đích, các lỗi gần đây và thời gian kể từ lần sao lưu thành công gần nhất.** ([OPS-11](https://linear.app/dbvault/issue/OPS-11/build-the-grafana-dashboard-for-backup-jobs))

## 🐛 Sửa lỗi

- **Các lịch trình Cron liên quan đến thay đổi giờ mùa hè hiện chạy như mong đợi, thay vì bị bỏ qua hoặc lặp lại. Một WallClockCronTrigger mới thay thế trình kích hoạt cron của Quartz để lập lịch, phù hợp với thời gian đồng hồ treo tường trong múi giờ đã chọn. Các thời điểm không tồn tại do sự thay đổi giờ mùa xuân hiện được lên lịch chính xác muộn hơn, và các thời điểm lặp lại vào mùa thu chỉ được kích hoạt một lần khi xuất hiện lần đầu tiên.** ([#41](https://github.com/hoangtulangtoc/dbvault/pull/41))

## 📚 Tài liệu

- **Một hướng dẫn từng bước để thực hiện bài tập khôi phục hàng tháng đã được thêm vào.** ([OPS-9](https://linear.app/dbvault/issue/OPS-9/write-the-restore-drill-runbook))

## 🔧 Bảo trì

- **Một bài kiểm tra mới đã được thêm vào, sử dụng một fixture được tạo sẵn để mô phỏng lịch sử sao lưu dài với 5.000 mục, bao gồm cả các lần sao lưu thành công và thất bại, nhằm xác minh rằng đầu ra dòng lệnh xử lý các lịch sử lớn một cách chính xác.** ([!6](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/6))

- **Các bản cập nhật phiên bản Dependabot đã được kích hoạt để tự động đề xuất cập nhật cho các phụ thuộc Maven và GitHub Actions mỗi tuần một lần, thông qua các pull requests nhắm vào nhánh 'develop'.** ([#36](https://github.com/hoangtulangtoc/dbvault/pull/36))

- **Thư viện phụ thuộc org.springframework.boot:spring-boot-starter-parent đã được cập nhật từ phiên bản 4.1.0 lên 4.1.1.** ([#37](https://github.com/hoangtulangtoc/dbvault/pull/37))
  - Cách chuyển đổi: Nếu bạn sử dụng plugin Spring Boot Gradle với Protobuf và gRPC, hãy kiểm tra lại cấu hình xây dựng của bạn vì cấu hình gRPC tự động hiện yêu cầu phải chọn tham gia rõ ràng. Nếu không, không cần thực hiện bất kỳ bước migration nào.
