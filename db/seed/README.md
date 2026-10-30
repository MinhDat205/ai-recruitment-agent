# db/seed — Dữ liệu demo và dữ liệu test

Thư mục có **hai bộ dữ liệu độc lập, hai vai trò** (`chore/seed-final`, K1 Q1):

| Bộ | Dùng cho | File | Nạp khi demo cho hội đồng? |
|---|---|---|---|
| **Demo** | Câu chuyện liền mạch cho hội đồng: 2 công ty, 12 tin OPEN, 9 ứng viên, điểm và giải thích do AI thật sinh | 5 tầng: 1 → 2 → 1b → 2b → 3 (mục 1) | **Có** |
| **Test** | Ma trận trạng thái để soát giao diện (mỗi màn hình đủ mọi trạng thái) | `seed-test.sql` (mục 8) | **Không** |

Bộ demo chia **năm tầng**, nạp đúng thứ tự:

- **Tầng 1** (`seed-demo-structural.sql`) — dữ liệu cấu trúc thuần, không phụ thuộc
  AI: HR, candidate, company, job, rubric, tiêu chí, mẫu giấy mời phỏng vấn. SQL
  thuần, idempotent (`ON CONFLICT (id) DO NOTHING`), viết tay, chạy lại vô hạn lần.
- **Tầng 2** (`seed-demo-ai-output.sql`) — output thật của pipeline AI (parse CV,
  embedding, chấm điểm, giải thích, gợi ý cải thiện CV) cộng với các
  bảng phụ thuộc thời điểm chạy thật (`resumes`, `job_applications`,
  `application_status_history`, `notifications`...). Sinh **một lần** bằng cách chạy
  ứng dụng thật qua UI, rồi xuất bằng `pg_dump` (script `export-ai-output.ps1`) và
  commit thẳng vào repo — **không phải SQL viết tay**, và **không idempotent**.
- **Tầng 1b** (`seed-demo-structural-1b.sql`, chore/seed-final) — dữ liệu cấu trúc viết tay, không phụ thuộc
  AI: công ty demo thứ hai + 6 tin OPEN, logo hai công ty, đơn tầng 2 chuyển sang đủ trạng thái pipeline
  (mời phỏng vấn, tuyển, từ chối) kèm lịch sử, giấy mời nhiều khung giờ, câu hỏi sàng lọc, kho ứng viên,
  ẩn danh Job Java, tin nhắn có đính kèm, thông báo. Idempotent khi **chưa** nạp 2b (xem mục 9, L11).
- **Tầng 2b** (`seed-demo-ai-output-2b.sql`, chore/seed-final) — output AI thật của **một** lượt chạy
  (19/10/2026) trên DB chỉ có demo: embedding 6 tin công ty 2 + 4 hồ sơ, lượt chấm + giải thích đơn
  Trần Minh Hoàng → Kỹ sư DevOps (FR-H14), 2 CV dựng bằng công cụ của Bùi Ngọc Mai (FR-U12). Sinh bằng
  `export-demo-ai-2b.ps1` (mục 10), không sửa tay.
- **Tầng 3** (`seed-demo-structural-3.sql`, FR-U16) — dữ liệu cấu trúc viết tay, không phụ thuộc AI: một tin của công
  ty Hoàn Mỹ đã **hết hạn nộp** ("Nhân viên Hỗ trợ kỹ thuật (IT Helpdesk)", `OPEN`, hạn `CURRENT_DATE - 1` lúc nạp) và
  **4 tin đã lưu** của Phạm Quốc Bảo (tin quá hạn + 3 tin đang mở của công ty Ngân Hà). Idempotent: đầu file xoá dải
  của mình rồi chèn lại, chạy lại bao nhiêu lần cũng được (đã thử hai lần liên tiếp, 30/10/2026). FR-C09 thêm phần của
  mình vào cùng file.

Lý do tách tầng và vì sao tầng 2/2b không viết tay: `docs/walkthrough/chore-seed-demo.md` mục "Quyết
định thiết kế" và `docs/walkthrough/chore-seed-final.md` (K1, K5).

### Dải id

| Dải | Bộ · tầng | Ghi chú |
|---|---|---|
| `d0`–`d6` (`d0000000-…`, nhóm 4 = `0000`) | Demo · tầng 1 | Người dùng, công ty, tin, rubric, hồ sơ… |
| UUID ngẫu nhiên | Demo · tầng 2 và 2b | Do app sinh trong lượt chạy thật |
| `d1b00000-TTTT-0000-0000-NNNNNNNNNNNN` | Demo · tầng 1b | `TTTT` = mã bảng, bảng mã ở `chore-seed-final.md` mục K2 |
| `d3a00000-TTTT-0000-0000-NNNNNNNNNNNN` | Demo · tầng 3 | Cùng bảng mã `TTTT`, thêm `0020` = `saved_jobs`. **Không** dùng `d3000000-`: đó là tiền tố 6 rubric của tầng 1 (FR-U16 B10) |
| `e0`–`ef` (`e0000000-…`), `f0000000-…`, `f1000000-…` | Test | Bộ test gốc |
| `f2000000-TTTT-0000-0000-NNNNNNNNNNNN` | Test · mục 17 | Công ty test thứ hai + HR + 2 ứng viên; cùng bảng mã `TTTT` |

Đếm hay dọn theo dải phải dùng tiền tố đủ 8 ký tự (vd `LIKE 'd1b00000-%'`, `LIKE 'd3a00000-%'`, `~ '^e[0-9a-f]000000-0000-0000-0000-'`),
xem mục 9.

---

## 1. Thứ tự chạy BẮT BUỘC — không được đảo

**Tiền đề (một lần): DB phải ở V21 trở lên.** `reset-demo-db.sql` liệt kê tường minh cả 32 bảng
nghiệp vụ đến V21 (`application_reconsideration_requests`, FR-C09); DB còn ở V20 thì `TRUNCATE` báo
`relation ... does not exist` và dừng, không xoá gì. Tầng 1b chấp nhận V19 trở lên, tầng 3 cần V20 trở lên (chốt chặn
đầu file; trước FR-U16 tầng 1b đòi **đúng** V19 nên chuỗi nạp từ DB trống dừng ở 1b sau khi có V20 — B11). DB trống (vd sau `docker compose down -v`) cũng
cần bước này. Khởi động backend một lần với mọi scheduler gọi AI **tắt**, chờ dòng log
`Started BackendApplication` (Flyway đã chạy xong), rồi dừng backend (Ctrl+C):

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--app.job-embedding.enabled=false --app.candidate-profile-embedding.enabled=false --app.resume-embedding.enabled=false --app.resume-parsing.enabled=false --app.scoring.enabled=false --app.explanation.enabled=false --app.cv-improvement.enabled=false --app.resume-reparse.enabled=false"
cd ..
```

**Nạp bộ demo** (từ gốc repo, PowerShell 5.1):

```powershell
# 1. Dọn sạch dữ liệu cũ (BẮT BUỘC truyền biến xác nhận, xem mục 2 để hiểu vì sao)
docker compose cp db\seed\reset-demo-db.sql postgres:/tmp/reset-demo-db.sql
docker compose exec -T postgres psql -U recruitment -d recruitment -v DEMO_SEED_CONFIRM=YES_RESET_DEMO_DB -f /tmp/reset-demo-db.sql

# 2. Tầng 1 — dữ liệu cấu trúc (HR, company, job, rubric, candidate)
docker compose cp db\seed\seed-demo-structural.sql postgres:/tmp/seed-demo-structural.sql
docker compose exec -T postgres psql -U recruitment -d recruitment -v ON_ERROR_STOP=1 -f /tmp/seed-demo-structural.sql

# 3. Tầng 2 — output AI thật đã đông cứng (parse, embedding, chấm điểm, giải thích...)
docker compose cp db\seed\seed-demo-ai-output.sql postgres:/tmp/seed-demo-ai-output.sql
docker compose exec -T postgres psql -U recruitment -d recruitment -v ON_ERROR_STOP=1 -f /tmp/seed-demo-ai-output.sql

# 4. Tầng 1b — công ty 2, trạng thái pipeline, giấy mời, kho, ẩn danh, tin nhắn...
docker compose cp db\seed\seed-demo-structural-1b.sql postgres:/tmp/seed-demo-structural-1b.sql
docker compose exec -T postgres psql -U recruitment -d recruitment -v ON_ERROR_STOP=1 -f /tmp/seed-demo-structural-1b.sql

# 5. Tầng 2b — output AI thật của lượt chạy chore/seed-final
docker compose cp db\seed\seed-demo-ai-output-2b.sql postgres:/tmp/seed-demo-ai-output-2b.sql
docker compose exec -T postgres psql -U recruitment -d recruitment -v ON_ERROR_STOP=1 -f /tmp/seed-demo-ai-output-2b.sql

# 5b. Tầng 3 — tin đã lưu (FR-U16): một tin hết hạn nộp + 4 tin đã lưu của Phạm Quốc Bảo
docker compose cp db\seed\seed-demo-structural-3.sql postgres:/tmp/seed-demo-structural-3.sql
docker compose exec -T postgres psql -U recruitment -d recruitment -v ON_ERROR_STOP=1 -f /tmp/seed-demo-structural-3.sql

# 6. Cài file (CV PDF, logo, tệp đính kèm) vào đúng chỗ backend đọc được (xem mục 5)
.\db\seed\install-demo-files.ps1
```

`-v ON_ERROR_STOP=1` buộc psql dừng ở lỗi đầu tiên và thoát mã khác 0 (tầng 1b, 2b, 3 còn bọc cả file trong
một transaction nên lỗi thì không ghi gì). Bỏ qua bước 6 thì DB vẫn đúng, nhưng nút "Xem CV gốc" (FR-H06),
logo và tệp đính kèm sẽ báo lỗi vì file không có trên đĩa. Chạy bước 6 mà chưa nạp dữ liệu demo sẽ báo lỗi
ngay, không âm thầm sai.

**Nạp thêm bộ test** (chỉ để soát giao diện, KHÔNG khi demo cho hội đồng): sau khối trên, chạy khối ở
mục 8.1. Dọn riêng bộ test: mục 8.2.

---

## 2. Cách nạp file trên Windows — TUYỆT ĐỐI không dùng `Get-Content | docker compose exec`

Luôn dùng `docker compose cp` để đưa file vào container, rồi `psql -f` đọc file **từ
bên trong container**, đúng như 3 lệnh ở mục 1. **Không** làm theo kiểu:

```powershell
# SAI — ĐỪNG LÀM THẾ NÀY
Get-Content db\seed\seed-demo-structural.sql | docker compose exec -T postgres psql -U recruitment -d recruitment
```

Đây không phải cảnh báo lý thuyết — lỗi này đã xảy ra thật trong dự án: seed chạy
xong không báo lỗi gì, nhưng mọi tên job, tên người có dấu tiếng Việt đều thành dấu
hỏi (`?`). Nguyên nhân: `Get-Content` đọc file thành chuỗi .NET, PowerShell 5.1 sau
đó mã hoá lại chuỗi đó theo **code page của console** (thường không phải UTF-8) khi
ghi vào pipe cho tiến trình con — dấu tiếng Việt bị phá huỷ ở đúng bước trung gian
này, trước khi psql kịp nhận byte nào. `docker compose cp` sao chép **nguyên byte**
của file, không đi qua bước diễn giải chuỗi nào của PowerShell, nên luôn an toàn.

---

## 3. `seed-demo-ai-output.sql` KHÔNG idempotent — chạy lần hai sẽ vỡ

Khác với hai file SQL kia (đều dùng `ON CONFLICT (id) DO NOTHING`), `seed-demo-ai-output.sql`
do `pg_dump` sinh ra tự động, chỉ chứa `INSERT` thuần — không có cơ chế bỏ qua bản
ghi trùng. Chạy lần thứ hai trên cùng một DB sẽ báo lỗi vi phạm khoá chính
(`duplicate key value violates unique constraint`) và dừng giữa chừng.

Muốn nạp lại: chạy lại **từ bước 1** (`reset-demo-db.sql`) để dọn sạch trước, không
chạy riêng lẻ `seed-demo-ai-output.sql` lần hai.

Tầng 2b (`seed-demo-ai-output-2b.sql`) dùng `INSERT … ON CONFLICT DO NOTHING` cho dòng mới và `UPDATE` cho
cột đổi trên dòng có sẵn, nhưng **chỉ nạp theo đúng chuỗi ở mục 1** — chưa từng thử nạp lại riêng 2b trên
cùng một DB. Đã nạp 2b thì **không chạy lại riêng tầng 1b** (chốt chặn đầu file 1b từ chối, xem mục 9).

File này nặng **~493 KB** — đây là dữ liệu AI **thật**, sinh ra bằng cách chạy toàn
bộ pipeline (Anthropic parse CV, OpenAI embedding, Anthropic chấm điểm + giải thích)
**một lần duy nhất** qua UI thật, rồi xuất ra và commit thẳng vào repo. Mục đích:
người clone repo về có ngay dữ liệu demo đầy đủ mà **không phải tự gọi lại API trả
phí** — và cũng để điểm số hiển thị lúc demo **ổn định, lặp lại được**, thay vì mỗi
lần chạy lại pipeline thật sẽ ra một bộ điểm/giải thích khác (LLM không xác định).

---

## 4. Tài khoản demo — mật khẩu công khai có chủ đích

Toàn bộ 11 tài khoản demo dùng chung mật khẩu **`Demo1234`** (đã kiểm bằng đăng nhập thật qua API ở
chore/seed-final, L6). Đây **không phải rò rỉ bí
mật** — mật khẩu được công khai có chủ đích để phục vụ buổi bảo vệ đồ án, ai cũng có
thể đăng nhập thử trên máy chạy demo cục bộ. Tài khoản bộ test (mật khẩu `12345678`) ở mục 8.3.

| Email | Vai trò | Họ tên |
|---|---|---|
| `hr@demo.local` | HR | Nguyễn Thị Hạnh — Công ty Cổ phần Công nghệ Hoàn Mỹ (6 tin, có đơn, điểm, giải thích) |
| `hr.nganha@demo.local` | HR | Phạm Thu Trang — Công ty TNHH Dịch vụ Số Ngân Hà (tầng 1b: 6 tin OPEN, chưa có đơn) |
| `tran.minh.hoang@demo.local` | Ứng viên | Trần Minh Hoàng |
| `le.van.duc@demo.local` | Ứng viên | Lê Văn Đức |
| `pham.quoc.bao@demo.local` | Ứng viên | Phạm Quốc Bảo |
| `nguyen.hai.son@demo.local` | Ứng viên | Nguyễn Hải Sơn |
| `nguyen.thi.thu.ha@demo.local` | Ứng viên | Nguyễn Thị Thu Hà |
| `do.khanh.linh@demo.local` | Ứng viên | Đỗ Khánh Linh |
| `vo.thanh.tung@demo.local` | Ứng viên | Võ Thanh Tùng |
| `bui.ngoc.mai@demo.local` | Ứng viên | Bùi Ngọc Mai |
| `hoang.minh.tuan@demo.local` | Ứng viên | Hoàng Minh Tuấn (không có CV, minh hoạ gợi ý theo hồ sơ) |

---

## 5. File CV PDF — vì sao cần bước cài đặt riêng

`resumes.file_url` chỉ lưu một **khoá tương đối**, dạng `resumes/<uuid>.pdf` — không
phải đường dẫn tuyệt đối, không gắn với máy nào. Khoá này được backend phân giải
thành đường dẫn đĩa thật qua cấu hình `app.storage.local-path` (`backend/src/main/
resources/application.yml`, mặc định `./uploads`) — tính **tương đối theo thư mục
làm việc lúc chạy `mvnw spring-boot:run`**, tức là `backend/`. Vậy file thật cần nằm
tại `backend/uploads/resumes/<uuid>.pdf`.

Ba file SQL ở mục 1 chỉ ghi được **dữ liệu trong Postgres** — không có cách nào để
SQL tự chép một file nhị phân lên đĩa. Vì vậy 11 file PDF gốc (9 của tầng 2 + 2 CV dựng bằng công cụ
của tầng 2b) được commit sẵn ở
`db/seed/resumes/` (đặt tên đúng theo UUID trong `file_url`), và bước 6
(`install-demo-files.ps1`) chép chúng sang đúng vị trí `backend/uploads/resumes/`
mà backend đọc.

`install-demo-files.ps1` (chore/seed-final) chép ba nguồn, nguồn chưa có thì bỏ qua:
`db/seed/resumes/` → `backend/uploads/resumes/`, `db/seed/demo-files/logos/` →
`backend/uploads/logos/`, `db/seed/demo-files/message-attachments/` →
`backend/uploads/message-attachments/`. Script **chỉ ghi đè file cùng tên với file nguồn**,
không xoá file nào khác (file của bộ test, file tải lên qua UI giữ nguyên). Sau khi chép,
script đối chiếu mọi `resumes.file_url`, `companies.logo_url`, `application_messages.attachment_key`
của **dữ liệu demo** (user `d0000000-…`/`d1b00000-…`, công ty `d1000000-…`/`d1b00000-…`) với file
đích; thiếu file nào thì báo lỗi, liệt kê đủ khoá thiếu. Lưu ý `logo_url` là URL công khai
`/uploads/logos/<tên>`, khác hai khoá kia (`resumes/<tên>`, `message-attachments/<tên>`).

---

## 6. Nội dung dữ liệu demo

**6 job đa ngành**, mỗi job 1 rubric (4 tiêu chí, tổng trọng số 100%): Senior Java
Backend Developer, Kỹ sư Kiểm thử phần mềm (QA), Kỹ sư DevOps, Kế toán tổng hợp,
Chuyên viên Marketing, Nhân viên kinh doanh qua điện thoại.

**6 mẫu giấy mời** (1/job) dùng đủ 3 placeholder thật `{{candidateName}}`, `{{jobTitle}}`,
`{{companyName}}` ở cả tiêu đề lẫn thân thư (không ghi cứng tên job/công ty, như bộ test) và
**không** có ngày giờ/địa điểm trong thư — khung giờ lưu và hiển thị tách khỏi
thư (FR-H12 R-C1), cùng câu chữ với mẫu của bộ test ("theo một trong các khung giờ phỏng vấn
được gửi kèm"). Trước chore/seed-final, mẫu demo còn placeholder cũ `[Tên ứng viên]`,
`[Giờ phỏng vấn]`, `[Ngày phỏng vấn]` mà app không thay.

**9 CV / 8 ứng viên** — một ứng viên (Lê Văn Đức) giữ 2 phiên bản CV (một bản chính,
một bản cũ), minh hoạ FR-U01 "nhiều phiên bản CV, một bản là chính". Ứng viên Bùi
Ngọc Mai (Nhân sự) **cố ý không nộp đơn nào** — xem lý do ở walkthrough.

**1 ứng viên thứ 9 — Hoàng Minh Tuấn — KHÔNG có CV nào (FR-U15, nạp ở Tầng 1):**
chỉ có hồ sơ nghề nghiệp đầy đủ, chưa từng tải CV lên — minh hoạ nhánh gợi ý việc làm
dựa trên hồ sơ/mong muốn (`PROFILE`/`DESIRES`) khi ứng viên chưa có CV, khác 8 ứng
viên còn lại (đều có ít nhất 1 CV).

**9 `candidate_profiles` (FR-U14/FR-U15, nạp ở Tầng 1):** cả 9 đều có `onboarding_completed_at`
(coi như đã qua màn "Hoàn thiện hồ sơ"). 4 người có đủ hồ sơ nghề nghiệp/mong muốn, khác
ngành nhau và khớp đúng ngành của 4 job demo tương ứng:

| Ứng viên | Chức danh mong muốn | Ngành mong muốn | Khớp job demo |
|---|---|---|---|
| Trần Minh Hoàng | Lập trình viên Backend (Java/Spring Boot) | Công nghệ thông tin - Phần mềm | Senior Java Backend Developer |
| Nguyễn Thị Thu Hà | Kế toán tổng hợp | Kế toán - Kiểm toán | Kế toán tổng hợp |
| Đỗ Khánh Linh | Chuyên viên Marketing | Marketing - Truyền thông | Chuyên viên Marketing |
| Hoàng Minh Tuấn *(không có CV)* | Chuyên viên Kinh doanh / Telesales | Kinh doanh - Bán hàng | Nhân viên kinh doanh qua điện thoại |

5 ứng viên còn lại (Lê Văn Đức, Phạm Quốc Bảo, Nguyễn Hải Sơn, Võ Thanh Tùng, Bùi Ngọc
Mai) giữ hồ sơ mong muốn trống (mảng rỗng, `desired_salary_min`/`bio` `NULL`) — chỉ có
cờ `onboarding_completed_at` đã đặt.

**8 đơn ứng tuyển đang hoạt động + 1 đơn đã rút** (`WITHDRAWN`).

**FR-C05 (01/10/2026):** dump được xuất lại sau khi gọi "Cập nhật dữ liệu trích xuất" thật cho cả 9
CV (Anthropic + OpenAI thật): `resume_parsed_data` nay là `resume-parse-v2`, có `industry_code`,
`region_code` và số tháng kinh nghiệm. `raw_text`, `criterion_scores`, `score_explanations` giống hệt bản
trước (so md5 trước/sau). `resume_reparse_requests` (hàng đợi vận hành) KHÔNG nằm trong dump.

Số dòng từng bảng sau khi nạp đủ 2 tầng (đã kiểm chứng bằng `export-ai-output.ps1`,
không phải số ước lượng) — riêng `candidate_profiles` đến từ Tầng 1
(`seed-demo-structural.sql`, SQL viết tay, không qua export script) nên không tính vào
dòng Tổng bên dưới:

| Bảng | Số dòng |
|---|---|
| `candidate_profiles` *(Tầng 1)* | 9 |
| `resumes` | 9 |
| `resume_parsed_data` | 9 |
| `job_embeddings` | 6 |
| `job_applications` | 9 |
| `application_status_history` | 10 |
| `scoring_runs` | 15 |
| `criterion_scores` | 57 |
| `score_explanations` | 12 |
| `score_explanation_attempts` | 0 |
| `cv_improvement_requests` | 2 |
| `cv_improvement_suggestions` | 2 |
| `notifications` | 22 |
| **Tổng số dòng `INSERT INTO`** | **153** |

Kiểm chứng kỹ thuật khi export: 9 file PDF khớp đúng 9 dòng `file_url` trong DB;
vector `job_embeddings` (6 dòng) và `resume_parsed_data.embedding` (8 dòng) đều đúng
1536 chiều, không bị cắt ngắn; 9/9 CV là `resume-parse-v2` và đã tính kinh nghiệm; 0 job thiếu
`category_code`.

**Ghi chú các con số dễ gây thắc mắc:**
- `candidate_profiles.embedding`/`embedding_model` (FR-U14): cả 9 hồ sơ đều `NULL` ngay
  sau khi nạp Tầng 1 — `seed-demo-structural.sql` KHÔNG tự tính embedding (SQL thuần,
  không gọi AI). `CandidateProfileEmbeddingScheduler` (R-E4) chỉ sinh embedding khi
  backend thật sự chạy với khoá OpenAI thật, và chỉ cho hồ sơ có ít nhất một trong ba
  trường `headline`/`skills`/`bio` khác rỗng (R-E2) — tức chỉ 4 hồ sơ "đầy đủ" (Trần Minh
  Hoàng, Nguyễn Thị Thu Hà, Đỗ Khánh Linh, Hoàng Minh Tuấn) sẽ có embedding sau khi backend
  chạy một lúc; 5 hồ sơ còn lại (mảng/`bio` rỗng) tiếp tục giữ `embedding = NULL` vĩnh viễn,
  đúng thiết kế, không phải lỗi. Số đo ngay sau khi nạp lại (trước khi chạy backend):
  **`SELECT count(*) FROM candidate_profiles WHERE embedding IS NOT NULL;` → 0** — đúng
  thiết kế, backend chưa chạy nên scheduler chưa có cơ hội tính.
- `job_recommendations` **đã bị xoá hẳn ở FR-U15 (đợt 4, `V11__drop_job_recommendations.sql`)**
  — bộ đệm cũ của FR-U04 (xoá-rồi-chèn lại định kỳ qua `JobRecommendationCacheScheduler`, đã xoá
  cùng đợt) được thay bằng tính toán trực tiếp mỗi lần gọi `GET /api/candidates/job-recommendations`
  (không còn bảng, không còn số dòng nào để đo ở bước seed). Chi tiết ở
  `docs/walkthrough/fr-u15-profile-recommend.md`.
- `resume_parsed_data.embedding` = 8/9: trích xuất lại đặt embedding về NULL và F1 chỉ embed **CV chính**;
  bản CV cũ (không chính) của Lê Văn Đức không được embed lại — đúng thiết kế.
- `score_explanation_attempts = 0` là **bình thường**, không phải thiếu sót — bảng
  này chỉ ghi khi một lượt sinh giải thích (D4) thất bại và cần theo dõi số lần thử
  lại; không có lượt nào thất bại thì bảng này rỗng là đúng.
- `notifications` (22 dòng) **không tăng** khi chạy tính năng gợi ý cải thiện CV
  (F2/FR-U05) — gợi ý này do chính ứng viên yêu cầu và tự xem ngay trên trang của
  họ, không có bên thứ hai cần được báo, nên không phát sinh thông báo nào cho bước
  này.

### 6b. Sau tầng 1b + 2b (chore/seed-final)

Phần trên mô tả tầng 1–2. Tầng 1b + 2b thêm (chi tiết theo trạng thái: `docs/walkthrough/chore-seed-final.md`
mục K3):

- **Công ty 2** "Công ty TNHH Dịch vụ Số Ngân Hà" (HR Phạm Thu Trang) với 6 tin OPEN → demo có **12 tin OPEN**,
  cả 12 có embedding; logo cho cả hai công ty.
- **Phễu công ty 1**: PENDING 5, INTERVIEW_INVITED 3, HIRED 1, REJECTED 1, WITHDRAWN 1; giấy mời một khung đã
  chốt (Hà, HIRED), nhiều khung chưa chọn (Linh), ứng viên đã chọn khung (Tùng → Telesales).
- **Job Kỹ sư DevOps**: rubric 5 tiêu chí (thêm "Viết tài liệu vận hành"), 2 câu hỏi sàng lọc, đơn thứ ba của
  Trần Minh Hoàng có lượt chấm + giải thích do AI thật sinh ở tầng 2b (FR-H14 so sánh 3 cột).
- **Job Senior Java Backend Developer**: bật chấm ẩn danh (FR-H16), có lịch sử bật/tắt; đơn mới của Võ Thanh
  Tùng đã được mời phỏng vấn (hiện tên thật), hai đơn còn lại hiện nhãn ẩn danh.
- **Job Kỹ sư Kiểm thử phần mềm (QA Engineer)** (demo 1 HR + 1 ứng viên): tin đã mở lại sang **chu kỳ 2**, có
  2 câu hỏi sàng lọc. Đơn đợt 1 của Phạm Quốc Bảo (`REJECTED`, 83.50) và Lê Văn Đức (`WITHDRAWN`) giữ ở chu kỳ 1,
  nên Phạm Quốc Bảo nộp lại được ở chu kỳ 2.
- **Kho ứng viên** (FR-H15): kho có mục, mục đã gỡ do rút đồng ý, kho rỗng, kho đã xoá.
- **Bùi Ngọc Mai**: thêm 2 CV dựng bằng công cụ (mẫu Cổ điển, rồi Hiện đại), không phải CV chính, không nộp đơn.
- **Hồ sơ nghề nghiệp**: 4 hồ sơ khai đủ (Trần Minh Hoàng, Nguyễn Thị Thu Hà, Đỗ Khánh Linh, Hoàng Minh Tuấn)
  có embedding đông cứng ở tầng 2b — bật backend **không** còn phát sinh lượt OpenAI cho hồ sơ demo.

Số dòng sau khi nạp đủ mục 1 (đo hai lần trên DB trống, 19/10/2026; `job_screening_questions` đo lại 21/10/2026 sau P1
(tin QA chu kỳ 2)):

| Bảng | Số dòng | Bảng | Số dòng |
|---|---|---|---|
| `users` | 11 | `scoring_runs` | 16 |
| `companies` | 2 | `criterion_scores` | 62 |
| `jobs` | 12 | `score_explanations` | 13 |
| `rubrics` / `rubric_criteria` | 12 / 43 | `job_embeddings` | 12 |
| `candidate_profiles` (có embedding) | 9 (4) | `notifications` | 35 |
| `resumes` / `resume_parsed_data` | 11 / 11 | `application_messages` | 3 |
| `cv_builder_documents` | 2 | `interview_invitations` / `_slots` | 4 / 7 |
| `job_applications` | 11 | `talent_pools` / `talent_pool_entries` | 4 / 3 |
| `application_status_history` | 18 | `job_screening_questions` / `application_screening_answers` | 4 / 2 |
| `job_blind_review_changes` | 3 | `interview_templates` | 12 |

### 6c. Sau tầng 3 (FR-U16)

Tầng 3 chỉ thêm 2 bảng so với bảng 6b: `jobs` **13** (12 tin OPEN còn hạn + 1 tin OPEN hết hạn nộp, không hiện trên bảng
việc làm công khai), `saved_jobs` **4**. Mọi bảng khác giữ nguyên số ở 6b (đo hai lần nạp đủ chuỗi mục 1 từ DB trống,
30/10/2026, số dòng mọi bảng giống nhau giữa hai lần).

**Kịch bản thử lưu tin** (đăng nhập `pham.quoc.bao@demo.local`):

1. Menu **"Tin đã lưu"** (`/candidate/saved-jobs`): "Đang lưu 4/100 tin", thứ tự từ trên xuống: Chuyên viên Thiết kế
   UI/UX, Chuyên viên Phân tích dữ liệu, Nhân viên Chăm sóc khách hàng, Nhân viên Hỗ trợ kỹ thuật (IT Helpdesk).
2. Tin IT Helpdesk: nhãn "Đã hết hạn nộp", nút Ứng tuyển khoá kèm câu lý do, không có liên kết sang trang tin. Bấm "Bỏ
   lưu" → hộp xác nhận (tin không còn mở thì không lưu lại được).
3. Trang Việc làm `/candidate`: bấm Lưu trên thẻ một tin khác → thẻ hiện "Đã lưu", trang tin không mở; quay lại Tin đã
   lưu thấy tin đó ở trên cùng.
4. "Đã ứng tuyển" + "Xem đơn": Bảo chưa có đơn ở chu kỳ hiện tại của 4 tin trên — bấm "Ứng tuyển" ở một tin đang mở, nộp
   đơn, quay lại trang để thấy trạng thái này.

Kiểm bằng SQL: `SELECT count(*) FROM saved_jobs WHERE deleted_at IS NULL AND candidate_id =
'd0000000-0000-0000-0000-000000000004';` → **4**.

---

## 7. Kiểm chứng sau khi nạp xong

Sau khi chạy đủ các bước ở mục 1, nên thấy đúng những điều sau — nếu khác, có gì đó
đã sai ở một trong các bước (riêng tầng 3: mục 6c):

- Đăng nhập `hr@demo.local`, mở job **"Kỹ sư DevOps"** → tab Ứng viên: **3 ứng viên**
  — Nguyễn Hải Sơn **81.00** hạng 1, Lê Văn Đức **59.50** hạng 2, Trần Minh Hoàng **35.00** hạng 3 (tầng 2b).
  Ba tiêu chí của Hoàng được 0 điểm, không có trích dẫn, kèm lý giải "không tìm thấy thông tin trong CV":
  đây là trạng thái hợp lệ (CV backend Java nộp vào DevOps) — dùng làm ca "AI không bịa evidence khi CV
  trái ngành" (`chore-seed-final.md` L38).
- Job **"Senior Java Backend Developer"**: **3 đơn**, job đang **bật chấm ẩn danh** — 1 đơn có điểm
  (**67.50**), 1 đơn chấm điểm **thất bại** (hiện trạng đã biết, không phải lỗi của bước nạp seed — xem ghi
  chú D2 trong `docs/ROADMAP.md`), 1 đơn của Võ Thanh Tùng đã mời phỏng vấn (chưa chấm, hiện tên thật).
- Job **"Kỹ sư Kiểm thử phần mềm (QA Engineer)"**: chu kỳ tuyển dụng **2**, 2 câu hỏi sàng lọc; tab Ứng viên có 2
  đơn chu kỳ 1 (Phạm Quốc Bảo bị từ chối **83.50**, Lê Văn Đức đã rút). Phạm Quốc Bảo nộp lại được tin này.
- Mở một hồ sơ bất kỳ, bấm **"Xem CV gốc"** — phải tải được file PDF. Nếu lỗi, nghĩa
  là bước 6 (`install-demo-files.ps1`) chưa chạy hoặc chạy chưa xong.
- Đăng nhập `bui.ngoc.mai@demo.local`: có dữ liệu gợi ý cải thiện CV. Khối gợi ý việc làm **tính
  trực tiếp ngay khi gọi `GET /api/candidates/job-recommendations` (FR-U15, không còn bảng đệm/scheduler
  — xem ghi chú `job_recommendations` ở mục 6)**. Khối này **không rỗng**: đo trực tiếp bằng vector đã
  nạp (19/10/2026), độ tương đồng CV chính của Mai với tin OPEN cao nhất là 0.6188 ("Chuyên viên Nhân sự tổng
  hợp", công ty 2, tầng 1b), và ngay tin tầng 1 "Chuyên viên Marketing" cũng đạt 0.4774 — đều trên ngưỡng
  `MIN_SIMILARITY_SCORE = 0.40`. Ca "gợi ý rỗng dưới ngưỡng" của chore/seed-demo không còn dùng được (mục 9).
- Đăng nhập `le.van.duc@demo.local`, trang hồ sơ: thấy **2 phiên bản CV**, một bản
  đánh dấu là chính; mục Đơn ứng tuyển có **1 đơn "Đã rút đơn"**.
- `bui.ngoc.mai@demo.local` có **3 CV**: 1 CV chính tải lên + 2 CV dựng bằng công cụ (FR-U12, tầng 2b).
- Bảng soát tay mục 7.4 của FR-H14 (nhóm A–B) và FR-H16 mô tả dữ liệu demo **gốc** (trước chore/seed-final):
  sau tầng 1b + 2b, nhóm B của H14 đã dựng sẵn (đơn thứ ba trên Job DevOps có điểm) và Job Java đã bật ẩn danh.
  Soát theo các bảng đó thì bỏ qua bước dựng tương ứng; REQUIREMENT đã hoàn thành không sửa (K8).
- FR-C05 — kiểm bằng SQL sau khi nạp:
  `SELECT count(*) FILTER (WHERE prompt_version = 'resume-parse-v2'), count(experience_computed_at),
  count(region_code) FROM resume_parsed_data;` → **9 | 9 | 9**;
  `SELECT count(*) FROM jobs WHERE deleted_at IS NULL AND category_code IS NULL;` → **0**.
- FR-C05 — mở "Xem dữ liệu đã trích xuất" của một CV bất kỳ: mục **Tổng quan nghề nghiệp** có chức danh,
  ngành nghề, khu vực (vd Trần Minh Hoàng: Backend Engineer · Công nghệ thông tin - Phần mềm · TP. Hồ Chí
  Minh · 6,3 năm). Không CV nào còn nút "Cập nhật dữ liệu trích xuất" (đã là v2).

---

## 8. Bộ dữ liệu TEST (`seed-test.sql`) — chỉ để soát giao diện

> **Bộ test CHỈ dùng để soát giao diện đủ mọi trạng thái. KHÔNG nạp khi demo cho hội
> đồng.** Khi nạp chung với demo, tin OPEN của công ty test hiện trên bảng việc làm công
> khai và có thể xuất hiện trong gợi ý việc làm của ứng viên demo.

Bộ test độc lập với hai tầng demo: nạp được trên DB chỉ có schema, hoặc nạp chung với
demo mà không va chạm (UUID tiền tố `e0…`–`ef…`, `f0…`, `f1…` và `f2000000-…`, email và tên công ty riêng).
Dải `f2` (mục 17 của `seed-test.sql`, chore/seed-final) là **công ty test thứ hai** — HR riêng, 2 ứng viên riêng,
3 tin (PAUSED, CLOSED, OPEN đã xoá mềm nên không hiện trên bảng việc làm công khai) — giữ các trạng thái "sau"
(kho có mục, mục đã gỡ, ẩn danh, khung đã chọn, đính kèm, `NEW_MESSAGE`) mà không làm sai các bảng soát tay 7.4
đang đếm dữ liệu `e…`/`f1`. SQL viết
tay, idempotent (`ON CONFLICT (id) DO NOTHING`), với **một ngoại lệ**: dòng
`resume_parsed_data` của CV R3 là output thật của pipeline trích xuất. Dòng này được chạy
một lần rồi chép nguyên văn vào file (xem comment mục 8 trong `seed-test.sql`). Bộ test
không có điểm AI, giải thích, evidence hay embedding nào được viết tay hoặc làm giả.

**Backend đọc khoá ở đâu:** `springboot4-dotenv` đọc file `.env` trong **thư mục làm việc
lúc chạy**. Với lệnh thường dùng `cd backend; .\mvnw.cmd spring-boot:run`, file được đọc
là **`backend/.env`**, không phải `.env` ở gốc repo. Biến môi trường đặt sẵn trong shell
được ưu tiên hơn file này.

### 8.1. Cách nạp

```powershell
# Chỉ bộ test (DB đã có schema):
docker compose cp db\seed\seed-test.sql postgres:/tmp/seed-test.sql
docker compose exec -T postgres psql -U recruitment -d recruitment -v ON_ERROR_STOP=1 -f /tmp/seed-test.sql
.\db\seed\install-test-files.ps1

# Cùng demo: chạy đủ các bước ở mục 1 TRƯỚC (reset-demo-db.sql xoá luôn bộ test), rồi mới 3 lệnh trên.
```

Từ chore/seed-final, `install-demo-files.ps1` chỉ ghi đè file có trong nguồn của demo và
chỉ đối chiếu khoá file của dữ liệu demo (mục 5), nên chạy lại bao nhiêu lần, trước hay sau
`install-test-files.ps1`, cũng không xoá file CV của bộ test. Trước đây script xoá mọi
`.pdf` trong `backend/uploads/resumes/` và dừng khi gặp CV test — ràng buộc thứ tự đó không
còn.

`install-test-files.ps1` **sinh** file ngay trong script, nên không commit PDF nào: 5 file CV của dải `e`,
2 CV + 3 tệp đính kèm (2 PDF, 1 PNG) của dải `f2`. Sau khi chép, script đối chiếu mọi khoá file của bộ test với
đĩa (dải `f2` so cả số byte, vì số byte ghi cứng trong SQL); lệch thì in khoá lệch và thoát mã 1.
Chữ trong PDF là ASCII không dấu vì font chuẩn PDF không có glyph tiếng Việt; dữ liệu
trong DB vẫn có dấu đầy đủ. Script chép thêm CV DOCX tiếng Việt có dấu
`test-files/cv-mau-quoc-huy.docx` cho R3. Nội dung mỗi file khớp trạng thái CV:
- R1 là PDF không có lớp chữ, khớp lỗi `EXTRACT_EMPTY`.
- R5 không phải file PDF, khớp lỗi `EXTRACT_CORRUPT`.

### 8.2. Dọn riêng dữ liệu test

Chạy lại `seed-test.sql` **không** khôi phục các dòng đã bị sửa qua UI. Muốn đưa bộ test
về trạng thái gốc:

```powershell
docker compose cp db\seed\reset-test-data.sql postgres:/tmp/reset-test-data.sql
docker compose exec -T postgres psql -U recruitment -d recruitment `
  -v TEST_SEED_CONFIRM=YES_RESET_TEST_DATA -f /tmp/reset-test-data.sql
# rồi nạp lại như mục 8.1
```

Script này chỉ xoá dữ liệu của 8 user test (5 dải `e`, 3 dải `f2`), hai công ty test và mọi dòng dải `f2`,
**kể cả dòng phát sinh qua UI**
(đơn, lượt chấm, embedding tin, thông báo…), theo thứ tự khoá ngoại. Một ngoại lệ có chủ
đích: đơn mà ứng viên **demo** tự nộp vào tin test qua UI cũng bị xoá, cùng thông báo gắn
với đơn đó. Dữ liệu demo gốc không bị đụng tới (đã kiểm bằng md5 nội dung 21 bảng). Script
không xoá file trên đĩa. `reset-demo-db.sql` (mục 1) `TRUNCATE` toàn bộ nên xoá luôn bộ test.

### 8.3. Tài khoản — mật khẩu chung `12345678`

| Email | Vai trò | Ghi chú |
|---|---|---|
| `minhdathr@gmail.com` | HR | Minh Đạt, chủ "Công ty TNHH Thử Nghiệm Ánh Dương" (hồ sơ công ty đầy đủ) |
| `quochuyuv@gmail.com` | Ứng viên | Quốc Huy: hồ sơ nghề nghiệp khai đủ, có đơn ở cả 5 trạng thái, 3 CV |
| `le.thi.lan@test.local` | Ứng viên phụ | Lê Thị Lan: đã bỏ qua onboarding (hồ sơ rỗng); có đơn A10 đã mời phỏng vấn, giấy mời 3 khung chưa chốt (1 khung đã qua); 1 thông báo chưa đọc |
| `pham.van.khoa@test.local` | Ứng viên phụ | Phạm Văn Khoa: đã bỏ qua onboarding, CV hỏng |
| `tran.bao.ngoc@test.local` | Ứng viên phụ | Trần Bảo Ngọc: chưa có hồ sơ, đăng nhập sẽ bị đưa sang màn onboarding |
| `dang.minh.thu.hr@test.local` | HR | Đặng Minh Thư (dải `f2`), chủ "Công ty TNHH Thử Nghiệm Bình Minh": tin bật ẩn danh, kho có mục ACTIVE và mục đã gỡ, tin nhắn có đính kèm |
| `vu.hoang.nam@test.local` | Ứng viên (`f2`) | Vũ Hoàng Nam: đơn đang ẩn danh (gửi tệp đính kèm), đơn bị từ chối rồi rút đồng ý lưu hồ sơ |
| `do.minh.chau@test.local` | Ứng viên (`f2`) | Đỗ Minh Châu: đơn đã mời phỏng vấn, đã chọn khung giờ, tin nhắn có đính kèm hai chiều |

Tổng cộng **8 tài khoản test**, cùng mật khẩu `12345678`.

Lần đầu Trần Bảo Ngọc mở trang ứng viên, `GET /api/candidates/profile/me` tự tạo một dòng
hồ sơ rỗng (`loadOrCreate`) với `onboarding_completed_at = NULL`. Màn onboarding vẫn hiện.
Đây là hành vi của ứng dụng, không phải lỗi seed.

### 8.4. Soát gì bằng tài khoản nào

| Cần soát | Tài khoản | Ở đâu |
|---|---|---|
| Tin đủ 4 trạng thái (DRAFT/OPEN/PAUSED/CLOSED) | HR test | Danh sách tin |
| Tin chưa chuẩn hoá ngành/khu vực, bị chặn khi "Mở lại" (R-J3) | HR test | "Chuyên viên Pháp chế" (PAUSED) |
| Tin không công bố lương | HR test, khách | "Nhân viên Hành chính văn phòng" (OPEN), bảng việc làm công khai |
| Rubric bị khoá do có lượt chấm đang chờ | HR test | Tab rubric của "Chuyên viên Chăm sóc khách hàng" |
| Danh sách ứng viên nhiều dòng, nhiều trạng thái | HR test | Tab ứng viên của "Chuyên viên Chăm sóc khách hàng" (4 đơn) |
| Lượt chấm đang chờ (đóng băng) / thất bại có thông báo lỗi | HR test | Đơn của Quốc Huy ở tin CSKH / tin Hành chính |
| Bấm "Chấm điểm" khi CV chưa trích xuất (bị từ chối) | HR test | Đơn của Lan / Khoa / Ngọc |
| Xem CV gốc (PDF, DOCX) | HR test | Mọi đơn |
| Thông báo đã đọc / chưa đọc (7 / 4) | HR test | Chuông thông báo |
| Tin nhắn: hộp thư hai phía (HR 3 dòng A9, A2, A3; Quốc Huy 2 dòng A2, A3, mỗi bên 1 tin chưa đọc), cuộc trao đổi chỉ còn xem, trạng thái rỗng | HR test, Quốc Huy | "Tin nhắn"; tab "Trao đổi" của đơn "Chuyên viên Pháp chế" (đã rút, chỉ còn xem) và đơn "Chuyên viên Chăm sóc khách hàng" của Quốc Huy (chưa có tin) |
| Đơn đủ 5 trạng thái, lịch sử, giấy mời phỏng vấn | Quốc Huy | Đơn ứng tuyển |
| Giấy mời nhiều khung giờ chưa chốt (3 khung, có khung là link), giấy mời đã gửi lại kèm giấy mời cũ, giấy mời một khung đã chốt, thông báo gửi lại (FR-H12) | HR test, Quốc Huy, Trần Bảo Ngọc | Đơn A9 của Trần Bảo Ngọc (chưa chốt), A2 của Quốc Huy (đã gửi lại, có 1 giấy mời trước đó), A4 của Quốc Huy (đã chốt, đơn `HIRED`); chuông thông báo của Quốc Huy |
| Ứng viên chọn khung giờ phỏng vấn: khung đã qua không chọn được, chọn một lần, HR nhận thông báo "Ứng viên đã chọn lịch phỏng vấn" (FR-U10) | Lê Thị Lan, HR test | Đơn A10 `/candidate/applications/f1000000-0000-0000-0000-000000000001` (3 khung, khung 1 đã qua); phía HR tab "Giấy mời phỏng vấn" của đơn đó. Chọn xong muốn soát lại thì chạy `reset-test-data.sql` rồi nạp lại |
| CV chờ trích xuất (đóng băng), thất bại, đã trích xuất | Quốc Huy | Hồ sơ → CV |
| Thông báo đã đọc / chưa đọc (2 / 3) | Quốc Huy | Chuông thông báo |
| Hồ sơ nghề nghiệp khai đủ | Quốc Huy | Hồ sơ |
| Hồ sơ bỏ qua onboarding | Lê Thị Lan | Hồ sơ |
| Màn onboarding | Trần Bảo Ngọc | Đăng nhập |
| Đồng ý lưu hồ sơ đang hiệu lực + nút "Rút đồng ý" (FR-U09) | Quốc Huy | Đơn "Chuyên viên Chăm sóc khách hàng" (A1, đơn duy nhất ở trạng thái đồng ý); 9 đơn còn lại (gồm A10 của FR-U10) "không đồng ý". Rút xong muốn soát lại thì chạy `reset-test-data.sql` rồi nạp lại |
| Nộp đơn có câu hỏi sàng lọc (FR-U09) | Ứng viên demo do đặc tả FR-U09 mục 7.4 chọn (`Demo1234`) | Tin "Chuyên viên Chăm sóc khách hàng" (3 câu); đơn demo vào tin test bị `reset-test-data.sql` xoá |
| **Màn hình điểm số, evidence, giải thích AI** | **`hr@demo.local`** (demo) | Bộ test cố ý không có điểm AI |

### 8.5. Lượt gọi API ngoài

- **Khi backend chạy với bộ test** (đã đo bằng chênh lệch DB, chạy 2 phút): đúng **3 lượt
  OpenAI một lần duy nhất**, gồm embedding 2 tin OPEN và 1 hồ sơ khai đủ. Không có lượt
  Anthropic hay SMTP nào. Các dòng "đang chờ" được đóng băng bằng `next_attempt_at = 2099`.
  Mọi thông báo đặt `email_status = 'SKIPPED'`.
- **Thao tác bấm tay của người soát có gọi API thật:**
  - "Thử lại" ở CV thất bại (Anthropic);
  - chấm lại đơn có lượt FAILED (Anthropic);
  - yêu cầu gợi ý cải thiện CV (Anthropic).
- **Nạp cùng demo:** từ chore/seed-final, 12 tin demo (6 ở tầng 2, 6 ở tầng 2b) và 4 hồ sơ demo (tầng 2b) đã
  có embedding đông cứng, nên nạp chung không phát sinh thêm lượt OpenAI nào cho dữ liệu demo (bộ test vẫn 3
  lượt như trên).
- **Ngoại lệ từ tầng 3 (FR-U16):** tin hết hạn nộp vẫn `OPEN` và không có embedding (seed không sinh embedding), nên
  backend chạy với khoá OpenAI thật và bật `job-embedding` sẽ gọi OpenAI **một lần** cho tin này.

---

## 9. Lưu ý đã biết

Mã `Lnn` tham chiếu `docs/walkthrough/chore-seed-final.md` mục K9.

- **Lệch thời gian giữa tin và đơn (L13).** `created_at`/`published_at` của tin tầng 1 tính tương đối theo
  lúc nạp, còn đơn tầng 2 và sự kiện tầng 1b ghi mốc tuyệt đối (08–10/2026); nạp sau khoảng cuối 10/2026 thì
  có đơn mang ngày trước ngày đăng tin. Không sửa ở chore/seed-final.
- **Số byte tệp đính kèm demo ghi cứng (L15).** `application_messages.attachment_size` trong tầng 1b là số byte
  của file trong `demo-files/message-attachments/` (19770, 29044); sinh lại file thì phải sửa hai số này.
  `install-demo-files.ps1` chỉ kiểm có file, không kiểm kích thước.
- **Đếm theo dải phải dùng tiền tố đủ 8 ký tự (L27).** `LIKE 'e%'` bắt nhầm UUID ngẫu nhiên của tầng 2/2b; dùng
  `~ '^e[0-9a-f]000000-0000-0000-0000-'`, `LIKE 'd1b00000-%'`, `LIKE 'f2000000-%'`. Dòng `resume_parsed_data` của
  CV R3 bộ test có id ngẫu nhiên (`01ddeb0e…`), không theo dải.
- **`updated_at` đổi mỗi lần nạp tầng 2b (L33).** 2b `UPDATE` `candidate_profiles` (embedding) và `rubrics`
  (khoá rubric DevOps) nên trigger `set_updated_at` ghi `now()`; giá trị này khác nhau giữa các lần nạp.
- **2b chỉ nạp theo chuỗi mục 1; đã nạp 2b thì không chạy lại riêng 1b (L11, L40).** 1b xoá rồi chèn lại dải
  `d1b00000-…`, sẽ kéo theo xoá dây chuyền lượt chấm/embedding của 2b; chốt chặn đầu file 1b báo
  `CHOT CHAN 1b THAT BAI` và không ghi gì. Muốn nạp lại: làm lại từ bước 1.
- **`seed-demo-ai-output-2b.sql` có CRLF trong dữ liệu (L42).** `raw_text` của 2 CV dựng bằng công cụ giữ ký tự
  `\r\n` của PDFBox trên Windows; `.gitattributes` khai `-text` riêng cho file này để git giữ nguyên byte. Đừng
  để editor đổi kiểu xuống dòng của file.
- **Khối gợi ý việc làm của Bùi Ngọc Mai không còn rỗng (L43).** Xem mục 7.

---

## 10. Sinh lại tầng 2b bằng AI thật (chỉ khi thật sự cần)

Tầng 2b đã đông cứng; chỉ sinh lại khi dữ liệu tầng 1–1b đổi đến mức 2b không còn khớp. Lượt chạy gọi API
**trả phí** (Anthropic + OpenAI). Kế hoạch và số đo của lượt chạy 19/10/2026: `chore-seed-final.md` K6, L29–L34.

1. Nạp demo theo mục 1 **đến hết tầng 1b** (bước 1–4, bỏ bước 5), rồi `install-demo-files.ps1`. **Không** nạp
   bộ test. Kiểm trong DB không có thông báo `email_status = 'PENDING'`.
2. Khởi động backend với đúng các cờ dưới đây (L37) — bật job-embedding, candidate-profile-embedding, scoring,
   explanation; tắt resume-embedding, resume-parsing, cv-improvement, resume-reparse; ép SMTP về MailHog:

   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--app.job-embedding.enabled=true --app.candidate-profile-embedding.enabled=true --app.resume-embedding.enabled=false --app.resume-parsing.enabled=false --app.scoring.enabled=true --app.explanation.enabled=true --app.cv-improvement.enabled=false --app.resume-reparse.enabled=false --spring.mail.host=localhost --spring.mail.port=1025 --spring.mail.username= --spring.mail.password= --spring.mail.properties.mail.smtp.auth=false --spring.mail.properties.mail.smtp.starttls.enable=false --logging.level.com.recruitment.ai.criterion.CriterionScoringService=DEBUG --logging.level.com.recruitment.ai.explanation.ScoreExplanationService=DEBUG"
   ```

   Hai logger DEBUG chỉ để đếm lượt gọi lỗi/thử lại; log có thể chứa đoạn output LLM — không commit log.
3. Chờ đủ 6 embedding tin + 4 embedding hồ sơ. Đăng nhập `bui.ngoc.mai@demo.local` gọi 2 lần
   `POST /api/candidates/cv-builder/resumes` (mẫu CLASSIC, rồi MODERN); đăng nhập `hr@demo.local` gọi
   `POST /api/hr/applications/d1b00000-0009-0000-0000-000000000001/scoring-runs`. Chờ lượt chấm DONE và có giải
   thích, rồi dừng backend.
4. **Trần lượt gọi:** Anthropic 24 (dự kiến 6), OpenAI 10. Dừng backend ngay khi vượt trần, khi có dòng WARN
   `Goi API embedding that bai` (scheduler embedding thử lại vô hạn), khi lượt chấm FAILED (không tự chấm lại),
   hoặc khi log có dòng ERROR.
5. Xuất: `.\db\seed\export-demo-ai-2b.ps1` — chạy **không** redirect stderr (L36: PowerShell 5.1 biến dòng tiến
   trình stderr của `docker compose cp` thành lỗi dừng). Script ghi cứng số dòng kỳ vọng `$Expected` của lượt
   19/10/2026 (L39): lượt chạy khác số dòng thì sửa `$Expected` trước, đối chiếu với số đo thật.
6. Kiểm nạp sạch theo mục 1 từ DB trống hai lần, đếm bảng hai lần giống nhau.
