# db/seed — Dữ liệu demo cho buổi bảo vệ (chore/seed-demo)

Bộ dữ liệu demo đầy đủ: 1 HR, 1 công ty, 6 job đa ngành có rubric, 9 CV thật (8 ứng
viên, một ứng viên giữ 2 phiên bản CV) đã qua parse/embedding/chấm điểm/giải thích
bằng pipeline AI thật (Anthropic + OpenAI) một lần duy nhất.

Chia **hai tầng**:

- **Tầng 1** (`seed-demo-structural.sql`) — dữ liệu cấu trúc thuần, không phụ thuộc
  AI: HR, candidate, company, job, rubric, tiêu chí, mẫu giấy mời phỏng vấn. SQL
  thuần, idempotent (`ON CONFLICT (id) DO NOTHING`), viết tay, chạy lại vô hạn lần.
- **Tầng 2** (`seed-demo-ai-output.sql`) — output thật của pipeline AI (parse CV,
  embedding, chấm điểm, giải thích, gợi ý việc làm, gợi ý cải thiện CV) cộng với các
  bảng phụ thuộc thời điểm chạy thật (`resumes`, `job_applications`,
  `application_status_history`, `notifications`...). Sinh **một lần** bằng cách chạy
  ứng dụng thật qua UI, rồi xuất bằng `pg_dump` (script `export-ai-output.ps1`) và
  commit thẳng vào repo — **không phải SQL viết tay**, và **không idempotent**.

Lý do tách hai tầng và vì sao tầng 2 không viết tay: xem
`docs/walkthrough/chore-seed-demo.md` mục "Quyết định thiết kế".

---

## 1. Thứ tự chạy BẮT BUỘC — không được đảo

```powershell
# 1. Dọn sạch dữ liệu cũ (BẮT BUỘC truyền biến xác nhận, xem mục 2 để hiểu vì sao)
docker compose cp db\seed\reset-demo-db.sql postgres:/tmp/reset-demo-db.sql
docker compose exec -T postgres psql -U recruitment -d recruitment `
  -v DEMO_SEED_CONFIRM=YES_RESET_DEMO_DB -f /tmp/reset-demo-db.sql

# 2. Tầng 1 — dữ liệu cấu trúc (HR, company, job, rubric, candidate)
docker compose cp db\seed\seed-demo-structural.sql postgres:/tmp/seed-demo-structural.sql
docker compose exec -T postgres psql -U recruitment -d recruitment -f /tmp/seed-demo-structural.sql

# 3. Tầng 2 — output AI thật đã đông cứng (parse, embedding, chấm điểm, giải thích...)
docker compose cp db\seed\seed-demo-ai-output.sql postgres:/tmp/seed-demo-ai-output.sql
docker compose exec -T postgres psql -U recruitment -d recruitment -f /tmp/seed-demo-ai-output.sql

# 4. Cài file PDF CV thật vào đúng chỗ backend đọc được (xem mục 5)
.\db\seed\install-demo-files.ps1
```

Bỏ qua bước 4 thì DB vẫn đúng, nhưng nút "Xem CV gốc" (FR-H06) sẽ báo lỗi vì file
không có trên đĩa. Chạy bước 4 mà chưa chạy xong bước 3 sẽ báo lỗi ngay (bảng
`resumes` rỗng), không âm thầm sai.

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

File này nặng **~493 KB** — đây là dữ liệu AI **thật**, sinh ra bằng cách chạy toàn
bộ pipeline (Anthropic parse CV, OpenAI embedding, Anthropic chấm điểm + giải thích)
**một lần duy nhất** qua UI thật, rồi xuất ra và commit thẳng vào repo. Mục đích:
người clone repo về có ngay dữ liệu demo đầy đủ mà **không phải tự gọi lại API trả
phí** — và cũng để điểm số hiển thị lúc demo **ổn định, lặp lại được**, thay vì mỗi
lần chạy lại pipeline thật sẽ ra một bộ điểm/giải thích khác (LLM không xác định).

---

## 4. Tài khoản demo — mật khẩu công khai có chủ đích

Toàn bộ 9 tài khoản dùng chung mật khẩu **`Demo1234`**. Đây **không phải rò rỉ bí
mật** — mật khẩu được công khai có chủ đích để phục vụ buổi bảo vệ đồ án, ai cũng có
thể đăng nhập thử trên máy chạy demo cục bộ.

| Email | Vai trò | Họ tên |
|---|---|---|
| `hr@demo.local` | HR | Nguyễn Thị Hạnh |
| `tran.minh.hoang@demo.local` | Ứng viên | Trần Minh Hoàng |
| `le.van.duc@demo.local` | Ứng viên | Lê Văn Đức |
| `pham.quoc.bao@demo.local` | Ứng viên | Phạm Quốc Bảo |
| `nguyen.hai.son@demo.local` | Ứng viên | Nguyễn Hải Sơn |
| `nguyen.thi.thu.ha@demo.local` | Ứng viên | Nguyễn Thị Thu Hà |
| `do.khanh.linh@demo.local` | Ứng viên | Đỗ Khánh Linh |
| `vo.thanh.tung@demo.local` | Ứng viên | Võ Thanh Tùng |
| `bui.ngoc.mai@demo.local` | Ứng viên | Bùi Ngọc Mai |

---

## 5. File CV PDF — vì sao cần bước cài đặt riêng

`resumes.file_url` chỉ lưu một **khoá tương đối**, dạng `resumes/<uuid>.pdf` — không
phải đường dẫn tuyệt đối, không gắn với máy nào. Khoá này được backend phân giải
thành đường dẫn đĩa thật qua cấu hình `app.storage.local-path` (`backend/src/main/
resources/application.yml`, mặc định `./uploads`) — tính **tương đối theo thư mục
làm việc lúc chạy `mvnw spring-boot:run`**, tức là `backend/`. Vậy file thật cần nằm
tại `backend/uploads/resumes/<uuid>.pdf`.

Ba file SQL ở mục 1 chỉ ghi được **dữ liệu trong Postgres** — không có cách nào để
SQL tự chép một file nhị phân lên đĩa. Vì vậy 9 file PDF gốc được commit sẵn ở
`db/seed/resumes/` (đặt tên đúng theo UUID trong `file_url`), và bước 4
(`install-demo-files.ps1`) chép chúng sang đúng vị trí `backend/uploads/resumes/`
mà backend đọc.

---

## 6. Nội dung dữ liệu demo

**6 job đa ngành**, mỗi job 1 rubric (4 tiêu chí, tổng trọng số 100%): Senior Java
Backend Developer, Kỹ sư Kiểm thử phần mềm (QA), Kỹ sư DevOps, Kế toán tổng hợp,
Chuyên viên Marketing, Nhân viên kinh doanh qua điện thoại.

**9 CV / 8 ứng viên** — một ứng viên (Lê Văn Đức) giữ 2 phiên bản CV (một bản chính,
một bản cũ), minh hoạ FR-U01 "nhiều phiên bản CV, một bản là chính". Ứng viên Bùi
Ngọc Mai (Nhân sự) **cố ý không nộp đơn nào** — xem lý do ở walkthrough.

**8 `candidate_profiles` (FR-U14, nạp ở Tầng 1):** cả 8 đều có `onboarding_completed_at`
(coi như đã qua màn "Hoàn thiện hồ sơ"). 3 người có đủ hồ sơ nghề nghiệp/mong muốn, khác
ngành nhau và khớp đúng ngành của 3 job demo tương ứng:

| Ứng viên | Chức danh mong muốn | Ngành mong muốn | Khớp job demo |
|---|---|---|---|
| Trần Minh Hoàng | Lập trình viên Backend (Java/Spring Boot) | Công nghệ thông tin - Phần mềm | Senior Java Backend Developer |
| Nguyễn Thị Thu Hà | Kế toán tổng hợp | Kế toán - Kiểm toán | Kế toán tổng hợp |
| Đỗ Khánh Linh | Chuyên viên Marketing | Marketing - Truyền thông | Chuyên viên Marketing |

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
| `candidate_profiles` *(Tầng 1)* | 8 |
| `resumes` | 9 |
| `resume_parsed_data` | 9 |
| `job_embeddings` | 6 |
| `job_recommendations` | **28** (đo 02/10/2026, backend chạy với 6 tin `OPEN` — xem ghi chú dưới) |
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
- `candidate_profiles.embedding`/`embedding_model` (FR-U14): cả 8 hồ sơ đều `NULL` ngay
  sau khi nạp Tầng 1 — `seed-demo-structural.sql` KHÔNG tự tính embedding (SQL thuần,
  không gọi AI). `CandidateProfileEmbeddingScheduler` (R-E4) chỉ sinh embedding khi
  backend thật sự chạy với khoá OpenAI thật, và chỉ cho hồ sơ có ít nhất một trong ba
  trường `headline`/`skills`/`bio` khác rỗng (R-E2) — tức chỉ 3 hồ sơ "đầy đủ" (Trần Minh
  Hoàng, Nguyễn Thị Thu Hà, Đỗ Khánh Linh) sẽ có embedding sau khi backend chạy một lúc;
  5 hồ sơ còn lại (mảng/`bio` rỗng) tiếp tục giữ `embedding = NULL` vĩnh viễn, đúng thiết
  kế, không phải lỗi. Số đo thật sau khi nạp lại và chạy backend với khoá OpenAI thật
  (03/10/2026): **`SELECT count(*) FROM candidate_profiles WHERE embedding IS NOT NULL;`
  → 3** — đúng 3 hồ sơ "đầy đủ" dự kiến ở trên, không hơn không kém.
- `job_recommendations` (FR-U07 R-D1, 02/10/2026): trước đây 6 job trong `seed-demo-structural.sql` ở
  trạng thái `DRAFT` nên bảng này luôn về 0 — `JobRecommendationCacheScheduler` cứ 5 giây xoá-rồi-chèn
  lại gợi ý cho mọi ứng viên có embedding CV chính, chỉ khớp job `OPEN`, không có job `OPEN` nào thì
  không có gợi ý nào. **Từ FR-U07 R-D1, 6 job seed thẳng ở trạng thái `OPEN`** (đã có `job_embeddings`
  sẵn, cột ở trên) — scheduler khi backend chạy tính ra gợi ý thật cho từng ứng viên theo mức tương
  đồng embedding. Số đo thật sau khi nạp lại và chạy backend (02/10/2026):
  **`SELECT count(*) FROM job_recommendations;` → 28** — chi tiết ở
  `docs/walkthrough/fr-u07-job-filter.md`.
- `resume_parsed_data.embedding` = 8/9: trích xuất lại đặt embedding về NULL và F1 chỉ embed **CV chính**;
  bản CV cũ (không chính) của Lê Văn Đức không được embed lại — đúng thiết kế.
- `score_explanation_attempts = 0` là **bình thường**, không phải thiếu sót — bảng
  này chỉ ghi khi một lượt sinh giải thích (D4) thất bại và cần theo dõi số lần thử
  lại; không có lượt nào thất bại thì bảng này rỗng là đúng.
- `notifications` (22 dòng) **không tăng** khi chạy tính năng gợi ý cải thiện CV
  (F2/FR-U05) — gợi ý này do chính ứng viên yêu cầu và tự xem ngay trên trang của
  họ, không có bên thứ hai cần được báo, nên không phát sinh thông báo nào cho bước
  này.

---

## 7. Kiểm chứng sau khi nạp xong

Sau khi chạy đủ 4 bước ở mục 1, nên thấy đúng những điều sau — nếu khác, có gì đó
đã sai ở một trong 4 bước:

- Đăng nhập `hr@demo.local`, mở job **"Kỹ sư DevOps"** → tab Ứng viên: **2 ứng viên**
  — Nguyễn Hải Sơn **81.00** hạng 1, Lê Văn Đức **59.50** hạng 2.
- Job **"Senior Java Backend Developer"**: **2 đơn** nhưng chỉ **1 đơn có điểm**
  (**67.50**) — đơn còn lại ở trạng thái chấm điểm **thất bại**. Đây là hiện trạng
  đã biết, không phải lỗi của bước nạp seed — xem ghi chú D2 trong `docs/ROADMAP.md`.
- Mở một hồ sơ bất kỳ, bấm **"Xem CV gốc"** — phải tải được file PDF. Nếu lỗi, nghĩa
  là bước 4 (`install-demo-files.ps1`) chưa chạy hoặc chạy chưa xong.
- Đăng nhập `bui.ngoc.mai@demo.local`: có dữ liệu gợi ý cải thiện CV. Khối gợi ý việc làm **phụ thuộc
  kết quả so khớp embedding thật của `JobRecommendationCacheScheduler`** (xem ghi chú `job_recommendations`
  ở mục 6, cập nhật FR-U07 R-D1, đo thật 28 dòng toàn bảng) — ứng viên này thuộc ngành Nhân sự, không
  trùng ngành với 6 job seed hiện có (không có job Nhân sự nào), nhiều khả năng khối gợi ý của riêng
  ứng viên này vẫn rỗng do dưới ngưỡng tương đồng.
- Đăng nhập `le.van.duc@demo.local`, trang hồ sơ: thấy **2 phiên bản CV**, một bản
  đánh dấu là chính; mục Đơn ứng tuyển có **1 đơn "Đã rút đơn"**.
- FR-C05 — kiểm bằng SQL sau khi nạp:
  `SELECT count(*) FILTER (WHERE prompt_version = 'resume-parse-v2'), count(experience_computed_at),
  count(region_code) FROM resume_parsed_data;` → **9 | 9 | 9**;
  `SELECT count(*) FROM jobs WHERE deleted_at IS NULL AND category_code IS NULL;` → **0**.
- FR-C05 — mở "Xem dữ liệu đã trích xuất" của một CV bất kỳ: mục **Tổng quan nghề nghiệp** có chức danh,
  ngành nghề, khu vực (vd Trần Minh Hoàng: Backend Engineer · Công nghệ thông tin - Phần mềm · TP. Hồ Chí
  Minh · 6,3 năm). Không CV nào còn nút "Cập nhật dữ liệu trích xuất" (đã là v2).
