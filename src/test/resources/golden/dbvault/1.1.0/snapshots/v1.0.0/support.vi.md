# Bản phát hành v1.0.0

dbvault 1.0: số liệu sao lưu cho Prometheus và Grafana, yêu cầu TLS cho mọi đích cơ sở dữ liệu, giao diện người dùng tiếng Việt và đầu ra JSON trong CLI.

## Có gì mới

Bản phát hành này gồm 7 thay đổi: 1 thay đổi phá vỡ, 3 tính năng mới, 1 cập nhật tài liệu và 2 thay đổi bảo trì.

> ⚠️ 1 thay đổi phá vỡ cần được xử lý trước khi nâng cấp: [đọc hướng dẫn nâng cấp](https://github.com/hoangtulangtoc/dbvault/blob/v1.0.0/docs/upgrading-to-1.0.md).

## ⚠️ Thay đổi phá vỡ

- 🔒 Bảo mật: **Các kết nối đến đích cơ sở dữ liệu hiện yêu cầu mã hóa TLS theo mặc định, với tùy chọn từ chối rõ ràng cho từng đích. Các đích hiện có sẽ không thể kết nối nếu máy chủ của chúng không hỗ trợ TLS sau khi nâng cấp, trừ khi được cấu hình rõ ràng khác đi. API, cấu hình, logic migration, phạm vi kiểm thử và tài liệu đã được cập nhật để phản ánh và áp dụng yêu cầu mặc định mới này.** ([#44](https://github.com/hoangtulangtoc/dbvault/pull/44))
  - Cách chuyển đổi: Sau khi nâng cấp, các quản trị viên cơ sở dữ liệu (DBA) phải xác minh từng đích: các máy chủ phải hỗ trợ TLS hoặc mỗi đích bị ảnh hưởng phải từ chối bằng cách bỏ chọn 'Yêu cầu TLS'. Các đích không thể thiết lập TLS sẽ không vượt qua các bài kiểm tra kết nối và sao lưu cho đến khi vấn đề được giải quyết.

## ✨ Tính năng mới

- **Tất cả các lệnh CLI hiện đều hỗ trợ chế độ xuất JSON bằng cách sử dụng cờ '--output json' hoặc '-o json', giúp trả về các phản hồi JSON tương đương với API. Tùy chọn này đã được ghi trong tệp README và năm bài kiểm tra bổ sung đã được thêm vào.** ([!7](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/7))

- **Điểm endpoint chỉ số Prometheus (/actuator/prometheus) đã được thêm vào, hiển thị các chỉ số sao lưu chi tiết cho từng đích, cùng với một cấu hình Docker Compose tùy chọn cho Prometheus và Grafana kèm theo dashboard được tạo sẵn để theo dõi quá trình sao lưu.** ([#43](https://github.com/hoangtulangtoc/dbvault/pull/43))

- **Console hiện có thể hiển thị bằng tiếng Anh hoặc tiếng Việt, tùy chọn theo từng trình duyệt (qua liên kết ở thanh bên và trên trang đăng nhập, hoặc `?lang=`), và được lưu trong cookie. Phần đầu tiên này dịch các phần điều hướng, đăng nhập, danh sách và biểu mẫu Đích, cũng như danh sách Đích sao lưu; các trang khác vẫn hiển thị bằng tiếng Anh.** ([#45](https://github.com/hoangtulangtoc/dbvault/pull/45))

## 📚 Tài liệu

- **Đã thêm hướng dẫn nâng cấp ghi lại các bước cần thiết để nâng cấp lên phiên bản 1.0, bao gồm yêu cầu mới về TLS trên mọi đích và các hướng dẫn vận hành cũng như khôi phục.** ([#47](https://github.com/hoangtulangtoc/dbvault/pull/47))

## 🔧 Bảo trì

- **Một workflow được kích hoạt thủ công đã được thêm vào để thông báo và ký xác nhận một bản phát hành ReleaseFlow đã được xuất bản trong CI.** ([#42](https://github.com/hoangtulangtoc/dbvault/pull/42))

- **Báo cáo giấy phép của bên thứ ba hiện được tạo ra trong quá trình xây dựng và được bao gồm trong tệp JAR của ứng dụng dưới tên 'THIRD-PARTY.txt'. Báo cáo này liệt kê mọi thư viện của bên thứ ba được gói kèm cùng với giấy phép đã khai báo của chúng; các phụ thuộc thiếu thông tin giấy phép sẽ khiến quá trình xây dựng thất bại. Tài liệu đã được cập nhật để giải thích về báo cáo này.** ([#46](https://github.com/hoangtulangtoc/dbvault/pull/46))
