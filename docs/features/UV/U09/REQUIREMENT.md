# FR-U09 — Trả lời sàng lọc và đồng ý lưu hồ sơ

> Trạng thái: ĐÃ DUYỆT 11/10/2026. Q1–Q20 và B1–B7 đã chốt 11/10/2026 (mục 11); O2 chờ Plan Mode (mục 12).

- Nhóm: Ứng viên
- Tóm tắt: Ở form nộp đơn `/jobs/:id/apply`, ứng viên trả lời các câu hỏi sàng lọc của tin (FR-H10) và tuỳ chọn
  tick "Cho phép công ty lưu hồ sơ…". Backend chặn câu bắt buộc bỏ trống, câu trả lời sai hình dạng, và bộ câu
  hỏi đã đổi giữa lúc điền (409); ghi bản chụp câu hỏi + câu trả lời trong **cùng** giao dịch nộp đơn. Ở trang
  chi tiết đơn `/candidate/applications/:id` (FR-U08), ứng viên xem lại câu trả lời của mình và trạng thái đồng
  ý, kèm nút "Rút đồng ý" (một chiều).
- Phụ thuộc: FR-U02, FR-H10 — cả hai **Đã hoàn thành** (`docs/SRS.md` mục 0). Trang chi tiết đơn của FR-U08 —
  **Đã hoàn thành**.
- Nhánh: `feat/fr-u09-screening-answer`, tách từ `main` tại `497f5cf`.
- Mở rộng: FR-U02 (form nộp đơn, `ApplicationService.apply`), FR-U08 (hai khối mới trong tab "Thông tin đơn",
  thêm một object lồng vào E1), FR-H10 (đường ghi bản chụp; tab "Sàng lọc" phía HR dùng chung component danh sách,
  **giao diện không đổi**).
- Bên nhận phần còn lại: **FR-H15** xoá mục khỏi mọi kho khi ứng viên rút đồng ý, chặn thêm đơn chưa đồng ý vào
  kho, và (nếu cần) hiện trạng thái đồng ý cho HR.

## 0. Đã đối chiếu code trước khi viết (CLAUDE.md §6)

Đối chiếu trên `feat/fr-u09-screening-answer` tại `497f5cf` (11/10/2026).

### (a) Hiện trạng

| # | Điểm kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| a1 | Request nộp đơn có đúng 4 field | **Đúng** | `ApplicationCreateRequest.java:8-12`: `@NotNull jobId`, `@NotNull resumeId`, `@AssertTrue boolean aiConsent`, `String coverLetter` |
| a2 | `apply` ghi trong một transaction: Job mở → CV của mình → INSERT đơn → lịch sử → event | **Đúng** | `ApplicationService.java:61-99`; `findOpenJobById` `:65-67`; `aiConsentAt = Instant.now()` `:82`; `saveAndFlush` `:88`; lịch sử `:92`; `ApplicationSubmittedEvent` `:96` |
| a3 | Chống nộp trùng chỉ ở DB | **Đúng** | `uq_application_per_cycle` (`V1__init_schema.sql:222`) → 409 `APPLICATION_DUPLICATE` (`GlobalExceptionHandler.java:372-377`); vi phạm khác của DB **ra 500** (`:393`) |
| a4 | Form nộp đơn dùng `useState`, không React Hook Form + Zod | **Đúng — lệch CLAUDE.md §3** | `JobApplyForm.tsx:27-29`; payload `:53-58`; ô AI consent `:124-134`; nút khoá khi chưa tick `:145`; màn thành công chỉ có link về `/` `:61-73` |
| a5 | Lỗi 400 Bean Validation (`{"aiConsent": "…"}`) không hiện được câu backend | **Đúng** | `handleValidation` trả map trường → câu (`GlobalExceptionHandler.java:22-29`); `extractErrorMessage` chỉ đọc `data.message` (`JobApplyForm.tsx:15-23`, `lib/httpError.ts:3-11`) → rơi về câu fallback |
| a6 | Bảng bản chụp và cột đánh dấu đã có (V13), chưa có đường ghi | **Đúng** | `V13__screening_questions.sql:39-71` (bảng), `:74` (`screening_captured_at`), `:77` (`screening_revision`). Entity `ApplicationScreeningAnswer` `@Immutable`, không setter, constructor `protected`, `@Id` **không** `@UuidGenerator` (`ApplicationScreeningAnswer.java:22-30`). Dòng bản chụp hiện chỉ do seed/test tạo bằng SQL |
| a7 | Chưa có endpoint ứng viên nào về sàng lọc | **Đúng** | Controller của `screening/` chỉ có `/api/hr/...` (`ScreeningQuestionOwnerController.java:25`, `ApplicationScreeningHrController.java:21`) |
| a8 | `screening_revision` tăng đúng 1 ở mọi thao tác ghi của HR, bằng câu `UPDATE` vừa khoá hàng `jobs` | **Đúng** | `ScreeningQuestionRepository.java:30-34` (`incrementRevision`), `:36-37` (`findRevision`) |
| a9 | Entity `JobApplication` chưa map `screening_captured_at` | **Đúng** | `JobApplication.java:26-64`; H10 đọc cột này bằng native query (`ApplicationScreeningAnswerRepository.java:17-18`) |
| a10 | Schema V1–V13 chưa có cột đồng ý lưu hồ sơ | **Đúng** | Chỉ có `ai_consent`, `ai_consent_at`, `chk_consent_true` (`V1__init_schema.sql:215-220`). Migration kế tiếp: **V14** (V9 là migration Java) |
| a11 | Rút đơn đọc entity rồi `save` toàn bộ cột | **Đúng** | `ApplicationService.java:114-115`. Hibernate không `@DynamicUpdate` nên ghi lại **mọi** cột updatable — xem R-K6 |
| a12 | Đổi trạng thái phía HR dùng `UPDATE` có điều kiện | **Đúng** | `JobApplicationRepository.java:31` (`updateStatusIfCurrent`), `ApplicationStatusService.java:56-79` |
| a13 | `job_applications.updated_at` có trigger, không màn hình nào hiển thị | **Đúng** | Trigger đăng ký ở `V1__init_schema.sql:373`; `grep updatedAt|updated_at` trên `frontend/src/**/*.tsx` → 0 dòng; backend chỉ trả ra ở `ApplicationSummaryResponse`/E1 |
| a14 | E1 (FR-U08) bị test khoá **đúng bộ khoá** | **Đúng** | `ApplicationCandidateDetailControllerIntegrationTest.java:335-336` (`containsExactlyInAnyOrder("id", "status", "appliedAt", "updatedAt", "coverLetter", "job", "resume")`); danh sách khoá cấm `:61-83` |
| a15 | Kiểm tĩnh của FR-H10 cấm `api/candidates` trong `features/screening` | **Đúng** | H10 REQUIREMENT mục 7.3 dòng 4 — phần gọi API phía ứng viên phải ở thư mục khác (mục 4.3) |
| a16 | Tab "Sàng lọc" phía HR viết sẵn chuỗi cho HR và import `TabLoadError` của HR | **Đúng** | `ScreeningTab.tsx:3-9`, `:65-83`; chuỗi `screeningLabels.ts:65-77` ("Ứng viên không trả lời câu này.") |
| a17 | K1 chỉ đọc getter cụ thể của `JobApplication` | **Đúng** | `ConversationContextAssembler.java:62-90` (`getJobId`, `getCandidateId`, `getStatus`) — field mới không tự lọt vào AI |
| a18 | Nơi gọi `new ApplicationCreateRequest(...)` | **9 chỗ, chỉ trong test** | `ApplicationServiceTest.java:96`, `:126`; `NotificationEventListenerIntegrationTest.java:117`, `:131`, `:151`, `:173`, `:187`, `:205`, `:226`. Không có trong `src/main` |
| a19 | File test gửi JSON nộp đơn kiểu cũ | **25 file** | `grep -l '"aiConsent"' backend/src/test/java` → 25 file (26 dòng); mọi body chỉ có `jobId`, `resumeId`, `aiConsent`, có thể `coverLetter` |
| a20 | `ApplicationServiceTest` dựng service bằng constructor | **Đúng** | `newService()` `ApplicationServiceTest.java:60-70` — thêm dependency là phải thêm một `@Mock` (R-C3) |
| a21 | `components/ui` không có RadioGroup | **Đúng** | `button, card, checkbox, dialog, input, label, popover, select, sheet, table, tabs, textarea` |
| a22 | Thư viện form có sẵn | **Đúng** | `react-hook-form ^7.84.0`, `@hookform/resolvers ^5.7.1`, `zod ^4.4.3` (`frontend/package.json`) |

### (b) Lệch / mâu thuẫn đã có hướng xử lý

| # | Điểm | Xử lý (đã chốt 11/10/2026, mục 11) |
|---|---|---|
| M1 | FR-U08 chốt E1 "không thêm field nào" và test khoá bộ khoá (a14) | **Ngoại lệ đã duyệt** (Q11, R-C2): thêm đúng **một** khoá `talentPoolConsent` vào cấp gốc; không bỏ khoá nào khỏi `FORBIDDEN_KEYS` |
| M2 | Rút đơn `save` toàn bộ cột (a11) sẽ ghi đè cờ đồng ý vừa rút | Ba cột đồng ý và `screening_captured_at` map `updatable = false` (R-K6) — chỉ câu `UPDATE` riêng của rút đồng ý ghi được |
| M3 | B4 "rút đồng ý không đụng cột khác" nhưng trigger V1 luôn đổi `updated_at` khi `UPDATE` | **Đã chốt** (Q19): câu lệnh chỉ `SET` 2 cột; `updated_at` đổi do trigger V1; T10 loại trừ `updated_at` khi so |
| M4 | CLAUDE.md §3c cấm `SELECT … FOR UPDATE SKIP LOCKED` để claim job nền; H10 L1 tránh `@Lock` | `FOR SHARE` ở `apply` là khoá đọc trong transaction ngắn không có lời gọi ngoài, **không** phải claim job nền; viết bằng native query, không `@Lock` (Q4, R-R3) |

## 1. Mục đích

Cho ứng viên trả lời các câu hỏi sàng lọc HR đặt cho tin (FR-H10) ngay lúc nộp đơn, và tự quyết định có cho công
ty lưu hồ sơ để liên hệ cho các vị trí sau hay không. Câu trả lời là thông tin tham khảo cho HR, không đi vào
chấm điểm, tổng điểm hay xếp hạng; đồng ý lưu hồ sơ là tuỳ chọn, tính theo từng đơn, không ảnh hưởng việc xét
tuyển, và rút được bất cứ lúc nào (một chiều).

## 2. Luồng người dùng

**Ứng viên nộp đơn** (đã đăng nhập, role CANDIDATE)
1. Từ `/jobs/:id` bấm Ứng tuyển → `/jobs/:id/apply`.
2. Form tải danh sách CV (như hiện tại) và bộ câu hỏi sàng lọc của tin (Q1). Tin có câu hỏi: khối "Câu hỏi sàng
   lọc" hiện giữa "Thư giới thiệu" và ô AI consent. Tin không có câu hỏi: không có khối này.
3. Ứng viên trả lời: trả lời ngắn (ô nhiều dòng, đếm ký tự), một lựa chọn (nút chọn), có/không (nút chọn). Câu
   không bắt buộc có thể bỏ trống.
4. Tick ô AI consent (bắt buộc, như hiện tại). Tuỳ chọn tick ô "Cho phép công ty lưu hồ sơ của tôi để liên hệ cho
   các vị trí sau này" (mặc định không tick, không chặn nút Nộp).
5. Bấm "Nộp đơn".
   - Thành công → màn thành công có liên kết "Xem đơn vừa nộp" (`/candidate/applications/{id}`) (Q18).
   - 409 `SCREENING_QUESTIONS_CHANGED` → câu thông báo; form tải lại câu hỏi; giữ câu trả lời của câu còn nguyên,
     xoá phần còn lại (R-F5); ứng viên kiểm lại rồi nộp lại.
   - 400/404/409 khác → câu lỗi của backend dưới form.

**Ứng viên xem lại** (trang chi tiết đơn FR-U08, tab "Thông tin đơn")
1. Sau khối "Hồ sơ đã nộp": khối "Câu trả lời sàng lọc" (chỉ khi đơn có bản chụp có câu hỏi, R-D2) rồi khối "Lưu
   hồ sơ cho đợt tuyển sau" (luôn có).
2. Khi đang đồng ý: bấm "Rút đồng ý" → hộp xác nhận → xác nhận → khối đổi sang "đã rút", không tải lại trang.

**Hệ thống**
- Không gọi AI. Không tạo lượt chấm. Thông báo "đơn mới" cho HR giữ nguyên nội dung (R-N4). Rút đồng ý không tạo
  thông báo, không đổi trạng thái đơn.

## 3. Quy tắc nghiệp vụ

### 3.1 Form và bộ câu hỏi (R-F)

- **R-F1.** Endpoint F1 (mục 4.4) trả bộ câu hỏi **đang hoạt động** (`deleted_at IS NULL`) của tin, theo
  `display_order` rồi `id`, kèm `revision = jobs.screening_revision`. Chỉ tin xem được công khai: nạp bằng
  **chính** `jobRepository.findOpenJobById` (Q1) — tin `DRAFT`/`PAUSED`/`CLOSED`/quá hạn/xoá mềm/không tồn tại
  → `404 JOB_NOT_FOUND`, giống `apply`.
- **R-F2.** F1 **không** trả gì ngoài nội dung câu hỏi: không `deleted_at`, không câu đã xoá, không thông tin công ty
  hay đơn. Không kiểm "đã nộp đơn chưa" (nộp trùng do `apply` chặn, 409 `APPLICATION_DUPLICATE`).
- **R-F3. Thứ tự đọc ở F1 (quy tắc cứng, Q4).** Trong transaction `readOnly`: (1) `findOpenJobById`; (2) đọc
  `screening_revision`; (3) **sau đó** mới đọc danh sách câu hỏi. Lý do: transaction HR commit xen giữa (2) và
  (3) cho client revision cũ + câu hỏi mới → lần nộp sau bị 409 (an toàn). Đảo thứ tự cho client revision mới +
  câu hỏi cũ → `apply` chấp nhận câu trả lời của bộ câu hỏi cũ ghép với bộ mới (sai). Có test (T2).
- **R-F4.** Frontend luôn gửi `screeningRevision` = `revision` nhận từ F1 (kể cả khi tin có 0 câu) và `answers`
  cho mọi câu ứng viên đã trả lời. Câu không bắt buộc bỏ trống: **không** gửi phần tử (hoặc gửi phần tử có giá
  trị rỗng — backend coi như không trả lời, R-V5).
- **R-F5. Sau 409 `SCREENING_QUESTIONS_CHANGED` (Q3).** Frontend tải lại F1. Với từng câu mới: giữ câu trả lời cũ
  **chỉ khi** có câu cũ cùng `id` **và** cùng `questionType`; riêng `SINGLE_CHOICE` chỉ giữ khi chữ phương án đã
  chọn **còn** trong `options` mới (so khớp đúng từng ký tự). Mọi câu trả lời khác bị xoá. Ô CV, thư giới thiệu,
  hai ô đồng ý giữ nguyên.
- **R-F6.** Không giới hạn số lần nộp lại sau 409; mỗi lần là một request `apply` mới.

### 3.2 Revision và khoá (R-R)

- **R-R1. `screeningRevision` được `null` (Q2).** `null` nghĩa là "client không thấy câu hỏi nào": chỉ nhận khi
  tin **hiện có 0 câu đang hoạt động**; tin có ≥ 1 câu → `409 SCREENING_QUESTIONS_CHANGED`. Nhờ vậy 25 file test
  gửi JSON cũ (a19, tin của chúng không có câu hỏi) giữ nguyên và vẫn xanh.
- **R-R2.** `screeningRevision` khác `null` phải **bằng** `jobs.screening_revision` hiện tại; khác → `409
  SCREENING_QUESTIONS_CHANGED` (kể cả khi tin có 0 câu — bộ câu hỏi đã đổi so với lúc client tải).
- **R-R3. Khoá ở `apply` (Q4).** Ngay sau khi nạp Job và CV, `apply` chạy native
  `SELECT screening_revision FROM jobs WHERE id = :jobId FOR SHARE` (không `@Lock`, không entity), **rồi** mới
  so revision (R-R1, R-R2), **rồi** mới đọc câu hỏi đang hoạt động và kiểm câu trả lời (R-V), ghi đơn, ghi bản
  chụp. Khoá giữ tới commit:
  - Transaction HR (S2–S5 của H10) đang giữ khoá hàng bằng câu `UPDATE … screening_revision + 1` → `FOR SHARE`
    chờ tới khi HR commit, rồi đọc giá trị **đã commit** (READ COMMITTED) → revision mới ≠ revision của client →
    409.
  - `apply` đang giữ `FOR SHARE` → câu `UPDATE` của HR chờ tới khi `apply` commit → bản chụp đã ghi khớp đúng bộ
    câu hỏi `apply` đã kiểm; thay đổi của HR áp dụng sau.
  - Hai `apply` song song vào cùng tin không chặn nhau (`FOR SHARE` tương thích `FOR SHARE`).
  - Hệ quả đã chấp nhận: trong lúc `apply` giữ `FOR SHARE`, **mọi** câu `UPDATE` vào chính hàng `jobs` đó (HR sửa
    tin, đổi trạng thái tin, sửa câu hỏi sàng lọc) phải chờ tới khi `apply` commit. Thời gian chờ ngắn vì transaction
    `apply` không có lời gọi ngoài (R-R4). Đã soát không deadlock với khoá khoá ngoại khi INSERT đơn/bản chụp: INSERT
    `job_applications` lấy `FOR KEY SHARE` trên hàng `jobs` (tương thích `FOR SHARE`); INSERT bản chụp lấy
    `FOR KEY SHARE` trên `job_screening_questions`, không xung đột với `FOR NO KEY UPDATE` của HR khi sửa cột không
    phải khoá, và transaction HR đằng nào cũng đã dừng ở câu `UPDATE jobs` trước khi chạm bảng câu hỏi.
- **R-R4.** Transaction `apply` không chứa lời gọi ngoài nào (không LLM, không email, không tệp) — khoá chỉ giữ
  trong thời gian vài câu SQL. Thông báo cho HR vẫn chạy `AFTER_COMMIT` như hiện tại.
- **R-R5.** Không bao giờ ghép lệch: mọi đơn tạo ra đều có bản chụp đúng bằng tập câu đang hoạt động tại thời điểm
  `apply` giữ khoá (T8).

### 3.3 Kiểm câu trả lời (R-V)

Mọi vi phạm bị chặn **ở service**, trả `400 INVALID_SCREENING_ANSWER` (`ErrorResponse {error, message}`) với câu
tiếng Việt cố định — **không** để rơi xuống CHECK của DB thành 500 (Q15). DTO **không** dùng `@Size`/`@NotBlank`/
`@Valid` lồng cho câu trả lời (Bean Validation chạy trước khi bỏ khoảng trắng — tiền lệ R-Q4b của FR-H10).

- **R-V1. Bảng lỗi** (enum `ScreeningAnswerViolation`, thứ tự khai báo = thứ tự ưu tiên ở R-V2):

  | Mã enum | Trường hợp | `message` |
  |---|---|---|
  | `UNKNOWN_QUESTION` | Phần tử `null`; `questionId` `null`; `questionId` không thuộc tập câu **đang hoạt động** của **chính** tin (id lạ, câu đã xoá mềm, câu của tin khác) | "Câu trả lời không khớp với câu hỏi sàng lọc của tin tuyển dụng." |
  | `DUPLICATE_QUESTION` | Hai phần tử cùng `questionId` | "Mỗi câu hỏi sàng lọc chỉ được trả lời một lần." |
  | `TYPE_MISMATCH` | Câu `YES_NO` có `answerText` khác `null`; câu `SHORT_TEXT`/`SINGLE_CHOICE` có `answerYesNo` khác `null` | "Câu trả lời không đúng loại câu hỏi sàng lọc." |
  | `REQUIRED_MISSING` | Câu bắt buộc: không có phần tử; `SHORT_TEXT`/`SINGLE_CHOICE` có `answerText` `null`, rỗng hoặc chỉ khoảng trắng; `YES_NO` có `answerYesNo` `null` | "Vui lòng trả lời đầy đủ các câu hỏi sàng lọc bắt buộc." |
  | `ANSWER_TOO_LONG` | `SHORT_TEXT` dài hơn 500 ký tự **sau khi bỏ khoảng trắng hai đầu**, đếm theo code point | "Câu trả lời sàng lọc tối đa 500 ký tự." |
  | `OPTION_NOT_FOUND` | `SINGLE_CHOICE`: `answerText` sau khi bỏ khoảng trắng hai đầu không **bằng đúng** (phân biệt hoa thường) một phần tử của `options` hiện tại của câu | "Phương án đã chọn không có trong câu hỏi sàng lọc." |

- **R-V2. Thứ tự kiểm (dừng ở lỗi đầu tiên).** (1) Duyệt `answers` theo thứ tự gửi: `UNKNOWN_QUESTION`, rồi
  `DUPLICATE_QUESTION`. (2) Duyệt câu đang hoạt động theo `display_order`, `id`; với mỗi câu: `TYPE_MISMATCH` →
  `REQUIRED_MISSING` → `ANSWER_TOO_LONG` → `OPTION_NOT_FOUND`. Message không chứa số thứ tự câu hay nội dung người
  dùng (frontend đã báo lỗi dưới từng câu bằng Zod).
- **R-V3. Thứ tự giữa các lỗi của `apply` (Q15).** Tin không mở (404 `JOB_NOT_FOUND`) → CV không thuộc mình (404
  `RESUME_NOT_FOUND`) → khoá + revision (409 `SCREENING_QUESTIONS_CHANGED`) → câu trả lời (400
  `INVALID_SCREENING_ANSWER`) → INSERT đơn (409 `APPLICATION_DUPLICATE` từ `uq_application_per_cycle`) → INSERT bản
  chụp. Câu trả lời sai + đã nộp rồi → **400** (kiểm trước INSERT). Lỗi Bean Validation của request (`jobId`/
  `resumeId` thiếu, `aiConsent` khác `true`) giữ nguyên hành vi hiện tại (400 map trường → câu), chạy trước tất cả.
- **R-V4. Chuẩn hoá giá trị lưu.** `SHORT_TEXT`: lưu chuỗi đã bỏ khoảng trắng hai đầu, giữ nguyên xuống dòng bên
  trong, không cắt, không lọc ký tự. `SINGLE_CHOICE`: lưu **chữ của phần tử trong `options`** đã khớp (không lưu
  chỉ số, R-S5 của H10). `YES_NO`: lưu boolean.
- **R-V5. Câu không bắt buộc bỏ trống** (không có phần tử, hoặc `answerText` rỗng/chỉ khoảng trắng, hoặc
  `answerYesNo` `null`): **vẫn có dòng bản chụp**, `answer_text` và `answer_yes_no` đều `NULL` (R-S1 của H10).
- **R-V6.** JSON sai kiểu dữ liệu (vd `"answerYesNo": "abc"`, `"screeningRevision": "x"`) bị Spring chặn trước khi
  vào service → 400 mặc định của Spring, không phải 500. Không thêm handler mới cho trường hợp này (ngoài phạm vi).

### 3.4 Ghi bản chụp (R-S)

- **R-S1.** Trong **cùng** transaction của `apply`, sau `saveAndFlush` đơn: mỗi câu đang hoạt động (tập đã kiểm ở
  R-V, đọc sau khoá R-R3) thành **một** dòng `application_screening_answers`, chép `display_order`,
  `question_type`, `question_text`, `options`, `is_required` vào các cột `*_snapshot`, `question_id` = id câu gốc,
  cùng câu trả lời đã chuẩn hoá (R-V4, R-V5). Tin có 0 câu → 0 dòng.
- **R-S2.** `job_applications.screening_captured_at` đặt **mọi** lần `apply` thành công (kể cả 0 câu, kể cả
  `screeningRevision = null`), bằng **cùng một** `Instant` với `ai_consent_at` (Q6): `Instant now = Instant.now()`
  một lần ở đầu phần ghi. Không còn đơn mới nào ở trạng thái `NOT_CAPTURED`.
- **R-S3.** Ghi bản chụp qua factory tĩnh của entity `ApplicationScreeningAnswer` + `@UuidGenerator`, entity vẫn
  `@Immutable`, không setter (Q5). Nếu Plan Mode chứng minh Hibernate 7 không persist được entity `@Immutable` thì
  chuyển sang native INSERT trong `ApplicationScreeningAnswerRepository` và báo lại để duyệt (mục 12, O2).
- **R-S4.** Bản chụp bất biến như H10 R-S2: không endpoint sửa/xoá, không `updated_at`. Ứng viên không sửa được câu
  trả lời sau khi nộp.

### 3.5 Đồng ý lưu hồ sơ (R-K)

- **R-K1.** Request nộp đơn có `talentPoolConsent` (`Boolean`, `null` → `false`). Mặc định không đồng ý; không
  tick vẫn nộp bình thường.
- **R-K2.** Đồng ý → `talent_pool_consent = TRUE`, `talent_pool_consent_at` = cùng `Instant` với `ai_consent_at`
  (R-S2), `talent_pool_consent_revoked_at = NULL`. Không đồng ý → `FALSE`, `NULL`, `NULL`.
- **R-K3.** Đồng ý tính **theo từng đơn**: hai đơn vào cùng công ty có thể khác nhau. Không có cờ ở cấp người dùng.
- **R-K4.** Chỉ chọn **lúc nộp** (Q9). Không có nút/endpoint đồng ý về sau; đơn `NOT_GIVEN` hay `REVOKED` không
  bật lại được.
- **R-K5. Trạng thái do backend tính** (enum `TalentPoolConsentState`):

  | `state` | Điều kiện |
  |---|---|
  | `ACTIVE` | `talent_pool_consent = TRUE` |
  | `REVOKED` | `talent_pool_consent = FALSE AND talent_pool_consent_revoked_at IS NOT NULL` |
  | `NOT_GIVEN` | `talent_pool_consent = FALSE AND talent_pool_consent_revoked_at IS NULL` (mọi đơn cũ, kể cả seed demo) |

- **R-K6. Chỉ đường rút đồng ý ghi được ba cột đồng ý.** Ba cột đồng ý và `screening_captured_at` map
  `@Column(updatable = false)` trong `JobApplication`: rút đơn (`save` cả entity, a11) hay bất kỳ `save` nào sau
  này **không** ghi đè được cờ vừa rút. Giá trị ban đầu ghi lúc INSERT.
- **R-K7. Rút đồng ý (Q8).** `PATCH /api/candidates/applications/{id}/talent-pool-consent/revoke`:
  - nạp đơn bằng `findByIdAndCandidateId` → không tồn tại hoặc của người khác → **cùng** `404
    APPLICATION_NOT_FOUND`;
  - **mọi** trạng thái đơn (`PENDING`, `INTERVIEW_INVITED`, `HIRED`, `REJECTED`, `WITHDRAWN`) đều rút được;
  - ghi bằng **một** câu native `UPDATE` có điều kiện, `@Modifying(clearAutomatically = true)`:
    `UPDATE job_applications SET talent_pool_consent = FALSE, talent_pool_consent_revoked_at = :now
    WHERE id = :id AND candidate_id = :candidateId AND talent_pool_consent = TRUE`. **Không** đọc entity rồi
    `save` (B4);
  - 0 dòng bị đổi → `409 TALENT_POOL_CONSENT_NOT_ACTIVE` (đơn `NOT_GIVEN`, đã `REVOKED`, hoặc hai tab bấm cùng
    lúc);
  - thành công → 200 `TalentPoolConsentResponse` đọc lại từ DB; `talent_pool_consent_at` giữ nguyên (audit).
- **R-K8.** Rút đồng ý **không** đổi `status`, không ghi `application_status_history`, không tạo thông báo, không
  phát event, không đụng bản chụp, điểm, lượt chấm (B4, Q10).
- **R-K9. Khi chưa có FR-H15** (Q10): chỉ đặt cờ + thời điểm. Không bảng kho, không event, không method rỗng, không
  chỗ trống chờ H15. FR-H15 sẽ tự thêm việc xoá mục khỏi kho vào **chính** transaction rút đồng ý (ghi nợ ROADMAP,
  mục 10 D2).

### 3.6 Xem lại phía ứng viên (R-D)

- **R-D1.** E1 (`GET /api/candidates/applications/{id}`, FR-U08) thêm **một** khoá gốc `talentPoolConsent`
  `{state, consentedAt, revokedAt}` (R-K5). Ngoại lệ đã duyệt với FR-U08 (R-C2).
- **R-D2.** Endpoint F3 trả bản chụp câu trả lời **của chính ứng viên**, cùng DTO `ApplicationScreeningResponse`
  và cùng cách tính `state` với S6 của H10 (`NOT_CAPTURED`/`NO_QUESTIONS`/`CAPTURED`, R-A3 của H10). Dữ liệu chỉ
  từ cột `*_snapshot` (R-S3 của H10). Khối "Câu trả lời sàng lọc" chỉ hiện khi `state = CAPTURED`; **ẩn** khối ở
  **cả** `NO_QUESTIONS` và `NOT_CAPTURED` (Q20). Lý do: `NOT_CAPTURED` chỉ nghĩa là đơn nộp trước FR-U09, không cho
  biết tin lúc đó có câu hỏi hay không — mọi câu thông báo đều gây hiểu lầm cho phần lớn đơn cũ (gồm toàn bộ đơn
  demo); `NO_QUESTIONS` không có gì để xem. Đây là **ngoại lệ có chủ đích** với UI_GUIDE mục 4 ("Dữ liệu thiếu: hiện
  nhãn, không ẩn mục đó"), cùng tiền lệ R-T5 của FR-U08 (ẩn khối giấy mời khi 404).
- **R-D3.** Không trạng thái đơn hay trạng thái tin nào khoá F3, E1, F4 (đơn `WITHDRAWN`, tin đã xoá mềm vẫn xem
  được và vẫn rút đồng ý được — như các phần khác của FR-U08).

### 3.7 Cách ly với AI, điểm, xếp hạng (R-N)

- **R-N1.** Câu trả lời sàng lọc và cờ đồng ý **không** đi vào bất kỳ ngữ cảnh AI nào (FR-H04, FR-H06, FR-C07/K1).
  `ai/`, `scoring/`, `aicontext/`, `ai/prompt/` không tham chiếu `screening/`, `talent_pool`, `TalentPool*`,
  `talentPool*` (kiểm tĩnh mục 7.1). Test T13 của H10 (`ScreeningAiIsolationIntegrationTest`) giữ nguyên và xanh.
- **R-N2.** Cờ đồng ý (`ACTIVE`/`NOT_GIVEN`/`REVOKED`) và câu trả lời không đổi `totalScore`, `rank`, `status`
  (B1, T14). Không thêm sắp xếp/bộ lọc theo cờ hay câu trả lời ở bất kỳ danh sách nào.
- **R-N3.** Giao diện không tô màu, không icon ✓/✗, không nhãn theo câu trả lời hay theo cờ đồng ý (R-N4 của H10).
- **R-N4.** Thông báo `APPLICATION_SUBMITTED` cho HR giữ nguyên tiêu đề/nội dung/link
  (`NotificationEventListener.java:69-80`) — không chèn câu trả lời hay cờ đồng ý.

### 3.8 Quyền truy cập (R-A)

- **R-A1.** F1–F4 nằm dưới `/api/candidates/**` (filter chain chỉ cho role CANDIDATE, `SecurityConfig.java:55`).
  HR → 403; không token → 401 `UNAUTHENTICATED`.
- **R-A2.** F3, F4 nạp đơn bằng `JobApplicationRepository.findByIdAndCandidateId` (`candidateId` lấy từ JWT) →
  không tồn tại / của người khác → **cùng** 404 `APPLICATION_NOT_FOUND` (R-Q2 của FR-U08, R-A5 của FR-H10). Không
  dùng `HrApplicationAccess`.
- **R-A3.** F1 không cần quyền sở hữu (bộ câu hỏi của tin công khai), nhưng chỉ cho role CANDIDATE (form nộp đơn đã
  bắt đăng nhập).
- **R-A4.** Không thêm hay đổi endpoint nào dưới `/api/hr/**`. S6 của H10 giữ nguyên hành vi (Q13).

### 3.9 Thay đổi code có sẵn và ngoại lệ đã duyệt (R-C)

- **R-C1. 9 chỗ gọi `new ApplicationCreateRequest(...)`** (a18) sửa **cơ học**: thêm ba đối số cuối `null, null,
  null` (`screeningRevision`, `answers`, `talentPoolConsent`), không đổi gì khác trong test.
  `ApplicationServiceTest.java:96`, `:126`; `NotificationEventListenerIntegrationTest.java:117`, `:131`, `:151`,
  `:173`, `:187`, `:205`, `:226`.
- **R-C2. Test U08 khoá bộ khoá E1** — `ApplicationCandidateDetailControllerIntegrationTest.java:336`: thêm đúng
  khoá `"talentPoolConsent"` vào `containsExactlyInAnyOrder(...)`. **Không** bỏ khoá nào khỏi `FORBIDDEN_KEYS`
  (`:61-83`), không đổi khẳng định `job` (`:344-345`) và `resume` (`:384`). Comment của
  `ApplicationCandidateDetailResponse.java:10-12` ("KHONG them field nao") sửa thành ghi rõ ngoại lệ FR-U09.
- **R-C3. `ApplicationServiceTest.newService()`** (`:60-70`): thêm một `@Mock ScreeningCaptureService` và truyền vào
  constructor; thân các test giữ nguyên. `ApplicationService.apply` **không** dùng kết quả của mock theo cách gây
  `NullPointerException` (chỉ chuyển tiếp cho `persist`) — Plan Mode chốt chữ ký để giữ điều này.
- **R-C4. 25 file test gửi JSON cũ** (a19) **không sửa** và phải xanh (R-R1, R-K1).
- **R-C5. Tab "Sàng lọc" phía HR** (`ScreeningTab.tsx`): phần danh sách `<ol>` (`:43-49`, `:58-84`) chuyển sang
  component dùng chung `features/screening/ScreeningAnswerList.tsx` nhận chuỗi qua tham số. HTML, class, chữ của
  tab HR **không đổi** (soát tay 7.4). `ApplicationScreeningHrService` chuyển phần tính `state` sang
  `ApplicationScreeningReader` dùng chung; hành vi S6 không đổi, 11 test `ApplicationScreeningHrIntegrationTest`
  giữ nguyên và xanh.
- **R-C6. `JobApplyForm.tsx` sang React Hook Form + Zod** (Q14). Chọn CV, thư giới thiệu, ô AI consent, nút Nộp:
  giữ nguyên chữ, thứ tự, bố cục, quy tắc lọc CV `FAILED` và chọn CV chính mặc định (`:32-44`). Lỗi 400 dạng map
  trường → câu hiện câu đầu tiên của map (thay vì câu fallback).
- **R-C7.** Không sửa file trong `components/ui`. Nút chọn của câu `SINGLE_CHOICE`/`YES_NO` dùng
  `<input type="radio">` gốc trong `<fieldset>` (a21).

## 4. Dữ liệu & quyền truy cập

### 4.1 Migration `V14__talent_pool_consent.sql` (dự kiến, Q7)

```sql
-- FR-U09 - Dong y luu ho so cho dot tuyen sau, tinh theo TUNG don (REQUIREMENT.md muc 3.5).
--
-- Quy uoc CHECK (L7 cua FR-H10): Postgres coi CHECK ra NULL la DAT. Cot duoc NULL co chu dich thi CHECK xu ly NULL
-- tuong minh (IS NULL / IS NOT NULL); so sanh hai cot chi dat trong ve da khang dinh ca hai IS NOT NULL.
--
-- Ba trang thai hop le (R-K5):
--   NOT_GIVEN : consent FALSE, consent_at NULL,     revoked_at NULL      (moi don cu - DEFAULT FALSE, khong backfill)
--   ACTIVE    : consent TRUE,  consent_at NOT NULL, revoked_at NULL
--   REVOKED   : consent FALSE, consent_at NOT NULL, revoked_at NOT NULL, revoked_at >= consent_at
-- Chi duong rut dong y (UPDATE co dieu kien) duoc doi ACTIVE -> REVOKED; entity map ba cot updatable = false.
ALTER TABLE job_applications
    ADD COLUMN talent_pool_consent            BOOLEAN NOT NULL DEFAULT FALSE,
    -- NULL co chu dich: don chua tung dong y (NOT_GIVEN).
    ADD COLUMN talent_pool_consent_at         TIMESTAMPTZ,
    -- NULL co chu dich: chua rut dong y (NOT_GIVEN, ACTIVE).
    ADD COLUMN talent_pool_consent_revoked_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_talent_pool_consent_state CHECK (
        (NOT talent_pool_consent
             AND talent_pool_consent_at IS NULL
             AND talent_pool_consent_revoked_at IS NULL)
        OR (talent_pool_consent
             AND talent_pool_consent_at IS NOT NULL
             AND talent_pool_consent_revoked_at IS NULL)
        OR (NOT talent_pool_consent
             AND talent_pool_consent_at IS NOT NULL
             AND talent_pool_consent_revoked_at IS NOT NULL
             AND talent_pool_consent_revoked_at >= talent_pool_consent_at));
```

Không index mới (không truy vấn nào lọc theo cờ ở FR-U09; FR-H15 tự thêm nếu cần). Không sửa V1–V13.

**Soát CHECK theo logic ba trị.** `talent_pool_consent` `NOT NULL` → `NOT c` và `c` không bao giờ `NULL`. Hai cột
thời gian chỉ xét bằng `IS NULL`/`IS NOT NULL` (không ra `NULL`), trừ phép so `revoked_at >= consent_at` ở vế 3:

| Dòng | Vế 1 | Vế 2 | Vế 3 | Kết quả |
|---|---|---|---|---|
| NOT_GIVEN (F, NULL, NULL) | TRUE | FALSE | `TRUE AND FALSE AND … AND NULL` = FALSE | TRUE — đạt |
| ACTIVE (T, x, NULL) | FALSE | TRUE | FALSE | TRUE — đạt |
| REVOKED (F, x, y≥x) | FALSE | FALSE | TRUE | TRUE — đạt |
| T, NULL, NULL | FALSE | FALSE | FALSE | FALSE — chặn |
| T, x, y | FALSE | FALSE | FALSE | FALSE — chặn |
| F, x, NULL | FALSE | FALSE | `… AND FALSE AND NULL` = FALSE | FALSE — chặn |
| F, NULL, y | FALSE | FALSE | `… AND FALSE AND …` = FALSE | FALSE — chặn |
| F, x, y<x | FALSE | FALSE | FALSE | FALSE — chặn |

Phép so `revoked_at >= consent_at` chỉ có thể ra `NULL` khi một cột `NULL`, nhưng khi đó vế 3 đã có một hạng tử
`IS NOT NULL` = FALSE nên `AND` ra FALSE (không phải `NULL`). Kiểm bằng T15.

### 4.2 Entity

`JobApplication` thêm 4 field (Q6, R-K6):

```java
// FR-U09 R-S2 - cung Instant voi aiConsentAt; updatable = false: save() cua rut don khong ghi de.
@Column(name = "screening_captured_at", updatable = false)
private Instant screeningCapturedAt;

// FR-U09 R-K6 - ba cot dong y CHI ghi luc INSERT; rut dong y dung UPDATE co dieu kien rieng.
@Column(name = "talent_pool_consent", nullable = false, updatable = false)
private boolean talentPoolConsent;

@Column(name = "talent_pool_consent_at", updatable = false)
private Instant talentPoolConsentAt;

@Column(name = "talent_pool_consent_revoked_at", updatable = false)
private Instant talentPoolConsentRevokedAt;
```

`ApplicationScreeningAnswer` (Q5): thêm `@UuidGenerator` cho `id` và factory tĩnh
`static ApplicationScreeningAnswer capture(UUID applicationId, ScreeningQuestion question, String answerText,
Boolean answerYesNo)`; giữ `@Immutable`, không setter, constructor `protected`. `ApplicationScreeningAnswerRepository`
đọc `screening_captured_at` bằng native query như hiện tại (không đổi).

### 4.3 Package và file

Không package backend mới. `screening/` thêm phần ứng viên; `jobapplication/` → `screening/` được phép (bảng R-B
của H10).

| Package | Thêm | Sửa |
|---|---|---|
| `screening/` | `ScreeningCaptureService` (prepare/persist, R-R3, R-V, R-S; `Propagation.MANDATORY`; `prepare` trả `List<ScreeningAnswerDraft>` — L4: mock Mockito mặc định trả list rỗng), record `ScreeningAnswerDraft`, `ScreeningCandidateService` + `ScreeningCandidateController` (F1, F3), `ApplicationScreeningReader` (tính `state` dùng chung S6/F3), `dto/ScreeningAnswerInput`, `dto/ScreeningFormResponse` | `ApplicationScreeningAnswer` (factory + `@UuidGenerator`), `ScreeningQuestionRepository` (thêm `findRevisionForShare`), `ApplicationScreeningHrService` (gọi `ApplicationScreeningReader`, R-C5) |
| `jobapplication/` | `TalentPoolConsentState`, `dto/TalentPoolConsentResponse` | `JobApplication`, `dto/ApplicationCreateRequest`, `dto/ApplicationCandidateDetailResponse`, `ApplicationService` (`apply`, `revokeTalentPoolConsent`, E1), `ApplicationCandidateController` (F4), `JobApplicationRepository` (`revokeTalentPoolConsentIfActive`) |
| `common/exception/` | `ScreeningAnswerViolation`, `InvalidScreeningAnswerException`, `ScreeningQuestionsChangedException`, `TalentPoolConsentNotActiveException` | `GlobalExceptionHandler` (3 handler) |
| `db/migration` | `V14__talent_pool_consent.sql` | — |

Frontend: mục 5 của `UI.md`. Phần gọi API ứng viên ở `features/candidateScreening/` — **không** ở
`features/screening/` (a15).

### 4.4 Endpoint

| # | Endpoint | Mô tả | Thành công | Lỗi riêng (ngoài 401/403 của R-A1) |
|---|---|---|---|---|
| F1 | `GET /api/candidates/jobs/{jobId}/screening-questions` (**mới**) | Bộ câu hỏi đang hoạt động + revision (R-F1–R-F3); `@Transactional(readOnly = true)` | 200 `ScreeningFormResponse` | 404 `JOB_NOT_FOUND`; 400 mặc định khi id không phải UUID |
| F2 | `POST /api/candidates/applications` (**sửa**) | Nộp đơn + bản chụp + cờ đồng ý (R-R, R-V, R-S, R-K1–R-K2) | 201 `ApplicationResponse` (không đổi) | 400 map trường (có sẵn); 404 `JOB_NOT_FOUND`, `RESUME_NOT_FOUND` (có sẵn); 409 `SCREENING_QUESTIONS_CHANGED`; 400 `INVALID_SCREENING_ANSWER`; 409 `APPLICATION_DUPLICATE` (có sẵn) |
| F3 | `GET /api/candidates/applications/{applicationId}/screening-answers` (**mới**) | Bản chụp của đơn của mình (R-D2) | 200 `ApplicationScreeningResponse` (DTO có sẵn của H10) | 404 `APPLICATION_NOT_FOUND` |
| F4 | `PATCH /api/candidates/applications/{applicationId}/talent-pool-consent/revoke` (**mới**) | Rút đồng ý (R-K7) | 200 `TalentPoolConsentResponse` | 404 `APPLICATION_NOT_FOUND`; 409 `TALENT_POOL_CONSENT_NOT_ACTIVE` |
| E1 | `GET /api/candidates/applications/{applicationId}` (**sửa**, FR-U08) | Thêm khoá `talentPoolConsent` (R-D1) | 200 `ApplicationCandidateDetailResponse` | Như FR-U08 |

`ApplicationResponse` của F2 **không** thêm field (trả về chỉ dùng `id` cho liên kết "Xem đơn vừa nộp").

### 4.5 DTO

```java
// F2 - ba field moi o CUOI; JSON cu (chi jobId/resumeId/aiConsent/coverLetter) van hop le (R-R1, R-K1).
public record ApplicationCreateRequest(
        @NotNull UUID jobId,
        @NotNull UUID resumeId,
        @AssertTrue(message = "Bạn phải đồng ý cho phép AI phân tích CV để ứng tuyển") boolean aiConsent,
        String coverLetter,
        Integer screeningRevision,             // null = client khong thay cau hoi nao (R-R1)
        List<ScreeningAnswerInput> answers,    // null -> List.of(); KHONG List.copyOf (phan tu null -> 400, R-V1)
        Boolean talentPoolConsent) {           // null -> false (R-K1)

    public ApplicationCreateRequest {
        answers = answers == null ? List.of() : answers;
    }
}

// screening/dto - KHONG @NotNull/@Size: kiem o service sau khi bo khoang trang (R-V).
public record ScreeningAnswerInput(UUID questionId, String answerText, Boolean answerYesNo) {}

// F1 - questions dung lai ScreeningQuestionResponse cua H10 (id, questionType, questionText, options, required,
// displayOrder) - khong field nao chi danh cho HR. KHONG co maxQuestions (khai niem phia HR).
public record ScreeningFormResponse(UUID jobId, int revision, List<ScreeningQuestionResponse> questions) {
    public ScreeningFormResponse {
        questions = questions == null ? List.of() : List.copyOf(questions);
    }
}

// F3 - dung lai NGUYEN ApplicationScreeningResponse cua H10 (applicationId, state, capturedAt, items[displayOrder,
// questionType, questionText, options, required, answerText, answerYesNo]).

// E1 - them DUNG mot khoa goc; cac khoa cu giu nguyen (R-C2).
public record ApplicationCandidateDetailResponse(
        UUID id, ApplicationStatus status, Instant appliedAt, Instant updatedAt, String coverLetter,
        JobInfo job, ResumeInfo resume,
        TalentPoolConsentResponse talentPoolConsent) { /* JobInfo, ResumeInfo, JobAvailability khong doi */ }

// E1 (long) va F4 (than response).
public record TalentPoolConsentResponse(
        TalentPoolConsentState state,          // NOT_GIVEN | ACTIVE | REVOKED (R-K5)
        Instant consentedAt,                   // null khi NOT_GIVEN
        Instant revokedAt) {}                  // khac null chi khi REVOKED
```

Không DTO, entity, cột nào tên `verdict`, `label`, `isQualified`, `passed`, `recommendation` (CLAUDE.md §7). Khoá
mới của E1 (`talentPoolConsent`, `state`, `consentedAt`, `revokedAt`) không trùng khoá cấm nào ở
`ApplicationCandidateDetailControllerIntegrationTest.java:61-83`.

### 4.6 Mã lỗi

| HTTP | `error` | `message` | Nơi | Mới? |
|---|---|---|---|---|
| 409 | `SCREENING_QUESTIONS_CHANGED` | "Nhà tuyển dụng vừa cập nhật câu hỏi sàng lọc. Vui lòng kiểm tra lại câu trả lời rồi nộp lại." | F2 (R-R1, R-R2) | Mới |
| 400 | `INVALID_SCREENING_ANSWER` | Một trong 6 câu ở R-V1 | F2 | Mới |
| 409 | `TALENT_POOL_CONSENT_NOT_ACTIVE` | "Đơn này hiện không có đồng ý lưu hồ sơ để rút." | F4 (R-K7) | Mới |
| 404 | `JOB_NOT_FOUND` | Có sẵn | F1, F2 | — |
| 404 | `RESUME_NOT_FOUND` | Có sẵn | F2 | — |
| 404 | `APPLICATION_NOT_FOUND` | Có sẵn | F3, F4, E1 | — |
| 409 | `APPLICATION_DUPLICATE` | Có sẵn | F2 | — |
| 400 | (map trường → câu) | Có sẵn (`@AssertTrue aiConsent`, `@NotNull`) | F2 | — |

Exception mới theo khuôn `InvalidScreeningQuestionException`: constructor chỉ nhận enum/không tham số, không nhận
`String` tự do (CLAUDE.md §4).

### 4.7 Seed kiểm thử tay (Q17) — `db/seed/seed-test.sql`

- Đơn A1 (`e8000000-0000-0000-0000-000000000001`, Quốc Huy → J1 CSKH): `talent_pool_consent = TRUE`,
  `talent_pool_consent_at = '2026-10-05 17:35:00+00'` (= `ai_consent_at` của A1), `revoked_at = NULL` → `ACTIVE`.
  Thêm hai cột vào danh sách cột của câu `INSERT INTO job_applications` (`seed-test.sql:359-392`); 8 đơn còn lại
  `FALSE`, `NULL` → `NOT_GIVEN`.
- Không thêm câu hỏi, đơn, bản chụp mới. Seed demo **không đổi** (mọi đơn demo `NOT_GIVEN` nhờ `DEFAULT FALSE`).
- `reset-test-data.sql`: không cần sửa (cột mới nằm trên `job_applications`, đã xoá ở `:97`; bản chụp của đơn mới
  nộp qua UI đã xoá ở `:93`).
- `db/seed/README.md`: ghi A1 là đơn `ACTIVE` dùng để soát rút đồng ý; ghi luồng nộp mới (7.4).

## 5. AI

Không dùng AI. Không gọi `ChatModel`/`EmbeddingModel`, không thuộc danh sách ngoại lệ K3, không dùng K1–K4. Câu
trả lời và cờ đồng ý không vào ngữ cảnh AI nào (R-N1) — T13 của H10 và kiểm tĩnh mục 7.1 chứng minh.

## 6. Ngoài phạm vi

- Xoá mục khỏi kho ứng viên khi rút đồng ý, bảng kho, kiểm "chỉ thêm đơn đã đồng ý" (FR-H15).
- Hiện cờ đồng ý cho HR ở bất kỳ màn hình nào (Q13; FR-H15 tự quyết).
- Đồng ý lại / đồng ý sau khi nộp (Q9). Cờ đồng ý ở cấp tài khoản.
- Sửa câu trả lời sau khi nộp; lưu nháp câu trả lời.
- Thông báo cho HR khi ứng viên rút đồng ý (Q10).
- Handler chuẩn hoá cho JSON sai kiểu (`HttpMessageNotReadableException`) (R-V6).
- Làm mới `['my-applications']` sau khi nộp đơn (hành vi hiện tại giữ nguyên).
- Sửa rút đơn (`ApplicationService.withdraw`) sang `UPDATE` có điều kiện (nợ có sẵn của FR-U06, a11) — FR-U09 chỉ
  chặn hậu quả lên cột đồng ý bằng `updatable = false` (R-K6).
- Câu hỏi nhiều lựa chọn, tệp đính kèm, ngày tháng, số (ngoài phạm vi của H10).

## 7. Xong khi

### 7.1 Test backend (Testcontainers)

Mỗi lớp test mới tự tạo toàn bộ dữ liệu (HR, công ty, tin, câu hỏi, ứng viên, CV, đơn) với email duy nhất
(tiền tố ≤ 27 ký tự), không đọc seed hay dữ liệu lớp khác để lại. Tầng test không gọi LLM thật.

| # | Test | Loại |
|---|---|---|
| T1 | F1: tin OPEN có 3 câu (đủ 3 loại) + 1 câu đã xoá mềm → 200, `revision` = giá trị cột, 3 câu đúng thứ tự, không câu đã xoá; tin 0 câu → `questions: []` + revision; tin `DRAFT`, `PAUSED`, `CLOSED`, quá hạn (hạn = `CURRENT_DATE` − 1 đọc từ DB), xoá mềm, không tồn tại → 404 `JOB_NOT_FOUND`; token HR → 403; không token → 401. JSON không có `maxQuestions`, `deletedAt` | dương + âm + biên |
| T2 | **Thứ tự đọc của F1 (R-F3):** test đơn vị Mockito `InOrder`: `findOpenJobById` → `findRevision` → đọc danh sách câu hỏi; đảo thứ tự thì test đỏ | âm |
| T3 | F2 thành công, tin có 3 câu (SQ bắt buộc SHORT_TEXT, YES_NO không bắt buộc, SINGLE_CHOICE không bắt buộc): 201; đúng 3 dòng bản chụp theo `display_order`, cột `*_snapshot` khớp câu gốc, `question_id` đúng; `answer_text` đã bỏ khoảng trắng hai đầu, giữ `\n` bên trong; SINGLE_CHOICE lưu chữ phương án; câu bỏ trống có dòng với hai cột trả lời `NULL`; `screening_captured_at = ai_consent_at`; S6 (HR) và F3 trả `CAPTURED` cùng `items` | dương |
| T4 | F2 tin 0 câu: (a) `screeningRevision` = revision → 201, `NO_QUESTIONS`, 0 dòng; (b) JSON kiểu cũ chỉ `jobId`/`resumeId`/`aiConsent` → 201, `NO_QUESTIONS`, `talent_pool_consent = FALSE` | dương |
| T5 | 409 `SCREENING_QUESTIONS_CHANGED`: `screeningRevision = null` khi tin có ≥ 1 câu; revision cũ (HR đã thêm/sửa/xoá/đổi thứ tự sau khi đọc F1 — mỗi thao tác một ca); revision lệch khi tin có 0 câu. Mỗi ca: **không** có dòng `job_applications`, `application_status_history`, bản chụp, thông báo mới | âm |
| T6 | **Biên và hình dạng (B2)** — mỗi ca 400 `INVALID_SCREENING_ANSWER` với **đúng** câu R-V1 (hoặc 201 với ca hợp lệ), không ca nào 500, ca 400 không tạo dòng nào: SHORT_TEXT 499, 500 ký tự → 201; 501 → 400 `ANSWER_TOO_LONG`; `" " + 500 ký tự + " "` → 201 và lưu đúng 500; 500 ký tự ngoài BMP (vd `"😀".repeat(500)`, `String.length() = 1000`) → 201; 501 ký tự ngoài BMP → 400; câu bắt buộc: không có phần tử, `answerText` `null`, `""`, `"   "`, YES_NO `answerYesNo` `null` → 400 `REQUIRED_MISSING`; SINGLE_CHOICE ngoài `options` → 400 `OPTION_NOT_FOUND`; khác hoa thường → 400; có khoảng trắng hai đầu → 201 và lưu chữ gốc của phương án; `questionId` ngẫu nhiên, của tin khác, của câu đã xoá mềm, `null`, phần tử `null` → 400 `UNKNOWN_QUESTION`; trùng `questionId` → 400 `DUPLICATE_QUESTION`; YES_NO có `answerText`, SHORT_TEXT có `answerYesNo`, SINGLE_CHOICE có `answerYesNo` → 400 `TYPE_MISMATCH` | biên + âm |
| T7 | Thứ tự lỗi (R-V3): tin không mở + câu trả lời sai → 404 `JOB_NOT_FOUND`; CV của người khác + câu trả lời sai → 404 `RESUME_NOT_FOUND`; revision cũ + câu trả lời sai → 409 `SCREENING_QUESTIONS_CHANGED`; đã nộp + câu trả lời đúng → 409 `APPLICATION_DUPLICATE` và số dòng bản chụp của đơn đầu không đổi; đã nộp + câu trả lời sai → 400 `INVALID_SCREENING_ANSWER`; câu vi phạm hai quy tắc → lỗi đứng trước trong R-V2 | âm |
| T8 | **Song song (B3)**, lớp **không** `@Transactional` toàn lớp (CLAUDE.md §3c), dữ liệu ngẫu nhiên riêng, chỉ khẳng định trên tin của chính test. Cách xác nhận "luồng kia đang bị khoá chặn": **không** chờ cố định theo thời gian; luồng test thăm dò `pg_stat_activity` tới khi phiên của luồng kia có `wait_event_type = 'Lock'`, với giới hạn thời gian chờ (quá hạn → test đỏ với thông báo nêu rõ "phiên … không bị khoá chặn"), rồi mới commit. (a) HR trước: `TransactionTemplate` của test gọi S3 (sửa nội dung + loại câu 1) và **giữ** transaction; luồng thứ hai gọi F2 với revision cũ → phiên của nó chờ khoá; commit HR → F2 trả 409. (b) Ứng viên trước: `TransactionTemplate` gọi `applicationService.apply` (revision đúng) và **giữ** transaction; luồng thứ hai gọi S3 → phiên của nó chờ khoá; commit → bản chụp của đơn bằng đúng bộ câu **trước** khi sửa, S3 hoàn tất sau đó, revision +1. Không ca nào tạo bản chụp lệch bộ câu hỏi đã kiểm | âm |
| T9 | Cờ đồng ý lúc nộp: `talentPoolConsent: true` → `TRUE`, `talent_pool_consent_at = ai_consent_at`, `revoked_at NULL`, E1 `ACTIVE`; `false` và vắng mặt → `FALSE`, `NULL`, `NULL`, E1 `NOT_GIVEN` | dương + âm |
| T10 | F4 (B4): đơn `ACTIVE` → 200 `REVOKED`, `revokedAt` khác null, `consentedAt` giữ nguyên; lặp lại cho đủ 5 trạng thái đơn (`PENDING`, `INTERVIEW_INVITED`, `HIRED`, `REJECTED`, `WITHDRAWN`) → mỗi ca `status` không đổi, `application_status_history` không thêm dòng, không thông báo mới; đọc toàn bộ hàng `job_applications` bằng `JdbcTemplate` trước/sau → mọi cột giống hệt trừ `talent_pool_consent`, `talent_pool_consent_revoked_at`, `updated_at` (Q19); bản chụp không đổi. Gọi lần hai → 409 `TALENT_POOL_CONSENT_NOT_ACTIVE`; đơn `NOT_GIVEN` → 409; đơn của người khác → 404 và hàng không đổi; UUID không tồn tại → 404; token HR → 403; không token → 401 | dương + âm |
| T11 | **R-K6:** đơn `ACTIVE` → F4 → rút đơn (`PATCH /withdraw`, `save` cả entity) → cờ vẫn `FALSE`, `revoked_at` giữ nguyên; HR đổi trạng thái sau khi rút đồng ý → cờ không đổi | âm |
| T12 | E1: khoá gốc đúng bộ cũ + `talentPoolConsent` (R-C2, sửa `:336`); `talentPoolConsent` có đúng `state`, `consentedAt`, `revokedAt`; ba trạng thái R-K5 trả đúng; T4 của FR-U08 (khoá cấm, duyệt đệ quy) vẫn xanh không sửa | dương |
| T13 | F3: đơn của mình `CAPTURED`/`NO_QUESTIONS`/`NOT_CAPTURED` (đơn tạo bằng repository, `screening_captured_at` `NULL`) đúng `state`; HR sửa/xoá câu gốc sau khi nộp → F3 trả y hệt (so JSON); đơn của người khác → 404 và UUID không tồn tại → 404 (cùng mã `error`); token HR → 403; không token → 401 | dương + âm |
| T14 | **Cách ly điểm/hạng/trạng thái (B1)**, khuôn `ScreeningRankingIsolationIntegrationTest`: ba đơn cùng tin có `criterion_scores` giống hệt, cờ `ACTIVE` / `NOT_GIVEN` / `REVOKED`, hai đơn có bản chụp khác nhau → `totalScore`, `rank`, `status` bằng nhau ở `GET /api/hr/jobs/{jobId}/applications` và E3 của FR-H09; rút đồng ý một đơn rồi gọi lại → JSON từng dòng y hệt | dương |
| T15 | CHECK V14 (insert thẳng): 3 trạng thái hợp lệ insert được; mỗi ca chặn ở bảng mục 4.1 (5 ca) → `DataIntegrityViolationException`; đơn cũ sau migration có `FALSE`, `NULL`, `NULL` | biên + âm |
| T16 | JSON của F1, F3, F4, E1 không chứa khoá `verdict`/`label`/`isQualified`/`passed`/`recommendation` (so nguyên tên khoá, duyệt đệ quy) | âm |
| T17 | Thông báo (R-N4): F2 có câu trả lời + đồng ý → đúng một thông báo `APPLICATION_SUBMITTED` cho HR, tiêu đề/nội dung/link giống hệt đơn không có câu trả lời; F4 → không thông báo mới | dương + âm |
| T18 | Hồi quy: 25 file test JSON cũ (R-C4), 7 lớp test của H10 (gồm T13 `ScreeningAiIsolationIntegrationTest`, T14 `ScreeningRankingIsolationIntegrationTest`), `ApplicationCandidateDetailControllerIntegrationTest`, `CandidateApplicationCrossEndpointAccessIntegrationTest` xanh — chỉ với các sửa cơ học R-C1, R-C2, R-C3 | hồi quy |

Kiểm tĩnh (chạy ở đợt cuối, kỳ vọng 0 dòng):

```bash
rg -n "com\.recruitment\.screening|screening_|Screening[A-Z]|talent_pool|TalentPool|talentPool" backend/src/main/java/com/recruitment/ai backend/src/main/java/com/recruitment/scoring backend/src/main/java/com/recruitment/aicontext backend/src/main/resources/ai/prompt
rg -n -i "screening|talentpool|talent_pool" backend/src/main/java/com/recruitment/jobapplication/ApplicationSortOption.java frontend/src/features/candidates frontend/src/features/dashboard
rg -n "\.save\(" backend/src/main/java/com/recruitment/jobapplication/ApplicationService.java
```

Lệnh thứ ba: chỉ được ra đúng dòng `save` có sẵn của `withdraw` (a11) — `revokeTalentPoolConsent` không có `save`.

### 7.2 Kiểm bằng HTTP — Windows PowerShell 5.1

Nạp seed demo + seed test (`db/seed/README.md` mục 8.1). Backend ở `localhost:8080`. Dán cả khối vào **một** cửa
sổ PowerShell.

```powershell
$base = 'http://localhost:8080'

function Get-Token([string]$Email, [string]$Password) {
    $body = @{ email = $Email; password = $Password } | ConvertTo-Json
    (Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' -Body $body).accessToken
}

# In "<ma HTTP> <phuong thuc> <duong dan> <than loi neu co>". $Json = $null cho GET/PATCH khong than.
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

$tokenUv  = Get-Token 'quochuyuv@gmail.com' '12345678'
$tokenLan = Get-Token 'le.thi.lan@test.local' '12345678'
$tokenHr  = Get-Token 'minhdathr@gmail.com' '12345678'

$qJ1    = '/api/candidates/jobs/e2000000-0000-0000-0000-000000000001/screening-questions'  # J1 CSKH, 3 cau
$qJ6    = '/api/candidates/jobs/e2000000-0000-0000-0000-000000000006/screening-questions'  # J6 DRAFT
$ansA1  = '/api/candidates/applications/e8000000-0000-0000-0000-000000000001/screening-answers'
$ansA9  = '/api/candidates/applications/e8000000-0000-0000-0000-000000000009/screening-answers'
$revA1  = '/api/candidates/applications/e8000000-0000-0000-0000-000000000001/talent-pool-consent/revoke'
$revA9  = '/api/candidates/applications/e8000000-0000-0000-0000-000000000009/talent-pool-consent/revoke'
$apply  = '/api/candidates/applications'
# Lan nop J2 (0 cau hoi, revision 0) bang CV R4 cua Lan - chi dung cho ca bi chan, khong tao don.
$staleRev  = '{"jobId":"e2000000-0000-0000-0000-000000000002","resumeId":"e7000000-0000-0000-0000-000000000004","aiConsent":true,"screeningRevision":999}'
$badAnswer = '{"jobId":"e2000000-0000-0000-0000-000000000002","resumeId":"e7000000-0000-0000-0000-000000000004","aiConsent":true,"screeningRevision":0,"answers":[{"questionId":"ee000000-0000-0000-0000-000000000001","answerText":"x"}]}'

'--- 1. Duong: ung vien doc cau hoi J1 (mong doi 200); Am: tin DRAFT (mong doi 404 JOB_NOT_FOUND)'
Test-Call 'GET' $qJ1 $tokenUv $null
Test-Call 'GET' $qJ6 $tokenUv $null

'--- 2. Am: HR goi endpoint ung vien (mong doi 403); khong token (mong doi 401)'
Test-Call 'GET' $qJ1 $tokenHr $null
Test-Call 'GET' $qJ1 $null $null

'--- 3. Duong: Quoc Huy doc ban chup A1 (mong doi 200 CAPTURED); Am: doc A9 cua nguoi khac (mong doi 404)'
Test-Call 'GET' $ansA1 $tokenUv $null
Test-Call 'GET' $ansA9 $tokenUv $null

'--- 4. Am: nop don voi revision cu (mong doi 409 SCREENING_QUESTIONS_CHANGED)'
Test-Call 'POST' $apply $tokenLan $staleRev

'--- 5. Am: cau tra loi cho cau hoi cua tin khac (mong doi 400 INVALID_SCREENING_ANSWER)'
Test-Call 'POST' $apply $tokenLan $badAnswer

'--- 6. Am: rut dong y don cua nguoi khac (mong doi 404)'
Test-Call 'PATCH' $revA9 $tokenUv $null

'--- 7. Duong: rut dong y A1 (mong doi 200); Am: rut lan hai (mong doi 409 TALENT_POOL_CONSENT_NOT_ACTIVE)'
Test-Call 'PATCH' $revA1 $tokenUv $null
Test-Call 'PATCH' $revA1 $tokenUv $null
```

Mong đợi: nhóm 1 `200`, `404 … JOB_NOT_FOUND`; nhóm 2 `403`, `401 … UNAUTHENTICATED`; nhóm 3 `200`, `404 …
APPLICATION_NOT_FOUND`; nhóm 4 `409 … SCREENING_QUESTIONS_CHANGED`; nhóm 5 `400 … INVALID_SCREENING_ANSWER`; nhóm 6
`404 … APPLICATION_NOT_FOUND`; nhóm 7 `200` rồi `409 … TALENT_POOL_CONSENT_NOT_ACTIVE`. Chạy xong:
`reset-test-data.sql` rồi nạp lại seed test (A1 trở lại `ACTIVE`). **Bắt buộc ở đợt cuối:** chạy thật, dán nguyên
văn output; lệnh sai cú pháp thì dừng, đề xuất sửa mục này, chờ duyệt lại.

### 7.3 Frontend

- `npm run build` + `npm run lint` sạch.
- `rg -n "dangerouslySetInnerHTML" frontend/src` → 0 dòng.
- `rg -n "api/candidates" frontend/src/features/screening` → 0 dòng (H10 7.3 giữ nguyên, a15).
- `rg -n "api/hr|/hr/|features/scoring|features/applicationDetail/(api|queries|types)|totalScore|rank|criterionScores|explanation" frontend/src/pages/CandidateApplicationDetailPage.tsx frontend/src/features/candidateApplicationDetail frontend/src/features/candidateScreening`
  → 0 dòng (lệnh của FR-U08 7.3, mở rộng sang thư mục mới).
- `rg -n "screening|talentPool" frontend/src/features/scoring frontend/src/features/candidates frontend/src/features/dashboard`
  → 0 dòng.
- `rg -n "useState" frontend/src/features/applications/JobApplyForm.tsx` → **0 dòng**; mọi trường form qua React
  Hook Form (R-C6). Nếu khi code thật sự cần state ngoài form, phải dừng, đề xuất thêm tên biến đích danh vào chính
  dòng này và chờ duyệt lại (danh sách biến được phép hiện tại: trống).
- Tên file theo `UI.md` mục 5.

### 7.4 Soát tay (desktop ≥ `lg`, 768px và 375px)

| Trạng thái cần thấy | Tài khoản | Nơi | Mong đợi |
|---|---|---|---|
| Form tin có 3 câu | Ứng viên demo do Plan Mode chọn (CV không `FAILED`), bổ sung tên vào bảng này trước đợt 4 / `Demo1234` | `/jobs/e2000000-0000-0000-0000-000000000001/apply` | Khối "Câu hỏi sàng lọc" đủ SQ1–SQ3 theo thứ tự, "Bắt buộc"/"Không bắt buộc" bằng chữ; lưu ý trung tính; ô đồng ý lưu hồ sơ không tick sẵn; nút Nộp chỉ khoá theo ô AI consent |
| Lỗi Zod | như trên | Bỏ trống SQ1, gõ 501 ký tự | Lỗi dưới đúng câu; bộ đếm "n/500 ký tự"; không gửi request |
| Nộp thành công + đồng ý | như trên | Trả lời SQ1, SQ2 "Có", bỏ SQ3, tick đồng ý → Nộp | Màn thành công có "Xem đơn vừa nộp" → trang chi tiết: khối câu trả lời đủ 3 câu (SQ3 "Bạn không trả lời câu này."), khối lưu hồ sơ "đang đồng ý" + nút "Rút đồng ý"; HR mở đơn này ở tab Sàng lọc thấy `CAPTURED` |
| 409 khi HR sửa câu hỏi | Lan `le.thi.lan@test.local` + HR `minhdathr@gmail.com` (hai trình duyệt) | HR thêm 2 câu cho J2 (`/hr/jobs/e2…02/edit?tab=screening`); Lan mở `/jobs/e2…02/apply`, trả lời; HR sửa nội dung một câu; Lan bấm Nộp | Câu 409 hiện; câu bị đổi loại/xoá mất câu trả lời, câu còn nguyên giữ câu trả lời; nộp lại thành công |
| Rút đồng ý | `quochuyuv@gmail.com` | `/candidate/applications/e8000000-0000-0000-0000-000000000001` | Khối "đang đồng ý" + "Rút đồng ý" → hộp xác nhận → khối thành "đã rút" kèm thời điểm, nút biến mất, badge trạng thái đơn không đổi |
| Đơn không đồng ý | `quochuyuv@gmail.com` | A2 `e8…02` | Khối lưu hồ sơ câu "không đồng ý", không nút; không khối câu trả lời (`NO_QUESTIONS`) |
| Đơn cũ chưa chụp | `quochuyuv@gmail.com` | A3 `e8…03` | Không khối câu trả lời (`NOT_CAPTURED`); khối lưu hồ sơ "không đồng ý" |
| Tab Sàng lọc phía HR không đổi | `minhdathr@gmail.com` | `/hr/applications/e8…01?tab=screening`, `e8…09`, `e8…02`, `e8…06` | Giống hệt soát tay 7.4 của H10 |
| 375px | cả hai | Form nộp đơn, trang chi tiết | Không cuộn ngang; nút chọn ≥ 48px; hộp xác nhận vừa màn hình |

Chạy `reset-test-data.sql` (và xoá đơn demo vừa nộp — script đã xoá đơn demo vào tin test, `reset-test-data.sql:72-74`)
sau khi soát.

## 8. AI hay làm sai (dành cho người code)

- **Bỏ `FOR SHARE` ở `apply`** vì "đã so revision rồi" — giữa lúc so và lúc ghi, HR commit được, bản chụp ghép lệch
  (R-R3, T8). Thay bằng `@Lock(PESSIMISTIC_READ)`/`PESSIMISTIC_WRITE` hay `SELECT … FOR UPDATE` (khoá mạnh hơn, chặn
  cả các `apply` song song).
- **Đảo thứ tự đọc ở F1** (đọc câu hỏi trước revision) — client nhận revision mới + câu hỏi cũ, `apply` chấp nhận
  câu trả lời lệch (R-F3, T2).
- Đọc câu hỏi **trước** khi giữ khoá ở `apply`, hoặc dùng lại danh sách câu hỏi client gửi lên thay vì đọc từ DB.
- **Gọi `findRevisionForShare` từ F1 (`readOnly`) hoặc đánh dấu `apply` là `readOnly`** — Postgres báo lỗi "cannot
  execute SELECT FOR SHARE in a read-only transaction" (đã thử trên DB dev ở Plan Mode, L2). F1 dùng `findRevision`.
- Coi `screeningRevision = null` là "bỏ qua kiểm" khi tin có câu hỏi (R-R1).
- Để vi phạm câu trả lời rơi xuống CHECK của V13 (ra 500) thay vì 400 `INVALID_SCREENING_ANSWER` (R-V); đặt
  `@Size`/`@NotBlank` lên `answerText` (kiểm trước khi bỏ khoảng trắng); `List.copyOf(answers)` (NPE với phần tử
  `null`).
- Đếm độ dài bằng `String.length()` (UTF-16) thay vì code point; đếm trước khi bỏ khoảng trắng.
- So phương án không phân biệt hoa thường rồi lưu chữ client gửi; lưu chỉ số phương án (R-V1, R-V4).
- Không ghi dòng cho câu không bắt buộc bỏ trống; không đặt `screening_captured_at` khi tin có 0 câu (R-V5, R-S2).
- Ghi bản chụp ngoài transaction của `apply` (vd sau commit, trong listener) — đơn có thể tồn tại không có bản
  chụp (R-S1).
- Hiển thị câu trả lời bằng cách join sang `job_screening_questions` hiện tại thay vì cột `*_snapshot`.
- **Rút đồng ý bằng cách đọc entity rồi `save`**; map ba cột đồng ý `updatable = true` — rút đơn sau đó ghi đè cờ
  vừa rút (R-K6, R-K7, T11).
- Đổi `status`, ghi lịch sử, tạo thông báo, phát event, hay tạo bảng/method "chờ FR-H15" khi rút đồng ý (R-K8, R-K9).
- Thêm nút/endpoint "Đồng ý lại" (R-K4).
- Trả 403 cho đơn của người khác ở F3/F4, hoặc dùng `HrApplicationAccess` (R-A2).
- Đưa câu trả lời hay cờ đồng ý vào prompt, K1, `CriterionScoringService`, hay dùng để sắp xếp/lọc/tô màu (R-N).
- Thêm khoá vào E1 ngoài `talentPoolConsent`, hoặc bỏ khoá nào khỏi `FORBIDDEN_KEYS` để test xanh (R-C2).
- Sửa 25 file test JSON cũ "cho đủ field mới" (R-C4); sửa 9 chỗ gọi constructor nhiều hơn thêm `null, null, null`
  (R-C1).
- Đặt hàm gọi `/api/candidates/...` trong `features/screening/` (đỏ kiểm tĩnh H10 7.3).
- Đổi giao diện tab "Sàng lọc" phía HR khi tách component dùng chung (R-C5).
- Thêm RadioGroup vào `components/ui` (R-C7); tô màu "Có"/"Không"; dấu `*` đỏ cho câu bắt buộc.
- Để ô đồng ý lưu hồ sơ tick sẵn hoặc chặn nút Nộp; gộp nó với ô AI consent (R-K1).
- Comment không dấu chứa chữ "tuoi" (dương tính giả `srs-guard` nguyên tắc 13) — viết "nhan than".

## 9. Kế hoạch đợt code

Mỗi đợt: code xong toàn bộ đợt → chạy kiểm ở cột Kiểm → dừng → báo cáo diff → chờ duyệt → **một** commit (B6).
Giữa các đợt backend chỉ `.\mvnw.cmd test-compile` và **riêng** các lớp test liên quan bằng `-Dtest`; full suite
`.\mvnw.cmd test` chạy **đúng một lần** ở đợt cuối. Frontend: `npm run build` + `npm run lint` sau mỗi đợt có sửa
frontend.

| Đợt | Nội dung | Kiểm |
|---|---|---|
| 1 | **Plan Mode:** đối chiếu lại mục 0 trên HEAD lúc đó; chứng minh Hibernate 7 persist được entity `@Immutable` (đọc source/javap của `hibernate-core` trong `~/.m2`, trích bằng chứng) hoặc chuyển R-S3 sang native INSERT (O2); xác nhận native `@Query … FOR SHARE` chạy được với Spring Data JPA; chọn ứng viên demo có CV không `FAILED` cho 7.4; chốt chữ ký `ScreeningCaptureService` (R-C3); chốt danh sách file | — |
| 2 | Backend nộp đơn: V14; field entity (R-K6); F1; `ScreeningCaptureService` (khoá, revision, kiểm, ghi bản chụp); cờ đồng ý lúc nộp; 3 exception + handler; sửa cơ học R-C1, R-C3; T1–T9, T15, T16 (F1), T17 (phần F2) | test-compile; `-Dtest` lớp mới + `ApplicationServiceTest`, `NotificationEventListenerIntegrationTest`, `ApplicationIntegrationTest`, các lớp `Screening*`, `ApplicationScreeningHrIntegrationTest` |
| 3 | Backend xem lại và rút: `ApplicationScreeningReader` (R-C5), F3, F4, E1 + sửa `:336` (R-C2); T10–T14, T16 (F3, F4, E1), T17 (phần F4); seed A1 + `db/seed/README.md` | test-compile; `-Dtest` lớp mới + `ApplicationCandidateDetailControllerIntegrationTest`, `CandidateApplicationCrossEndpointAccessIntegrationTest`, `ApplicationScreeningHrIntegrationTest`, `ScreeningRankingIsolationIntegrationTest` |
| 4 | Frontend form nộp đơn: `features/candidateScreening` (api, queries, types, schema, khối câu hỏi); `JobApplyForm` sang RHF + Zod (R-C6); xử lý 409 (R-F5); ô đồng ý; liên kết "Xem đơn vừa nộp" | build + lint |
| 5 | Frontend trang chi tiết: `ScreeningAnswerList` dùng chung + `ScreeningTab` HR dùng lại (R-C5); `ScreeningAnswersSection`, `TalentPoolConsentSection`, hộp xác nhận, mutation rút đồng ý | build + lint; lệnh 7.3 |
| 6 | Đợt cuối: full `.\mvnw.cmd test` (một lần), khối 7.2 (dán output), kiểm tĩnh 7.1, `srs-guard`, `walkthrough`, trạng thái `ĐÃ HOÀN THÀNH`, tài liệu mục 10 | full suite; build + lint; 7.2; 7.3 |

## 10. Tài liệu dùng chung sẽ sửa

D1–D5 sửa **cùng commit** đổi trạng thái hai file đặc tả sang `ĐÃ DUYỆT` (sau khi người dùng duyệt); D6–D8 sửa ở
đợt cuối. Số dòng tính tại `497f5cf`; khi sửa tìm theo câu cũ.

| # | File:dòng | Câu cũ (nguyên văn) | Câu mới | Khi nào |
|---|---|---|---|---|
| D1 | `docs/features/README.md:475` | `Ở form nộp đơn (FR-U02) → trả lời các câu hỏi sàng lọc của Job (H10) → tuỳ chọn tick "Cho phép công ty lưu hồ sơ của tôi để liên hệ cho các vị trí sau này" → Nộp đơn.` | `Ở form nộp đơn (FR-U02) → trả lời các câu hỏi sàng lọc của Job (H10) → tuỳ chọn tick "Cho phép công ty lưu hồ sơ của tôi để liên hệ cho các vị trí sau này" → Nộp đơn. Sau khi nộp: xem lại câu trả lời và trạng thái đồng ý, rút đồng ý ở trang chi tiết đơn (FR-U08).` | Duyệt |
| D2 | `docs/features/README.md:479` | `- Chặn nộp đơn khi thiếu câu bắt buộc, kiểm tra ở backend chứ không chỉ ở frontend.` | `- Chặn nộp đơn khi thiếu câu bắt buộc, kiểm tra ở backend chứ không chỉ ở frontend. Bộ câu hỏi đổi giữa lúc ứng viên đang điền → từ chối (409), ứng viên kiểm lại rồi nộp lại.` | Duyệt |
| D3 | `docs/UI_GUIDE.md:241-243` (mục 4) | `**Ô consent khi ứng tuyển (FR-U02)**` / `- Checkbox không tick sẵn. Nút "Nộp đơn" disabled cho tới khi tick.` / `- Nội dung ghi rõ CV sẽ được AI phân tích và chấm điểm để hỗ trợ HR đánh giá.` | Giữ ba dòng cũ, thêm sau: `**Ô đồng ý lưu hồ sơ (FR-U09)**` / `- Checkbox riêng, không tick sẵn, KHÔNG chặn nút "Nộp đơn"; ghi rõ có thể rút sau ở trang chi tiết đơn. Không gộp với ô consent AI.` | Duyệt |
| D4 | `docs/UI_GUIDE.md:341` | `\| ★/candidate/applications/:id \| Chi tiết đơn ứng tuyển — tab "Thông tin đơn" (giấy mời, tóm tắt tin, CV đã nộp, thư giới thiệu, CV đã trích xuất), "Lịch sử", "Trao đổi"; nút Rút đơn. Tab/nút của FR sau do chính FR đó thêm \| ★Mới \| FR-U08; FR-U03, FR-U06; FR-U10 (chọn giờ); FR-U09 (câu trả lời, rút đồng ý); FR-C06 (trao đổi); FR-C07 (soạn bằng AI ở tab Trao đổi) \|` (route trong dấu backtick) | `\| ★/candidate/applications/:id \| Chi tiết đơn ứng tuyển — tab "Thông tin đơn" (giấy mời, tóm tắt tin, CV đã nộp, thư giới thiệu, câu trả lời sàng lọc, lưu hồ sơ cho đợt tuyển sau kèm nút Rút đồng ý, CV đã trích xuất), "Lịch sử", "Trao đổi"; nút Rút đơn. Tab/nút của FR sau do chính FR đó thêm \| ★Mới \| FR-U08; FR-U03, FR-U06; FR-U10 (chọn giờ); FR-U09 (câu trả lời, rút đồng ý); FR-C06 (trao đổi); FR-C07 (soạn bằng AI ở tab Trao đổi) \|` | Duyệt |
| D5 | `docs/UI_GUIDE.md:344` | `\| /jobs/:id/apply \| Nộp đơn ứng tuyển \| Hiện có \| FR-U02, FR-U09 \|` (route trong dấu backtick) | `\| /jobs/:id/apply \| Nộp đơn ứng tuyển — câu hỏi sàng lọc của tin, ô đồng ý lưu hồ sơ; màn thành công có liên kết "Xem đơn vừa nộp" \| Hiện có \| FR-U02, FR-U09 \|` | Duyệt |
| D6 | `docs/SRS.md:53` | `\| FR-U09 \| Trả lời sàng lọc và đồng ý lưu hồ sơ \| Ứng viên \| Chưa đặc tả \| [features/UV/U09/REQUIREMENT.md](features/UV/U09/REQUIREMENT.md) \|` | Cột Trạng thái `Chưa đặc tả` → `Đã hoàn thành` | Đợt cuối |
| D7 | `docs/ROADMAP.md:870` | `- [ ] \`feat/fr-u09-screening-answer\` — FR-U09 · Trả lời sàng lọc và đồng ý lưu hồ sơ` | `- [x] … — **HOÀN THÀNH** (ngày, số đợt, walkthrough)` + tóm tắt + nợ kỹ thuật: (1) FR-H15 phải xoá mục khỏi mọi kho **trong chính transaction** rút đồng ý (`ApplicationService.revokeTalentPoolConsent`) và chặn thêm đơn không `ACTIVE`; (2) `ApplicationService.withdraw` vẫn đọc-rồi-`save` (lost update với HR), FR-U09 chỉ chặn hậu quả lên cột đồng ý bằng `updatable = false`; (3) nợ của H10 "đơn mới hiện `NOT_CAPTURED` tới khi FR-U09 xong" đã đóng | Đợt cuối |
| D8 | `docs/ROADMAP.md:864-865` (nợ của H10) | `  - Đường ghi bản chụp thuộc FR-U09: đơn nộp mới sau khi triển khai FR-H10 chưa có bản chụp nên hiện` / `    \`NOT_CAPTURED\` cho tới khi FR-U09 xong; bản chụp hiện có chỉ do seed test và test tạo.` | Thêm cuối câu: ` — **Đã đóng ở FR-U09.**` | Đợt cuối |

Không sửa: `CLAUDE.md` (không package backend mới; `screening/` đã có ở §3 dòng 69), `docs/SRS.md:128` (dòng FR-U09
mục 2 đã đúng sau D2 của FR-U08), các file đặc tả của FR-H10/FR-U08 (đã hoàn thành; lệch R-C2, R-C5 ghi ở đặc tả và
walkthrough của FR-U09).

## 11. Đã chốt 11/10/2026

| # | Câu hỏi | Đã chọn |
|---|---|---|
| Q1 | Endpoint lấy câu hỏi | `GET /api/candidates/jobs/{jobId}/screening-questions`, `findOpenJobById`, trả revision + câu hỏi (R-F1) |
| Q2 | Request và revision vắng | `screeningRevision` được `null` + `answers` (`null` → rỗng); `null` chỉ nhận khi tin có 0 câu, ngược lại 409; 9 chỗ gọi sửa cơ học; 25 file JSON cũ giữ nguyên (R-R1, R-C1, R-C4) |
| Q3 | Mã 409 và xử lý frontend | `SCREENING_QUESTIONS_CHANGED`; tải lại, giữ câu cùng id + cùng loại (SINGLE_CHOICE thêm điều kiện phương án còn) (R-F5) |
| Q4 | Nhất quán khi HR sửa song song | `FOR SHARE` hàng `jobs` ở `apply`; F1 đọc revision trước câu hỏi, có test (R-R3, R-F3) |
| Q5 | Ghi bản chụp | Factory + `@UuidGenerator`, vẫn `@Immutable`; Plan Mode chứng minh không được thì native INSERT, báo lại (R-S3) |
| Q6 | `screening_captured_at` | Map vào entity `updatable = false`, cùng `Instant` với `aiConsentAt` (R-S2) |
| Q7 | Cột đồng ý | V14: `talent_pool_consent`, `_at`, `_revoked_at` + CHECK (mục 4.1) |
| Q8 | Endpoint rút | `PATCH …/talent-pool-consent/revoke`, 404, mọi trạng thái, `UPDATE` có điều kiện, 409 `TALENT_POOL_CONSENT_NOT_ACTIVE` (R-K7) |
| Q9 | Đồng ý về sau | Không; chỉ lúc nộp; rút một chiều (R-K4) |
| Q10 | Rút khi chưa có H15 | Chỉ cờ + thời điểm; không event, không chỗ trống, không báo HR; nợ ROADMAP (R-K8, R-K9) |
| Q11 | Xem lại | F3 cho câu trả lời; `talentPoolConsent` lồng trong E1; sửa `:336` là ngoại lệ đã duyệt (R-D1, R-C2) |
| Q12 | Vị trí | Hai khối trong tab "Thông tin đơn" sau "Hồ sơ đã nộp"; không thêm tab |
| Q13 | Phía HR | Không thêm gì (R-A4) |
| Q14 | Form | React Hook Form + Zod; phần cũ giữ chữ, bố cục; sửa hiện lỗi 400 dạng map (R-C6) |
| Q15 | Lỗi câu trả lời | 400 `INVALID_SCREENING_ANSWER`, bảng câu cố định, thứ tự kiểm R-V2/R-V3; không để rơi xuống CHECK |
| Q16 | Chữ trên form | Lưu ý trung tính; ô đồng ý không tick sẵn, không chặn Nộp, nói rõ rút được sau; component danh sách dùng chung, tab HR không đổi |
| Q17 | Seed | A1 `ACTIVE` ở seed test; seed demo giữ `FALSE`; luồng nộp mới soát bằng ứng viên demo → J1 và Lan → J2 (mục 4.7, 7.4) |
| Q18 | Màn thành công | Thêm liên kết "Xem đơn vừa nộp" |
| B1–B7 | Bổ sung bắt buộc | T14 + kiểm tĩnh (B1); T6 (B2); T8 (B3); R-K7, T10 (B4); UI.md mục 5d, 10 (B5); mục 9 (B6); mục 10 (B7) |
| Q19 | Rút đồng ý và trigger `updated_at` (B4) | Câu `UPDATE` chỉ `SET` 2 cột (`talent_pool_consent`, `talent_pool_consent_revoked_at`); `updated_at` đổi do trigger `set_updated_at` (V1) — chấp nhận, không màn hình nào hiển thị cột này (a13); T10 loại trừ `updated_at` khi so (M3) |
| Q20 | Khối câu trả lời ở `NO_QUESTIONS`/`NOT_CAPTURED` | Ẩn khối ở cả hai trạng thái; ngoại lệ có chủ đích với UI_GUIDE mục 4, tiền lệ R-T5 của FR-U08 (R-D2) |
| — | Soát spec-review 11/10/2026 | Sửa tên kiểu ở UI.md 5a theo `features/screening/types.ts`; T8 thăm dò `pg_stat_activity` thay vì chờ cố định; 7.3 `useState` kỳ vọng 0 dòng; 7.4 ghi ứng viên demo do Plan Mode chọn; R-R3 thêm hệ quả khoá hàng `jobs` và kết quả soát deadlock |

## 12. Điểm còn mở

- **O2 (R-S3) — đã đóng ở Plan Mode 11/10/2026 (L1).** Hibernate 7.4.1.Final (`<hibernate.version>` của
  `spring-boot-dependencies-4.1.0.pom`) persist được entity `@Immutable`: `javap` cho thấy `EntityBinder.isMutable()` đọc
  `@Immutable` → `RootClass.setMutable(false)`; `EntityPersister.isMutable()` chỉ được gọi ở
  `DefaultFlushEntityEventListener.onFlushEntity` (dirty-check/UPDATE) và `logScheduleUpdate`; `DefaultPersistEventListener`,
  `AbstractSaveEventListener`, `EntityInsertAction` không kiểm mutability. Giữ R-S3 (factory + `@UuidGenerator`); T3 chứng
  minh bằng chạy thật.
