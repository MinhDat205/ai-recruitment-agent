# Walkthrough: chore/seed-demo — Dữ liệu demo cho buổi bảo vệ

## 1. Mục tiêu

Dựng một bộ dữ liệu demo đầy đủ, tái tạo được, để trình bày trong buổi bảo vệ đồ án:
1 HR, 1 công ty, 6 job đa ngành có rubric, 8 CV thật ban đầu (8 ứng viên) đã qua trọn
vẹn pipeline AI thật (D1 parse, F1 embedding, D2 chấm điểm, D3 tổng hợp, D4 giải
thích, F1 gợi ý việc làm, F2 gợi ý cải thiện CV). Trong quá trình kiểm thử tay phát
hiện CV của Lê Văn Đức ghi nhầm tên người khác — ứng viên upload thêm một bản thay
thế đúng định danh, nâng tổng số resume trong DB lên **9** (chi tiết ở mục 4).

DB dev trước nhánh này là dữ liệu rác tích luỹ qua nhiều đợt test tay: 13 job (7
thiếu rubric/template), 18 đơn hầu hết `WITHDRAWN`, 2/7 CV parse thành công — không
dùng được cho demo. Quyết định: dọn sạch, dựng lại từ đầu bằng 8 CV thật mới, không
tái dùng dữ liệu AI cũ.

## 2. Các file đã tạo

- `db/seed/reset-demo-db.sql` — dọn sạch DB dev, ba lớp chốt chặn.
- `db/seed/seed-demo-structural.sql` — tầng 1: HR, company, job, rubric, tiêu chí,
  interview template, candidate.
- `db/seed/export-ai-output.ps1` — script export tầng 2 từ DB thật ra SQL, kèm chép
  file PDF và kiểm chứng.
- `db/seed/seed-demo-ai-output.sql` — tầng 2: output AI thật đã đông cứng (181 dòng
  `INSERT`, sinh bằng `export-ai-output.ps1`).
- `db/seed/resumes/` — 9 file PDF CV thật, đặt tên theo UUID trong `file_url`.
- `db/seed/install-demo-files.ps1` — chép ngược 9 file PDF vào
  `backend/uploads/resumes/`.
- `db/seed/README.md` — hướng dẫn vận hành: thứ tự chạy, tài khoản demo, kiểm chứng.
- `docs/ROADMAP.md` — tick `chore/seed-demo` hoàn thành (mục 5 file này).

`db/seed/dev-seed.sql` (seed cũ, phục vụ A2) **giữ nguyên, không xoá** — vẫn còn giá
trị cho việc thử bộ lọc job công khai, không liên quan tới bộ dữ liệu demo của nhánh
này.

## 3. Luồng chính

**Bước script (Tier 1 + dọn dẹp, vài giây, không gọi API):**
1. `reset-demo-db.sql` — `TRUNCATE CASCADE` toàn bộ 21 bảng nghiệp vụ, sau ba lớp
   chốt chặn.
2. `seed-demo-structural.sql` — tạo 1 HR, 8 candidate, 1 company, 6 job (`DRAFT`),
   6 rubric + 24 tiêu chí (mỗi rubric tổng trọng số đúng 100%), 6 interview template.

**Bước thao tác tay qua UI thật (một lần, có gọi API Anthropic/OpenAI thật):**
3. HR mở 6 job từ `DRAFT` sang `OPEN`.
4. 8 candidate lần lượt đăng nhập, upload đúng CV của mình — kích hoạt D1 (parse)
   rồi F1 (embedding resume) tự động qua poller.
5. F1 tự động embed 6 job đã `OPEN`.
6. 7/8 candidate (trừ Bùi Ngọc Mai) nộp đơn vào job đã phân — hai người nộp **2 đơn
   mỗi người**: Nguyễn Thị Thu Hà (Kế toán tổng hợp đúng ngành + Senior Java Backend
   Developer lệch ngành cố ý) và Lê Văn Đức (Kỹ sư DevOps + Kỹ sư Kiểm thử phần mềm
   QA) → tổng **9 đơn**.
7. HR kích hoạt chấm điểm (D2) cho từng đơn → D3 tổng hợp tự động → D4 sinh giải
   thích.
8. F1 tự động tính gợi ý việc làm cho mọi candidate có embedding (thuần Postgres,
   không gọi thêm API).
9. Hai candidate dùng F2 (gợi ý cải thiện CV): **Bùi Ngọc Mai** và **Nguyễn Thị Thu
   Hà**.
10. Lê Văn Đức tự rút đơn ứng tuyển job **Kỹ sư Kiểm thử phần mềm (QA Engineer)**
    (`WITHDRAWN`). 8 đơn còn lại giữ nguyên `PENDING` — tính tới thời điểm export,
    HR chưa đổi trạng thái đơn nào (chưa mời phỏng vấn, chưa tuyển, chưa từ chối đơn
    nào).

**Bước export (sau khi bước tay xong, một lần):**
11. `export-ai-output.ps1` — `pg_dump` riêng từng bảng (13 bảng, đúng thứ tự khoá
    ngoại) bên trong container, gộp thành `seed-demo-ai-output.sql`; đối chiếu hai
    chiều PDF ↔ `file_url`; kiểm vector 1536 chiều; kiểm dấu tiếng Việt. Commit cả
    file SQL lẫn 9 file PDF vào repo.

**Người clone repo sau này chỉ cần 4 lệnh** (mục 1 của `README.md`): `reset` → tầng 1
→ tầng 2 → `install-demo-files.ps1` — không cần lặp lại bước 3-10, không tốn API,
không cần chờ.

## 4. Quyết định thiết kế

**Seed hai tầng, không gộp một file.** Tầng 1 (SQL viết tay, idempotent) chứa dữ
liệu KHÔNG phụ thuộc AI và KHÔNG phụ thuộc thời điểm chạy thật — rẻ, dễ đọc, dễ sửa
tay khi cần chỉnh nội dung job/rubric. Tầng 2 là output thật của một lần chạy pipeline
AI + ứng dụng thật (`resumes`, `job_applications` cũng nằm ở đây vì gắn với file trên
đĩa và với timestamp runtime, không tiện viết tay). Tầng 2 **không viết tay** vì:
`resume_parsed_data.data`/`criterion_scores.evidence` phải là nguyên văn trích từ CV
thật (nguyên tắc evidence, FR-H06) — viết tay tương đương bịa dữ liệu, vi phạm chính
nguyên tắc mà tính năng đang chứng minh. Sinh một lần rồi đông cứng còn giúp điểm số
demo **ổn định giữa các lần chạy**, không phụ thuộc vào việc LLM trả lời không xác
định.

**6 job thay vì 2 như PHASES.md quy định — lệch có chủ đích.** Với chỉ 2 job, phép so
khớp embedding không có đủ mẫu để nói lên điều gì — cần job trải nhiều lĩnh vực khác
nhau (không chỉ IT) để kiểm chứng được F1 phân biệt ngành đến đâu. Kết quả đo trên 6
job cho thấy F1 **xếp hạng** đúng 7/7 ứng viên (job đúng ngành luôn đứng đầu), nhưng
ngưỡng tuyệt đối 0.40 không loại được các cặp không liên quan — chi tiết ở
`docs/ROADMAP.md`. Nếu chỉ có 2 job thì không phát hiện được điều này.

**Bùi Ngọc Mai (Nhân sự) cố ý không nộp đơn nào, và không có job ngành Nhân sự trong
6 job.** Ý định ban đầu: dựng ca chứng minh `MIN_SIMILARITY_SCORE = 0.40` loại đúng —
ứng viên này lẽ ra nhận `job_recommendations` RỖNG. **Kết quả đo thật cho thấy điều
ngược lại**: cô ấy vẫn nhận 3 gợi ý (Marketing 0.466, Sales 0.436, QA 0.432) vì nền
tương đồng của văn bản tiếng Việt cùng thể loại nằm quanh 0.40. Ca này vì vậy trở
thành bằng chứng cho **giới hạn của ngưỡng tuyệt đối**, không phải cho hiệu quả của
nó — xem ghi chú chi tiết trong `docs/ROADMAP.md`. Điều F1 làm đúng là **xếp hạng**:
7/7 ứng viên có job đúng ngành đứng đầu danh sách.

**Commit file PDF vào `db/seed/resumes/`.** Nguyên tắc "không commit file CV" trong
`.gitignore`/CLAUDE.md nhắm vào CV **thật do người dùng** upload lúc ứng dụng chạy
(dữ liệu cá nhân của người dùng thật, không kiểm soát được nội dung) — không nhắm
vào dữ liệu demo do chính dự án tự chuẩn bị và tự sinh để phục vụ trình bày. Thiếu
file PDF thì nút "Xem CV gốc" (D4) và việc đối chiếu evidence trích xuất với văn bản
gốc — yêu cầu cốt lõi của FR-H06 (Explainable AI) — không demo được. `db/seed/resumes/`
không nằm trong bất kỳ pattern nào của `.gitignore`, xác nhận bằng `git check-ignore`
trước khi commit.

**Lê Văn Đức có 2 bản CV — diễn biến thật, không phải dàn dựng.** Bản CV đầu upload
mang tên **"Lê Thị Ngọc Anh"** trong nội dung — không khớp tên tài khoản đăng nhập,
phát hiện khi kiểm thử tay. Ứng viên upload một bản thay thế, giữ nguyên nội dung
ngành (Data/IT tổng hợp) nhưng sửa đúng định danh, và bản này trở thành bản chính
(`is_primary = true`). Bản cũ **được giữ lại có chủ đích**, không phải do hệ thống
không xoá — đã đặt `version_label` cho cả hai bản để phân biệt trên UI ("Ban cu - CV
mau nganh Data" cho bản cũ, "Ban chinh - CV Data Engineer" cho bản chính). Đây là
hành động chủ động sau khi phát hiện sai sót, không phải bỏ mặc. Dù vậy, việc minh
hoạ đúng FR-U01 "ứng viên có thể có nhiều phiên bản CV, đúng một bản là chính" vẫn là
**hệ quả tình cờ** của việc sửa lỗi, không phải mục đích ban đầu khi tạo dữ liệu.

**Dùng `pg_dump` để export tầng 2, không viết lớp export bằng Java.** `pg_dump
--column-inserts` xử lý sẵn và đúng chuẩn: escape chuỗi (kể cả dấu tiếng Việt, dấu
nháy đơn trong văn bản CV), định dạng JSONB, và đặc biệt là vector `pgvector` — kiểu
`vector(1536)` xuất ra dạng text `[0.0123,...]` mà Postgres tự ép kiểu khi INSERT lại,
không cần logic serialize tay nào. Viết lại logic này bằng Java vừa tốn công vừa có
nguy cơ escape sai mà không nhận ra ngay (dấu tiếng Việt, ký tự đặc biệt trong CV).

**Ba lớp chốt chặn của `reset-demo-db.sql`** (script phá huỷ dữ liệu, khác hẳn hai
file seed idempotent còn lại):
1. Biến psql `DEMO_SEED_CONFIRM` phải được truyền đúng giá trị `YES_RESET_DEMO_DB`.
2. `current_database()` phải đúng là `recruitment`.
3. Tổng số dòng `users` không vượt 50 — chặn trường hợp lỡ tay trỏ vào một DB đã có
   dữ liệu thật quy mô lớn.
Không có `ON CONFLICT`/cơ chế idempotent nào ở đây — sự bất đối xứng này (2 file
idempotent, 1 file phá huỷ) tự nó là tín hiệu cảnh báo khi đọc thư mục `db/seed/`.

## 5. Lỗi môi trường đã gặp và cách tránh

Bốn lỗi thật gặp phải khi làm nhánh này, đều thuộc lớp "PowerShell 5.1 trên Windows
diễn giải sai byte/cú pháp", không phải lỗi logic nghiệp vụ:

1. **Pipe qua PowerShell làm mất dấu tiếng Việt khi nạp seed.** `Get-Content
   file.sql | docker compose exec -T postgres psql ...` khiến PowerShell đọc file
   thành chuỗi .NET rồi mã hoá lại theo code page console (thường không phải UTF-8)
   khi ghi vào pipe cho tiến trình con — seed chạy "thành công", không báo lỗi gì,
   nhưng mọi tên job/tên người có dấu đều thành dấu hỏi. Cách tránh: luôn dùng
   `docker compose cp` để đưa file vào container (sao chép nguyên byte, không qua
   diễn giải chuỗi nào), rồi `psql -f` đọc file từ bên trong container.

2. **`Set-Content -Encoding ASCII` ghi CRLF làm hỏng script bash chạy trong
   container.** Windows PowerShell 5.1 luôn nối dòng bằng `\r\n` khi dùng
   `Set-Content`, bất kể encoding chỉ định. Bash đọc dòng `set -e\r` thì `\r` dính
   vào cuối tham số, `set` nhận chuỗi option không hợp lệ, vỡ ngay dòng đầu. Cách
   tránh: ghi script bash bằng `[System.IO.File]::WriteAllText` với nội dung nối
   bằng `` `n `` thuần (LF), không dùng `Set-Content`.

3. **Dấu phẩy có độ ưu tiên cao hơn `+` chưa bọc ngoặc bên trong `@(...)`.**
   `@('a', 'b' + $x, 'c')` **không** cho ra 3 phần tử như tưởng — nó bị đọc thành
   4 phần tử (`'a'`, `'b'`, giá trị của `$x`, `'c'`), vì `+` không tự động gom hết
   biểu thức về phía trái khi đứng ngay sau dấu phẩy trong ngữ cảnh này. Tái hiện
   được bằng thực nghiệm trực tiếp (`$arr.Count` ra 4, không phải 3). Hậu quả thực
   tế: biến `OUT=` và đường dẫn file bị tách thành hai dòng riêng trong script bash,
   khiến bash chạy nhầm đường dẫn file như một **lệnh** thay vì mục tiêu redirect
   (`No such file or directory`, exit code 127). Cách tránh: **luôn bọc ngoặc đơn**
   quanh bất kỳ biểu thức `+`/`-f` nào là một phần tử độc lập bên trong `@(...)` —
   `@('a', ('b' + $x), 'c')`.

4. **File `.ps1` không có BOM UTF-8 khiến PowerShell 5.1 đọc sai mọi ký tự tiếng
   Việt có dấu ngay trong mã nguồn.** Windows PowerShell 5.1 đọc file `.ps1` không
   BOM bằng codepage ANSI mặc định của hệ thống, không phải UTF-8 — chuỗi literal
   viết tay như tên riêng có dấu bị hiểu sai **lúc parse**, trước khi dòng lệnh nào
   kịp chạy, dù dữ liệu trong file SQL đích vẫn hoàn toàn đúng (xác nhận bằng
   `Select-String -Encoding UTF8` trực tiếp trên file SQL, ra đúng nội dung có dấu).
   Cách tránh đã áp dụng: bỏ hoàn toàn ký tự tiếng Việt có dấu khỏi mã nguồn `.ps1`
   — mọi comment viết tiếng Việt không dấu; khi cần so khớp một ký tự có dấu cụ thể
   (ví dụ kiểm chứng dấu tiếng Việt trong file SQL xuất ra), dựng chuỗi từ **mã ký
   tự** (`[char]0x1EC5` cho "ễ") thay vì gõ thẳng literal có dấu — cách này không
   phụ thuộc encoding của file `.ps1` chứa nó.

## 6. Ràng buộc SRS đã thực thi

- Không có cột/field nào tên `verdict`/`label`/`isQualified`/`passed` được seed vào
  bất kỳ bảng nào — `criterion_scores.score` là điểm AI chấm riêng từng tiêu chí,
  `scoring_runs.total_score` là điểm Backend tính theo trọng số, `job_applications.status`
  là quyết định HR/candidate tự đổi qua UI thật, đúng ranh giới FR-H04/FR-H05/FR-H07.
- `job_applications.ai_consent = TRUE` cho mọi đơn (ràng buộc `chk_consent_true` ở
  DB) — mọi đơn đều nộp qua UI thật có tick consent, không có đơn nào seed tay bỏ
  qua bước này (FR-U02).
- Rút đơn của Lê Văn Đức (job Kỹ sư Kiểm thử phần mềm - QA Engineer) là đổi
  `status = 'WITHDRAWN'` qua hành động candidate thật trên UI, không phải xoá bản
  ghi (FR-U06).
- `criterion_scores.evidence` và `resume_parsed_data.data` là output AI **thật**,
  trích nguyên văn từ CV thật do pipeline chạy — không có ô nào bị viết tay/bịa số
  liệu ở tầng 2.

## 7. Nợ kỹ thuật

Một nợ kỹ thuật phát sinh trong lúc tạo dữ liệu demo (lỗi D2 thật, không phải lỗi
của nhánh `chore/seed-demo`) đã được ghi chi tiết trực tiếp trong `docs/ROADMAP.md`
dưới mục `chore/seed-demo` — không nhắc lại ở đây để tránh hai nguồn có thể lệch
nhau theo thời gian. Tóm tắt một câu: một tiêu chí rubric trượt guard evidence
(`EVIDENCE_NOT_VERIFIED`) làm hỏng cả lượt chấm cho đơn Kế toán → Senior Java Backend
Developer — nghi vấn do khác biệt định dạng bullet giữa `resume_parsed_data.data`
(JSON) và `raw_text`, chưa xác nhận, chưa sửa, không nới guard trước khi có test xác
nhận giả thuyết.

Không có nợ kỹ thuật nào khác phát sinh riêng từ việc dựng seed (script, tài liệu)
ngoài mục đã nêu trên.

## Ba câu hỏi kiểm tra

1. Nếu xoá `db/seed/resumes/*.pdf` thì hỏng cái gì? — `install-demo-files.ps1` sẽ
   throw ngay ("Khong tim thay file PDF nguon") thay vì âm thầm bỏ qua; nút "Xem CV
   gốc" trên UI sẽ báo lỗi 404 cho mọi resume vì `backend/uploads/resumes/` rỗng.
2. Chạy `seed-demo-ai-output.sql` hai lần liên tiếp mà không `reset-demo-db.sql` ở
   giữa thì sao? — Vỡ ngay ở câu `INSERT` đầu tiên trùng khoá chính
   (`duplicate key value violates unique constraint`) vì đây là dump thuần, không có
   `ON CONFLICT`. Phải reset trước khi nạp lại.
3. Vì sao ứng viên Bùi Ngọc Mai vẫn nhận được gợi ý việc làm dù không có job ngành
   Nhân sự nào? — Vì `MIN_SIMILARITY_SCORE = 0.40` là ngưỡng tuyệt đối, trong khi sàn
   tương đồng thực tế của CV và JD tiếng Việt cùng nằm quanh mức đó (đo trên 8 CV ×
   6 job: toàn bộ 28 cặp nằm trong dải 0.402–0.720). Ngưỡng này gần như không loại
   được gì. Thứ F1 làm đúng là thứ tự: cả 7 ứng viên đều có job đúng ngành xếp hạng
   nhất. Chi tiết và hướng sửa ở `docs/ROADMAP.md`.
