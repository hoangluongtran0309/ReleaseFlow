# Bản phát hành v0.2.0

## Có gì mới

Bản phát hành này gồm 25 thay đổi: 1 thay đổi phá vỡ, 3 thay đổi bảo mật, 11 tính năng mới, 3 lỗi được sửa, 1 cải thiện hiệu năng, 2 cập nhật tài liệu và 4 thay đổi bảo trì.

> ⚠️ 1 thay đổi phá vỡ cần được xử lý trước khi nâng cấp.

## ⚠️ Thay đổi phá vỡ

- **CLI hiện yêu cầu ID đích sao lưu phải được cung cấp thông qua tùy chọn --target-id thay vì dưới dạng đối số vị trí.** ([!3](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/3))
  - Cách chuyển đổi: Hãy cập nhật các tập lệnh hoặc lệnh thủ công để sử dụng --target-id \<id> thay vì truyền ID đích theo vị trí.
  - Theo dõi tại [DBV-17](https://hoangluongtran0309.atlassian.net/browse/DBV-17): CLI: rename --target to --target-id (Done)

## 🔒 Bảo mật

- **Việc mã hóa mật khẩu của từng đích hiện sử dụng một khóa duy nhất được dẫn xuất từ khóa chính thông qua HKDF với ID của đích làm bối cảnh, thay vì mã hóa tất cả mật khẩu bằng cùng một khóa.** ([#25](https://github.com/hoangtulangtoc/dbvault/pull/25))
  - Cách chuyển đổi: Không cần thực hiện bất kỳ thao tác trực tiếp nào; thông tin đăng nhập sẽ được chuyển đổi sang cơ chế khóa mới một cách tự động khi cập nhật.
  - Theo dõi tại [DBV-16](https://hoangluongtran0309.atlassian.net/browse/DBV-16): Every target's credentials are encrypted with the same key (Done)

- **Một cuộc đánh giá đã được tiến hành đối với cơ chế mã hóa thông tin đăng nhập của dbvault, bao gồm quản lý khóa mã hóa và các rủi ro rò rỉ từ các bản sao lưu cơ sở dữ liệu.** ([OPS-6](https://linear.app/dbvault/issue/OPS-6/security-review-of-credential-encryption))

- **Các bài kiểm tra tích hợp không còn chứa thông tin đăng nhập S3 được mã hóa cứng trong mã nguồn; thay vào đó, thông tin đăng nhập được chèn qua repository secrets cho CI, hoặc được tạo ngẫu nhiên cho các lần chạy cục bộ. Emulator image S3 hiện được ghim bằng digest thay vì bằng thẻ.** ([OPS-7](https://linear.app/dbvault/issue/OPS-7/rotate-s3-credentials-used-by-integration-tests)) — Cập nhật này không ảnh hưởng đến dữ liệu sản xuất, cấu trúc cơ sở dữ liệu (schema) hoặc cấu hình lưu trữ. Cập nhật này chỉ ảnh hưởng đến hành vi của bộ kiểm thử và không thay đổi các cơ sở dữ liệu đã triển khai cũng như không yêu cầu bất kỳ hành động nào từ quản trị viên cơ sở dữ liệu (DBA).

## ✨ Tính năng mới

- **Giao diện đã được cải tiến, bao gồm lịch sử và bảng đích rõ ràng hơn, thanh bên mới hỗ trợ chủ đề và phông chữ tùy chỉnh, biểu tượng trạng thái được cải thiện, tiến trình tương tác trên các nút gửi, hiển thị lỗi trực tiếp và chi tiết mở rộng về trạng thái sao lưu và khôi phục.** ([#11](https://github.com/hoangtulangtoc/dbvault/pull/11))

- **Hiện đã hỗ trợ chỉnh sửa chi tiết kết nối của đích.** ([#14](https://github.com/hoangtulangtoc/dbvault/pull/14))

- **API hiện cung cấp các tổng kiểm tra sao lưu, bao gồm giá trị tổng kiểm tra đã ghi lại và thuật toán được sử dụng.** ([#15](https://github.com/hoangtulangtoc/dbvault/pull/15))
  - Theo dõi tại [DBV-15](https://hoangluongtran0309.atlassian.net/browse/DBV-15): Expose backup checksums in the API (Done)

- **Một tính năng mới đã được thêm vào, cho phép khôi phục bản sao lưu vào bất kỳ đích đã đăng ký nào.** ([#16](https://github.com/hoangtulangtoc/dbvault/pull/16))

- **Chức năng phân trang đã được thêm vào tính năng lịch sử sao lưu và khôi phục.** ([#17](https://github.com/hoangtulangtoc/dbvault/pull/17))

- **Chức năng xóa hàng loạt nhiều bản sao lưu và xóa đích sao lưu cùng với các bản sao lưu liên quan đã được thêm vào. Trang xác nhận sẽ tổng hợp số lượng tệp, dung lượng (bytes) và bản ghi khôi phục trước khi xóa. Các kiểm tra trước đảm bảo rằng việc xóa sẽ bị từ chối nếu bản sao lưu đang chạy hoặc đang được khôi phục. Việc xóa một đích không có bản sao lưu vẫn giữ nguyên hộp thoại một cú nhấp chuột; việc xóa một đích có bản sao lưu yêu cầu xác nhận và nhập tên của nó.** ([#18](https://github.com/hoangtulangtoc/dbvault/pull/18))

- **Các kiểm tra kết nối cơ sở dữ liệu, sao lưu và khôi phục hiện được định tuyến dựa trên công cụ cơ sở dữ liệu đích, với sự hỗ trợ được thêm vào cho PostgreSQL bên cạnh chức năng MySQL hiện có. Các bộ điều hợp PostgreSQL, hỗ trợ giao diện người dùng, xử lý thời gian chạy, phạm vi di chuyển, các bài kiểm tra tích hợp và tài liệu cập nhật đều được bao gồm. Việc khôi phục bị giới hạn ở loại công cụ ban đầu.** ([#20](https://github.com/hoangtulangtoc/dbvault/pull/20))
  - Cách chuyển đổi: Không cần thực hiện quá trình migration cụ thể nào trừ khi hỗ trợ các đích PostgreSQL; hệ thống hạn chế việc thay đổi engine của đích sau khi đăng ký.
  - Theo dõi tại [DBV-10](https://hoangluongtran0309.atlassian.net/browse/DBV-10): Route backups by engine and add PostgreSQL (Done)

- **Đã thêm hỗ trợ cho lịch trình sao lưu Quartz.** ([#21](https://github.com/hoangtulangtoc/dbvault/pull/21))
  - Theo dõi tại [DBV-12](https://hoangluongtran0309.atlassian.net/browse/DBV-12): Backup schedules (Done)

- **Giờ đây, có thể lên lịch sao lưu cho các đích sao lưu bằng cách sử dụng biểu thức cron với múi giờ đã chọn.** ([DBV-12](https://hoangluongtran0309.atlassian.net/browse/DBV-12))

- **Một tính năng lưu trữ mới đã được thêm vào để chỉ giữ lại N bản sao lưu thành công mới nhất cho mỗi đích sao lưu.** ([#22](https://github.com/hoangtulangtoc/dbvault/pull/22))
  - Cách chuyển đổi: Cấu hình cài đặt thời gian lưu trữ để chỉ định số lượng bản sao lưu gần đây cần giữ lại, nếu muốn. Không cần thực hiện bất kỳ quá trình migration đặc biệt nào khác.
  - Theo dõi tại [DBV-13](https://hoangluongtran0309.atlassian.net/browse/DBV-13): Keep the newest N backups per target (Done)

- **Đã thêm hỗ trợ cho các cấu hình lưu trữ tương thích với S3, cho phép lưu trữ các artifacts trong bất kỳ bucket nào tương thích với S3 thông qua các cấu hình được đặt tên.** ([#23](https://github.com/hoangtulangtoc/dbvault/pull/23))
  - Theo dõi tại [DBV-11](https://hoangluongtran0309.atlassian.net/browse/DBV-11): S3-compatible storage profiles (Done)

## 🐛 Sửa lỗi

- **CLI hiện ưu tiên biến môi trường DBVAULT_URL hơn tệp cấu hình khi xác định máy chủ cần kết nối.** ([!4](https://gitlab.com/dbvault1/dbvault-cli/-/merge_requests/4))
  - Theo dõi tại [DBV-18](https://hoangluongtran0309.atlassian.net/browse/DBV-18): CLI ignores the DBVAULT\_URL environment variable (Done)

- **Các artifact sao lưu cục bộ hiện sử dụng đường dẫn tệp duy nhất cho mỗi lần thực hiện sao lưu để tránh xung đột khi nhiều tác vụ sao lưu chạy đồng thời cho cùng một cơ sở dữ liệu.** ([#24](https://github.com/hoangtulangtoc/dbvault/pull/24))
  - Theo dõi tại [DBV-14](https://hoangluongtran0309.atlassian.net/browse/DBV-14): Two backups started in the same second overwrite each other (Done)

- **Console hiện hiển thị kích thước bản sao lưu bằng B, KB, MB, GB hoặc TB thay vì số byte thô.** ([#28](https://github.com/hoangtulangtoc/dbvault/pull/28))

## ⚡ Hiệu năng

- **Quá trình khôi phục hiện truyền trực tiếp đầu ra gunzip vào máy khách MySQL thay vì giải nén tệp lưu trữ vào đĩa trước.** ([#19](https://github.com/hoangtulangtoc/dbvault/pull/19))
  - Theo dõi tại [DBV-32](https://hoangluongtran0309.atlassian.net/browse/DBV-32): Stream decompression into the restore client (Done)

## 📚 Tài liệu

- **Đã bổ sung tài liệu hướng dẫn về việc cấu hình các cấu hình lưu trữ tương thích với S3 cho việc sao lưu, bao gồm các bước thiết lập và yêu cầu về chính sách.** ([#27](https://github.com/hoangtulangtoc/dbvault/pull/27)) — Các tài liệu mới mô tả chi tiết cách cấu hình và duy trì lưu trữ S3 hoặc MinIO cho các bản sao lưu dbvault, bao gồm các chính sách IAM cho bucket cần thiết và các thiết lập cấu hình nào an toàn để thay đổi sau khi đã thực hiện sao lưu.

- **Đã bổ sung tài liệu hướng dẫn sử dụng lưu trữ tương thích với S3, nêu chi tiết các nhà cung cấp được hỗ trợ và các quyền truy cập bucket cần thiết cho dbvault.** ([DBV-19](https://hoangluongtran0309.atlassian.net/browse/DBV-19)) — Hiện đã có hướng dẫn mô tả cách cấu hình lưu trữ AWS S3 và MinIO với dbvault, bao gồm chính sách bucket tối thiểu để đảm bảo hoạt động an toàn. Không cần thay đổi lược đồ, dữ liệu hoặc thông tin xác thực.

## 🔧 Bảo trì

- **Bài kiểm tra tích hợp PostgreSQL Testcontainers hiện đã bao quát nhiều loại dữ liệu hơn (`jsonb`, `timestamptz`, `bytea`, `numeric`), cũng như các kiểm tra về chỉ mục, view, khóa ngoại và tính liên tục của dãy bigserial.** ([#26](https://github.com/hoangtulangtoc/dbvault/pull/26))

- **Các cảnh báo Dependabot và cấu hình cập nhật phiên bản tự động đã được kích hoạt cho repository dbvault.** ([OPS-8](https://linear.app/dbvault/issue/OPS-8/enable-dependabot-alerts-and-version-updates-for-dbvault))

- **Các kiểm tra khởi động cho các bản nhị phân khách hàng của MySQL và PostgreSQL đã được tái cấu trúc thành một kiểm tra chung có tên ClientBinaries.require, và một bài kiểm tra đơn vị nhỏ đã được thêm vào cho thành phần này.** ([#29](https://github.com/hoangtulangtoc/dbvault/pull/29)) — Không có thay đổi nào đối với dữ liệu đã lưu trữ, cấu trúc cơ sở dữ liệu, thông tin đăng nhập hoặc cách công cụ tương tác với các engines quản trị cơ sở dữ liệu như MySQL hoặc PostgreSQL. Việc tái cấu trúc này không ảnh hưởng đến cách bạn chạy hoặc cấu hình dbvault.

- **Các bài kiểm tra tích hợp S3 không còn chứa thông tin đăng nhập được mã hóa cứng; thông tin đăng nhập kiểm tra hiện được chèn qua repository secrets hoặc được tạo ngẫu nhiên trong quá trình chạy cục bộ, và emulator image S3 được ghim bằng digest.** ([#30](https://github.com/hoangtulangtoc/dbvault/pull/30)) — Thay đổi này chỉ ảnh hưởng đến môi trường thử nghiệm tích hợp S3 và không tác động đến dữ liệu sản xuất, lược đồ hoặc cấu hình lưu trữ. Không cần thực hiện kiểm tra cơ sở dữ liệu hoặc migration sau khi nâng cấp.
