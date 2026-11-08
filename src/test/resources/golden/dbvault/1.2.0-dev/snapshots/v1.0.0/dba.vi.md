# Bản phát hành v1.0.0

dbvault 1.0: số liệu sao lưu cho Prometheus và Grafana, yêu cầu TLS cho mọi đích cơ sở dữ liệu, giao diện người dùng tiếng Việt và đầu ra JSON trong CLI.

## Có gì mới

Bản phát hành này gồm 7 thay đổi: 1 thay đổi phá vỡ, 3 tính năng mới, 1 cập nhật tài liệu và 2 thay đổi bảo trì.

> ⚠️ 1 thay đổi phá vỡ cần được xử lý trước khi nâng cấp: [đọc hướng dẫn nâng cấp](https://github.com/hoangtulangtoc/dbvault/blob/v1.0.0/docs/upgrading-to-1.0.md).

## ⚠️ Thay đổi phá vỡ

- 🔒 Bảo mật: **Các kết nối đến đích cơ sở dữ liệu hiện yêu cầu mã hóa TLS theo mặc định, với tùy chọn từ chối rõ ràng cho từng đích. Các đích hiện có sẽ không thể kết nối nếu máy chủ của chúng không hỗ trợ TLS sau khi nâng cấp, trừ khi được cấu hình rõ ràng khác đi. API, cấu hình, logic migration, phạm vi kiểm thử và tài liệu đã được cập nhật để phản ánh và áp dụng yêu cầu mặc định mới này.** ([#44](https://github.com/hoangtulangtoc/dbvault/pull/44)) — Sau bản cập nhật này, mọi đích cơ sở dữ liệu giờ đây đều yêu cầu kết nối được mã hóa TLS theo mặc định. Hãy xác minh rằng các đích MySQL, PostgreSQL và MongoDB của bạn hỗ trợ TLS — nếu không, bạn phải chủ động tắt tính năng này trên từng đích không hỗ trợ TLS, nếu không quá trình sao lưu và kiểm tra kết nối sẽ thất bại. Hãy kiểm tra Console để xem có cảnh báo nào về các đích được đánh dấu là 'Không yêu cầu TLS' hay không, và rà soát cấu hình máy chủ của bạn để đảm bảo tuân thủ.
  - Cách chuyển đổi: Sau khi nâng cấp, các quản trị viên cơ sở dữ liệu (DBA) phải xác minh từng đích: các máy chủ phải hỗ trợ TLS hoặc mỗi đích bị ảnh hưởng phải từ chối bằng cách bỏ chọn 'Yêu cầu TLS'. Các đích không thể thiết lập TLS sẽ không vượt qua các bài kiểm tra kết nối và sao lưu cho đến khi vấn đề được giải quyết.
  - Theo dõi tại [DBV-33](https://hoangluongtran0309.atlassian.net/browse/DBV-33): Enforce TLS to database targets (Done)

## ✨ Tính năng mới

- **Tất cả các lệnh CLI hiện đều hỗ trợ chế độ xuất JSON bằng cách sử dụng cờ '--output json' hoặc '-o json', giúp trả về các phản hồi JSON tương đương với API. Tùy chọn này đã được ghi trong tệp README và năm bài kiểm tra bổ sung đã được thêm vào.** ([!7](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/7)) — Tất cả các lệnh dbvault CLI hiện đều hỗ trợ chế độ xuất ra JSON, giúp hỗ trợ tự động hóa và tích hợp; điều này không thay đổi bất kỳ lược đồ hay dữ liệu đã lưu trữ nào, và không cần các bước migration.
  - Theo dõi tại [DBV-34](https://hoangluongtran0309.atlassian.net/browse/DBV-34): CLI: JSON output for every command (Done)

- **Điểm endpoint chỉ số Prometheus (/actuator/prometheus) đã được thêm vào, hiển thị các chỉ số sao lưu chi tiết cho từng đích, cùng với một cấu hình Docker Compose tùy chọn cho Prometheus và Grafana kèm theo dashboard được tạo sẵn để theo dõi quá trình sao lưu.** ([#43](https://github.com/hoangtulangtoc/dbvault/pull/43)) — Giờ đây, bạn có thể theo dõi trạng thái sao lưu, thời lượng và các lỗi cho tất cả các đích cơ sở dữ liệu (bao gồm MySQL và PostgreSQL) bằng Prometheus và Grafana mà không cần thực hiện bất kỳ thao tác nào, trừ khi bạn chọn tham gia cấu hình khả năng quan sát của Compose; trong trường hợp đó, bạn phải đặt mật khẩu trong hai tệp. Cấu trúc cơ sở dữ liệu và dữ liệu lưu trữ không thay đổi, và bạn có thể khôi phục lại trạng thái ban đầu bằng cách vô hiệu hóa cấu hình khả năng quan sát.
  - Theo dõi tại [DBV-30](https://hoangluongtran0309.atlassian.net/browse/DBV-30): Prometheus metrics and a Grafana dashboard (Done)

- **Console hiện có thể hiển thị bằng tiếng Anh hoặc tiếng Việt, tùy chọn theo từng trình duyệt (qua liên kết ở thanh bên và trên trang đăng nhập, hoặc `?lang=`), và được lưu trong cookie. Phần đầu tiên này dịch các phần điều hướng, đăng nhập, danh sách và biểu mẫu Đích, cũng như danh sách Đích sao lưu; các trang khác vẫn hiển thị bằng tiếng Anh.** ([#45](https://github.com/hoangtulangtoc/dbvault/pull/45)) — Không có thay đổi nào ảnh hưởng đến dữ liệu đã lưu trữ hoặc thông tin đăng nhập. Ngôn ngữ giao diện người dùng có thể được chuyển đổi giữa tiếng Anh và tiếng Việt trên từng trình duyệt bằng cách sử dụng tham số URL hoặc Accept-Language; thay đổi này không ảnh hưởng đến cơ sở dữ liệu và không yêu cầu thực hiện bất kỳ hành động nào sau khi nâng cấp. Việc khôi phục lại trạng thái cũ là an toàn và không ảnh hưởng đến cấu trúc cơ sở dữ liệu hay các quy trình khôi phục.
  - Theo dõi tại [DBV-31](https://hoangluongtran0309.atlassian.net/browse/DBV-31): Vietnamese console

## 📚 Tài liệu

- **Đã thêm hướng dẫn nâng cấp ghi lại các bước cần thiết để nâng cấp lên phiên bản 1.0, bao gồm yêu cầu mới về TLS trên mọi đích và các hướng dẫn vận hành cũng như khôi phục.** ([#47](https://github.com/hoangtulangtoc/dbvault/pull/47)) — Tài liệu mới giải thích cách sao lưu cơ sở dữ liệu siêu dữ liệu, kiểm tra hỗ trợ TLS theo từng engine cơ sở dữ liệu (MySQL, PostgreSQL, v.v.), quản lý quá trình migration, xác thực các đích sau khi nâng cấp, loại trừ các máy chủ không hỗ trợ TLS và khôi phục từ bản sao lưu siêu dữ liệu nếu cần thiết.

## 🔧 Bảo trì

- **Một workflow được kích hoạt thủ công đã được thêm vào để thông báo và ký xác nhận một bản phát hành ReleaseFlow đã được xuất bản trong CI.** ([#42](https://github.com/hoangtulangtoc/dbvault/pull/42)) — Điều này không ảnh hưởng đến dữ liệu đã lưu trữ, cấu trúc cơ sở dữ liệu, thông tin đăng nhập hoặc các thao tác khôi phục đối với bất kỳ hệ quản trị cơ sở dữ liệu (DBMS) nào được hỗ trợ. Không cần thực hiện bất kỳ thao tác nào từ phía quản trị viên cơ sở dữ liệu (DBA).

- **Báo cáo giấy phép của bên thứ ba hiện được tạo ra trong quá trình xây dựng và được bao gồm trong tệp JAR của ứng dụng dưới tên 'THIRD-PARTY.txt'. Báo cáo này liệt kê mọi thư viện của bên thứ ba được gói kèm cùng với giấy phép đã khai báo của chúng; các phụ thuộc thiếu thông tin giấy phép sẽ khiến quá trình xây dựng thất bại. Tài liệu đã được cập nhật để giải thích về báo cáo này.** ([#46](https://github.com/hoangtulangtoc/dbvault/pull/46)) — Ứng dụng đóng gói hiện bao gồm một báo cáo toàn diện về tất cả các thư viện của bên thứ ba và giấy phép của chúng ('THIRD-PARTY.txt'). Không có thay đổi nào đối với thời gian chạy hoặc xử lý dữ liệu, và không cần thực hiện bất kỳ kiểm tra đặc biệt nào sau khi nâng cấp.

**Hướng dẫn nâng cấp:** [Đọc trước khi nâng cấp](https://github.com/hoangtulangtoc/dbvault/blob/v1.0.0/docs/upgrading-to-1.0.md)
