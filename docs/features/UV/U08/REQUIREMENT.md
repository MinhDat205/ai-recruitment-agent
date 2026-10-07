# FR-U08 — Trang chi tiết đơn ứng tuyển

> Trạng thái: ĐÃ HOÀN THÀNH (07/10/2026). Duyệt: 07/10/2026.

- Nhóm: Ứng viên
- Tóm tắt: Một màn hình `/candidate/applications/:id` cho một đơn của chính ứng viên: tab "Thông tin
  đơn" (giấy mời, tóm tắt tin tuyển dụng, CV đã nộp, thư giới thiệu, CV đã trích xuất) và tab "Lịch sử";
  nút Rút đơn. Thay các hộp thoại "Xem lịch sử", "Xem giấy mời" và nút "Rút đơn" ở trang danh sách.
- Phụ thuộc: FR-U03, FR-U06 — cả 2 đều **Đã hoàn thành** (`docs/SRS.md` mục 0).
- Nhánh: `feat/fr-u08-application-detail`, **xếp chồng lên `feat/fr-h09-application-detail`** (FR-H09 chưa
  merge vào `main`). Dùng lại các phần FR-H09 đã tách: `ApplicationHistoryTimeline` nhận dữ liệu qua prop,
  `ResumeParsedDataView`, `CoverLetterBlock`, `lib/date.ts`. Nếu FR-H09 đổi trước khi merge thì rebase và
  soát lại mục 0 của file này.
- Mở rộng: FR-U03 (trang danh sách đơn), FR-U06 (Rút đơn chuyển sang trang chi tiết), FR-C03 (link thông
  báo đổi trạng thái của ứng viên).
- Chạm màn hình cũ, **không đổi giao diện**: thẻ việc làm (`JobCard`), `/jobs/:id`, `/hr/jobs` — chỉ gộp hàm
  định dạng lương thành một (R-C4).

## 0. Đã đối chiếu code trước khi viết (CLAUDE.md §6)

### (a) Hiện trạng phía ứng viên

| # | Điều đã kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| a1 | Chưa có `GET /api/candidates/applications/{id}` | **Đúng** | `ApplicationCandidateController.java:33-57` chỉ có POST, GET danh sách, `/{id}/history`, `/{id}/withdraw`. Giấy mời: `InterviewInvitationCandidateController.java:15,24` |
| a2 | DTO danh sách không có `resumeId`, `coverLetter` | **Đúng** | `ApplicationSummaryResponse.java:7-14` (7 field). `coverLetter`/`resumeId` chỉ có trong `ApplicationResponse` (`ApplicationResponse.java:7-16`), trả lúc nộp/rút đơn |
| a3 | Trang danh sách có 3 hộp thoại và 3 nút trên mỗi dòng | **Đúng** | `CandidateApplicationsPage.tsx`: "Xem lịch sử" `:178`, "Xem giấy mời" `:181-190`, "Rút đơn" `:191-200`; phần thân giấy mời `:51-93`; hộp xác nhận rút đơn `:221-244` (viết thẳng trong trang) |
| a4 | Quy tắc hiện nút giấy mời | `INTERVIEW_INVITED`, `HIRED`, `REJECTED` | `CandidateApplicationsPage.tsx:113` (`INVITATION_VIEWABLE_STATUSES`) |
| a5 | Quy tắc rút đơn (FR-U06) | Chỉ `PENDING`/`INTERVIEW_INVITED`; sai → 409 `APPLICATION_NOT_WITHDRAWABLE` | `ApplicationService.java:97-100`, `GlobalExceptionHandler.java:180-184`; frontend `CandidateApplicationsPage.tsx:108` |
| a6 | Mutation rút đơn chỉ làm mới danh sách | **Đúng** | `features/applications/queries.ts:32-40` (chỉ `['my-applications']`) |
| a7 | Mục "Đơn ứng tuyển" trên thanh điều hướng đã sáng cho route con | **Đúng** | `CandidateLayout.tsx:35-36` (`path.startsWith('/candidate/applications')`) |

### (b) Kiểm quyền sở hữu phía ứng viên — hai quy ước đang cùng tồn tại

| Endpoint | Đơn của người khác | Đơn không tồn tại | Bằng chứng |
|---|---|---|---|
| `/{id}/history`, `/{id}/withdraw` | 404 `APPLICATION_NOT_FOUND` | 404 | `ApplicationService.java:93-95,125-127` (`findByIdAndCandidateId`) |
| `/api/candidates/resumes/{id}/download`, `/parsed` | 404 `RESUME_NOT_FOUND` | 404 | `ResumeService.java:126-129,144-146`; comment `ResumeCandidateController.java:25-28` |
| `/{applicationId}/interview-invitation` | **403** | 404 | `InterviewInvitationService.java:127-140`; walkthrough `candidate-view-invitation.md` mục 4a ghi là lựa chọn có chủ đích |

Endpoint mới theo quy ước 404 (R-Q2). Không sửa 403 của endpoint giấy mời; test T9 ghi lại rõ hai mã.

### (c) B1 — Tin đã tạm dừng / đóng / xoá mềm

- Xoá mềm chỉ ghi `deleted_at`, dòng `jobs` còn nguyên: `JobOwnerService.java:186-192`.
- `job_applications.job_id` khoá ngoại `ON DELETE RESTRICT` (`V1__init_schema.sql:208`) — dòng `jobs` có
  đơn trỏ tới không thể bị xoá cứng.
- Entity `Job` không có `@SQLRestriction`/`@Where` (`Job.java:22-27`), nên `jobRepository.findById` trả cả
  tin đã xoá mềm. Mọi field tóm tắt (`title`, `categoryCode`, `locationCode`, `category`, `location`,
  `employmentType`, `workMode`, `salaryMin/Max/Currency`, `deadline`, `status`) đều đọc được
  (`Job.java:39-87`).
- Danh sách đơn của ứng viên hiện **không** lọc `j.deleted_at` (`JobApplicationRepository.java:43-55`) — đơn
  vào tin đã xoá vẫn hiện ở danh sách; trang chi tiết phải nhất quán, vẫn mở được.
- Chi tiết tin công khai chỉ trả tin xem được công khai: `status = 'OPEN' AND deleted_at IS NULL AND
  (deadline IS NULL OR deadline >= CURRENT_DATE)` (`JobRepository.java:233-241`, gọi ở
  `JobPublicService.java:252`). Tin PAUSED/CLOSED/DRAFT/xoá mềm/quá hạn mở `/jobs/:id` sẽ 404.
- Công ty không có xoá mềm (`Company.java:16`), `jobs.company_id` khoá ngoại tới `companies`
  (`V1__init_schema.sql:63`) — tên công ty luôn đọc được.
- Nhãn ngành nghề/khu vực tính ở **một** chỗ: `JobCatalogFields.of` (`JobCatalogFields.java:16-26`), nhưng
  record này **package-private** trong `job/` — package `jobapplication/` chưa gọi được (R-C6).

### (d) B2 — CV của đơn có thể mất không

- **Không có đường xoá CV:** `ResumeCandidateController.java:42-100` không có `DELETE`; cả backend không có
  `@DeleteMapping` nào cho resume (chỉ rubric và job). Bảng `resumes` không có cột `deleted_at`
  (`Resume.java:22-82`; comment `ResumeHrService.java:62-67`).
- `job_applications.resume_id` khoá ngoại `ON DELETE RESTRICT` (`V1__init_schema.sql:210`) — dòng `resumes`
  của một đơn không thể biến mất.
- **Đường duy nhất làm "mất" CV là mất tệp trong kho lưu trữ** (dòng DB còn, file trên đĩa/MinIO không
  còn): `ResumeService.downloadMine` trả 404 `RESUME_NOT_FOUND` khi `storageService.load` rỗng
  (`ResumeService.java:130`). Trường hợp này chỉ lộ ra khi bấm tải, không lộ ra lúc mở trang.
- Kết luận: không cần trạng thái "CV không còn" cho dòng DB; cần xử lý **tải tệp gốc thất bại** tại chỗ,
  không lỗi trang (R-T13).

### (e) Thông báo của ứng viên

- Link luôn là `/candidate/applications`: `NotificationContentBuilder.java:27-33`. `entity_id` đã lưu
  `applicationId` (`NotificationEventListener.java:60,116-117`).
- Test đang **khẳng định** link cũ: chỉ **một** chỗ — `NotificationEventListenerIntegrationTest.java:219-233`
  (`changeStatus_candidateNotificationLinkUnchanged`, assert ở `:232`), viết ở FR-H09 với chú thích "FR-U08
  xử lý sau". Hai chỗ còn lại có chuỗi `/candidate/applications` là **dữ liệu dựng sẵn** của test, không
  phải khẳng định hành vi: `NotificationControllerIntegrationTest.java:92`,
  `NotificationMailOrchestratorIntegrationTest.java:139` (`n.setLink(...)` cho một thông báo tạo tay) — giữ
  nguyên, vì thông báo cũ trong DB vẫn mang link đó (R-N2).
- Trang "Xem tất cả" `NotificationList.tsx:60-67` không điều hướng theo `link` (chỉ đánh dấu đã đọc); chuông
  `NotificationDropdown.tsx:37` có điều hướng. Email chỉ có tiêu đề + nội dung, không có link
  (`NotificationMailOrchestrator.java:73-74`).

### (f) Dữ liệu ứng viên không được thấy

DTO phía HR **tuyệt đối không tái dùng** cho endpoint ứng viên: `ApplicationHrListItemResponse`
(`totalScore`, `rank`, `criterionScores`, `explanation`…), `ApplicationScoresResponse`,
`ApplicationExplanationResponse`, `ApplicationHrDetailResponse`, `ApplicationSearchItemResponse`,
`InterviewInvitationResponse` (`sentBy`), `InterviewInvitationPreviewResponse`.

Cột `application_status_history.note` **đang trả cho ứng viên** (`ApplicationHistoryEntryResponse.java:9-10`)
và hiện ở timeline (`ApplicationHistoryTimeline.tsx:54`). Hiện cả 3 nơi ghi đều truyền `null`
(`ApplicationService.java:80,105`, `ApplicationStatusService.java:83`) — xem mục 8.

### (g) Migration

Số lớn nhất hiện tại: `V11__drop_job_recommendations.sql`. FR-U08 **không migration** — mọi dữ liệu đã có
trong `job_applications`, `jobs`, `companies`, `resumes`, `resume_parsed_data`, `application_status_history`,
`interview_invitations`.

### (h) Các chỗ định dạng lương ở frontend

| File:dòng | Chữ ký | Có lương | Không có lương | Ghi chú |
|---|---|---|---|---|
| `features/jobs/JobCard.tsx:8-19` | `formatSalary(job: JobSummary): string \| null` | `"{min} - {max} {cur}"` hoặc `"{giá trị} {cur}"`, số `toLocaleString('vi-VN')`, tiền tệ mặc định `VND` | `null` | Thẻ việc làm (`/`, `/candidate`, khối gợi ý) |
| `pages/PublicJobDetailPage.tsx:12-27` | `formatSalary(job: {salaryMin, salaryMax, salaryCurrency}): string \| null` | Giống hệt dòng trên | `null` | Trang `/jobs/:id` |
| `pages/HrJobListPage.tsx:20-30` | `formatSalary(min, max, currency): string` | Giống hệt dòng trên | `'—'` | Cột lương ở `/hr/jobs` |

Ba hàm cho **cùng** kết quả khi có lương, chỉ khác giá trị khi không có lương — giá trị đó là việc của nơi
gọi. `JobFilterBar.tsx:272-273` (`"Lương: {min} - {max} triệu"`) là nhãn chip bộ lọc theo đơn vị triệu do người
dùng nhập, **không** phải định dạng lương của tin — không thuộc nhóm này. R-C4 gộp ba hàm trên thành một.

## 1. Mục đích

Ứng viên xem và thao tác trên **một** đơn ứng tuyển của chính mình tại **một** màn hình: trạng thái và lịch
sử đơn, giấy mời phỏng vấn, tóm tắt tin đã ứng tuyển, CV và thư giới thiệu đã nộp, nội dung CV hệ thống đã
trích xuất, và nút Rút đơn. Màn hình này là điểm đặt cho các FR sau (U09, U10, C06) — mỗi FR tự thêm phần
của mình.

## 2. Luồng người dùng

**Ứng viên đã đăng nhập**
1. Vào trang từ: liên kết "Xem chi tiết" ở `/candidate/applications`, hoặc bấm một thông báo đổi trạng thái
   **mới** ở chuông (R-E).
2. Đầu trang: liên kết quay lại danh sách đơn, tên tin tuyển dụng, tên công ty, badge trạng thái đơn, ngày
   nộp, nút "Rút đơn" (chỉ khi được rút).
3. Tab mặc định "Thông tin đơn", từ trên xuống: khối Giấy mời phỏng vấn (nếu có), khối Tin tuyển dụng, khối
   Hồ sơ đã nộp (tệp CV, tải CV gốc, thư giới thiệu), khối CV đã trích xuất.
4. Tab "Lịch sử": các lần chuyển trạng thái của đơn, cũ trước mới sau.
5. Bấm "Rút đơn" → hộp xác nhận hiện có của FR-U06 (chữ giữ nguyên) → xác nhận → badge, nút, tab Lịch sử và
   danh sách đơn cập nhật ngay, không tải lại trang.

**Hệ thống**
- Tải đầu trang (E1) trước; mỗi tab chỉ gọi API của mình khi tab được mở.
- Không gọi LLM/embedding, không tạo yêu cầu trích xuất lại khi ứng viên mở trang.

## 3. Quy tắc nghiệp vụ

### 3.1 Phạm vi (R-P)

- **R-P1.** Đúng 2 tab: "Thông tin đơn" và "Lịch sử". Một nút thao tác duy nhất: "Rút đơn".
- **R-P2.** Không dựng tab rỗng, tab bị khoá, nút bị khoá hay khoảng trống chờ cho: câu trả lời sàng lọc và
  Rút đồng ý lưu hồ sơ (FR-U09), chọn khung giờ (FR-U10), Trao đổi (FR-C06). FR nào tự thêm phần FR đó.
- **R-P3.** Trang danh sách `/candidate/applications` bỏ hẳn hộp thoại "Lịch sử ứng tuyển", hộp thoại
  "Giấy mời phỏng vấn" và nút "Rút đơn". Mỗi dòng còn đúng một liên kết "Xem chi tiết". Không tồn tại hai
  nơi xem lịch sử/giấy mời hay hai nơi rút đơn.
- **R-P4.** Không có nút "Thử lại"/"Trích xuất lại" CV trên trang này (gọi AI tốn phí; thuộc trang "Hồ sơ và
  CV", FR-U01/FR-C05).

### 3.2 Quyền truy cập (R-Q)

- **R-Q1.** E1 chỉ cho role CANDIDATE (filter chain `/api/candidates/**`, `SecurityConfig.java:55-56`)
  **và** đơn phải có `candidate_id` = người đang đăng nhập (lấy từ JWT, không tin path variable).
- **R-Q2.** Đơn của người khác và đơn không tồn tại trả **cùng** `404 APPLICATION_NOT_FOUND` — nạp bằng
  `JobApplicationRepository.findByIdAndCandidateId` như `getMyApplicationHistory`/`withdraw`. Không phân biệt
  hai trường hợp ở body hay mã.
- **R-Q3.** Không đổi hành vi kiểm quyền của endpoint có sẵn: `/history`, `/withdraw` (404),
  `/interview-invitation` (403 cho đơn người khác), `/resumes/{id}/download`, `/resumes/{id}/parsed` (404).
- **R-Q4.** HR không gọi được E1 (403 ở filter chain). Không thêm endpoint nào dưới `/api/hr/**`.
- **R-Q5.** Trang dùng lại `/api/candidates/resumes/{resumeId}/download` và `/parsed` với `resumeId` lấy từ
  E1. Hai endpoint này tự kiểm CV thuộc người gọi; CV của đơn luôn là CV của chính ứng viên
  (`ApplicationService.java:59-61` chỉ cho nộp CV của mình).

### 3.3 Dữ liệu (R-D)

- **R-D1. Đầu trang + thông tin đơn** đọc `job_applications` (gồm `cover_letter`), `jobs`, `companies.name`,
  `resumes` (`file_name`, `parse_status`, `parse_error`). Không đọc `scoring_runs`, `criterion_scores`,
  `score_explanations`, `score_explanation_attempts`, `interview_invitations`, `application_status_history`.
- **R-D2. CV của đơn** là `job_applications.resume_id` — **không** phải CV chính hiện tại của ứng viên.
- **R-D3. Thông tin tin là bản hiện tại** của dòng `jobs`, không phải bản lúc nộp (hệ thống không chụp
  lại tin). Giao diện ghi rõ điều này (UI.md mục 7).
- **R-D4. Tình trạng tin (`job.availability`)** do backend tính, frontend không tự suy. Đúng 5 giá trị:

  | Giá trị | Điều kiện (xét theo thứ tự, dừng ở dòng đầu tiên khớp) |
  |---|---|
  | `UNAVAILABLE` | `jobs.deleted_at IS NOT NULL` (bất kể `status`) |
  | `OPEN` | `jobRepository.findOpenJobById(jobId)` có kết quả — **đúng một** điều kiện với trang tin công khai (`JobRepository.java:233-241`), không viết điều kiện thứ hai |
  | `EXPIRED` | `status = OPEN`, chưa xoá, nhưng `findOpenJobById` rỗng (đã quá hạn nộp) |
  | `PAUSED` | `status = PAUSED` |
  | `CLOSED` | `status = CLOSED` |
  | `UNAVAILABLE` | `status = DRAFT` — `JobOwnerService.changeStatus` (`JobOwnerService.java:154-181`) chỉ kiểm điều kiện khi chuyển **sang** `OPEN`, không chặn chuyển về `DRAFT` |

  Tin đã xoá mềm và tin bị đưa về nháp **gộp chung** một giá trị `UNAVAILABLE`: ứng viên không cần biết HR đã
  xoá tin hay đưa tin về nháp — với ứng viên, cả hai đều là "tin không còn đăng". Không có giá trị nào cho
  phép phân biệt hai trường hợp này ở DTO hay giao diện.

- **R-D5. Liên kết "Xem tin tuyển dụng"** chỉ hiện khi `availability = OPEN`. Mọi giá trị khác: ẩn liên
  kết, vẫn hiện tóm tắt và nhãn tình trạng trung tính.
- **R-D6. Ngành nghề/khu vực** tính bằng chính `JobCatalogFields.of` (nhãn của mã → giá trị cũ → null), hiển
  thị bằng `jobCategoryText`/`jobLocationText` (`features/jobs/catalogDisplay.ts:19-32`). Không viết bộ
  chuyển mã → nhãn thứ hai.
- **R-D7. Thư giới thiệu** trả nguyên văn như đã lưu (không cắt, không trim lại, không lọc ký tự); `null` khi
  không nhập.
- **R-D8. Lỗi trích xuất CV** (`resumeParseError`) trả nguyên giá trị cột `resumes.parse_error` — đã là mã lỗi
  chuẩn hoá kèm câu mô tả (CLAUDE.md §4), cùng giá trị ứng viên đang thấy ở trang "Hồ sơ và CV"
  (`ResumeResponse.parseError`).
- **R-D9. Lịch sử** dùng lại `GET /api/candidates/applications/{id}/history` và DTO
  `ApplicationHistoryEntryResponse` nguyên trạng (có `note`, không có `changed_by`).
- **R-D10. Giấy mời** dùng lại `GET /api/candidates/applications/{id}/interview-invitation` và DTO
  `CandidateInterviewInvitationResponse` nguyên trạng (5 field, không `sentBy`).

### 3.4 Trạng thái hiển thị (R-T)

Nguồn trạng thái là field backend trả về; frontend không suy từ dữ liệu khác.

| Mã | Phần | Điều kiện | Hiển thị |
|---|---|---|---|
| R-T1 | Cả trang | E1 trả 400 (id không phải UUID), 403 hoặc 404 | Cùng một câu "Không tìm thấy đơn ứng tuyển." + liên kết về danh sách. Không hiện tab, không gọi API nào khác |
| R-T2 | Cả trang | E1 lỗi mạng/5xx | "Không tải được đơn ứng tuyển." + nút "Thử lại" |
| R-T3 | Giấy mời | `status` ∉ {`INTERVIEW_INVITED`, `HIRED`, `REJECTED`} | Không gọi API giấy mời, không hiện khối |
| R-T4 | Giấy mời | `status` ∈ {`INTERVIEW_INVITED`, `HIRED`, `REJECTED`} | Gọi API giấy mời (đúng quy tắc `CandidateApplicationsPage.tsx:113`) |
| R-T5 | Giấy mời | API trả 404 `INTERVIEW_INVITATION_NOT_FOUND` (vd đơn bị từ chối thẳng từ `PENDING`) | **Ẩn cả khối**, không báo lỗi |
| R-T6 | Giấy mời | API trả 200 | Khối giấy mời: thời gian (giờ Việt Nam), địa điểm nếu có, tiêu đề, nội dung nguyên văn |
| R-T7 | Giấy mời | API lỗi khác (mạng/5xx) | Trong khối: câu lỗi có sẵn "Không tải được giấy mời, vui lòng thử lại." + nút "Thử lại" |
| R-T8 | Tin tuyển dụng | Luôn có | Tóm tắt (R-D3…R-D6) + nhãn tình trạng tin + dòng chú thích "bản hiện tại" |
| R-T9 | Hồ sơ đã nộp | `resumeParseStatus` ∈ {`PENDING`, `PROCESSING`} | Tên tệp + `ParseStatusBadge`; khối CV đã trích xuất hiện câu chờ; không gọi `/parsed` |
| R-T10 | Hồ sơ đã nộp | `resumeParseStatus = FAILED` | Tên tệp + badge; khối CV đã trích xuất hiện "Trích xuất CV thất bại." + `resumeParseError` nguyên văn + câu hướng dẫn tới trang "Hồ sơ và CV"; **không** có nút thử lại (R-P4); không gọi `/parsed` |
| R-T11 | Hồ sơ đã nộp | `resumeParseStatus = DONE` | Gọi `/parsed` của `resumeId`, hiện `ResumeParsedDataView`; mục nào trống hiện câu trống có sẵn của view (vd "CV không có mục học vấn", `ResumeParsedDataView.tsx:47,63`). `/parsed` trả 404 (về lý thuyết; `getParsedResumeRequest` đổi thành `null`) → câu có sẵn nguyên văn "Chưa có dữ liệu trích xuất cho CV này." (`ResumeParsedDataDialog.tsx:26`) |
| R-T12 | Hồ sơ đã nộp | `coverLetter` khác `null` và khác rỗng | Khối "Thư giới thiệu" (`CoverLetterBlock`). Không có nội dung → không hiện tiêu đề |
| R-T13 | Hồ sơ đã nộp | Bấm "Tải CV gốc" lỗi (vd 404 `RESUME_NOT_FOUND` khi tệp không còn trong kho) | Dưới nút: "Tải CV gốc thất bại, vui lòng thử lại." — không lỗi trang, phần còn lại vẫn dùng được |
| R-T14 | Lịch sử | Đang tải / lỗi / rỗng / có dữ liệu | Theo `ApplicationHistoryTimeline` |
| R-T15 | Đầu trang | `status` ∈ {`PENDING`, `INTERVIEW_INVITED`} | Nút "Rút đơn" |
| R-T16 | Đầu trang | `status` ∈ {`HIRED`, `REJECTED`, `WITHDRAWN`} | Không có nút; không có dòng chữ thay thế (badge đã đủ) |

### 3.5 Rút đơn (R-W)

- **R-W1.** Nút "Rút đơn" hiện theo **đúng** điều kiện FR-U06: `PENDING` hoặc `INTERVIEW_INVITED` (cùng
  `ApplicationService.java:98`). Không thêm điều kiện khác.
- **R-W2.** Bấm → hộp xác nhận hiện có, **chuyển** từ `CandidateApplicationsPage.tsx:221-244` thành component
  trong `features/applications/`; tiêu đề, nội dung, nhãn nút, câu lỗi giữ **nguyên văn**. Gọi
  `PATCH /api/candidates/applications/{id}/withdraw` có sẵn; không thêm endpoint.
- **R-W3.** Thành công → làm mới E1, lịch sử của đơn (`['application-history', id]`) và danh sách
  (`['my-applications']`). Làm ở **chính** `useWithdrawApplicationMutation` (một mutation, không tạo bản
  thứ hai).
- **R-W4.** Lỗi 409 `APPLICATION_NOT_WITHDRAWABLE` (vd HR vừa đổi trạng thái ở tab khác) hiện trong hộp thoại
  như hiện tại; đồng thời làm mới E1 để nút biến mất nếu trạng thái đã đổi.

### 3.6 Điểm vào (R-E)

- **R-E1.** `/candidate/applications`: mỗi dòng có liên kết "Xem chi tiết" tới `/candidate/applications/{id}`
  (R-P3).
- **R-E2.** Liên kết quay lại ở đầu trang luôn trỏ `/candidate/applications`.
- **R-E3.** Route `/candidate/applications/:id` nằm trong nhóm `ProtectedRoute(CANDIDATE)` cùng các route
  ứng viên khác (`App.tsx:61-72`). Tab đang mở lưu ở `?tab=` (`info` mặc định, `history`; giá trị lạ →
  `info`), đổi tab bằng `replace`.

### 3.7 Thông báo (R-N)

- **R-N1.** Thông báo đổi trạng thái của ứng viên (`APPLICATION_STATUS_CHANGED`) tạo sau khi FR-U08 chạy có
  `link = /candidate/applications/{applicationId}`.
- **R-N2.** Thông báo đã có trong DB giữ nguyên link cũ `/candidate/applications`. Không migration, không sửa
  dữ liệu seed.
- **R-N3.** Không đổi tiêu đề, nội dung, kiểu, email của thông báo — chỉ đổi link.
- **R-N4. Ngoại lệ đã duyệt (test cũ đổi kỳ vọng):** `NotificationEventListenerIntegrationTest.java:219-233`
  (`changeStatus_candidateNotificationLinkUnchanged`, assert `:232`) đổi tên và kỳ vọng thành link mới
  (T10). Đây là test FR-H09 viết để giữ chỗ cho FR-U08, đổi có chủ đích. Không sửa
  `NotificationControllerIntegrationTest.java:92` và `NotificationMailOrchestratorIntegrationTest.java:139`
  (dữ liệu dựng sẵn, không phải khẳng định — mục 0.e).

### 3.8 Ràng buộc hiển thị (R-G)

- **R-G1.** Badge trạng thái đơn dùng `ApplicationStatusBadge` (bảng trung tính UI_GUIDE mục 3). Nhãn tình
  trạng tin dùng **một** kiểu trung tính cho mọi giá trị, không xanh/đỏ, không gợi ý tốt–xấu.
- **R-G2.** Trang không hiện bất kỳ điểm số, thứ hạng, tiêu chí, giải thích AI, ghi chú nội bộ nào.
- **R-G3.** Thư giới thiệu và giấy mời là lời của người (ứng viên/HR), không phải nội dung AI: văn bản thuần,
  giữ xuống dòng, không `dangerouslySetInnerHTML`, không khối "Do AI tạo".
- **R-G4.** Dữ liệu tóm tắt tin bị thiếu (ngành nghề, khu vực, hình thức, chế độ làm việc) hiện "Chưa có dữ
  liệu", không ẩn dòng (UI_GUIDE mục 4). Lương không công bố hiện "Không công bố". Hạn nộp `null` **không**
  phải thiếu dữ liệu — tin không đặt hạn là không giới hạn (R-D4 cũng coi hạn `NULL` là `OPEN`): hiện "Không
  giới hạn" bằng `formatDeadline` có sẵn (`lib/date.ts`), như trang tin công khai và thẻ việc làm.

### 3.9 Thay đổi code dùng chung (R-C)

- **R-C1.** Không sửa file trong `components/ui`.
- **R-C2.** Phần thân giấy mời (`CandidateApplicationsPage.tsx:51-93`) tách thành
  `features/interviewinvitation/InterviewInvitationDetails.tsx`; định dạng giờ, nhãn "Địa điểm: ", câu tải
  và câu lỗi giữ nguyên văn. Riêng nhánh 404 đổi từ câu "Đơn này chưa có giấy mời phỏng vấn." thành
  **không render gì** (R-T5) — nơi duy nhất dùng câu đó là hộp thoại bị bỏ ở R-P3.
- **R-C3.** Hộp xác nhận rút đơn (`CandidateApplicationsPage.tsx:221-244`) tách thành
  `features/applications/WithdrawApplicationDialog.tsx`; chữ giữ nguyên văn (R-W2).
- **R-C4.** Ba hàm `formatSalary` ở mục 0.h gộp thành **một** hàm dùng chung
  `features/jobs/formatSalary.ts`:
  `formatSalary(job: { salaryMin: number | null; salaryMax: number | null; salaryCurrency: string | null }): string | null`
  — thân hàm chuyển nguyên từ `PublicJobDetailPage.tsx:12-27`. Bốn nơi dùng:
  `JobCard.tsx`, `PublicJobDetailPage.tsx`, `HrJobListPage.tsx` (gọi `formatSalary(job) ?? '—'`, giữ nguyên ký
  tự hiển thị cũ khi không có lương) và khối Tin tuyển dụng của trang này (`?? 'Không công bố'`, R-G4). Xoá
  cả ba bản cục bộ; không tạo bản thứ tư; không đổi kết quả hiển thị ở ba màn hình cũ. `JobFilterBar.tsx:273`
  không thuộc phạm vi (mục 0.h).
- **R-C5.** `CoverLetterBlock`, `ResumeParsedDataView`, `ApplicationHistoryTimeline`, `ParseStatusBadge`,
  `ApplicationStatusBadge`, `formatDateTimeVi` dùng lại nguyên trạng, không sửa.
- **R-C6.** `JobCatalogFields` (`JobCatalogFields.java:8`) và method `of` đổi sang `public` để
  `jobapplication/` dùng được. Không đổi logic, không đổi nơi gọi cũ.

## 4. Dữ liệu & quyền truy cập

Không migration. Không đổi entity. Endpoint mới là GET, chỉ đọc, `@Transactional(readOnly = true)` như
`getMyApplicationHistory`.

| # | Endpoint | Dùng ở | Response | Lỗi |
|---|---|---|---|---|
| E1 | `GET /api/candidates/applications/{applicationId}` (**mới**) | Đầu trang + tab Thông tin đơn | `ApplicationCandidateDetailResponse` | 401 `UNAUTHENTICATED`; 403 (không phải CANDIDATE, filter chain); 404 `APPLICATION_NOT_FOUND` (không tồn tại **hoặc** của người khác); 400 mặc định của Spring khi id không phải UUID |

Endpoint có sẵn được trang gọi, **không đổi**: `GET /{id}/history`, `GET /{id}/interview-invitation`,
`PATCH /{id}/withdraw`, `GET /api/candidates/resumes/{resumeId}/download`,
`GET /api/candidates/resumes/{resumeId}/parsed`.

E1 đặt ở `ApplicationCandidateController` (thêm method), logic ở `ApplicationService` (thêm method đọc), DTO ở
`jobapplication/dto/`.

```java
// E1 - moi field duoc liet ke day du; KHONG them field nao khac
public record ApplicationCandidateDetailResponse(
        UUID id,
        ApplicationStatus status,
        Instant appliedAt,
        Instant updatedAt,
        String coverLetter,              // nguyen van, null khi khong nhap (R-D7)
        JobInfo job,
        ResumeInfo resume) {

    public record JobInfo(
            UUID id,
            String title,
            UUID companyId,
            String companyName,          // ten cong ty HIEN TAI
            JobAvailability availability, // R-D4
            String categoryCode,         // 6 field danh muc tu JobCatalogFields.of (R-D6)
            String categoryLabel,
            String locationCode,
            String locationLabel,
            String legacyCategory,
            String legacyLocation,
            String employmentType,
            String workMode,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String salaryCurrency,
            LocalDate deadline) {}

    public record ResumeInfo(
            UUID id,                     // = job_applications.resume_id (R-D2)
            String fileName,
            ParseStatus parseStatus,
            String parseError) {}        // "MA: mo ta" da chuan hoa hoac null (R-D8)

    public enum JobAvailability { OPEN, EXPIRED, PAUSED, CLOSED, UNAVAILABLE } // R-D4, dung 5 gia tri
}
```

Không có trong DTO (và test T4 khẳng định không có ở **bất kỳ** cấp lồng nào). So khớp theo **nguyên tên
khoá** (bằng chính xác, phân biệt hoa/thường), **không** theo chuỗi con: `categoryLabel`/`locationLabel` là
khoá hợp lệ của `JobInfo`; chỉ khoá tên đúng `label` mới bị cấm. Danh sách cấm: `totalScore`, `rank`,
`criterionScores`, `explanation`, `explanationStatus`, `scoringRunId`, `latestScoringRunId`,
`latestScoringRunStatus`, `latestScoringRunFinishedAt`, `sentBy`, `changedBy`, `candidateId`,
`aiConsent`, `recruitmentCycle`, `description`, `requirements`, `fileUrl`, và các khoá cấm của CLAUDE.md §7:
`verdict`, `label`, `isQualified`, `passed`, `recommendation`.

## 5. AI

Không dùng AI. Trang chỉ đọc dữ liệu đã lưu (kể cả `resume_parsed_data` của FR-C04). Không gọi LLM/embedding,
không tạo yêu cầu trích xuất lại, không thuộc danh sách ngoại lệ K3, không dùng K1–K4.

## 6. Ngoài phạm vi

- **Rút đồng ý lưu hồ sơ** (cờ đồng ý + nút Rút đồng ý) — thuộc FR-U09; phần xoá khỏi kho ứng viên thuộc
  FR-H15 (`docs/ROADMAP.md:768-770`). Các câu SRS/README/UI_GUIDE đang ghi phần này vào U08 được sửa trong
  commit đặc tả sau khi duyệt — danh sách đầy đủ ở mục 10.
- Câu trả lời sàng lọc (FR-U09), chọn khung giờ phỏng vấn (FR-U10), tab Trao đổi (FR-C06).
- Mô tả/yêu cầu đầy đủ của tin (chỉ tóm tắt — Q4), bản chụp tin lúc nộp.
- Nút thử lại/trích xuất lại CV (R-P4).
- Sửa lỗi `NotificationList` không điều hướng theo `link` (`NotificationList.tsx:60-67`, có từ FR-C03, ảnh
  hưởng cả HR) — ghi nợ kỹ thuật.
- Thêm link vào email thông báo.
- Sửa link thông báo cũ trong DB/seed; thêm dữ liệu seed.
- Chuẩn hoá lỗi 400 cho path variable sai định dạng (`MethodArgumentTypeMismatchException`) — giao diện coi
  400 như "Không tìm thấy" (R-T1), như FR-H09.
- Đổi 403 của endpoint giấy mời sang 404.
- Lọc đơn vào tin đã xoá mềm khỏi danh sách đơn (hành vi hiện tại giữ nguyên).
- Nhãn chip bộ lọc lương `JobFilterBar.tsx:273` (không phải định dạng lương của tin, mục 0.h).

## 7. Xong khi

### 7.1 Test backend (Testcontainers)

Lớp mới `ApplicationCandidateDetailControllerIntegrationTest` tự tạo toàn bộ dữ liệu của mình (người dùng
email duy nhất — prefix ngắn, ≤ 27 ký tự, xem nợ `uniqueEmail` của FR-H09; công ty, tin, CV, đơn), không
đọc dữ liệu seed hay dữ liệu lớp test khác để lại.

| # | Test | Loại |
|---|---|---|
| T1 | E1 với đơn của mình → 200, đủ **mọi** field mục 4 với giá trị đúng (gồm `job.companyName`, `resume.fileName`) | dương |
| T2 | E1 với đơn của ứng viên khác → 404 `APPLICATION_NOT_FOUND`; với UUID không tồn tại → 404 `APPLICATION_NOT_FOUND`; hai body có cùng mã `error` | âm |
| T3 | E1 bằng token HR → 403; không token → 401 `UNAUTHENTICATED` | âm |
| T4 | JSON của E1 (duyệt đệ quy mọi khoá ở mọi cấp) không chứa khoá nào trong danh sách cấm ở mục 4. So khớp **nguyên tên khoá** (bằng chính xác), không phải chứa chuỗi con — `categoryLabel`/`locationLabel` hợp lệ, chỉ khoá tên đúng `label` bị cấm | âm |
| T5 | Ứng viên nộp đơn bằng CV A, sau đó tải CV B và đặt B làm CV chính → `resume.id` của E1 vẫn là A | dương |
| T6 | `job.availability`: tin OPEN hạn nộp `NULL` → `OPEN`; hạn = ngày DB hiện tại − 1 → `EXPIRED`; = ngày DB hiện tại → `OPEN`; = ngày DB hiện tại + 1 → `OPEN`; `PAUSED` → `PAUSED`; `CLOSED` → `CLOSED`; `DRAFT` → `UNAVAILABLE`; tin OPEN còn hạn nhưng đã xoá mềm → `UNAVAILABLE` và `title`/lương/hạn nộp vẫn trả đúng; tin `CLOSED` đã xoá mềm → `UNAVAILABLE` (xoá mềm thắng `status`, R-D4). JSON của mọi ca chỉ chứa một trong 5 giá trị của R-D4. "Ngày DB hiện tại" đọc bằng `SELECT CURRENT_DATE` trong test, **không** dùng `LocalDate.now()` (JVM và Postgres có thể khác múi giờ) | biên + dương |
| T7 | `coverLetter`: thư nhiều dòng chứa `<b>x</b>` và `&` → trả nguyên văn, giữ `\n`; đơn không nhập → `null` | dương + âm |
| T8 | Danh mục: tin có mã → `categoryLabel`/`locationLabel` khác null, `legacy*` null; tin chưa chuẩn hoá (mã null, cột cũ có giá trị) → `legacy*` = giá trị cũ | dương |
| T9 | Lớp riêng `CandidateApplicationCrossEndpointAccessIntegrationTest`: cùng **một** đơn của ứng viên khác — E1 → 404, `/history` → 404, `PATCH /withdraw` → 404 và trạng thái đơn trong DB không đổi, `/interview-invitation` → **403**. Comment trong test ghi rõ: 403 của giấy mời là quy ước cũ có chủ đích, không phải lỗi | âm |
| T10 | (thay `changeStatus_candidateNotificationLinkUnchanged`, R-N4) HR đổi trạng thái đơn → thông báo của ứng viên có `link = /candidate/applications/{applicationId}`; 3 thông báo HR giữ link `/hr/applications/{id}` (test FR-H09 có sẵn vẫn xanh) | dương |
| T11 | `resume.parseStatus`/`parseError`: CV `FAILED` có mã lỗi → trả đúng chuỗi trong cột; CV `PENDING` → `parseError = null` | dương + âm |
| T12 | Đơn `PENDING` → rút đơn → gọi lại E1 → `status = WITHDRAWN`; `/history` → dòng cuối `PENDING → WITHDRAWN` | dương |

Tầng test không gọi LLM thật; FR này không cần stub `ChatModel` mới.

### 7.2 Kiểm bằng HTTP — Windows PowerShell 5.1

Nạp seed test (`db/seed/README.md` mục 8). Backend chạy ở `localhost:8080`. Dán **cả khối** vào **một** cửa
sổ PowerShell.

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
        $text = $_.ErrorDetails.Message
        if (-not $text) {
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $text = $reader.ReadToEnd()
        }
        '{0} {1} {2}' -f [int]$resp.StatusCode, $Path, $text
    }
}

$tokenUv  = Get-Token 'quochuyuv@gmail.com' '12345678'
$tokenLan = Get-Token 'le.thi.lan@test.local' '12345678'
$tokenHr  = Get-Token 'minhdathr@gmail.com' '12345678'

$a1      = '/api/candidates/applications/e8000000-0000-0000-0000-000000000001'  # don A1 cua Quoc Huy
$a9      = '/api/candidates/applications/e8000000-0000-0000-0000-000000000009'  # don A9 cua Tran Bao Ngoc
$missing = '/api/candidates/applications/00000000-0000-0000-0000-000000000000'

'--- 1. Duong: Quoc Huy doc 5 don cua minh (mong doi 200 x5)'
foreach ($n in 1..5) { Test-Get "/api/candidates/applications/e8000000-0000-0000-0000-00000000000$n" $tokenUv }

'--- 2. Am: Quoc Huy doc don cua nguoi khac (mong doi 404 APPLICATION_NOT_FOUND)'
Test-Get $a9 $tokenUv

'--- 3. Am: don khong ton tai (mong doi 404 APPLICATION_NOT_FOUND)'
Test-Get $missing $tokenUv

'--- 4. Am: Le Thi Lan doc don A1 cua Quoc Huy (mong doi 404 APPLICATION_NOT_FOUND)'
Test-Get $a1 $tokenLan

'--- 5. Quy uoc cu giu nguyen: giay moi don nguoi khac (mong doi 403), lich su don nguoi khac (mong doi 404)'
Test-Get "$a9/interview-invitation" $tokenUv
Test-Get "$a9/history" $tokenUv

'--- 6. Am: HR goi endpoint ung vien (mong doi 403)'
Test-Get $a1 $tokenHr

'--- 7. Am: khong token (mong doi 401 UNAUTHENTICATED)'
Test-Get $a1 $null
```

Mong đợi: nhóm 1 năm dòng `200`; nhóm 2, 3, 4 mỗi nhóm một dòng `404 … {"error":"APPLICATION_NOT_FOUND",…}`;
nhóm 5 `403 …` rồi `404 … APPLICATION_NOT_FOUND`; nhóm 6 `403`; nhóm 7 `401 … {"error":"UNAUTHENTICATED",…}`.

**Bắt buộc ở đợt code cuối:** chạy thật khối trên, dán **nguyên văn** output vào báo cáo đợt. Lệnh sai cú pháp
hoặc cho kết quả khác "Mong đợi" vì lệnh viết sai thì dừng, đề xuất sửa mục này, chờ duyệt lại.

### 7.3 Frontend

- `npm run build` + `npm run lint` sạch.
- Tìm `Dialog|useApplicationHistoryQuery|useInterviewInvitationQuery|WithdrawApplicationDialog` trong
  `frontend/src/pages/CandidateApplicationsPage.tsx` → 0 dòng (R-P3).
- Tìm `api/hr|/hr/|features/scoring|features/applicationDetail/(api|queries|types)|totalScore|rank|criterionScores|explanation`
  trong `frontend/src/pages/CandidateApplicationDetailPage.tsx` và `frontend/src/features/candidateApplicationDetail`
  → 0 dòng (trang ứng viên không gọi API/hook/kiểu phía HR, không chạm dữ liệu điểm).
- Tìm `dangerouslySetInnerHTML` trong `frontend/src` → 0 dòng.
- Tìm `function formatSalary` trong `frontend/src` → **đúng 1** dòng, ở `features/jobs/formatSalary.ts` (R-C4).
- Lệnh tìm token cũ của Phase 2.1b (`docs/ROADMAP.md`, "Xong khi" của Phase 2.1b) vẫn trả 0 dòng.

### 7.4 Soát tay (desktop ≥ `lg` và điện thoại 375px) — seed test, `quochuyuv@gmail.com` / `12345678`

| Trạng thái cần thấy | Đơn | Mong đợi |
|---|---|---|
| Chờ duyệt, có thư, CV đã trích xuất, tin đang mở | A1 (`e8…01`) | Nút "Rút đơn"; không khối giấy mời (R-T3); khối thư giới thiệu; CV đã trích xuất hiện nội dung R3; liên kết "Xem tin tuyển dụng" |
| Đã mời phỏng vấn, lịch tương lai | A2 (`e8…02`) | Khối giấy mời đủ thời gian/địa điểm/tiêu đề/nội dung giữ xuống dòng; nút "Rút đơn"; không thư giới thiệu |
| Đã rút, tin tạm dừng, CV lỗi trích xuất | A3 (`e8…03`) | Không nút; nhãn tin "Tạm dừng", không liên kết xem tin; "Trích xuất CV thất bại." + `EXTRACT_EMPTY…`; không nút thử lại; "Tải CV gốc" tải được |
| Trúng tuyển, tin đã đóng, giấy mời đã qua | A4 (`e8…04`) | Khối giấy mời lịch cũ; nhãn tin "Đã đóng"; thư giới thiệu; Lịch sử 3 bước |
| Bị từ chối thẳng, không giấy mời | A5 (`e8…05`) | **Không** có khối giấy mời, không câu lỗi (R-T5); nhãn tin "Đã đóng" |
| Rút đơn tại chỗ | A1 → Rút đơn → Xác nhận | Badge thành "Đã rút đơn", nút biến mất, tab Lịch sử có bước mới, danh sách đơn đổi theo; xong chạy `reset-test-data.sql` |
| Không tìm thấy | `/candidate/applications/e8000000-0000-0000-0000-000000000009`, `…/00000000-0000-0000-0000-000000000000`, `…/abc` | Cùng một câu "Không tìm thấy đơn ứng tuyển."; tab Network không có lời gọi nào khác ngoài E1 |
| Danh sách | `/candidate/applications` | Mỗi dòng chỉ có "Xem chi tiết"; không còn "Xem lịch sử", "Xem giấy mời", "Rút đơn" |
| Thông báo cũ | Chuông | 4 thông báo seed vẫn mở `/candidate/applications` (R-N2) |
| Thông báo mới | Đăng nhập HR `minhdathr@gmail.com`, Từ chối A1 → đăng nhập lại Quốc Huy → chuông | Mở thẳng `/candidate/applications/e8…01` (R-N1); xong chạy `reset-test-data.sql` |
| Trang tin công khai `/jobs/:id` | `/jobs/e2000000-0000-0000-0000-000000000001` (J1) | Dòng lương hiện đúng `9.000.000 - 14.000.000 VND` (R-C4); phần còn lại của trang không đổi |
| Trang tin công khai, tin không công bố lương | `/jobs/e2000000-0000-0000-0000-000000000002` (J2) | Không có dòng lương (như trước) |
| Thẻ việc làm | `/candidate`, thẻ của J1 và J2 | J1 hiện đúng `9.000.000 - 14.000.000 VND`; J2 không có dòng lương (như trước) |
| Danh sách tin phía HR | `minhdathr@gmail.com` → `/hr/jobs` | Cột lương J1 `9.000.000 - 14.000.000 VND`; J2 `—` (như trước) |
| Tóm tắt tin trên trang chi tiết | A1 (J1) và A2 (J2) | A1 "Mức lương" `9.000.000 - 14.000.000 VND`; A2 "Mức lương" `Không công bố` |

Trạng thái không có trong seed (không làm giả dữ liệu): CV `PENDING` trên đơn, tin quá hạn/`DRAFT`/xoá mềm, thư
nhiều dòng, tệp CV mất trong kho — phủ bằng T6, T7, T11 và đọc code R-T13.

## 8. AI hay làm sai (dành cho người code)

- Tái dùng DTO/service phía HR (`ApplicationHrDetailResponse`, `HrApplicationAccess`, `ApplicationOwnerService`…)
  cho endpoint ứng viên, hoặc thêm "cho đủ" field điểm/hạng/lượt chấm vào DTO mới.
- Bỏ bất kỳ khoá nào khỏi danh sách cấm của T4 (mục 4) để test xanh, hoặc đổi T4 sang so chuỗi con rồi "sửa"
  bằng cách loại `label` vì vướng `categoryLabel`/`locationLabel`. T4 đỏ nghĩa là DTO sai, không phải danh sách
  sai.
- Trả 403 cho đơn của người khác ở E1 vì "giống endpoint giấy mời" (R-Q2); hoặc "sửa cho đồng bộ" endpoint
  giấy mời sang 404 (R-Q3, ngoài phạm vi).
- Đọc CV chính hiện tại thay vì `job_applications.resume_id` (R-D2).
- Lọc `deleted_at`/`status` khi nạp tin rồi trả 404 cho đơn vào tin đã đóng/xoá — đơn vẫn là của ứng viên
  (R-D4, mục 0.c).
- Tự viết điều kiện "tin còn mở" ở Java (`LocalDate.now()`) thay vì dùng `findOpenJobById` — lệch với trang
  tin công khai ở ranh giới ngày (R-D4).
- Frontend tự suy tình trạng tin từ `deadline`/ngày hiện tại; hiện liên kết "Xem tin tuyển dụng" khi không
  phải `OPEN` (dẫn tới trang 404).
- Tách lại `UNAVAILABLE` thành hai giá trị (xoá mềm / nháp), thêm field `deleted`/`deletedAt`, hay hiện nhãn
  khác nhau cho hai trường hợp (R-D4).
- Để lại một bản `formatSalary` cục bộ, tạo bản mới cho trang này, hoặc đổi chữ hiển thị khi không có lương ở
  thẻ việc làm/trang tin/danh sách HR (R-C4).
- Viết bộ chuyển mã ngành/khu vực → nhãn thứ hai (R-D6).
- **Ghi chú nội bộ của HR vào `application_status_history.note`.** Cột này là dữ liệu **ứng viên nhìn thấy**:
  nó đi thẳng ra `ApplicationHistoryEntryResponse` và hiện ở timeline của ứng viên. FR nào cần ghi chú nội
  bộ của HR phải dùng chỗ lưu khác (bảng/cột riêng chỉ phía HR đọc).
- Thêm nút "Thử lại"/"Trích xuất lại" CV trên trang (R-P4).
- Hiện câu "Đơn này chưa có giấy mời phỏng vấn." hay câu lỗi khi API giấy mời trả 404 (R-T5); hoặc gọi API
  giấy mời cho đơn `PENDING`/`WITHDRAWN` (R-T3).
- Tạo mutation rút đơn thứ hai, hoặc chỉ làm mới danh sách mà quên E1 và lịch sử (R-W3).
- Viết lại chữ của hộp xác nhận rút đơn/khối giấy mời khi tách component (R-C2, R-C3).
- Giữ dialog lịch sử/giấy mời/nút Rút đơn ở trang danh sách song song với trang chi tiết (R-P3).
- Dựng tab "Trao đổi", khối "Câu trả lời sàng lọc", nút "Rút đồng ý" rỗng/khoá "cho đủ bố cục" (R-P2).
- Render thư giới thiệu/giấy mời bằng HTML, trim/cắt bớt, hoặc đặt trong khối "Do AI tạo" (R-G3).
- Dùng màu xanh/đỏ cho nhãn tình trạng tin hay badge trạng thái đơn (R-G1).
- Sửa `NotificationControllerIntegrationTest.java:92`/`NotificationMailOrchestratorIntegrationTest.java:139`
  "cho khớp link mới" — đó là dữ liệu thông báo cũ (R-N4).
- Viết migration hoặc sửa seed để đổi link thông báo cũ (R-N2).
- Gọi `LocalDate.now()` trong T6 thay vì `CURRENT_DATE` của DB.
- Prefix `uniqueEmail` dài hơn 27 ký tự (phần trước `@` vượt 64 ký tự → 400 khi đăng ký).

## 9. Kế hoạch đợt code

Mỗi đợt: dừng → báo cáo diff → chờ duyệt → một commit. Giữa các đợt backend chỉ chạy `.\mvnw.cmd test-compile`
và **riêng** lớp test liên quan (`-Dtest=...`); full suite `.\mvnw.cmd test` chạy **đúng một lần** ở đợt cuối.
Frontend: `npm run build` + `npm run lint` sau mỗi đợt có sửa frontend.

| Đợt | Nội dung | Kiểm |
|---|---|---|
| 1 | Plan Mode: đối chiếu đặc tả đã duyệt với code (mục 0), chốt danh sách file | — |
| 2 | Backend E1: `ApplicationCandidateDetailResponse`, method ở `ApplicationService` + controller, `JobCatalogFields` public (R-C6); T1–T9, T11, T12 | test-compile; `-Dtest=ApplicationCandidateDetailControllerIntegrationTest,CandidateApplicationCrossEndpointAccessIntegrationTest` |
| 3 | Backend thông báo: link mới (R-N1), đổi test `:219-233` thành T10 (R-N4) | test-compile; `-Dtest=NotificationEventListenerIntegrationTest` |
| 4 | Frontend tách: `InterviewInvitationDetails`, `WithdrawApplicationDialog` (trang danh sách dùng bản tách); `formatSalary` một bản dùng chung cho `JobCard`, `PublicJobDetailPage`, `HrJobListPage` (R-C4) — giao diện cả bốn màn hình không đổi | build + lint; lệnh tìm `function formatSalary` (7.3) |
| 5 | Frontend trang `/candidate/applications/:id` + route; danh sách thành liên kết "Xem chi tiết" (R-P3); mutation rút đơn làm mới đủ (R-W3) | build + lint; lệnh tìm 7.3 |
| 6 | Đợt cuối: full `.\mvnw.cmd test` (một lần), khối PowerShell 7.2 (dán output), `srs-guard`, `walkthrough`, trạng thái `ĐÃ HOÀN THÀNH`, SRS mục 0, ROADMAP | full suite; 7.2; 7.3 |

## 10. Tài liệu dùng chung sửa trong commit đặc tả

Sửa **cùng commit** với việc đổi trạng thái hai file đặc tả này sang `ĐÃ DUYỆT`, sau khi người dùng duyệt. Lý
do: "Rút đồng ý lưu hồ sơ" thuộc FR-U09/FR-H15, không thuộc FR-U08 (mục 6; `docs/ROADMAP.md:768-770`), và hai
dòng bản đồ màn hình phải khớp phạm vi mới. Số dòng tính ở thời điểm viết đặc tả; khi sửa, tìm theo nội dung
câu cũ.

| # | File:dòng | Câu cũ (nguyên văn) | Câu mới |
|---|---|---|---|
| D1 | `docs/SRS.md:127` | `\| FR-U08 \| Trang chi tiết đơn ứng tuyển \| Một màn hình gom trạng thái, lịch sử, giấy mời, câu trả lời sàng lọc, trao đổi, rút đơn, rút đồng ý \| Ứng viên \| FR-U03, FR-U06 \|` | `\| FR-U08 \| Trang chi tiết đơn ứng tuyển \| Một màn hình gom trạng thái, lịch sử, giấy mời, câu trả lời sàng lọc, trao đổi, rút đơn (rút đồng ý do FR-U09 thêm) \| Ứng viên \| FR-U03, FR-U06 \|` |
| D2 | `docs/SRS.md:128` | `\| FR-U09 \| Trả lời sàng lọc và đồng ý lưu hồ sơ \| Khi nộp đơn: trả lời câu hỏi sàng lọc; tuỳ chọn cho công ty lưu hồ sơ (mặc định không) \| Ứng viên \| FR-U02, FR-H10 \|` | `\| FR-U09 \| Trả lời sàng lọc và đồng ý lưu hồ sơ \| Khi nộp đơn: trả lời câu hỏi sàng lọc; tuỳ chọn cho công ty lưu hồ sơ (mặc định không); rút đồng ý ở trang chi tiết đơn \| Ứng viên \| FR-U02, FR-H10 \|` |
| D3 | `docs/features/README.md:318` | `Từ Đơn của tôi hoặc thông báo → mở chi tiết đơn → xem thông tin Job, CV đã nộp, trạng thái và lịch sử (FR-U03), giấy mời và chọn khung giờ (U10), câu trả lời sàng lọc đã gửi (U09), trạng thái đồng ý lưu hồ sơ kèm nút Rút đồng ý, tab Trao đổi (C06), nút Rút đơn (FR-U06).` | `Từ Đơn của tôi hoặc thông báo → mở chi tiết đơn → xem thông tin Job, CV đã nộp, trạng thái và lịch sử (FR-U03), giấy mời và chọn khung giờ (U10), câu trả lời sàng lọc đã gửi và trạng thái đồng ý lưu hồ sơ kèm nút Rút đồng ý (U09), tab Trao đổi (C06), nút Rút đơn (FR-U06).` |
| D4 | `docs/features/README.md:324` | `- Rút đồng ý: đặt lại cờ đồng ý lưu hồ sơ của đơn, ghi thời điểm, xoá đơn khỏi mọi kho ứng viên (H15). Rút đồng ý là một chiều, không bật lại được cho đơn đã nộp.` | `- Rút đồng ý (FR-U09 thêm vào trang này; phần xoá khỏi kho do FR-H15): đặt lại cờ đồng ý lưu hồ sơ của đơn, ghi thời điểm, xoá đơn khỏi mọi kho ứng viên. Rút đồng ý là một chiều, không bật lại được cho đơn đã nộp.` |
| D5 | `docs/features/README.md:336` (nguồn) | `Ứng viên chỉ xem được đơn của chính mình. Rút đồng ý không ảnh hưởng trạng thái, điểm số hay việc xét tuyển của đơn.` | `Ứng viên chỉ xem được đơn của chính mình.` |
| D6 | `docs/features/README.md:494` (đích của câu chuyển ở D5 — đoạn **Lưu ý** của mục "10. FR-U09") | `Ô đồng ý lưu hồ sơ mặc định không tick và không bắt buộc; không tick vẫn nộp đơn bình thường và không ảnh hưởng điểm, xếp hạng hay việc xét tuyển. Đồng ý tính theo từng đơn: hai đơn vào cùng công ty có thể có lựa chọn khác nhau.` | Giữ nguyên câu cũ, **nối thêm vào cuối đoạn** câu chuyển **nguyên văn** từ D5: `… có thể có lựa chọn khác nhau. Rút đồng ý không ảnh hưởng trạng thái, điểm số hay việc xét tuyển của đơn.` |
| D7 | `docs/features/README.md:750` | `- Khi ứng viên rút đồng ý (U08), mục bị xoá khỏi mọi kho kèm ghi chú và bản tóm tắt.` | `- Khi ứng viên rút đồng ý (U09), mục bị xoá khỏi mọi kho kèm ghi chú và bản tóm tắt.` |
| D8 | `docs/UI_GUIDE.md:340` | `\| /candidate/applications \| "Đơn ứng tuyển" \| Hiện có \| FR-U03 \|` (route trong dấu backtick) | `\| /candidate/applications \| "Đơn ứng tuyển" \| Hiện có \| FR-U03; FR-U08 (liên kết "Xem chi tiết") \|` |
| D9 | `docs/UI_GUIDE.md:341` | `\| ★/candidate/applications/:id \| Chi tiết đơn ứng tuyển \| ★Mới \| FR-U08; FR-U10 (chọn giờ); FR-U09 (câu trả lời); FR-C06 (trao đổi); rút đồng ý \|` (route trong dấu backtick) | `\| ★/candidate/applications/:id \| Chi tiết đơn ứng tuyển — tab "Thông tin đơn" (giấy mời, tóm tắt tin, CV đã nộp, thư giới thiệu, CV đã trích xuất), "Lịch sử"; nút Rút đơn. Tab/nút của FR sau do chính FR đó thêm \| ★Mới \| FR-U08; FR-U03, FR-U06; FR-U10 (chọn giờ); FR-U09 (câu trả lời, rút đồng ý); FR-C06 (trao đổi) \|` |

Không sửa gì khác trong ba file trên ở commit này. Dòng trạng thái FR-U08 ở `docs/SRS.md:52` chỉ đổi ở đợt
code cuối (mục 9).

## 11. Điểm còn mở

Không còn điểm mở.
