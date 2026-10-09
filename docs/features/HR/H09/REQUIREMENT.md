# FR-H09 — Trang hồ sơ đơn ứng tuyển

> Trạng thái: ĐÃ HOÀN THÀNH (07/10/2026). Duyệt: 06/10/2026.

- Nhóm: HR
- Tóm tắt: Một màn hình `/hr/applications/:id` cho một đơn: tab "CV & điểm", "Giải thích", "Lịch sử"
  và thanh thao tác FR-H07. Thay Sheet "Hồ sơ ứng viên" đang có ở tab Ứng viên của trang sửa Job.
- Phụ thuộc: FR-H04, FR-H05, FR-H06, FR-H07 — cả 4 đều **Đã hoàn thành** (`docs/SRS.md` mục 0).
  Phase 2.1b (`refactor/ui-md3-legacy`) đã tick (`docs/ROADMAP.md`).
- Nhánh: `feat/fr-h09-application-detail`
- Mở rộng: FR-H07 (chuyển nút quyết định từ Sheet sang trang này), FR-C03 (link thông báo HR).

## 0. Đã đối chiếu code trước khi viết (CLAUDE.md §6)

### (a)–(e) Các giả định ban đầu

| # | Giả định | Kết quả | Bằng chứng |
|---|---|---|---|
| a | Chưa có `GET /api/hr/applications/{id}`; điểm và giải thích chỉ trả kèm danh sách theo Job | **Đúng** | Dưới `/api/hr/applications/{applicationId}` chỉ có `/status` (PATCH, `ApplicationStatusController.java:19`), `/scoring-runs` (POST/GET, `ScoringRunHrController.java:24`), `/resume/download` (`ResumeHrController.java:20,31`), `/interview-invitation` (`InterviewInvitationController.java:22`). Điểm/giải thích chỉ có trong `ApplicationHrListItemResponse` của `GET /api/hr/jobs/{jobId}/applications` (`ApplicationOwnerController.java:17,28-35`, `ApplicationOwnerService.java:81-174`) |
| b | HR chưa có API đọc CV đã trích xuất | **Đúng** | `ResumeHrController` chỉ có `/download`. `/parsed` chỉ ở phía ứng viên: `ResumeCandidateController.java:85-88` → `ResumeService.getParsedData` kiểm `findByIdAndCandidateId` |
| c | HR chưa có API lịch sử trạng thái đơn | **Đúng** | Chỉ `ApplicationCandidateController.java:46-51` (`/api/candidates/applications/{id}/history`). `ApplicationStatusHistoryRepository` chỉ được dùng bởi `ApplicationService` và `ApplicationStatusRecorder`. `ScoringRunAuditController` (`/api/hr/candidates/{id}/audit/scoring-runs`, FR-H08) là lịch sử **lượt chấm**, không phải lịch sử trạng thái |
| d | Mọi thông báo của HR trỏ `/hr/jobs` | **Đúng** | `NotificationContentBuilder.java:41,48,55` — cả 3 loại thông báo HR. Cả 3 **đều gắn với một đơn**: `NotificationEventListener.java:74,88,101` lưu `event.applicationId()` vào `entity_id` (`:115`). Không có thông báo HR nào phải giữ `/hr/jobs` |
| e | `ApplicationsTab.tsx` có Sheet "Hồ sơ ứng viên" hiện điểm + giải thích | **Đúng, và Sheet còn chứa nút quyết định** | `ApplicationsTab.tsx:240-304`: `CriterionScoreBreakdown` + `ExplanationReport` + 3 nút Mời phỏng vấn/Trúng tuyển/Từ chối (`:284-302`). Đây là **nơi duy nhất** có nút FR-H07 trong toàn bộ frontend |

### (f) Phát hiện thêm, ảnh hưởng đặc tả

- **Quy ước kiểm quyền của nhóm `/api/hr/applications/{id}/**`:** đơn không tồn tại → 404
  `APPLICATION_NOT_FOUND`; đơn thuộc công ty khác → 403 (`AccessDeniedException` → `JsonAccessDeniedHandler`,
  `{"error":"FORBIDDEN"}`): `ScoringRunService.java:131`, `ApplicationStatusService.java:110`,
  `InterviewInvitationService.java:173`, `ResumeHrService.java:105`, `ScoringRunAuditService.java:127`.
  `ResumeHrService.java:79-95` ghi rõ đây là lựa chọn có chủ đích. FR-H09 theo đúng quy ước này (R-Q2).
- `ApplicationHistoryTimeline.tsx` gọi cứng hook phía ứng viên `useApplicationHistoryQuery`
  (`features/applications/queries.ts:24`) — HR gọi sẽ bị 403 ở filter chain (`SecurityConfig.java:53`).
  Muốn dùng lại phải cho component nhận dữ liệu từ ngoài (R-C3).
- `InterviewInvitationDialog` nhận prop kiểu `ApplicationHrListItem` (`InterviewInvitationDialog.tsx:12,46-50`)
  và `useSendInterviewInvitationMutation(jobId)` chỉ làm mới danh sách theo Job.
- Hộp xác nhận Từ chối/Trúng tuyển **không phải component riêng**: viết thẳng trong `ApplicationsTab.tsx:332-345`
  (`CONFIRM_STATUS_COPY`) và `:463-489` (`Dialog`).
- `ExplanationReport` trả `null` khi cả `explanation` và `explanationStatus` đều `null`
  (`ExplanationReport.tsx:62-64`) — trang phải tự hiện trạng thái "chưa có giải thích".
- `/hr/candidates` (`CandidatesTable.tsx:61-91`) hiện chưa có lối vào hồ sơ đơn, chỉ có "Lịch sử đánh
  giá" (FR-H08) và "Chấm điểm hồ sơ".
- Trang sửa Job đã đọc `?tab=` từ URL (`HrJobEditPage.tsx:480-481`) → link quay lại
  `/hr/jobs/{jobId}/edit?tab=applications` dùng được ngay.
- Route HR cần công ty nằm trong nhóm `RequireCompany` (`App.tsx:80-94`).
- **Nút "Chấm điểm hồ sơ"** dùng `scoringDisabledReason` (`features/scoring/scoringRules.ts:15-30`): khoá khi
  CV chưa `DONE` hoặc có lượt `PENDING`/`RUNNING` với `finishedAt = null` — khớp partial unique index
  `uq_scoring_run_in_progress` (`V4__scoring_run_in_progress_unique.sql`). Lượt `RUNNING` đã có
  `finished_at` (chờ D3 tổng hợp) **không** bị coi là đang chạy ở cả hai nơi.
- **Thư giới thiệu (`cover_letter`)**: form nộp đơn có ô nhập "Thư giới thiệu (tuỳ chọn)"
  (`JobApplyForm.tsx:115-121`, gửi `coverLetter.trim() || undefined` ở `:57`); backend nhận tại
  `ApplicationCreateRequest.java:12` (không `@Size`), lưu ở `ApplicationService.java:71`, cột `TEXT`
  (`V1__init_schema.sql:217`). Không giới hạn độ dài ở cả hai phía. HR hiện không thấy nội dung này ở đâu.
- Seed: `seed-demo-ai-output.sql` có 22 dòng và `seed-test.sql` có 10 dòng thông báo `link = '/hr/jobs'`
  — là "thông báo cũ" theo R-N2, không sửa dữ liệu.
- Không cần migration: mọi dữ liệu đã có trong `job_applications`, `resumes`, `resume_parsed_data`,
  `scoring_runs`, `criterion_scores`, `score_explanations`, `score_explanation_attempts`,
  `application_status_history`.

## 1. Mục đích

HR xem và quyết định trên **một** đơn ứng tuyển tại **một** màn hình: CV gốc, thư giới thiệu và CV đã
trích xuất, điểm từng tiêu chí kèm evidence, báo cáo giải thích, lịch sử trạng thái, và các nút Mời
phỏng vấn / Từ chối / Trúng tuyển. Màn hình này là điểm đặt cho các FR sau (H10, H13, C06, C08, H15) —
mỗi FR tự thêm tab/nút của mình.

## 2. Luồng người dùng

**HR đã đăng nhập, có hồ sơ công ty**
1. Vào trang từ một trong ba điểm (R-E): tab "Ứng viên" của `/hr/jobs/:id/edit`, trang `/hr/candidates`,
   hoặc bấm một thông báo HR mới.
2. Đầu trang: tên ứng viên, tên tin tuyển dụng (liên kết quay lại tab Ứng viên của Job), ngày nộp, badge
   trạng thái đơn, thanh thao tác.
3. Tab mặc định "CV & điểm": phần CV (tải CV gốc, thư giới thiệu nếu có, CV đã trích xuất) và phần điểm
   (tổng điểm, hạng, điểm từng tiêu chí; mở từng tiêu chí để xem diễn giải và evidence; nút "Chấm điểm
   hồ sơ").
4. Tab "Giải thích": báo cáo FR-H06 của đúng lượt chấm đang hiện điểm.
5. Tab "Lịch sử": các lần chuyển trạng thái của đơn, cũ trước mới sau.
6. Bấm nút thao tác → hộp thoại hiện có của FR-H07 → xác nhận → badge, thanh thao tác và tab Lịch sử
   cập nhật ngay, không cần tải lại trang.

**Hệ thống**
- Tải phần đầu trang trước; mỗi tab chỉ gọi API của mình khi tab được mở.
- Không gọi LLM/embedding, không tạo lượt chấm nào khi HR mở trang. Lượt chấm chỉ được tạo khi HR bấm
  "Chấm điểm hồ sơ" (R-S).

## 3. Quy tắc nghiệp vụ

### 3.1 Phạm vi (R-P)

- **R-P1.** Đúng 3 tab: "CV & điểm", "Giải thích", "Lịch sử". Thanh thao tác chỉ gồm 3 nút FR-H07 (Mời
  phỏng vấn, Từ chối, Trúng tuyển). Nút "Chấm điểm hồ sơ" (R-S) và "Xem CV gốc" (R-V) thuộc tab "CV &
  điểm", không thuộc thanh thao tác.
- **R-P2.** Không dựng tab rỗng, tab bị khoá hay nút bị khoá cho Sàng lọc (H10), Câu hỏi phỏng vấn
  (H13), Trao đổi (C06), Hỏi đáp CV (C08), Thêm vào kho (H15). FR nào tự thêm phần của FR đó.
- **R-P3.** Bỏ hẳn Sheet "Hồ sơ ứng viên" ở `ApplicationsTab.tsx`. Không tồn tại hai nơi xem hồ sơ đơn
  song song. Sau FR-H09, nút quyết định FR-H07 **chỉ** còn ở trang này.
- **R-P4.** Giữ nguyên ở danh sách theo Job: cột Hạng/Tổng điểm, `ScoringProgressHint`, nút "Xem CV
  gốc" (kể cả cách khoá hiện tại theo `resumeDownloadDisabledReason`), nút "Chấm điểm hồ sơ", bộ sắp
  xếp. Chỉ đổi nút "Xem hồ sơ" (mở Sheet) thành liên kết.

### 3.2 Quyền truy cập (R-Q)

- **R-Q1.** Mọi endpoint mới (mục 4) chỉ cho role HR (filter chain `/api/hr/**`, `SecurityConfig.java:53`)
  **và** đơn phải thuộc một Job của công ty mà HR đang đăng nhập sở hữu.
- **R-Q2.** Theo đúng quy ước hiện có của nhóm `/api/hr/applications/{id}/**` (mục 0.f): đơn không tồn
  tại → `404 APPLICATION_NOT_FOUND`; đơn tồn tại nhưng thuộc công ty khác → `403 FORBIDDEN`
  (`AccessDeniedException`).
- **R-Q3.** E1, E3, E4, E5 dùng **một** method nạp đơn có kiểm quyền dùng chung, theo khuôn
  `ApplicationStatusService.loadOwnedApplication` (kiểm công ty của HR trước → `404 COMPANY_NOT_FOUND`
  nếu chưa có; rồi đơn → 404; rồi Job của đơn → 403 nếu khác công ty). Không chép lại method này ở từng
  service mới. E2 dùng method có sẵn `ResumeHrService.loadOwnedApplication` (`ResumeHrService.java:96-108`).
- **R-Q4.** Không đổi hành vi kiểm quyền của các endpoint hiện có (`/status`, `/scoring-runs`,
  `/resume/download`, `/interview-invitation`). Endpoint mới và endpoint cũ cho cùng kết quả 403/404 với
  cùng một đơn.
- **R-Q5.** Ứng viên không gọi được bất kỳ endpoint nào của FR-H09 (403 ở filter chain). Không thêm
  endpoint nào dưới `/api/candidates/**`.

### 3.3 Dữ liệu từng phần (R-D)

- **R-D1. Đầu trang** đọc `job_applications` (gồm `cover_letter`) + `jobs.title` + `users.full_name` +
  `resumes.parse_status`/`parse_error`/`file_name`. Không chứa điểm.
- **R-D2. CV đã trích xuất** của **đúng** `job_applications.resume_id` (không phải CV chính hiện tại của
  ứng viên). Dùng lại nguyên DTO `ResumeParsedDataResponse`, không thêm/bớt field, không trả `raw_text`.
- **R-D3. Điểm** lấy từ **lượt DONE mới nhất** của đơn — đúng nguồn của danh sách theo Job
  (`ApplicationOwnerService.java:112-119`). Lượt mới nhất bất kể trạng thái chỉ dùng để hiện tiến độ,
  đọc qua endpoint có sẵn `GET /api/hr/applications/{id}/scoring-runs`.
- **R-D4. Tổng điểm và hạng** do Backend tính bằng **chính** code FR-H05 đang dùng cho danh sách
  (`assignRanks`/`sortByRank`, `ApplicationOwnerService.java:264-298`) — tách thành phần dùng chung, không
  viết công thức thứ hai. Hạng của một đơn ở trang này phải bằng hạng của đơn đó ở danh sách theo Job.
  Frontend không tự tính tổng hay hạng.
- **R-D5. Thứ tự tiêu chí** theo `rubric_snapshot` của chính lượt DONE đó (`buildCriterionScores`,
  `ApplicationOwnerService.java:236-262`), không đọc `rubric_criteria` hiện tại.
- **R-D6. Giải thích** lấy từ **cùng** lượt DONE với R-D3. Response mang `scoringRunId`; frontend không
  ghép giải thích của lượt này với điểm của lượt khác. `explanationStatus` tính đúng quy tắc hiện có
  (`ApplicationOwnerService.java:200-210`): chỉ khác `null` khi có lượt DONE mà chưa có giải thích;
  `FAILED` khi số lần thử ≥ `app.explanation.max-attempts`, ngược lại `PENDING`.
- **R-D7. Lịch sử** đọc `application_status_history` theo `changed_at` tăng dần, dùng lại DTO
  `ApplicationHistoryEntryResponse` (không có `changed_by`).
- **R-D8. Thư giới thiệu** trả nguyên văn như đã lưu (không cắt, không trim lại, không lọc ký tự). `null`
  khi ứng viên không nhập.

### 3.4 Trạng thái hiển thị của từng tab (R-T)

Nguồn trạng thái là field backend trả về; frontend không suy từ dữ liệu khác.

| Mã | Tab / phần | Điều kiện | Hiển thị |
|---|---|---|---|
| R-T1 | CV | `resumeParseStatus` ∈ {`PENDING`, `PROCESSING`} | "CV đang chờ trích xuất". Không gọi API CV đã trích xuất. Nút "Xem CV gốc" vẫn bấm được (R-V1) |
| R-T2 | CV | `resumeParseStatus = FAILED` | "Trích xuất CV thất bại" + câu mô tả trong `resumeParseError` (đã chuẩn hoá). Không gọi API CV đã trích xuất. Nút "Xem CV gốc" vẫn bấm được (R-V1) |
| R-T3 | CV | `resumeParseStatus = DONE` | Gọi API CV đã trích xuất và hiện nội dung |
| R-T3b | CV | `coverLetter` khác `null` và khác rỗng | Khối "Thư giới thiệu" (R-L). Không có nội dung → không hiện khối |
| R-T4 | Điểm | Chưa có lượt chấm nào | "Đơn này chưa được chấm điểm". Nút "Chấm điểm hồ sơ" theo R-S1 (khoá nếu CV chưa `DONE`) |
| R-T5 | Điểm | Lượt mới nhất `PENDING`/`RUNNING` (kể cả `RUNNING` đã có `finished_at`, chờ tổng hợp) | Badge tiến độ `ScoringRunStatusBadge` (N/M tiêu chí), tự cập nhật theo cơ chế poll có sẵn (`useScoringRunsQuery`, dừng sau 10 phút + nút "Tải lại"). Nút "Chấm điểm hồ sơ" theo R-S1: khi `finishedAt = null` nút hiện nhưng **bị khoá** kèm câu lý do "đang có một lượt chấm điểm chưa hoàn tất…"; khi `RUNNING` đã có `finished_at` thì theo đúng kết quả của hàm (hiện tại: mở) |
| R-T6 | Điểm | Lượt mới nhất `FAILED`, chưa từng có lượt DONE | "Chấm điểm thất bại" + `errorMessage` đã chuẩn hoá của lượt đó. Nút "Chấm điểm hồ sơ" theo R-S1 |
| R-T7 | Điểm | Có lượt DONE | Tổng điểm, hạng, `CriterionScoreBreakdown`. Nếu lượt mới nhất khác lượt DONE (đang chấm hoặc FAILED) thì hiện thêm dòng trạng thái của lượt mới nhất phía trên, nói rõ điểm bên dưới là của lượt hoàn tất trước đó. Nút "Chấm điểm hồ sơ" theo R-S1 |
| R-T8 | Giải thích | Chưa có lượt DONE | "Chưa có báo cáo giải thích vì đơn chưa có lượt chấm hoàn tất" |
| R-T9 | Giải thích | `explanationStatus = PENDING` | Câu có sẵn của `ExplanationReport` ("…đang được xử lý") + nút "Tải lại" (R-T14) |
| R-T10 | Giải thích | `explanationStatus = FAILED` | Câu có sẵn của `ExplanationReport` ("Không tạo được…") |
| R-T11 | Giải thích | Có `explanation` | `ExplanationReport` nguyên trạng (có dòng "không phải khuyến nghị tuyển dụng") |
| R-T12 | Lịch sử | Đang tải / lỗi / rỗng / có dữ liệu | Theo `ApplicationHistoryTimeline` |
| R-T13 | Cả trang | Đầu trang (E1) trả 403, 404, hoặc 400 (id trên URL không phải UUID — Spring trả 400 mặc định vì `GlobalExceptionHandler` không có handler cho `MethodArgumentTypeMismatchException`) | Cùng một thông điệp "Không tìm thấy đơn ứng tuyển" (UI_GUIDE mục 5), không gọi E2–E5 |

- **R-T14.** Khi lượt chấm đang chạy chuyển sang kết thúc (poll thấy `finishedAt` khác `null` hoặc
  trạng thái `DONE`/`FAILED`), frontend làm mới dữ liệu đầu trang, điểm và giải thích. Tab Giải thích
  **không** tự poll khi `explanationStatus = PENDING`; thay vào đó có nút "Tải lại" gọi lại E4. Lý do:
  sau R-S, HR có thể chấm ngay trên trang và ở lại chờ báo cáo; poll vô hạn không được phép, nút tải
  lại là đủ. Mở lại tab cũng tải lại.

### 3.5 Nút "Chấm điểm hồ sơ" (R-S)

- **R-S1.** Một luật duy nhất: nút **luôn hiện** ở phần điểm của tab "CV & điểm", ở mọi trạng thái R-T4 →
  R-T7. Khoá/mở và câu lý do lấy **nguyên** kết quả `scoringDisabledReason`, giống hệt danh sách theo Job.
  Trang này không có điều kiện ẩn hay khoá riêng.
- **R-S2.** Dùng lại `POST /api/hr/applications/{id}/scoring-runs` và `scoringDisabledReason`
  (`features/scoring/scoringRules.ts`). Điều kiện khoá và nội dung câu lý do lấy **nguyên** từ
  `scoringDisabledReason` — không chép lại điều kiện, không viết câu mới. Cách hiển thị câu lý do ở trang
  này theo `UI.md` mục 9 (hiện thành chữ dưới nút, gắn vào nút bằng `aria-describedby`).
- **R-S3.** Đầu vào của `scoringDisabledReason`: `resumeParseStatus` từ E1; `latestScoringRunStatus`/
  `latestScoringRunFinishedAt` từ phần tử đầu của `GET /scoring-runs`.
- **R-S4.** Sau khi tạo lượt thành công: làm mới `GET /scoring-runs` của đơn (bắt đầu poll), E3, E4, danh
  sách theo Job và `/hr/candidates`. Lỗi (409 đang có lượt chưa xong, 404…) hiện tại chỗ như danh sách.
- **R-S5.** Không tự tạo lượt chấm khi mở trang hay khi CV vừa trích xuất xong. Không khoá nút vì trạng
  thái đơn (đơn `REJECTED`/`WITHDRAWN` vẫn chấm được như danh sách hiện tại).

### 3.6 Nút "Xem CV gốc" (R-V)

- **R-V1.** Ở trang này nút **luôn bấm được**, bất kể `resumeParseStatus`. Gọi
  `GET /api/hr/applications/{id}/resume/download` có sẵn, tải bằng blob như `ApplicationsTab.tsx:175-193`.
- **R-V2.** Khác biệt có chủ đích với danh sách theo Job (R-P4, danh sách vẫn khoá khi CV chưa `DONE`):
  file gốc luôn tồn tại từ lúc upload (`ApplicationsTab.tsx:46-54`), và ở R-T1/R-T2 tải file gốc là cách
  **duy nhất** để HR đọc CV trên trang này. Khoá ở danh sách chỉ là lựa chọn UX, không phải ràng buộc kỹ
  thuật.
- **R-V3.** Lỗi tải file (vd file mất trên đĩa → 404 `RESUME_NOT_FOUND`) hiện sau khi bấm, câu
  "Tải CV gốc thất bại, vui lòng thử lại." như hiện có.

### 3.7 Thư giới thiệu (R-L)

- **R-L1.** Hiện trong tab "CV & điểm", phần CV, chỉ khi `coverLetter` có nội dung (R-T3b). Nhãn "Thư giới
  thiệu", đúng nhãn ứng viên thấy ở form nộp đơn (`JobApplyForm.tsx:115`).
- **R-L2.** Hiển thị dạng văn bản thuần, giữ nguyên xuống dòng (`whitespace-pre-wrap`), tự ngắt từ dài.
  Không render HTML/Markdown, không dùng `dangerouslySetInnerHTML`, không tự nhận dạng liên kết.
- **R-L3.** Đây là lời của ứng viên, không phải nội dung AI — không đặt trong khối "Do AI tạo", không
  dùng kiểu evidence.

### 3.8 Thanh thao tác (R-A)

- **R-A1.** Nút hiện theo **đúng** máy trạng thái FR-H07 (`ApplicationStatusService.java:33-35`, phía UI
  là `nextActionsFor`, `ApplicationsTab.tsx:67-75`): `PENDING` → Mời phỏng vấn, Từ chối;
  `INTERVIEW_INVITED` → Trúng tuyển, Từ chối; `HIRED`/`REJECTED`/`WITHDRAWN` → không có nút, thay bằng
  một dòng chữ trung tính.
- **R-A2.** Nút chỉ phụ thuộc trạng thái đơn. **Không** khoá hay ẩn nút vì đơn chưa chấm, đang chấm,
  chấm lỗi, hay CV chưa trích xuất (`ApplicationsTab.tsx:242-248` đã ghi lý do).
- **R-A3.** Mời phỏng vấn → `InterviewInvitationDialog` hiện có → `POST /api/hr/applications/{id}/interview-invitation`.
  Từ chối / Trúng tuyển → hộp xác nhận hiện có (cùng tiêu đề, nội dung, nhãn nút trong
  `CONFIRM_STATUS_COPY`) → `PATCH /api/hr/applications/{id}/status`. Không thêm endpoint, không đổi
  bảng chuyển trạng thái.
- **R-A4.** Hộp xác nhận được **chuyển** khỏi `ApplicationsTab.tsx` thành một component trong
  `features/` để trang này dùng; nội dung chữ và hành vi không đổi. `InterviewInvitationDialog` chỉ được
  nới kiểu prop thành dạng tối thiểu (`id`, `candidateName`) và cho phép trang truyền thêm danh sách query
  cần làm mới; form, kiểm tra và API không đổi.
- **R-A5.** Sau thao tác thành công: làm mới đầu trang, tab Lịch sử, danh sách theo Job
  (`hrApplicationsKeyPrefix(jobId)`) và danh sách `/hr/candidates`. Lỗi 400/409 hiện trong hộp thoại như
  hiện tại.

### 3.9 Điểm vào (R-E)

- **R-E1.** Tab Ứng viên của `/hr/jobs/:id/edit`: nút "Xem hồ sơ" thành liên kết tới
  `/hr/applications/{id}` (R-P3).
- **R-E2.** `/hr/candidates`: thêm liên kết "Xem hồ sơ" ở mỗi dòng. Giữ "Lịch sử đánh giá" (FR-H08) và
  "Chấm điểm hồ sơ".
- **R-E3.** Liên kết quay lại ở đầu trang luôn trỏ `/hr/jobs/{jobId}/edit?tab=applications`.
- **R-E4.** Route `/hr/applications/:id` nằm trong nhóm `ProtectedRoute(HR)` + `RequireCompany`
  (`App.tsx:80-94`). Tab đang mở lưu ở `?tab=` (`cv` mặc định, `explanation`, `history`), giống trang sửa Job.

### 3.10 Thông báo (R-N)

- **R-N1.** Ba thông báo HR mới tạo sau khi FR-H09 chạy (`APPLICATION_SUBMITTED`, `APPLICATION_WITHDRAWN`,
  `SCORING_FINISHED`) có `link = /hr/applications/{applicationId}`. Thông báo của ứng viên giữ nguyên
  `/candidate/applications` (FR-U08 sẽ xử lý).
- **R-N2.** Thông báo đã có trong DB giữ nguyên link cũ `/hr/jobs`. Không viết migration, không sửa dữ liệu
  seed.
- **R-N3.** Không đổi tiêu đề, nội dung, kiểu, email của thông báo — chỉ đổi link.
- **R-N4.** Cùng đợt code đổi link: sửa câu comment mục 13 của `db/seed/seed-test.sql` (hiện ghi "khớp 1-1
  với sự kiện thật (`NotificationEventListener` + `NotificationContentBuilder`)") cho đúng: các dòng seed
  mang link cũ `/hr/jobs`, cố ý giữ để soát R-N2. Chỉ sửa comment, không sửa dòng `INSERT` nào.

### 3.11 Ràng buộc hiển thị (R-G)

- **R-G1.** Áp đủ CLAUDE.md §8 và UI_GUIDE mục 4: không nhãn Đạt/Không đạt/Phù hợp, không màu theo
  ngưỡng điểm, không icon ✓/✗, không in đậm theo hạng; badge trạng thái dùng `ApplicationStatusBadge`.
- **R-G2.** Mọi điểm trên trang mở ra được evidence: điểm từng tiêu chí qua `CriterionScoreBreakdown`;
  tổng điểm đặt cùng khối với danh sách tiêu chí, không đứng riêng ở đầu trang.
- **R-G3.** Dùng lại `CriterionScoreBreakdown`, `ExplanationReport`, `ApplicationHistoryTimeline`,
  `ScoringRunStatusBadge`, `ParseStatusBadge`, `ApplicationStatusBadge`, phần thân của
  `ResumeParsedDataDialog` (gồm `CareerOverviewSection`) — không viết lại. Được phép sửa tối
  thiểu để nhận dữ liệu từ ngoài (R-C3), không đổi giao diện của chúng ở màn hình cũ.

### 3.12 Thay đổi component dùng chung (R-C)

- **R-C1.** Không sửa file trong `components/ui`.
- **R-C2.** `ApplicationsTab.tsx` sau khi đổi: không còn `Sheet`, `CriterionScoreBreakdown`,
  `ExplanationReport`, `InterviewInvitationDialog`, hộp xác nhận.
- **R-C3.** `ApplicationHistoryTimeline` tách phần hiển thị khỏi hook: trang ứng viên vẫn dùng
  `useApplicationHistoryQuery`, trang HR dùng hook mới gọi endpoint HR. Giao diện phía ứng viên không đổi.
- **R-C4.** Logic tải blob CV gốc (`ApplicationsTab.tsx:175-193`) được tách ra dùng chung cho danh sách và
  trang này; không chép thành bản thứ hai. Điều kiện khoá **không** nằm trong phần dùng chung (R-V2).
- **R-C5.** Phần thân hiển thị của `ResumeParsedDataDialog` (`features/resumes/ResumeParsedDataDialog.tsx:195-221`)
  tách thành component nhận `data` từ ngoài; hộp thoại phía ứng viên vẫn dùng `useResumeParsedDataQuery`,
  trang HR dùng hook mới gọi E2. Giao diện phía ứng viên không đổi.

## 4. Dữ liệu & quyền truy cập

Không migration. Không đổi entity. Mọi endpoint mới là GET, chỉ đọc, không `@Transactional` ghi.

| # | Endpoint | Tab | Response | Lỗi |
|---|---|---|---|---|
| E1 | `GET /api/hr/applications/{applicationId}` | Đầu trang | `ApplicationHrDetailResponse` | 401 `UNAUTHENTICATED`; 403 `FORBIDDEN` (không phải HR, hoặc đơn thuộc công ty khác); 404 `COMPANY_NOT_FOUND`; 404 `APPLICATION_NOT_FOUND` (đơn không tồn tại) |
| E2 | `GET /api/hr/applications/{applicationId}/resume/parsed` | CV & điểm | `ResumeParsedDataResponse` (dùng lại) | như E1 + 404 `RESUME_PARSED_DATA_NOT_FOUND` khi chưa có dữ liệu trích xuất |
| E3 | `GET /api/hr/applications/{applicationId}/scores` | CV & điểm | `ApplicationScoresResponse` | như E1 |
| E4 | `GET /api/hr/applications/{applicationId}/explanation` | Giải thích | `ApplicationExplanationResponse` | như E1 |
| E5 | `GET /api/hr/applications/{applicationId}/history` | Lịch sử | `List<ApplicationHistoryEntryResponse>` (dùng lại) | như E1 |

Endpoint có sẵn được trang gọi, không đổi: `GET`/`POST /scoring-runs`, `GET /resume/download`,
`PATCH /status`, `GET /interview-invitation/preview`, `POST /interview-invitation`.

E1, E3, E4, E5 đặt ở package `jobapplication/` (controller mới); E2 thêm vào `ResumeHrController`.

```java
// E1
record ApplicationHrDetailResponse(
        UUID id,
        UUID jobId,
        String jobTitle,
        String candidateName,
        ApplicationStatus status,
        Instant appliedAt,
        String coverLetter,        // nguyen van, null khi khong nhap (R-D8)
        ParseStatus resumeParseStatus,
        String resumeParseError,   // "MA: mo ta" da chuan hoa hoac null
        String resumeFileName) {}

// E3 - moi field null/rong khi don chua co luot DONE
record ApplicationScoresResponse(
        UUID scoringRunId,         // luot DONE moi nhat
        Instant scoredAt,          // finished_at cua luot do
        BigDecimal totalScore,
        Integer rank,              // cung gia tri voi danh sach theo Job (R-D4)
        List<ApplicationHrListItemResponse.CriterionScoreItem> criterionScores) {}

// E4
record ApplicationExplanationResponse(
        UUID scoringRunId,         // cung luot voi E3
        Instant scoredAt,          // finished_at cua luot do (= scoredAt cua E3), cho cau "Bao cao cua luot cham hoan tat ngay ..." (UI.md muc 7)
        ApplicationHrListItemResponse.ExplanationStatus explanationStatus,
        ApplicationHrListItemResponse.Explanation explanation) {}
```

Không DTO nào có field tên `verdict`, `label`, `isQualified`, `passed`, `recommendation` (CLAUDE.md §7).

## 5. AI

Không gọi AI mới. Chỉ hiển thị kết quả đã lưu của FR-C04 (`resume_parsed_data`), FR-H04
(`criterion_scores`) và FR-H06 (`score_explanations`). Nút "Chấm điểm hồ sơ" chỉ tạo lượt chấm qua API
có sẵn của FR-H04 (job nền chấm sau, không đồng bộ). Không tạo lượt chấm tự động khi mở trang. Không
thuộc danh sách ngoại lệ K3; không dùng K1–K4.

## 6. Ngoài phạm vi

- Tab Sàng lọc (FR-H10), Câu hỏi phỏng vấn (FR-H13), Trao đổi (FR-C06), Hỏi đáp CV (FR-C08); nút Thêm vào
  kho (FR-H15); giấy mời nhiều khung giờ và gửi lại giấy mời (FR-H12); chế độ ẩn danh (FR-H16).
- Che thư giới thiệu (và các thông tin nhân thân khác) ở chế độ ẩn danh — thuộc FR-H16.
- Lịch sử các lượt chấm (đã có ở "Lịch sử đánh giá" của `/hr/candidates`, FR-H08).
- Chuyển sang đơn trước/sau trong danh sách từ trang này.
- Trang chi tiết đơn phía ứng viên và đổi link thông báo của ứng viên (FR-U08).
- Sửa link thông báo cũ trong DB hoặc dữ liệu seed.
- Đổi cách khoá nút "Xem CV gốc" ở danh sách theo Job.
- Giới hạn độ dài thư giới thiệu (hiện không có ở cả form lẫn backend).
- Chuẩn hoá lỗi 400 cho path variable sai định dạng (vd `/api/hr/applications/abc`). Không thêm handler
  backend ở FR này; giao diện coi 400 từ E1 như "Không tìm thấy" (R-T13).
- Ghi chú "CV đã được trích xuất lại sau lượt chấm". Không hiện vì sẽ sai sự thật: chấm điểm chỉ đọc
  `resume_parsed_data.raw_text` (`ScoringRunOrchestrator.java:73-76`, truyền vào
  `criterionScoringService.score(criterion, rawText)` ở `:94`), còn trích xuất lại không đổi `raw_text`
  (`ResumeReparseStateService.java:43`; `ResumeParsedDataRepository.touchAfterReparse`, `:74-81`, chỉ
  đổi `parsed_at` và `embedding`). Điểm và evidence vì vậy không phụ thuộc phần dữ liệu bị trích xuất lại.

## 7. Xong khi

### 7.1 Test backend (Testcontainers, full `./mvnw test` xanh)

| # | Test | Loại |
|---|---|---|
| T1 | E1 với đơn của công ty mình → 200, đủ field R-D1 | dương |
| T2 | E1, E2, E3, E4, E5 với đơn của công ty khác → 403 `FORBIDDEN`; với UUID không tồn tại → 404 `APPLICATION_NOT_FOUND` | âm |
| T3 | E1 bằng token ứng viên → 403; không token → 401 `UNAUTHENTICATED` | âm |
| T4 | E1 bằng HR chưa có công ty → 404 `COMPANY_NOT_FOUND` | âm |
| T5 | E2 khi CV `DONE` → 200, nội dung bằng response của `/api/candidates/resumes/{id}/parsed` cho cùng CV; khi CV `PENDING`/`FAILED` → 404 `RESUME_PARSED_DATA_NOT_FOUND` | dương + âm |
| T6 | E2 trả CV của `job_applications.resume_id`, không phải CV chính hiện tại, khi ứng viên đã đổi CV chính sau khi nộp | dương |
| T7 | E3: chưa có lượt nào → mọi field null, `criterionScores` rỗng; chỉ có lượt FAILED → như trên; lượt DONE cũ + lượt FAILED mới hơn → điểm của lượt DONE | dương + âm |
| T8 | E3: Job có 4 đơn hoà điểm kiểu 1-2-2-4 và 1 đơn chưa chấm → `rank` của từng đơn bằng `rank` trong `GET /api/hr/jobs/{jobId}/applications`; đơn chưa chấm `rank = null` | dương |
| T9 | E4: số lần thử sinh giải thích = `max-attempts − 1` → `PENDING`; `= max-attempts` → `FAILED`; `= max-attempts + 1` → `FAILED`; có giải thích → `explanationStatus = null`, `scoringRunId` và `scoredAt` bằng E3 | biên |
| T10 | E5: thứ tự `changed_at` tăng dần; đơn công ty khác → 403 | dương + âm |
| T11 | JSON của E1, E3, E4 không chứa khoá `verdict`/`label`/`isQualified`/`passed`/`recommendation` | âm |
| T12 | Nộp đơn, rút đơn, tổng hợp điểm xong → thông báo HR có `link = /hr/applications/{applicationId}`; thông báo đổi trạng thái của ứng viên vẫn `/candidate/applications` | dương |
| T13 | Cùng một đơn của công ty khác: E1–E5 và `GET /scoring-runs`, `GET /resume/download`, `PATCH /status` (body `{"status":"REJECTED"}`), `GET /interview-invitation/preview` đều trả 403 (R-Q4 — mới và cũ nhất quán). Body `PATCH` phải là `REJECTED`: với `INTERVIEW_INVITED`, controller trả 400 **trước** khi kiểm quyền (`ApplicationStatusController.java:39-43`), test sẽ thấy 400 vì sai lý do | âm |
| T14 | E1 `coverLetter`: đơn có thư nhiều dòng chứa `<b>x</b>` và ký tự `&` → trả nguyên văn, giữ `\n`, không escape/lọc; đơn không nhập → `null` | dương + âm |

Nút "Chấm điểm hồ sơ" (R-S) và "Xem CV gốc" (R-V) dùng endpoint có sẵn, không đổi backend — đã có test
của FR-H04/FR-H06; kiểm phần giao diện ở 7.3 và 7.4. Tầng test không gọi LLM thật (CLAUDE.md §7); FR này
không cần stub `ChatModel` mới.

### 7.2 Kiểm bằng curl (Phase 2.2 "Xong khi") — Windows PowerShell 5.1

Nạp cả seed demo và seed test (mục 7.4). Backend chạy ở `localhost:8080`. Dán **cả khối** vào **một**
cửa sổ PowerShell (hàm và biến chỉ sống trong phiên đó).

```powershell
$base = 'http://localhost:8080'

function Get-Token([string]$Email, [string]$Password) {
    $body = @{ email = $Email; password = $Password } | ConvertTo-Json
    (Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' -Body $body).accessToken
}

# In "<ma HTTP> <duong dan> <than loi neu co>" cho ca truong hop thanh cong lan 401/403/404.
function Test-Get([string]$Path, [string]$Token) {
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Method Get -Uri "$base$Path" -Headers $headers
        '{0} {1}' -f [int]$r.StatusCode, $Path
    } catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { 'KHONG KET NOI DUOC {0}' -f $Path; return }
        # PowerShell 5.1 da doc san than loi vao ErrorDetails voi response 401/403 cua Spring Security
        # (luong response khi do da rong) - doc ErrorDetails truoc, rong moi doc luong response.
        $text = $_.ErrorDetails.Message
        if (-not $text) {
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $text = $reader.ReadToEnd()
        }
        '{0} {1} {2}' -f [int]$resp.StatusCode, $Path, $text
    }
}

$tokenTest = Get-Token 'minhdathr@gmail.com' '12345678'
$tokenDemo = Get-Token 'hr@demo.local' 'Demo1234'
$tokenUv   = Get-Token 'quochuyuv@gmail.com' '12345678'

$a1      = '/api/hr/applications/e8000000-0000-0000-0000-000000000001'  # don A1, cong ty test
$demoApp = '/api/hr/applications/3ec35ae2-c8ae-4100-a819-3b59f6948707'  # Tran Minh Hoang, cong ty demo
$missing = '/api/hr/applications/00000000-0000-0000-0000-000000000000'
$suffixes = @('', '/resume/parsed', '/scores', '/explanation', '/history')

'--- 1. Duong: HR test doc don cua minh (mong doi 200 x5)'
foreach ($s in $suffixes) { Test-Get "$a1$s" $tokenTest }

'--- 2. Am: HR test doc don cong ty demo (mong doi 403 FORBIDDEN x5)'
foreach ($s in $suffixes) { Test-Get "$demoApp$s" $tokenTest }

'--- 3. Am: HR demo doc don cong ty test (mong doi 403 FORBIDDEN)'
Test-Get $a1 $tokenDemo

'--- 4. Am: don khong ton tai (mong doi 404 APPLICATION_NOT_FOUND x5)'
foreach ($s in $suffixes) { Test-Get "$missing$s" $tokenTest }

'--- 5. Am: ung vien goi endpoint HR (mong doi 403)'
Test-Get $a1 $tokenUv

'--- 6. Am: khong token (mong doi 401 UNAUTHENTICATED)'
Test-Get $a1 $null
```

Mong đợi: nhóm 1 năm dòng `200` (riêng `/resume/parsed` của A1 là `200` vì CV R3 đã `DONE`); nhóm 2 năm
dòng `403 … {"error":"FORBIDDEN",…}`; nhóm 3 một dòng `403`; nhóm 4 năm dòng
`404 … {"error":"APPLICATION_NOT_FOUND",…}`; nhóm 5 `403`; nhóm 6 `401 … {"error":"UNAUTHENTICATED",…}`.

**Bắt buộc ở đợt code cuối:** chạy thật khối PowerShell trên (sau khi nạp đủ hai bộ seed và bật backend),
dán **nguyên văn** output vào báo cáo đợt. Lệnh nào sai cú pháp hoặc cho kết quả khác "Mong đợi" vì lệnh
viết sai (không phải vì code sai) thì dừng, đề xuất sửa mục 7.2 này và chờ duyệt lại theo quy trình
CLAUDE.md §6 — không tự sửa lệnh cho "qua".

### 7.3 Frontend

- `npm run build` + `npm run lint` sạch.
- `rg -n "Sheet|CriterionScoreBreakdown|ExplanationReport|InterviewInvitationDialog" frontend/src/features/scoring/ApplicationsTab.tsx` → 0 dòng.
- `rg -n "api/candidates|useApplicationHistoryQuery|useResumeParsedDataQuery" frontend/src/pages/HrApplicationDetailPage.tsx frontend/src/features/applicationDetail` → 0 dòng
  (trang HR không gọi hook/endpoint phía ứng viên; tên file/thư mục chốt ở UI.md mục 5).
- `rg -n "dangerouslySetInnerHTML" frontend/src` → 0 dòng (R-L2).
- `rg -n "export function scoringDisabledReason" frontend/src` → đúng 1 dòng, ở
  `features/scoring/scoringRules.ts` (R-S2).
- Lệnh `rg` token cũ của Phase 2.1b (`docs/ROADMAP.md`, mục "Xong khi" của Phase 2.1b) vẫn trả 0 dòng.

### 7.4 Soát tay (desktop ≥ `lg` và điện thoại 375px)

Phải nạp **cả hai** bộ seed: bộ demo có lượt chấm DONE + giải thích; bộ test cố ý không có
`criterion_scores`/`score_explanations` nhưng có các trạng thái chưa chấm, đang chấm, chấm lỗi, trích
xuất lỗi. Thứ tự nạp theo `db/seed/README.md` mục 8.1 (demo đủ 4 bước trước, rồi bộ test).

| Trạng thái cần thấy | Tài khoản | Đơn (id) | Mong đợi |
|---|---|---|---|
| Có điểm + evidence + giải thích | `hr@demo.local` / `Demo1234` | Trần Minh Hoàng — Senior Java Backend Developer (`3ec35ae2-c8ae-4100-a819-3b59f6948707`) | R-T3, R-T7, R-T11; mở một tiêu chí thấy evidence; hạng bằng hạng ở tab Ứng viên; nút "Chấm điểm hồ sơ" mở; không có khối thư giới thiệu (demo không có thư) |
| Chấm lỗi, chưa từng có điểm | `hr@demo.local` | Nguyễn Thị Thu Hà — Senior Java (`967d5c84-1519-4a70-8bed-6252781ac052`, 3 lượt FAILED `EVIDENCE_NOT_VERIFIED`) | R-T6 (nút "Chấm điểm hồ sơ" mở), R-T8 |
| Nhiều lượt DONE | `hr@demo.local` | Nguyễn Thị Thu Hà — Kế toán tổng hợp (`cd3857e1-71b4-4f46-bd81-b04c897e2cd0`) | Điểm và giải thích cùng lượt mới nhất |
| Đơn đã rút, không còn nút quyết định | `hr@demo.local` | Lê Văn Đức — QA (`1029b885-ddae-4417-8e0c-8284d6198f79`) | R-A1 dòng chữ trung tính; Lịch sử có bước rút đơn |
| Thông báo cũ link `/hr/jobs` vẫn mở được | `hr@demo.local` | Chuông thông báo | Mở `/hr/jobs`, không lỗi (R-N2) |
| Đang chấm (lượt PENDING đóng băng) | `minhdathr@gmail.com` / `12345678` | A1 Quốc Huy — Chăm sóc khách hàng (`e8000000-0000-0000-0000-000000000001`) | R-T5 (nút "Chấm điểm hồ sơ" hiện nhưng bị khoá, `title` = câu "Đơn này đang có một lượt chấm điểm chưa hoàn tất…" — giống tab Ứng viên của tin CSKH); CV R3 `DONE` hiện nội dung (R-T3); khối "Thư giới thiệu" hiện (R-T3b); nút Mời phỏng vấn + Từ chối |
| Chấm lỗi, đã mời phỏng vấn | `minhdathr@gmail.com` | A2 Quốc Huy — Hành chính (`e8000000-0000-0000-0000-000000000002`, `LLM_RETRY_EXHAUSTED`) | R-T6, nút "Chấm điểm hồ sơ" mở; không có khối thư giới thiệu; nút Trúng tuyển + Từ chối |
| CV đang chờ trích xuất, chưa chấm | `minhdathr@gmail.com` | A6 Lê Thị Lan — CSKH (`e8000000-0000-0000-0000-000000000006`) | R-T1, R-T4 (nút "Chấm điểm hồ sơ" bị khoá, `title` = câu "CV của ứng viên chưa được AI trích xuất xong…"), R-T8; "Xem CV gốc" tải được (R-V1); nút quyết định vẫn bấm được (R-A2) |
| Trích xuất lỗi | `minhdathr@gmail.com` | A8 Phạm Văn Khoa — Hành chính (`e8000000-0000-0000-0000-000000000008`, `EXTRACT_CORRUPT`) | R-T2; "Xem CV gốc" bấm được và tải về file (R-V1) — so với tab Ứng viên của tin Hành chính, nút này ở danh sách vẫn bị khoá (R-P4) |
| Trạng thái cuối HIRED, lịch sử đủ bước, có thư | `minhdathr@gmail.com` | A4 Quốc Huy (`e8000000-0000-0000-0000-000000000004`) | Lịch sử Nộp đơn → Đã mời PV → Trúng tuyển; CV R1 lỗi `EXTRACT_EMPTY`; khối "Thư giới thiệu" hiện |
| Không tìm thấy (403) | `minhdathr@gmail.com` | Mở `/hr/applications/3ec35ae2-c8ae-4100-a819-3b59f6948707` | R-T13 |
| Không tìm thấy (404) | `minhdathr@gmail.com` | Mở `/hr/applications/00000000-0000-0000-0000-000000000000` | R-T13, cùng thông điệp với dòng trên |
| Không tìm thấy (400, id sai định dạng) | `minhdathr@gmail.com` | Mở `/hr/applications/abc` | R-T13, cùng thông điệp với hai dòng trên; tab Network không có lời gọi E2–E5 |
| Thao tác + cập nhật tại chỗ | `minhdathr@gmail.com` | A8 → Từ chối | Badge, thanh thao tác, Lịch sử đổi ngay (R-A5); xong chạy `reset-test-data.sql` |
| Chấm ngay trên trang | `hr@demo.local` | Nguyễn Thị Thu Hà — Senior Java (`967d5c84-…`) → "Chấm điểm hồ sơ" | **Gọi Anthropic thật.** Nút chuyển sang khoá kèm câu lý do, badge tiến độ chạy (R-T5); khi xong điểm/giải thích tự làm mới (R-T14); nếu giải thích còn `PENDING` thì nút "Tải lại" ở tab Giải thích lấy được báo cáo (R-T9). Bỏ qua dòng này nếu không muốn tốn lượt gọi API |
| Điểm vào | cả hai | Tab Ứng viên của Job, `/hr/candidates`, thông báo mới | R-E1–R-E3 |

Các trạng thái **không có trong seed nào** (không được làm giả dữ liệu AI để soát): giải thích `FAILED`
(T9 bao phủ); "chưa chấm nhưng CV đã `DONE`" (T7 bao phủ); thư giới thiệu nhiều dòng (T14 bao phủ).
Bấm "Chấm điểm hồ sơ" hay "Thử lại" khi soát sẽ gọi
API Anthropic thật (`db/seed/README.md` mục 8.5).

## 8. AI hay làm sai (dành cho người code)

- Trả 404 cho đơn công ty khác ở endpoint mới "cho an toàn hơn" — sai quy ước của nhóm route (R-Q2,
  T13); hoặc chép method nạp đơn vào từng service mới thay vì dùng chung (R-Q3).
- Tính lại tổng điểm/hạng ở frontend, hoặc viết hàm xếp hạng thứ hai ở backend (R-D4).
- Lấy điểm từ lượt mới nhất (có thể FAILED/đang chấm) thay vì lượt DONE mới nhất; lấy giải thích từ lượt
  khác lượt điểm (R-D3, R-D6).
- Đọc CV chính hiện tại của ứng viên thay vì `job_applications.resume_id` (R-D2).
- Thêm `rawText`, `model`, `promptVersion` vào `ResumeParsedDataResponse` hoặc tạo DTO CV riêng cho HR.
- Viết lại điều kiện khoá nút "Chấm điểm hồ sơ" thay vì gọi `scoringDisabledReason` (R-S2); thêm điều kiện
  ẩn/khoá riêng của trang (vd ẩn khi đang chấm, khoá khi đơn đã có kết quả cuối) (R-S1, R-S5).
- Thêm ghi chú "CV đã được trích xuất lại sau lượt chấm" (mục 6 — sai sự thật vì điểm chỉ dựa trên `raw_text`).
- Khoá "Xem CV gốc" ở trang này theo thói quen của danh sách, hoặc mở khoá luôn ở danh sách (R-V1, R-P4).
- Render thư giới thiệu bằng HTML, trim/cắt bớt, hoặc đặt trong khối "Do AI tạo" (R-L).
- Dùng `ApplicationHistoryTimeline` nguyên trạng → gọi `/api/candidates/...` từ trang HR → 403 (R-C3).
- Tự poll tab Giải thích khi `PENDING` (R-T14).
- Dựng tab rỗng/nút khoá cho FR sau "cho đủ bố cục" (R-P2).
- Khoá nút Mời phỏng vấn khi đơn chưa chấm (R-A2).
- Giữ Sheet song song với trang mới (R-P3).
- Mutation chỉ làm mới danh sách theo Job → đầu trang và tab Lịch sử không đổi sau thao tác (R-A5, R-S4).
- Viết migration hoặc sửa dòng `INSERT` trong seed để đổi link thông báo cũ (R-N2, R-N4).
- Tự tạo lượt chấm hoặc gọi LLM khi HR mở trang (mục 5).
- Tô màu tổng điểm theo ngưỡng, in đậm hạng 1, chip đỏ cho "Tiêu chí chưa thể hiện" (R-G1).
- Đặt route ngoài `RequireCompany`.

## 9. Điểm còn mở

Không còn điểm mở.
