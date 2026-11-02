# Bản phát hành v1.1.0

Toàn bộ giao diện điều khiển bằng tiếng Việt, thông báo lỗi rõ ràng trên CLI khi nhập sai địa chỉ máy chủ, và các container images được cố định bằng digest.

## Có gì mới

Bản phát hành này gồm 3 thay đổi: 1 tính năng mới và 2 lỗi được sửa.

## ✨ Tính năng mới

- **Giao diện console hiện hỗ trợ đầy đủ cả tiếng Anh và tiếng Việt trên tất cả các trang và thông báo, với tùy chọn ngôn ngữ được áp dụng cho từng phiên trình duyệt.** ([#53](https://github.com/hoangtulangtoc/dbvault/pull/53)) — Bản cập nhật này không ảnh hưởng đến lược đồ, dữ liệu, thông tin đăng nhập, logic sao lưu/khôi phục hoặc các engines của cơ sở dữ liệu. Ngôn ngữ của console thay đổi theo từng phiên làm việc nhưng không ảnh hưởng đến dữ liệu được lưu trữ hoặc hoạt động của cơ sở dữ liệu. Tính năng này không yêu cầu kiểm tra sau khi nâng cấp hoặc khôi phục lại hệ thống.
  - Theo dõi tại [DBV-31](https://hoangluongtran0309.atlassian.net/browse/DBV-31): Vietnamese console (Done)

## 🐛 Sửa lỗi

- **CLI hiện từ chối các địa chỉ máy chủ không phải là URL http(s) hợp lệ, hiển thị một thông báo rõ ràng và exit code 2 thay vì lỗi nội bộ.** ([!9](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/9)) — Khi nâng cấp, hãy đảm bảo rằng DBVAULT_URL và các tệp cấu hình của bạn chỉ định đầy đủ địa chỉ http:// hoặc https://. Nếu không làm như vậy, các lệnh CLI sẽ thất bại với exit code 2 và một lỗi cấu hình rõ ràng, thay vì một lỗi không rõ ràng. Dữ liệu, lược đồ hoặc thông tin đăng nhập cơ sở dữ liệu sẽ không bị ảnh hưởng; nếu cần, bạn có thể khôi phục lại bằng cách quay về cấu hình hoặc phiên bản trước đó.
  - Cách chuyển đổi: Đảm bảo tất cả các biến cấu hình và biến môi trường đều sử dụng địa chỉ máy chủ đầy đủ, bao gồm giao thức http(s) và tên máy chủ, để tránh các lỗi khởi động.
  - Theo dõi tại [DBV-44](https://hoangluongtran0309.atlassian.net/browse/DBV-44): CLI: a server address without http:// fails with an internal error (In Progress)

- **Tất cả các base images của container trong Dockerfiles và docker-compose.yml hiện đã được cố định bằng digests, và cấu hình Dependabot đã được cập nhật để theo dõi các thay đổi đối với các digests này. Tài liệu triển khai liên quan cũng đã được cập nhật.** ([#48](https://github.com/hoangtulangtoc/dbvault/pull/48)) — Thay đổi này không ảnh hưởng đến dữ liệu đã lưu trữ, cấu trúc cơ sở dữ liệu, thông tin đăng nhập hoặc các quy trình khôi phục. Các môi trường container được xây dựng một cách có thể tái tạo bằng cách sử dụng các base images được cố định, không yêu cầu kiểm tra hoặc thực hiện bất kỳ thao tác nào sau khi nâng cấp.
