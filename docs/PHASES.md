# Kế hoạch Phase & Branch

18 mã FR → 6 phase → 18 nhánh. **Một nhánh = một mã FR = một phiên làm việc.**

Thứ tự phase là thứ tự phụ thuộc dữ liệu, không phải thứ tự ưu tiên:
Phase D (AI chấm điểm) cần đơn ứng tuyển từ Phase C · Phase C cần tin tuyển dụng từ Phase B ·
Phase B cần tài khoản HR từ Phase A.

> **Sai lầm phổ biến nhất: làm phần AI trước vì nó thú vị nhất.** Không có `job_applications`
> và `rubric_criteria` trong DB thì FR-H04 không có dữ liệu vào và không có chỗ ghi kết quả ra.

Ký hiệu trong mỗi nhánh:
- **Backend / Frontend** — phạm vi việc
- **Bảng DB** — bảng được động tới (schema đã có sẵn từ `V1__init_schema.sql`, không tạo mới)
- **Xong khi** — tiêu chí nghiệm thu, phải kiểm chứng được
- **AI hay làm sai** — điều cần soi kỹ khi review diff

---

# PHASE A — Nền tảng tài khoản

Không có phase này thì không kiểm thử được bất kỳ chức năng nào phía sau.

## A1 · `feat/fr-c01-auth` — Tài khoản & Phân quyền (FR-C01)

**Backend**
- Entity `User`, `CandidateProfile`; repository tương ứng
- `POST /api/auth/register/candidate`, `POST /api/auth/register/hr`, `POST /api/auth/login`,
  `POST /api/auth/refresh`, `GET /api/auth/me`
- Mã hoá mật khẩu bằng BCrypt (Spring Security có sẵn `BCryptPasswordEncoder`)
- JWT: sinh token, filter xác thực, đọc `app.jwt.secret` từ config
- `SecurityFilterChain`: khai báo endpoint công khai, còn lại yêu cầu xác thực
- RBAC bằng `@PreAuthorize("hasRole('HR')")` ở tầng service hoặc controller

**Frontend**
- Trang đăng ký (2 tab: Ứng viên / Nhà tuyển dụng), trang đăng nhập
- Lưu token, axios interceptor gắn `Authorization: Bearer`
- `ProtectedRoute` phân theo role, chuyển hướng khi thiếu quyền

**Bảng DB** `users`, `candidate_profiles`

**Xong khi**
- Đăng ký ứng viên → đăng nhập → `GET /api/auth/me` trả đúng role `CANDIDATE`
- Tài khoản ứng viên gọi một endpoint HR bất kỳ → **403**, không phải 200 và cũng không phải 401
- Mật khẩu trong bảng `users` là chuỗi băm bắt đầu bằng `$2a$`, không phải plain text
- Ứng dụng khởi động được với `ddl-auto: validate` → entity khớp schema

**AI hay làm sai**
- Chỉ ẩn nút ở UI mà không chặn ở API. Phải test bằng cách gọi API trực tiếp.
- Đặt `permitAll()` quá rộng cho tiện phát triển rồi quên gỡ.
- Tự thêm cột vào entity không có trong schema → app không khởi động (đây là tính năng, không phải lỗi).
- Trả về `password_hash` trong response DTO.

## A2 · `feat/fr-c02-public-browse` — Duyệt thông tin công khai (FR-C02)

**Backend**
- `GET /api/public/jobs` (phân trang, lọc từ khoá / địa điểm / danh mục)
- `GET /api/public/jobs/{id}`, `GET /api/public/companies/{id}`
- Chỉ trả job có `status = OPEN` và `deleted_at IS NULL`
- Các endpoint này nằm trong danh sách `permitAll()`

**Frontend**
- Layout công khai: header + hero tìm kiếm + footer (theo `docs/UI_GUIDE.md`)
- Trang danh sách việc làm dùng card 2 cột, trang chi tiết, trang hồ sơ doanh nghiệp
- Nút "Ứng tuyển" hiện nhưng bấm vào thì chuyển sang trang đăng nhập nếu chưa login

**Bảng DB** `jobs`, `companies` (chỉ đọc)

**Xong khi**
- Mở trang danh sách ở cửa sổ ẩn danh (chưa đăng nhập) → thấy được tin
- Job `DRAFT` / `PAUSED` / `CLOSED` không xuất hiện trong API công khai
- Giao diện dùng token màu, không hardcode mã hex

**AI hay làm sai**
- Trả cả job `DRAFT` vì quên điều kiện lọc.
- Trả kèm thông tin nhạy cảm của HR (email nội bộ, id người tạo).
- Bỏ phân trang, `SELECT` toàn bộ bảng.

---

# PHASE B — HR dựng chiến dịch tuyển dụng

## B1 · `feat/fr-h01-company` — Hồ sơ doanh nghiệp (FR-H01)

**Backend** CRUD `Company`; mỗi HR sở hữu một công ty (`owner_id`); upload logo.
**Frontend** Form hồ sơ công ty trong layout quản trị (sidebar trái, không hero).
**Bảng DB** `companies`

**Xong khi** HR A không sửa được công ty của HR B (kiểm tra bằng cách gọi API với id của người khác → 403).

**AI hay làm sai** Kiểm tra quyền bằng `@PreAuthorize("hasRole('HR')")` là chưa đủ — cần kiểm tra
**quyền sở hữu bản ghi**, không chỉ vai trò.

## B2 · `feat/fr-h02-jobs` — Tin tuyển dụng & Mẫu giấy mời (FR-H02)

**Backend**
- CRUD `Job`: tạo, sửa, đổi `status` (DRAFT/OPEN/PAUSED/CLOSED), xoá mềm qua `deleted_at`
- Mỗi Job bắt buộc có một Rubric (tạo rubric rỗng cùng lúc với job, điền tiêu chí ở B3)
- `InterviewTemplate` gắn 1-1 với Job: tên công ty, lời mời, người gửi, địa chỉ
- **Ô ngày giờ phỏng vấn để trống trong template** — điền khi mời từng ứng viên ở E1

**Frontend** Danh sách tin (bảng, lọc theo trạng thái), form tạo/sửa nhiều bước, form mẫu giấy mời.

**Bảng DB** `jobs`, `interview_templates`, `rubrics`

**Xong khi**
- Không tạo được Job mà không có Rubric đi kèm
- Xoá Job không làm mất bản ghi (kiểm tra `deleted_at` được set, hàng vẫn còn)
- Tăng `recruitment_cycle` khi mở lại tuyển dụng cùng vị trí

**AI hay làm sai** Dùng `DELETE FROM jobs` thay vì set `deleted_at`. Bỏ qua `recruitment_cycle` vì
chưa thấy nó dùng ở đâu — nhưng C2 phụ thuộc trực tiếp vào nó.

## B3 · `feat/fr-h03-rubric` — Cấu hình Rubric (FR-H03)
- B3 phải chặn DRAFT → OPEN khi rubric chưa đủ 100% trọng số, và E1 đối chiếu template.company_name với tên công ty hiện tại trước khi gửi giấy mời.
**Backend**
- CRUD `RubricCriterion`: tên, mô tả, trọng số, `max_score` (mặc định 5), thứ tự hiển thị
- **Validate tổng trọng số = 100%** ở tầng service, kèm thông báo lỗi rõ ràng
  (DB đã có trigger chặn > 100%, nhưng người dùng cần thông báo dễ hiểu chứ không phải lỗi SQL)
- `scale_description` là tuỳ chọn: NULL → dùng thang mặc định dùng chung
- Khoá rubric (`is_locked = true`) khi đã có lượt chấm đầu tiên

**Frontend** Màn hình rubric: thêm/xoá tiêu chí, kéo thả đổi thứ tự, hiển thị **tổng trọng số hiện tại**
theo thời gian thực, nút Lưu disabled khi tổng ≠ 100%.

**Bảng DB** `rubrics`, `rubric_criteria`

**Xong khi**
- Lưu rubric có tổng 95% hoặc 105% → bị chặn ở cả UI lẫn API
- Không mô tả thang điểm → hệ thống vẫn chấm được bằng thang mặc định
- Rubric đã khoá thì không sửa được trọng số

**AI hay làm sai**
- Bắt buộc HR nhập mô tả thang 1–5 cho từng tiêu chí. **SRS nói rõ đây là tuỳ chọn.**
- Chỉ validate ở frontend.
- Cho phép sửa trọng số sau khi đã chấm mà không cảnh báo → hỏng lịch sử đánh giá.

---

# PHASE C — Ứng viên nộp đơn

## C1 · `feat/fr-u01-resume` — Hồ sơ cá nhân & Upload CV (FR-U01)

**Backend**
- CRUD `CandidateProfile`
- Upload CV: chỉ nhận PDF/DOCX, giới hạn 10MB, kiểm tra magic bytes chứ không chỉ đuôi file
- Lưu file vào MinIO (hoặc thư mục local ở dev), DB chỉ lưu đường dẫn
- Nhiều phiên bản CV, đánh dấu một bản là `is_primary`
- Tạo bản ghi `resumes` với `parse_status = PENDING` (D1 sẽ xử lý)

**Frontend** Trang hồ sơ, khu vực kéo thả upload, danh sách CV kèm trạng thái xử lý.

**Bảng DB** `resumes`

**Xong khi**
- Upload file `.exe` đổi tên thành `.pdf` → bị từ chối
- File CV **không** nằm trong thư mục repo (kiểm tra `git status` sau khi upload phải sạch)
- Bản ghi có `parse_status = PENDING`

**AI hay làm sai** Lưu file vào thư mục project rồi commit lên Git. Lưu nội dung file vào cột DB.
Tin vào đuôi file để xác định định dạng.

## C2 · `feat/fr-u02-apply` — Tìm kiếm & Ứng tuyển (FR-U02)

**Backend**
- Tìm kiếm job (dùng lại API công khai của A2, thêm bộ lọc)
- `POST /api/applications`: tạo `JobApplication` với `recruitment_cycle` lấy từ Job hiện tại
- **`ai_consent` bắt buộc `true`, lưu `ai_consent_at`** — DB có `chk_consent_true` chặn sẵn
- Bắt `DataIntegrityViolationException` từ `uq_application_per_cycle` → trả lỗi 409 dễ hiểu

**Frontend**
- Form ứng tuyển: chọn CV đã upload, thư giới thiệu (tuỳ chọn)
- **Checkbox consent không tick sẵn, nút Nộp đơn disabled cho tới khi tick**
- Nội dung consent ghi rõ CV sẽ được AI phân tích và chấm điểm để hỗ trợ HR đánh giá

**Bảng DB** `job_applications`

**Xong khi**
- Nộp lần hai cho cùng job cùng chu kỳ → 409, và ràng buộc DB là thứ chặn cuối cùng
- Bỏ tick consent → không gửi được request
- Gọi API trực tiếp với `ai_consent: false` → bị DB từ chối

**AI hay làm sai**
- Tick sẵn checkbox consent cho "tiện người dùng". Đây là vi phạm nguyên tắc SRS.
- Chỉ kiểm tra trùng bằng `SELECT` trước khi `INSERT` — có race condition, ràng buộc DB mới là chốt chặn.

## C3 · `feat/fr-u03-tracking` — Theo dõi trạng thái (FR-U03)

**Backend**
- `GET /api/applications/my` — danh sách đơn của chính mình
- `GET /api/applications/{id}/history` — lịch sử chuyển trạng thái
- Service ghi `application_status_history` **mỗi lần** đổi trạng thái (tập trung một chỗ, không rải rác)

**Frontend** Trang "Đơn ứng tuyển của tôi", badge 5 trạng thái theo bảng màu trong `UI_GUIDE.md`,
timeline lịch sử.

**Bảng DB** `job_applications`, `application_status_history`

**Xong khi** Mọi thay đổi trạng thái đều sinh một dòng lịch sử, kể cả thay đổi do hệ thống
(`changed_by = NULL`).

**AI hay làm sai** Dùng màu đỏ/xanh gợi ý tốt–xấu cho trạng thái. Đây là badge trạng thái, không phải
đánh giá — dùng bảng màu trung tính đã định nghĩa sẵn.

## C4 · `feat/fr-u06-withdraw` — Rút đơn (FR-U06)

**Backend** `PATCH /api/applications/{id}/withdraw` → chuyển sang `WITHDRAWN`.
Chỉ cho rút khi trạng thái hiện tại là `PENDING` hoặc `INTERVIEW_INVITED`.

**Frontend** Nút "Rút đơn" + hộp thoại xác nhận nêu rõ hành động không hoàn tác.

**Bảng DB** `job_applications`, `application_status_history`

**Xong khi**
- Sau khi rút, bản ghi vẫn còn, điểm số đã chấm vẫn còn
- Không rút được đơn đã `HIRED` hoặc `REJECTED`

**AI hay làm sai** Dùng `DELETE`. SRS nói rõ: soft state, không hard delete — vì `FR-H08` cần số liệu
đầy đủ để tính tỷ lệ chuyển đổi.

---

# PHASE D — AI đọc và chấm

Đây là phần lõi của đồ án và cũng là phần bạn cần hiểu sâu nhất khi bảo vệ.

## D1 · `feat/fr-c04-parsing` — AI Resume Parsing (FR-C04)

**Backend**
- Trích text: PDFBox cho PDF, POI cho DOCX
- Prompt parsing đặt trong `ai/prompt/`, có đánh version (ví dụ `resume-parse-v1.st`)
- Output JSON theo schema cố định: `contact`, `education[]`, `experience[]`, `skills[]`,
  `certifications[]`, `projects[]`
- Validate bằng Bean Validation / `BeanOutputConverter`; parse fail → retry 1 lần → `parse_status = FAILED`
- Chạy nền: `@Scheduled` poller quét `resumes` có `parse_status = PENDING`, không chặn request upload
- Lưu cả `raw_text` lẫn `data` (JSONB), kèm `model` và `prompt_version`

**Frontend** Hiển thị trạng thái xử lý trên danh sách CV; xem dữ liệu đã trích xuất.

**Bảng DB** `resumes`, `resume_parsed_data`

**Xong khi**
- Test với **5 CV thật khác dạng**: 1 cột, 2 cột, tiếng Việt, tiếng Anh, bản scan
- Upload xong vài giây sau `parse_status` chuyển `DONE` và `data` có JSON đúng schema
- CV hỏng không làm sập ứng dụng, chỉ set `FAILED` kèm `parse_error`

**AI hay làm sai**
- Gọi LLM đồng bộ ngay trong request upload → người dùng chờ 30 giây.
- Không validate output, giả định LLM luôn trả JSON hợp lệ.
- Hardcode prompt giữa business logic thay vì tách ra file riêng.

## D2 · `feat/fr-h04-scoring` — AI chấm từng tiêu chí (FR-H04)

**Backend**
- Với mỗi đơn ứng tuyển: tạo `scoring_runs` và **chụp ảnh rubric vào `rubric_snapshot`**
- **Gọi LLM riêng cho TỪNG tiêu chí**, không gộp một lần trả cả bảng điểm
- Input mỗi lần gọi: CV JSON + đúng một tiêu chí + thang điểm (riêng hoặc mặc định)
- Output: `score` + `reasoning` + `evidence[]` (trích dẫn nguyên văn kèm vị trí trong CV)
- Ghi `criterion_scores` với `weight_snapshot`, `max_score_snapshot`, `criterion_name_snapshot`
- Chạy nền qua poller, cập nhật `scoring_runs.status`

**Frontend** Nút "Chấm điểm hồ sơ" cho HR, hiển thị tiến độ.

**Bảng DB** `scoring_runs`, `criterion_scores`

**Xong khi**
- Mỗi tiêu chí có ít nhất một evidence trích từ CV, không có evidence bịa
- `scoring_runs.total_score` vẫn **NULL** sau bước này — D3 mới tính
- Sửa trọng số rubric sau khi chấm không làm thay đổi `weight_snapshot` đã lưu

**AI hay làm sai** — soi kỹ nhất ở nhánh này
- Gộp tất cả tiêu chí vào một lần gọi LLM để "tiết kiệm token". Vi phạm nguyên tắc FR-H04
  và làm chất lượng chấm giảm rõ rệt.
- Để LLM trả luôn `total_score`. **Cột đó không được LLM ghi vào.**
- Bỏ `evidence` vì thấy phiền, hoặc để LLM tự diễn giải thay vì trích nguyên văn.
- Coi `weight_snapshot` là dữ liệu thừa và đọc thẳng từ `rubric_criteria`.

## D3 · `feat/fr-h05-aggregate` — Tổng hợp điểm & Xếp hạng (FR-H05)

**Backend — Java thuần, KHÔNG gọi LLM**
- `ScoreAggregator`: `total = Σ (score_i / max_score_i × weight_i)`, chuẩn hoá về thang 100
- Ghi `scoring_runs.total_score`, đổi `status = DONE`
- `GET /api/jobs/{id}/candidates?sort=total_score,desc` — danh sách xếp hạng
- **Unit test bắt buộc**: trọng số bình thường, tiêu chí điểm 0, số lẻ làm tròn, tiêu chí bị xoá

**Frontend** Bảng xếp hạng: thứ hạng, tổng điểm, điểm từng tiêu chí dạng cột.

**Bảng DB** `scoring_runs`, `criterion_scores`

**Xong khi**
- Unit test pass, có test cho ít nhất 4 trường hợp biên
- Đổi trọng số → chấm lại → thứ hạng đổi đúng theo công thức
- Không có cột/field nào tên `verdict`, `label`, `isQualified`, `passed` trong toàn bộ code

**AI hay làm sai**
- Nhờ LLM tính tổng "cho nhanh". Đây là phép cộng có trọng số, phải là code xác định kiểm chứng được.
- Thêm ngưỡng phân loại (≥80 = Phù hợp cao). **Vi phạm trực tiếp FR-H05 và FR-H07.**
- Tô màu ô điểm theo thang đỏ–vàng–xanh. Đây là phán quyết trá hình.

## D4 · `feat/fr-h06-explain` — Giải thích điểm số (FR-H06)

**Backend**
- Sinh báo cáo: tóm tắt, điểm mạnh, điểm yếu, tiêu chí đã đáp ứng, tiêu chí còn thiếu
- Mọi luận điểm phải dẫn được về evidence đã lưu ở D2 — **không sinh evidence mới**
- Lưu `score_explanations` kèm `model` và `prompt_version`

**Frontend**
- Mỗi tiêu chí là một khối gập/mở; mở ra thấy đoạn trích nguyên văn từ CV
- Trích dẫn hiển thị viền trái màu brand, nền `brand-light`
- **Không điểm nào hiển thị mà không mở ra được evidence**

**Bảng DB** `score_explanations`

**Xong khi** Mở bất kỳ tiêu chí nào cũng thấy được câu trích từ CV giải thích cho điểm đó.

**AI hay làm sai** Viết giải thích chung chung ("ứng viên có kinh nghiệm phù hợp") không dẫn evidence.
Kết luận nên tuyển hay không — nằm ngoài phạm vi FR-H06.

---

# PHASE E — Quyết định & thông báo

## E1 · `feat/fr-h07-pipeline` — Pipeline & Quyết định tuyển dụng (FR-H07)

**Backend**
- `PATCH /api/applications/{id}/status` với máy trạng thái tường minh:
  `PENDING → INTERVIEW_INVITED | REJECTED` · `INTERVIEW_INVITED → HIRED | REJECTED` ·
  `* → WITHDRAWN` (chỉ ứng viên thực hiện, trước khi có kết quả cuối)
- Chuyển trạng thái sai luồng → 400
- Mời phỏng vấn: render `interview_templates` + tên ứng viên → HR điền ngày giờ, sửa nội dung →
  lưu `interview_invitations` (lưu nguyên văn đã gửi) → phát thông báo

**Frontend**
- Màn hình pipeline: danh sách đã xếp hạng → mở hồ sơ → xem điểm + giải thích → 2 nút hành động
- Hộp thoại mời phỏng vấn: nội dung đã render, ô ngày giờ, cho phép sửa trước khi gửi

**Bảng DB** `job_applications`, `application_status_history`, `interview_invitations`

**Xong khi**
- Không tồn tại bất kỳ đường code nào tự động chuyển trạng thái đậu/rớt
- Mọi chuyển trạng thái đều do một hành động của HR (hoặc ứng viên với `WITHDRAWN`)
- Chuyển sai luồng bị chặn ở backend, không chỉ ẩn nút

**AI hay làm sai** — soi kỹ nhất ở nhánh này
- Thêm "tự động từ chối ứng viên dưới ngưỡng điểm" như một tiện ích. **Cấm tuyệt đối.**
- Gợi ý hành động dựa trên điểm ("Nên mời phỏng vấn"). Đây là gán nhãn trá hình.
- Cho phép nhảy thẳng `PENDING → HIRED`.

## E2 · `feat/fr-c03-notification` — Hệ thống thông báo (FR-C03)

**Backend**
- Spring Events: publish sự kiện khi đổi trạng thái, khi chấm điểm xong, khi có đơn mới
- Listener ghi `notifications` + gửi email qua Spring Mail (MailHog ở dev)
- `GET /api/notifications`, `PATCH /api/notifications/{id}/read`
- Gửi email thất bại → `email_status = FAILED`, không làm hỏng nghiệp vụ chính

**Frontend** Chuông thông báo có badge số chưa đọc, dropdown danh sách, trang xem tất cả.

**Bảng DB** `notifications`

**Xong khi**
- Đổi trạng thái đơn → ứng viên nhận thông báo web, email hiện ở http://localhost:8025
- HR nhận thông báo khi có đơn mới và khi chấm điểm xong một đợt
- Tắt MailHog → nghiệp vụ vẫn chạy, chỉ `email_status = FAILED`

**AI hay làm sai** Gửi email đồng bộ trong transaction chính → SMTP chậm làm treo request, hoặc lỗi
email làm rollback cả việc đổi trạng thái.

---

# PHASE F — Gợi ý & thống kê

## F1 · `feat/fr-u04-recommend` — Gợi ý việc làm (FR-U04)

**Backend**
- Sinh embedding cho JD khi job chuyển `OPEN` → lưu `job_embeddings`
- Sinh embedding cho CV sau khi parse xong → lưu `resume_parsed_data.embedding`
- Truy vấn cosine similarity qua pgvector (index HNSW đã tạo sẵn)
- Cache kết quả vào `job_recommendations`, làm mới định kỳ

**Frontend** Khối "Việc làm phù hợp với bạn" trên bảng tin ứng viên.

**Bảng DB** `job_embeddings`, `resume_parsed_data`, `job_recommendations`

**Xong khi** Ứng viên ngành IT không nhận gợi ý việc kế toán. Truy vấn dùng index vector, không quét toàn bảng.

**AI hay làm sai** Sinh embedding mỗi lần load trang (tốn tiền và chậm). Dùng số chiều khác 1536
mà không sửa schema → lỗi runtime.

## F2 · `feat/fr-u05-cv-improve` — Gợi ý cải thiện CV (FR-U05)

**Backend** Phân tích CV + kết quả đánh giá trước đó (nếu có) → từ khoá kỹ năng còn thiếu,
đoạn cần chỉnh sửa, lộ trình học tập/chứng chỉ. Lưu `cv_improvement_suggestions`.

**Frontend** Trang gợi ý, chia 3 khối theo 3 loại đề xuất.

**Bảng DB** `cv_improvement_suggestions`

**Xong khi** Gợi ý cụ thể và hành động được, không phải lời khuyên chung chung.

**AI hay làm sai** Tiết lộ điểm số hoặc nhận xét nội bộ của HR cho ứng viên. Ứng viên chỉ được thấy
gợi ý cải thiện, không thấy rubric hay điểm chi tiết.

## F3 · `feat/fr-h08-dashboard` — Dashboard & Lịch sử đánh giá (FR-H08)

**Backend**
- Thống kê: tổng hồ sơ, tỷ lệ chuyển đổi giữa các vòng, hiệu suất từng chiến dịch
- Lọc theo khoảng tổng điểm **hoặc theo điểm của một tiêu chí cụ thể**
  (index `idx_criterion_scores_filter` đã tạo sẵn cho việc này)
- Tra cứu toàn bộ lịch sử đánh giá AI của một ứng viên (phục vụ audit)

**Frontend** Dashboard với Recharts, bộ lọc nâng cao, trang lịch sử đánh giá.

**Bảng DB** `scoring_runs`, `criterion_scores`, `job_applications`, `application_status_history`

**Xong khi**
- Đơn `WITHDRAWN` vẫn được đếm đúng trong thống kê
- Lọc được "ứng viên có điểm tiêu chí Docker ≥ 4/5"
- Xem được mọi lượt chấm của một ứng viên kèm `model` và `prompt_version` từng lượt

**AI hay làm sai** Loại đơn `WITHDRAWN` khỏi thống kê → sai tỷ lệ từ chối, đúng thứ FR-U06 muốn tránh.

---

# Sau Phase F

Không phải nhánh tính năng, nhưng cần trước khi bảo vệ:

- `chore/hardening` — rate limit, xử lý lỗi LLM (timeout, hết quota), theo dõi chi phí token,
  bảo mật file CV (presigned URL, bucket private)
- `chore/seed-demo` — script sinh dữ liệu demo: 1 HR, 2 job có rubric, 8 ứng viên với CV thật
- `docs/final` — README hoàn chỉnh, kịch bản demo cho buổi bảo vệ, sơ đồ ER xuất từ database thật

---

# Bảng tra nhanh

| # | Nhánh | FR | Phụ thuộc |
|---|---|---|---|
| A1 | `feat/fr-c01-auth` | FR-C01 | — |
| A2 | `feat/fr-c02-public-browse` | FR-C02 | A1 |
| B1 | `feat/fr-h01-company` | FR-H01 | A1 |
| B2 | `feat/fr-h02-jobs` | FR-H02 | B1 |
| B3 | `feat/fr-h03-rubric` | FR-H03 | B2 |
| C1 | `feat/fr-u01-resume` | FR-U01 | A1 |
| C2 | `feat/fr-u02-apply` | FR-U02 | B2, C1 |
| C3 | `feat/fr-u03-tracking` | FR-U03 | C2 |
| C4 | `feat/fr-u06-withdraw` | FR-U06 | C3 |
| D1 | `feat/fr-c04-parsing` | FR-C04 | C1 |
| D2 | `feat/fr-h04-scoring` | FR-H04 | B3, C2, D1 |
| D3 | `feat/fr-h05-aggregate` | FR-H05 | D2 |
| D4 | `feat/fr-h06-explain` | FR-H06 | D2 |
| E1 | `feat/fr-h07-pipeline` | FR-H07 | D3, D4 |
| E2 | `feat/fr-c03-notification` | FR-C03 | E1 |
| F1 | `feat/fr-u04-recommend` | FR-U04 | D1 |
| F2 | `feat/fr-u05-cv-improve` | FR-U05 | D1 |
| F3 | `feat/fr-h08-dashboard` | FR-H08 | E1 |