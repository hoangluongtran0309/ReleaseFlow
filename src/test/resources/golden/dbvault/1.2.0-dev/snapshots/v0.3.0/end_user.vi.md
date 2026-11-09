# Bản phát hành v0.3.0

Sao lưu MongoDB, thông báo và xác minh khôi phục.

## Có gì mới

Bản phát hành này gồm 13 thay đổi: 3 thay đổi phá vỡ, 5 tính năng mới, 1 lỗi được sửa, 1 cập nhật tài liệu và 3 thay đổi bảo trì.

> ⚠️ 3 thay đổi phá vỡ cần được xử lý trước khi nâng cấp.

## ⚠️ Thay đổi phá vỡ

- **Một lệnh 'verify' mới đã được thêm vào CLI, cho phép người dùng bắt đầu và theo dõi quá trình xác minh khôi phục cho một bản sao lưu. Tất cả các lệnh hiện nay đều sử dụng endpoint API có phiên bản (/api/v1), ngừng hỗ trợ các đường dẫn không có phiên bản và các API máy chủ cũ hơn (0.2).** — Bây giờ bạn có thể yêu cầu dbvault kiểm tra xem bản sao lưu có thực sự khôi phục được hay không. Lệnh mới này sẽ thông báo cho bạn biết quá trình xác minh đã thành công, thất bại hay hết thời gian chờ, và yêu cầu cả công cụ lẫn máy chủ phải là phiên bản mới nhất.

- **Các endpoint API HTTP hiện đã được phân phiên bản dưới /api/v1. Tất cả các đường dẫn /api/targets... trước đây hiện sẽ trả về phản hồi chuyển hướng 308 đến các endpoint tương đương mới /api/v1... và đã bị loại bỏ. Một endpoint mới để bắt đầu và theo dõi quá trình xác minh khôi phục bản sao lưu đã được thêm vào, và tài liệu API đã được cập nhật. Tính năng bảo vệ CSRF đã bị loại bỏ khỏi API (nhưng không phải trên console).** — Việc sử dụng API hiện yêu cầu một địa chỉ khác (/api/v1), và có một tính năng mới để kiểm tra bản sao lưu tự động; các công cụ cũ có thể cần được cập nhật để tiếp tục hoạt động.

- **Các endpoint '/api/targets' và '/api/targets/{id}/backups' cũ không có phiên bản đã bị xóa. Bộ điều khiển chuyển hướng liên quan và bài kiểm tra của nó cũng đã bị xóa. Tài liệu đã được cập nhật để phản ánh việc xóa bỏ thay vì chuyển hướng.** — Bất kỳ công cụ hoặc tập lệnh nào sử dụng các đường dẫn API cũ sẽ không còn hoạt động nữa; chúng sẽ cần sử dụng các đường dẫn cập nhật bắt đầu bằng '/api/v1'.

## ✨ Tính năng mới

- **Đã thêm hỗ trợ sao lưu và khôi phục MongoDB như một engine thứ ba, sử dụng mongodump/mongorestore, đồng thời cập nhật xử lý xác thực/cơ sở dữ liệu, định dạng artifact và các yêu cầu về CLI/CI.** — Giờ đây, bạn có thể sao lưu và khôi phục cơ sở dữ liệu MongoDB bằng dbvault, giống như cách bạn đã làm với MySQL và PostgreSQL.

- **Đã thêm các kênh thông báo (Slack, email, webhook chung) để cảnh báo người vận hành về các sự kiện sao lưu và khôi phục, kèm theo các trang quản lý mới và tính năng đăng ký theo từng đích.** — Giờ đây, bạn có thể nhận thông báo qua email, Slack hoặc các dịch vụ khác khi quá trình sao lưu hoặc khôi phục bắt đầu, thành công hoặc thất bại, do đó bạn không cần phải theo dõi dashboard để cập nhật thông tin.

- **Đã thêm tính năng xác minh khôi phục cho các bản sao lưu, cho phép khôi phục bản sao lưu vào một cơ sở dữ liệu tạm thời, cách ly để xác thực mà không ảnh hưởng đến dữ liệu sản xuất.** — Giờ đây, bạn có thể kiểm tra bất kỳ bản sao lưu nào bằng cách khôi phục nó vào một môi trường tạm thời, an toàn để xác nhận rằng nó thực sự hoạt động — không có rủi ro nào đối với dữ liệu thực của bạn. Tùy chọn này bị tắt trừ khi bạn chọn bật nó lên.

- **Một lịch trực ca đã được thiết lập để nhận cảnh báo về các bản sao lưu theo lịch trình bị thất bại, với thông báo qua Slack, nâng cấp cảnh báo sau 30 phút và danh sách kiểm tra phản hồi ban đầu.** — Bạn có thể nhận thấy các vấn đề liên quan đến sao lưu được giải quyết nhanh hơn, nhưng không có thay đổi trực tiếp nào đối với cách bạn sử dụng sản phẩm.

- **Một dashboard Grafana đã được tạo ra để hiển thị các chỉ số về các tác vụ sao lưu, chẳng hạn như thời lượng và kích thước sao lưu cho mỗi đích, các lỗi gần đây và thời gian kể từ lần sao lưu thành công gần nhất.** — Giờ đây, bạn có thể xem thêm chi tiết về các bản sao lưu của mình — chẳng hạn như thời gian thực hiện, dung lượng và các lỗi gần đây — trong một dashboard mới.

## 🐛 Sửa lỗi

- **Các lịch trình Cron liên quan đến thay đổi giờ mùa hè hiện chạy như mong đợi, thay vì bị bỏ qua hoặc lặp lại. Một WallClockCronTrigger mới thay thế trình kích hoạt cron của Quartz để lập lịch, phù hợp với thời gian đồng hồ treo tường trong múi giờ đã chọn. Các thời điểm không tồn tại do sự thay đổi giờ mùa xuân hiện được lên lịch chính xác muộn hơn, và các thời điểm lặp lại vào mùa thu chỉ được kích hoạt một lần khi xuất hiện lần đầu tiên.** — Các tác vụ được lên lịch sẽ không còn bị bỏ sót hoặc lặp lại vào thời điểm bắt đầu và kết thúc giờ tiết kiệm ánh sáng ban ngày; mỗi tác vụ hiện được thực thi một lần mỗi ngày như dự kiến.

## 📚 Tài liệu

- **Một hướng dẫn từng bước để thực hiện bài tập khôi phục hàng tháng đã được thêm vào.** — Bạn có thể không nhận thấy bất kỳ thay đổi nào, nhưng bản cập nhật này giúp đảm bảo các quy trình khôi phục sao lưu được kiểm tra thường xuyên và hoạt động đáng tin cậy.

## 🔧 Bảo trì

- **Một bài kiểm tra mới đã được thêm vào, sử dụng một fixture được tạo sẵn để mô phỏng lịch sử sao lưu dài với 5.000 mục, bao gồm cả các lần sao lưu thành công và thất bại, nhằm xác minh rằng đầu ra dòng lệnh xử lý các lịch sử lớn một cách chính xác.** — Bạn sẽ không nhận thấy bất kỳ thay đổi nào, vì bản cập nhật này chỉ bổ sung một bài kiểm tra phía máy chủ để đảm bảo lệnh hoạt động như mong đợi khi xử lý lượng lớn dữ liệu sao lưu.

- **Các bản cập nhật phiên bản Dependabot đã được kích hoạt để tự động đề xuất cập nhật cho các phụ thuộc Maven và GitHub Actions mỗi tuần một lần, thông qua các pull requests nhắm vào nhánh 'develop'.** — Bạn sẽ không nhận thấy bất kỳ sự khác biệt nào khi sử dụng dbvault; thay đổi này chỉ ảnh hưởng đến công tác bảo trì phía sau hậu trường.

- **Thư viện phụ thuộc org.springframework.boot:spring-boot-starter-parent đã được cập nhật từ phiên bản 4.1.0 lên 4.1.1.** — Bạn sẽ không nhận thấy bất kỳ thay đổi nào trong quá trình sử dụng hàng ngày sau bản cập nhật này.
