# Bản phát hành v0.2.0

## Có gì mới

Bản phát hành này gồm 25 thay đổi: 1 thay đổi phá vỡ, 3 thay đổi bảo mật, 11 tính năng mới, 3 lỗi được sửa, 1 cải thiện hiệu năng, 2 cập nhật tài liệu và 4 thay đổi bảo trì.

> ⚠️ 1 thay đổi phá vỡ cần được xử lý trước khi nâng cấp.

## ⚠️ Thay đổi phá vỡ

- **CLI hiện yêu cầu ID đích sao lưu phải được cung cấp thông qua tùy chọn --target-id thay vì dưới dạng đối số vị trí.** — Bây giờ, bạn cần chỉ định đích sao lưu bằng cách sử dụng --target-id khi thực thi lệnh sao lưu. Nếu bạn sử dụng cách cũ, hệ thống sẽ hiển thị lỗi thay vì tự động đoán ý định của bạn.

## 🔒 Bảo mật

- **Việc mã hóa mật khẩu của từng đích hiện sử dụng một khóa duy nhất được dẫn xuất từ khóa chính thông qua HKDF với ID của đích làm bối cảnh, thay vì mã hóa tất cả mật khẩu bằng cùng một khóa.** — Mật khẩu của bạn hiện được bảo vệ tốt hơn, vì mỗi mật khẩu đều có khóa mã hóa riêng. Bạn không cần thực hiện bất kỳ thao tác nào và vẫn có thể truy cập dữ liệu của mình.

- **Một cuộc đánh giá đã được tiến hành đối với cơ chế mã hóa thông tin đăng nhập của dbvault, bao gồm quản lý khóa mã hóa và các rủi ro rò rỉ từ các bản sao lưu cơ sở dữ liệu.** — Bản cập nhật này không có thay đổi nào ảnh hưởng đến cách bạn sử dụng sản phẩm.

- **Các bài kiểm tra tích hợp không còn chứa thông tin đăng nhập S3 được mã hóa cứng trong mã nguồn; thay vào đó, thông tin đăng nhập được chèn qua repository secrets cho CI, hoặc được tạo ngẫu nhiên cho các lần chạy cục bộ. Emulator image S3 hiện được ghim bằng digest thay vì bằng thẻ.** — Bạn sẽ không nhận thấy bất kỳ sự khác biệt nào khi sử dụng sản phẩm; bản cập nhật này chỉ thay đổi cách thức hoạt động của một số bài kiểm tra nội bộ.

## ✨ Tính năng mới

- **Giao diện đã được cải tiến, bao gồm lịch sử và bảng đích rõ ràng hơn, thanh bên mới hỗ trợ chủ đề và phông chữ tùy chỉnh, biểu tượng trạng thái được cải thiện, tiến trình tương tác trên các nút gửi, hiển thị lỗi trực tiếp và chi tiết mở rộng về trạng thái sao lưu và khôi phục.** — Bạn sẽ thấy một console rõ ràng và dễ đọc hơn, với các chủ đề, bảng dữ liệu được cải tiến và phản hồi tốt hơn khi các thao tác đang diễn ra hoặc xảy ra lỗi.

- **Hiện đã hỗ trợ chỉnh sửa chi tiết kết nối của đích.** — Giờ đây, bạn có thể cập nhật thông tin kết nối của một đích hiện có trực tiếp từ giao diện.

- **API hiện cung cấp các tổng kiểm tra sao lưu, bao gồm giá trị tổng kiểm tra đã ghi lại và thuật toán được sử dụng.** — Giờ đây, bạn có thể xem tổng kiểm tra (checksum) kèm theo mỗi bản sao lưu, giúp bạn xác minh rằng các tệp đã tải xuống là đầy đủ và không bị thay đổi.

- **Một tính năng mới đã được thêm vào, cho phép khôi phục bản sao lưu vào bất kỳ đích đã đăng ký nào.** — Giờ đây, bạn có thể khôi phục bản sao lưu vào bất kỳ vị trí nào đã lưu, không chỉ giới hạn ở vị trí ban đầu.

- **Chức năng phân trang đã được thêm vào tính năng lịch sử sao lưu và khôi phục.** — Giờ đây, bạn có thể xem lịch sử sao lưu và khôi phục theo từng trang, giúp việc tìm kiếm các bản ghi gần đây trở nên dễ dàng hơn mà không cần tải toàn bộ dữ liệu cùng một lúc.

- **Chức năng xóa hàng loạt nhiều bản sao lưu và xóa đích sao lưu cùng với các bản sao lưu liên quan đã được thêm vào. Trang xác nhận sẽ tổng hợp số lượng tệp, dung lượng (bytes) và bản ghi khôi phục trước khi xóa. Các kiểm tra trước đảm bảo rằng việc xóa sẽ bị từ chối nếu bản sao lưu đang chạy hoặc đang được khôi phục. Việc xóa một đích không có bản sao lưu vẫn giữ nguyên hộp thoại một cú nhấp chuột; việc xóa một đích có bản sao lưu yêu cầu xác nhận và nhập tên của nó.** — Giờ đây, bạn có thể xóa nhiều bản sao lưu cùng lúc và xóa một đích sao lưu cùng với tất cả các bản sao lưu của nó nếu bạn xác nhận. Hệ thống sẽ cảnh báo và chặn việc xóa nếu có bất kỳ bản sao lưu nào đang chạy hoặc đang được khôi phục.

- **Các kiểm tra kết nối cơ sở dữ liệu, sao lưu và khôi phục hiện được định tuyến dựa trên công cụ cơ sở dữ liệu đích, với sự hỗ trợ được thêm vào cho PostgreSQL bên cạnh chức năng MySQL hiện có. Các bộ điều hợp PostgreSQL, hỗ trợ giao diện người dùng, xử lý thời gian chạy, phạm vi di chuyển, các bài kiểm tra tích hợp và tài liệu cập nhật đều được bao gồm. Việc khôi phục bị giới hạn ở loại công cụ ban đầu.** — Giờ đây, bạn có thể sử dụng dịch vụ này với cơ sở dữ liệu PostgreSQL cũng như MySQL, hệ thống sẽ đảm bảo việc sao lưu và khôi phục diễn ra chính xác cho loại cơ sở dữ liệu bạn đã chọn.

- **Đã thêm hỗ trợ cho lịch trình sao lưu Quartz.** — Giờ đây, bạn có thể lên lịch sao lưu bằng hệ thống mới, giúp việc quản lý thời điểm thực hiện sao lưu trở nên dễ dàng hơn.

- **Giờ đây, có thể lên lịch sao lưu cho các đích sao lưu bằng cách sử dụng biểu thức cron với múi giờ đã chọn.** — Bây giờ bạn có thể thiết lập sao lưu tự động định kỳ bằng cách chọn lịch trình và múi giờ. Nếu hệ thống bị tắt và bỏ lỡ một lần sao lưu, hệ thống sẽ thực hiện bù lại khi khởi động lại.

- **Một tính năng lưu trữ mới đã được thêm vào để chỉ giữ lại N bản sao lưu thành công mới nhất cho mỗi đích sao lưu.** — Hiện tại, hệ thống có thể giới hạn số lượng bản sao lưu gần đây được lưu trữ, do đó các bản sao lưu cũ hơn sẽ tự động bị xóa và dung lượng lưu trữ được kiểm soát chặt chẽ.

- **Đã thêm hỗ trợ cho các cấu hình lưu trữ tương thích với S3, cho phép lưu trữ các artifacts trong bất kỳ bucket nào tương thích với S3 thông qua các cấu hình được đặt tên.** — Giờ đây, bạn có thể chọn nơi lưu trữ tệp của mình và sử dụng các tùy chọn tương thích với S3 khác nhau như AWS S3 hoặc MinIO. Các khóa bí mật của bạn được lưu trữ an toàn và có thể được kiểm tra trước khi tệp được tải lên.

## 🐛 Sửa lỗi

- **CLI hiện ưu tiên biến môi trường DBVAULT_URL hơn tệp cấu hình khi xác định máy chủ cần kết nối.** — Nếu bạn thiết lập biến môi trường DBVAULT_URL, CLI giờ đây sẽ kết nối chính xác với máy chủ mà bạn chỉ định tại đó, thay vì luôn sử dụng máy chủ trong tệp cấu hình của nó.

- **Các artifact sao lưu cục bộ hiện sử dụng đường dẫn tệp duy nhất cho mỗi lần thực hiện sao lưu để tránh xung đột khi nhiều tác vụ sao lưu chạy đồng thời cho cùng một cơ sở dữ liệu.** — Khi tạo nhiều bản sao lưu cùng lúc, mỗi bản sao lưu giờ đây sẽ có tệp riêng để chúng không ghi đè lên nhau.

- **Console hiện hiển thị kích thước bản sao lưu bằng B, KB, MB, GB hoặc TB thay vì số byte thô.**

## ⚡ Hiệu năng

- **Quá trình khôi phục hiện truyền trực tiếp đầu ra gunzip vào máy khách MySQL thay vì giải nén tệp lưu trữ vào đĩa trước.** — Quá trình khôi phục hiện nay cần ít dung lượng đĩa hơn và diễn ra nhanh hơn, vì các bản sao lưu được giải nén trong quá trình nhập, chứ không phải trước đó.

## 📚 Tài liệu

- **Đã bổ sung tài liệu hướng dẫn về việc cấu hình các cấu hình lưu trữ tương thích với S3 cho việc sao lưu, bao gồm các bước thiết lập và yêu cầu về chính sách.** — Có các hướng dẫn mới chỉ ra cách lưu trữ bản sao lưu của bạn trên S3 hoặc MinIO và những gì cần thiết để thiết lập điều này.

- **Đã bổ sung tài liệu hướng dẫn sử dụng lưu trữ tương thích với S3, nêu chi tiết các nhà cung cấp được hỗ trợ và các quyền truy cập bucket cần thiết cho dbvault.** — Tài liệu hiện nay giải thích các dịch vụ lưu trữ tương thích với S3 nào hoạt động với dbvault và các quyền cần thiết để thiết lập.

## 🔧 Bảo trì

- **Bài kiểm tra tích hợp PostgreSQL Testcontainers hiện đã bao quát nhiều loại dữ liệu hơn (`jsonb`, `timestamptz`, `bytea`, `numeric`), cũng như các kiểm tra về chỉ mục, view, khóa ngoại và tính liên tục của dãy bigserial.** — Bản cập nhật này không ảnh hưởng đến cách bạn sử dụng sản phẩm và không thay đổi bất kỳ tính năng nào dành cho người dùng.

- **Các cảnh báo Dependabot và cấu hình cập nhật phiên bản tự động đã được kích hoạt cho repository dbvault.** — Bạn sẽ không nhận thấy bất kỳ thay đổi nào trong cách sản phẩm hoạt động, nhưng nhóm phát triển đang cải thiện cách thức cập nhật phần mềm nền tảng.

- **Các kiểm tra khởi động cho các bản nhị phân khách hàng của MySQL và PostgreSQL đã được tái cấu trúc thành một kiểm tra chung có tên ClientBinaries.require, và một bài kiểm tra đơn vị nhỏ đã được thêm vào cho thành phần này.** — Không có thay đổi nào hiển thị; mọi thứ vẫn hoạt động như trước đây.

- **Các bài kiểm tra tích hợp S3 không còn chứa thông tin đăng nhập được mã hóa cứng; thông tin đăng nhập kiểm tra hiện được chèn qua repository secrets hoặc được tạo ngẫu nhiên trong quá trình chạy cục bộ, và emulator image S3 được ghim bằng digest.** — Không có thay đổi nào hiển thị cho người dùng; bản cập nhật này chỉ cải thiện cách các nhà phát triển chạy các bài kiểm thử nội bộ.
