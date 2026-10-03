# Đặc tả yêu cầu chức năng — AI Recruitment Agent

> Nguồn: `SRS_Chuc_Nang_AI_Recruitment_Agent.docx` (chuyển sang markdown).
> Đặc tả này phản ánh hệ thống đã hoàn thành, cập nhật lần gần nhất ngày 29/09/2026. Các quyết
> định thiết kế chi tiết và nợ kỹ thuật liên quan được ghi ở `docs/ROADMAP.md` và
> `docs/walkthrough/`.
> Tham chiếu theo mã: FR-C (chung), FR-H (HR), FR-U (ứng viên).

Tài liệu này tổ chức lại toàn bộ yêu cầu chức năng theo 3 nhóm dựa trên đối tượng sử dụng: **Chức năng chung, Chức năng Nhà tuyển dụng (HR), và Chức năng Ứng viên (Candidate)**. Các mã yêu cầu được đánh số riêng theo từng nhóm: **FR-C (Common)**, **FR-H (HR)**, **FR-U (Ứng viên)**.

# 0. Tổng quan hệ thống

Nền tảng có hai vai trò sử dụng: **Nhà tuyển dụng (HR)** và **Ứng viên (Candidate)**. Tính đến thời
điểm cập nhật này, hệ thống có tổng cộng 39 mã yêu cầu chức năng: **18 chức năng đã hoàn thành**
(FR-C01–C04, FR-H01–H08, FR-U01–U06 — đặc tả đầy đủ ở Mục 1/2/3 bên dưới) và **21 chức năng bổ
sung đang ở giai đoạn đặc tả** (chưa được duyệt, chưa được code), mỗi chức năng có tài liệu chi
tiết riêng tại `docs/features/<nhóm>/<mã>/REQUIREMENT.md`.

| Mã | Tên | Actor | Trạng thái | Tài liệu chi tiết |
| --- | --- | --- | --- | --- |
| FR-C01 | Quản lý Tài khoản & Phân quyền | HR, Ứng viên | Đã hoàn thành | Mục 1 bên dưới |
| FR-C02 | Duyệt Thông tin Công khai | HR, Ứng viên | Đã hoàn thành | Mục 1 bên dưới |
| FR-C03 | Hệ thống Thông báo (Notification) | HR, Ứng viên | Đã hoàn thành | Mục 1 bên dưới |
| FR-C04 | AI Resume Parsing (Trích xuất CV) | Hệ thống | Đã hoàn thành | Mục 1 bên dưới |
| FR-C05 | Danh mục dùng chung và chuẩn hoá dữ liệu | Hệ thống | Đã hoàn thành | [features/CHUNG/C05/REQUIREMENT.md](features/CHUNG/C05/REQUIREMENT.md) |
| FR-C06 | Nhắn tin theo đơn ứng tuyển | HR, Ứng viên | Chưa đặc tả | [features/CHUNG/C06/REQUIREMENT.md](features/CHUNG/C06/REQUIREMENT.md) |
| FR-C07 | AI soạn nháp tin nhắn | HR, Ứng viên | Chưa đặc tả | [features/CHUNG/C07/REQUIREMENT.md](features/CHUNG/C07/REQUIREMENT.md) |
| FR-C08 | Hỏi đáp CV có trích dẫn | HR, Ứng viên | Chưa đặc tả | [features/CHUNG/C08/REQUIREMENT.md](features/CHUNG/C08/REQUIREMENT.md) |
| FR-H01 | Quản lý Hồ sơ Doanh nghiệp | HR | Đã hoàn thành | Mục 2 bên dưới |
| FR-H02 | Quản lý Tin Tuyển dụng (Job Posting) | HR | Đã hoàn thành | Mục 2 bên dưới |
| FR-H03 | Thiết lập Rubric Tuyển dụng | HR | Đã hoàn thành | Mục 2 bên dưới |
| FR-H04 | AI Rubric Scoring | HR (xem) — AI thực hiện | Đã hoàn thành | Mục 2 bên dưới |
| FR-H05 | Tổng hợp Điểm & Xếp hạng Ứng viên | HR (xem) — Backend thực hiện | Đã hoàn thành | Mục 2 bên dưới |
| FR-H06 | AI Explainable Scoring | HR (xem) — AI thực hiện | Đã hoàn thành | Mục 2 bên dưới |
| FR-H07 | Quản lý Pipeline & Quyết định Tuyển dụng | HR | Đã hoàn thành | Mục 2 bên dưới |
| FR-H08 | Dashboard, Thống kê & Lịch sử Đánh giá | HR | Đã hoàn thành | Mục 2 bên dưới |
| FR-H09 | Trang hồ sơ đơn ứng tuyển | HR | Chưa đặc tả | [features/HR/H09/REQUIREMENT.md](features/HR/H09/REQUIREMENT.md) |
| FR-H10 | Câu hỏi sàng lọc theo Job | HR | Chưa đặc tả | [features/HR/H10/REQUIREMENT.md](features/HR/H10/REQUIREMENT.md) |
| FR-H11 | AI tạo tin tuyển dụng | HR | Chưa đặc tả | [features/HR/H11/REQUIREMENT.md](features/HR/H11/REQUIREMENT.md) |
| FR-H12 | Giấy mời nhiều khung giờ | HR | Chưa đặc tả | [features/HR/H12/REQUIREMENT.md](features/HR/H12/REQUIREMENT.md) |
| FR-H13 | Câu hỏi phỏng vấn theo CV | HR | Chưa đặc tả | [features/HR/H13/REQUIREMENT.md](features/HR/H13/REQUIREMENT.md) |
| FR-H14 | So sánh 2–3 ứng viên | HR | Chưa đặc tả | [features/HR/H14/REQUIREMENT.md](features/HR/H14/REQUIREMENT.md) |
| FR-H15 | Kho ứng viên | HR | Chưa đặc tả | [features/HR/H15/REQUIREMENT.md](features/HR/H15/REQUIREMENT.md) |
| FR-H16 | Chế độ ẩn danh | HR | Chưa đặc tả | [features/HR/H16/REQUIREMENT.md](features/HR/H16/REQUIREMENT.md) |
| FR-U01 | Quản lý Hồ sơ Cá nhân & Upload CV | Ứng viên | Đã hoàn thành | Mục 3 bên dưới |
| FR-U02 | Tìm kiếm & Ứng tuyển Việc làm | Ứng viên | Đã hoàn thành | Mục 3 bên dưới |
| FR-U03 | Theo dõi Trạng thái Ứng tuyển | Ứng viên | Đã hoàn thành | Mục 3 bên dưới |
| FR-U04 | AI Job Recommendation | Ứng viên | Đã hoàn thành | Mục 3 bên dưới |
| FR-U05 | AI CV Improvement | Ứng viên | Đã hoàn thành | Mục 3 bên dưới |
| FR-U06 | Rút đơn Ứng tuyển | Ứng viên | Đã hoàn thành | Mục 3 bên dưới |
| FR-U07 | Bộ lọc tìm việc nâng cao | Ứng viên | Đã hoàn thành | [features/UV/U07/REQUIREMENT.md](features/UV/U07/REQUIREMENT.md) |
| FR-U08 | Trang chi tiết đơn ứng tuyển | Ứng viên | Chưa đặc tả | [features/UV/U08/REQUIREMENT.md](features/UV/U08/REQUIREMENT.md) |
| FR-U09 | Trả lời sàng lọc và đồng ý lưu hồ sơ | Ứng viên | Chưa đặc tả | [features/UV/U09/REQUIREMENT.md](features/UV/U09/REQUIREMENT.md) |
| FR-U10 | Chọn khung giờ phỏng vấn | Ứng viên | Chưa đặc tả | [features/UV/U10/REQUIREMENT.md](features/UV/U10/REQUIREMENT.md) |
| FR-U11 | Thống kê ứng tuyển cá nhân | Ứng viên | Chưa đặc tả | [features/UV/U11/REQUIREMENT.md](features/UV/U11/REQUIREMENT.md) |
| FR-U12 | Tạo CV (CV builder) | Ứng viên | Chưa đặc tả | [features/UV/U12/REQUIREMENT.md](features/UV/U12/REQUIREMENT.md) |
| FR-U13 | Tìm việc bằng ngôn ngữ tự nhiên | Ứng viên | Chưa đặc tả | [features/UV/U13/REQUIREMENT.md](features/UV/U13/REQUIREMENT.md) |
| FR-U14 | Hồ sơ nghề nghiệp và mong muốn công việc | Ứng viên | Đã hoàn thành | [features/UV/U14/REQUIREMENT.md](features/UV/U14/REQUIREMENT.md) |
| FR-U15 | Gợi ý việc làm theo hồ sơ | Ứng viên | Đã duyệt | [features/UV/U15/REQUIREMENT.md](features/UV/U15/REQUIREMENT.md) |

# 1. Chức năng chung (Common Functions)

*Các chức năng mà cả hai loại người dùng (HR và Ứng viên) đều sử dụng, hoặc là hạ tầng dùng chung phục vụ các chức năng chuyên biệt phía sau.*

| **Mã YC** | **Tên chức năng** | **Mô tả chi tiết** | **Actor** |
| --- | --- | --- | --- |
| **FR-C01** | **Quản lý Tài khoản ****&**** Phân quyền** | Hệ thống cung cấp cơ chế đăng ký, đăng nhập độc lập cho hai loại tài khoản: Nhà tuyển dụng (HR) và Ứng viên (Candidate). Xác thực áp dụng các giao thức bảo mật chuẩn: mật khẩu băm bằng BCrypt, phiên đăng nhập không trạng thái (stateless) dùng cặp JWT access token + refresh token. Giao diện và quyền truy xuất API được giới hạn nghiêm ngặt theo vai trò người dùng (Role-Based Access Control). | HR, Ứng viên |
| **FR-C02** | **Duyệt Thông tin Công khai** | Cả hai loại tài khoản đều có thể xem các thông tin công khai trên nền tảng: tìm kiếm tin tuyển dụng, danh sách tin đang mở, chi tiết một tin tuyển dụng, và trang hồ sơ của một doanh nghiệp cụ thể (truy cập từ tin tuyển dụng của doanh nghiệp đó). Không yêu cầu đăng nhập để xem, nhưng cần đăng nhập để thực hiện hành động (ứng tuyển, tạo tin...). | HR, Ứng viên |
| **FR-C03** | **Hệ thống Thông báo (Notification)** | Hệ thống tự động phát thông báo (qua giao diện web và email) đến người dùng liên quan khi có sự kiện thay đổi trạng thái: ứng viên nhận thông báo khi hồ sơ ứng tuyển đổi trạng thái; HR nhận thông báo khi có ứng viên mới nộp đơn, khi ứng viên rút đơn, hoặc khi AI hoàn tất chấm điểm một hồ sơ ứng tuyển. | HR, Ứng viên |
| **FR-C04** | **AI Resume Parsing (Trích xuất CV)** | Sau khi CV được ứng viên tải lên, hệ thống chạy một job nền tự động đọc, xử lý cấu trúc văn bản (PDF/DOCX) và trích xuất thông tin thành dữ liệu có cấu trúc (JSON): thông tin liên hệ, học vấn, kinh nghiệm làm việc, kỹ năng, chứng chỉ, dự án cá nhân. Lỗi tạm thời (mạng, quá tải, giới hạn tần suất của nhà cung cấp AI) được tự động thử lại; ứng viên có thể yêu cầu thử lại thủ công khi đã thất bại hẳn. Đây là dữ liệu nền dùng chung cho cả hai nhóm chức năng phía sau: Rubric Scoring (phía HR) và Job Recommendation / CV Improvement (phía Ứng viên). | Hệ thống (tự động, nền cho cả HR & Ứng viên) |

### Chức năng bổ sung

| Mã | Tên | Tóm tắt | Actor | Phụ thuộc |
| --- | --- | --- | --- | --- |
| FR-C05 | Danh mục dùng chung và chuẩn hoá dữ liệu | Danh mục ngành nghề, tỉnh/thành cố định; chuẩn hoá Job và CV; backend tự tính số năm kinh nghiệm | Hệ thống | FR-H02, FR-C04 |
| FR-C06 | Nhắn tin theo đơn ứng tuyển | HR và ứng viên trao đổi quanh từng đơn, có đính kèm file, thông báo web + email, tự tải lại định kỳ | HR, Ứng viên | FR-C03, FR-U01, FR-H09, FR-U08 |
| FR-C07 | AI soạn nháp tin nhắn | Soạn nháp theo tình huống có sẵn của từng vai trò; người dùng sửa rồi tự bấm Gửi; không lộ điểm/rubric | HR, Ứng viên | FR-C06 |
| FR-C08 | Hỏi đáp CV có trích dẫn | Hỏi về một CV cụ thể, trả lời kèm trích dẫn đã kiểm chứng; HR hỏi CV của đơn, ứng viên hỏi CV của mình | HR, Ứng viên | FR-C04, FR-H09 |

# 2. Chức năng Nhà tuyển dụng (HR)

*Các chức năng dành riêng cho tài khoản Nhà tuyển dụng, bao gồm cả các chức năng AI mà HR là bên tiêu thụ kết quả (chấm điểm, giải thích). Lưu ý ranh giới trách nhiệm giữa AI và Backend được nêu rõ ở cột "Nguyên tắc" của từng mục liên quan.*

| **Mã YC** | **Tên chức năng** | **Mô tả chi tiết** | **Actor** |
| --- | --- | --- | --- |
| **FR-H01** | **Quản lý Hồ sơ Doanh nghiệp** | HR tạo lập, cập nhật thông tin định danh doanh nghiệp: tên công ty, logo, website, mô tả quy mô, lĩnh vực hoạt động, thông tin liên hệ. Dữ liệu hiển thị công khai phục vụ tra cứu của ứng viên (liên kết FR-C02). | HR |
| **FR-H02** | **Quản lý Tin Tuyển dụng (Job Posting)** | HR tạo mới bài đăng tuyển dụng (tiêu đề, mô tả công việc, mức lương, địa điểm, loại hình làm việc), chỉnh sửa, tạm dừng hoặc gỡ khỏi danh sách công khai (xóa mềm, giữ lại dữ liệu phục vụ audit/thống kê). Mỗi tin tuyển dụng phải được liên kết với một bộ tiêu chí đánh giá (Rubric – FR-H03). Khi đăng tin, HR đồng thời tạo Mẫu Giấy mời Phỏng vấn gắn với Job này, gồm các trường cố định: tên công ty, lời mời, người gửi, địa chỉ (ô ngày giờ phỏng vấn để trống, sẽ điền khi mời từng ứng viên cụ thể — xem FR-H07). | HR |
| **FR-H03** | **Thiết lập Rubric Tuyển dụng (Rubric Configuration)** | HR thêm các tiêu chí đánh giá cho từng Job và gán trọng số cho từng tiêu chí (tổng trọng số = 100%). HR có thể chỉnh sửa trọng số, xóa tiêu chí. HR không bắt buộc phải mô tả chi tiết thang điểm 1–5 cho từng tiêu chí; nếu không cung cấp, hệ thống áp dụng thang điểm mặc định dùng chung. HR có thể tùy chọn tự viết mô tả thang điểm riêng nếu muốn kiểm soát chặt chẽ hơn. **Nguyên tắc: ***HR quyết định tiêu chí và trọng số; các mức điểm cụ thể của ứng viên do AI đánh giá (FR-H04), không phải HR định nghĩa sẵn.* | HR |
| **FR-H04** | **AI Rubric Scoring (Chấm điểm từng tiêu chí)** | Dựa trên dữ liệu CV đã trích xuất (FR-C04) và bộ cấu hình Rubric (FR-H03), AI (LLM) đối chiếu, phân tích ngữ nghĩa và chấm điểm cho từng tiêu chí riêng lẻ, kèm minh chứng (evidence) trích từ CV. Mỗi minh chứng AI trả về phải là trích dẫn nguyên văn khớp với nội dung CV gốc; hệ thống đối chiếu tự động và từ chối minh chứng không khớp. Nếu một tiêu chí có minh chứng không đạt kiểm tra này, toàn bộ lượt chấm của hồ sơ dừng lại và không tính điểm — hệ thống không lưu kết quả mà nó không kiểm chứng được. HR có thể tạo lượt chấm mới để thử lại. **Nguyên tắc: ***AI chỉ đánh giá và trả về điểm số của từng tiêu chí đơn lẻ. AI không tính tổng điểm và không xếp hạng ứng viên — việc này thuộc về FR-H05.* | HR (xem kết quả) — AI thực hiện |
| **FR-H05** | **Tổng hợp Điểm ****&**** Xếp hạng Ứng viên** | Backend tổng hợp điểm từng tiêu chí (FR-H04) theo đúng trọng số HR đã cấu hình (FR-H03) để tính tổng điểm, sau đó sắp xếp danh sách ứng viên của cùng một chiến dịch theo thứ tự tổng điểm từ cao xuống thấp. **Nguyên tắc: ***Đây là phép tính xác định (cộng theo trọng số + sắp xếp) do Backend thực hiện, không do AI. Hệ thống chỉ xếp hạng theo điểm, không phân loại hay gán nhãn Đạt/Cần xem xét/Không đạt — HR tự mở từng hồ sơ theo thứ tự và ra quyết định.* | HR (xem kết quả) — Backend thực hiện |
| **FR-H06** | **AI Explainable Scoring (Giải thích Điểm số)** | Với mỗi hồ sơ đã chấm điểm, AI tạo báo cáo giải thích bằng ngôn ngữ tự nhiên: lý do ứng viên đạt điểm cao/thấp ở từng tiêu chí, các tiêu chí đã đáp ứng, các tiêu chí còn thiếu hụt, tổng kết điểm mạnh và điểm yếu cốt lõi. HR có thể tải về CV gốc của ứng viên để đối chiếu trực tiếp với các minh chứng trong báo cáo. **Nguyên tắc: ***Mọi giải thích phải có thể kiểm chứng dựa trên nội dung CV (trích dẫn evidence cụ thể), phục vụ yêu cầu minh bạch (Explainable AI) và tuân thủ nguyên tắc Human-in-the-loop.* | HR (xem kết quả) — AI thực hiện |
| **FR-H07** | **Quản lý Pipeline ****&**** Quyết định Tuyển dụng** | HR xem danh sách ứng viên đã được xếp hạng theo điểm (FR-H05), mở từng hồ sơ để xem điểm số và giải thích (FR-H06), rồi tự tay quyết định: (1) Mời phỏng vấn — hệ thống tự render Mẫu Giấy mời đã tạo ở FR-H02 kèm tên ứng viên, HR điền ngày giờ cụ thể và có thể chỉnh sửa nội dung trước khi gửi qua FR-C03; hoặc (2) Từ chối ngay từ vòng hồ sơ. Nội dung giấy mời được lưu lại nguyên văn tại thời điểm gửi; các thay đổi sau đó với mẫu giấy mời không ảnh hưởng tới giấy mời đã gửi cho ứng viên. Sau buổi phỏng vấn, HR quay lại xác nhận kết quả cuối: Trúng tuyển hoặc Bị từ chối. Trạng thái ứng tuyển gồm 5 giá trị: Chờ duyệt → Đã mời phỏng vấn (có lịch hẹn) → Trúng tuyển / Bị từ chối; Chờ duyệt → Bị từ chối; và Đã rút đơn (do ứng viên chủ động — xem FR-U06) có thể xảy ra ở bất kỳ giai đoạn nào trước khi có kết quả cuối. **Nguyên tắc: ***Không sử dụng AI để tự quyết định ứng viên đậu hay rớt, kể cả dưới hình thức gán nhãn phân loại. Hệ thống chỉ cung cấp điểm số, xếp hạng và giải thích; quyết định Mời phỏng vấn / Từ chối / Trúng tuyển luôn do HR trực tiếp thực hiện trên từng hồ sơ.* | HR |
| **FR-H08** | **Dashboard, Thống kê ****&**** Lịch sử Đánh giá** | Bảng điều khiển trực quan tổng hợp: tổng số hồ sơ, tỷ lệ chuyển đổi giữa các vòng, hiệu suất từng chiến dịch tuyển dụng. Tỷ lệ chuyển đổi được tính dựa trên việc đơn ứng tuyển đã từng đạt tới một trạng thái nhất định trong lịch sử, không phụ thuộc trạng thái hiện tại của đơn (ví dụ đơn được mời phỏng vấn rồi rút vẫn được tính là đã từng mời). HR có thể lọc/sắp xếp danh sách ứng viên theo khoảng tổng điểm hoặc theo điểm của một tiêu chí cụ thể (VD: chỉ xem ứng viên có điểm Docker ≥ 8/10), để thu hẹp danh sách khi có nhiều hồ sơ. HR có thể truy xuất toàn bộ lịch sử đánh giá của AI đối với từng ứng viên để phục vụ đối chiếu, kiểm tra và tuân thủ (audit). | HR |

### Chức năng bổ sung

| Mã | Tên | Tóm tắt | Actor | Phụ thuộc |
| --- | --- | --- | --- | --- |
| FR-H09 | Trang hồ sơ đơn ứng tuyển | Một màn hình gom CV, điểm, giải thích, sàng lọc, câu hỏi PV, trao đổi, hỏi đáp CV và các nút quyết định | HR | FR-H04, FR-H05, FR-H06, FR-H07 |
| FR-H10 | Câu hỏi sàng lọc theo Job | HR đặt câu hỏi ngắn/lựa chọn/có-không cho Job; không tự loại, không đưa vào chấm điểm | HR | FR-H02 |
| FR-H11 | AI tạo tin tuyển dụng | Từ mô tả tự do sinh bản nháp Job, gợi ý tiêu chí rubric và câu hỏi sàng lọc; trọng số do HR nhập | HR | FR-H02, FR-H03, FR-C05, FR-H10 |
| FR-H12 | Giấy mời nhiều khung giờ | Giấy mời có 1–5 khung giờ để ứng viên chọn; thêm thao tác gửi lại giấy mời | HR | FR-H07, FR-C03 |
| FR-H13 | Câu hỏi phỏng vấn theo CV | AI sinh câu hỏi theo từng tiêu chí rubric, kèm lý do và đoạn CV liên quan; HR sửa và lưu | HR | FR-H09 |
| FR-H14 | So sánh 2–3 ứng viên | Bảng song song điểm, tiêu chí, kinh nghiệm, kỹ năng của các đơn cùng Job; không đánh dấu người thắng | HR | FR-H05, FR-H06, FR-H09 |
| FR-H15 | Kho ứng viên | Lưu đơn đã được ứng viên đồng ý vào các kho, kèm ghi chú, AI tóm tắt và bộ lọc chuyên môn | HR | FR-H09, FR-C05, FR-U09 |
| FR-H16 | Chế độ ẩn danh | Che thông tin nhân thân khi HR duyệt hồ sơ của một Job; tự bỏ khi mời phỏng vấn | HR | FR-H09, FR-H14, FR-C08 |

# 3. Chức năng Ứng viên (Candidate)

*Các chức năng dành riêng cho tài khoản Ứng viên, bao gồm các chức năng AI phục vụ trực tiếp ứng viên (gợi ý việc làm, gợi ý cải thiện CV).*

| **Mã YC** | **Tên chức năng** | **Mô tả chi tiết** | **Actor** |
| --- | --- | --- | --- |
| **FR-U01** | **Quản lý Hồ sơ Cá nhân ****&**** Upload CV** | Ứng viên quản lý thông tin nhân khẩu học cơ bản và tải lên, lưu trữ nhiều phiên bản CV dưới định dạng PDF hoặc DOCX (kiểm định dạng bằng magic bytes của nội dung file, không tin theo phần mở rộng), mỗi file tối đa 10MB, không giới hạn số lượng CV lưu trữ. Việc upload sẽ tự động kích hoạt AI Resume Parsing (FR-C04) ở tầng nền. | Ứng viên |
| **FR-U02** | **Tìm kiếm ****&**** Ứng tuyển Việc làm** | Ứng viên tìm kiếm việc làm theo từ khóa, địa điểm hoặc danh mục, và gửi CV ứng tuyển vào một vị trí cụ thể. Hệ thống đảm bảo mỗi ứng viên chỉ có thể nộp một CV duy nhất cho một vị trí trong một chu kỳ tuyển dụng; một chu kỳ tuyển dụng mới bắt đầu khi HR mở lại một tin đã tạm dừng/đóng (chuyển từ Đã đóng sang Đang mở). Tại bước nộp đơn, ứng viên phải đánh dấu đồng ý (checkbox) cho phép CV được hệ thống AI phân tích và chấm điểm để hỗ trợ HR đánh giá; không đồng ý thì không thể hoàn tất ứng tuyển. | Ứng viên |
| **FR-U03** | **Theo dõi Trạng thái Ứng tuyển** | Ứng viên theo dõi sự dịch chuyển trạng thái hồ sơ qua 5 trạng thái và xem lại toàn bộ lịch sử ứng tuyển của bản thân. Danh sách được tải lại mỗi khi ứng viên truy cập trang; hệ thống chưa tự động đẩy cập nhật về giao diện. | Ứng viên |
| **FR-U04** | **AI Job Recommendation (Tư vấn Việc làm)** | Dựa trên dữ liệu CV đã trích xuất (FR-C04), hệ thống tính độ tương đồng ngữ nghĩa (embedding + cosine similarity) giữa CV và từng mô tả công việc để xếp hạng và đề xuất các vị trí phù hợp nhất trên bảng tin của ứng viên. Hệ thống áp một ngưỡng tương đồng tối thiểu để loại các vị trí quá xa hồ sơ. Đo đạc trên dữ liệu thật cho thấy giá trị ngưỡng hiện tại loại được rất ít — kết quả có ý nghĩa của chức năng này nằm ở thứ tự xếp hạng, không phải ở phép cắt theo ngưỡng; chi tiết đo đạc và hướng cải thiện xem `docs/ROADMAP.md`. | Ứng viên |
| **FR-U05** | **AI CV Improvement (Gợi ý Cải thiện CV)** | AI phân tích nội dung CV hiện tại, đối chiếu với xu hướng kỹ năng thị trường (rút từ các tin tuyển dụng đang mở), sau đó đưa ra gợi ý bổ sung từ khóa kỹ năng còn thiếu, chỉ ra đoạn văn cần chỉnh sửa, và đề xuất lộ trình học tập/chứng chỉ nên bổ sung. | Ứng viên |
| **FR-U06** | **Rút đơn Ứng tuyển** | Ứng viên có thể chủ động rút lại một đơn ứng tuyển đã nộp (VD: đã nhận việc nơi khác, đổi ý) ở bất kỳ giai đoạn nào trước khi có kết quả cuối cùng. Hệ thống chuyển trạng thái đơn sang "Đã rút đơn", không xóa dữ liệu hồ sơ/điểm số đã có, để đảm bảo tính chính xác của thống kê (FR-H08) và khả năng kiểm chứng lịch sử. **Nguyên tắc: ***Dùng đổi trạng thái (soft state), không dùng xóa cứng (hard delete) — giữ dấu vết cho audit và tránh sai lệch số liệu tỷ lệ từ chối.* | Ứng viên |

### Chức năng bổ sung

| Mã | Tên | Tóm tắt | Actor | Phụ thuộc |
| --- | --- | --- | --- | --- |
| FR-U07 | Bộ lọc tìm việc nâng cao | Lọc theo ngành nghề, khu vực, lương, hình thức, thời gian đăng; giữ bộ lọc trên URL | Ứng viên | FR-C02, FR-C05 |
| FR-U08 | Trang chi tiết đơn ứng tuyển | Một màn hình gom trạng thái, lịch sử, giấy mời, câu trả lời sàng lọc, trao đổi, rút đơn, rút đồng ý | Ứng viên | FR-U03, FR-U06 |
| FR-U09 | Trả lời sàng lọc và đồng ý lưu hồ sơ | Khi nộp đơn: trả lời câu hỏi sàng lọc; tuỳ chọn cho công ty lưu hồ sơ (mặc định không) | Ứng viên | FR-U02, FR-H10 |
| FR-U10 | Chọn khung giờ phỏng vấn | Ứng viên chọn một khung giờ trong giấy mời; HR được thông báo | Ứng viên | FR-H12, FR-U08 |
| FR-U11 | Thống kê ứng tuyển cá nhân | Số đơn theo trạng thái, tỷ lệ được mời phỏng vấn, biểu đồ theo thời gian | Ứng viên | FR-U03 |
| FR-U12 | Tạo CV (CV builder) | Tạo CV từ form, xuất PDF, dùng như CV upload; AI chỉ gợi ý diễn đạt, không thêm nội dung | Ứng viên | FR-U01, FR-C04 |
| FR-U13 | Tìm việc bằng ngôn ngữ tự nhiên | Mô tả việc mong muốn bằng lời; hệ thống tách điều kiện lọc và xếp theo độ liên quan | Ứng viên | FR-U07, FR-U15, FR-C05 |
| FR-U14 | Hồ sơ nghề nghiệp và mong muốn công việc | Sau đăng ký hoặc ở trang hồ sơ: khai chức danh, ngành, khu vực, lương, kỹ năng mong muốn; điền nhanh từ CV | Ứng viên | FR-U01, FR-C05, FR-U04 |
| FR-U15 | Gợi ý việc làm theo hồ sơ | Khối "Gợi ý cho bạn" trên trang Việc làm, dựa trên mong muốn + CV (hoặc hồ sơ nếu chưa có CV); mở rộng FR-U04 | Ứng viên | FR-U14, FR-U07, FR-C05, FR-U04 |

## Ghi chú tổng hợp về nguyên tắc thiết kế AI

- HR quyết định tiêu chí đánh giá và trọng số (FR-H03).

- AI chỉ chấm điểm và giải thích ở cấp độ từng tiêu chí (FR-H04, FR-H06).

- Backend tính tổng điểm và xếp hạng (FR-H05) bằng công thức tường minh (theo trọng số), có thể kiểm chứng không phân loại, không dùng ngưỡng, không gán nhãn Đạt/Không đạt.

- Mỗi minh chứng (evidence) AI trả về phải là trích dẫn nguyên văn, khớp được với nội dung CV gốc; hệ thống đối chiếu tự động và từ chối minh chứng không khớp. Một tiêu chí có minh chứng không đạt kiểm tra này làm hỏng toàn bộ lượt chấm của hồ sơ — hệ thống không lưu kết quả mà nó không kiểm chứng được (FR-H04).

- Không sử dụng AI để tự quyết định ứng viên đậu hay rớt, kể cả dưới hình thức gán nhãn phân loại; HR tự mở từng hồ sơ theo thứ tự xếp hạng và trực tiếp quyết định Mời phỏng vấn / Từ chối / Trúng tuyển (FR-H07).

- Vòng đời ứng tuyển gồm 5 trạng thái: Chờ duyệt, Đã mời phỏng vấn (có lịch hẹn), Trúng tuyển, Bị từ chối, Đã rút đơn (FR-H07, FR-U03, FR-U06).

- Ứng viên phải đồng ý (consent) cho CV được AI phân tích trước khi ứng tuyển (FR-U02).

### Nguyên tắc bổ sung cho các chức năng mới

- AI không tự gửi, tự đăng hay tự quyết định; mọi nội dung AI sinh ra là bản nháp/gợi ý, chỉ có hiệu lực khi người dùng bấm xác nhận.

- Không lọc, không hỏi, không suy đoán thông tin nhân thân (tuổi, giới tính, ảnh, hôn nhân, tôn giáo…).

- Không gán nhãn Phù hợp/Không phù hợp cho ứng viên, không dùng ngưỡng để loại ứng viên; xếp hạng ứng viên chỉ có một nguồn là FR-H05.

- Dữ liệu thiếu phải hiển thị rõ là thiếu, không âm thầm loại khỏi kết quả.

- Nội dung đã gửi cho người khác (tin nhắn, giấy mời, câu trả lời sàng lọc) lưu nguyên văn, không đổi khi mẫu/câu hỏi gốc bị sửa.

- Phía ứng viên không bao giờ nhận điểm số, rubric, giải thích AI hay ghi chú nội bộ của HR.

# 4. Chức năng hiện có bị ảnh hưởng

| Mã hiện có | Bị mở rộng bởi | Thay đổi dự kiến |
| --- | --- | --- |
| FR-H02 | FR-C05, FR-H10, FR-H11 | Ngành nghề/địa điểm chọn theo danh mục; thêm câu hỏi sàng lọc; tạo tin bằng AI |
| FR-C04 | FR-C05 | Schema trích xuất phiên bản 2: chức danh, ngành nghề, khu vực chuẩn hoá; backend tự tính số năm kinh nghiệm |
| FR-C03 | FR-C06, FR-H12, FR-U10 | Thêm loại thông báo: tin nhắn mới, ứng viên đã chốt lịch |
| FR-H07 | FR-H12 | Giấy mời nhiều khung giờ; thêm thao tác gửi lại giấy mời khi đơn đang ở INTERVIEW_INVITED |
| FR-U01 | FR-U14 | Hồ sơ nghề nghiệp và mong muốn công việc |
| FR-U02 | FR-U09 | Trả lời câu hỏi sàng lọc; tuỳ chọn đồng ý cho công ty lưu hồ sơ |
| FR-U04 | FR-U15 | Gợi ý được cả khi chưa có CV (dựa trên hồ sơ); hiển thị ở trang Việc làm; một nguồn gợi ý duy nhất, dùng chung bộ máy với FR-U13 |

# 5. Ngoài phạm vi

Các nội dung dưới đây đã được chủ động loại bỏ khỏi phạm vi, ghi lại để không ai thêm lại:

- Kho CV công khai: HR chỉ thao tác với CV đã gửi tới công ty mình.
- Tìm ứng viên bằng ngôn ngữ tự nhiên phía HR: sẽ tạo xếp hạng thứ hai cạnh tranh với FR-H05.
- Chatbot hỏi trên nhiều CV cùng lúc: mỗi lần hỏi chỉ trên 1 CV.
- Chat thời gian thực (WebSocket): giao diện tự tải lại định kỳ.
- Che thông tin nhân thân trong đầu vào chấm điểm AI: chế độ ẩn danh chỉ che khi hiển thị.
- Dùng AI để nhận diện thông tin nhân thân.