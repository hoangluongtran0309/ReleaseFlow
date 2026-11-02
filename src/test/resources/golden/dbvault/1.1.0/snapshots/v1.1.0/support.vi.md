# Bản phát hành v1.1.0

Toàn bộ giao diện điều khiển bằng tiếng Việt, thông báo lỗi rõ ràng trên CLI khi nhập sai địa chỉ máy chủ, và các container images được cố định bằng digest.

## Có gì mới

Bản phát hành này gồm 3 thay đổi: 1 tính năng mới và 2 lỗi được sửa.

## ✨ Tính năng mới

- **Giao diện console hiện hỗ trợ đầy đủ cả tiếng Anh và tiếng Việt trên tất cả các trang và thông báo, với tùy chọn ngôn ngữ được áp dụng cho từng phiên trình duyệt.** ([#53](https://github.com/hoangtulangtoc/dbvault/pull/53))

## 🐛 Sửa lỗi

- **CLI hiện từ chối các địa chỉ máy chủ không phải là URL http(s) hợp lệ, hiển thị một thông báo rõ ràng và exit code 2 thay vì lỗi nội bộ.** ([!9](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/9))
  - Cách chuyển đổi: Đảm bảo tất cả các biến cấu hình và biến môi trường đều sử dụng địa chỉ máy chủ đầy đủ, bao gồm giao thức http(s) và tên máy chủ, để tránh các lỗi khởi động.

- **Tất cả các base images của container trong Dockerfiles và docker-compose.yml hiện đã được cố định bằng digests, và cấu hình Dependabot đã được cập nhật để theo dõi các thay đổi đối với các digests này. Tài liệu triển khai liên quan cũng đã được cập nhật.** ([#48](https://github.com/hoangtulangtoc/dbvault/pull/48))
