# FR-H10 — Câu hỏi sàng lọc theo Job

> Trạng thái: ĐÃ DUYỆT 10/10/2026. Q1–Q10 và bốn mâu thuẫn ở mục 0.b đã chốt 10/10/2026 (mục 11).
>
> Làm rõ sau Plan Mode 10/10/2026: L1–L6 (L1: R-Q10, mục 0 a14, mục 4.2, mục 8; L2: T5; L3: mục 4.2, bảng R-B;
> L4: mục 4.5, mục 9; L5: mục 4.1; L6: mục 9; L7: mục 4.1, T15; L8: T10; L9: UI.md mục 9) — chi tiết ở mục 12.

- Nhóm: HR
- Tóm tắt: HR đặt tối đa 10 câu hỏi sàng lọc cho một Job (trả lời ngắn, một lựa chọn, có/không; đánh dấu
  câu bắt buộc; sắp thứ tự) ở tab "Câu hỏi sàng lọc" của trang sửa Job. HR đọc câu trả lời đã chụp theo
  đơn ở tab "Sàng lọc" của trang hồ sơ đơn (FR-H09). Câu trả lời không đi vào chấm điểm AI, tổng điểm hay
  xếp hạng; hệ thống không tự loại ai theo câu trả lời.
- Phụ thuộc: FR-H02 — **Đã hoàn thành**. Tab Sàng lọc gắn vào trang của FR-H09 — **Đã hoàn thành**
  (`docs/SRS.md` mục 0).
- Nhánh: `feat/fr-h10-screening` (HEAD `ce27de0`).
- Mở rộng: FR-H02 (thêm tab ở `/hr/jobs/:id/edit`), FR-H09 (thêm tab ở `/hr/applications/:id`).
- Bên nhận phần còn lại: **FR-U09** làm đường ghi câu trả lời (form nộp đơn, kiểm câu bắt buộc ở backend,
  ghi bản chụp trong cùng giao dịch nộp đơn) và phần ứng viên xem lại câu trả lời của mình.

## 0. Đã đối chiếu code trước khi viết (CLAUDE.md §6)

Đối chiếu trên `feat/fr-h10-screening` tại `ce27de0` (10/10/2026).

### (a) Hiện trạng

| # | Điểm kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| a1 | Chưa có gì về sàng lọc | **Đúng** | `rg -i "screening\|sàng lọc"` trong `backend/src/main`, `frontend/src` → 0 dòng |
| a2 | Tạo Job là wizard 3 bước, **không có tab** | **Đúng** | `HrJobCreatePage.tsx:85-92` (Thông tin cơ bản → Lương & thời hạn → Mẫu giấy mời); tạo xong chuyển tới `/hr/jobs/{id}/edit?tab=rubric&created=1` (`:185`). Rubric cũng chỉ nhập **sau** khi Job tồn tại |
| a3 | `JobOwnerService.create` tạo Job + Rubric rỗng + Mẫu giấy mời trong một transaction, Job luôn `DRAFT` | **Đúng** | `JobOwnerService.java:66-95`, `:76`. Job `DRAFT` không nhận đơn: `ApplicationService.apply` dùng `findOpenJobById` (`ApplicationService.java:65-67`) |
| a4 | Trang sửa Job có 4 tab | **Đúng** | `HrJobEditPage.tsx:469` `VALID_TABS = ['job','template','rubric','applications']`; `TabsList` chỉ `w-fit` (`:496`), **chưa** có `overflow-x-auto` như trang hồ sơ đơn (`HrApplicationDetailPage.tsx:138`) |
| a5 | Trang hồ sơ đơn có 4 tab, chưa có Sàng lọc | **Đúng** | `HrApplicationDetailPage.tsx:18` `['cv','explanation','history','messages']`; FR-H09 R-P2 cấm dựng sẵn tab Sàng lọc |
| a6 | Nộp đơn chỉ nhận `jobId`, `resumeId`, `aiConsent`, `coverLetter` | **Đúng** | `ApplicationCreateRequest.java`; `JobApplyForm.tsx:53-58`; `ApplicationService.apply` ghi bằng `saveAndFlush` (`:88`) trong một `@Transactional` (`:61`) |
| a7 | Chấm điểm chỉ đọc rubric snapshot + `raw_text` | **Đúng** | `ScoringRunOrchestrator.java:69-76` (đọc `resume_parsed_data.raw_text`), `:94` gọi `criterionScoringService.score(criterion, rawText)`; chữ ký `CriterionScoringService.score(CriterionSnapshot, String)` (`CriterionScoringService.java:111`) |
| a8 | Giải thích (FR-H06) chỉ đọc điểm từng tiêu chí; tổng điểm/hạng chỉ từ điểm | **Đúng** | `ScoreExplanationOrchestrator.java:67-70`; `ScoreAggregator.aggregate(List<CriterionScoreInput>)` (`ScoreAggregator.java:42`); hạng `ApplicationOwnerService.assignRanks` (`:309`); sắp xếp chỉ `TOTAL_SCORE_DESC`, `APPLIED_AT_DESC` (`ApplicationSortOption.java:11-13`) |
| a9 | Khuôn kiểm quyền đã làm cứng ở `chore/hardening` | **Có hai thứ tự đang cùng tồn tại** | Đã làm cứng (công ty **trước**, rồi mới tra tài nguyên): `ApplicationOwnerService.loadOwnedJob` (`:341-352`), `HrApplicationAccess.loadOwned` (`:42-56`). Chưa làm cứng (tra Job trước): `JobOwnerService.loadOwned` (`:200-209`), `RubricOwnerService.loadOwnedRubric` (`:95-104`). FR-H10 theo khuôn đã làm cứng; không sửa hai service cũ |
| a10 | Tiền lệ xoá tài nguyên con của Job là **xoá cứng** | **Đúng — lệch CLAUDE.md §4** | `RubricOwnerService.deleteCriterion` gọi `criterionRepository.delete` (`:92`). Xem Q4 |
| a11 | Tiền lệ "chốt độ dài ở DB" | **Có** | `chk_app_msg_body_length` (`V12__application_messages.sql`). Ngược lại `cover_letter` không có giới hạn ở đâu (FR-H09 mục 0.f) |
| a12 | Migration kế tiếp | **V13** | Cao nhất là `V12__application_messages.sql`; V9 là migration Java (`V9__normalize_job_catalog_codes.java`, commit `f136a55`) |
| a13 | Mỗi công ty một HR | **Đúng** | `uq_company_per_owner` (`V2__company_unique_owner.sql`) — không có hai HR cùng sửa một Job, chỉ có hai tab của cùng một người |
| a14 | Không chỗ nào sắp xếp theo hay hiển thị `jobs.updated_at` (L1 — câu `UPDATE` tăng `screening_revision` làm trigger `trg_jobs_updated_at` đổi cột này) | **Đúng — không bị ảnh hưởng** | Soát 10/10/2026 bằng `grep -rn -i "updated_at\|updatedAt"` trên `backend/src/main/java`, `frontend/src`. Backend: `jobs.updated_at` chỉ được map ở `Job.java:94-95` và trả ra ở `JobOwnerService.java:308` (`JobOwnerResponse.java:32`); `ORDER BY updated_at` duy nhất là của `candidate_profiles` (`CandidateProfileRepository.java:38`), không phải `jobs`; không có `Sort.by(...updated...)` hay `OrderByUpdated…`. `V9__normalize_job_catalog_codes.java:50,90` chỉ tắt/bật trigger trong migration. Frontend: `updatedAt` của Job chỉ được khai kiểu ở `features/jobs/ownerTypes.ts:26`; `grep "updatedAt" --include=*.tsx` → 0 dòng (không màn hình nào hiển thị) |

### (b) Mâu thuẫn đã chốt (10/10/2026)

| # | Mâu thuẫn | Đã chốt |
|---|---|---|
| M1 | README mục FR-H10 ghi "Khi tạo/sửa Job → tab Câu hỏi sàng lọc", nhưng trang tạo là wizard không có tab (a2); UI_GUIDE mục 7 ghi tab chỉ ở `/hr/jobs/:id/edit` | Tab **chỉ** ở trang sửa Job (Q3). Sửa câu chữ README ở đợt cuối (mục 10, D5) |
| M2 | README ghi câu trả lời "hiển thị cạnh điểm số và CV" nhưng cũng ghi "tab Sàng lọc" | Sàng lọc là **tab riêng**, không nhúng vào tab "CV & điểm"; vị trí theo Q6. Câu chữ README sửa cùng D5 |
| M3 | `CLAUDE.md` §4 bắt xoá mềm "xuyên suốt", tiền lệ gần nhất là xoá cứng (a10) | **Xoá mềm** theo `CLAUDE.md` §4 (Q4, R-Q8) |
| M4 | Hai thứ tự kiểm quyền cùng tồn tại (a9) | FR-H10 theo khuôn **kiểm công ty trước** (R-A2); **không** sửa `JobOwnerService`/`RubricOwnerService`; ghi nợ kỹ thuật vào ROADMAP ở đợt cuối (D4) |

## 1. Mục đích

Cho HR thu thập sớm những thông tin CV thường không có (thời gian bắt đầu đi làm được, mức lương mong muốn,
sẵn sàng làm ca…) bằng vài câu hỏi ngắn gắn với từng Job, và đọc câu trả lời ngay trên hồ sơ đơn. Câu trả
lời là thông tin tham khảo cho HR; không phải đầu vào của AI, không đổi điểm, không đổi thứ hạng.

## 2. Luồng người dùng

**HR soạn bộ câu hỏi** (đã đăng nhập, có công ty)
1. Tạo Job như hiện tại (wizard 3 bước, không đổi). Job tạo ra ở `DRAFT`.
2. Ở `/hr/jobs/:id/edit`, mở tab "Câu hỏi sàng lọc" (`?tab=screening`).
3. Bấm "Thêm câu hỏi" → hộp thoại: nội dung câu hỏi, loại (Trả lời ngắn / Một lựa chọn / Có–Không), các
   phương án (chỉ với Một lựa chọn), ô "Bắt buộc trả lời" → Lưu. Câu mới nằm cuối danh sách.
4. Sửa, xoá (có hộp xác nhận), đưa lên/xuống từng câu. Mỗi thao tác lưu ngay, không có nút "Lưu tất cả".
5. Đầu tab luôn có ba lưu ý (đúng chữ ở `UI.md` mục 4a, mục 7): (1) không hỏi thông tin nhân thân như tuổi, giới
   tính, tình trạng hôn nhân, tôn giáo, sức khoẻ, dân tộc, ảnh; (2) câu trả lời chỉ để HR tham khảo — hệ thống
   không tự loại ứng viên, không dùng câu trả lời để chấm điểm hay xếp hạng; (3) thay đổi chỉ áp dụng cho đơn nộp
   sau, đơn đã nộp giữ nguyên câu hỏi và câu trả lời lúc nộp.

**Ứng viên nộp đơn** — thuộc FR-U09, không làm ở FR-H10 (mục 3.6).

**HR đọc câu trả lời**
1. Mở `/hr/applications/:id` → tab "Sàng lọc" (`?tab=screening`).
2. Thấy đúng bộ câu hỏi và câu trả lời đã chụp lúc ứng viên nộp đơn, theo thứ tự lúc đó; hoặc một câu trạng
   thái rỗng (R-A3).

**Hệ thống**
- Không gọi AI. Không thông báo. Không đổi trạng thái đơn. Không tạo lượt chấm.

## 3. Quy tắc nghiệp vụ

### 3.1 Bộ câu hỏi theo Job (R-Q)

- **R-Q1.** Mỗi Job có 0–10 câu hỏi đang hoạt động (Q2). Câu thứ 11 → `400 SCREENING_QUESTION_LIMIT_REACHED`.
- **R-Q2.** Ba loại, enum `ScreeningQuestionType`: `SHORT_TEXT` (Trả lời ngắn), `SINGLE_CHOICE` (Một lựa chọn),
  `YES_NO` (Có/Không). Không có loại nào khác (mục 6).
- **R-Q3.** Nội dung câu hỏi: bỏ khoảng trắng hai đầu **trước**, rồi mới kiểm dài 1–300 ký tự (Q2); lưu giá trị
  đã bỏ khoảng trắng hai đầu, giữ nguyên xuống dòng bên trong.
- **R-Q4.** `SINGLE_CHOICE` có 2–10 phương án; mỗi phương án bỏ khoảng trắng hai đầu **trước**, rồi mới kiểm dài
  1–100 ký tự và không trùng nhau (không phân biệt hoa thường); lưu giá trị đã bỏ khoảng trắng. `SHORT_TEXT`,
  `YES_NO` gửi kèm phương án (danh sách khác `null` và khác rỗng) → 400. Thứ tự phương án giữ đúng thứ tự HR nhập.
- **R-Q4b.** Mọi giới hạn độ dài (câu hỏi 300, phương án 100, trả lời ngắn 500 ở FR-U09) tính **sau khi bỏ
  khoảng trắng hai đầu** và kiểm **ở service**. DTO **không** dùng `@Size`/`@NotBlank` cho các trường văn bản này
  (Bean Validation chạy trước khi trim, sẽ chặn nhầm `" " + 300 ký tự`). Vi phạm R-Q3/R-Q4 →
  `400 INVALID_SCREENING_QUESTION` (`ErrorResponse {error, message}`) với câu tiếng Việt cố định theo từng trường hợp:

  | Trường hợp | `message` |
  |---|---|
  | `questionText` là `null`, thiếu hẳn trong JSON, rỗng, hoặc chỉ gồm khoảng trắng | "Vui lòng nhập nội dung câu hỏi." |
  | Nội dung câu hỏi > 300 ký tự sau khi bỏ khoảng trắng | "Nội dung câu hỏi tối đa 300 ký tự." |
  | `SINGLE_CHOICE` có `options` là `null`, thiếu hẳn trong JSON, rỗng, ít hơn 2 hoặc nhiều hơn 10 phương án | "Câu hỏi một lựa chọn cần từ 2 đến 10 phương án." |
  | Một phương án là `null`, rỗng, hoặc chỉ gồm khoảng trắng | "Phương án không được để trống." |
  | Một phương án > 100 ký tự sau khi bỏ khoảng trắng | "Mỗi phương án tối đa 100 ký tự." |
  | Hai phương án trùng nhau (không phân biệt hoa thường) | "Các phương án không được trùng nhau." |
  | `SHORT_TEXT`/`YES_NO` gửi kèm phương án | "Chỉ câu hỏi một lựa chọn mới có phương án." |

  Kiểm theo đúng thứ tự bảng, trả lỗi đầu tiên gặp. Service kiểm `null` **trước** khi trim: không ca nào ở bảng
  được ra 500 hay `NullPointerException`. Chỉ `questionType` thiếu mới đi đường Bean Validation
  (`@NotNull`, map trường → câu của `GlobalExceptionHandler.handleValidation`).
- **R-Q5.** Mỗi câu có cờ bắt buộc, mặc định **không** bắt buộc.
- **R-Q6.** Thứ tự: câu mới thêm vào cuối. Đổi thứ tự bằng một lệnh gửi **toàn bộ** danh sách id theo thứ tự
  mới; danh sách phải đúng bằng tập câu đang hoạt động của Job (thiếu, thừa, trùng, id của Job khác) →
  `400 INVALID_SCREENING_ORDER`.
- **R-Q7.** Sửa được nội dung, loại, phương án, cờ bắt buộc của câu đang hoạt động. Đổi loại thì phương án
  phải hợp lệ theo loại mới (R-Q4).
- **R-Q8.** Xoá là **xoá mềm** (`deleted_at`, Q4). Câu đã xoá không hiện, không tính vào giới hạn R-Q1, không
  khôi phục được qua giao diện.
- **R-Q9.** Sửa được ở mọi trạng thái Job (`DRAFT`, `OPEN`, `PAUSED`, `CLOSED`), kể cả khi Job đã có đơn (Q1).
  Không khoá theo `rubrics.is_locked`.
- **R-Q10.** Mọi thao tác ghi (thêm, sửa, xoá, đổi thứ tự) chạy trong **một** transaction ngắn theo đúng thứ tự:
  (1) kiểm quyền (R-A2); (2) kiểm đầu vào (R-Q3, R-Q4, R-Q4b); (3) khoá hàng `jobs` của Job đó **bằng chính câu**
  `UPDATE jobs SET screening_revision = screening_revision + 1 WHERE id = :jobId` (khoá hàng giữ tới commit — đây
  cũng là bước tăng revision của R-Q11); (4) sau đó mới đếm, tìm, kiểm tập id và ghi câu hỏi. Transaction thứ hai
  cùng Job chặn ở bước (3) cho tới khi transaction thứ nhất commit/rollback, nên hai tab cùng thêm câu không vượt
  10 câu và không trùng `display_order`. Không dùng `@Lock`/`PESSIMISTIC_WRITE` hay `SELECT … FOR UPDATE` riêng.
  Transaction không chứa lời gọi ngoài nào. Ràng buộc kèm theo:
  - **R-Q10a.** Sau câu `UPDATE` ở bước (3), không dùng lại entity `Job` đã nạp trước đó trong cùng persistence
    context: câu `UPDATE` khai `@Modifying(clearAutomatically = true)` (xoá persistence context), hoặc method
    không nạp entity `Job` mà chỉ cần `companyId` qua truy vấn riêng. Cần gì của Job sau bước (3) thì đọc lại.
  - **R-Q10b.** Câu `UPDATE` này làm trigger `trg_jobs_updated_at` đổi `jobs.updated_at`; đã soát không chỗ nào
    sắp xếp theo hay hiển thị cột này (mục 0, a14).
- **R-Q11.** **Cả bốn** thao tác ghi — thêm (S2), sửa (S3), xoá (S4), đổi thứ tự (S5) — tăng
  `jobs.screening_revision` đúng 1, trong **cùng** transaction với thay đổi câu hỏi (Q7). Thao tác thất bại
  (400/403/404) rollback, không tăng. GET (S1) không tăng, chỉ trả giá trị hiện tại. FR-H10 không tự dùng giá trị
  này để chặn gì; FR-U09 dùng nó để trả 409 khi bộ câu hỏi đổi giữa lúc ứng viên đang điền form.

### 3.2 Bản chụp theo đơn (R-S)

- **R-S1.** Khi ứng viên nộp đơn (FR-U09), mỗi câu đang hoạt động của Job **tại thời điểm đó** được chụp thành
  một dòng `application_screening_answers`, chép **toàn bộ** nội dung: thứ tự, loại, nội dung câu hỏi, phương
  án, cờ bắt buộc, cùng câu trả lời. Câu không bắt buộc mà ứng viên bỏ trống vẫn có dòng, phần trả lời `NULL`.
- **R-S2.** Bản chụp **bất biến**: không cột `updated_at`, không trigger `set_updated_at`, không endpoint sửa
  hay xoá. Sửa, xoá, đổi thứ tự, đổi loại câu hỏi gốc sau đó không đổi bất kỳ byte nào của bản chụp.
- **R-S3.** Bản chụp không phụ thuộc câu hỏi gốc để hiển thị. `question_id` chỉ để truy vết, có thể trỏ tới câu
  đã xoá mềm.
- **R-S4.** `job_applications.screening_captured_at` đánh dấu đơn **đã đi qua** bước chụp của FR-U09 (đặt bằng
  thời điểm nộp, kể cả khi Job lúc đó có 0 câu). `NULL` = đơn nộp trước khi có bước chụp (mọi đơn cũ, kể cả seed
  demo, và đơn nộp giữa lúc FR-H10 xong và FR-U09 xong).
- **R-S5.** Câu trả lời lưu nguyên văn như ứng viên gửi, chỉ bỏ khoảng trắng hai đầu: `SHORT_TEXT` ≤ 500 ký tự tính
  sau khi bỏ khoảng trắng (Q2, R-Q4b — FR-U09 kiểm ở service); `SINGLE_CHOICE` lưu **chữ** của phương án đã chọn
  (không lưu chỉ số, vì phương án gốc có thể đổi); `YES_NO` lưu boolean.
- **R-S6.** Phạm vi ràng buộc chốt ở DB (mục 4.1) cho bản chụp: loại câu hỏi hợp lệ; hình dạng `options_snapshot`
  theo loại (`chk_screening_answer_options`, cùng khuôn `chk_screening_question_options`); hình dạng câu trả lời
  khớp loại (`chk_screening_answer_shape`); câu bắt buộc có trả lời khác `NULL` (`chk_screening_answer_required`);
  `answer_text` ≤ 500 ký tự trên giá trị đã lưu; thứ tự không trùng trong một đơn (`uq_screening_answer_order`).
  **Không** chốt ở DB, FR-U09 kiểm ở service: câu trả lời `SINGLE_CHOICE` thuộc `options_snapshot`; `SHORT_TEXT`
  bắt buộc khác rỗng sau khi bỏ khoảng trắng; độ dài tính sau khi bỏ khoảng trắng.

### 3.3 Không ảnh hưởng chấm điểm, xếp hạng, quyết định (R-N)

- **R-N1.** Câu hỏi và câu trả lời sàng lọc **không** đi vào bất kỳ ngữ cảnh AI nào: chấm điểm (FR-H04),
  giải thích (FR-H06), soạn nháp (FR-C07, K1 `ConversationContext`). Package `ai/`, `scoring/`, `aicontext/`
  và thư mục `ai/prompt/` không tham chiếu tới package `screening/`. FR sau muốn đưa câu trả lời vào AI phải ghi
  rõ trong đặc tả đã duyệt của chính FR đó.
- **R-N2.** Không ảnh hưởng tổng điểm, hạng (FR-H05), không thêm tuỳ chọn sắp xếp, không thêm bộ lọc theo câu
  trả lời ở bất kỳ danh sách nào (`/hr/jobs/:id/edit?tab=applications`, `/hr/candidates`, dashboard).
- **R-N3.** Không có cơ chế tự loại: không "câu trả lời mong muốn", không "câu loại trực tiếp", không đổi trạng
  thái đơn theo câu trả lời. Bộ câu hỏi không có trường nào để HR khai đáp án đúng.
- **R-N4.** Giao diện không tô màu, không icon ✓/✗, không nhãn theo câu trả lời; "Có" và "Không" hiển thị như
  nhau (UI.md mục 10).

### 3.4 Thông tin nhân thân (R-P)

- **R-P1.** Tab soạn câu hỏi và hộp thoại thêm/sửa luôn hiện lưu ý không hỏi thông tin nhân thân (tuổi, giới
  tính, tình trạng hôn nhân, tôn giáo, sức khoẻ, dân tộc, ảnh…). Không ẩn được.
- **R-P2.** Backend không lọc từ khoá trong nội dung câu hỏi (Q8). Trách nhiệm nội dung thuộc HR; `srs-guard`
  nguyên tắc 13 vẫn áp cho code (không field nhân thân nào bị hỏi tự động ở form sàng lọc).
- **R-P3.** Không có câu hỏi mẫu nào do hệ thống tạo sẵn. Gợi ý câu hỏi là phần của FR-H11.

### 3.5 Quyền truy cập (R-A)

- **R-A1.** Mọi endpoint FR-H10 nằm dưới `/api/hr/**` (filter chain chỉ cho role HR). Ứng viên gọi → 403; không
  token → 401 `UNAUTHENTICATED`.
- **R-A2.** S1–S5 (mục 4.3) kiểm theo khuôn đã làm cứng (mục 0, a9), dùng **một** method nạp Job có kiểm quyền
  trong `screening/`: công ty của HR trước (`404 COMPANY_NOT_FOUND`), rồi Job (`404 JOB_NOT_FOUND`), rồi Job
  thuộc công ty khác → `403 FORBIDDEN` (`AccessDeniedException`). Câu hỏi không thuộc Job trên đường dẫn, không
  tồn tại hoặc đã xoá mềm → `404 SCREENING_QUESTION_NOT_FOUND`. Không chép method này vào từng handler.
- **R-A3.** S6 dùng nguyên `HrApplicationAccess.loadOwned` (FR-H09 R-Q2, R-Q3): đơn không tồn tại → 404
  `APPLICATION_NOT_FOUND`; đơn của công ty khác → 403. Trạng thái trả về do backend tính:

  | `state` | Điều kiện | Ý nghĩa |
  |---|---|---|
  | `NOT_CAPTURED` | `screening_captured_at IS NULL` | Đơn nộp trước khi có bước chụp |
  | `NO_QUESTIONS` | đã chụp, 0 dòng bản chụp | Job không có câu hỏi lúc ứng viên nộp |
  | `CAPTURED` | đã chụp, ≥ 1 dòng | Có bản chụp |

- **R-A4.** Không trạng thái Job hay trạng thái đơn nào làm S6 bị khoá (đơn `WITHDRAWN`, Job đã xoá mềm vẫn xem
  được — như các tab khác của FR-H09).
- **R-A5.** FR-H10 không thêm endpoint nào dưới `/api/candidates/**` hay `/api/jobs/**` (công khai). Phần ứng
  viên đọc câu hỏi để trả lời và xem lại câu trả lời **của chính mình** là của FR-U09, phải theo khuôn
  `findByIdAndCandidateId` → 404 như `ApplicationService.withdraw` (`:105-107`).

### 3.6 Ranh giới với FR-U09 và FR-H11 (R-B)

| Phần | FR-H10 | FR-U09 | FR-H11 |
|---|---|---|---|
| Bảng `job_screening_questions`, CRUD phía HR, tab soạn câu hỏi | ✔ | | |
| Bảng `application_screening_answers`, cột `screening_captured_at`, `screening_revision` (migration V13) | ✔ (Q5) | | |
| Đọc bản chụp phía HR (S6) + tab Sàng lọc ở `/hr/applications/:id` | ✔ | | |
| Endpoint ứng viên lấy câu hỏi của Job `OPEN` để trả lời | | ✔ | |
| Form trả lời ở `/jobs/:id/apply`, kiểm câu bắt buộc **ở backend**, kiểm hình dạng/độ dài | | ✔ | |
| Ghi bản chụp + đặt `screening_captured_at` **trong cùng transaction** của `ApplicationService.apply` | | ✔ | |
| Phụ thuộc gói `jobapplication/` → `screening/` (để `ApplicationService.apply` ghi bản chụp) | không có (mục 4.2) | ✔ được phép | |
| Đọc `job_applications.screening_captured_at` | ✔ bằng native query trong `screening/`, **không** sửa entity `JobApplication` | | |
| Thêm field `screeningCapturedAt` vào entity `JobApplication` (để ghi) | | ✔ | |
| Dùng `screening_revision` để trả 409 khi bộ câu hỏi đổi giữa lúc ứng viên đang điền (Q7) | | ✔ | |
| Ứng viên xem lại câu trả lời của mình ở `/candidate/applications/:id` | | ✔ | |
| Gợi ý câu hỏi sàng lọc bằng AI | | | ✔ |

- **R-B1.** FR-H10 **không** sửa `ApplicationService`, `ApplicationCreateRequest`, `JobApplyForm.tsx`,
  `JobOwnerService.create`, `JobCreateRequest`, `HrJobCreatePage.tsx`.
- **R-B2.** Trong FR-H10, dòng bản chụp chỉ được tạo bởi test (repository/SQL) và seed (Q9) để kiểm đường đọc.

## 4. Dữ liệu & quyền truy cập

### 4.1 Migration `V13__screening_questions.sql` (Q5)

Khối dưới đây là **nguyên văn** file V13 (đã sửa theo L7, mục 12).

```sql
-- FR-H10 - Cau hoi sang loc theo Job (REQUIREMENT.md muc 4.1).
--
-- Quy uoc CHECK (L7): Postgres coi CHECK ra NULL la DAT. Moi CHECK duoi day da xet tung cot tham gia la NULL: cot
-- KHONG duoc NULL thi khai NOT NULL o dinh nghia cot hoac IS NOT NULL trong CHECK; cot DUOC NULL co chu dich thi CHECK
-- xu ly NULL tuong minh (IS NULL / IS NOT NULL) va ghi ly do ngay canh cot.

-- Bo cau hoi sang loc theo Job. Xoa mem bang deleted_at (CLAUDE.md §4, R-Q8). Gioi han do dai (300 ky tu) tinh SAU khi
-- bo khoang trang hai dau va kiem o service (R-Q4b); gia tri luu da bo khoang trang nen CHECK duoi day la chot chan cuoi
-- tren gia tri da luu.
CREATE TABLE job_screening_questions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id         UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    question_type  VARCHAR(20) NOT NULL CHECK (question_type IN ('SHORT_TEXT','SINGLE_CHOICE','YES_NO')),
    question_text  VARCHAR(300) NOT NULL CHECK (char_length(btrim(question_text)) > 0),
    -- NULL co chu dich: SHORT_TEXT/YES_NO khong co phuong an. SINGLE_CHOICE bat buoc khac NULL (IS NOT NULL trong
    -- chk_screening_question_options - thieu no thi NULL lot qua vi jsonb_typeof(NULL) = 'array' ra NULL).
    options        JSONB,
    is_required    BOOLEAN NOT NULL DEFAULT FALSE,
    display_order  INT NOT NULL,
    -- NULL co chu dich: cau dang hoat dong (R-Q8). Khong tham gia CHECK nao.
    deleted_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_screening_question_options CHECK (
        (question_type = 'SINGLE_CHOICE' AND options IS NOT NULL AND jsonb_typeof(options) = 'array'
             AND jsonb_array_length(options) BETWEEN 2 AND 10)
        OR (question_type <> 'SINGLE_CHOICE' AND options IS NULL))
);
CREATE INDEX idx_screening_question_job ON job_screening_questions(job_id, display_order)
    WHERE deleted_at IS NULL;

-- Ham set_updated_at() o V1__init_schema.sql:362-367; khoi dang ky trigger cho cac bang cu o :369-378 khong sua.
CREATE TRIGGER trg_job_screening_questions_updated_at BEFORE UPDATE ON job_screening_questions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Ban chup cau hoi + cau tra loi theo don (R-S). BAT BIEN: khong updated_at, khong trigger (R-S2).
-- Duong ghi thuoc FR-U09; FR-H10 chi doc.
-- Khong them index rieng: uq_screening_answer_order (application_id, display_order) da du cho S6 (L5).
CREATE TABLE application_screening_answers (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id          UUID NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    -- NULL co chu dich: chi de truy vet (R-S3); khong tham gia CHECK nao.
    question_id             UUID REFERENCES job_screening_questions(id) ON DELETE SET NULL,
    display_order           INT NOT NULL,
    question_type_snapshot  VARCHAR(20) NOT NULL
                            CHECK (question_type_snapshot IN ('SHORT_TEXT','SINGLE_CHOICE','YES_NO')),
    question_text_snapshot  VARCHAR(300) NOT NULL,
    -- NULL co chu dich: SHORT_TEXT/YES_NO. SINGLE_CHOICE bat buoc khac NULL (IS NOT NULL trong
    -- chk_screening_answer_options).
    options_snapshot        JSONB,
    required_snapshot       BOOLEAN NOT NULL,
    -- NULL co chu dich: cau khong bat buoc ung vien bo trong (R-S1), va moi cau YES_NO (chk_screening_answer_shape).
    -- CHECK do dai xu ly NULL tuong minh (answer_text IS NULL OR ...).
    answer_text             TEXT CHECK (answer_text IS NULL OR char_length(answer_text) <= 500),
    -- NULL co chu dich: cau YES_NO khong bat buoc bo trong, va moi cau khong phai YES_NO (chk_screening_answer_shape).
    answer_yes_no           BOOLEAN,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_screening_answer_order UNIQUE (application_id, display_order),
    CONSTRAINT chk_screening_answer_options CHECK (
        (question_type_snapshot = 'SINGLE_CHOICE' AND options_snapshot IS NOT NULL
             AND jsonb_typeof(options_snapshot) = 'array'
             AND jsonb_array_length(options_snapshot) BETWEEN 2 AND 10)
        OR (question_type_snapshot <> 'SINGLE_CHOICE' AND options_snapshot IS NULL)),
    -- Chi dung IS NULL tren cot duoc NULL + cot NOT NULL -> bieu thuc khong bao gio ra NULL.
    CONSTRAINT chk_screening_answer_shape CHECK (
        (question_type_snapshot = 'YES_NO' AND answer_text IS NULL)
        OR (question_type_snapshot <> 'YES_NO' AND answer_yes_no IS NULL)),
    -- required_snapshot NOT NULL, hai cot tra loi chi xet IS NOT NULL -> khong bao gio ra NULL.
    CONSTRAINT chk_screening_answer_required CHECK (
        NOT required_snapshot OR answer_text IS NOT NULL OR answer_yes_no IS NOT NULL)
);

-- NULL co chu dich = don chua di qua buoc chup cua FR-U09 (moi don cu, R-S4). Khong backfill, khong CHECK.
ALTER TABLE job_applications ADD COLUMN screening_captured_at TIMESTAMPTZ;

-- Tang 1 o moi thao tac ghi bo cau hoi, trong cung transaction (R-Q10, R-Q11, Q7). Khong map vao entity Job.
ALTER TABLE jobs ADD COLUMN screening_revision INT NOT NULL DEFAULT 0;
```

Không thêm index nào cho `application_screening_answers`: `uq_screening_answer_order (application_id,
display_order)` đã tạo index trên đúng hai cột S6 lọc và sắp xếp (L5). Trigger `updated_at` của
`job_screening_questions` là câu `CREATE TRIGGER` riêng trong V13, không sửa V1.

**Soát CHECK theo logic ba trị (L7).** Postgres coi CHECK ra `NULL` là đạt. Kết quả soát từng ràng buộc khi mỗi cột
tham gia là `NULL`:

| Ràng buộc | Cột có thể `NULL` | Kết luận |
|---|---|---|
| `job_screening_questions.question_type` `CHECK (… IN …)` | không (`NOT NULL`) | Đúng |
| `job_screening_questions.question_text` `CHECK (char_length(btrim(…)) > 0)` | không (`NOT NULL`) | Đúng |
| `chk_screening_question_options` | `options` (có chủ đích với `SHORT_TEXT`/`YES_NO`) | **Lỗ hổng, đã sửa:** `SINGLE_CHOICE` + `options NULL` → `jsonb_typeof(NULL) = 'array'` ra `NULL`, cả CHECK ra `NULL` → lọt. Thêm `options IS NOT NULL` vào vế `SINGLE_CHOICE` |
| `application_screening_answers.question_type_snapshot` `CHECK (… IN …)` | không (`NOT NULL`) | Đúng |
| `application_screening_answers.answer_text` `CHECK (answer_text IS NULL OR char_length(…) <= 500)` | `answer_text` (có chủ đích: câu bỏ trống, câu `YES_NO`) | Đúng — `NULL` xử lý tường minh bằng `IS NULL` |
| `chk_screening_answer_options` | `options_snapshot` (có chủ đích với `SHORT_TEXT`/`YES_NO`) | **Lỗ hổng, đã sửa:** cùng lỗi với `chk_screening_question_options`. Thêm `options_snapshot IS NOT NULL` |
| `chk_screening_answer_shape` | `answer_text`, `answer_yes_no` (có chủ đích) | Đúng — hai cột chỉ xét bằng `IS NULL`, `question_type_snapshot` `NOT NULL` → không bao giờ ra `NULL` |
| `chk_screening_answer_required` | `answer_text`, `answer_yes_no` (có chủ đích) | Đúng — `required_snapshot` `NOT NULL`, hai cột chỉ xét bằng `IS NOT NULL` → không bao giờ ra `NULL` |
| `uq_screening_answer_order` (UNIQUE) | không (`application_id`, `display_order` `NOT NULL`) | Đúng — UNIQUE coi `NULL` là khác nhau, nhưng hai cột không `NULL` được |

Cột được `NULL` có chủ đích nhưng không tham gia CHECK nào: `deleted_at`, `question_id`,
`job_applications.screening_captured_at` — lý do ghi trong comment của V13.

Không backfill dữ liệu. Phần DB **không** chốt (R-S6): câu trả lời `SINGLE_CHOICE` thuộc `options_snapshot`;
`SHORT_TEXT` bắt buộc khác rỗng sau khi bỏ khoảng trắng; độ dài tính sau khi bỏ khoảng trắng — FR-U09 kiểm ở
service.

### 4.2 Package

Package mới `screening/` (CLAUDE.md §3: package mới khi FR thật sự cần): entity `ScreeningQuestion`,
`ApplicationScreeningAnswer` (FR-H10 chỉ đọc), enum `ScreeningQuestionType`, repository, service, controller,
`dto/`. Mã lỗi mới ở `common/exception/` như các exception hiện có.

Phụ thuộc gói: `screening/` được phép import `jobapplication/HrApplicationAccess`, `job/`, `company/`. Quy tắc
"`screening/` → `jobapplication/` một chiều" **chỉ áp trong phạm vi FR-H10**: code của FR-H10 không thêm import
`screening/` vào `jobapplication/`, `job/`. FR-U09 **được phép** `jobapplication/` → `screening/` để
`ApplicationService.apply` ghi bản chụp trong cùng transaction nộp đơn (bảng R-B). Phụ thuộc hai chiều giữa gói
đã có tiền lệ (`jobapplication/ApplicationOwnerService` import `scoring.*`; `messaging/MessageService` import
`jobapplication.*`). Không có kiểm tĩnh nào chặn chiều này.

Không thêm field nào vào `Job` entity cho `screening_revision`: đọc/tăng bằng native query trong
`ScreeningQuestionRepository` (không sửa `JobRepository`); câu tăng khai `@Modifying(clearAutomatically = true)`
(R-Q10, R-Q10a). Không thêm field nào vào `JobApplication` entity cho `screening_captured_at`: S6 đọc bằng native
query trong `ApplicationScreeningAnswerRepository` (L3); FR-U09 thêm field vào entity khi làm đường ghi (bảng R-B).
`ddl-auto: validate` không đòi entity phải ánh xạ mọi cột.

### 4.3 Endpoint

| # | Endpoint | Mô tả | Thành công | Lỗi riêng (ngoài 401/403/404 của R-A2, R-A3) |
|---|---|---|---|---|
| S1 | `GET /api/hr/jobs/{jobId}/screening-questions` | Câu đang hoạt động, theo `display_order` | 200 `ScreeningQuestionListResponse` | — |
| S2 | `POST /api/hr/jobs/{jobId}/screening-questions` | Thêm vào cuối | 201 `ScreeningQuestionResponse` | 400 `INVALID_SCREENING_QUESTION` (R-Q4b); 400 map trường → câu khi thiếu `questionType`; 400 `SCREENING_QUESTION_LIMIT_REACHED` |
| S3 | `PUT /api/hr/jobs/{jobId}/screening-questions/{questionId}` | Sửa | 200 `ScreeningQuestionResponse` | 400 `INVALID_SCREENING_QUESTION`; 400 map trường → câu khi thiếu `questionType`; 404 `SCREENING_QUESTION_NOT_FOUND` |
| S4 | `DELETE /api/hr/jobs/{jobId}/screening-questions/{questionId}` | Xoá mềm | 204 | 404 `SCREENING_QUESTION_NOT_FOUND` |
| S5 | `PUT /api/hr/jobs/{jobId}/screening-questions/order` | Đổi thứ tự | 200 `ScreeningQuestionListResponse` | 400 `INVALID_SCREENING_ORDER` |
| S6 | `GET /api/hr/applications/{applicationId}/screening-answers` | Bản chụp của đơn | 200 `ApplicationScreeningResponse` | — |

S1–S5 không tạo lượt chấm, không gửi thông báo, không xoá `job_embeddings`. Tăng `screening_revision` làm trigger
`trg_jobs_updated_at` (`V1__init_schema.sql:369-378`) đổi `jobs.updated_at` — chấp nhận: cột này chỉ được trả ra ở
`JobOwnerResponse` (`JobOwnerService.java:308`), không dùng để sắp xếp hay làm mới embedding.

### 4.4 DTO

```java
record ScreeningQuestionRequest(
        @NotNull ScreeningQuestionType questionType,
        String questionText,       // KHONG @Size/@NotBlank - trim roi kiem o service (R-Q4b)
        List<String> options,      // null/rong voi SHORT_TEXT, YES_NO; 2-10 voi SINGLE_CHOICE (R-Q4)
        Boolean required) {}       // null -> false (R-Q5)

record ScreeningQuestionOrderRequest(@NotNull List<UUID> questionIds) {}

record ScreeningQuestionResponse(
        UUID id, ScreeningQuestionType questionType, String questionText,
        List<String> options, boolean required, int displayOrder) {}

record ScreeningQuestionListResponse(
        UUID jobId, int revision, int maxQuestions,   // maxQuestions = 10, FE khong tu hardcode
        List<ScreeningQuestionResponse> questions) {}

record ApplicationScreeningResponse(
        UUID applicationId,
        ScreeningCaptureState state,                  // NOT_CAPTURED | NO_QUESTIONS | CAPTURED (R-A3)
        Instant capturedAt,                           // null khi NOT_CAPTURED
        List<Item> items) {                           // rong tru khi CAPTURED
    record Item(int displayOrder, ScreeningQuestionType questionType, String questionText,
                List<String> options, boolean required,
                String answerText,                    // SHORT_TEXT / chu phuong an da chon; null = khong tra loi
                Boolean answerYesNo) {}               // YES_NO; null = khong tra loi
}
```

`List` chuẩn hoá `null` → `List.of()` trong compact constructor (CLAUDE.md §4). Không DTO, entity, cột nào tên
`verdict`, `label`, `isQualified`, `passed`, `recommendation` (CLAUDE.md §7) — phương án gọi là `options`, không
phải `labels`. `Item` không có `questionId` (không cần cho HR, tránh gợi ý "đối chiếu với câu hiện tại").

### 4.5 Seed kiểm thử tay (Q9) — `db/seed/seed-test.sql`

Thêm ở đợt 3. UUID dùng hai tiền tố mới `ee` (câu hỏi) và `ef` (bản chụp), nối tiếp `e0..ed` đang dùng — sửa
comment đầu file "tiền tố riêng e0..ed" thành "e0..ef", và câu "UUID tiền tố `e0…`–`ed…`" ở `db/seed/README.md:255`
thành "`e0…`–`ef…`" (L4). Giờ hiển thị là giờ Việt Nam (UTC+7). Seed demo không đổi
(mọi đơn demo là `NOT_CAPTURED`).

**Câu hỏi của tin CSKH `e2000000-0000-0000-0000-000000000001`** (`created_at` = `2026-09-01 02:00:00+00`, lúc
đăng tin; `jobs.screening_revision` của tin này đặt `3`):

| Mã | id | `display_order` | Loại | Bắt buộc | Nội dung | Phương án |
|---|---|---|---|---|---|---|
| SQ1 | `ee000000-0000-0000-0000-000000000001` | 1 | `SHORT_TEXT` | có | "Bạn có thể bắt đầu đi làm từ khi nào?" | — |
| SQ2 | `ee000000-0000-0000-0000-000000000002` | 2 | `YES_NO` | không | "Bạn có sẵn sàng làm ca tối (18:00–22:00) không?" | — |
| SQ3 | `ee000000-0000-0000-0000-000000000003` | 3 | `SINGLE_CHOICE` | không | "Mức lương mong muốn của bạn?" | "Dưới 10 triệu" · "10–14 triệu" · "Trên 14 triệu" |

Tin Hành chính `e2000000-0000-0000-0000-000000000002` **không** có câu hỏi.

**Bản chụp theo đơn** (cột `*_snapshot` chép đúng SQ1–SQ3 ở trên, `question_id` trỏ câu tương ứng;
`screening_captured_at` = `applied_at` của đơn):

| Đơn | id đơn | `screening_captured_at` (hiển thị) | `state` | id dòng bản chụp |
|---|---|---|---|---|
| A1 — Quốc Huy, tin CSKH | `e8000000-0000-0000-0000-000000000001` | `2026-10-05 17:35:00+00` (06/10/2026 00:35) | `CAPTURED` | `ef000000-0000-0000-0000-000000000001` … `-03` |
| A9 — Trần Bảo Ngọc, tin CSKH | `e8000000-0000-0000-0000-000000000009` | `2026-09-07 03:00:00+00` (07/09/2026 10:00) | `CAPTURED` | `ef000000-0000-0000-0000-000000000004` … `-06` |
| A2 — Quốc Huy, tin Hành chính | `e8000000-0000-0000-0000-000000000002` | `2026-10-05 17:36:00+00` (06/10/2026 00:36) | `NO_QUESTIONS` (0 dòng) | — |
| A6 — Lê Thị Lan, tin CSKH | `e8000000-0000-0000-0000-000000000006` | `NULL` | `NOT_CAPTURED` | — |

Các đơn test còn lại giữ `screening_captured_at = NULL`.

| Câu | Trả lời của A1 | Trả lời của A9 |
|---|---|---|
| SQ1 | `answer_text` = "Đầu tháng 11/2026, sau khi bàn giao công việc hiện tại." | `answer_text` = "Có thể đi làm ngay." |
| SQ2 | `answer_yes_no` = `FALSE` → "Không" | `answer_yes_no` = `TRUE` → "Có" |
| SQ3 | `answer_text` = "10–14 triệu" | `NULL` → "Ứng viên không trả lời câu này." |

`reset-test-data.sql`: xoá `application_screening_answers` theo đơn test trước khi xoá `job_applications`, xoá
`job_screening_questions` theo Job test trước khi xoá `jobs` (ghi tường minh như các bảng con khác, không dựa vào
`ON DELETE CASCADE`).

## 5. AI

Không dùng AI. Không gọi `ChatModel`/`EmbeddingModel`, không thuộc danh sách ngoại lệ K3, không dùng K1–K4. Gợi ý
câu hỏi bằng AI là của FR-H11. Câu trả lời không vào ngữ cảnh AI nào (R-N1) — mục 7.1 có test chứng minh.

## 6. Ngoài phạm vi

- Bài test có đáp án, chấm tự động; điểm hay trọng số cho câu trả lời.
- Câu hỏi tải tệp đính kèm; câu hỏi nhiều lựa chọn (chọn nhiều); thang điểm; ngày tháng; số.
- Tự loại, lọc, sắp xếp, tô màu, đếm "đạt/không đạt" theo câu trả lời (R-N2–R-N4).
- Phần của FR-U09 (mục 3.6), gồm mọi endpoint phía ứng viên và form nộp đơn.
- Gợi ý câu hỏi bằng AI (FR-H11); hiện câu trả lời ở màn hình so sánh (FR-H14); che câu trả lời ở chế độ ẩn
  danh (FR-H16 — câu trả lời tự do có thể chứa thông tin nhân thân, đã ghi ở `docs/ROADMAP.md` Phase 2.6).
- Bộ câu hỏi mẫu dùng lại giữa các Job; sao chép câu hỏi từ Job khác.
- Khôi phục câu đã xoá; lịch sử sửa câu hỏi (bản chụp ở đơn đã đủ cho kiểm chứng).
- Nhập câu hỏi ngay trong wizard tạo Job (Q3).
- Sửa thứ tự kiểm quyền của `JobOwnerService`, `RubricOwnerService` (mục 0, a9, M4) — ghi nợ kỹ thuật (D4).

## 7. Xong khi

### 7.1 Test backend (Testcontainers, full `./mvnw test` xanh)

| # | Test | Loại |
|---|---|---|
| T1 | S2 thêm đủ 3 loại; S1 trả đúng thứ tự thêm, `maxQuestions = 10` | dương |
| T2 | Nội dung câu hỏi (R-Q3, R-Q4b): `"questionText": null`, thiếu hẳn khoá `questionText` trong JSON, `""`, chỉ khoảng trắng → mỗi ca `400 INVALID_SCREENING_QUESTION` "Vui lòng nhập nội dung câu hỏi." (không 500, không `NullPointerException`); 299, 300 ký tự → 201; `" " + 300 ký tự + " "` → 201 và S1 trả đúng 300 ký tự (đã bỏ khoảng trắng); 301 ký tự → 400 "Nội dung câu hỏi tối đa 300 ký tự."; `" " + 301 ký tự` → 400 | biên |
| T3 | Phương án `SINGLE_CHOICE` (R-Q4, R-Q4b), mọi lỗi là `400 INVALID_SCREENING_QUESTION` với đúng câu ở bảng R-Q4b: 1 phương án → 400; 2 → 201; 10 → 201; 11 → 400; phương án 99, 100 ký tự → 201; `" " + 100 ký tự + " "` → 201 và S1 trả đúng 100 ký tự; 101 ký tự → 400; một phương án là `null`, `""` hoặc chỉ khoảng trắng → 400 "Phương án không được để trống."; `SINGLE_CHOICE` với `"options": null`, thiếu hẳn khoá `options`, hoặc `[]` → 400 "Câu hỏi một lựa chọn cần từ 2 đến 10 phương án." (mọi ca không 500, không `NullPointerException`); hai phương án trùng sau trim/không phân biệt hoa thường → 400; `SHORT_TEXT`/`YES_NO` kèm phương án → 400 | biên + âm |
| T4 | Giới hạn câu: có 9 câu → thêm được câu 10; có 10 → câu 11 trả `400 SCREENING_QUESTION_LIMIT_REACHED`; xoá 1 câu → thêm lại được | biên |
| T5 | Hai request S2 song song khi Job có 9 câu → đúng một thành công, Job có đúng 10 câu, `display_order` không trùng (R-Q10). Đặt ở lớp test **không** có `@Transactional` toàn lớp; dữ liệu chuẩn bị và mỗi request tự commit riêng (CLAUDE.md §3c — transaction bọc toàn lớp làm hai luồng không thấy dữ liệu của nhau). Dùng dữ liệu ngẫu nhiên riêng của test (email, công ty, Job mới), chỉ khẳng định trên Job của chính test, không dọn sau test — theo tiền lệ các test không `@Transactional` hiện có (`ScoringRunOrchestratorTest`, `ScoreExplanationOrchestratorTest`) | âm |
| T6 | S5: đúng hoán vị → 200 theo thứ tự mới; thiếu id, thừa id, trùng id, id của Job khác, id đã xoá → `400 INVALID_SCREENING_ORDER` | dương + âm |
| T7 | S4 xoá mềm: hàng còn trong DB với `deleted_at` khác `NULL`; S1 không trả câu đó; S3/S4 lại với id đó → 404 | dương + âm |
| T8 | Quyền S1–S5: HR công ty khác → 403; Job không tồn tại → 404 `JOB_NOT_FOUND`; HR chưa có công ty → 404 `COMPANY_NOT_FOUND` **kể cả khi jobId không tồn tại** (thứ tự R-A2); câu hỏi của Job khác trên đường dẫn → 404 `SCREENING_QUESTION_NOT_FOUND`; token ứng viên → 403; không token → 401 | âm |
| T9 | Sửa được khi Job `OPEN` có đơn và khi rubric `is_locked = true` (R-Q9) | dương |
| T10 | **Bản chụp bất biến:** tạo đơn + dòng bản chụp bằng repository/SQL (giả lập FR-U09; dòng bản chụp ghi bằng SQL vì entity `ApplicationScreeningAnswer` là `@Immutable`, không có setter — L8), sau đó S3 đổi nội dung + loại + phương án, S5 đổi thứ tự, S4 xoá câu → S6 của đơn đó trả **y hệt** trước khi sửa (so cả JSON) | dương |
| T11 | S6 ba trạng thái: `screening_captured_at = NULL` → `NOT_CAPTURED`, `items` rỗng; đã chụp, 0 dòng → `NO_QUESTIONS`; có dòng → `CAPTURED`, đúng `display_order`; câu không bắt buộc bỏ trống → `answerText`/`answerYesNo` `null` | dương |
| T12 | Quyền S6: như FR-H09 T2–T4 (403 công ty khác, 404 `APPLICATION_NOT_FOUND`, 404 `COMPANY_NOT_FOUND`, 403 ứng viên, 401) | âm |
| T13 | **Không vào ngữ cảnh AI chấm điểm và giải thích:** đơn có bản chụp chứa chuỗi đánh dấu duy nhất (vd `SCREENING-MARKER-7f3a`) ở nội dung câu hỏi, phương án và câu trả lời; Job có câu hỏi đang hoạt động chứa cùng chuỗi. `ChatModel` mock, bắt **mọi** `Prompt` bằng `ArgumentCaptor`, hai nhánh: (a) **FR-H04** — `ScoringRunOrchestrator.processOne` đi qua `CriterionScoringService.score`; (b) **FR-H06** — `ScoreExplanationOrchestrator.processOne` cho lượt `DONE` của chính đơn đó. Mỗi nhánh khẳng định: bắt được ≥ 1 prompt (nhánh thật sự gọi `ChatModel`, không "qua" bằng 0 prompt) **và** không message nào (system lẫn user) của prompt nào chứa chuỗi đánh dấu | âm |
| T14 | **Không đổi tổng điểm/hạng:** hai đơn cùng Job có `criterion_scores` giống hệt, một đơn có bản chụp (trả lời "Có"), một đơn `NOT_CAPTURED` → `totalScore` bằng nhau và cùng `rank` ở `GET /api/hr/jobs/{jobId}/applications` và ở E3 của FR-H09; thêm/xoá dòng bản chụp rồi gọi lại → không đổi | dương |
| T15 | Ràng buộc DB (insert thẳng, không qua service): `answer_text` 499, 500 ký tự → insert được; 501 ký tự → `DataIntegrityViolationException`. Mỗi trường hợp sau cũng → `DataIntegrityViolationException`: `YES_NO` có `answer_text`; câu bắt buộc không có trả lời; `job_screening_questions` `SINGLE_CHOICE` với `options` 1 phần tử; bản chụp `SINGLE_CHOICE` có `options_snapshot` `NULL` hoặc 1 phần tử; bản chụp `SHORT_TEXT`/`YES_NO` có `options_snapshot` khác `NULL`; `job_screening_questions` `SINGLE_CHOICE` với `options` `NULL` (L7); `job_screening_questions` `SHORT_TEXT` có `options` khác `NULL`. Mỗi nhóm có ca đối chứng hợp lệ insert được | biên + âm |
| T16 | JSON của S1, S2, S6 không chứa khoá `verdict`/`label`/`isQualified`/`passed`/`recommendation` | âm |
| T17 | **`screening_revision` (R-Q11):** đọc giá trị ban đầu, rồi lần lượt S2 thêm → +1, S3 sửa → +1, S5 đổi thứ tự → +1, S4 xoá → +1 (mỗi bước đọc cả qua S1 lẫn truy vấn thẳng cột `jobs.screening_revision`); thao tác thất bại (S2 nội dung rỗng → 400, S3 id không tồn tại → 404, S5 sai tập id → 400, HR công ty khác gọi S2 → 403) → không đổi; gọi S1 nhiều lần → không đổi | dương + âm |

Kiểm tĩnh (chạy ở đợt cuối, kỳ vọng 0 dòng):

```bash
rg -n "screening" backend/src/main/java/com/recruitment/ai backend/src/main/java/com/recruitment/scoring backend/src/main/java/com/recruitment/aicontext backend/src/main/resources/ai/prompt
rg -n -i "screening" backend/src/main/java/com/recruitment/jobapplication/ApplicationSortOption.java frontend/src/features/candidates frontend/src/features/dashboard
```

### 7.2 Kiểm bằng HTTP — Windows PowerShell 5.1

Nạp seed demo + seed test (`db/seed/README.md` mục 8.1); seed test đã gồm phần sàng lọc ở mục 4.5. Backend ở `localhost:8080`. Dán
cả khối vào một cửa sổ PowerShell.

```powershell
$base = 'http://localhost:8080'

function Get-Token([string]$Email, [string]$Password) {
    $body = @{ email = $Email; password = $Password } | ConvertTo-Json
    (Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' -Body $body).accessToken
}

# In "<ma HTTP> <phuong thuc> <duong dan> <than loi neu co>". $Json = $null cho GET/DELETE.
function Test-Call([string]$Method, [string]$Path, [string]$Token, [string]$Json) {
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    try {
        if ($Json) {
            $bytes = [Text.Encoding]::UTF8.GetBytes($Json)
            $r = Invoke-WebRequest -UseBasicParsing -Method $Method -Uri "$base$Path" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $bytes
        } else {
            $r = Invoke-WebRequest -UseBasicParsing -Method $Method -Uri "$base$Path" -Headers $headers
        }
        '{0} {1} {2}' -f [int]$r.StatusCode, $Method, $Path
    } catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { 'KHONG KET NOI DUOC {0}' -f $Path; return }
        $text = $_.ErrorDetails.Message
        if (-not $text) {
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $text = $reader.ReadToEnd()
        }
        '{0} {1} {2} {3}' -f [int]$resp.StatusCode, $Method, $Path, $text
    }
}

$tokenTest = Get-Token 'minhdathr@gmail.com' '12345678'
$tokenUv   = Get-Token 'quochuyuv@gmail.com' '12345678'

$qTest   = '/api/hr/jobs/e2000000-0000-0000-0000-000000000001/screening-questions'  # tin CSKH, cong ty test
$qDemo   = '/api/hr/jobs/d2000000-0000-0000-0000-000000000001/screening-questions'  # tin cong ty demo
$qMiss   = '/api/hr/jobs/00000000-0000-0000-0000-000000000000/screening-questions'
$ansA1   = '/api/hr/applications/e8000000-0000-0000-0000-000000000001/screening-answers'
$ansDemo = '/api/hr/applications/3ec35ae2-c8ae-4100-a819-3b59f6948707/screening-answers'
$newQ    = '{"questionType":"YES_NO","questionText":"Bạn có thể bắt đầu đi làm trong tháng tới không?","required":false}'

'--- 1. Duong: HR test doc + them cau hoi tin cua minh (mong doi 200, 201)'
Test-Call 'GET'  $qTest $tokenTest $null
Test-Call 'POST' $qTest $tokenTest $newQ

'--- 2. Am: HR test doc/them cau hoi tin cong ty demo (mong doi 403 x2)'
Test-Call 'GET'  $qDemo $tokenTest $null
Test-Call 'POST' $qDemo $tokenTest $newQ

'--- 3. Am: Job khong ton tai (mong doi 404 JOB_NOT_FOUND)'
Test-Call 'GET' $qMiss $tokenTest $null

'--- 4. Am: ung vien goi endpoint HR (mong doi 403 x2); khong token (mong doi 401)'
Test-Call 'GET' $qTest $tokenUv $null
Test-Call 'GET' $ansA1 $tokenUv $null
Test-Call 'GET' $qTest $null $null

'--- 5. Duong: HR test doc ban chup don A1 (mong doi 200, state CAPTURED theo seed muc 4.5)'
Test-Call 'GET' $ansA1 $tokenTest $null

'--- 6. Am: HR test doc ban chup don cong ty demo (mong doi 403)'
Test-Call 'GET' $ansDemo $tokenTest $null
```

Mong đợi: nhóm 1 `200`, `201`; nhóm 2 hai dòng `403 … FORBIDDEN`; nhóm 3 `404 … JOB_NOT_FOUND`; nhóm 4 `403`,
`403`, `401 … UNAUTHENTICATED`; nhóm 5 `200`; nhóm 6 `403`. Chạy xong: `reset-test-data.sql` để bỏ câu hỏi vừa
thêm. **Bắt buộc ở đợt cuối:** chạy thật, dán nguyên văn output vào báo cáo; lệnh sai cú pháp thì dừng, đề
xuất sửa mục này, chờ duyệt lại (như FR-H09 mục 7.2).

### 7.3 Frontend

- `npm run build` + `npm run lint` sạch.
- `rg -n "dangerouslySetInnerHTML" frontend/src` → 0 dòng.
- `rg -n "screening" frontend/src/features/scoring frontend/src/features/candidates frontend/src/features/dashboard` → 0 dòng
  (không sắp xếp/lọc theo câu trả lời, R-N2).
- `rg -n "api/candidates" frontend/src/features/screening` → 0 dòng.
- Tên file theo `UI.md` mục 5.

### 7.4 Soát tay (desktop ≥ `lg`, 768px và 375px)

| Trạng thái cần thấy | Tài khoản | Nơi | Mong đợi |
|---|---|---|---|
| Job chưa có câu hỏi | `minhdathr@gmail.com` / `12345678` | `/hr/jobs/e2000000-0000-0000-0000-000000000002/edit?tab=screening` (tin Hành chính) | Rỗng + "Thêm câu hỏi"; đủ ba lưu ý luôn hiện, đúng chữ `UI.md` mục 7 (nhân thân; không tự loại/không chấm điểm, xếp hạng; chỉ áp dụng cho đơn nộp sau) |
| Có câu hỏi đủ 3 loại | `minhdathr@gmail.com` | `/hr/jobs/e2000000-0000-0000-0000-000000000001/edit?tab=screening` (tin CSKH) | Đúng 3 câu SQ1–SQ3 của mục 4.5 theo thứ tự, "3/10 câu hỏi"; câu đầu không có "Lên", câu cuối không có "Xuống"; ba lưu ý hiện |
| Thêm, sửa, đổi loại, xoá, đổi thứ tự | `minhdathr@gmail.com` | Tin CSKH | Lưu ngay; lỗi validation hiện dưới trường; xoá có hộp xác nhận |
| Đủ 10 câu | `minhdathr@gmail.com` | Tin CSKH sau khi thêm đủ | Nút "Thêm câu hỏi" khoá + câu lý do thành chữ |
| Đơn có bản chụp, trả lời đủ | `minhdathr@gmail.com` | `/hr/applications/e8000000-0000-0000-0000-000000000001?tab=screening` (A1) | `CAPTURED`; ba câu và ba câu trả lời đúng bảng A1 ở mục 4.5 ("Không" hiện như chữ thường); chú thích "…lúc nộp đơn (06/10/2026 00:35)…" |
| Đơn có bản chụp, có câu bỏ trống | `minhdathr@gmail.com` | `/hr/applications/e8000000-0000-0000-0000-000000000009?tab=screening` (A9, Trần Bảo Ngọc) | `CAPTURED`; đúng bảng A9 ở mục 4.5: "Có" cùng kiểu chữ với "Không" của A1; SQ3 hiện "Ứng viên không trả lời câu này." |
| Bản chụp không đổi sau khi sửa câu hỏi | `minhdathr@gmail.com` | Sửa nội dung SQ1 và xoá SQ3 ở tin CSKH, rồi mở lại A1 | Tab Sàng lọc của A1 vẫn đúng bảng A1 ở mục 4.5 |
| Đơn nộp khi Job chưa có câu hỏi | `minhdathr@gmail.com` | `/hr/applications/e8000000-0000-0000-0000-000000000002?tab=screening` (A2, tin Hành chính) | `NO_QUESTIONS`: "Tin tuyển dụng chưa có câu hỏi sàng lọc vào thời điểm ứng viên nộp đơn (06/10/2026 00:36)." |
| Đơn chưa qua bước chụp (công ty test) | `minhdathr@gmail.com` | `/hr/applications/e8000000-0000-0000-0000-000000000006?tab=screening` (A6, tin CSKH) | `NOT_CAPTURED` dù tin CSKH đang có câu hỏi |
| Đơn cũ trước khi triển khai (demo) | `hr@demo.local` / `Demo1234` | `/hr/applications/3ec35ae2-c8ae-4100-a819-3b59f6948707?tab=screening` | `NOT_CAPTURED` |
| 375px | cả hai | Cả hai tab | Không cuộn ngang toàn trang; `TabsList` của trang sửa Job cuộn ngang được (5 tab) |
| Điểm, hạng không đổi | `minhdathr@gmail.com` | Tab Ứng viên của tin CSKH trước/sau khi thêm câu hỏi | Cột Hạng/Tổng điểm không đổi; không có cột/bộ lọc sàng lọc |

Chạy `reset-test-data.sql` sau khi soát.

## 8. AI hay làm sai (dành cho người code)

- Đưa câu hỏi/câu trả lời vào prompt chấm điểm, giải thích hay K1 "cho AI hiểu ứng viên hơn"; thêm tham số
  vào `CriterionScoringService.score` (R-N1).
- Cộng điểm cho câu trả lời, thêm `sort=screening`, thêm bộ lọc "trả lời Có", đếm "đạt" ở dashboard (R-N2).
- Thêm trường "đáp án mong muốn"/"câu loại trực tiếp" vào câu hỏi "để HR tiện lọc" (R-N3).
- Tô xanh "Có", đỏ "Không"; icon ✓/✗; in đậm câu trả lời "tốt" (R-N4).
- Hiển thị bản chụp bằng cách join sang `job_screening_questions` hiện tại thay vì đọc cột `*_snapshot` — đơn
  cũ đổi theo câu hỏi đã sửa (R-S2, R-S3, T10).
- Thêm `updated_at`/trigger cho `application_screening_answers`, hoặc viết endpoint sửa câu trả lời (R-S2).
- Lưu chỉ số phương án đã chọn thay vì chữ của phương án (R-S5).
- Xoá cứng câu hỏi theo tiền lệ rubric (R-Q8, Q4).
- Kiểm quyền theo thứ tự cũ của `RubricOwnerService` (tra Job trước) — T8 sẽ đỏ; hoặc chép method nạp Job
  vào từng handler; hoặc tự viết kiểm quyền cho S6 thay vì `HrApplicationAccess.loadOwned` (R-A2, R-A3).
- Trả 404 cho Job/đơn của công ty khác "cho an toàn" — quy ước nhóm route này là 403 (R-A2, R-A3).
- Đếm số câu bằng `SELECT` rồi `INSERT` không khoá hàng `jobs` — hai tab thêm cùng lúc thành 11 câu (R-Q10, T5);
  chạy câu `UPDATE … screening_revision` **sau** khi đếm/tìm thay vì trước; thêm `@Lock(PESSIMISTIC_WRITE)` song song
  với câu `UPDATE` (R-Q10).
- Dùng lại entity `Job` nạp trước câu `UPDATE` rồi `save` nó — ghi đè trạng thái cũ của persistence context (R-Q10a).
- Thêm field `screeningCapturedAt` vào `JobApplication` ở FR-H10 (L3, bảng R-B).
- Chỉ tăng `screening_revision` ở một vài thao tác, hoặc tăng ngoài transaction của thao tác (R-Q11, T17).
- Đặt tab "Sàng lọc" sai chỗ, đổi thứ tự các tab cũ, hoặc nhúng câu trả lời vào tab "CV & điểm" (Q6, M2).
- Sửa `ApplicationService.apply`, `JobApplyForm.tsx`, hay thêm endpoint `/api/candidates/**` "cho trọn luồng"
  — phần của FR-U09 (R-B1).
- Thêm bước "Câu hỏi sàng lọc" vào wizard tạo Job hay đổi `JobCreateRequest` (Q3).
- Hardcode 10 ở frontend thay vì dùng `maxQuestions` (mục 4.4).
- Đặt `@Size`/`@NotBlank` lên `questionText`/phương án "cho gọn" — kiểm trước khi trim, `" " + 300 ký tự` bị chặn
  nhầm và lỗi ra định dạng map trường thay vì `INVALID_SCREENING_QUESTION` (R-Q4b, T2, T3).
- Đặt tên field `label` cho phương án (CLAUDE.md §7).
- Tự suy trạng thái rỗng ở frontend từ `items.length` thay vì đọc `state` — gộp mất `NOT_CAPTURED` và
  `NO_QUESTIONS` (R-A3).
- Viết comment không dấu chứa chữ "tuoi" — dương tính giả ở `srs-guard` nguyên tắc 13; viết "nhan than".
- Render câu hỏi/câu trả lời bằng HTML; trim hay cắt câu trả lời khi hiển thị.

## 9. Kế hoạch đợt code

Mỗi đợt: code xong toàn bộ đợt → chạy kiểm ở cột Kiểm → dừng → báo cáo diff → chờ duyệt → một commit (L6):
- Đợt 2, 3: `.\mvnw.cmd test-compile` rồi **chỉ** các lớp test của đợt đó bằng `-Dtest` (cùng các lớp cũ ghi ở cột
  Kiểm); không chạy full suite.
- Đợt 4, 5: `npm run build` + `npm run lint` (CLAUDE.md §5).
- Đợt 6: `.\mvnw.cmd test` toàn bộ **đúng một lần**, cùng `npm run build` + `npm run lint`.

| Đợt | Nội dung | Kiểm |
|---|---|---|
| 1 | Plan Mode: đối chiếu lại mục 0 trên HEAD lúc đó; chốt cách khoá hàng `jobs` (R-Q10 — đã chốt bằng câu `UPDATE`, mục 12 L1); chốt danh sách file | — |
| 2 | V13; entity, repository, enum; S1–S5 + mã lỗi; T1–T9, T15, T16 (phần S1/S2), T17 | test-compile; `-Dtest` lớp mới + `RubricOwner*Test`, `JobOwner*Test` |
| 3 | S6; T10–T14, T16 (phần S6); seed Q9 + cập nhật `reset-test-data.sql` + sửa `db/seed/README.md:255` (L4) | test-compile; `-Dtest` lớp mới + `ScoringRunOrchestratorTest`, lớp test `ApplicationOwner*`, `ApplicationHrDetail*` |
| 4 | Frontend: tab "Câu hỏi sàng lọc" ở trang sửa Job (`features/screening`) | build + lint |
| 5 | Frontend: tab "Sàng lọc" ở trang hồ sơ đơn | build + lint; lệnh 7.3 |
| 6 | Đợt cuối: full `.\mvnw.cmd test`, khối 7.2 (dán output), kiểm tĩnh 7.1, `srs-guard`, `walkthrough`, trạng thái `ĐÃ HOÀN THÀNH`, tài liệu mục 10 | full `mvnw test` (một lần); build + lint; 7.2; 7.3 |

## 10. Tài liệu dùng chung sẽ sửa (ở đợt cuối, không sửa trong commit đặc tả)

| # | File | Thay đổi |
|---|---|---|
| D1 | `docs/SRS.md:38`, `:101` | Dòng 38: `Chưa đặc tả` → `Đã hoàn thành`. Dòng 101 (bảng "Chức năng bổ sung" mục 2): cột Phụ thuộc của FR-H10 `FR-H02` → `FR-H02, FR-H09` |
| D2 | `CLAUDE.md` §3 | Thêm `screening/` vào danh sách package |
| D3 | `docs/UI_GUIDE.md` mục 7, dòng `/hr/applications/:id` | Thêm tab "Sàng lọc" và `FR-H10` vào cột FR; bỏ "tab Sàng lọc (FR-H10)" khỏi ghi chú "FR sau tự thêm" |
| D4 | `docs/ROADMAP.md:854` | Tick + tóm tắt + nợ kỹ thuật: (1) đường ghi chờ FR-U09, mọi đơn hiện `NOT_CAPTURED` tới khi FR-U09 xong; (2) M4 — `JobOwnerService.loadOwned` (`:200-209`) và `RubricOwnerService.loadOwnedRubric` (`:95-104`) còn tra Job trước khi kiểm công ty, lệch khuôn `chore/hardening` mà FR-H10 dùng (HR chưa có công ty + jobId không tồn tại: hai service cũ trả `JOB_NOT_FOUND`, FR-H10 trả `COMPANY_NOT_FOUND`) |
| D5 | `docs/features/README.md` mục FR-H10 | M1: "Khi tạo/sửa Job → tab Câu hỏi sàng lọc" → "Sau khi tạo Job, ở trang sửa Job → tab Câu hỏi sàng lọc". M2: "hiển thị cạnh điểm số và CV" → "hiển thị ở tab Sàng lọc của trang hồ sơ đơn". Chỉ sửa hai câu này |

## 11. Đã chốt 10/10/2026

Phần thân đã viết theo các phương án dưới đây.

| # | Câu hỏi | Đã chọn | Lý do |
|---|---|---|---|
| Q1 | Sửa câu hỏi khi Job `OPEN` đã có đơn? | Cho sửa ở mọi trạng thái, kể cả đã có đơn (R-Q9) | Bản chụp giữ nguyên đơn cũ; câu trả lời không vào điểm/hạng nên không cần khoá như rubric |
| Q2 | Giới hạn | ≤ 10 câu/Job; câu hỏi 1–300 ký tự; 2–10 phương án, mỗi phương án 1–100 ký tự; trả lời ngắn ≤ 500 ký tự; chốt ở DB | Giữ form nộp đơn ngắn; chốt độ dài ở DB theo tiền lệ `chk_app_msg_body_length` (V12) |
| Q3 | Nhập câu hỏi lúc tạo Job hay sau? | Chỉ sau khi Job tồn tại, ở tab của `/hr/jobs/:id/edit`; không đổi wizard, `JobCreateRequest`, `JobOwnerService.create` (M1) | Giống Rubric (`HrJobCreatePage.tsx:185`); Job luôn tạo ở `DRAFT` nên chưa ai nộp được trước khi có câu hỏi |
| Q4 | Xoá cứng hay mềm? | Mềm (`deleted_at`) (R-Q8, M3) | `CLAUDE.md` §4; bản chụp chép đủ nội dung nên không phụ thuộc câu gốc |
| Q5 | Ai tạo migration bảng bản chụp? | FR-H10 tạo cả hai bảng + `screening_captured_at` + `screening_revision` trong V13; FR-H10 chỉ đọc, FR-U09 ghi | FR-H10 cần bảng thật để hiện tab Sàng lọc và để test cô lập chấm điểm (T13, T14) |
| Q6 | Vị trí tab "Sàng lọc" trên trang hồ sơ đơn | **Ngay sau "Giải thích"**: CV & điểm → Giải thích → Sàng lọc → Lịch sử → Trao đổi; tab mặc định vẫn `cv`; thứ tự tương đối của bốn tab hiện có không đổi (M2) | Người duyệt chốt thứ tự CV & điểm → Giải thích → Sàng lọc, không theo đề xuất "ngay sau CV & điểm"; code thật hiện là `['cv','explanation','history','messages']` (`HrApplicationDetailPage.tsx:18`, `:139-142`) |
| Q7 | Chống lệch khi HR sửa câu hỏi lúc ứng viên đang điền | Cột `jobs.screening_revision`; cả bốn thao tác ghi tăng 1 trong cùng transaction (R-Q11, T17); FR-U09 trả 409 khi lệch | Tránh ghép câu trả lời với câu hỏi đã bị sửa nghĩa; một số nguyên so được trong transaction nộp đơn |
| Q8 | Chặn từ khoá nhân thân ở backend? | Không chặn; lưu ý luôn hiện ở form (R-P1, R-P2) | Danh sách từ dễ chặn nhầm, dễ lách; không được dùng AI để nhận diện (SRS mục 5) |
| Q9 | Seed để soát tay | Mục 4.5: 3 câu SQ1–SQ3 cho tin CSKH; bản chụp cho A1, A9; A2 đã chụp 0 dòng; cập nhật `reset-test-data.sql`; không đụng seed demo | Câu trả lời là dữ liệu ứng viên, không phải output AI; cần để soát `CAPTURED`/`NO_QUESTIONS` trước FR-U09 |
| Q10 | Job đã xoá mềm hay `CLOSED` có sửa được câu hỏi? | Như tab Rubric: không chặn riêng | Job không `OPEN` thì không nhận đơn; giữ một hành vi chung cho các tab của trang sửa Job |

## 12. Làm rõ sau Plan Mode (duyệt 10/10/2026)

- **L1 (R-Q10, R-Q10a, R-Q10b; mục 0 a14, mục 4.2, mục 8).** Bỏ `PESSIMISTIC_WRITE`: codebase chưa có `@Lock` nào
  (`grep -rn "@Lock\|PESSIMISTIC"` trên `backend/src/main` → 0 dòng). Khoá hàng `jobs` bằng chính câu
  `UPDATE jobs SET screening_revision = screening_revision + 1 WHERE id = :jobId`, chạy sau kiểm quyền và kiểm đầu
  vào, trước khi đếm/tìm/ghi câu hỏi — một câu vừa khoá vừa tăng revision (R-Q11), lỗi ở bước sau rollback cả lần
  tăng. Sau câu `UPDATE` không dùng lại entity `Job` đã nạp trong cùng persistence context. Đã soát `jobs.updated_at`
  (trigger đổi cột này khi `UPDATE`): không chỗ nào sắp xếp theo hay hiển thị (mục 0, a14).
- **L2 (T5).** Bỏ "dọn dữ liệu sau test": T5 dùng dữ liệu ngẫu nhiên riêng, chỉ khẳng định trên Job của chính test,
  theo tiền lệ `ScoringRunOrchestratorTest`, `ScoreExplanationOrchestratorTest` (không `@Transactional`, không
  `@AfterEach`).
- **L3 (mục 4.2, bảng R-B).** `screening_captured_at` đọc bằng native query trong `screening/`, không sửa
  `JobApplication.java` (hiện không map cột này). FR-U09 thêm field vào entity khi làm đường ghi.
- **L4 (mục 4.5, mục 9).** Đợt 3 sửa thêm `db/seed/README.md:255` ("UUID tiền tố `e0…`–`ed…`" → "`e0…`–`ef…`").
- **L5 (mục 4.1).** Không thêm index cho `application_screening_answers`: `uq_screening_answer_order
  (application_id, display_order)` đã đủ cho S6. Trigger `updated_at` của `job_screening_questions` viết thành câu
  `CREATE TRIGGER` riêng trong V13.
- **L6 (mục 9).** Đợt 2, 3 chỉ chạy các lớp test của đợt đó; đợt 4, 5 chạy `npm run build` + `npm run lint`;
  `.\mvnw.cmd test` toàn bộ chỉ chạy ở đợt 6.
- **L7 (mục 4.1, T15; duyệt trong đợt 2).** T15 lộ lỗ hổng logic ba trị trong hai CHECK đã duyệt: bản chụp
  `SINGLE_CHOICE` với `options_snapshot = NULL` insert được, vì `jsonb_typeof(NULL) = 'array'` ra `NULL`, vế sau ra
  `FALSE`, `NULL OR FALSE` ra `NULL`, và Postgres coi CHECK ra `NULL` là đạt. Sửa `chk_screening_question_options` và
  `chk_screening_answer_options`: thêm `options IS NOT NULL` / `options_snapshot IS NOT NULL` vào vế `SINGLE_CHOICE`.
  Soát lại mọi CHECK còn lại của V13 (bảng ở mục 4.1): không lỗ hổng nào khác. Comment của V13 ghi lý do cho từng cột
  được `NULL` có chủ đích. T15 thêm ca `job_screening_questions` `SINGLE_CHOICE` + `options NULL` → bị chặn. Khối SQL
  ở mục 4.1 thay bằng nguyên văn file V13.
- **L8 (T10; duyệt trong đợt 3).** T10 ghi dòng bản chụp bằng SQL (`JdbcTemplate`), không qua repository: entity
  `ApplicationScreeningAnswer` là `@Immutable`, không có setter, để FR-H10 không có đường ghi bản chụp nào (R-B2 cho
  phép test tạo dòng bằng repository/SQL). Câu chữ T10 đổi thành "bằng repository/SQL".
- **L9 (UI.md mục 9; duyệt trong đợt 4).** Thông báo `aria-live` sau khi đổi thứ tự câu hỏi dùng một câu cho cả
  hai chiều ↑/↓: "Đã chuyển câu "{nội dung rút gọn}" tới vị trí {n}." (trước đây "lên vị trí {n}", đọc sai khi đưa
  câu xuống). Câu này không có ở UI.md mục 7; `screeningLabels.ts` dùng đúng nguyên văn.
