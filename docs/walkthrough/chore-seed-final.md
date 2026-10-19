# Walkthrough: chore/seed-final — Bộ dữ liệu cuối cho soát tay và demo

> Trạng thái: **Kế hoạch (đợt 1)**. Các mục sau "Kế hoạch" sẽ viết ở đợt cuối, chỉ ghi lại việc đã xảy ra.

## Kế hoạch

### K0. Hiện trạng đo được (19/10/2026, DB dev, `BEGIN READ ONLY … ROLLBACK`)

- Flyway cao nhất: **V18** (`18 cv builder documents`). **V19 (FR-H16) chưa chạy** trên DB dev — backend chưa khởi
  động lại sau khi merge H16.
- Số dòng: users 18 (demo 10, test 5, khác 3: `u12-…@example.com`, `6@gmail.com`, `7@gmail.com` — dữ liệu soát tay
  để lại), candidate_profiles 14, companies 2, jobs 12 (OPEN 8: demo 6, test 2), resumes 17, resume_parsed_data 12,
  cv_builder_documents 2 (đều của dữ liệu soát tay, không thuộc seed), job_embeddings 8, job_applications 19,
  application_status_history 28, interview_invitations 5, interview_invitation_slots 9, scoring_runs 17 (DONE 12,
  FAILED 4, PENDING 1), criterion_scores 57, score_explanations 12, cv_improvement_requests/suggestions 2/2,
  notifications 41 (đã đọc 9), application_messages 6 (0 đính kèm), job_screening_questions 3,
  application_screening_answers 6; job_ai_generations, talent_pools, talent_pool_entries, resume_reparse_requests,
  score_explanation_attempts đều 0.
- 6 mẫu giấy mời demo (`d5…`) còn placeholder cũ `[Tên ứng viên]`, không có `{{candidateName}}`; 6 mẫu test (`e5…`)
  đúng.
- **Dump demo tầng 2 nạp được trên schema hiện tại (đã đối chiếu, không chạy lệnh ghi):** 112 cặp bảng–cột trong các
  câu `INSERT` của `seed-demo-ai-output.sql` đều tồn tại trong `information_schema`; không cột `NOT NULL` không default
  nào bị dump bỏ qua (đối chứng dương: cột giả bị báo đúng). Kiểm trên V18; V19 chỉ thêm cột có default vào `jobs` và
  bảng mới `job_blind_review_changes`, không chạm 11 bảng có trong dump → suy ra vẫn nạp được trên V19.

### K1. Quyết định

| Mã | Câu hỏi | Chốt |
|---|---|---|
| Q1 | Một bộ hay hai bộ | **Hai bộ, hai vai trò**: demo = câu chuyện liền mạch cho hội đồng; test = ma trận trạng thái để soát giao diện |
| Q2 | Kết quả AI mới | **Chạy AI thật một lần rồi dump** (tầng 2b). Báo số lượt gọi ước tính (K6) và **chờ duyệt** trước khi chạy |
| Q3 | ≥ 11 tin OPEN | **Công ty demo thứ hai**, 6 tin OPEN → demo có 12 tin OPEN |
| Q4 | Trạng thái một chiều | **Seed sẵn cả "trước" và "sau" trên hai đơn khác nhau** |
| Q5 | File CV builder | **Tạo bằng app một lần, commit PDF**; sửa `install-demo-files.ps1` chỉ xoá file đã biết |
| Q6 | Lỗi seed cũ | **Sửa trong nhánh này** (đợt 2) |
| Q7 | Mật khẩu | **Giữ hai mật khẩu**: demo `Demo1234`, test `12345678` |
| B1 | H08 | Tầng 1b chuyển một số đơn demo **đã có điểm** sang đủ trạng thái pipeline, kèm lịch sử hợp lệ; **không đụng** `scoring_runs`/`criterion_scores`/`score_explanations` |
| B2 | H01 | Logo cho hai công ty demo, ảnh tự vẽ đơn giản (không logo thương hiệu thật), commit cùng seed, script cài file |
| B3 | C05 CV v1 | Bỏ qua, ghi giới hạn (K8) |

Quyết định phát sinh khi lập kế hoạch (cần người duyệt xác nhận):

| Mã | Nội dung | Lý do |
|---|---|---|
| P1 | Logo dùng **PNG**, không dùng SVG | Upload logo chỉ nhận png/jpg/webp (`CompanyOwnerService.java:130`); SVG là trạng thái app không tạo được |
| P2 | Trạng thái "sau" của bộ test (kho có mục, REVOKED, ẩn danh, khung đã chọn, đính kèm, `NEW_MESSAGE`) đặt ở **công ty test thứ hai + 2 ứng viên mới** (dải `f2`), không thêm vào dữ liệu `e…`/`f1` | Bảng soát tay 7.4 của các FR đã hoàn thành (C06, H15, H16, U08, U10) đếm đúng số dòng/số chưa đọc của HR test và Quốc Huy, và H15/H16 7.4 bắt đầu từ trạng thái "kho rỗng", "chưa bật ẩn danh". Thêm vào `e…` làm sai các bảng đó. Tin của công ty test 2 không ở trạng thái OPEN công khai nên bảng việc làm công khai không đổi |
| P3 | Tầng 2b = `INSERT … ON CONFLICT (id) DO NOTHING` cho dòng mới + `UPDATE` cho cột đổi trên dòng có sẵn (embedding hồ sơ, `rubrics.is_locked`) | Hồ sơ là dòng tầng 1; dump nguyên bảng sẽ trùng khoá. Tiền lệ R3 (`seed-test.sql:324`) đã thêm `ON CONFLICT` vào output dump |
| P4 | CV builder demo làm cho **Bùi Ngọc Mai**, 2 phiên bản (Cổ điển rồi sửa thành Hiện đại), **không phải CV chính** | Không phát sinh embedding; Mai vẫn không nộp đơn nào (giữ ca ngưỡng gợi ý của demo); Hoàng Minh Tuấn giữ vai "chưa có CV" |
| P5 | Sửa rubric DevOps cho H14 (đổi tên + trọng số tiêu chí `d4…34`, thêm tiêu chí "Viết tài liệu vận hành") viết bằng SQL ở tầng 1b, không bấm UI trong lượt chạy AI | Rubric demo đang mở khoá (`seed-demo-structural.sql:387-388`), API sửa được nên đây là trạng thái hợp lệ; giữ tầng 2b chỉ chứa output AI |

### K2. Dải ID và quy tắc đặt

Dải cũ giữ nguyên: demo tầng 1 `d0`–`d6` (nhóm 4 = `0000`), demo tầng 2 UUID ngẫu nhiên, test `e0`–`ef`, `f0`, `f1`.
`dev-seed.sql` dùng `c0000000-…`, `10000000-…` (không đụng).

Dải mới, dạng `PPPPPPPP-TTTT-0000-0000-NNNNNNNNNNNN`:

- `PPPPPPPP` = `d1b00000` cho **demo tầng 1b**, `f2000000` cho **test bổ sung**;
- `TTTT` = mã bảng (bảng dưới);
- `NNNN…` = số thứ tự 12 chữ số trong bảng đó.

Không va chạm với dải cũ (`d1b…` khác `d1000000`, `f2…` khác `f0`/`f1`). Truy dọn theo tiền tố: `LIKE 'd1b00000-%'`,
`LIKE 'f2000000-%'`.

| TTTT | Bảng | TTTT | Bảng |
|---|---|---|---|
| 0001 | users | 0010 | application_status_history |
| 0002 | companies | 0011 | application_screening_answers |
| 0003 | candidate_profiles | 0012 | interview_invitations |
| 0004 | jobs | 0013 | interview_invitation_slots |
| 0005 | rubrics | 0014 | application_messages |
| 0006 | rubric_criteria | 0015 | notifications |
| 0007 | interview_templates | 0016 | talent_pools |
| 0008 | job_screening_questions | 0017 | talent_pool_entries |
| 0009 | job_applications | 0018 | job_blind_review_changes |

Tầng 2b: UUID ngẫu nhiên do app sinh trong lượt chạy thật (như tầng 2). Tên file trên đĩa: logo
`logos/<id công ty>.png`; đính kèm `message-attachments/<id tin nhắn>.<pdf|png>` (khoá thật của app là
`message-attachments/<uuid>.<ext>`, `MessageService.java:306-309`).

Mốc thời gian: theo quy ước có sẵn — sự kiện đã xảy ra dùng mốc **tuyệt đối** (09–10/2026, sau mốc tầng 2 ngày
28/08/2026); khung phỏng vấn còn hạn, hạn nộp tin dùng mốc **tương đối** theo lúc nạp. Thông báo viết tay đặt
`email_status = 'SKIPPED'`.

### K3. Bảng mục tiêu theo trạng thái (mục 3 của khảo sát)

Ký hiệu đơn demo tầng 2 (UUID đầy đủ ở `seed-demo-ai-output.sql`):
`APP-JAVA-HOANG` = `3ec35ae2-c8ae-4100-a819-3b59f6948707`, `APP-JAVA-HA` = `967d5c84-1519-4a70-8bed-6252781ac052`
(FAILED), `APP-QA-BAO` = `a611e169-fa6c-4b6f-a1fd-da36503ae0ac`, `APP-QA-DUC` = `1029b885-ddae-4417-8e0c-8284d6198f79`
(WITHDRAWN), `APP-DEVOPS-SON` = `31447cc8-8702-442e-8fbc-9dd320afef02`, `APP-DEVOPS-DUC` =
`bb83d9cd-cb26-4ef3-9970-1f9b347deba4`, `APP-KT-HA` = `cd3857e1-71b4-4f46-bd81-b04c897e2cd0`, `APP-MKT-LINH` =
`28f563b1-603b-4191-b5bd-23bc65a9af5b`, `APP-SALES-TUNG` = `dd1d46b2-fb7e-49f9-9976-594c7cd8237d`.

| FR | Trạng thái | Bộ · tầng | Dữ liệu / ID |
|---|---|---|---|
| C02/U07/U13 | ≥ 11 tin OPEN, chia trang | Demo · 1b | Công ty 2 `d1b00000-0002-…-000000000001`, HR `d1b00000-0001-…-01`, 6 tin `d1b00000-0004-…-01..06` (OPEN, `published_at` rải, 1 tin không công bố lương), rubric `0005`/`0006`, mẫu giấy mời `0007` dùng `{{candidateName}}` → demo 12 tin OPEN |
| C02 | Tin xoá mềm (ẩn khỏi trang công khai) | Test · f2 | Tin `f2000000-0004-…-03` OPEN + `deleted_at` |
| C03 | Đủ 7 loại thông báo; đã đọc/chưa đọc ở demo | Demo · 1b | `d1b00000-0015-…`: STATUS_CHANGED, SUBMITTED, `INTERVIEW_SLOT_CONFIRMED`, `NEW_MESSAGE`; `UPDATE` một phần thông báo tầng 2 thành đã đọc. RESENT đã có ở test (`ec…17`) |
| C03 | `NEW_MESSAGE`, `INTERVIEW_SLOT_CONFIRMED` ở test | Test · f2 | `f2000000-0015-…` |
| C06 | Tin có đính kèm PDF và PNG | Demo · 1b + Test · f2 | Demo: `d1b00000-0014-…` trên `APP-SALES-TUNG` (HR gửi PDF), `APP-MKT-LINH` (HR gửi, ứng viên chưa đọc); ứng viên Tùng gửi PNG. Test: `f2000000-0014-…`. File: demo commit ở `db/seed/demo-files/message-attachments/`, test sinh trong `install-test-files.ps1` |
| H01 | Logo công ty | Demo · 1b | `UPDATE companies SET logo_url` cho `d1000000-…-01` và `d1b00000-0002-…-01`; PNG ở `db/seed/demo-files/logos/` |
| H07/H08 | Phễu đủ trạng thái trong công ty có điểm | Demo · 1b (B1) | `APP-KT-HA` → INTERVIEW_INVITED → HIRED; `APP-QA-BAO` → REJECTED; `APP-MKT-LINH` → INTERVIEW_INVITED; `APP-SALES-TUNG` → INTERVIEW_INVITED. Lịch sử `d1b00000-0010-…`. Điểm/giải thích không đổi. Phễu công ty 1 sau 1b + 2b: PENDING 5, INTERVIEW_INVITED 3, HIRED 1, REJECTED 1, WITHDRAWN 1 |
| H12 | Giấy mời một khung đã chốt (đơn HIRED) | Demo · 1b | `APP-KT-HA`: giấy mời `d1b00000-0012-…-01`, 1 khung đã qua, chốt lúc gửi |
| U10 | **Trước**: nhiều khung, chưa chọn | Demo · 1b | `APP-MKT-LINH`: giấy mời `…0012-…-02`, 3 khung tương lai |
| U10 | **Sau**: ứng viên đã chọn khung | Demo · 1b + Test · f2 | `APP-SALES-TUNG`: giấy mời `…0012-…-03`, 2 khung, `confirmed_at` > `sent_at` + thông báo `INTERVIEW_SLOT_CONFIRMED` cho HR. Test: đơn `f2000000-0009-…-02` |
| H10/U09 | Câu hỏi sàng lọc + bản chụp CAPTURED ở demo | Demo · 1b | Job DevOps `d2…03`: 2 câu `d1b00000-0008-…-01..02` (YES_NO, SHORT_TEXT — đúng câu chữ H14 7.4 B1), `screening_revision` = 2 |
| H10 | Câu hỏi đã xoá mềm, bản chụp vẫn giữ câu đó | Test · f2 | Tin `f2000000-0004-…-01`: 3 câu, câu 3 có `deleted_at` sau thời điểm nộp của đơn `f2…0009-…-01` |
| H14 | 3 cột, sàng lọc CAPTURED, rubric khác phiên bản | Demo · 1b (P5) + 2b | 1b: sửa rubric DevOps (P5), đơn **Trần Minh Hoàng → DevOps** `d1b00000-0009-…-01` (CV `0b716edc…`, PENDING, câu trả lời `0011`, lịch sử, thông báo nộp đơn). 2b: lượt chấm DONE + `criterion_scores` + giải thích do AI thật sinh |
| H15 | **Sau** ở demo: kho có mục, ACTIVE, REVOKED (mục đã gỡ), kho rỗng, kho đã xoá | Demo · 1b | `UPDATE` đồng ý: `APP-KT-HA` ACTIVE, `APP-DEVOPS-SON` ACTIVE, `APP-DEVOPS-DUC` REVOKED. Kho `d1b00000-0016-…-01` "Kế toán tiềm năng" (Hà), `-02` "Kỹ thuật hạ tầng" (Sơn hiệu lực; Đức `removed_at` = lúc rút), `-03` "Marketing 2027" (rỗng), `-04` (`deleted_at`). Mục `0017` |
| H15 | **Trước** ở test | Test · e (giữ nguyên) | Kho rỗng; A1 ACTIVE; A7, A2 NOT_GIVEN (H15 7.4 nhóm A–C) |
| H15 | **Sau** ở test | Test · f2 | Kho `f2000000-0016-…`, đơn ACTIVE trong kho, đơn REVOKED có mục đã gỡ |
| H16 | **Sau** ở demo: Job bật ẩn danh, lịch sử bật/tắt, đơn ẩn danh cạnh đơn đã mời (tên thật) | Demo · 1b | Job Java `d2…01` `blind_review_enabled = TRUE`; lịch sử `d1b00000-0018-…-01..03` (bật → tắt → bật). Đơn mới **Võ Thanh Tùng → Java** `d1b00000-0009-…-02` (CV `1654697f…`), nộp khi đang ẩn danh, rồi INTERVIEW_INVITED (bỏ ẩn danh). `APP-JAVA-HOANG`, `APP-JAVA-HA` hiện nhãn ẩn danh |
| H16 | **Trước** ở test | Test · e (giữ nguyên) | J1 chưa bật (H16 7.4 "Bật ẩn danh J1 trước") |
| H16 | **Sau** ở test | Test · f2 | Tin `f2000000-0004-…-01` bật ẩn danh + lịch sử `0018`; đơn `-01` ẩn danh, đơn `-02` đã mời |
| U12 | CV dựng bằng công cụ, có phiên bản sửa | Demo · 2b (P4) | Bùi Ngọc Mai: 2 CV builder (CLASSIC, rồi "Lưu thành CV mới" MODERN), không phải CV chính; `resumes` + `resume_parsed_data` (`cv-builder-v1`) + `cv_builder_documents` + 2 PDF commit vào `db/seed/resumes/` |
| U15/U04 | Embedding tin công ty 2 + 4 hồ sơ demo đủ dữ liệu | Demo · 2b | `job_embeddings` 6 dòng mới; `UPDATE candidate_profiles SET embedding, embedding_model` cho `d6…02`, `…06`, `…07`, `…10` |
| U13 | Mọi tin OPEN của demo có embedding, ≥ 11 tin | Demo · 2b | 6 tin tầng 2 + 6 tin 2b = 12 |
| C01, C04, H02, H03, H04–H06, U01, U03, U05, U06, U08, U14 | Như khảo sát mục 3 | Không đổi | Đã có ở demo tầng 1–2 và test `e`/`f1` |

### K4. File sẽ tạo/sửa theo đợt

| Đợt | Tạo | Sửa |
|---|---|---|
| 1 | `docs/walkthrough/chore-seed-final.md` (phần này) | — |
| 2 | — | `db/seed/seed-demo-structural.sql` (6 mẫu giấy mời → `{{candidateName}}`, bỏ ngày giờ khỏi thư theo R-C1 của H12; comment mật khẩu `:39`); `db/seed/reset-demo-db.sql` (liệt kê đủ bảng, sửa comment số bảng); `db/seed/install-demo-files.ps1` (chỉ xoá/ghi đè file có trong nguồn của seed; nguồn gồm `resumes/`, `demo-files/logos/`, `demo-files/message-attachments/`; kiểm mọi `file_url`/`logo_url`/`attachment_key` của **dữ liệu demo** có file nguồn); `.gitattributes` (thêm `*.png binary -text`); `db/seed/README.md` (ghi các sửa trên) |
| 3 | `db/seed/seed-demo-structural-1b.sql`; `db/seed/demo-files/logos/*.png` (2); `db/seed/demo-files/message-attachments/*` (PDF, PNG) | `db/seed/install-demo-files.ps1` (nếu cần thêm nguồn) |
| 4 | — | `db/seed/seed-test.sql` (mục 17+ dải `f2`); `db/seed/reset-test-data.sql` (thêm 3 user `f2`); `db/seed/install-test-files.ps1` (2 CV + 2 đính kèm `f2`) |
| 5 | `db/seed/export-demo-ai-2b.ps1`; `db/seed/seed-demo-ai-output-2b.sql`; 2 PDF CV builder trong `db/seed/resumes/` | — |
| 6 | Phần còn lại của walkthrough này | `db/seed/README.md` (mục 1, 4, 6, 7, 8); `docs/ROADMAP.md` |

Không file Java/TypeScript nào thay đổi. `dev-seed.sql` **không xoá** ở nhánh này (để nhánh sau).

### K5. Thứ tự nạp cuối cùng

Tiền đề (một lần): DB phải ở V19. Khởi động backend một lần với mọi scheduler gọi AI **tắt**, chờ log Flyway xong
rồi dừng:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--app.job-embedding.enabled=false --app.candidate-profile-embedding.enabled=false --app.resume-embedding.enabled=false --app.resume-parsing.enabled=false --app.scoring.enabled=false --app.explanation.enabled=false --app.cv-improvement.enabled=false --app.resume-reparse.enabled=false"
```

**Demo** (từ gốc repo, luôn `docker compose cp` rồi `psql -f`, không pipe qua `Get-Content`):

```powershell
docker compose cp db\seed\reset-demo-db.sql postgres:/tmp/reset-demo-db.sql
docker compose exec -T postgres psql -U recruitment -d recruitment -v DEMO_SEED_CONFIRM=YES_RESET_DEMO_DB -f /tmp/reset-demo-db.sql
docker compose cp db\seed\seed-demo-structural.sql postgres:/tmp/seed-demo-structural.sql
docker compose exec -T postgres psql -U recruitment -d recruitment -v ON_ERROR_STOP=1 -f /tmp/seed-demo-structural.sql
docker compose cp db\seed\seed-demo-ai-output.sql postgres:/tmp/seed-demo-ai-output.sql
docker compose exec -T postgres psql -U recruitment -d recruitment -v ON_ERROR_STOP=1 -f /tmp/seed-demo-ai-output.sql
docker compose cp db\seed\seed-demo-structural-1b.sql postgres:/tmp/seed-demo-structural-1b.sql
docker compose exec -T postgres psql -U recruitment -d recruitment -v ON_ERROR_STOP=1 -f /tmp/seed-demo-structural-1b.sql
docker compose cp db\seed\seed-demo-ai-output-2b.sql postgres:/tmp/seed-demo-ai-output-2b.sql
docker compose exec -T postgres psql -U recruitment -d recruitment -v ON_ERROR_STOP=1 -f /tmp/seed-demo-ai-output-2b.sql
.\db\seed\install-demo-files.ps1
```

**Test** (độc lập; nạp chung với demo thì nạp sau demo):

```powershell
docker compose cp db\seed\reset-test-data.sql postgres:/tmp/reset-test-data.sql
docker compose exec -T postgres psql -U recruitment -d recruitment -v TEST_SEED_CONFIRM=YES_RESET_TEST_DATA -f /tmp/reset-test-data.sql
docker compose cp db\seed\seed-test.sql postgres:/tmp/seed-test.sql
docker compose exec -T postgres psql -U recruitment -d recruitment -v ON_ERROR_STOP=1 -f /tmp/seed-test.sql
.\db\seed\install-test-files.ps1
```

Sau khi sửa ở đợt 2, `install-demo-files.ps1` chỉ đụng file của chính nó nên không còn ràng buộc thứ tự với
`install-test-files.ps1`.

### K6. Ước tính số lượt gọi AI cho tầng 2b (chờ duyệt trước khi chạy)

Điều kiện lượt chạy: DB chỉ nạp **demo** (1 → 2 → 1b), **không** nạp bộ test; tắt `resume-parsing`, `cv-improvement`,
`resume-reparse` (không có việc cần làm, tắt để chặn gọi ngoài dự kiến).

| Nhà cung cấp | Việc | Dự kiến | Trần |
|---|---|---|---|
| Anthropic | Chấm đơn Hoàng → DevOps: 5 tiêu chí (rubric sau P5), mỗi tiêu chí 1 lượt (H14 REQ 7.4 B4: "5 tiêu chí + 1 giải thích") | 5 | 10 (mỗi tiêu chí thử lại tối đa 1 lần khi JSON hỏng) |
| Anthropic | Giải thích điểm (D4) | 1 | 2 |
| Anthropic | Chấm lại nếu lượt FAILED (vd `EVIDENCE_NOT_VERIFIED`) — tối đa 1 lần chấm lại | 0 | 12 |
| OpenAI | Embedding 6 tin công ty 2 | 6 | 6 |
| OpenAI | Embedding 4 hồ sơ demo đủ dữ liệu (`d6…02`, `…06`, `…07`, `…10`; 5 hồ sơ còn lại rỗng nên không embed — R-E2) | 4 | 4 |
| — | CV builder (P4): lưu không gọi AI (`model = 'cv-builder'`); không phải CV chính nên không embed | 0 | 0 |
| **Tổng** | | **Anthropic 6, OpenAI 10** | **Anthropic 24, OpenAI 10** |

Không tính lượt thử lại ngầm của SDK Anthropic (không tắt được qua `ChatModel` — CLAUDE.md §7). Quy tắc dừng: hai lượt
chấm liên tiếp FAILED thì dừng, báo lại, không chấm thêm.

### K7. Kế hoạch 6 đợt và điểm dừng an toàn

| Đợt | Việc | Kiểm chứng | Điểm dừng an toàn |
|---|---|---|---|
| 1 | Kế hoạch (file này) | Khảo sát chỉ đọc trên DB dev | Không đổi gì ngoài tài liệu |
| 2 | Sửa seed cũ (K4 đợt 2); người dùng khởi động backend một lần để lên V19 (K5) | Reset → tầng 1 → tầng 2 → install; kiểm README mục 7; câu đếm mẫu giấy mời có `{{candidateName}}` = 6; nạp bộ test sau đó vẫn sạch | Quy trình demo/test cũ vẫn chạy nguyên, chỉ sửa lỗi |
| 3 | Tầng 1b + logo + đính kèm demo | Reset → 1 → 2 → 1b → install; câu đếm theo dải `d1b00000-%`; chạy 1b lần hai không nhân đôi; kiểm chứng bằng SQL, **không** bật backend có khoá AI | Demo đủ trạng thái không cần AI; 2b chưa có thì H14 thiếu cột 3, demo vẫn chạy |
| 4 | Test `f2` | Reset test → seed-test → install ×2; số dòng dải `e…`/`f1` không đổi so với trước | Bộ test cũ y nguyên, thêm phần `f2` |
| 5 | **Duyệt K6** → lượt chạy AI thật → `export-demo-ai-2b.ps1` → `seed-demo-ai-output-2b.sql` + PDF CV builder | Reset → nạp đủ chuỗi demo từ đầu; H14 Job DevOps có 3 đơn DONE; 12 tin OPEN đều có embedding; 4 hồ sơ có embedding | Dump đã commit, nạp lại từ DB sạch ra đúng kết quả |
| 6 | README, phần còn lại walkthrough, ROADMAP | Nạp sạch toàn bộ hai lần từ DB trống (demo, rồi demo + test); soát theo K3 | Nhánh sẵn sàng merge |

Mỗi đợt: dừng → báo diff → chờ duyệt → mới commit.

### K8. Giới hạn chấp nhận

- **C05 CV v1** (nút "Cập nhật dữ liệu trích xuất"): không seed; cả 9 CV demo đã là v2 (B3).
- **AI chỉ có lúc dùng, không seed được**: C07 soạn nháp tin nhắn, C08 hỏi đáp CV, U12 gợi ý diễn đạt, U13 tách câu
  mô tả và embedding câu truy vấn, H11 sinh nháp tin. Soát/demo các chức năng này cần khoá API thật.
- **Bộ test không có điểm AI** (giữ quy ước `db/seed/README.md` mục 8). Embedding 2 tin OPEN + 1 hồ sơ của bộ test
  vẫn do scheduler tính lúc chạy (3 lượt OpenAI, như hiện nay), không đông cứng.
- **H14**: không có ca 4 đơn DONE cùng Job và ca khác chu kỳ (giới hạn đã ghi ở H14 REQ 7.4); backend có test tự động.
- **U10 "mọi khung đã qua"**: không seed (U10 O5 giữ nguyên cách soát bằng câu SQL lùi giờ).
- **CV builder dùng để nộp đơn và chấm điểm**: không seed (Mai không nộp đơn — P4).
- **Đồng ý lưu hồ sơ trên đơn demo tầng 2** (`APP-KT-HA`, `APP-DEVOPS-SON`, `APP-DEVOPS-DUC`) gán bằng `UPDATE`,
  `talent_pool_consent_at = applied_at` — trạng thái API tạo được nếu đơn nộp sau FR-U09.
- **Bảng soát tay 7.4 của H14 (nhóm A–B) và H16** mô tả dữ liệu demo gốc; sau 1b/2b nhóm B của H14 đã dựng sẵn và Job
  Java hiện nhãn ẩn danh. Không sửa REQUIREMENT đã hoàn thành; README seed ghi rõ khác biệt.
- Dữ liệu soát tay để lại trên DB dev (`u12-…`, `6@gmail.com`, `7@gmail.com`, 2 `cv_builder_documents`) bị
  `reset-demo-db.sql` xoá; `reset-test-data.sql` không đụng tới.
