# Bản phát hành v1.1.0

Toàn bộ giao diện điều khiển bằng tiếng Việt, thông báo lỗi rõ ràng trên CLI khi nhập sai địa chỉ máy chủ, và các container images được cố định bằng digest.

## Có gì mới

Bản phát hành này gồm 3 thay đổi: 1 tính năng mới và 2 lỗi được sửa.

## ✨ Tính năng mới

- **Giao diện console hiện hỗ trợ đầy đủ cả tiếng Anh và tiếng Việt trên tất cả các trang và thông báo, với tùy chọn ngôn ngữ được áp dụng cho từng phiên trình duyệt.** — Tất cả các trang và thông báo trong console hiện đã có sẵn bằng tiếng Anh hoặc tiếng Việt, và bạn có thể chọn ngôn ngữ thông qua trình duyệt của mình.

## 🐛 Sửa lỗi

- **CLI hiện từ chối các địa chỉ máy chủ không phải là URL http(s) hợp lệ, hiển thị một thông báo rõ ràng và exit code 2 thay vì lỗi nội bộ.** — Nếu bạn không bao gồm 'http://' hoặc 'https://' trong địa chỉ máy chủ, chương trình giờ đây sẽ hiển thị một thông báo lỗi rõ ràng thay vì bị treo.

- **Tất cả các base images của container trong Dockerfiles và docker-compose.yml hiện đã được cố định bằng digests, và cấu hình Dependabot đã được cập nhật để theo dõi các thay đổi đối với các digests này. Tài liệu triển khai liên quan cũng đã được cập nhật.** — Người dùng sẽ không thấy bất kỳ thay đổi nào khi sử dụng sản phẩm; mọi thứ vẫn hoạt động như trước đây.
