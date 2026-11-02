# Bản phát hành v0.3.0

Sao lưu MongoDB, thông báo và xác minh khôi phục.

## Có gì mới

Bản phát hành này gồm 13 thay đổi: 3 thay đổi phá vỡ, 5 tính năng mới, 1 lỗi được sửa, 1 cập nhật tài liệu và 3 thay đổi bảo trì.

> ⚠️ 3 thay đổi phá vỡ cần được xử lý trước khi nâng cấp.

## ⚠️ Thay đổi phá vỡ

- **Một lệnh 'verify' mới đã được thêm vào CLI, cho phép người dùng bắt đầu và theo dõi quá trình xác minh khôi phục cho một bản sao lưu. Tất cả các lệnh hiện nay đều sử dụng endpoint API có phiên bản (/api/v1), ngừng hỗ trợ các đường dẫn không có phiên bản và các API máy chủ cũ hơn (0.2).** ([!5](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/5)) — Lệnh 'verify' cho phép bạn chạy và chờ kết quả xác minh khôi phục phía máy chủ từ CLI, phản ánh kết quả thông qua exit codes. Sau khi nâng cấp cả máy chủ và CLI lên phiên bản 0.3 trở lên, hãy đảm bảo rằng bạn không sử dụng các máy chủ cũ hơn (0.2) vì chúng không còn tương thích nữa.
  - Cách chuyển đổi: Hãy cập nhật cả CLI và máy chủ lên phiên bản 0.3 hoặc mới hơn trước khi sử dụng. Điều chỉnh các tập lệnh hoặc tích hợp để thay thế các đường dẫn API không có phiên bản bằng '/api/v1'. Kiểm tra để đảm bảo rằng các máy chủ cũ (0.2) không còn được sử dụng nữa.
  - Theo dõi tại [DBV-26](https://hoangluongtran0309.atlassian.net/browse/DBV-26): CLI: verify command (In Progress)

- **Các endpoint API HTTP hiện đã được phân phiên bản dưới /api/v1. Tất cả các đường dẫn /api/targets... trước đây hiện sẽ trả về phản hồi chuyển hướng 308 đến các endpoint tương đương mới /api/v1... và đã bị loại bỏ. Một endpoint mới để bắt đầu và theo dõi quá trình xác minh khôi phục bản sao lưu đã được thêm vào, và tài liệu API đã được cập nhật. Tính năng bảo vệ CSRF đã bị loại bỏ khỏi API (nhưng không phải trên console).** ([#34](https://github.com/hoangtulangtoc/dbvault/pull/34)) — Tính năng xác minh bản sao lưu cơ sở dữ liệu hiện đã có sẵn và các endpoint API đã được chuyển sang /api/v1, do đó các tập lệnh truy cập API trực tiếp phải được cập nhật để tuân theo các đường dẫn phiên bản mới; hãy đảm bảo thực hiện migration cho mọi quy trình tự động hóa và lưu ý rằng việc xác minh thủ công qua API cũng có thể thực hiện được.
  - Cách chuyển đổi: Cập nhật tất cả các tập lệnh, tích hợp và máy khách để gọi trực tiếp các endpoint /api/v1; đảm bảo tính năng theo dõi chuyển hướng đã được bật hoặc cập nhật lên phiên bản máy khách tương thích trước khi nâng cấp.
  - Theo dõi tại [DBV-24](https://hoangluongtran0309.atlassian.net/browse/DBV-24): Version the HTTP API under /api/v1 (In Progress)
  - Theo dõi tại [DBV-26](https://hoangluongtran0309.atlassian.net/browse/DBV-26): CLI: verify command (Done)

- **Các endpoint '/api/targets' và '/api/targets/{id}/backups' cũ không có phiên bản đã bị xóa. Bộ điều khiển chuyển hướng liên quan và bài kiểm tra của nó cũng đã bị xóa. Tài liệu đã được cập nhật để phản ánh việc xóa bỏ thay vì chuyển hướng.** ([#35](https://github.com/hoangtulangtoc/dbvault/pull/35)) — Sau khi nâng cấp, bất kỳ tập lệnh hoặc công cụ nào sử dụng các endpoint '/api/targets' cũ sẽ báo lỗi 404 và cần chuyển sang '/api/v1/targets'. Hãy kiểm tra xem các tập lệnh và quy trình tự động hóa của máy khách cơ sở dữ liệu đã được cập nhật trước khi triển khai để tránh gián đoạn; không có cơ chế nào để quay lại các endpoint cũ.
  - Cách chuyển đổi: Cập nhật tất cả các client hoặc skript để sử dụng các endpoint '/api/v1/...' thay vì các đường dẫn cũ '/api/...'. Sau khi thay đổi này, sẽ không thể quay lại các đường dẫn không có phiên bản.
  - Theo dõi tại [DBV-24](https://hoangluongtran0309.atlassian.net/browse/DBV-24): Version the HTTP API under /api/v1 (In Progress)

## ✨ Tính năng mới

- **Đã thêm hỗ trợ sao lưu và khôi phục MongoDB như một engine thứ ba, sử dụng mongodump/mongorestore, đồng thời cập nhật xử lý xác thực/cơ sở dữ liệu, định dạng artifact và các yêu cầu về CLI/CI.** ([#31](https://github.com/hoangtulangtoc/dbvault/pull/31)) — Giờ đây, bạn có thể thêm cơ sở dữ liệu MongoDB làm đích sao lưu/khôi phục. Hãy cấu hình 'cơ sở dữ liệu xác thực' (mặc định là 'admin') trong quá trình thiết lập. Các đích SQL hiện có không bị ảnh hưởng. Sau khi nâng cấp, hãy xác nhận tính khả dụng của mongodump/mongorestore và xem lại tên các artifact sao lưu mới (.archive.gz). Để khôi phục lại phiên bản trước, cần xóa các đích MongoDB hoặc bỏ qua cột bổ sung.
  - Cách chuyển đổi: Áp dụng bản cập nhật V11 để thêm cột cơ sở dữ liệu xác thực. Không cần thay đổi gì trừ khi sử dụng các đích cơ sở dữ liệu MongoDB.
  - Theo dõi tại [DBV-21](https://hoangluongtran0309.atlassian.net/browse/DBV-21): MongoDB logical backup and restore (In Progress)

- **Đã thêm các kênh thông báo (Slack, email, webhook chung) để cảnh báo người vận hành về các sự kiện sao lưu và khôi phục, kèm theo các trang quản lý mới và tính năng đăng ký theo từng đích.** ([#32](https://github.com/hoangtulangtoc/dbvault/pull/32)) — Các sự kiện thông báo không ảnh hưởng trực tiếp đến cấu trúc cơ sở dữ liệu hoặc dữ liệu được lưu trữ bên ngoài các bảng kênh thông báo/đăng ký mới (migration V12). Sau khi nâng cấp, hãy kiểm tra xem các bảng mới có tồn tại hay không; việc xóa một đích sẽ loại bỏ các đăng ký của nó, trong khi các kênh đang được sử dụng không thể bị xóa. Thông tin đăng nhập SMTP cho email cần được cấu hình trong quá trình triển khai khi cần thiết; việc khôi phục lại chỉ đơn giản là loại bỏ chức năng thông báo.
  - Cách chuyển đổi: Sau khi nâng cấp, hãy xem xét và cấu hình các kênh thông báo cũng như đăng ký sự kiện cho từng đích theo ý muốn; cập nhật docker-compose với các tùy chọn SMTP nếu sử dụng email.
  - Theo dõi tại [DBV-22](https://hoangluongtran0309.atlassian.net/browse/DBV-22): Notify operators through Slack, email and webhooks (In Progress)

- **Đã thêm tính năng xác minh khôi phục cho các bản sao lưu, cho phép khôi phục bản sao lưu vào một cơ sở dữ liệu tạm thời, cách ly để xác thực mà không ảnh hưởng đến dữ liệu sản xuất.** ([#33](https://github.com/hoangtulangtoc/dbvault/pull/33)) — Giờ đây, các bản sao lưu có thể được xác minh bằng cách khôi phục chúng vào một container cơ sở dữ liệu tạm thời (MySQL 8.4, PostgreSQL 17-alpine, MongoDB 8.0), giúp xác nhận rằng dữ liệu thực sự có thể khôi phục được mà không cần can thiệp vào môi trường sản xuất hoặc các đích hiện có. Sau khi nâng cấp, mọi thứ sẽ không thay đổi cho đến khi bạn kích hoạt tính năng này (DBVAULT_VERIFICATION_ENABLED=true) và đảm bảo quyền truy cập vào socket Docker; để quay lại phiên bản trước, chỉ cần tắt cờ này.
  - Cách chuyển đổi: Không cần thực hiện bất kỳ hành động nào ngay lập tức khi nâng cấp. Để bắt đầu sử dụng tính năng này, hãy đặt DBVAULT_VERIFICATION_ENABLED=true và đảm bảo có thể truy cập được socket Docker, sau đó tùy chọn cập nhật các kênh thông báo. Theo mặc định, các đích cơ sở dữ liệu hoặc các bản sao lưu hiện có sẽ không bị ảnh hưởng.
  - Theo dõi tại [DBV-23](https://hoangluongtran0309.atlassian.net/browse/DBV-23): Isolated restore verification (In Progress)

- **Một lịch trực ca đã được thiết lập để nhận cảnh báo về các bản sao lưu theo lịch trình bị thất bại, với thông báo qua Slack, nâng cấp cảnh báo sau 30 phút và danh sách kiểm tra phản hồi ban đầu.** ([OPS-10](https://linear.app/dbvault/issue/OPS-10/set-up-an-on-call-rotation-for-backup-failures)) — Nếu một bản sao lưu theo lịch trình thất bại, hệ thống cảnh báo sẽ gửi thông báo ngay lập tức đến kỹ sư trực ca trên Slack và nâng cấp mức độ cảnh báo sau 30 phút, giúp đảm bảo phản hồi kịp thời; không có thay đổi nào ảnh hưởng đến dữ liệu đã lưu trữ, cấu trúc cơ sở dữ liệu hoặc hành vi khôi phục/hủy bỏ.

- **Một dashboard Grafana đã được tạo ra để hiển thị các chỉ số về các tác vụ sao lưu, chẳng hạn như thời lượng và kích thước sao lưu cho mỗi đích, các lỗi gần đây và thời gian kể từ lần sao lưu thành công gần nhất.** ([OPS-11](https://linear.app/dbvault/issue/OPS-11/build-the-grafana-dashboard-for-backup-jobs)) — Bây giờ bạn đã có một dashboard Grafana hiển thị thời lượng sao lưu, dung lượng, các lỗi trong 24 giờ qua và thời gian kể từ lần sao lưu thành công gần nhất cho từng đích sao lưu. Không cần thực hiện bất kỳ thay đổi nào đối với cơ sở dữ liệu của bạn. Hãy xác nhận rằng các chỉ số của bạn được truyền đến Grafana như mong đợi sau khi nâng cấp.

## 🐛 Sửa lỗi

- **Các lịch trình Cron liên quan đến thay đổi giờ mùa hè hiện chạy như mong đợi, thay vì bị bỏ qua hoặc lặp lại. Một WallClockCronTrigger mới thay thế trình kích hoạt cron của Quartz để lập lịch, phù hợp với thời gian đồng hồ treo tường trong múi giờ đã chọn. Các thời điểm không tồn tại do sự thay đổi giờ mùa xuân hiện được lên lịch chính xác muộn hơn, và các thời điểm lặp lại vào mùa thu chỉ được kích hoạt một lần khi xuất hiện lần đầu tiên.** ([#41](https://github.com/hoangtulangtoc/dbvault/pull/41)) — Các tác vụ được lên lịch hiện chạy ổn định một lần mỗi ngày ngay cả khi có sự thay đổi giờ mùa hè. Ví dụ: một tác vụ được đặt vào lúc 02:30 theo múi giờ America/New_York sẽ được kích hoạt vào lúc 03:30 vào ngày chuyển sang giờ mùa xuân, đảm bảo không bỏ sót hoặc lặp lại các lần chạy. Không có thay đổi nào đối với lược đồ hoặc dữ liệu được lưu trữ, và không cần thực hiện các bước nâng cấp đặc biệt.
  - Theo dõi tại [DBV-25](https://hoangluongtran0309.atlassian.net/browse/DBV-25): Schedules skip or repeat runs at daylight saving changes (In Progress)

## 📚 Tài liệu

- **Một hướng dẫn từng bước để thực hiện bài tập khôi phục hàng tháng đã được thêm vào.** ([OPS-9](https://linear.app/dbvault/issue/OPS-9/write-the-restore-drill-runbook)) — Hiện đã có quy trình được tài liệu hóa cho các bài kiểm tra khôi phục hàng tháng: khôi phục bản sao lưu đã chọn vào một môi trường cách ly, so sánh số lượng hàng và tổng kiểm tra, và ghi lại kết quả. Hãy xem lại tài liệu hướng dẫn để điều chỉnh các quy trình xác thực bản sao lưu của bạn.

## 🔧 Bảo trì

- **Một bài kiểm tra mới đã được thêm vào, sử dụng một fixture được tạo sẵn để mô phỏng lịch sử sao lưu dài với 5.000 mục, bao gồm cả các lần sao lưu thành công và thất bại, nhằm xác minh rằng đầu ra dòng lệnh xử lý các lịch sử lớn một cách chính xác.** ([!6](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/6)) — Không có thay đổi nào ảnh hưởng đến các bản sao lưu đã lưu trữ thực tế hoặc cách quản lý lịch sử sao lưu. Bản cập nhật này chỉ thêm một bài kiểm tra để xác minh cách CLI hiển thị lịch sử sao lưu, do đó bạn không cần thực hiện bất kỳ thao tác nào sau khi nâng cấp.

- **Các bản cập nhật phiên bản Dependabot đã được kích hoạt để tự động đề xuất cập nhật cho các phụ thuộc Maven và GitHub Actions mỗi tuần một lần, thông qua các pull requests nhắm vào nhánh 'develop'.** ([#36](https://github.com/hoangtulangtoc/dbvault/pull/36)) — Không có lược đồ cơ sở dữ liệu, thông tin đăng nhập hoặc dữ liệu được lưu trữ nào bị ảnh hưởng bởi thay đổi này. Các bản cập nhật phụ thuộc định kỳ hiện đã được tự động hóa; không có thay đổi nào ảnh hưởng đến hoạt động cơ sở dữ liệu hoặc quy trình khôi phục.

- **Thư viện phụ thuộc org.springframework.boot:spring-boot-starter-parent đã được cập nhật từ phiên bản 4.1.0 lên 4.1.1.** ([#37](https://github.com/hoangtulangtoc/dbvault/pull/37)) — Bản cập nhật này của phụ thuộc Spring Boot cấp trên không gây ra bất kỳ thay đổi nào đối với cấu trúc cơ sở dữ liệu, cách xử lý dữ liệu hoặc thông tin đăng nhập.
  - Cách chuyển đổi: Nếu bạn sử dụng plugin Spring Boot Gradle với Protobuf và gRPC, hãy kiểm tra lại cấu hình xây dựng của bạn vì cấu hình gRPC tự động hiện yêu cầu phải chọn tham gia rõ ràng. Nếu không, không cần thực hiện bất kỳ bước migration nào.
