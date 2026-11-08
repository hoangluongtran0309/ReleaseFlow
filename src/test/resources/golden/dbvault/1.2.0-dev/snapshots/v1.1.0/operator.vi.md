# Bản phát hành v1.1.0

Toàn bộ giao diện điều khiển bằng tiếng Việt, thông báo lỗi rõ ràng trên CLI khi nhập sai địa chỉ máy chủ, và các container images được cố định bằng digest.

## Có gì mới

Bản phát hành này gồm 3 thay đổi: 1 tính năng mới và 2 lỗi được sửa.

## ✨ Tính năng mới

- **Giao diện console hiện hỗ trợ đầy đủ cả tiếng Anh và tiếng Việt trên tất cả các trang và thông báo, với tùy chọn ngôn ngữ được áp dụng cho từng phiên trình duyệt.** ([#53](https://github.com/hoangtulangtoc/dbvault/pull/53)) — Không gây ra rủi ro vận hành: thay đổi này chỉ ảnh hưởng đến ngôn ngữ giao diện, không ảnh hưởng đến dữ liệu lưu trữ hay logic phía sau. Việc hoàn nguyên cũng giống như bất kỳ bản cập nhật giao diện người dùng nào; hãy theo dõi các bản dịch bị thiếu hoặc phản hồi của người dùng về hiển thị ngôn ngữ.
  - Lý do: Để đáp ứng yêu cầu của khách hàng về hỗ trợ tiếng Việt và cung cấp trải nghiệm người dùng được bản địa hóa như được mô tả trong DBV-31.
  - Chi tiết: Tất cả văn bản hiển thị cho người dùng trên console hiện được quản lý bằng các gói ngôn ngữ và MessageFormat cho các mẫu đối số. Flash messages, kiểm tra biểu mẫu và trạng thái công việc trực tiếp được bản địa hóa dựa trên ngôn ngữ của trình duyệt. Các bài kiểm tra tự động xác minh bản dịch, định dạng thông báo và tính nhất quán của các khóa giữa các gói, trong khi tiếng Anh vẫn được giữ nguyên cho API HTTP, CLI, nhật ký và thông báo theo ADR-042.

## 🐛 Sửa lỗi

- **CLI hiện từ chối các địa chỉ máy chủ không phải là URL http(s) hợp lệ, hiển thị một thông báo rõ ràng và exit code 2 thay vì lỗi nội bộ.** ([!9](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/9)) — Theo dõi các lỗi CLI với exit code 2 và các thông báo về địa chỉ máy chủ không hợp lệ sau khi triển khai. Các dòng DBVAULT_URL hoặc máy chủ không hợp lệ hiện sẽ chặn quá trình khởi động với một lỗi hiển thị; hãy sửa cấu hình nếu cần để khôi phục dịch vụ. Không ảnh hưởng đến lược đồ hoặc dữ liệu, rủi ro khôi phục lại thấp.
  - Lý do: Trước đây, một địa chỉ máy chủ không hợp lệ (chẳng hạn như thiếu phần "scheme") sẽ gây ra lỗi nội bộ không rõ ràng và exit code 1, khiến người dùng bối rối.
  - Chi tiết: Quá trình phân tích cú pháp hiện yêu cầu các địa chỉ máy chủ (từ DBVAULT_URL hoặc server= trong cấu hình CLI) phải bắt đầu bằng http:// hoặc https:// và bao gồm tên máy chủ. Các định dạng không hợp lệ sẽ gây ra exit code 2 và một thông báo lỗi cụ thể; tài liệu README mô tả hành vi này. ServerCommandsTest bao gồm các trường hợp này với các bài kiểm tra mới.
  - Cách chuyển đổi: Đảm bảo tất cả các biến cấu hình và biến môi trường đều sử dụng địa chỉ máy chủ đầy đủ, bao gồm giao thức http(s) và tên máy chủ, để tránh các lỗi khởi động.

- **Tất cả các base images của container trong Dockerfiles và docker-compose.yml hiện đã được cố định bằng digests, và cấu hình Dependabot đã được cập nhật để theo dõi các thay đổi đối với các digests này. Tài liệu triển khai liên quan cũng đã được cập nhật.** ([#48](https://github.com/hoangtulangtoc/dbvault/pull/48)) — Về mặt vận hành, các base images hiện đã được khóa với các digests cụ thể, ngăn chặn những thay đổi bất ngờ trong quá trình xây dựng lại và giúp việc triển khai trở nên dễ dự đoán hơn. Các quy trình khôi phục vẫn không thay đổi; hãy theo dõi các PRs của Dependabot để cập nhật image.
  - Lý do: Để đảm bảo các lần xây dựng lại luôn sử dụng chính xác cùng một base image trừ khi được cập nhật có chủ đích, từ đó nâng cao khả năng tái tạo và bảo mật.
  - Chi tiết: Các tệp Dockerfile chỉ định digest chỉ mục nền tảng cho từng nền tảng cơ sở, bao gồm Maven, CLI Docker, Eclipse Temurin và Postgres; Dependabot được cấu hình để tạo các PRs khi có digest mới.
