# Walkthrough: chore/seed-final — Bộ dữ liệu cuối cho soát tay và demo

> Trạng thái: **Đợt 1–6 xong, chờ soát tay**. Mục "Kết quả" chỉ ghi lại việc đã xảy ra; mục "Soát tay" để người
> duyệt điền.

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
| | | 0019 | resumes (L19, đợt 4) |
| | | 0020 | saved_jobs (FR-U16, tầng 3 `d3a00000-`) |
| | | 0021 | application_reconsideration_requests (FR-C09, tầng 3 `d3a00000-`, test mục 18 `f3000000-`) |

Tầng 2b: UUID ngẫu nhiên do app sinh trong lượt chạy thật (như tầng 2). Logo: cột `companies.logo_url` lưu
dạng URL công khai `'/uploads/logos/<id công ty>.png'` (`LocalStorageService.java:41`), file trên đĩa ở
`backend/uploads/logos/<id công ty>.png`; đính kèm `message-attachments/<id tin nhắn>.<pdf|png>` (khoá thật của app là
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
| U15/U04 | Embedding tin công ty 2 + 4 hồ sơ demo đủ dữ liệu | Demo · 2b | `job_embeddings` 6 dòng mới; `UPDATE candidate_profiles SET embedding, embedding_model` cho `d6…01`, `…05`, `…06`, `…09` (Hoàng, Hà, Linh, Tuấn — sửa đợt 6 theo số đo, L29) |
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
| Anthropic | Chấm đơn Hoàng → DevOps: 5 tiêu chí (rubric sau P5), mỗi tiêu chí 1 lượt (H14 REQ 7.4 B4: "5 tiêu chí + 1 giải thích") | 5 | 14 (mỗi tiêu chí thử lại tối đa 1 lần khi output hỏng; lỗi tạm thời đưa lượt về PENDING, tối đa 3 lần thử — L32) |
| Anthropic | Giải thích điểm (D4) | 1 | 6 (tối đa 3 lần thử × 2 — L32) |
| OpenAI | Embedding 6 tin công ty 2 | 6 | 6 |
| OpenAI | Embedding 4 hồ sơ demo đủ dữ liệu (`d6…01`, `…05`, `…06`, `…09`; 5 hồ sơ còn lại rỗng nên không embed — R-E2) | 4 | 4 |
| — | CV builder (P4): lưu không gọi AI (`model = 'cv-builder'`); không phải CV chính nên không embed | 0 | 0 |
| **Tổng** | | **Anthropic 6, OpenAI 10** | **Anthropic 24 (trần duyệt; trần theo code 20), OpenAI 10** |

Sửa ở đợt 6: mã hồ sơ theo số đo (L29); bỏ dòng "chấm lại nếu lượt FAILED" (12 lượt) và thay trần từng dòng bằng
trần theo code (L32). Không tính lượt thử lại ngầm của SDK Anthropic (không tắt được qua `ChatModel` — CLAUDE.md §7).
Quy tắc dừng: lượt chấm FAILED thì dừng, báo lại, không tự chấm lại (L32).

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

### K9. Làm rõ (phát sinh ở đợt 2, đã duyệt)

| Mã | Nội dung | Quyết định |
|---|---|---|
| L1 | `reset-demo-db.sql` đã đòi V19 từ commit FR-H16 (liệt kê `job_blind_review_changes`), không chạy được trên V18 như kế hoạch giả định. Ca âm trên V18: `TRUNCATE` báo `relation does not exist`, exit 3, users 18 → 18, không xoá gì | Không thêm chốt chặn thứ 4; ghi điều kiện "DB ở V19 trở lên" vào comment script và README mục 1. Người dùng lên V19 bằng lệnh K5 (bước 1 đợt 2 xác nhận đủ 8 cờ có thật, 3 scheduler còn lại không gọi AI) |
| L2 | Danh sách `TRUNCATE` cũ có 26 bảng (comment ghi 22) | Liệt kê đủ 30 bảng nghiệp vụ V1–V19, đối chiếu `information_schema`. Không truncate `catalog_*`, `flyway_schema_history`, `vector_store` (Spring AI tự tạo, `application.yml:58-59`, 0 dòng) |
| L3 | `companies.logo_url` là URL công khai `/uploads/logos/<tên>` (`LocalStorageService.java:41`), khác `resumes.file_url` (`resumes/<tên>`) và `attachment_key` (`message-attachments/<tên>`) | Tầng 1b ghi `logo_url = '/uploads/logos/<id>.png'`; K2 đã sửa theo |
| L4 | Mẫu giấy mời demo chỉ đổi `[Tên ứng viên]`, tên job/công ty vẫn ghi cứng | Đổi sang đủ 3 placeholder `{{candidateName}}`, `{{jobTitle}}`, `{{companyName}}` ở cả tiêu đề và thân thư, như bộ test |
| L5 | Comment `seed-demo-structural.sql:38` ghi "8 candidate" | Sửa thành 9 cùng với comment mật khẩu `:39` |
| L6 | Comment mật khẩu `Demo1234` lấy theo đầu file và README, chưa kiểm hash bcrypt độc lập | Kiểm bằng đăng nhập ở đợt 6 |
| L7 | `install-demo-files.ps1` không còn dọn file thừa trong `backend/uploads/` (file soát tay cũ nằm lại) | Chấp nhận: DB sau reset không trỏ tới chúng; logo cũ `d1000000-…01.jpg` khác đuôi `.png` của đợt 3 nên không đè |
| L8 | Câu đối chiếu của `install-demo-files.ps1` đã gồm dải `d1b00000-%` | Đợt 3 không cần sửa script nếu file nguồn đặt đúng `demo-files/logos/`, `demo-files/message-attachments/` |
| L9 | README mục 1 còn thiếu `-v ON_ERROR_STOP=1` ở lệnh nạp (K5 đã có) | Sửa ở đợt 6 khi viết lại mục 1 |

Phát sinh ở đợt 3 (chờ duyệt):

| Mã | Nội dung | Quyết định |
|---|---|---|
| L10 | K3 ghi 3 giấy mời, nhưng đơn Võ Thanh Tùng → Java ở `INTERVIEW_INVITED` mà không có giấy mời là trạng thái app không tạo được: PATCH trạng thái chặn đích `INTERVIEW_INVITED` (`ApplicationStatusController.java:40-43`), đường duy nhất là gửi giấy mời (`InterviewInvitationService.sendInvitation`) | Thêm giấy mời thứ 4 `d1b00000-0012-…-04` (1 khung tương lai, chốt lúc gửi, `{{candidateName}}` đã thay bằng họ tên thật — H16 R-I2). Tầng 1b: 4 giấy mời, 7 khung |
| L11 | "Xoá dải `d1b` rồi chèn lại" sẽ xoá dây chuyền dữ liệu 2b nếu chạy lại 1b sau 2b: `scoring_runs.application_id` và `job_embeddings.job_id` là `ON DELETE CASCADE`, `criterion_scores.criterion_id` là `SET NULL` (tiêu chí P5 `d1b00000-0006-…-01`) | Thêm chốt chặn 3 đầu file: có dòng 2b trỏ tới dải `d1b` thì `RAISE EXCEPTION`, không xoá gì (ca âm đã chạy). Đã nạp 2b thì nạp lại từ đầu theo K5, không chạy lại riêng 1b |
| L12 | Trigger `set_updated_at` (V1) ghi đè `updated_at = now()` ở mọi `UPDATE` → chạy lại 1b ra giá trị khác | Tắt tạm 4 trigger `trg_<bảng>_updated_at` (`job_applications`, `jobs`, `companies`, `rubric_criteria`) trong transaction của file, bật lại trước `COMMIT`. Đơn tầng 2 nhận `updated_at` tuyệt đối của lần ghi cuối (gồm cả lúc ứng viên chọn khung — `lockIfInterviewInvited` + trigger, và lúc rút đồng ý); dòng tầng 1 giữ `updated_at` = giờ nạp tầng 1. Trước khi bật lại phải `SET CONSTRAINTS ALL IMMEDIATE` (constraint trigger `trg_rubric_weight_sum` DEFERRED còn sự kiện chờ thì `ALTER TABLE` bị từ chối — gặp ở lần chạy đầu, exit 3, transaction rollback). Hệ quả: vân tay các dòng tầng 1 khác nhau giữa hai lần nạp từ DB trống, giống nhau khi chạy lại 1b trên cùng một lần nạp |
| L13 | Lệch thời gian có sẵn từ trước: `jobs.created_at`/`published_at` tầng 1 tính tương đối theo lúc nạp (≤ 35 ngày), còn đơn tầng 2 ghi tuyệt đối 28/08/2026 → nạp sau khoảng cuối 10/2026 thì đơn có trước ngày đăng tin. Sự kiện 1b (09–10/2026, tuyệt đối theo K2) thừa hưởng cùng độ lệch (vd bật ẩn danh Job Java 20/09) | Không sửa ở nhánh này (tầng 1/2 ngoài phạm vi đợt 3). Ghi vào README ở đợt 6 |
| L14 | K3 ghi phễu công ty 1 "sau 1b + 2b"; đo sau 1b (chưa 2b) đã ra đúng PENDING 5, INTERVIEW_INVITED 3, HIRED 1, REJECTED 1, WITHDRAWN 1 | Không chênh: 2b chỉ thêm lượt chấm/embedding, không thêm đơn |
| L15 | `application_messages.attachment_size` ghi cứng số byte của file nguồn (19770, 29044). File sinh một lần bằng chương trình Java tạm (PDFBox 3.0.3 + Noto Sans của dự án cho PDF, AWT cho PNG), không commit chương trình | Sinh lại file thì phải sửa hai số này trong 1b; `install-demo-files.ps1` chỉ kiểm tồn tại, không kiểm kích thước |
| L16 | Công ty 2 (user, công ty, tin, rubric, mẫu giấy mời) dùng mốc **tương đối** (`now() - …`), không tuyệt đối như K2 | Tin công ty 2 cần `published_at` tương đối (rải ngày đăng, K3); công ty và HR lấy mốc trước tin để không tạo trước khi công ty tồn tại. Công ty 2 không có đơn/sự kiện nên không lệch với mốc tuyệt đối |

Phát sinh ở đợt 4 (đã duyệt 19/10/2026):

| Mã | Câu hỏi | Quyết định | Lý do |
|---|---|---|---|
| L17 | K3 chỉ ghi trạng thái tin `-03`; P2 nói tin công ty test 2 không OPEN công khai — tin `-01`, `-02` ở trạng thái nào? | Tin `-01` PAUSED, `-02` CLOSED, `-03` OPEN + `deleted_at` | Chọn khung, gửi tin, gửi giấy mời không kiểm trạng thái tin (`MessageService.java:380-382`, `InterviewInvitationService.java:139-166`); bảng việc làm công khai và embedding chỉ lấy `OPEN AND deleted_at IS NULL` (`JobRepository.java:50`, `JobEmbeddingRepository.java:26`) → bảng công khai không đổi, không thêm lượt OpenAI (K8 giữ 3) |
| L18 | Đơn `REVOKED` của H15 đặt ở đâu? | Thêm đơn `0009-03` (U1 → tin `-02` CLOSED): nộp có đồng ý → HR thêm vào kho → REJECTED → U1 rút đồng ý 20/09, `removed_at = revoked_at` | Đơn `-01` đang ẩn danh không thêm vào kho được (H16 R-K2); `-02` dùng cho mục ACTIVE; `uq_application_per_cycle` (V1:222) + 2 ứng viên → cần tin thứ hai |
| L19 | Bảng mã K2 không có mã cho `resumes` (K4 cần 2 CV `f2`) | `0019` = resumes; bổ sung vào bảng mã K2 | Giữ quy tắc truy dọn theo tiền tố |
| L20 | Hồ sơ nghề nghiệp của 2 ứng viên `f2`? | Hồ sơ "bỏ qua onboarding" như Lan/Khoa (`seed-test.sql:92-97`) | Không vào màn onboarding; hồ sơ rỗng nên không embedding |
| L21 | Trạng thái 2 CV `f2`? | PENDING, `next_attempt_at = 2099` | DONE cần output AI thật, viết tay bị cấm (`seed-test.sql:21-23`) |
| L22 | Đính kèm đặt trên đơn nào, kích thước ghi thế nào? | Trên đơn `-02`: `0014-01` HR → U2 kèm PDF (đã đọc), `0014-02` U2 → HR kèm PNG (HR chưa đọc). Thêm `0014-03` U1 → HR kèm tệp trên đơn ẩn danh `-01` (soát H16 R-T2), HR chưa đọc, kèm 1 thông báo `NEW_MESSAGE` cho HR theo quy tắc nhãn ẩn danh H16 (R-O1, R-O2). `attachment_size` ghi cứng đúng số byte tệp do `install-test-files.ps1` sinh. Tổng: application_messages 3, notifications 9, đính kèm 3 | Đơn `-02` đã hết ẩn danh → nhãn tên thật; `0014-03` cho trạng thái tên tệp ẩn + tải 409 phía HR |
| L23 | Lịch sử `0018` mấy dòng? | 1 dòng (bật) | Không có endpoint đọc lưu vết (H16 R-B5) |
| L24 | `install-test-files.ps1` không đối chiếu khoá file với DB | Thêm bước đối chiếu theo mẫu `install-demo-files.ps1`; chỉ đọc, lệch thì in rõ khoá/đường dẫn lệch và thoát mã khác 0; script vẫn chỉ ghi file của chính nó | Bắt thiếu file như bộ demo |
| L25 | Comment/README ghi "5 tài khoản test" | Đợt này sửa comment đầu `seed-test.sql` (số tài khoản test mới); README để đợt 6 | README viết lại ở đợt 6 (K4) |
| L26 | Grep tên cấm (CLAUDE.md §7) khớp `sender_label` trên dòng thêm của đợt 4 (cùng `version_label` cột V1) — có phải vi phạm? | Giữ nguyên `sender_label`. **Đã xét**: 3 dòng khớp (`version_label` ×1, `sender_label` ×2) không phải vi phạm — lần soát sau không cần xét lại | `sender_label` là bí danh trong `SELECT`, không phải cột/field; đổi tên phải sửa 1b đã commit (`seed-demo-structural-1b.sql:592`) và kiểm lại |
| L27 | Đếm dải `e` bằng `LIKE 'e%'`/`~ '^e'` bắt nhầm UUID ngẫu nhiên của demo tầng 2 (đo được scoring_runs e = 3 thay vì 2) | Đợt 6 ghi vào README: đếm theo dải phải dùng tiền tố đủ 8 ký tự (`~ '^e[0-9a-f]000000-0000-0000-0000-'`), và `resume_parsed_data` của R3 có id ngẫu nhiên (`01ddeb0e…`) | Script đã commit đều dùng tiền tố đủ 8 ký tự; chỉ câu kiểm chứng viết tay bị ảnh hưởng |
| L28 | Bước đối chiếu của `install-test-files.ps1` chỉ so số byte với dải `f2` | Chấp nhận như hiện tại: so byte chỉ dải `f2`, dải `e` chỉ kiểm có file | Đã đo dải `e` khớp DB (574/837/779/71/787); số byte dải `f2` ghi cứng trong SQL (L22) nên cần chốt chặn riêng |

Phát sinh ở đợt 5 (đã duyệt 19/10/2026, trước lượt chạy AI):

| Mã | Nội dung | Quyết định |
|---|---|---|
| L29 | K3/K6 ghi 4 hồ sơ cần embedding là `d6…02, 06, 07, 10`; đo bằng câu chọn của `CandidateProfileRepository.findIdsNeedingEmbedding` ra `d6…01, 05, 06, 09` (Hoàng, Hà, Linh, Tuấn). Số lượng vẫn 4 | Lấy theo số đo; sửa K3/K6 ở đợt 6. Trước khi chạy xác nhận tài khoản demo xem gợi ý việc làm (U15) nằm trong 4 hồ sơ này |
| L30 | Lượt chấm H14 chỉ tạo được qua `POST /api/hr/applications/{id}/scoring-runs` (`ScoringRunHrController.java:33`) — 1b không tạo; CV builder P4 cần 2 lần `POST /api/candidates/cv-builder/resumes` | Lượt chạy gọi hai API này bằng `hr@demo.local` và `bui.ngoc.mai@demo.local`; nội dung form lấy nguyên dữ liệu đã trích xuất của CV hiện có của Mai (bản 2: mẫu MODERN + kỹ năng "Xây dựng khung năng lực"). Đăng nhập `Demo1234` thành công là bằng chứng hash cho L6 |
| L31 | Lượt chấm xong sinh 1 thông báo `SCORING_FINISHED` kèm email (`NotificationEventListener.java:145-150`); tầng 2 có 12 dòng cùng loại đều `SENT` | Giữ mail bật, ép SMTP về MailHog bằng tham số dòng lệnh. Trước khi khởi động: 0 thông báo `PENDING`. Sau khi chạy: MailHog nhận đúng 1 thư |
| L32 | Trần theo code của một lượt chấm: D2 14 lượt (5 × 2 + 2 lần thử lại tạm thời × 2, `hardening.llm.max-attempts: 3`), D4 6 lượt (3 lần × 2) — cao hơn từng dòng K6 nhưng tổng 20 ≤ 24 | Bỏ dòng "chấm lại 12" của K6: lượt chấm FAILED → dừng, hỏi, không tự chấm lại |
| L33 | Nạp 2b chạy `UPDATE` trên `candidate_profiles`/`rubrics` → trigger đặt `updated_at = now()` | Chấp nhận (cùng loại L12); ghi README ở đợt 6 |
| L34 | Kiểm nạp sạch dùng `docker compose down -v` → mỗi lần phải khởi động backend (tắt AI) để Flyway dựng schema; DB dev mất bộ test | Chấp nhận; ghi README ở đợt 6 |

Phát sinh ở đợt 5 sau lượt chạy AI (đã duyệt 19/10/2026):

| Mã | Câu hỏi | Quyết định | Lý do |
|---|---|---|---|
| L35 | Lần chạy đầu `export-demo-ai-2b.ps1` dừng ở chốt chặn của chính nó (`File SQL con ten schema tam seed2b_export. DUNG LAI.`, `export-demo-ai-2b.ps1:162`) vì pg_dump tự ghi comment `Schema: seed2b_export` — sửa thế nào? | Giữ như đã sửa: `sed` đổi cả dòng comment đó thành `Schema: public`; chốt chặn `:162` giữ nguyên | Tên schema tạm chỉ nằm ở comment, không ở lệnh SQL; chốt chặn vẫn bắt mọi chỗ khác còn sót |
| L36 | Gọi script kèm `2>$null` thì dừng với `NativeCommandError` — có phải lỗi script? | Không; đợt 6 ghi README: chạy `export-demo-ai-2b.ps1` không redirect stderr | PowerShell 5.1 biến dòng tiến trình stderr của `docker compose cp` thành lỗi dừng khi stderr bị redirect; chạy không redirect thì exit 0 |
| L37 | `application.yml:36-38` chỉ có `spring.mail.host`/`port`; lượt chạy thêm `--spring.mail.username=`, `--spring.mail.password=`, `--spring.mail.properties.mail.smtp.auth=false`, `--spring.mail.properties.mail.smtp.starttls.enable=false` | Đợt 6 ghi vào walkthrough lệnh khởi động backend đầy đủ của lượt chạy AI, gồm các tham số mail ép về MailHog | Tham số dòng lệnh ưu tiên hơn `.env`; MailHog nhận đúng 1 thư |
| L38 | Lượt chấm H14 có 3/5 tiêu chí 0 điểm, evidence rỗng (tổng 35,000) — có vi phạm "không có điểm trần trụi" (CLAUDE.md §8)? | Giữ nguyên; dùng làm ca demo "AI không bịa evidence khi CV trái ngành" | Trạng thái hợp lệ đã thiết kế: prompt `criterion-score-v1.st:40-43` buộc 0 điểm + evidence rỗng + giải thích trong `reasoning` khi CV không có thông tin; `CriterionScoringService.java:227` chỉ cho evidence rỗng khi điểm = 0; `EvidenceQuoteList.tsx:49-53` hiện "Không tìm thấy trích dẫn nào trong CV cho tiêu chí này." kèm lý giải |
| L39 | `export-demo-ai-2b.ps1` ghi cứng số dòng kỳ vọng (`$Expected`) của lượt chạy này | Chấp nhận; đợt 6 ghi README: chạy AI lại thì phải sửa `$Expected` | Kỳ vọng cứng bắt được dòng thừa/thiếu ngay lúc export |
| L40 | Chưa thử nạp 2b lần hai trên cùng một DB | Không thử; đợt 6 ghi README "2b chỉ nạp theo K5 (L11)" | Đã nạp 2b thì nạp lại từ đầu theo K5, như tầng 2 |
| L41 | Sau đợt 5 DB dev không còn bộ test (L34) | Chấp nhận | Nạp lại bộ test khi cần soát giao diện |
| L42 | `raw_text` của 2 CV builder trong `seed-demo-ai-output-2b.sql` chứa CRLF (PDFBox trích văn bản trên Windows); `* text=auto eol=lf` sẽ đổi thành LF khi commit — giữ hay chuẩn hoá? | Giữ nguyên byte bằng dòng `db/seed/seed-demo-ai-output-2b.sql -text` trong `.gitattributes`, chỉ đúng đường dẫn này | Tầng 2b là output thật đông cứng, không chỉnh; hai lần nạp sạch ở bước 12 đã chạy trên đúng bản CRLF nên vẫn còn giá trị. Tầng 2 (`seed-demo-ai-output.sql`) không có CR — không đổi gì ở file đó |

Phát sinh ở đợt 6 (đã duyệt 19/10/2026):

| Mã | Câu hỏi | Quyết định | Lý do |
|---|---|---|---|
| L43 | README mục 7 ghi khối gợi ý việc làm của Bùi Ngọc Mai "nhiều khả năng rỗng" (ca ngưỡng của chore/seed-demo, ROADMAP mục `chore/seed-demo`) — còn đúng không? | Không đúng; giữ phần sửa README mục 7 theo số đo. KHÔNG tạo ca "gợi ý rỗng" mới; ngưỡng trình bày bằng lời khi demo. Mục tiêu "ca ngưỡng gợi ý" của P4: không đạt, chấp nhận. Câu sai ở ROADMAP:465-467 (mục `chore/seed-demo`) để nhánh `docs/final` sửa | Đo bằng vector đã nạp (19/10/2026): độ tương đồng CV chính của Mai với tin OPEN cao nhất 0.6188 ("Chuyên viên Nhân sự tổng hợp", tầng 1b), và tin tầng 1 "Chuyên viên Marketing" đã 0.4774 — đều ≥ `MIN_SIMILARITY_SCORE = 0.40` (`JobRecommendationCandidateService.java:36`). Câu cũ đã sai từ trước nhánh này; 1b làm lệch thêm. Tạo ca mới phải sửa seed và chạy lại embedding, phá chuỗi nạp đã kiểm |
| L44 | Đợt 4 không lưu câu đo "md5 dải demo" — đợt 6 so thế nào? | md5 nội dung từng bảng (36 bảng, `string_agg` mọi dòng theo thứ tự text) trên DB chỉ có demo, so với sau khi reset bộ test | Mạnh hơn so riêng dải demo: bắt được cả dòng test sót lại lẫn dòng demo bị đổi |
| L45 | README có 6 khối PowerShell — khối nào không chạy ở đợt 6? | Khối "SAI — ĐỪNG LÀM THẾ NÀY" (mục 2) và khối khởi động backend gọi AI thật (mục 10) không chạy | Khối mục 2 là ví dụ sai có chủ đích; khối mục 10 gọi API trả phí, đợt 6 cấm gọi AI. Khối mục 10 đã chạy nguyên văn tham số ở đợt 5 |
| L46 | ROADMAP không có mục `chore/seed-final` để tick | Thêm một dòng `[x]` sau `fix/hr-job-edit-select` (mục gần nhất cùng loại) | Không đụng mục khác |

## Kết quả

### Đợt 4 — bộ test dải `f2` (đo lại ở đợt 6, DB demo + test)

- Dải `f2`: 3 user, 3 tin, 3 đơn, 3 tin nhắn (cả 3 có đính kèm), 9 thông báo, 2 mục kho ứng viên. Bộ test tổng
  8 tài khoản (5 dải `e` + 3 dải `f2`), 190 dòng có `id` thuộc dải test trên các bảng có cột `id` kiểu uuid.
- `install-test-files.ps1`: mọi khoá file của bộ test có file trên đĩa; số byte dải `f2` khớp DB.

### Đợt 5 — lượt chạy AI thật (19/10/2026)

| | Ước tính | Trần | Thực tế |
|---|---|---|---|
| Anthropic | 6 (5 tiêu chí + 1 giải thích) | 24 | **6**: 5 `criterion_scores` + 1 `score_explanations`, 0 dòng log lỗi/thử lại, `attempt_count` 0 |
| OpenAI | 10 (6 tin + 4 hồ sơ) | 10 | **10**: 0 WARN embedding |

- Lượt chấm đơn Trần Minh Hoàng → Kỹ sư DevOps: DONE, `total_score` 35,000, `token_usage` 13 149,
  `claude-sonnet-4-6`, `criterion-score-v1`. Tiêu chí (điểm/5, trọng số, số evidence): vận hành/CI-CD 2,00/35/3;
  Kubernetes/Docker/cloud 3,50/30/4; scripting 0/20/0; giao tiếp & xử lý sự cố 0/10/0; viết tài liệu vận hành
  0/5/0 (L38). Giải thích `score-explanation-v1`.
- MailHog nhận đúng 1 thư (`SCORING_FINISHED` gửi `hr@demo.local`); log backend 0 ERROR.
- CV builder của Bùi Ngọc Mai: 2 CV DONE, không phải CV chính. Đăng nhập `Demo1234` thành công (L6).
- `seed-demo-ai-output-2b.sql`: 20 INSERT + 5 UPDATE; không khoá API; email/số điện thoại duy nhất là dữ liệu giả
  của Mai (`example.com`) đã có ở tầng 2; cột lỗi đều NULL.

### Đợt 6 — nạp sạch và soát

| Kiểm | Kết quả |
|---|---|
| a. Nạp demo hai lần từ `down -v` (Flyway V19 bằng khối lệnh README mục 1, rồi khối nạp demo chạy nguyên văn) | Cả hai lần: 11/11 lệnh exit 0, 0 dòng `ERROR:`; 36 bảng giống nhau giữa hai lần và giống số đợt 5 (`job_embeddings` 12, `scoring_runs` 16, `criterion_scores` 62, `score_explanations` 13, `resumes` 11, `cv_builder_documents` 2, `notifications` 35); `install-demo-files.ps1` đối chiếu resume 11, logo 2, đính kèm 2 |
| b. Nạp bộ test → reset bộ test (khối README mục 8.1, 8.2) | Nạp: exit 0, đối chiếu file đạt. Reset: dải test 190 → 0 dòng, user test 8 → 0; md5 36 bảng trùng mốc chỉ có demo (đối chứng: lúc có bộ test lệch 46 dòng) — L44 |
| c. Đăng nhập API (backend tắt AI) | `hr@demo.local`, `bui.ngoc.mai@demo.local` (`Demo1234`), `minhdathr@gmail.com`, `dang.minh.thu.hr@test.local` (`12345678`): cả 4 HTTP 200 có `accessToken`; đối chứng sai mật khẩu → 401 |
| d. Khối lệnh README trên PowerShell 5.1 | 4/6 khối chạy nguyên văn ở a–c; 2 khối không chạy (L45) |
| e. Khoá API, đường dẫn máy cục bộ trong README/walkthrough/ROADMAP | 0 kết quả (đối chứng dương đạt) |

Trạng thái DB dev cuối đợt 6: demo đầy đủ (tầng 1 → 2 → 1b → 2b) + bộ test.

## Soát tay

Ngày 20/10/2026, người soát: Nguyễn Quốc Huy. Backend chạy với AI tắt.

| Màn hình | Tài khoản | Kết quả |
|---|---|---|
| So sánh tin Kỹ sư DevOps (H14): Sơn 81 / Đức 59,5 / Hoàng 35, hạng 1/2/3; badge trung tính, không nhãn/màu phán quyết; thông báo khác phiên bản rubric, "Chưa có dữ liệu" ở tiêu chí "Viết tài liệu vận hành" (P5); tóm tắt có nhãn "Do AI tạo" | hr@demo.local | Đạt |
| Tiêu chí 0 điểm của Hoàng khi mở rộng: "Không tìm thấy trích dẫn nào trong CV cho tiêu chí này." kèm lý giải (L38) | hr@demo.local | Đạt |
| Bật ẩn danh tin DevOps (H16): hộp thoại xác nhận; danh sách hiện "Ứng viên #…"; "Xem CV gốc" bị khoá | hr@demo.local | Đạt |
| Danh sách CV: 2 CV builder có "Sửa CV"; gợi ý việc làm có "Chuyên viên Nhân sự tổng hợp" (L43) | bui.ngoc.mai@demo.local | Đạt |
| Tin nhắn: "Ứng viên #00000001" với nội dung "[đã ẩn]"; Đỗ Minh Châu hiện tên thật; chuông 3 chưa đọc | dang.minh.thu.hr@test.local | Đạt |
| Đính kèm trên đơn ẩn danh: không hiện tên tệp, tải về báo lỗi (R-T2); đơn Đỗ Minh Châu có PDF + PNG, tải được | dang.minh.thu.hr@test.local | Đạt |
| Tin -01: đơn ẩn danh cạnh đơn tên thật; CV "Chờ xử lý", "chưa so sánh được" (L21); hồ sơ Đỗ Minh Châu có khung giờ đã chọn (U10); kho có 1 mục hiệu lực (H15) | dang.minh.thu.hr@test.local | Đạt |
| Danh sách tin f2: -01 Tạm dừng, -02 Đã đóng, -03 (xoá mềm) không hiện | dang.minh.thu.hr@test.local | Đạt |
| Danh sách tin bộ test cũ: đủ trạng thái, nhãn "Chưa chuẩn hoá" | minhdathr@gmail.com | Đạt |

Khi soát đã bật ẩn danh tin DevOps của bộ demo; DB đã được nạp lại từ đầu sau khi soát (xem việc 2).

