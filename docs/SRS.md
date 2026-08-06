# Đặc tả yêu cầu chức năng — AI Recruitment Agent

> Nguồn: `SRS_Chuc_Nang_AI_Recruitment_Agent.docx` (chuyển sang markdown).
> Đây là NGUỒN SỰ THẬT cho mọi yêu cầu chức năng. Khi code mâu thuẫn với file này, file này đúng.
> Tham chiếu theo mã: FR-C (chung), FR-H (HR), FR-U (ứng viên).

Tài liệu này tổ chức lại toàn bộ yêu cầu chức năng theo 3 nhóm dựa trên đối tượng sử dụng: **Chức năng chung, Chức năng Nhà tuyển dụng (HR), và Chức năng Ứng viên (Candidate)**. Các mã yêu cầu được đánh số riêng theo từng nhóm: **FR-C (Common)**, **FR-H (HR)**, **FR-U (Ứng viên)**.

# 1. Chức năng chung (Common Functions)

*Các chức năng mà cả hai loại người dùng (HR và Ứng viên) đều sử dụng, hoặc là hạ tầng dùng chung phục vụ các chức năng chuyên biệt phía sau.*

| **Mã YC** | **Tên chức năng** | **Mô tả chi tiết** | **Actor** |
| --- | --- | --- | --- |
| **FR-C01** | **Quản lý Tài khoản ****&**** Phân quyền** | Hệ thống cung cấp cơ chế đăng ký, đăng nhập độc lập cho hai loại tài khoản: Nhà tuyển dụng (HR) và Ứng viên (Candidate). Xác thực áp dụng các giao thức bảo mật chuẩn (mật khẩu băm bcrypt/Argon2, phiên đăng nhập qua JWT/Session). Giao diện và quyền truy xuất API được giới hạn nghiêm ngặt theo vai trò người dùng (Role-Based Access Control). | HR, Ứng viên |
| **FR-C02** | **Duyệt Thông tin Công khai** | Cả hai loại tài khoản đều có thể xem các thông tin công khai trên nền tảng: tìm kiếm tin tuyển dụng, danh sách thông tin, bài đăng, chi tiết tin tuyển dụng đang mở, hồ sơ thương hiệu/thông tin doanh nghiệp. Không yêu cầu đăng nhập để xem, nhưng cần đăng nhập để thực hiện hành động (ứng tuyển, tạo tin...). | HR, Ứng viên |
| **FR-C03** | **Hệ thống Thông báo (Notification)** | Hệ thống tự động phát thông báo (qua giao diện web và email) đến người dùng liên quan khi có sự kiện thay đổi trạng thái: ứng viên nhận thông báo khi hồ sơ được đánh giá hoặc đổi trạng thái; HR nhận thông báo khi có ứng viên mới nộp đơn hoặc khi AI đã hoàn tất chấm điểm một đợt hồ sơ. | HR, Ứng viên |
| **FR-C04** | **AI Resume Parsing (Trích xuất CV)** | Ngay khi CV được ứng viên tải lên, hệ thống AI tự động đọc, xử lý cấu trúc văn bản (PDF/DOCX) và trích xuất thông tin thành dữ liệu có cấu trúc (JSON): thông tin liên hệ, học vấn, kinh nghiệm làm việc, kỹ năng, chứng chỉ, dự án cá nhân. Đây là dữ liệu nền dùng chung cho cả hai nhóm chức năng phía sau: Rubric Scoring (phía HR) và Job Recommendation / CV Improvement (phía Ứng viên). | Hệ thống (tự động, nền cho cả HR & Ứng viên) |

# 2. Chức năng Nhà tuyển dụng (HR)

*Các chức năng dành riêng cho tài khoản Nhà tuyển dụng, bao gồm cả các chức năng AI mà HR là bên tiêu thụ kết quả (chấm điểm, giải thích). Lưu ý ranh giới trách nhiệm giữa AI và Backend được nêu rõ ở cột "Nguyên tắc" của từng mục liên quan.*

| **Mã YC** | **Tên chức năng** | **Mô tả chi tiết** | **Actor** |
| --- | --- | --- | --- |
| **FR-H01** | **Quản lý Hồ sơ Doanh nghiệp** | HR tạo lập, cập nhật thông tin định danh doanh nghiệp: tên công ty, logo, mô tả quy mô, lĩnh vực hoạt động, thông tin liên hệ. Dữ liệu hiển thị công khai phục vụ tra cứu của ứng viên (liên kết FR-C02). | HR |
| **FR-H02** | **Quản lý Tin Tuyển dụng (Job Posting)** | HR tạo mới bài đăng tuyển dụng (tiêu đề, mô tả công việc, mức lương, địa điểm, loại hình làm việc), chỉnh sửa, tạm dừng hoặc xóa vĩnh viễn. Mỗi tin tuyển dụng phải được liên kết với một bộ tiêu chí đánh giá (Rubric – FR-H03). Khi đăng tin, HR đồng thời tạo Mẫu Giấy mời Phỏng vấn gắn với Job này, gồm các trường cố định: tên công ty, lời mời, người gửi, địa chỉ (ô ngày giờ phỏng vấn để trống, sẽ điền khi mời từng ứng viên cụ thể — xem FR-H07). | HR |
| **FR-H03** | **Thiết lập Rubric Tuyển dụng (Rubric Configuration)** | HR thêm các tiêu chí đánh giá cho từng Job và gán trọng số cho từng tiêu chí (tổng trọng số = 100%). HR có thể chỉnh sửa trọng số, xóa tiêu chí. HR không bắt buộc phải mô tả chi tiết thang điểm 1–5 cho từng tiêu chí; nếu không cung cấp, hệ thống áp dụng thang điểm mặc định dùng chung. HR có thể tùy chọn tự viết mô tả thang điểm riêng nếu muốn kiểm soát chặt chẽ hơn. **Nguyên tắc: ***HR quyết định tiêu chí và trọng số; các mức điểm cụ thể của ứng viên do AI đánh giá (FR-H04), không phải HR định nghĩa sẵn.* | HR |
| **FR-H04** | **AI Rubric Scoring (Chấm điểm từng tiêu chí)** | Dựa trên dữ liệu CV đã trích xuất (FR-C04) và bộ cấu hình Rubric (FR-H03), AI (LLM) đối chiếu, phân tích ngữ nghĩa và chấm điểm cho từng tiêu chí riêng lẻ, kèm minh chứng (evidence) trích từ CV. **Nguyên tắc: ***AI chỉ đánh giá và trả về điểm số của từng tiêu chí đơn lẻ. AI không tính tổng điểm và không xếp hạng ứng viên — việc này thuộc về FR-H05.* | HR (xem kết quả) — AI thực hiện |
| **FR-H05** | **Tổng hợp Điểm ****&**** Xếp hạng Ứng viên** | Backend tổng hợp điểm từng tiêu chí (FR-H04) theo đúng trọng số HR đã cấu hình (FR-H03) để tính tổng điểm, sau đó sắp xếp danh sách ứng viên của cùng một chiến dịch theo thứ tự tổng điểm từ cao xuống thấp. **Nguyên tắc: ***Đây là phép tính xác định (cộng theo trọng số + sắp xếp) do Backend thực hiện, không do AI. Hệ thống chỉ xếp hạng theo điểm, không phân loại hay gán nhãn Đạt/Cần xem xét/Không đạt — HR tự mở từng hồ sơ theo thứ tự và ra quyết định.* | HR (xem kết quả) — Backend thực hiện |
| **FR-H06** | **AI Explainable Scoring (Giải thích Điểm số)** | Với mỗi hồ sơ đã chấm điểm, AI tạo báo cáo giải thích bằng ngôn ngữ tự nhiên: lý do ứng viên đạt điểm cao/thấp ở từng tiêu chí, các tiêu chí đã đáp ứng, các tiêu chí còn thiếu hụt, tổng kết điểm mạnh và điểm yếu cốt lõi. **Nguyên tắc: ***Mọi giải thích phải có thể kiểm chứng dựa trên nội dung CV (trích dẫn evidence cụ thể), phục vụ yêu cầu minh bạch (Explainable AI) và tuân thủ nguyên tắc Human-in-the-loop.* | HR (xem kết quả) — AI thực hiện |
| **FR-H07** | **Quản lý Pipeline ****&**** Quyết định Tuyển dụng** | HR xem danh sách ứng viên đã được xếp hạng theo điểm (FR-H05), mở từng hồ sơ để xem điểm số và giải thích (FR-H06), rồi tự tay quyết định: (1) Mời phỏng vấn — hệ thống tự render Mẫu Giấy mời đã tạo ở FR-H02 kèm tên ứng viên, HR điền ngày giờ cụ thể và có thể chỉnh sửa nội dung trước khi gửi qua FR-C03; hoặc (2) Từ chối ngay từ vòng hồ sơ. Sau buổi phỏng vấn, HR quay lại xác nhận kết quả cuối: Trúng tuyển hoặc Bị từ chối. Trạng thái ứng tuyển gồm 5 giá trị: Chờ duyệt → Đã mời phỏng vấn (có lịch hẹn) → Trúng tuyển / Bị từ chối; Chờ duyệt → Bị từ chối; và Đã rút đơn (do ứng viên chủ động — xem FR-U06) có thể xảy ra ở bất kỳ giai đoạn nào trước khi có kết quả cuối. **Nguyên tắc: ***Không sử dụng AI để tự quyết định ứng viên đậu hay rớt, kể cả dưới hình thức gán nhãn phân loại. Hệ thống chỉ cung cấp điểm số, xếp hạng và giải thích; quyết định Mời phỏng vấn / Từ chối / Trúng tuyển luôn do HR trực tiếp thực hiện trên từng hồ sơ.* | HR |
| **FR-H08** | **Dashboard, Thống kê ****&**** Lịch sử Đánh giá** | Bảng điều khiển trực quan tổng hợp: tổng số hồ sơ, tỷ lệ chuyển đổi giữa các vòng, hiệu suất từng chiến dịch tuyển dụng. HR có thể lọc/sắp xếp danh sách ứng viên theo khoảng tổng điểm hoặc theo điểm của một tiêu chí cụ thể (VD: chỉ xem ứng viên có điểm Docker ≥ 8/10), để thu hẹp danh sách khi có nhiều hồ sơ. HR có thể truy xuất toàn bộ lịch sử đánh giá của AI đối với từng ứng viên để phục vụ đối chiếu, kiểm tra và tuân thủ (audit). | HR |

# 3. Chức năng Ứng viên (Candidate)

*Các chức năng dành riêng cho tài khoản Ứng viên, bao gồm các chức năng AI phục vụ trực tiếp ứng viên (gợi ý việc làm, gợi ý cải thiện CV).*

| **Mã YC** | **Tên chức năng** | **Mô tả chi tiết** | **Actor** |
| --- | --- | --- | --- |
| **FR-U01** | **Quản lý Hồ sơ Cá nhân ****&**** Upload CV** | Ứng viên quản lý thông tin nhân khẩu học cơ bản và tải lên, lưu trữ các phiên bản CV khác nhau dưới định dạng PDF hoặc DOCX. Việc upload sẽ tự động kích hoạt AI Resume Parsing (FR-C04) ở tầng nền. | Ứng viên |
| **FR-U02** | **Tìm kiếm ****&**** Ứng tuyển Việc làm** | Ứng viên tìm kiếm việc làm theo từ khóa, địa điểm hoặc danh mục, và gửi CV ứng tuyển vào một vị trí cụ thể. Hệ thống đảm bảo mỗi ứng viên chỉ có thể nộp một CV duy nhất cho một vị trí trong một chu kỳ tuyển dụng. Tại bước nộp đơn, ứng viên phải đánh dấu đồng ý (checkbox) cho phép CV được hệ thống AI phân tích và chấm điểm để hỗ trợ HR đánh giá; không đồng ý thì không thể hoàn tất ứng tuyển. | Ứng viên |
| **FR-U03** | **Theo dõi Trạng thái Ứng tuyển** | Ứng viên theo dõi sự dịch chuyển trạng thái hồ sơ theo thời gian thực, gồm 5 trạng thái: Chờ duyệt, Đã mời phỏng vấn (có lịch hẹn), Trúng tuyển, Bị từ chối, Đã rút đơn — và xem lại toàn bộ lịch sử ứng tuyển của bản thân. | Ứng viên |
| **FR-U04** | **AI Job Recommendation (Tư vấn Việc làm)** | Dựa trên dữ liệu CV đã trích xuất (FR-C04), hệ thống áp dụng semantic search (embedding + cosine similarity) để đo mức độ tương đồng giữa CV và các mô tả công việc, từ đó chủ động đề xuất trên bảng tin của ứng viên những vị trí phù hợp nhất với năng lực của họ. | Ứng viên |
| **FR-U05** | **AI CV Improvement (Gợi ý Cải thiện CV)** | AI phân tích nội dung CV hiện tại, đối chiếu với xu hướng kỹ năng của thị trường và kết quả đánh giá trước đó (nếu có), sau đó đưa ra gợi ý bổ sung từ khóa kỹ năng còn thiếu, chỉ ra đoạn văn cần chỉnh sửa, và đề xuất lộ trình học tập/chứng chỉ nên bổ sung. | Ứng viên |
| **FR-U06** | **Rút đơn Ứng tuyển** | Ứng viên có thể chủ động rút lại một đơn ứng tuyển đã nộp (VD: đã nhận việc nơi khác, đổi ý) ở bất kỳ giai đoạn nào trước khi có kết quả cuối cùng. Hệ thống chuyển trạng thái đơn sang "Đã rút đơn", không xóa dữ liệu hồ sơ/điểm số đã có, để đảm bảo tính chính xác của thống kê (FR-H08) và khả năng kiểm chứng lịch sử. **Nguyên tắc: ***Dùng đổi trạng thái (soft state), không dùng xóa cứng (hard delete) — giữ dấu vết cho audit và tránh sai lệch số liệu tỷ lệ từ chối.* | Ứng viên |

## Ghi chú tổng hợp về nguyên tắc thiết kế AI

- HR quyết định tiêu chí đánh giá và trọng số (FR-H03).

- AI chỉ chấm điểm và giải thích ở cấp độ từng tiêu chí (FR-H04, FR-H06).

- Backend tính tổng điểm và xếp hạng (FR-H05) bằng công thức tường minh (theo trọng số), có thể kiểm chứng không phân loại, không dùng ngưỡng, không gán nhãn Đạt/Không đạt.

- Mọi kết quả AI đều có thể kiểm chứng dựa trên nội dung CV (evidence).

- Không sử dụng AI để tự quyết định ứng viên đậu hay rớt, kể cả dưới hình thức gán nhãn phân loại; HR tự mở từng hồ sơ theo thứ tự xếp hạng và trực tiếp quyết định Mời phỏng vấn / Từ chối / Trúng tuyển (FR-H07).

- Vòng đời ứng tuyển gồm 5 trạng thái: Chờ duyệt, Đã mời phỏng vấn (có lịch hẹn), Trúng tuyển, Bị từ chối, Đã rút đơn (FR-H07, FR-U03, FR-U06).

- Ứng viên phải đồng ý (consent) cho CV được AI phân tích trước khi ứng tuyển (FR-U02).