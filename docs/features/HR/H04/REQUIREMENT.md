# FR-H04 (mở rộng) — Chấm tất cả CV trong tin

> Trạng thái: ĐÃ DUYỆT (30/10/2026). Mọi quyết định ở mục 11 đã chốt 30/10/2026 (Q1–Q13; Q13 đổi sang đếm theo lý do).
>
> Viết theo kế hoạch `docs/plans/2026-10-28-tang-toc-cham-diem.md` (mục "Nút chấm tất cả CV trong tin") và mục "Sửa
> đổi kế hoạch tăng tốc chấm điểm (28/10)" điểm 5 của `docs/plans/2026-11-dot-bo-sung.md` (điểm 5 **thắng** kế hoạch
> 28/10: "không có lượt `DONE` nào", frontend dùng `totalScore === null` và lọc theo chu kỳ hiện tại).

- Nhóm: HR
- Tóm tắt: Nút "Chấm tất cả (N)" ở tab Ứng viên của tin: một request tạo một lượt chấm `PENDING` cho mỗi đơn đủ điều
  kiện của chu kỳ hiện tại. Request **chỉ tạo bản ghi, không gọi AI**; AI chạy qua job nền như nút đơn lẻ. Phần mở rộng
  của FR-H04, **không phải mã mới**. Không migration. Code trong package `scoring/`.
- Phụ thuộc: FR-H03, FR-H04 (cả hai **Đã hoàn thành**, `docs/SRS.md` mục 0); làm sau khi nhánh `perf/scoring-latency`
  đã merge (`docs/features/README.md` mục 22). **Rủi ro:** tại `0d6dbef`, `main` **chưa** có `perf/scoring-latency`
  (chỉ có trên `origin/perf/scoring-latency`) — không bắt đầu đợt code trước khi perf merge và nhánh này rebase (mục 9).
- Nhánh: `feat/fr-h04-bulk-scoring` (tạo từ `main` tại `0d6dbef`, **chưa** có `perf/scoring-latency`).
- Mở rộng: FR-H04 (tạo lượt chấm hàng loạt). FR-H05, FR-H06, FR-H07 **không đổi**.

## 0. Đã đối chiếu code trước khi viết (CLAUDE.md §6)

Đối chiếu trên `feat/fr-h04-bulk-scoring` tại `0d6dbef` (30/10/2026, cây làm việc sạch). Kết quả nhánh perf đọc từ
`git show origin/perf/scoring-latency:docs/walkthrough/perf-scoring-latency.md` (nhánh đã có trên origin).

### (a) Tạo lượt chấm đơn lẻ — có sẵn gì để dùng lại

| # | Điểm kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| a1 | Thứ tự kiểm của nút đơn lẻ | `loadOwnedApplication` → `requireResumeParsed` → `requireRubricComplete` → `requireNoRunInProgress` → chụp snapshot → `stateService.create` | `ScoringRunService.java:77-95` |
| a2 | Các hàm kiểm là `private`, kiểm **một** đơn, báo bằng **exception** | Đúng. Gọi lặp cho N đơn = N × 3–4 câu SELECT và dùng exception làm luồng điều khiển | `ScoringRunService.java:122-178` |
| a3 | `ScoringRunService` không `@Transactional` | Đúng — chỉ đọc, giao câu ghi duy nhất cho `ScoringRunStateService.create` | `ScoringRunService.java:34-38` |
| a4 | `create` mở transaction riêng, `saveAndFlush`, khoá rubric cùng transaction (idempotent) | Đúng | `ScoringRunStateService.java:66-88` |
| a5 | "Đang chạy" của nút đơn lẻ | `PENDING`, hoặc `RUNNING` có `finished_at IS NULL`. `RUNNING` + `finished_at` khác `NULL` (chờ D3) **không** chặn | `ScoringRunService.java:169-178`; `ScoringRunRepository.java:58-62` |
| a6 | Chốt chặn DB | `uq_scoring_run_in_progress ON scoring_runs(application_id) WHERE status IN ('PENDING','RUNNING') AND finished_at IS NULL` | `V4__scoring_run_in_progress_unique.sql:19-21` |
| a7 | Dịch vi phạm index | `GlobalExceptionHandler` so chuỗi `uq_scoring_run_in_progress` → 409 `SCORING_RUN_IN_PROGRESS`, câu lấy từ `ScoringRunInProgressException` | `GlobalExceptionHandler.java:616-623` |
| a8 | Rubric thiếu | `RubricIncompleteException.forScoring(total)` → 409 `RUBRIC_INCOMPLETE`; rubric không có tiêu chí → tổng 0 → cùng nhánh. Không có rubric → 404 `RUBRIC_NOT_FOUND` | `ScoringRunService.java:160-167`; `RubricIncompleteException.forScoring`; `GlobalExceptionHandler.java:89-92, 171-175` |
| a9 | Nút đơn lẻ **không** kiểm trạng thái đơn (`PENDING`…), **không** kiểm chu kỳ, **không** kiểm trạng thái tin (`OPEN`/`PAUSED`/`CLOSED`), **không** kiểm `jobs.deleted_at` | Đúng — `loadOwnedApplication` chỉ kiểm tồn tại + công ty | `ScoringRunService.java:122-134` |

### (b) Danh sách tab Ứng viên — nguồn của N phía frontend

| # | Điểm kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| b1 | Quyền theo tin | `ApplicationOwnerService.loadOwnedJob` (public): 404 `COMPANY_NOT_FOUND` → 404 `JOB_NOT_FOUND` → 403. `JobOwnerService.loadOwned` (public): 404 `JOB_NOT_FOUND` → 404 `COMPANY_NOT_FOUND` → 403 | `ApplicationOwnerService.java:394-400`; `JobOwnerService.java:200-211` |
| b2 | Danh sách lấy **mọi** chu kỳ | `findByJobIdOrderByAppliedAtDesc(jobId)`, không lọc chu kỳ | `JobApplicationRepository.java:81`; `ApplicationOwnerService.java:158` |
| b3 | Câu theo lô có sẵn (không N+1) | `resumeRepository.findAllById`, `resumeParsedDataRepository.findByResumeIdIn`, `scoringRunRepository.findLatestByApplicationIdIn` (lượt mới nhất: `status`, `finished_at`), `findLatestDoneByApplicationIdIn` (lượt `DONE` mới nhất) | `ApplicationOwnerService.java:166-205`; `ResumeParsedDataRepository.java:20`; `ScoringRunRepository.java:56, 219` |
| b4 | `totalScore = null` ⇔ không có lượt `DONE` nào | Đúng — đọc từ `findLatestDoneByApplicationIdIn` | `ApplicationOwnerService.java:259`; comment `ApplicationHrListItemResponse.java` |
| b5 | Trường DTO frontend dùng được | `status`, `resumeParseStatus`, `latestScoringRunStatus`, `latestScoringRunFinishedAt`, `totalScore`, `recruitmentCycle`, `identityMasked` | `ApplicationHrListItemResponse.java` |
| b6 | Thiếu ở DTO | Không có cờ "có `resume_parsed_data`" — frontend chỉ thấy `resumeParseStatus` | như trên |

### (c) Tin đã xoá mềm và trạng thái tin

| # | Điểm kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| c1 | `GET /api/hr/jobs/{id}`, `.../applications`, `.../rubric`, `.../screening-questions`, `.../interview-template`, `.../applications/compare` và nút chấm đơn lẻ | **Không** lọc `jobs.deleted_at` — tin đã xoá mềm vẫn mở được bằng URL | `JobOwnerService.java:110-112, 200-211`; grep `deleted` ở `rubric/`, `comparison/`, `scoring/`: 0 chỗ kiểm `jobs.deleted_at` |
| c2 | Endpoint **ghi** duy nhất có quyết định riêng | `PUT /api/hr/jobs/{jobId}/blind-review` (FR-H16): `loadOwned` rồi `deletedAt != null` → 404 `JOB_NOT_FOUND` | `BlindReviewService.java:36-40` |
| c3 | Frontend trang sửa tin | Không xử lý riêng `deletedAt` (có trong kiểu `JobOwnerResponse` nhưng không đọc ở trang) | `ownerTypes.ts:24`; `HrJobEditPage.tsx` (grep `deletedAt`: 0) |
| c4 | Tin `DRAFT` có đơn được không | Có thể: tin `OPEN` có đơn rồi đưa về nháp (FR-U08 R-D4 gộp "về nháp" vào `UNAVAILABLE`) | `job/JobAvailabilityResolver.java` |

### (d) Rate limit, quyền, thông báo, job nền

| # | Điểm kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| d1 | Nhóm `llm-action` | Đếm theo **request** + userId; mẫu `"/api/hr/applications/*/scoring-runs"` (`*` một đoạn), capacity 20, nạp 20/phút; chưa xác thực → bỏ qua để chain trả 401/403 | `RateLimitFilter.java:47, 182-207`; `application.yml:164-166` |
| d2 | `/api/hr/**` | `hasRole("HR")` ở filter chain; không token → 401 `UNAUTHENTICATED` | `SecurityConfig.java:53-54`; `JsonAuthenticationEntryPoint.java:23` |
| d3 | Thông báo khi chấm xong | Mỗi lượt tổng hợp xong → một `SCORING_FINISHED` cho chủ công ty, **kèm email** (`email_status = PENDING`) | `NotificationEventListener.java:143-154, 214-225` |
| d4 | Vòng chấm | `ScoringRunScheduler` lấy tối đa `batch-size` (10) lượt `PENDING`, xử lý **tuần tự** trong một nhịp | `ScoringRunScheduler.java:49-50`; `application.yml:73-74` |
| d5 | Số đo nhánh perf (30/10/2026) | 8 luồng scheduler, quét 1 s; dồn 3 đơn: lượt 2/3 chờ nhận việc 47,8 / 90,0 s; chấm tiêu chí ~42 s/lượt; tổng một lượt ~58–111 s | walkthrough `perf-scoring-latency.md` mục 6, 7 |

### (e) Frontend tab Ứng viên

| # | Điểm kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| e1 | Vị trí ô "Sắp xếp theo" | Một hàng `flex items-center gap-2` ngay sau banner ẩn danh | `ApplicationsTab.tsx:414-428` |
| e2 | Query tin | `useHrJobQuery(jobId)` có sẵn trong tab (khoá chung với trang) — có `recruitmentCycle`, `deletedAt`, `status` | `ApplicationsTab.tsx:378`; `ownerTypes.ts:3-30` |
| e3 | Số tiêu chí | `useRubricQuery(jobId)` (khoá `['hr-rubric', jobId]`, tab Rubric đã dùng) trả `criteria[]` | `features/rubric/ownerQueries.ts:10-20`; `ownerTypes.ts:27` |
| e4 | Polling danh sách | Bật khi có đơn `PENDING` hoặc `RUNNING` + `totalScore === null`; chu kỳ 5 s; dừng sau `MAX_POLL_DURATION_MS` = 10 phút, hiện "Tải lại" | `queries.ts:30, 43, 62-66, 117-140`; `ApplicationsTab.tsx:432-441` |
| e5 | Vòng poll từng hàng | `useScoringRunsQuery(application.id, hasRun)` khoá `['scoring-runs', id]`; nút đơn lẻ invalidate khoá của **đúng** đơn đó | `queries.ts:150-191`; `ApplicationsTab.tsx:201-206` |
| e6 | Mẫu khoá nút có lý do | `disabled` + `aria-describedby` trỏ `<span className="sr-only">` (FR-H16) | `ApplicationsTab.tsx:291-305` |
| e7 | Snackbar/toast | **Dự án chưa có Snackbar**; tiền lệ dùng câu tại chỗ `role="status"` (FR-C05, FR-U12, FR-U16) | `docs/UI_GUIDE.md:208-210`; `components/ui/` không có toast |
| e8 | `Dialog` | Có `components/ui/dialog.tsx`; tiền lệ hộp xác nhận `ApplicationStatusConfirmDialog.tsx` | — |

### (f) Lệch giữa yêu cầu và code — đã đưa vào mục 11

- **F1.** Lượt `RUNNING` + `finished_at` khác `NULL` (chờ D3, ≤ 1 s sau nhánh perf) **không** là "đang chạy" với nút đơn
  lẻ và index (a5, a6), nhưng cũng **chưa** `DONE`. Nếu bulk dùng nguyên định nghĩa của nút đơn lẻ, nó tạo lượt thứ hai cho
  đơn sắp có điểm — tức chấm lại, trái mục đích "không chấm lại". → Q3.
- **F2.** "Tái dùng `requireResumeParsed`/`requireNoRunInProgress`" theo nghĩa đen là gọi lặp từng đơn (a2, N+1). Các câu
  theo lô của danh sách (b3) đã có sẵn và cho đúng cùng dữ liệu mà frontend dùng để đếm N. → Q4.
- **F3.** "Toast" không có trong dự án (e7). → Q7.
- **F4.** Tin đã xoá mềm: endpoint đọc không lọc, endpoint ghi duy nhất (FR-H16) trả 404 (c1, c2). → Q5.

## 1. Mục đích

Cho HR xếp mọi đơn **chưa từng có điểm** của chu kỳ hiện tại vào hàng chấm bằng một lần bấm, thay vì bấm "Chấm điểm hồ
sơ" từng dòng. Hộp xác nhận nói rõ chi phí (số lượt gọi AI ước tính) và thời gian chờ trước khi HR đồng ý.

Chức năng **không** thay đổi cách AI chấm: vẫn mỗi tiêu chí một lần gọi, evidence qua K2 (FR-H04); backend tổng hợp bằng
công thức trọng số (FR-H05); không nhãn, không phán quyết (FR-H07). Request bulk **không gọi AI** (CLAUDE.md §7).

## 2. Luồng người dùng

**HR (chủ công ty sở hữu tin)**
1. Mở `/hr/jobs/:id/edit`, tab "Ứng viên". Cạnh ô "Sắp xếp theo" có nút **"Chấm tất cả (N)"**, N đếm từ danh sách đang
   hiện theo R-F1.
2. N = 0: nút khoá, kèm câu lý do (UI.md mục 7).
3. N > 0: bấm → hộp xác nhận: số đơn sẽ xếp hàng (tối đa 50, R-C1), số lượt gọi AI ước tính, thời gian ước tính, và lưu ý
   danh sách tự cập nhật tối đa 10 phút.
4. Bấm "Chấm tất cả" trong hộp → `POST /api/hr/jobs/{jobId}/scoring-runs/bulk`.
5. Thành công: hộp đóng; dưới hàng nút hiện câu kết quả "Đã xếp {created} đơn vào hàng chấm." + số đơn bỏ qua đếm theo lý
   do (R-F5) (+ số còn lại nếu vượt trần). Danh sách làm mới; polling sẵn có tự bật; các dòng hiện tiến độ như khi bấm từng đơn.
6. Lỗi (409 rubric, 404, 403, 429, mạng): hộp giữ mở, câu lỗi hiện trong hộp; không lượt nào được tạo (409/404/403/429).

**Ứng viên**: không thấy gì; gọi endpoint → 403.

**Hệ thống**
- Request chỉ đọc + tạo hàng `scoring_runs` `PENDING`. Job nền `ScoringRunScheduler` chấm lần lượt như hiện có.
- Mỗi lượt chấm xong vẫn tạo một thông báo `SCORING_FINISHED` + email cho HR (d3) — không gộp (mục 6).

## 3. Quy tắc nghiệp vụ

### 3.1 Đơn có thể chấm (R-E) — kế hoạch 28/10 bảng "Đơn có thể chấm", sửa đổi 11/2026 điểm 5

- **R-E1. Tập xét.** Chỉ đơn có `job_id` = tin **và** `recruitment_cycle` = `jobs.recruitment_cycle` hiện tại. Đơn chu kỳ
  cũ **không** được xét: không tạo lượt, **không** vào `skipped`, không tính vào N.
- **R-E2. Đủ điều kiện** khi **cả năm** điều kiện đúng:
  1. Trạng thái đơn `PENDING`.
  2. CV `parse_status = DONE` **và** có dòng `resume_parsed_data` (đúng hai điều kiện của `requireResumeParsed`, a1).
  3. Không có lượt đang chạy theo **R-E4**.
  4. **Không có lượt `DONE` nào** (bất kể lượt mới nhất là gì). Lượt gần nhất `FAILED` mà chưa từng `DONE` → vẫn đủ
     điều kiện.
  5. Thuộc tập xét R-E1.
- **R-E3. Lý do bỏ qua** (mã chuẩn hoá, enum `BulkScoringSkipReason`): `NOT_PENDING`, `RESUME_NOT_PARSED`,
  `RUN_IN_PROGRESS`, `ALREADY_SCORED`. Một đơn trúng nhiều lý do → lấy **một** theo thứ tự ưu tiên
  **`NOT_PENDING` > `RESUME_NOT_PARSED` > `RUN_IN_PROGRESS` > `ALREADY_SCORED`** (Q2). Không có giá trị thứ năm; mã chỉ
  là chuỗi enum, không kèm câu.
- **R-E4. "Đang chạy" của bulk** (Q3): lượt **mới nhất** của đơn có `status` ∈ {`PENDING`, `RUNNING`}, **bất kể**
  `finished_at`. Rộng hơn nút đơn lẻ đúng một trường hợp: `RUNNING` + `finished_at` khác `NULL` (chờ D3) — để bulk không
  tạo lượt thứ hai cho đơn sắp có điểm (F1). Nút đơn lẻ **giữ nguyên** định nghĩa cũ (a5). Khớp điều kiện polling của
  frontend (`PENDING` hoặc `RUNNING` + `totalScore === null`, e4).
- **R-E5. Một nguồn dữ liệu với danh sách** (Q4): bulk đọc trạng thái bằng **đúng** các câu theo lô danh sách tab Ứng viên
  đang dùng (b2, b3): `findByJobIdOrderByAppliedAtDesc`, `resumeRepository.findAllById`,
  `resumeParsedDataRepository.findByResumeIdIn`, `findLatestByApplicationIdIn`, `findLatestDoneByApplicationIdIn`. Số câu
  đọc cố định (≤ 5 + 1 tin + 2 rubric), không N+1. Không viết câu SQL mới, không viết lại điều kiện "đang chạy"/"đã
  chấm" bằng SQL thứ hai.
- **R-E6. Lọc chu kỳ và sắp ở Java** trên kết quả `findByJobIdOrderByAppliedAtDesc`: giữ `recruitmentCycle ==
  job.recruitmentCycle`, sắp `applied_at ASC, id ASC`. Thứ tự này quyết định ai vào 50 đơn đầu (R-C1) và thứ tự `skipped`.

### 3.2 Rubric (R-R)

- **R-R1.** Kiểm **một lần**, trước mọi đơn, bằng `requireRubricComplete(jobId)` sẵn có (a8): không có rubric → 404
  `RUBRIC_NOT_FOUND`; tổng ≠ 100 (kể cả 0 tiêu chí) → 409 `RUBRIC_INCOMPLETE`, **cùng mã và câu** với nút đơn lẻ
  (`forScoring`). Không tạo lượt nào, không khoá rubric.
- **R-R2.** Chụp `RubricSnapshot` **một lần** (`findByRubricIdOrderByDisplayOrderAsc` + `RubricSnapshotMapper.toSnapshot`,
  như a1) và truyền **cùng một** đối tượng cho mọi `create`. Không đọc lại `rubric_criteria` trong vòng lặp.
- **R-R3.** Rubric đủ 100 mà **không** có đơn nào đủ điều kiện → 200, `created = 0`, không khoá rubric (chỉ `create` khoá).

### 3.3 Trần (R-C)

- **R-C1. Trần 50** đơn được **thử tạo** mỗi request. Hằng số khai một chỗ: `ScoringRunService.MAX_BULK_SCORING_RUNS =
  50`. Đơn đủ điều kiện sắp theo R-E6; 50 đơn đầu được thử, phần còn lại **không** thử, **không** vào `skipped`, chỉ đếm vào
  `remaining` (Q1).
- **R-C2.** Không bù chỗ: nếu một trong 50 đơn rơi vào `RUN_IN_PROGRESS` do race (R-T3), đơn thứ 51 **không** được thử
  thay. `remaining` = (số đơn đủ điều kiện) − (số đơn được thử).
- **R-C3.** Bấm lại sau: 50 đơn vừa tạo nay `RUN_IN_PROGRESS` → bị bỏ qua, request mới thử 50 đơn kế tiếp. Không cần
  phân trang hay tham số.

### 3.4 Tạo lượt và transaction (R-T)

- **R-T1. Không bọc cả request.** Method bulk ở `ScoringRunService` (class giữ **không** `@Transactional`, a3). Mỗi đơn
  gọi `stateService.create(applicationId, snapshot, rubricId)` riêng — mỗi lần một transaction ngắn, commit riêng.
- **R-T2. Không gọi AI** trong request; không gọi `ChatModel`/`EmbeddingModel`/K3.
- **R-T3. Race với nút đơn lẻ.** `create` ném `DataIntegrityViolationException` mà `getMostSpecificCause().getMessage()`
  chứa `uq_scoring_run_in_progress` → đơn đó vào `skipped` với `RUN_IN_PROGRESS`, vòng lặp chạy tiếp. **Mọi**
  `DataIntegrityViolationException` khác → ném tiếp (500), không nuốt.
- **R-T4.** Lượt đã tạo trước khi một lỗi ngoài dự kiến (R-T3 câu cuối) xảy ra **giữ nguyên** (đã commit). Chấp nhận:
  bấm lại thì các đơn đó thành `RUN_IN_PROGRESS`.
- **R-T5.** Khoá rubric giữ nguyên hành vi `create` (khoá ở lượt đầu, no-op các lượt sau, a4).

### 3.5 Quyền và tin (R-P)

- **R-P1.** `/api/hr/**` đã `hasRole("HR")` (d2): không token → 401 `UNAUTHENTICATED`; ứng viên → 403.
- **R-P2.** Sở hữu tin: `JobOwnerService.loadOwned(jobId, ownerId)` (public, dùng lại như FR-H16 L3): tin không tồn tại →
  404 `JOB_NOT_FOUND`; HR chưa có công ty → 404 `COMPANY_NOT_FOUND`; tin công ty khác → 403.
- **R-P3. Tin đã xoá mềm** (Q5): `job.getDeletedAt() != null` → 404 `JOB_NOT_FOUND`, kiểm tường minh sau `loadOwned` —
  khuôn endpoint ghi FR-H16 (c2). Trả lời trước rubric.
- **R-P4. Trạng thái tin** (Q6): **không** kiểm `OPEN`/`PAUSED`/`CLOSED`/`DRAFT` — đồng nhất với nút đơn lẻ (a9). HR được
  chấm đơn của tin đã tạm dừng/đóng.
- **R-P5.** Thứ tự kiểm: R-P1 → R-P2 → R-P3 → R-R1 → R-E → tạo. Lỗi ở bước nào thì không bước sau nào chạy.

### 3.6 Rate limit (R-L)

- **R-L1.** Thêm mẫu `"/api/hr/jobs/*/scoring-runs/bulk"` (hằng `SCORING_RUN_BULK_PATTERN`) vào nhánh `llm-action` của
  `RateLimitFilter.classify`. Một lần bấm = một lượt token, bất kể tạo bao nhiêu lượt chấm; trần 50 (R-C1) là chốt chặn
  chi phí thật. Không đổi capacity/nạp lại.
- **R-L2.** Mẫu đơn lẻ `"/api/hr/applications/*/scoring-runs"` không khớp đường bulk (khác tiền tố) — hai mẫu không chồng.

### 3.7 Frontend (R-F)

- **R-F1. Đếm N** bằng hàm thuần mới `isBulkScorable(application, jobRecruitmentCycle)` trong `scoringRules.ts`:
  `status === 'PENDING'` **và** `resumeParseStatus === 'DONE'` **và** `totalScore === null` **và**
  `latestScoringRunStatus` ∉ {`PENDING`, `RUNNING`} **và** `recruitmentCycle === jobRecruitmentCycle`. N = số dòng
  thoả. Frontend không thấy "có `resume_parsed_data`" (b6) — N có thể lớn hơn `created` trong ca dữ liệu hỏng; backend là
  chốt, `skipped` nói rõ.
- **R-F2. Khoá nút** khi N = 0, khi `job` chưa tải, khi `job.deletedAt !== null` (R-P3), hoặc khi request đang chạy;
  `disabled` + `aria-describedby` trỏ câu lý do (khuôn e6).
- **R-F3. Ước tính trong hộp xác nhận.** `k = min(N, 50)`. Số lượt gọi AI ≈ `k × (số tiêu chí + 1)` — mỗi tiêu chí một
  lần chấm, cộng một lần viết giải thích (FR-H06); chưa tính lần thử lại. Số tiêu chí = `useRubricQuery(jobId).data
  .criteria.length` (e3, Q8) — không thêm endpoint, không thêm trường vào response bulk. Rubric chưa tải/lỗi → thay dòng
  số lượt bằng câu "chưa tính được" (UI.md mục 7). Thời gian ≈ `k` phút ("khoảng 1 phút mỗi đơn vì các đơn được chấm lần
  lượt"). Có câu: danh sách tự cập nhật tối đa 10 phút, sau đó bấm "Tải lại".
- **R-F4. Sau khi thành công**: invalidate `hrApplicationsKeyPrefix(jobId)` **và** mọi khoá `['scoring-runs']` (theo tiền
  tố) — response không trả id các lượt đã tạo, mà dòng `FAILED` cũ đang giữ cache `['scoring-runs', id]` đã dừng poll
  (e5). Polling danh sách tự bật theo điều kiện có sẵn (e4); không sửa `MAX_POLL_DURATION_MS`.
- **R-F5. Câu kết quả** tại chỗ, `role="status"` (Q7), dưới hàng nút; tự mất khi HR bấm lại hoặc rời tab. Không tô màu,
  không nhãn theo kết quả chấm (CLAUDE.md §8). Khi `skipped.length > 0`, câu bỏ qua **đếm theo lý do** (Q13): "Bỏ qua {k}
  đơn: …" rồi liệt kê **chỉ** lý do có số > 0, cách nhau dấu phẩy, đúng thứ tự R-E3 (`NOT_PENDING`, `RESUME_NOT_PARSED`,
  `RUN_IN_PROGRESS`, `ALREADY_SCORED`), nhãn ở UI.md mục 7; đếm từ `skipped[].reason` của response, không suy từ danh
  sách. Chữ trung tính, không tên hay id đơn.
- **R-F6.** Không tự kiểm tổng trọng số 100 ở frontend; 409 `RUBRIC_INCOMPLETE` hiện nguyên câu backend trong hộp.

### 3.8 Ràng buộc chung (R-G)

- **R-G1. Không AI trong request** (R-T2). Lượt chấm do job nền chạy đúng luật FR-H04 hiện có.
- **R-G2. Không nhãn, không phán quyết.** Response và giao diện không có `verdict`/`label`/`passed`/`recommendation`, không
  đổi màu theo điểm (CLAUDE.md §7, §8).
- **R-G3. Ẩn danh FR-H16.** Bulk vẫn chấm đơn ẩn danh như thường; response chỉ chứa `applicationId` + mã lý do — **không**
  tên, email, hay trường danh tính nào.
- **R-G4. Không đổi nút đơn lẻ**: hành vi, mã lỗi và test của `POST /api/hr/applications/{id}/scoring-runs` giữ nguyên.

## 4. Dữ liệu & quyền truy cập

### 4.1 Migration

Không có. Không đổi schema, không đổi `docs/ER.md`, không đổi seed.

### 4.2 Endpoint

| Mã | Method + đường dẫn | Vai trò | Body | Thành công | Lỗi |
|---|---|---|---|---|---|
| B1 | `POST /api/hr/jobs/{jobId}/scoring-runs/bulk` | HR (chủ công ty sở hữu tin) | không | 200 + DTO mục 4.3 | 401 `UNAUTHENTICATED`; 403 (ứng viên, tin công ty khác); 404 `JOB_NOT_FOUND` (không tồn tại, đã xoá mềm); 404 `COMPANY_NOT_FOUND`; 404 `RUBRIC_NOT_FOUND`; 409 `RUBRIC_INCOMPLETE`; 429 `RATE_LIMIT_EXCEEDED` |

Controller mới `scoring/JobScoringRunBulkHrController` (`@RequestMapping("/api/hr/jobs/{jobId}/scoring-runs")`,
`@PostMapping("/bulk")`), ownerId lấy từ `authentication.getName()` như `ScoringRunHrController`. 200 (không phải 201): một
request tạo 0..50 tài nguyên, trả bản tóm tắt chứ không trả tài nguyên. `{jobId}` không phải UUID: xử lý như mọi
`@PathVariable UUID` hiện có, không thêm nhánh riêng.

### 4.3 DTO (`scoring/dto/BulkScoringRunResponse`)

```json
{
  "created": 3,
  "remaining": 0,
  "skipped": [
    { "applicationId": "…", "reason": "ALREADY_SCORED" },
    { "applicationId": "…", "reason": "NOT_PENDING" }
  ]
}
```

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `created` | `int` | Số lượt `PENDING` đã tạo trong request |
| `remaining` | `int` | Số đơn đủ điều kiện **không** được thử vì vượt trần (R-C1, R-C2); 0 khi ≤ 50 (Q1) |
| `skipped` | `List<SkippedApplication>` | Đơn trong tập xét R-E1 không được tạo lượt, sắp `applied_at ASC, id ASC`; chuẩn hoá `null` → `List.of()` |
| `skipped[].applicationId` | `UUID` | |
| `skipped[].reason` | `BulkScoringSkipReason` | Một trong 4 mã R-E3 |

Không trả id lượt đã tạo, tên ứng viên, điểm, hay trường danh tính. Kiểm: JSON B1 (duyệt đệ quy) chỉ có đúng các khoá
`created`, `remaining`, `skipped`, `applicationId`, `reason` (T13).

### 4.4 Câu truy vấn

Không câu mới. Dùng đúng: `JobRepository.findById` (qua `loadOwned`), `RubricRepository.findByJobId` +
`RubricCriterionRepository.sumWeightByRubricId` (qua `requireRubricComplete`),
`RubricCriterionRepository.findByRubricIdOrderByDisplayOrderAsc`, năm câu theo lô ở R-E5. Mỗi đơn được thử: các câu của
`ScoringRunStateService.create` (INSERT + đọc/khoá rubric). Danh sách rỗng → không gọi các câu `IN (...)` (guard như
`ApplicationOwnerService.java:201-205`).

### 4.5 Bảng chạm tới

| Bảng | Đọc | Ghi |
|---|---|---|
| `jobs`, `companies` | quyền, chu kỳ, `deleted_at` | — |
| `rubrics`, `rubric_criteria` | tổng trọng số, snapshot | `rubrics.is_locked` (qua `create`, có sẵn) |
| `job_applications`, `resumes`, `resume_parsed_data` | điều kiện R-E2 | — |
| `scoring_runs` | lượt mới nhất, lượt `DONE` mới nhất | INSERT `PENDING` (qua `create`) |

## 5. AI

Request B1 **không** gọi AI, không thuộc ngoại lệ K3, không prompt mới, không đổi `PROMPT_VERSION`. Sau đó job nền chấm
từng lượt đúng như nút đơn lẻ: mỗi tiêu chí một lần gọi, evidence kiểm bằng K2, retry 1 lần khi JSON hỏng; backend tổng
hợp (FR-H05); job giải thích chạy sau (FR-H06). AI không tính tổng, không xếp hạng, không gán nhãn.

## 6. Ngoài phạm vi

- **Gộp thông báo**: N lượt xong = N `SCORING_FINISHED` + N email cho HR (d3), giữ nguyên (Q9).
- Chấm song song nhiều lượt hay nhiều tiêu chí; đánh thức job bằng sự kiện; prompt caching; đổi prompt (kế hoạch 28/10
  mục "Không làm").
- Chấm lại đơn đã có lượt `DONE` hàng loạt (nút đơn lẻ vẫn làm được từng đơn).
- Chọn tay một nhóm đơn để chấm; chấm đơn chu kỳ cũ; chấm nhiều tin một lần.
- Huỷ lượt đã xếp hàng; thanh tiến độ tổng của cả lô; tự kéo dài polling quá 10 phút.
- Kiểm trạng thái tin `OPEN` (R-P4).
- Đổi định nghĩa "đang chạy" của nút đơn lẻ (R-E4, R-G4).
- Xây component Snackbar dùng chung (Q7).
- Trả id các lượt đã tạo trong response.

## 7. Xong khi

### 7.1 Test backend (Testcontainers, Postgres thật)

Lớp `JobScoringRunBulkIntegrationTest` tự tạo dữ liệu (HR, công ty, tin, rubric, ứng viên, CV, đơn; email duy nhất), không
đọc seed. **Không** bọc `@Transactional` cấp lớp: B1 gọi `create` nhiều lần, mỗi lần một transaction; ở lớp
`@Transactional` các lần gọi gộp vào transaction của test và một vi phạm index sẽ làm hỏng cả transaction (T10 không chạy
được), và `created_at`/`applied_at` trùng nhau (CLAUDE.md §3c). Dọn dữ liệu theo khuôn lớp đồng thời hiện có. Đơn cần
`applied_at` khác nhau thì backdate bằng native update. Mock `ChatModel`/`EmbeddingModel` mặc định throw — test xanh
chứng minh B1 không gọi AI.

| # | Test | Loại |
|---|---|---|
| T1 | 3 đơn đủ điều kiện → 200 `{created: 3, remaining: 0, skipped: []}`; DB có đúng 3 lượt `PENDING`, mỗi đơn một lượt, cả 3 cùng `rubric_snapshot` (so bằng nhau) và đúng thứ tự `display_order`; rubric `is_locked = true` | dương |
| T2 | Mỗi lý do một đơn, cùng tin với 1 đơn đủ điều kiện: `INTERVIEW_INVITED`, `REJECTED`, `HIRED`, `WITHDRAWN` → `NOT_PENDING`; CV `PENDING` parse → `RESUME_NOT_PARSED`; CV `DONE` nhưng không có `resume_parsed_data` → `RESUME_NOT_PARSED`; lượt mới nhất `PENDING` → `RUN_IN_PROGRESS`; `RUNNING` + `finished_at NULL` → `RUN_IN_PROGRESS`; `RUNNING` + `finished_at` khác `NULL` (chờ D3) → `RUN_IN_PROGRESS` (R-E4); có lượt `DONE` → `ALREADY_SCORED`; lượt `DONE` cũ + lượt mới nhất `FAILED` → `ALREADY_SCORED` → `created = 1`, `skipped` đúng 10 dòng đúng mã; không đơn bị loại nào có lượt mới | âm |
| T3 | Chỉ lượt `FAILED` (một hoặc hai lượt, chưa từng `DONE`) → được tạo lượt mới | dương |
| T4 | Ưu tiên lý do (R-E3): đơn `REJECTED` + CV chưa parse → `NOT_PENDING`; đơn `PENDING` + CV chưa parse + có lượt `DONE` → `RESUME_NOT_PARSED`; đơn `PENDING` có lượt `DONE` cũ + lượt `PENDING` mới nhất → `RUN_IN_PROGRESS` | biên |
| T5 | Rubric 99,99 → 409 `RUBRIC_INCOMPLETE`, câu **bằng** câu của nút đơn lẻ cùng tổng; rubric 0 tiêu chí → 409; 0 lượt mới; `is_locked` không đổi. Rubric 100,00 (scale khác) → 200 | âm + biên |
| T6 | Quyền: không token → 401 `UNAUTHENTICATED`; ứng viên → 403; HR công ty khác → 403; UUID không tồn tại → 404 `JOB_NOT_FOUND`; tin đã xoá mềm → 404 `JOB_NOT_FOUND`; HR chưa có công ty → 404 `COMPANY_NOT_FOUND`; mỗi ca 0 lượt mới | âm |
| T7 | Trần (biên): 49 đơn đủ điều kiện → `created 49, remaining 0`; 50 → `50, 0`; 51 → `50, 1`, đơn nộp **muộn nhất** (theo `applied_at`, hoà thì `id` lớn hơn) không có lượt và **không** có trong `skipped` | biên |
| T8 | Gọi hai lần liên tiếp (3 đơn đủ điều kiện): lần hai `created 0`, `skipped` = 3 đơn đều `RUN_IN_PROGRESS`; tổng 3 lượt | âm |
| T9 | 51 đơn: gọi lần một → 50; lần hai → `created 1, remaining 0`, 50 dòng `RUN_IN_PROGRESS` (R-C3) | dương |
| T10 | Race (R-T3): `@MockitoSpyBean ScoringRunStateService`; `doAnswer` ở lần `create` cho đơn A: chèn một lượt `PENDING` cho A bằng `JdbcTemplate` (tự commit) rồi `callRealMethod()` → index ném thật. Kết quả: A trong `skipped` `RUN_IN_PROGRESS`, B và C (thử sau A) vẫn được tạo, `created = 2`; DB: A đúng 1 lượt (lượt chèn tay) | âm |
| T11 | Lỗi ngoài dự kiến không bị nuốt: spy `create` ném `DataIntegrityViolationException` với thông điệp không chứa `uq_scoring_run_in_progress` ở đơn thứ hai → 500; lượt của đơn thứ nhất **vẫn còn** (R-T4) | âm |
| T12 | Chu kỳ (R-E1): đơn chu kỳ 1 (đủ mọi điều kiện khác) + tăng `jobs.recruitment_cycle` lên 2 + đơn chu kỳ 2 → chỉ đơn chu kỳ 2 được tạo; đơn chu kỳ 1 **không** có trong `skipped` và không có lượt mới | âm |
| T13 | Ẩn danh (R-G3): bật `blind_review_enabled` → vẫn tạo đủ lượt; JSON B1 (duyệt đệ quy) chỉ gồm khoá `created`, `remaining`, `skipped`, `applicationId`, `reason`; chuỗi JSON không chứa tên hay email ứng viên | âm |
| T14 | Trạng thái tin (R-P4): tin `PAUSED` và `CLOSED` có đơn đủ điều kiện → 200, tạo lượt | dương |
| T15 | Rubric đủ, không đơn nào đủ điều kiện (chỉ có đơn `WITHDRAWN`) → 200 `created 0`; rubric `is_locked` vẫn `false` (R-R3) | biên |
| T16 | Số câu đọc cố định (R-E5): 3 đơn và 20 đơn (đều đã `DONE`, `created 0`) → số câu SQL **bằng nhau** — đếm bằng `org.hibernate.stat.Statistics` (`generate_statistics: true` đã bật ở `application-test.yml:96`; tiền lệ `ApplicationComparisonControllerIntegrationTest.java:1206`) | đơn vị |
| T17 | Rate limit (`RateLimitFilterTest`, không Spring context): `POST /api/hr/jobs/{uuid}/scoring-runs/bulk` có xác thực → nhóm `llm:`; `GET` cùng đường → không giới hạn; `POST /api/hr/jobs/{uuid}/scoring-runs` (thiếu `/bulk`) → không thuộc nhóm nào; chưa xác thực → không giới hạn | dương + âm |
| T18 | Hồi quy nút đơn lẻ: `ScoringRunHrControllerIntegrationTest` xanh **không sửa dòng nào** (R-G4) | hồi quy |

### 7.2 Kiểm bằng HTTP — Windows PowerShell 5.1

Nạp seed test (`db/seed/README.md`). Backend ở `localhost:8080`, scheduler **bật** (để thấy lượt chạy). Dán cả khối vào
một cửa sổ PowerShell. Đợt cuối điền `$jobId` bằng một tin của `minhdathr@gmail.com` có đơn chưa chấm và rubric đủ 100
(Plan Mode tra seed và ghi id cố định vào đây trước khi duyệt đợt cuối).

```powershell
$base = 'http://localhost:8080'

function Get-Token([string]$Email, [string]$Password) {
    $body = @{ email = $Email; password = $Password } | ConvertTo-Json
    (Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' -Body $body).accessToken
}

# In "<ma HTTP> <METHOD> <duong dan> <than loi neu co>".
function Test-Call([string]$Method, [string]$Path, [string]$Token) {
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Method $Method -Uri "$base$Path" -Headers $headers
        '{0} {1} {2} {3}' -f [int]$r.StatusCode, $Method, $Path, $r.Content
    } catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { 'KHONG KET NOI DUOC {0} {1}' -f $Method, $Path; return }
        $text = $_.ErrorDetails.Message
        if (-not $text) {
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $text = $reader.ReadToEnd()
        }
        '{0} {1} {2} {3}' -f [int]$resp.StatusCode, $Method, $Path, $text
    }
}

$tokenHr = Get-Token 'minhdathr@gmail.com' '12345678'
$tokenUv = Get-Token 'quochuyuv@gmail.com' '12345678'

$jobId = '<DIEN O PLAN MODE>'
$bulk  = "/api/hr/jobs/$jobId/scoring-runs/bulk"
$none  = '/api/hr/jobs/00000000-0000-0000-0000-000000000000/scoring-runs/bulk'

'--- 1. Duong: lan mot (mong doi 200, created > 0)'
Test-Call POST $bulk $tokenHr

'--- 2. Am: bam lai ngay (mong doi 200, created 0, moi dong RUN_IN_PROGRESS hoac ALREADY_SCORED)'
Test-Call POST $bulk $tokenHr

'--- 3. Am: tin khong ton tai (404 JOB_NOT_FOUND), ung vien (403), khong token (401)'
Test-Call POST $none $tokenHr
Test-Call POST $bulk $tokenUv
Test-Call POST $bulk $null
```

Mong đợi: nhóm 1 `200 … "created":k,…` với k > 0; nhóm 2 `200 … "created":0` và mọi `reason` thuộc
`RUN_IN_PROGRESS`/`ALREADY_SCORED`/`NOT_PENDING`/`RESUME_NOT_PARSED`; nhóm 3 `404 … JOB_NOT_FOUND`, `403`, `401 …
UNAUTHENTICATED`.

**Bắt buộc ở đợt code cuối:** chạy thật khối trên, dán **nguyên văn** output vào báo cáo. Kết quả khác "Mong đợi" vì lệnh
viết sai thì dừng, đề xuất sửa mục này, chờ duyệt lại.

### 7.3 Frontend

- `npm run build` + `npm run lint` sạch.
- `isBulkScorable` nằm trong `features/scoring/scoringRules.ts`; tìm `recruitmentCycle` trong `ApplicationsTab.tsx` chỉ
  thấy ở chỗ chọn so sánh (có sẵn) và lời gọi `isBulkScorable` — không có bản điều kiện thứ hai nội tuyến.
- Tìm `toast|sonner|Snackbar` trong `frontend/src` → 0 dòng (Q7, nếu giữ mặc định).
- Tìm `bg-red|bg-green|text-red|text-green|verdict|passed` trong file mới/sửa của `features/scoring/` → 0 dòng.
- Tìm `scoring-runs/bulk` trong `frontend/src` → chỉ ở `features/scoring/api.ts`.
- *(Q13)* `bulkScoringTexts.ts` có đúng 4 nhãn lý do của UI.md mục 7, khai theo thứ tự R-E3; câu bỏ qua dựng bằng một
  hàm duyệt mảng thứ tự đó và bỏ lý do có số 0 — không có chuỗi nào ghép cứng "0 …".

### 7.4 Soát tay (desktop ≥ `lg`, 768px, 375px) — `minhdathr@gmail.com` / `12345678`, AI thật

Dữ liệu: Plan Mode (đợt 1) ghi vào đây **id tin seed cụ thể** và bảng đơn của tin đó (trạng thái đơn, `parse_status`,
có/không lượt `DONE`, chu kỳ) cùng **N mong đợi bằng số**; seed không có đủ năm loại đơn thì Plan Mode ghi các bước
dựng tay (rút một đơn, tăng chu kỳ tin…). Không soát tay khi chưa có con số N mong đợi.

| Cần thấy | Thao tác | Mong đợi |
|---|---|---|
| N đúng | Tin seed đã ghi ở trên (có đơn đã chấm, chưa chấm, đã rút, CV chưa trích xuất xong, đơn chu kỳ cũ) | Nhãn nút = N mong đợi đã ghi; sau khi bấm, `created + skipped.length` (DevTools) = số đơn chu kỳ hiện tại |
| N = 0 | Tin mà mọi đơn đã có điểm | Nút khoá; câu lý do đọc được bằng trình đọc màn hình (`aria-describedby`) |
| Hộp xác nhận | Bấm nút | Đủ 4 ý: số đơn, số lượt gọi AI ước tính = k × (tiêu chí + 1), thời gian ≈ k phút, lưu ý 10 phút / "Tải lại" |
| Rubric thiếu | Sửa rubric về 99% ở tab Rubric (trước khi rubric bị khoá — tin mới), bấm | Hộp giữ mở, câu 409 của backend, không dòng nào đổi tiến độ |
| Bỏ qua theo lý do (Q13) | Tin có 1 đơn đã rút, 2 đơn đã có điểm, còn lại đủ điều kiện; bấm và xác nhận | Câu đúng "Bỏ qua 3 đơn: 1 không ở trạng thái chờ duyệt, 2 đã có điểm." — không có phần "0 …", thứ tự R-E3, không tên đơn; bấm lại ngay → thêm phần "{k} đang có lượt chấm chạy" |
| Thành công | Xác nhận | Hộp đóng; câu "Đã xếp k đơn vào hàng chấm." + câu bỏ qua theo lý do; các dòng hiện "Đang chờ xử lý" rồi tiến độ; DevTools: đúng **một** `POST …/bulk` |
| Dòng `FAILED` cũ | Một đơn có lượt `FAILED`, chấm tất cả | Dòng đó chuyển sang tiến độ của lượt mới (R-F4), không kẹt badge `FAILED` |
| Lần lượt | 3 đơn | Các dòng xong lần lượt, không cùng lúc; mỗi lượt xong một thông báo ở chuông |
| Ẩn danh | Tin bật ẩn danh | Nút hoạt động như thường; câu kết quả không có tên ứng viên |
| Bấm nhanh hai lần | Bấm "Chấm tất cả" trong hộp hai lần liền | Chỉ một request (nút khoá khi đang chạy) |
| Không màu phán quyết | Sau khi chấm xong | Không màu đỏ/vàng/xanh, không nhãn Đạt/Không đạt; điểm vẫn mở được evidence |
| Bề rộng | 375px, 768px, 1024px | Không cuộn ngang trang; nút xuống dòng dưới "Sắp xếp theo" ở 375px (UI.md mục 8) |

## 8. AI hay làm sai (dành cho người code)

- Gọi AI hay chấm ngay trong request ("cho nhanh") thay vì chỉ tạo `PENDING` (R-T2, CLAUDE.md §7).
- Đặt `@Transactional` lên method bulk hay lên `ScoringRunService` → một vi phạm index làm hỏng mọi lượt, mọi lượt cùng
  `created_at`, transaction giữ suốt vòng lặp (R-T1).
- Gọi `this.create(...)`/tự viết INSERT thay vì qua bean `ScoringRunStateService` (self-invocation, CLAUDE.md §3c).
- Gọi `createScoringRun` (nút đơn lẻ) trong vòng lặp rồi bắt exception — chụp snapshot N lần, đọc rubric N lần, N+1 câu
  kiểm (R-R2, R-E5).
- Nuốt mọi `DataIntegrityViolationException` thành `RUN_IN_PROGRESS` (R-T3).
- Dùng định nghĩa "đang chạy" của nút đơn lẻ cho bulk (bỏ sót `RUNNING` + `finished_at`) → chấm lại đơn sắp có điểm
  (R-E4); hoặc ngược lại, sửa luôn nút đơn lẻ (R-G4).
- Coi "lượt mới nhất `FAILED`" là "chưa chấm" mà không kiểm lượt `DONE` cũ hơn (R-E2.4).
- Đưa đơn chu kỳ cũ vào `skipped`, hay đơn vượt trần vào `skipped` (R-E1, R-C1).
- Sắp theo `applied_at DESC` (thứ tự của danh sách) thay vì `ASC` cho trần; quên khoá phụ `id` (R-E6).
- Bù chỗ đơn thứ 51 khi một đơn trong 50 bị race (R-C2).
- Chụp snapshot mỗi đơn một lần, hay đọc lại `rubric_criteria` trong vòng lặp (R-R2).
- Trả câu riêng cho rubric thiếu thay vì `forScoring` (R-R1).
- Trả tên ứng viên / email / `candidateName` trong `skipped` (R-G3).
- Thêm trường `verdict`, `label`, `passed`, `recommendation` hay đếm "đơn đạt" vào response (CLAUDE.md §7).
- Thêm endpoint đếm N ở backend hoặc trường `criteriaCount` vào response chỉ để ước tính (R-F3, Q8).
- Viết lại điều kiện đủ điều kiện nội tuyến trong `ApplicationsTab.tsx` thay vì `isBulkScorable` (R-F1).
- Chỉ invalidate danh sách mà quên `['scoring-runs']` → dòng `FAILED` cũ kẹt (R-F4).
- Cài thư viện toast (`sonner`…) hoặc sửa file trong `components/ui` (Q7, UI.md mục 10).
- Tô màu câu kết quả theo số đơn bỏ qua (đỏ khi nhiều đơn bị bỏ) (R-F5).
- Sửa `MAX_POLL_DURATION_MS` để "đủ cho lô lớn" (R-F4).
- Bọc lớp test bằng `@Transactional` (T10, T11 không thể đúng; mục 7.1).
- Dựng race T10 bằng hai luồng + `sleep` (không xác định) thay vì spy chèn lượt trước khi gọi thật.
- *(Tiền lệ U16 B4)* Viết lệnh Maven có `-D` không bọc nháy kép trong PowerShell.

## 9. Kế hoạch đợt code

Mỗi đợt: dừng → báo cáo diff → chờ duyệt → một commit. Giữa các đợt chạy `.\mvnw.cmd test-compile` + lớp test liên quan;
full suite ở đợt cuối. Frontend: `npm run build` + `npm run lint` sau mỗi đợt có sửa frontend. **Trước khi code:** rebase
`feat/fr-h04-bulk-scoring` lên `main` đã có `perf/scoring-latency` (README mục 22 — "làm sau khi perf đã merge"). Lệnh
Maven trong PowerShell bọc nháy kép cả cụm `-D`: `.\mvnw.cmd test "-Dtest=A,B"`.

| Đợt | Nội dung | Kiểm |
|---|---|---|
| 1 | Plan Mode: đối chiếu đặc tả đã duyệt với code sau rebase (mục 0, số dòng có thể lệch); tra id tin seed cho 7.2 | — |
| 2 | Backend: `BulkScoringSkipReason`, `BulkScoringRunResponse`, method bulk trong `ScoringRunService` (R-E, R-R, R-C, R-T, R-P), `JobScoringRunBulkHrController`, mẫu rate limit (R-L1); T1–T18 | `.\mvnw.cmd test "-Dtest=JobScoringRunBulkIntegrationTest,RateLimitFilterTest,ScoringRunHrControllerIntegrationTest"` |
| 3 | Frontend: `isBulkScorable` (`scoringRules.ts`), `bulkCreateScoringRunsRequest` (`api.ts`), `useBulkCreateScoringRunsMutation` (`queries.ts`), `BulkScoringButton` + `BulkScoringConfirmDialog`, gắn vào `ApplicationsTab` (UI.md) | build + lint; 7.3 |
| 4 | Soát tay 7.4 (người dùng làm, AI thật) | 7.4 |
| 5 | Đợt cuối: full `.\mvnw.cmd test`; khối 7.2 (dán output); `srs-guard`; `walkthrough`; tài liệu mục 10; trạng thái `ĐÃ HOÀN THÀNH` | full suite; 7.2; 7.3 |

## 10. Tài liệu dùng chung — checklist đợt cuối (chưa sửa ở đợt đặc tả)

- [ ] `docs/features/HR/H04/REQUIREMENT.md`, `UI.md`: `ĐÃ HOÀN THÀNH <ngày>`.
- [ ] `docs/SRS.md`: câu đầu file "cập nhật lần gần nhất". Dòng FR-H04 ở mục 4 (`SRS.md:201`, có từ đợt D1) đã đối chiếu
  và sửa cho khớp trong commit duyệt (Q12) — đợt cuối chỉ soát lại cho khớp code.
- [ ] `docs/ROADMAP.md`: tick `feat/fr-h04-bulk-scoring`.
- [x] `docs/UI_GUIDE.md` mục 7, dòng `/hr/jobs/:id/edit`: **đã sửa trong commit duyệt đặc tả** (Q12). Đợt cuối chỉ soát
  lại cho khớp code.
- [ ] `docs/features/README.md` mục 22: khối "Phạm vi thực tế" nếu có lệch so với mô tả định hướng (vd R-E4, Q7).
- [ ] `docs/DEMO.md`: kịch bản chấm tất cả + số đo thời gian lô thật.
- [ ] `CLAUDE.md` §3: không cần thêm package (code trong `scoring/`).
- [ ] `docs/ER.md`: không đổi (không migration).

## 11. Đã chốt 30/10/2026

Q1–Q11 chốt **theo đề xuất mặc định** ghi ở cột Quyết định. Q12, Q13 chốt như ghi trong bảng (Q13 khác đề xuất ban đầu).

| # | Câu hỏi | Quyết định |
|---|---|---|
| Q1 | Tên, kiểu trường "số còn lại" khi vượt trần | **`remaining: number`** (int, luôn có, 0 khi ≤ 50); nghĩa = số đơn đủ điều kiện không được thử (R-C1, R-C2); không liệt kê id các đơn đó |
| Q2 | Thứ tự ưu tiên lý do khi một đơn trúng nhiều lý do | **`NOT_PENDING` > `RESUME_NOT_PARSED` > `RUN_IN_PROGRESS` > `ALREADY_SCORED`** (R-E3) — lý do "HR không cần làm gì" đứng trước lý do "đợi là xong" |
| Q3 | Lượt `RUNNING` + `finished_at` khác `NULL` (chờ D3) có tính là "đang chạy" với bulk không? (lệch F1) | **Có** (R-E4): bulk xét **lượt mới nhất** `PENDING`/`RUNNING` bất kể `finished_at`; nút đơn lẻ và index giữ nguyên. Kiểm bằng Java, không có chốt DB cho đúng trường hợp này; cửa sổ race ≤ 1 nhịp D3 (1 s sau nhánh perf), hậu quả tệ nhất là một lượt chấm thừa — giống hệt điều nút đơn lẻ đang cho phép |
| Q4 | "Tái dùng các hàm kiểm tra sẵn có" — gọi lặp `requireResumeParsed`/`requireNoRunInProgress` từng đơn, hay dùng các câu theo lô của danh sách? (lệch F2) | **Câu theo lô có sẵn** (R-E5): cùng nguồn với N phía frontend, số câu cố định. Tái dùng nguyên `requireRubricComplete`, snapshot, `stateService.create`, `JobOwnerService.loadOwned`. `requireResumeParsed`/`requireNoRunInProgress` giữ cho nút đơn lẻ, không sửa. Không viết SQL mới |
| Q5 | Tin đã xoá mềm (lệch F4) | **404 `JOB_NOT_FOUND`** (R-P3), khuôn endpoint ghi FR-H16 (`BlindReviewService.java:39`); frontend khoá nút khi `job.deletedAt !== null`. Phương án khác: không kiểm, như nút đơn lẻ và các endpoint đọc |
| Q6 | Tin `PAUSED`/`CLOSED`/`DRAFT` có cho chấm tất cả không? | **Có, không kiểm trạng thái tin** (R-P4) — đồng nhất nút đơn lẻ (a9); HR vẫn cần chấm đơn đã nhận khi tạm dừng/đóng tin |
| Q7 | "Toast" khi dự án chưa có Snackbar (lệch F3) | **Câu tại chỗ `role="status"`** dưới hàng nút (R-F5), tiền lệ FR-C05/U12/U16; nội dung y như toast đã chốt. Phương án khác: thêm component Snackbar dùng chung (thư viện mới, sửa `components/ui`, UI_GUIDE mục 3) — đề xuất để FR riêng |
| Q8 | Số tiêu chí cho ước tính lấy từ đâu | **`useRubricQuery(jobId)` sẵn có** (khoá `['hr-rubric', jobId]`, tab Rubric đã dùng) — không thêm endpoint, không thêm trường vào response (R-F3) |
| Q9 | N lượt xong = N `SCORING_FINISHED` + N email | **Giữ nguyên**; "gộp thông báo" ở mục 6 |
| Q10 | Cách test nhánh bắt vi phạm `uq_scoring_run_in_progress` | **`@MockitoSpyBean ScoringRunStateService` + `doAnswer`** chèn lượt `PENDING` cho đơn đó bằng `JdbcTemplate` (tự commit) rồi `callRealMethod()` — index ném thật, xác định, không cần hai luồng (T10). Tiền lệ `@MockitoSpyBean`: `CvBuilderCreateIntegrationTest`. Kèm T11 cho nhánh "không nuốt lỗi khác" |
| Q11 | Mã HTTP thành công | **200** (đã chốt) — giữ, dù nút đơn lẻ trả 201: B1 trả bản tóm tắt, không trả tài nguyên vừa tạo (mục 4.2) |
| Q12 | *(spec-review)* Khi nào sửa `docs/UI_GUIDE.md` mục 7 (dòng `/hr/jobs/:id/edit`)? Dòng FR-H04 ở `docs/SRS.md` mục 4 có khớp đặc tả không? | **UI_GUIDE sửa trong commit duyệt đặc tả**, như tiền lệ FR-U16 Q6: dòng `/hr/jobs/:id/edit` thêm nút "Chấm tất cả" ở tab Ứng viên và "FR-H04 (mở rộng)" ở cột FR. **SRS mục 4 đã có dòng FR-H04** (`SRS.md:201`, từ đợt D1) — **không** thêm dòng mới; đối chiếu thấy dòng đó thiếu hai điều kiện R-E2 (CV đã trích xuất xong, không có lượt đang chạy) nên sửa cho khớp trong cùng commit |
| Q13 | *(spec-review)* Câu kết quả chỉ ghi tổng "Bỏ qua k đơn." — SRS "Nguyên tắc bổ sung": "Dữ liệu thiếu phải hiển thị rõ là thiếu, không âm thầm loại khỏi kết quả" | **Đếm theo lý do** (khác đề xuất ban đầu "giữ tổng"). Khi `skipped > 0`: "Bỏ qua {k} đơn: {a} không ở trạng thái chờ duyệt, {b} CV chưa trích xuất xong, {c} đang có lượt chấm chạy, {d} đã có điểm." — chỉ lý do có số > 0, đúng thứ tự R-E3, chữ trung tính, không tên đơn. Ghi ở R-F5; UI.md mục 4a, 4b, 7; mục 7.3, 7.4 |
