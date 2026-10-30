# FR-C09 — Yêu cầu xem xét lại đơn bị từ chối

> Trạng thái: ĐÃ DUYỆT (30/10/2026). Mọi quyết định ở mục 11 đã chốt 30/10/2026 (Q1–Q10; cách chia 400/409 ở R-S3
> bước (0) và R-D3 xác nhận khi duyệt; bổ sung khoá `ON DELETE RESTRICT` ở mục 9 và mục 8).
>
> Viết theo kế hoạch `docs/plans/2026-11-dot-bo-sung.md` (mục "FR-C09", quyết định 29/10).

- Nhóm: Chung
- Tóm tắt: Ứng viên có đơn bị từ chối ở vòng hồ sơ được gửi **một** yêu cầu xem xét lại trong **7 ngày**; HR **mời
  phỏng vấn** (qua hộp giấy mời FR-H12) hoặc **giữ quyết định từ chối** (kèm phản hồi hoặc không). Đơn vẫn đúng 5 trạng
  thái; yêu cầu nằm ở bảng riêng (V21), package `reconsideration/`. Không dùng AI.
- Phụ thuộc: FR-H07, FR-H12, FR-U08, FR-H09, FR-C03, FR-H16 — cả sáu **Đã hoàn thành** (`docs/SRS.md:25`, `:38`,
  `:40`, `:43`, `:46`, `:54`). Khớp danh sách phụ thuộc ở `docs/SRS.md:88`.
- Nhánh: `feat/fr-c09-reconsideration` (tạo từ `main` tại `f0e6354`, 30/10/2026; **đã rebase** lên `main` tại `0d6dbef`
  sau khi FR-U16 — V20, Đã hoàn thành, `docs/SRS.md:61` — merge, 30/10/2026; xem mục 11 "Đồng bộ sau FR-U16").
- Mở rộng: FR-H07 (một cạnh chuyển trạng thái mới, chỉ qua FR-C09), FR-H12 (giấy mời nhận đơn `REJECTED` có yêu cầu
  `OPEN`), FR-U03 (danh sách đơn), FR-U08 (khối xem xét lại, nút thứ hai), FR-H09 (khối yêu cầu ở hồ sơ đơn), trang sửa
  tin `/hr/jobs/:id/edit` (tab mới), FR-C03 (hai loại thông báo mới).

## 0. Đã đối chiếu code trước khi viết (CLAUDE.md §6)

Đối chiếu trên `feat/fr-c09-reconsideration` tại `f0e6354` (30/10/2026, cây làm việc sạch). Số dòng các file FR-U16 có
sửa đã cập nhật theo `main` tại `0d6dbef` (mục 11 "Đồng bộ sau FR-U16"); file FR-U16 không sửa giữ nguyên số dòng.

### (a) Hiện trạng

| # | Điểm kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| a1 | Đơn có đúng 5 trạng thái | Đúng | `ApplicationStatus.java:3-8`; `labelVi()` `:15-23` là `switch` phủ đủ 5 giá trị |
| a2 | `REJECTED` hiện không chuyển đi đâu | Đúng | `ApplicationStatusService.java:33-35`: `ALLOWED_TRANSITIONS` chỉ có khoá `PENDING` (→ `INTERVIEW_INVITED`, `REJECTED`) và `INTERVIEW_INVITED` (→ `HIRED`, `REJECTED`); comment `:29-32` ghi rõ `REJECTED` "không quay lại được `INTERVIEW_INVITED`". `changeStatus` tra bảng này (`:71-74`), sai → 400 `INVALID_APPLICATION_STATUS_TRANSITION` (`GlobalExceptionHandler.java:248-251`) |
| a3 | Đổi trạng thái = `UPDATE` có điều kiện + ghi lịch sử + phát sự kiện | Đúng | `updateStatusIfCurrent` (`JobApplicationRepository.java:30-35`), 0 dòng → 409 `APPLICATION_STATUS_CONFLICT` (`ApplicationStatusService.java:76-79`, `GlobalExceptionHandler.java:256-259`); `applicationStatusRecorder.record` (`:83`); `ApplicationStatusChangedEvent` (`:88-89`) |
| a4 | `PATCH /status` chặn đặt thẳng `INTERVIEW_INVITED` | Đúng | `ApplicationStatusController.java:40-44` — guard ở controller, `sendInvitation` gọi thẳng service nên không bị chặn |
| a5 | `application_status_history` có `from_status`, `changed_at` | Đúng | `V1__init_schema.sql:228-236`: `from_status VARCHAR(25)` (NULL được), `to_status`, `changed_at TIMESTAMPTZ NOT NULL DEFAULT now()`; index `idx_status_hist_app (application_id, changed_at)` `:237`. Không migration nào sau V1 sửa bảng này |
| a6 | `changed_at` do **DB** gán, không phải JVM | Đúng | `ApplicationStatusHistory.java:50-52` (`@Generated(event = INSERT)`, `insertable = false`); `ApplicationStatusRecorder.record` không đặt thời gian (`:26-35`). Vì `now()` theo transaction, `changed_at` của dòng `PENDING → REJECTED` = thời điểm **bắt đầu** transaction từ chối |
| a7 | Mọi đường ghi lịch sử | Ba chỗ | `ApplicationService.java:118` (`NULL → PENDING`), `:146` (→ `WITHDRAWN`), `ApplicationStatusService.java:83`. Không đường nào khác ghi `REJECTED` |
| a8 | Gửi giấy mời FR-H12 đổi trạng thái trong cùng transaction | Đúng | `InterviewInvitationService.sendInvitation` (`:138-166`, `@Transactional`): quyền (`:140`) → mẫu (`:145`) → kiểm khung (`:148`) → kiểm ẩn danh **trước** đổi trạng thái (`:153-155`) → `applicationStatusService.changeStatus(…, INTERVIEW_INVITED)` (`:161`) → tạo giấy mời (`:164-165`). Chỗ chèn bước "đơn `REJECTED` có yêu cầu `OPEN` → `ACCEPTED`": giữa `:155` và `:161` (R-S3) |
| a9 | Preview giấy mời không xét trạng thái đơn | Đúng | `previewInvitation` (`:107-136`) chỉ kiểm quyền + mẫu — mở được cho đơn `REJECTED` |
| a10 | Ẩn danh của đơn suy từ lịch sử, hết khi có dòng `→ INTERVIEW_INVITED` | Đúng | `JobBlindReviewChangeRepository.findMaskedApplicationIds` (`:19-33`): Job bật công tắc **và** không có dòng `to_status = 'INTERVIEW_INVITED'`. Đơn bị từ chối ở vòng hồ sơ của Job bật ẩn danh **vẫn ẩn** |
| a11 | Che chuỗi tự do ứng viên gõ | Có khuôn | `IdentityMaskSource.load(applicationId)` (`:24-26`) / `load(Collection)` (`:29-54`, hai câu cho cả tập) → `IdentityMasker.mask` (`:61-70`, thay bằng `"[đã ẩn]"`). Dùng cho câu trả lời sàng lọc `SHORT_TEXT` (`ApplicationScreeningHrService.java:41-60`), thân tin nhắn (`MessageService.java:97-100`, `:159-173`). Nhãn `CandidateAlias.of` (`CandidateAlias.java:16-19`). Phía ứng viên không che (`MessageService.java:103-107`) |
| a12 | Xử lý văn bản tin nhắn FR-C06 | Đúng | `MessageService.normalizeLineBreaks` (`:384-390`): **chỉ** đổi `\r\n`, `\r` → `\n`, không trim/cắt/lọc; chỉ khoảng trắng (`isBlank`) → không có chữ (`:250`, `:264-265`); độ dài `String.length()` **sau** chuẩn hoá (`:256`); C06 R-M2, R-M3 (`docs/features/CHUNG/C06/REQUIREMENT.md:152-156`); CHECK `char_length` ở DB (C06 REQUIREMENT `:350`) |
| a13 | Thêm loại thông báo không cần migration | Đúng | `notifications.type VARCHAR(50) NOT NULL`, không CHECK (`V1:343`); `NotificationType.java:3-15` (7 loại, tiền lệ `NEW_MESSAGE`, `INTERVIEW_INVITATION_RESENT`, `INTERVIEW_SLOT_CONFIRMED`) |
| a14 | Mời phỏng vấn phát thông báo đổi trạng thái tới ứng viên | Đúng | `NotificationEventListener.onApplicationStatusChanged` (`:64-74`) → `forStatusChanged` (`NotificationContentBuilder.java:39-46`): "… đã chuyển sang trạng thái: Đã mời phỏng vấn", link `/candidate/applications/{id}`. Không phụ thuộc trạng thái nguồn |
| a15 | Nhãn ứng viên trong thông báo gửi HR theo ẩn danh | Có | `NotificationEventListener.candidateLabel` (`:207-212`), xét tại lúc listener chạy |
| a16 | Chi tiết đơn ứng viên (FR-U08 E1) | Có | `ApplicationCandidateDetailResponse.java:15-23`; `ApplicationService.getMyApplicationDetail` (`:177-230`); quyền `findByIdAndCandidateId` → 404 cho cả đơn người khác (`:181-183`). **Comment `:175-176` (R-D1 của FR-U08) ghi "KHÔNG đọc … lịch sử"** — FR-C09 phải đọc lịch sử để tính hạn (M1). Từ FR-U16: `job.availability` lấy qua `job/JobAvailabilityResolver.of` (`:205`), enum `JobAvailability` ở `job/` (DTO `:51`) — FR-C09 không dùng hai thứ này |
| a17 | Danh sách đơn ứng viên (FR-U03) | Có | `ApplicationSummaryResponse.java:7-14`; `findSummariesByCandidateId` (`JobApplicationRepository.java:86-98`, `ORDER BY a.applied_at DESC` **không** có khoá cuối) |
| a18 | Hồ sơ đơn HR (FR-H09 E1) | Có | `ApplicationHrDetailResponse.java:15-28`; `ApplicationHrDetailService.getDetail` (`:55-83`), ẩn danh `:64-82` |
| a19 | Kiểm quyền phía HR theo đơn | Có | `HrApplicationAccess.loadOwned` (`:42-56`): công ty (404) → đơn (404) → Job (404) → công ty khác 403. **Không lọc `jobs.deleted_at`** |
| a20 | Kiểm quyền phía HR theo tin | Có | `JobOwnerService.loadOwned` (`:200-210`), `ApplicationOwnerService.loadOwnedJob` (`:394-401`): không lọc `deleted_at`. Danh sách tin HR ẩn tin đã xoá (`JobOwnerService.java:102-103`) |
| a21 | Tin xoá mềm chỉ ghi `deleted_at` | Đúng | `JobOwnerService.delete` (`:186-192`) |
| a22 | Trang sửa tin có 5 tab | Đúng | `HrJobEditPage.tsx:480` `VALID_TABS = ['job','template','rubric','screening','applications']`; `:509-515`; `?tab=` lạ → `job` (`:491-492`) |
| a23 | Thanh thao tác H09 với đơn `REJECTED` | Một câu cố định | `ApplicationActionBar.tsx:16-24` (`REJECTED` → không nút), `:67-73` "Đơn đã có kết quả cuối. Không còn thao tác nào." — sai khi có yêu cầu `OPEN` (M3) |
| a24 | Hộp thoại giấy mời dùng được ngoài trang hồ sơ đơn | Đúng | `InterviewInvitationDialog` nhận `application {id, candidateName}`, `jobId`, `invalidateQueryKeys` (`InterviewInvitationDialog.tsx:95-104`); đã dùng ở ba điểm mở (comment `:183`) |
| a25 | Funnel FR-H08 đếm "đã từng mời" bằng `EXISTS` | Đúng | `JobApplicationRepository.countFunnelForCompany` (`:122-140`), `JobRepository.findJobPerformanceForCompany` (`:347-350`) — đơn được mời lại đếm **một** lần; số "theo trạng thái hiện tại" (`countByStatusForCompany` `:104-114`) đổi khi đơn rời `REJECTED` |
| a26 | Migration | **V21** còn trống | Cao nhất trên `main` (`0d6dbef`): `V20__saved_jobs.sql` (FR-U16) |

### (b) Mâu thuẫn và chỗ lệch

| # | Mâu thuẫn | Xử lý trong đặc tả này |
|---|---|---|
| M1 | FR-U08 R-D1 (comment `ApplicationService.java:175-176`) cấm E1 đọc lịch sử; FR-C09 cần `changed_at` của dòng `PENDING → REJECTED` để tính hạn, và cần bảng yêu cầu | **Thay đổi hành vi FR đã hoàn thành**, ghi ở R-C1: E1 đọc thêm lịch sử **của chính đơn** và bảng yêu cầu, chỉ để dựng khối `reconsideration`. Không thêm dữ liệu nội bộ nào. Sửa comment `:175-176` cùng đợt |
| M2 | `ApplicationCandidateDetailResponse` ghi "KHÔNG thêm field nào khác" (`:11-14`) | Ngoại lệ có duyệt như FR-U09: thêm đúng một khoá `reconsideration` (R-C1) |
| M3 | Thanh thao tác H09 nói "Không còn thao tác nào" cho mọi đơn `REJECTED` (a23) | Khi đơn có yêu cầu `OPEN`, câu đổi thành câu trỏ tới khối yêu cầu (R-D7, UI.md 4f). Không thêm nút vào thanh thao tác (Q8) |
| M4 | `CLAUDE.md` §2, `docs/SRS.md:102` (FR-H07), `:156` (nguyên tắc) mô tả vòng đời không có cạnh `REJECTED → INTERVIEW_INVITED` | Sửa **cùng commit đặc tả này** (kế hoạch, bảng "Tài liệu chung phải sửa", dòng D4): giữ "5 trạng thái", thêm ngoại lệ FR-C09 |
| M5 | FR-U08 R-P1 "Một nút thao tác duy nhất: Rút đơn" | FR-U08 R-P2 cho phép FR sau tự thêm phần của mình; FR-C09 thêm nút "Yêu cầu xem xét lại" ở **khối** trong tab "Thông tin đơn", không ở đầu trang (UI.md 4b) |
| M6 | FR-H12 R-F3 (`docs/features/HR/H12/REQUIREMENT.md:175-177`): "Đơn không `PENDING` → 400 `INVALID_APPLICATION_STATUS_TRANSITION`, không tạo dòng nào" | **Thay đổi hành vi FR đã hoàn thành**: đơn `REJECTED` có yêu cầu `OPEN` → I2 thành công (R-S3). Mọi ca khác của R-F3 giữ nguyên, gồm đơn `REJECTED` không có yêu cầu `OPEN` (T11); test `send_applicationAlreadyInterviewInvited_returns400` không sửa. Đợt cuối thêm dòng "*Sửa bởi FR-C09*" dưới R-F3, theo tiền lệ FR-U10 sửa FR-H12 |

Kế hoạch không nói sai điểm nào so với code. M1–M3, M5, M6 là chỗ kế hoạch không nêu, đặc tả nêu ra để duyệt.

## 1. Mục đích

Cho ứng viên bị từ chối ở vòng hồ sơ **một** lần đề nghị nhà tuyển dụng xem lại, trong thời hạn ngắn và rõ ràng; cho HR
một chỗ xử lý các đề nghị đó. Quyết định vẫn hoàn toàn là của HR (FR-H07): mời phỏng vấn, hoặc giữ nguyên từ chối.

## 2. Luồng người dùng

**Ứng viên** — `/candidate/applications/:id` (FR-U08)
1. Đơn đủ điều kiện (R-E1) hiện khối "Xem xét lại" ở tab "Thông tin đơn" với câu hạn chót (giờ, ngày) và nút "Yêu cầu
   xem xét lại".
2. Bấm nút → hộp thoại: ô lý do **không bắt buộc**, tối đa 1000 ký tự; câu nhắc chỉ gửi được một lần, gửi xong không
   sửa, không huỷ. Bấm "Gửi yêu cầu".
3. Khối đổi sang "Đang chờ xem xét lại" kèm thời điểm gửi và lý do. Đầu trang và danh sách `/candidate/applications`
   hiện badge "Bị từ chối" kèm chữ "Đang chờ xem xét lại".
4. HR quyết xong: mời phỏng vấn → đơn sang "Đã mời phỏng vấn", khối giấy mời FR-H12 xuất hiện như mọi đơn được mời,
   khối xem xét lại ghi "đã chấp nhận"; giữ từ chối → khối ghi kết quả, thời điểm và phản hồi (nếu có). Ứng viên nhận
   thông báo (R-N).
5. Quá hạn, hoặc tin đã gỡ (R-E4): khối ghi rõ lý do không gửi được, không có nút.

**HR** — tab mới ở `/hr/jobs/:id/edit` và khối ở `/hr/applications/:id`
1. Nhận thông báo "Ứng viên yêu cầu xem xét lại" (link tới hồ sơ đơn).
2. Tab "Yêu cầu xem xét lại" của tin (nhãn tab kèm số yêu cầu đang chờ): mỗi dòng có tên ứng viên (hoặc "Ứng viên #…"
   khi ẩn danh), ngày nộp đơn, ngày bị từ chối, ngày gửi yêu cầu, lý do, liên kết "Mở hồ sơ" và hai nút.
3. "Mời phỏng vấn" mở **đúng** hộp thoại giấy mời FR-H12 (1–5 khung giờ, nội dung render từ mẫu). Gửi thành công → yêu
   cầu `ACCEPTED`, đơn `INTERVIEW_INVITED`, trong cùng transaction.
4. "Giữ quyết định từ chối" mở hộp thoại phản hồi không bắt buộc (tối đa 1000 ký tự), ghi rõ phản hồi **gửi cho ứng
   viên**. Xác nhận → yêu cầu `DECLINED`, đơn vẫn `REJECTED`.
5. Hồ sơ đơn FR-H09 hiện khối yêu cầu (mọi trạng thái của yêu cầu); khi `OPEN` có cùng hai nút, dùng chung hộp thoại với
   tab.

**Hệ thống** — không gọi AI, không job nền. Mọi thao tác là request đồng bộ ngắn, chỉ ghi DB.

## 3. Quy tắc nghiệp vụ

Ánh xạ với kế hoạch: R1 → R-E1, R-E3; R2 → R-T; R3 → R-E4; R4 → R-E2; R5 → R-S; R6 → R-D; R7 → R-V; R8 → R-A;
R9 → R-C; R10 → R-N; R11 → mục 5.

### 3.1 Điều kiện gửi (R-E)

- **R-E1. Đủ điều kiện** khi **đồng thời**: (a) đơn của chính ứng viên đang đăng nhập; (b) `job_applications.status =
  'REJECTED'`; (c) dòng lịch sử chuyển **vào** `REJECTED` mới nhất của đơn (`ORDER BY changed_at DESC, id DESC`) có
  `from_status = 'PENDING'`; (d) đơn chưa có dòng nào trong `application_reconsideration_requests`; (e) Job của đơn chưa
  xoá mềm (R-E4); (f) còn hạn (R-T).
- **R-E2. Một lần cho mỗi đơn**, chốt ở DB bằng `UNIQUE (application_id)` (`uq_reconsideration_per_application`, mục
  4.1). Service kiểm trước chỉ để trả 409 sớm. Hai request đồng thời: cả hai qua bước kiểm trước (chưa thấy dòng của
  nhau); `INSERT` của request sau chờ ở unique index tới khi request trước commit rồi văng `unique_violation` →
  `DataIntegrityViolationException` → `GlobalExceptionHandler.handleDataIntegrityViolation` nhận ra tên constraint →
  **409 `RECONSIDERATION_ALREADY_REQUESTED`** với câu cố định ở mục 4.3 — **không** phải 500, không lộ thông báo lỗi của
  Postgres. Request thua không ghi gì, không phát thông báo (sự kiện chỉ xử lý `AFTER_COMMIT`). Bị giữ từ chối
  (`DECLINED`) hoặc đã được chấp nhận (`ACCEPTED`) thì không gửi lại được. Test T5.
- **R-E3. Từ chối sau phỏng vấn** (`INTERVIEW_INVITED → REJECTED`) → không đủ điều kiện (409
  `RECONSIDERATION_NOT_ELIGIBLE`). Đơn `REJECTED` không có dòng lịch sử vào `REJECTED` (dữ liệu ghi tay) → cũng không đủ
  điều kiện.
- **R-E4. Tình trạng tin**: tin quá hạn nộp, `PAUSED`, `CLOSED` vẫn gửi được. Tin **đã xoá mềm** (`jobs.deleted_at IS
  NOT NULL`) → 409 `RECONSIDERATION_JOB_REMOVED` (đã chốt Q1, 30/10).
- **R-E5. Thứ tự kiểm của C1** (mục 4.3), trả lỗi đầu tiên gặp: quyền (role → đơn của chính mình, 404) → đã có yêu cầu
  (409 `RECONSIDERATION_ALREADY_REQUESTED`) → R-E1 b/c (409 `RECONSIDERATION_NOT_ELIGIBLE`) → tin đã xoá (409
  `RECONSIDERATION_JOB_REMOVED`) → hạn (409 `RECONSIDERATION_DEADLINE_PASSED`) → văn bản (400
  `RECONSIDERATION_TEXT_TOO_LONG`, R-V). Theo khuôn C06 R-M5: trạng thái trước nội dung.

### 3.2 Hạn 7 ngày (R-T)

- **R-T1.** `deadline = changed_at + interval '168 hours'` của dòng lịch sử ở R-E1(c). Dùng `'168 hours'`, **không**
  `'7 days'`: phép cộng `'7 days'` vào `timestamptz` đi theo lịch của `TimeZone` phiên và lệch một giờ ở ngày đổi giờ
  mùa hè; 168 giờ luôn đúng 7 × 24 giờ.
- **R-T2. Còn hạn ⇔ `now() <= deadline`**, so bằng `now()` của Postgres **trong câu SQL**. Đúng mốc là còn hạn; sau mốc
  dù chỉ 1 micro giây là quá hạn (Q6). Java **không** so sánh thời gian để quyết định hạn (không `Instant.now()`,
  `LocalDate`, `Clock`).
- **R-T3.** Câu ghi yêu cầu là **một** câu `INSERT … SELECT … WHERE <R-E1 b, c, e> AND now() <= deadline` (native);
  0 dòng → service đọc lại để trả đúng mã theo R-E5. Bước kiểm trước (R-E5) và câu ghi dùng cùng biểu thức hạn, nên một
  request chạm mốc giữa hai bước chỉ có thể rơi vào nhánh 0 dòng → 409 `RECONSIDERATION_DEADLINE_PASSED`, không ghi
  được yêu cầu quá hạn.
- **R-T4.** `deadline` trả ra API (C1, E1 ứng viên) là giá trị đã tính ở backend; giao diện chỉ hiển thị, không tự tính
  còn/hết hạn theo đồng hồ máy. Trạng thái `AVAILABLE`/`EXPIRED` của khối (R-C1) cũng tính bằng `now()` trong SQL.
- **R-T5.** Hạn không dừng khi tin tạm dừng/đóng, không gia hạn, HR không đổi được.

### 3.3 Chuyển trạng thái đơn (R-S)

- **R-S1. Đúng một cạnh mới** `REJECTED → INTERVIEW_INVITED`, **chỉ** chạy trong `InterviewInvitationService.sendInvitation`
  (I2 của FR-H12) và **chỉ** sau khi đã chuyển được yêu cầu `OPEN` của chính đơn sang `ACCEPTED` trong cùng transaction.
- **R-S2. Không đổi bảng chuyển tiếp chung**: `ALLOWED_TRANSITIONS` (`ApplicationStatusService.java:33-35`) giữ nguyên
  từng giá trị; `ApplicationStatus` giữ 5 giá trị; `PATCH /status` giữ guard `INTERVIEW_INVITED` (a4). Đường đổi trạng
  thái riêng cho FR-C09 là một method **riêng** (đề xuất: `ApplicationStatusService.reinviteAfterReconsideration(ownerId,
  applicationId)`, Plan Mode chốt tên) cố định nguồn `REJECTED`, đích `INTERVIEW_INVITED`: `updateStatusIfCurrent(id,
  REJECTED, INTERVIEW_INVITED)` (0 dòng → 409 `APPLICATION_STATUS_CONFLICT`) → `record(id, REJECTED, INTERVIEW_INVITED,
  ownerId, null)` → `ApplicationStatusChangedEvent(…, REJECTED, INTERVIEW_INVITED)`. Có test canh nguồn: method này chỉ
  được gọi từ `InterviewInvitationService` (khuôn `AiOutputConvertersSourceGuardTest`).
- **R-S3. Thứ tự trong `sendInvitation`** — giữ nguyên các bước có sẵn tới bước kiểm ẩn danh (a8, `:140-155`), rồi:
  - Đơn đọc ở bước quyền **khác** `REJECTED` → nhánh cũ, không đổi gì (`changeStatus(…, INTERVIEW_INVITED)`).
  - Đơn `REJECTED` → (0) đọc trạng thái yêu cầu của đơn; không có yêu cầu, hoặc yêu cầu `ACCEPTED`/`DECLINED` → **400
    `INVALID_APPLICATION_STATUS_TRANSITION`** với đúng câu lỗi hiện nay
    (`InvalidApplicationStatusTransitionException("REJECTED", "INTERVIEW_INVITED")`), không ghi gì; (1) yêu cầu đọc được
    là `OPEN` → `UPDATE application_reconsideration_requests SET status = 'ACCEPTED', decided_at = now(), decided_by =
    :ownerId WHERE application_id = :id AND status = 'OPEN'` (`@Modifying(clearAutomatically = true, flushAutomatically
    = true)`); **0 dòng** (yêu cầu vừa bị quyết ở request khác giữa (0) và (1)) → **409 `RECONSIDERATION_NOT_OPEN`**
    (R-D3), không ghi gì; (2) R-S2; (3) tạo giấy mời như cũ.
  - Bước kiểm ẩn danh (`:153-155`) vẫn chạy **trước** (1)–(2): đơn ẩn danh được thay `{{candidateName}}` bằng họ tên
    như FR-H16 R-I2.
- **R-S4. Không thêm thông báo** khi chấp nhận: `ApplicationStatusChangedEvent` ở R-S2 tạo đúng một thông báo
  `APPLICATION_STATUS_CHANGED` như mọi lần mời (a14). FR-C09 không phát sự kiện nào khác ở I2.
- **R-S5. Sau khi được mời lại**, đơn đi tiếp theo máy trạng thái hiện có: `INTERVIEW_INVITED → HIRED | REJECTED`, rút
  đơn được (FR-U06), gửi lại giấy mời được (FR-H12 I3). Bị từ chối lần nữa (từ `INTERVIEW_INVITED`) thì không gửi yêu
  cầu được nữa (R-E2, R-E3).
- **R-S6. Hệ quả thống kê** (FR-H08, không sửa code dashboard): funnel "đã từng mời" tự tính cả đơn được mời lại, mỗi
  đơn một lần (a25); bảng "theo trạng thái hiện tại" đổi từ `REJECTED` sang `INTERVIEW_INVITED`; lịch sử đơn có thêm dòng
  `REJECTED → INTERVIEW_INVITED`.

### 3.4 Quyết định của HR (R-D)

- **R-D1. Chấp nhận = Mời phỏng vấn** qua I2 (R-S3). Không có endpoint "accept" riêng, không có trạng thái "chấp nhận
  nhưng chưa mời".
- **R-D2. Giữ từ chối** (H2, mục 4.3): một câu `UPDATE … SET status = 'DECLINED', hr_response = :response, decided_at =
  now(), decided_by = :ownerId WHERE application_id = :id AND status = 'OPEN'`. 0 dòng → 409 `RECONSIDERATION_NOT_OPEN`
  (không có yêu cầu, đã `ACCEPTED`, đã `DECLINED`). Đơn không đổi trạng thái, không ghi lịch sử trạng thái.
- **R-D3. Tranh chấp — chỉ một bên thắng.** Các ca: chấp nhận (I2) và giữ từ chối (H2) cùng lúc; hai lần giữ từ chối;
  hai lần chấp nhận; bấm trùng, hai tab, hai phiên đăng nhập. ("Hai HR" của cùng công ty thực chất là cùng một tài khoản
  chủ công ty ở hai nơi: mỗi công ty đúng một chủ — `uq_company_per_owner`, `V2__company_unique_owner.sql:2`.) Mọi đường
  quyết định đều là `UPDATE … WHERE application_id = :id AND status = 'OPEN'` trên **cùng một hàng**; Postgres (READ
  COMMITTED) bắt lượt sau chờ khoá hàng rồi đánh giá lại `WHERE` trên bản đã commit → 0 dòng. **Lượt thua luôn nhận 409
  `RECONSIDERATION_NOT_OPEN`** với câu cố định ở mục 4.3, không ghi gì (I2 thua thì không tạo giấy mời, không đổi trạng
  thái đơn, không thông báo; H2 thua thì không ghi phản hồi, không thông báo). Không dùng `SELECT … FOR UPDATE`/`@Lock`.
  Phân biệt với R-S3 (0): I2 gửi **sau** khi yêu cầu đã quyết xong (không tranh chấp) → 400 như FR-H12 hiện nay; I2
  **thua tranh chấp** (đọc thấy `OPEN` nhưng `UPDATE` 0 dòng) → 409. Test T15.
- **R-D4. Quyết định là cuối**: không sửa phản hồi, không đổi `DECLINED` ↔ `ACCEPTED`, không mở lại.
- **R-D5. Quyền**: H1, H2 và nhánh R-S3 chỉ cho HR **chủ công ty của Job của đơn**. H2 dùng `HrApplicationAccess.loadOwned`
  (404 `COMPANY_NOT_FOUND` / 404 `APPLICATION_NOT_FOUND` / 403 công ty khác); H1 dùng `ApplicationOwnerService.loadOwnedJob`
  (404 `JOB_NOT_FOUND` / 403). I2 giữ method quyền hiện có (FR-H12 R-A4).
- **R-D6. Không có quyết định tự động**: không job nền tự `DECLINED` khi quá lâu, không tự đóng khi tin bị gỡ (Q2).
- **R-D7. Thanh thao tác FR-H07 ở hồ sơ đơn (M3, Q8)**: không thêm nút nào. Khi đơn `REJECTED` **và**
  `reconsideration.status = 'OPEN'` (E1 của FR-H09), thanh thao tác **không** hiện "Đơn đã có kết quả cuối. Không còn thao
  tác nào." mà hiện "Ứng viên đã gửi yêu cầu xem xét lại. Xử lý ở khối bên dưới." (UI.md 4f). Đơn `REJECTED` không có
  yêu cầu, hoặc yêu cầu `ACCEPTED`/`DECLINED` → giữ câu cũ. Đơn `WITHDRAWN` giữ câu cũ của nó. Sau khi chấp nhận, đơn
  `INTERVIEW_INVITED` hiện lại hai nút "Trúng tuyển", "Từ chối" như mọi đơn đã mời. Kiểm ở mục 7.2, 7.3.

### 3.5 Lý do và phản hồi (R-V)

- **R-V1. Văn bản thuần**, xử lý **giống nội dung tin nhắn FR-C06** (a12): chuẩn hoá duy nhất `\r\n`, `\r` → `\n`;
  không trim, không cắt, không lọc ký tự, không escape khi lưu. Chuỗi `null`, thiếu, hoặc chỉ gồm khoảng trắng
  (`isBlank()`) → lưu `NULL` (cả hai ô đều không bắt buộc).
- **R-V2. Độ dài** sau chuẩn hoá tối đa **1000** (`String.length()`, cách đếm của C06 R-M3); 1000 hợp lệ, 1001 → 400
  `RECONSIDERATION_TEXT_TOO_LONG`, không ghi gì. DB có CHECK `char_length(...) <= 1000` (luôn rộng hơn hoặc bằng giới
  hạn Java, như `chk_app_msg_body_length` của C06).
- **R-V3.** Lý do lưu ở `reason`, phản hồi ở `hr_response` — **nguyên văn**, không đổi khi tin/mẫu bị sửa. Phản hồi là
  **nội dung gửi ứng viên**, không phải ghi chú nội bộ; giao diện phải ghi rõ (UI.md mục 7).
- **R-V4.** Giao diện hiện văn bản bằng `whitespace-pre-wrap break-words`, không render HTML/Markdown, không
  `dangerouslySetInnerHTML`.

### 3.6 Ẩn danh FR-H16 (R-A)

- **R-A1.** Ẩn danh của đơn chỉ hỏi `BlindReviewResolver` (a10), không viết điều kiện thứ hai. Đơn bị từ chối ở vòng hồ
  sơ của Job bật ẩn danh thì **đang ẩn** khi HR xem yêu cầu.
- **R-A2.** Đơn đang ẩn: **mọi** response phía HR có khối yêu cầu — H1 (tab danh sách), khối `reconsideration` ở E1
  của FR-H09, và response của H2 — trả `candidateName = CandidateAlias.of(applicationId)`, `identityMasked = true`;
  `reason` **và** `hrResponse` đi qua `IdentityMasker.mask` (khuôn tin nhắn — che mọi văn bản trên đơn ẩn danh, kể cả văn
  bản HR viết, a11). H1 dùng `maskedAmong` + `IdentityMaskSource.load(Collection)` cho cả danh sách (không N+1).
- **R-A2a. Bản gốc chỉ ở DB.** Cột `reason`/`hr_response` lưu nguyên văn; không ghi chuỗi đã che xuống DB. Khi đơn đang
  ẩn, **không** endpoint nào dưới `/api/hr/**` trả bản gốc của `reason` (không trường phụ, không endpoint "xem bản gốc",
  không đưa vào thông báo — R-A3). Việc che làm ở service phía HR **trước** khi dựng DTO; DTO HR không có thành phần nào
  chứa văn bản chưa che. Test T18.
- **R-A3.** Thông báo `RECONSIDERATION_REQUESTED` dùng `candidateLabel` của listener (a15) và **không** chứa lý do (tiền
  lệ FR-H16 R-O2: không đưa văn bản ứng viên gõ vào thông báo của đơn ẩn danh).
- **R-A4.** Mời phỏng vấn xong thì đơn hết ẩn theo luật FR-H16 hiện có (dòng lịch sử `→ INTERVIEW_INVITED`); FR-C09
  không thêm luật nào.
- **R-A5.** Phía ứng viên không xét ẩn danh (FR-H16 R-U1).

### 3.7 Phía ứng viên thấy gì (R-C)

- **R-C1. Khối `reconsideration` ở E1 ứng viên** (`GET /api/candidates/applications/{id}`, M1, M2) — `null` hoặc một
  object có `state` thuộc đúng 6 giá trị:

  | `state` | Khi nào | Thành phần khác |
  |---|---|---|
  | `AVAILABLE` | Không có yêu cầu; R-E1 b, c, e đúng; còn hạn | `deadline` |
  | `EXPIRED` | Như trên nhưng quá hạn | `deadline` |
  | `JOB_REMOVED` | Không có yêu cầu; R-E1 b, c đúng; tin đã xoá mềm (Q1) | `deadline` |
  | `OPEN` | Có yêu cầu `OPEN` | `deadline`, `requestedAt`, `reason` |
  | `ACCEPTED` | Có yêu cầu `ACCEPTED` | + `decidedAt` |
  | `DECLINED` | Có yêu cầu `DECLINED` | + `decidedAt`, `hrResponse` |

  `null` khi không có yêu cầu và R-E1 b hoặc c sai (đơn chưa từng bị từ chối ở vòng hồ sơ, bị từ chối sau phỏng vấn,
  đang `PENDING`/`INTERVIEW_INVITED`/`HIRED`/`WITHDRAWN`). Có yêu cầu thì khối luôn hiện, **mọi** trạng thái đơn (vd đơn
  đã được mời lại rồi trúng tuyển vẫn thấy `ACCEPTED`).
- **R-C2. Danh sách đơn** (`GET /api/candidates/applications`): mỗi phần tử thêm `reconsiderationStatus` (`null` |
  `OPEN` | `ACCEPTED` | `DECLINED`). Vẫn **một** câu SQL (`LEFT JOIN`, không N+1); `ORDER BY a.applied_at DESC, a.id
  DESC` (thêm khoá cuối, a17).
- **R-C3. Không lộ dữ liệu nội bộ**: DTO ứng viên **không** có `decidedBy`, id người HR, điểm, rubric, giải thích AI,
  ghi chú HR, `identityMasked`. DTO riêng, không tái dùng DTO phía HR (khuôn FR-U08 mục 0(f)).
- **R-C4. Quyền C1**: `findByIdAndCandidateId` — đơn không tồn tại và đơn của ứng viên khác **cùng** 404
  `APPLICATION_NOT_FOUND` (quy ước FR-U08 R-Q2). HR gọi `/api/candidates/**` → 403 (role).

### 3.8 Thông báo (R-N)

- **R-N1.** Hai loại mới trong `NotificationType`: `RECONSIDERATION_REQUESTED`, `RECONSIDERATION_DECLINED`. Không
  migration (a13). Hai sự kiện mới khai trong gói `notification/` (`ReconsiderationRequestedEvent(applicationId, jobId,
  candidateId)`, `ReconsiderationDeclinedEvent(applicationId, jobId, candidateId)`), phát **trong** transaction C1/H2;
  listener `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`, lỗi không văng ra ngoài
  (khuôn `onApplicationWithdrawn`). Gói `notification/` **không** import `reconsideration/`.
- **R-N2. `RECONSIDERATION_REQUESTED`** — người nhận: chủ công ty của Job (`company.ownerId`). Tiêu đề "Ứng viên yêu cầu
  xem xét lại"; nội dung một dòng `{candidateLabel} đã gửi yêu cầu xem xét lại đơn ứng tuyển vị trí "{tiêu đề tin}".`;
  link `/hr/applications/{applicationId}`. Không chứa lý do (R-A3). Email = nội dung (không `email_body`).
- **R-N3. `RECONSIDERATION_DECLINED`** — người nhận: ứng viên. Tiêu đề "Kết quả yêu cầu xem xét lại"; nội dung `Nhà
  tuyển dụng đã xem xét lại và giữ quyết định từ chối đơn ứng tuyển vị trí "{tiêu đề tin}". Xem chi tiết trong đơn ứng
  tuyển.`; link `/candidate/applications/{applicationId}`. **Không** chứa phản hồi (Q5) — ứng viên đọc phản hồi ở trang
  chi tiết đơn.
- **R-N4. Chấp nhận** chỉ có thông báo đổi trạng thái sẵn có (R-S4). Đếm: chấp nhận = đúng 1 thông báo mới cho ứng viên,
  0 cho HR.
- **R-N5.** Không thông báo nào chứa điểm, rubric, giải thích AI hay ghi chú HR (`NotificationContentBuilder.java:12-14`).

## 4. Dữ liệu & quyền truy cập

### 4.1 Migration `V21__application_reconsideration_requests.sql`

Nội dung dự kiến; đợt code có thể sửa cú pháp nhưng không đổi ràng buộc.

```sql
-- FR-C09 - Yeu cau xem xet lai don bi tu choi (REQUIREMENT.md muc 4.1). Don van dung 5 trang thai; yeu cau nam o bang
-- rieng. Quy uoc CHECK (FR-H10 L7): Postgres coi CHECK ra NULL la DAT - cot duoc NULL co chu dich phai xu ly tuong minh.
CREATE TABLE application_reconsideration_requests (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- RESTRICT: don khong bao gio xoa cung; script reset du lieu test phai xoa bang nay truoc (tien le V17).
    application_id UUID NOT NULL REFERENCES job_applications(id) ON DELETE RESTRICT,
    reason        TEXT,                                   -- NULL = ung vien khong nhap (R-V1)
    status        VARCHAR(10) NOT NULL DEFAULT 'OPEN'
                  CHECK (status IN ('OPEN', 'ACCEPTED', 'DECLINED')),
    hr_response   TEXT,                                   -- chi khi DECLINED, NULL = HR khong nhap
    requested_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at    TIMESTAMPTZ,
    decided_by    UUID REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uq_reconsideration_per_application UNIQUE (application_id),                     -- R-E2
    CONSTRAINT chk_reconsideration_reason_length
        CHECK (reason IS NULL OR char_length(reason) <= 1000),
    CONSTRAINT chk_reconsideration_response_length
        CHECK (hr_response IS NULL OR char_length(hr_response) <= 1000),
    -- OPEN <=> chua quyet; da quyet thi co ca thoi diem lan nguoi quyet.
    CONSTRAINT chk_reconsideration_decision
        CHECK ((status = 'OPEN') = (decided_at IS NULL) AND (decided_at IS NULL) = (decided_by IS NULL)),
    CONSTRAINT chk_reconsideration_response_declined
        CHECK (hr_response IS NULL OR status = 'DECLINED')
);
```

- **Không có cột `job_id`** (Q4): suy từ `job_applications.job_id` (bất biến); H1 join qua `idx_app_job` (`V1:224`) +
  `uq_reconsideration_per_application`. Không index thêm.
- **Không `updated_at`, không trigger, không `deleted_at`**: mỗi dòng chỉ được ghi một lần sau khi tạo (OPEN → quyết);
  không có đường xoá (CLAUDE.md §4 — soft delete chỉ áp khi có thao tác xoá).
- Không cột tên `verdict`, `label`, `isQualified`, `passed`, `recommendation` (CLAUDE.md §7).
- Lỗi vi phạm `uq_reconsideration_per_application` → `GlobalExceptionHandler.handleDataIntegrityViolation` thêm nhánh
  theo tên constraint → 409 `RECONSIDERATION_ALREADY_REQUESTED`.

### 4.2 Package và file backend

| File | Vai trò |
|---|---|
| `reconsideration/ReconsiderationRequest.java` | Entity; `status` là `ReconsiderationStatus`; `requestedAt` `@Generated(INSERT)` |
| `reconsideration/ReconsiderationStatus.java` | `OPEN`, `ACCEPTED`, `DECLINED` |
| `reconsideration/ReconsiderationRequestRepository.java` | `insertIfEligible` (R-T3), `acceptIfOpen` (R-S3), `declineIfOpen` (R-D2), câu đọc khối ứng viên/HR, câu danh sách H1 |
| `reconsideration/ReconsiderationCandidateService.java` + controller | C1 |
| `reconsideration/ReconsiderationHrService.java` + controller | H1, H2; `claimForAcceptance(applicationId, ownerId)` cho I2 |
| `reconsideration/dto/*` | DTO mục 4.4; DTO ứng viên và DTO HR tách riêng |
| `common/exception/*` | Exception cho 6 mã lỗi mới (mục 4.3) + nhánh `GlobalExceptionHandler` (gồm nhánh dịch `uq_reconsideration_per_application`) |
| `jobapplication/ApplicationStatusService.java` | Method riêng của R-S2; **không** sửa `ALLOWED_TRANSITIONS` |
| `interviewinvitation/InterviewInvitationService.java` | Nhánh `REJECTED` của R-S3 |
| `jobapplication/ApplicationService.java`, `ApplicationHrDetailService.java`, `JobApplicationRepository.java` | Khối `reconsideration` (R-C1, H09), `reconsiderationStatus` (R-C2) |
| `notification/*` | Hai loại, hai sự kiện, hai hàm builder, hai listener (R-N) |

Phụ thuộc gói: `reconsideration/` → `jobapplication/`, `blindreview/`, `notification/`; `interviewinvitation/` →
`reconsideration/`; `jobapplication/` **không** import `reconsideration/` ngoài phần đọc khối (Plan Mode chốt cách
tránh vòng: đọc khối bằng câu SQL trong `JobApplicationRepository` hoặc gọi một reader của `reconsideration/`).

### 4.3 Endpoint

| Mã | Method + path | Vai trò | Thành công | Lỗi |
|---|---|---|---|---|
| C1 | `POST /api/candidates/applications/{id}/reconsideration-request` body `{ "reason": string \| null }` | Ứng viên | 201 + khối ứng viên (`state = OPEN`) | 404 `APPLICATION_NOT_FOUND`; 409 `RECONSIDERATION_ALREADY_REQUESTED` · `RECONSIDERATION_NOT_ELIGIBLE` · `RECONSIDERATION_JOB_REMOVED` · `RECONSIDERATION_DEADLINE_PASSED`; 400 `RECONSIDERATION_TEXT_TOO_LONG` |
| H1 | `GET /api/hr/jobs/{jobId}/reconsideration-requests?status=OPEN\|ALL` (tham số chuỗi; thiếu hoặc giá trị lạ → `OPEN`, theo tiền lệ `ApplicationSortOption.fromParam` — `ApplicationSortOption.java:17-22`) | HR | 200 `{ jobId, openCount, items[] }` | 404 `COMPANY_NOT_FOUND` · `JOB_NOT_FOUND`; 403 |
| H2 | `POST /api/hr/applications/{id}/reconsideration-request/decline` body `{ "response": string \| null }` | HR | 200 + khối HR (`status = DECLINED`) | 404 `COMPANY_NOT_FOUND` · `APPLICATION_NOT_FOUND`; 403; 409 `RECONSIDERATION_NOT_OPEN`; 400 `RECONSIDERATION_TEXT_TOO_LONG` |
| I2 | `POST /api/hr/applications/{id}/interview-invitation` (có sẵn) | HR | 201 như cũ | Như cũ; đơn `REJECTED` không có yêu cầu, hoặc yêu cầu `ACCEPTED`/`DECLINED` → 400 `INVALID_APPLICATION_STATUS_TRANSITION` như cũ; thua tranh chấp (R-D3) → 409 `RECONSIDERATION_NOT_OPEN` |

Thứ tự kiểm H2: quyền → văn bản (400) → `UPDATE` có điều kiện (409). Câu lỗi tiếng Việt cố định:

| Mã | `message` |
|---|---|
| `RECONSIDERATION_ALREADY_REQUESTED` | "Bạn đã gửi yêu cầu xem xét lại cho đơn này. Mỗi đơn chỉ gửi được một lần." |
| `RECONSIDERATION_NOT_ELIGIBLE` | "Chỉ đơn bị từ chối ở vòng hồ sơ mới gửi được yêu cầu xem xét lại." |
| `RECONSIDERATION_JOB_REMOVED` | "Tin tuyển dụng của đơn này đã bị gỡ nên không gửi được yêu cầu xem xét lại." |
| `RECONSIDERATION_DEADLINE_PASSED` | "Đã quá hạn 7 ngày để gửi yêu cầu xem xét lại." |
| `RECONSIDERATION_TEXT_TOO_LONG` | "Nội dung tối đa 1000 ký tự." |
| `RECONSIDERATION_NOT_OPEN` | "Yêu cầu xem xét lại này không còn chờ xử lý. Vui lòng tải lại trang." |

### 4.4 DTO

```java
// Phia ung vien (C1, E1 ung vien). KHONG decidedBy, KHONG identityMasked (R-C3).
record CandidateReconsiderationResponse(
        CandidateReconsiderationState state, // AVAILABLE | EXPIRED | JOB_REMOVED | OPEN | ACCEPTED | DECLINED
        Instant deadline,                    // luon co khi khoi khac null (R-T4)
        Instant requestedAt, String reason,  // null khi chua gui
        Instant decidedAt, String hrResponse) {}

// Phia HR: khoi o E1 cua FR-H09 (null khi don khong co yeu cau) va moi phan tu cua H1.
record HrReconsiderationResponse(
        UUID applicationId, String candidateName, boolean identityMasked,
        Instant appliedAt, Instant rejectedAt, Instant requestedAt,
        String reason,                       // da che khi an danh (R-A2)
        ReconsiderationStatus status, Instant decidedAt, String hrResponse) {}

record HrReconsiderationListResponse(UUID jobId, int openCount, List<HrReconsiderationResponse> items) {}
```

- `ApplicationCandidateDetailResponse` thêm thành phần cuối `CandidateReconsiderationResponse reconsideration`;
  `ApplicationSummaryResponse` thêm `ReconsiderationStatus reconsiderationStatus`; `ApplicationHrDetailResponse` thêm
  `HrReconsiderationResponse reconsideration`.
- `rejectedAt` = `changed_at` của dòng lịch sử ở R-E1(c). H1 sắp `requested_at ASC, application_id ASC` (chờ lâu nhất ở
  trên, khoá cuối duy nhất). `openCount` đếm yêu cầu `OPEN` của tin, không phụ thuộc bộ lọc. H1 không phân trang (một tin
  có số yêu cầu ≤ số đơn, cùng cách tab "Ứng viên" không phân trang).
- H1 lọc theo `ja.job_id = :jobId`, **mọi** chu kỳ tuyển dụng của tin.

### 4.5 Seed (đợt cuối)

- Demo (S1 đã duyệt 30/10/2026; cụ thể ở mục 11 "Sau Plan Mode", L9): thêm **Phần B** vào **tầng 3**
  `db/seed/seed-demo-structural-3.sql` (không tạo tầng riêng), dải `d3a00000-`, mã bảng mới `0021`, mốc tương đối theo
  `now()`: B1 — đơn bị từ chối ở vòng hồ sơ `now() - 1 day`, chưa có yêu cầu (ứng viên tự gửi); B2 — đơn bị từ chối
  `now() - 2 days` kèm yêu cầu `OPEN` (HR xử lý). Không sửa tầng 1/1b/2/2b.
- `seed-test.sql`: đơn bị từ chối 6 ngày 23 giờ (còn hạn), 8 ngày (quá hạn), bị từ chối sau phỏng vấn; một yêu cầu
  `DECLINED` có phản hồi.
- `reset-test-data.sql` xoá `application_reconsideration_requests` của đơn test **trước** câu xoá đơn (FK RESTRICT);
  `reset-demo-db.sql` + `db/seed/README.md` cập nhật tới V21 (31 → 32 bảng nghiệp vụ); chốt chặn tầng 3 nâng từ
  "V20 trở lên" lên "V21 trở lên". Chốt chặn tầng 1b ("V19 trở lên" từ FR-U16 B11) **không** sửa.

## 5. AI

Không dùng AI (kế hoạch R11). Không gọi `ChatModel`/`EmbeddingModel` ở bất kỳ endpoint nào của FR này; không AI gợi ý
chấp nhận hay từ chối, không AI soạn lý do hay phản hồi, không nhãn "nên xem lại". FR-C07 (soạn nháp tin nhắn) không có
tình huống mới.

## 6. Ngoài phạm vi

- Yêu cầu cho đơn bị từ chối **sau phỏng vấn** (R-E3).
- Ứng viên sửa hoặc huỷ yêu cầu (Q3); HR sửa phản hồi hay đổi quyết định (R-D4).
- Gửi lại yêu cầu sau `DECLINED`; nhiều yêu cầu cho một đơn.
- HR gia hạn hay rút ngắn thời hạn; thời hạn cấu hình theo công ty.
- Tự động `DECLINED` khi quá lâu hoặc khi tin bị gỡ (R-D6).
- Thống kê yêu cầu trên dashboard FR-H08; cột hay bộ lọc "đang chờ xem xét lại" ở tab "Ứng viên", `/hr/candidates`,
  hộp thư.
- AI hỗ trợ dưới mọi hình thức (mục 5).
- Phản hồi trong nội dung thông báo/email (Q5).
- Sửa code dashboard: funnel tự tính đơn được mời lại (R-S6).

### Giới hạn đã biết

- **Yêu cầu `OPEN` khi tin bị gỡ sau đó (Q2).** HR xử lý ở hồ sơ đơn FR-H09, vào bằng link của thông báo
  `RECONSIDERATION_REQUESTED` (`/hr/applications/{id}`). Khối H09, I2, H2 vẫn chạy vì `HrApplicationAccess.loadOwned`
  (`:42-56`) và `InterviewInvitationService.loadOwnedApplication` (`:412-424`) không lọc `jobs.deleted_at`. Tab "Yêu cầu
  xem xét lại" của tin đã gỡ vẫn mở được bằng URL (`JobOwnerService.loadOwned` không lọc) nhưng không còn lối vào từ
  danh sách tin HR (`JobOwnerService.java:102-103`). Yêu cầu **không** tự `DECLINED`; nếu HR không mở thông báo, yêu cầu
  ở `OPEN` vô thời hạn.

## 7. Xong khi

### 7.1 Test backend (Testcontainers, full `./mvnw test` xanh)

Biên thời gian dùng **cùng một transaction** cho bước dựng dữ liệu và lời gọi service (`TransactionTemplate`; service
`@Transactional` tham gia transaction ngoài), vì `now()` theo transaction (CLAUDE.md §3c): backdate `changed_at` bằng
native `UPDATE … SET changed_at = now() - interval '168 hours' (+/-) interval '1 second'` rồi gọi C1 ngay trong
transaction đó — `now()` của câu kiểm bằng đúng `now()` lúc backdate.

| # | Ca | Loại |
|---|---|---|
| T1 | Đơn `PENDING → REJECTED`, `changed_at = now() - 168h + 1s` → C1 201, `state = OPEN`, dòng có `reason` nguyên văn, `requested_at` khác `NULL` | biên −1 s, dương |
| T2 | `changed_at = now() - 168h` (đúng hạn) → 201 | biên đúng hạn |
| T3 | `changed_at = now() - 168h - 1s` → 409 `RECONSIDERATION_DEADLINE_PASSED`, không dòng nào | biên +1 s, âm |
| T4 | Gửi lần hai (cùng đơn) → 409 `RECONSIDERATION_ALREADY_REQUESTED`, vẫn 1 dòng; sau `DECLINED` gửi lại → cùng 409 | âm |
| T5 | Hai C1 đồng thời (hai thread, hai transaction, `CountDownLatch` để cả hai qua bước kiểm trước rồi mới `INSERT`; lớp test **không** `@Transactional`) → đúng một 201 và một 409 `RECONSIDERATION_ALREADY_REQUESTED` với đúng câu ở mục 4.3; **không** response 500; 1 dòng trong bảng; đúng 1 thông báo `RECONSIDERATION_REQUESTED` | đồng thời + âm |
| T6 | Đơn `PENDING → INTERVIEW_INVITED → REJECTED` → 409 `RECONSIDERATION_NOT_ELIGIBLE`; đơn `PENDING`, `INTERVIEW_INVITED`, `HIRED`, `WITHDRAWN` → cùng 409 | âm |
| T7 | Tin `EXPIRED` (hạn nộp hôm qua), `PAUSED`, `CLOSED` → 201; tin đã xoá mềm → 409 `RECONSIDERATION_JOB_REMOVED` (theo Q1) | dương + âm |
| T8 | Văn bản: `reason` 1000 ký tự → 201; 999 → 201; 1001 → 400 `RECONSIDERATION_TEXT_TOO_LONG`, không dòng; 999 ký tự + `\r\n` → 201, lưu 1000 ký tự kết thúc `\n`; `"  <b>x</b> & y\r\n"` lưu `"  <b>x</b> & y\n"`; `"   "` / `null` / thiếu khoá → 201, `reason` `NULL` | biên + dương |
| T9 | Quyền C1: đơn của ứng viên khác → 404 `APPLICATION_NOT_FOUND` (cùng mã với id không tồn tại); HR gọi C1 → 403; khách → 401 | âm |
| T10 | Chấp nhận: đơn `REJECTED` + yêu cầu `OPEN` → I2 201; trong DB: đơn `INTERVIEW_INVITED`, yêu cầu `ACCEPTED` với `decided_at`, `decided_by` = HR, 1 giấy mời hiệu lực, 1 dòng lịch sử `REJECTED → INTERVIEW_INVITED` có `changed_by` = HR | dương |
| T10b | Rollback nhánh chấp nhận (B1): đơn `REJECTED` + yêu cầu `OPEN` + **một giấy mời hiệu lực ghi tay** cho đơn đó (`superseded_at IS NULL`) → I2 qua bước claim (R-S3 (1)) và R-S2, rồi `createInvitation` `saveAndFlush` (`InterviewInvitationService.java:260`) vi phạm `uq_invitation_active` (`V16:48`) → **409 `INTERVIEW_INVITATION_CONFLICT`** đúng câu như FR-H12 hiện nay (`GlobalExceptionHandler.java:628-632`). Trong DB: yêu cầu vẫn `OPEN`, `decided_at`/`decided_by` `NULL`; đơn vẫn `REJECTED`; số giấy mời của đơn vẫn 1 (dòng ghi tay); 0 dòng lịch sử mới; 0 thông báo | âm (rollback) |
| T11 | Hồi quy FR-H12 (M6, Q9) — I2 → 400 `INVALID_APPLICATION_STATUS_TRANSITION`, đúng câu lỗi hiện nay, cho từng ca: (a) đơn `REJECTED` không có yêu cầu; (b) `REJECTED` có yêu cầu `ACCEPTED` (dữ liệu dựng: mời lại rồi từ chối lần nữa); (c) `REJECTED` có yêu cầu `DECLINED`; (d) đơn `HIRED`; (e) đơn `WITHDRAWN`. Mỗi ca: không giấy mời mới, không dòng lịch sử mới, trạng thái yêu cầu (nếu có) không đổi, 0 thông báo. `send_applicationAlreadyInterviewInvited_returns400` xanh, không sửa | âm (hồi quy) |
| T12 | Bảng chuyển tiếp chung không đổi: `PATCH /status` `REJECTED → INTERVIEW_INVITED` → 400 (guard a4); `PATCH` `REJECTED → PENDING`/`HIRED` → 400 như cũ; test cũ của `ApplicationStatusService` và FR-H12 xanh không sửa khẳng định | âm (hồi quy) |
| T13 | Test canh nguồn (đọc file `.java` như `AiOutputConvertersSourceGuardTest`): tên method R-S2 chỉ xuất hiện ở `ApplicationStatusService.java` và `InterviewInvitationService.java`; khối khai báo `ALLOWED_TRANSITIONS` không chứa chữ `REJECTED, Set.of`. `ApplicationStatus.values().length == 5` | kiểm tĩnh |
| T14 | Giữ từ chối: H2 với `response` → 200, yêu cầu `DECLINED`, `hr_response` nguyên văn, đơn vẫn `REJECTED`, không dòng lịch sử mới; `response` rỗng → `hr_response` `NULL`; H2 lần hai → 409 `RECONSIDERATION_NOT_OPEN`; H2 cho đơn không có yêu cầu → 409; `response` 1001 → 400 | dương + âm + biên |
| T15 | Tranh chấp (hai thread, `CountDownLatch`, lớp test không `@Transactional`): (a) H2 + H2 → một 200, một 409 `RECONSIDERATION_NOT_OPEN`; (b) I2 + I2 → một 201, một 409, đúng 1 giấy mời, 1 dòng lịch sử `REJECTED → INTERVIEW_INVITED`; (c) I2 + H2 → đúng một thành công, bên thua 409 `RECONSIDERATION_NOT_OPEN` — H2 thắng: đơn `REJECTED`, 0 giấy mời, yêu cầu `DECLINED`; I2 thắng: đơn `INTERVIEW_INVITED`, yêu cầu `ACCEPTED`, `hr_response` `NULL`. Mọi ca: bên thua có đúng câu ở mục 4.3, không 500; tổng thông báo = đúng của bên thắng | đồng thời + âm |
| T16 | Quyền HR: H1/H2 của công ty khác → 403; HR chưa có công ty → 404 `COMPANY_NOT_FOUND`; ứng viên gọi H1/H2 → 403 | âm |
| T17 | H1: mặc định chỉ `OPEN`, `ALL` gồm cả `ACCEPTED`/`DECLINED`; `openCount` không đổi theo bộ lọc; thứ tự `requested_at ASC, application_id ASC` (hai yêu cầu cùng `requested_at` — tạo trong một transaction — thứ tự ổn định qua nhiều lần gọi); gồm đơn của chu kỳ cũ | dương |
| T18 | Ẩn danh: Job bật ẩn danh, `reason` chứa họ tên, email và số điện thoại ứng viên; `hr_response` (sau H2) chứa họ tên → (a) H1 (`OPEN` và `ALL`), khối `reconsideration` ở E1 FR-H09, response H2 đều trả `"[đã ẩn]"` ở đúng chỗ, `candidateName = "Ứng viên #…"`, `identityMasked = true`; (b) **chuỗi JSON thô** của cả ba response không chứa họ tên, email, số điện thoại ở bất kỳ khoá nào; (c) cột `reason`/`hr_response` trong DB vẫn nguyên văn; (d) khối ứng viên (E1 ứng viên) nguyên văn; (e) thông báo `RECONSIDERATION_REQUESTED` không chứa họ tên. Tắt ẩn danh của Job → H1 trả nguyên văn (ca dương). Mời phỏng vấn đơn ẩn danh → thư thay `{{candidateName}}` bằng họ tên (FR-H16 R-I2), sau đó H09 hết ẩn | dương + âm |
| T19 | DTO ứng viên: JSON của khối `reconsideration` có **đúng** các khoá `state, deadline, requestedAt, reason, decidedAt, hrResponse` — không `decidedBy`, không `identityMasked`, không id HR | âm |
| T20 | Khối E1 ứng viên theo 6 `state` + `null` (R-C1): mỗi ca một test, gồm đơn đã được mời lại rồi `HIRED` → `ACCEPTED`; `deadline` = `changed_at + 168h` | dương |
| T21 | Danh sách đơn ứng viên: `reconsiderationStatus` đúng cho đơn có/không có yêu cầu; số câu SQL không tăng theo số đơn | dương |
| T22 | Thông báo: C1 → 1 `RECONSIDERATION_REQUESTED` cho chủ công ty, body không chứa lý do, nhãn "Ứng viên #…" khi ẩn danh; H2 → 1 `RECONSIDERATION_DECLINED` cho ứng viên, body không chứa phản hồi; I2 chấp nhận → đúng 1 `APPLICATION_STATUS_CHANGED` cho ứng viên, 0 thông báo cho HR; request lỗi (409/400) → 0 thông báo | dương + âm |
| T23 | Migration V21 (Flyway API, khuôn `JobCatalogMigrationTest`): chèn tay hai dòng cùng `application_id` → vi phạm unique; `status = 'OPEN'` có `decided_at` → vi phạm CHECK; `DECLINED` thiếu `decided_by` → vi phạm; `ACCEPTED` có `hr_response` → vi phạm; `reason` 1001 ký tự → vi phạm | âm |
| T24 | Funnel FR-H08: đơn được mời lại đếm "đã từng mời" đúng 1 lần (test hồi quy, không sửa code dashboard) | dương |
| T25 | Dữ liệu cho R-D7: E1 FR-H09 của đơn `REJECTED` có yêu cầu `OPEN` → `reconsideration.status = OPEN`; đơn `REJECTED` không có yêu cầu → `reconsideration = null`; yêu cầu `DECLINED` → `status = DECLINED`; sau chấp nhận → đơn `INTERVIEW_INVITED`, `status = ACCEPTED` | dương + âm |

### 7.2 Frontend

- `npm run build` + `npm run lint` sạch.
- Không tự tính hạn trong trình duyệt: `grep` các file mới không có `Date.now()`/`new Date()` dùng để so với `deadline`.
- `NotificationType` (`features/notifications/types.ts`) thêm hai loại.
- Frontend **không có** test runner tự động (`frontend/package.json` chỉ có `build`, `lint`); R-D7 kiểm bằng (i) backend
  T25 cho dữ liệu, (ii) soát tay 7.3 bước 7 cho hiển thị. Hàm chọn câu của thanh thao tác viết thành hàm thuần trong
  `ApplicationActionBar.tsx` (nhận `status` + `reconsideration?.status`), để soát bằng đọc code.

### 7.3 Soát tay (desktop ≥ `lg`, 768px, 375px — seed test)

1. Ứng viên mở đơn bị từ chối 6 ngày 23 giờ → khối có hạn chót và nút; gửi với lý do 2 dòng → khối "Đang chờ", badge
   đầu trang + danh sách có chữ "Đang chờ xem xét lại"; F5 vẫn đúng.
2. Đơn quá hạn 8 ngày → khối ghi đã quá hạn, không nút. Đơn bị từ chối sau phỏng vấn → không có khối.
3. HR mở tab "Yêu cầu xem xét lại (1)" → thấy dòng; "Mời phỏng vấn" → hộp giấy mời FR-H12 đúng nội dung mẫu; gửi →
   dòng rời bộ lọc "Đang chờ", số trên tab về 0; hồ sơ đơn hiện trạng thái "Đã mời phỏng vấn".
4. Yêu cầu khác: "Giữ quyết định từ chối" với phản hồi → ứng viên thấy phản hồi nguyên văn, thông báo ở chuông.
5. Job ẩn danh: tab và khối H09 hiện "Ứng viên #…", lý do bị che.
6. Hai tab HR cùng bấm "Giữ quyết định" → tab sau thấy câu lỗi 409 và dữ liệu làm mới.
7. Thanh thao tác hồ sơ đơn (R-D7): đơn `REJECTED` có yêu cầu `OPEN` → câu "Ứng viên đã gửi yêu cầu xem xét lại. Xử lý
   ở khối bên dưới.", **không** có câu "Không còn thao tác nào", không nút mới; sau "Giữ quyết định từ chối" → câu trở về
   "Đơn đã có kết quả cuối. Không còn thao tác nào." mà không tải lại trang; sau "Mời phỏng vấn" → hiện "Trúng tuyển",
   "Từ chối"; đơn `REJECTED` không có yêu cầu → câu cũ. Kiểm ở ≥ `lg` và 375px.

### 7.4 Tự kiểm test bắt lỗi (đột biến tạm, Plan Mode 30/10)

Lớp T5, T15 không `@Transactional`, mỗi ca có hai dạng: (a) tất định — luồng A giữ transaction (`TransactionTemplate`),
luồng B bị chặn ở khoá, xác nhận bằng `pg_stat_activity` (`wait_event_type = 'Lock'`) chứ không chờ theo thời gian;
(b) `CyclicBarrier(2)` lặp `ROUNDS = 20` vòng, dữ liệu mới mỗi vòng (khuôn `SavedJobConcurrencyIntegrationTest`, FR-U16
T15/T16). T5 đi qua HTTP (MockMvc) vì "409 chứ không 500" chỉ kiểm được ở tầng web. Mỗi đột biến dưới đây sửa tạm, chạy,
ghi kết quả đỏ vào báo cáo đợt, rồi hoàn nguyên (`git diff` sạch):

| Đột biến tạm | Test phải đỏ |
|---|---|
| Bỏ `AND status = 'OPEN'` trong `declineIfOpen` | T15 (a), T14 "H2 lần hai" |
| Bỏ `AND status = 'OPEN'` trong `acceptIfOpen` | T15 (b) — bên thua ra 409 `APPLICATION_STATUS_CONFLICT` thay vì `RECONSIDERATION_NOT_OPEN` |
| Bỏ nhánh `uq_reconsideration_per_application` trong `GlobalExceptionHandler` | T5 (bên thua 500) |
| `<=` → `<` trong biểu thức hạn | T2 |
| Thêm khoá `REJECTED` vào `ALLOWED_TRANSITIONS` (chỉ cục bộ) | T13, T11 (a) |

## 8. AI hay làm sai (dành cho người code)

- Thêm giá trị thứ 6 vào `ApplicationStatus` (vd `RECONSIDERING`) — sai: trạng thái nằm ở bảng riêng.
- Thêm `REJECTED → INTERVIEW_INVITED` vào `ALLOWED_TRANSITIONS` — mở cạnh này cho **mọi** đơn qua I2 và cả
  `changeStatus`; phải là method riêng gắn với yêu cầu `OPEN` (R-S2).
- Gọi method R-S2 **trước** khi chuyển yêu cầu sang `ACCEPTED`, hoặc chuyển `ACCEPTED` sau khi tạo giấy mời — thứ tự phải
  là R-S3 (1) → (2) → (3) trong một transaction.
- Kiểm ẩn danh **sau** khi đổi trạng thái: lúc đó đơn đã hết ẩn, `{{candidateName}}` không được thay (FR-H16 R-I2).
- Tính 7 ngày bằng `LocalDate`, `Instant.now()`, `Clock` hay giờ JVM; dùng `interval '7 days'` thay `'168 hours'`;
  dùng `<` thay `<=` (R-T).
- Tính hạn từ `job_applications.updated_at` (đổi theo mọi `UPDATE`, kể cả `lockIfInterviewInvited`) thay vì
  `application_status_history.changed_at`.
- Lấy dòng lịch sử `REJECTED` **đầu tiên** thay vì mới nhất; hoặc chỉ kiểm `status = REJECTED` mà quên `from_status =
  'PENDING'` (đơn bị từ chối sau phỏng vấn lọt qua).
- Chặn "một lần" chỉ bằng `existsBy…` ở service, không unique ở DB (R-E2); hoặc có unique nhưng quên nhánh dịch tên
  constraint trong `GlobalExceptionHandler` → request đồng thời thứ hai nhận 500.
- Trả 400 cho bên I2 thua tranh chấp (phải 409 — R-D3), hoặc trả 409 cho I2 gửi sau khi yêu cầu đã quyết xong (phải 400
  như FR-H12 — R-S3 (0), T11).
- Quyết định bằng đọc entity rồi `save` (không điều kiện `status = 'OPEN'` trong câu `UPDATE`) — hai tab ghi đè nhau.
- Phát thêm thông báo "yêu cầu được chấp nhận" ngoài thông báo đổi trạng thái (R-S4, R-N4).
- Đưa lý do vào thông báo gửi HR, hoặc lộ họ tên trong lý do khi ẩn danh (R-A2, R-A3).
- Che lý do ở phía ứng viên, hoặc ghi chuỗi đã che xuống DB.
- `trim()` lý do/phản hồi, escape HTML khi lưu, hoặc quên đổi CRLF (999 ký tự + xuống dòng bị từ chối — T8).
- Tái dùng DTO phía HR cho ứng viên; trả `decidedBy` cho ứng viên.
- Tự `DECLINED` bằng `@Scheduled` khi quá hạn hay khi tin bị gỡ.
- Thêm nút "Chấp nhận" riêng không gửi giấy mời; hoặc dựng hộp giấy mời thứ hai thay vì dùng `InterviewInvitationDialog`.
- Đặt tên cột/field `verdict`, `label`, `recommendation`… (CLAUDE.md §7).
- Thêm V21 mà không sửa các chỗ dọn dữ liệu ở mục 9.1 trong **cùng đợt**: quên xoá
  `application_reconsideration_requests` trước `job_applications` ở `reset-test-data.sql` hay `seed-demo-structural-1b.sql`
  thì `ON DELETE RESTRICT` làm câu `DELETE` lỗi; quên liệt kê bảng mới và tiền đề V21 ở `reset-demo-db.sql` thì script vẫn
  chạy (nhờ `CASCADE`) nhưng lệch quy ước liệt kê tường minh. Cũng sai: "chữa" bằng cách đổi FK sang `ON DELETE CASCADE`.

## 9. Kế hoạch đợt code (đề xuất, Plan Mode chốt)

| Đợt | Nội dung |
|---|---|
| 1 | Plan Mode: đối chiếu lại mục 0 trên `main` sau khi FR-U16 merge; chốt tên method/endpoint, cách tránh vòng gói |
| 2 | V21 + entity + repository + T23 + **dọn dữ liệu theo khoá `RESTRICT`** (mục 9.1) |
| 3 | C1, khối E1 ứng viên, danh sách (R-C), thông báo `RECONSIDERATION_REQUESTED`; T1–T9, T19–T21 |
| 4 | Nhánh I2 (R-S), H1, H2, khối H09, ẩn danh, thông báo `RECONSIDERATION_DECLINED`; T10–T18, T22, T24 |
| 5 | Frontend phía ứng viên (UI.md 4a–4d) |
| 6 | Frontend phía HR (UI.md 4e–4g) |
| 7 | `srs-guard` + `walkthrough` + seed (mục 4.5) + tài liệu chung (mục 10) + đổi trạng thái `ĐÃ HOÀN THÀNH` |

### 9.1 Dọn dữ liệu theo khoá `ON DELETE RESTRICT` (bắt buộc ở đợt có V21)

`application_reconsideration_requests.application_id` khai `ON DELETE RESTRICT` (mục 4.1): câu `DELETE FROM
job_applications` sẽ **lỗi** nếu đơn còn dòng yêu cầu. Đợt thêm V21 phải sửa luôn, trong cùng đợt, mọi chỗ dọn dữ liệu có
xoá `job_applications` để xoá bảng mới **trước** `job_applications`. Đã rà ngày 30/10/2026 (`rg` trên `*.sql`, `*.ps1`,
`*.sh`, `*.java`; không có `@AfterEach`/`@BeforeEach` hay helper nào trong test backend xoá `job_applications` — các lớp
test dọn bằng rollback của `@Transactional` hoặc tạo dữ liệu mới mỗi lần):

| Chỗ | Hiện trạng | Việc phải làm ở đợt V21 |
|---|---|---|
| `db/seed/reset-test-data.sql:110-123` | Mục 3 xoá con của `job_applications` rồi `DELETE FROM job_applications WHERE id IN (SELECT id FROM t_apps)` (`:123`) | Thêm `DELETE FROM application_reconsideration_requests WHERE application_id IN (SELECT id FROM t_apps)` trong mục 3, **trước** `:123`. Thiếu câu này thì reset lỗi FK ngay khi seed test có yêu cầu (mục 4.5) |
| `db/seed/seed-demo-structural-1b.sql:88-100` | Mục 1 xoá dải `d1b00000-%` "con trước cha", `DELETE FROM job_applications` ở `:100` | Thêm `DELETE FROM application_reconsideration_requests WHERE application_id::text LIKE 'd1b00000-%' OR id::text LIKE 'd1b00000-%'` **trước** `:100`. Theo đề xuất S1 (mục 11) yêu cầu demo nằm ở đơn dải `d3a`, không ở `d1b`, nên câu này là phòng hờ — giữ để 1b không lỗi FK nếu sau này có yêu cầu gắn vào đơn `d1b` |
| `db/seed/reset-demo-db.sql:92-124` | Một câu `TRUNCATE TABLE … CASCADE` (`:92`, `:124`), `job_applications` ở `:104` | `TRUNCATE … CASCADE` **không** bị `RESTRICT` chặn (CASCADE truncate mọi bảng có FK trỏ tới, comment `:89-91`), nên script không lỗi nếu quên. Vẫn phải thêm `application_reconsideration_requests` vào danh sách cạnh `job_applications` (quy ước liệt kê tường minh, comment `:89-91`, `:107`, `:111`, `:113`, `:115`), sửa tiền đề "Can DB o V20 tro len … chua co saved_jobs" (`:86-87`) thành V21, và comment đếm bảng "31 bang … V1..V20" (`:81-82`) thành "32 bang … V1..V21" |
| `db/seed/seed-demo-structural-3.sql:72-81` (theo S1, mục 11) | Mục 1 xoá dải `d3a00000-%`; chưa xoá `job_applications`. Chốt chặn `:52` đòi V20 trở lên | Nếu Phần B thêm đơn dải `d3a`: xoá `application_reconsideration_requests` → `notifications` → `application_status_history` → `job_applications` dải `d3a` **trước** `DELETE FROM jobs` (`:81`); nâng chốt chặn `:52`-`:53` lên V21 |
| `backend/src/test/java/com/recruitment/interviewinvitation/InterviewInvitationSchemaConstraintIntegrationTest.java:216` | `DELETE FROM job_applications WHERE id = ?` trong **thân** test T16 của FR-H12 (kiểm CASCADE giấy mời → khung), không phải bước dọn | Không sửa: đơn của test này không có dòng yêu cầu nên `RESTRICT` không chạm. Ghi lại để người code không nhầm là chỗ dọn |

Kiểm ở đợt đó: chạy `reset-test-data.sql` trên DB có seed test (gồm yêu cầu ở mục 4.5) → không lỗi, 0 dòng yêu cầu của
đơn test còn lại; nạp lại `seed-demo-structural-1b.sql` hai lần liên tiếp sau khi có tầng demo mới → không lỗi;
`reset-demo-db.sql` → bảng mới rỗng; nạp `seed-demo-structural-3.sql` hai lần liên tiếp → không lỗi (theo S1).

## 10. Tài liệu dùng chung

Sửa **cùng commit đặc tả** (đợt D4): `CLAUDE.md` §2 câu vòng đời đơn; `docs/SRS.md` dòng FR-H07 và câu "Vòng đời ứng
tuyển gồm 5 trạng thái" — giữ "5 trạng thái", thêm ngoại lệ FR-C09.

Sửa ở đợt cuối: `CLAUDE.md` §3 danh sách package (`reconsideration/`); `docs/UI_GUIDE.md` mục 7 (dòng
`/hr/jobs/:id/edit` thêm tab "Yêu cầu xem xét lại"; dòng `/hr/applications/:id` thêm khối yêu cầu; dòng
`/candidate/applications/:id` thêm khối xem xét lại; dòng `/candidate/applications` thêm chữ "Đang chờ xem xét lại");
`docs/ER.md` (bảng mới); `db/seed/*` (mục 4.5); `docs/DEMO.md`; `docs/SRS.md` mục 0 + `docs/ROADMAP.md`;
comment `ApplicationService.java:175-176` (M1), `ApplicationStatusService.java:29-32` (nêu ngoại lệ FR-C09).

Ghi chú vào đặc tả của FR đã hoàn thành (đợt cuối, khuôn "*Sửa bởi FR-U10 …*" ở `docs/features/HR/H12/REQUIREMENT.md`
mục 3.9):
- `docs/features/UV/U08/REQUIREMENT.md` + `UI.md`: R-D1 (E1 đọc thêm lịch sử và bảng yêu cầu — M1), DTO thêm khoá
  `reconsideration` (M2), R-P1 thêm nút ở khối xem xét lại (M5).
- `docs/features/HR/H12/REQUIREMENT.md`: dưới R-F3 — đơn `REJECTED` có yêu cầu `OPEN` gửi giấy mời được (M6, R-S3).

**Checklist đợt cuối** (ngoài các mục trên):
- [ ] Q10: sửa `docs/UI_GUIDE.md` mục 7 (bốn dòng route ở UI.md mục 2).
- [ ] `.claude/skills/srs-guard/SKILL.md`: thêm nguyên tắc "cạnh `REJECTED → INTERVIEW_INVITED` chỉ qua yêu cầu `OPEN`;
  `ALLOWED_TRANSITIONS` không có khoá `REJECTED`; `ApplicationStatus` đúng 5 giá trị"; "19 FR mới" → 21 (dòng 3, 175).
- [ ] `docs/features/README.md:935`: bỏ dấu ★ trước `/candidate/applications/:id` và `/hr/applications/:id` (route đã có).
- [ ] Ghi chú FR-C09 vào `docs/features/UV/U08/` và `docs/features/HR/H12/` (đoạn trên).
- [ ] Ghi "Giới hạn đã biết" (mục 6) vào `docs/ROADMAP.md`.

## 11. Quyết định — Đã chốt 30/10/2026

Người dùng chốt ngày 30/10/2026 sau `spec-review`; đặc tả duyệt cùng ngày.

| # | Câu hỏi | Đã chốt 30/10 | Ghi ở |
|---|---|---|---|
| Q1 | Tin đã xoá mềm có chặn gửi yêu cầu không? | **Chặn**: 409 `RECONSIDERATION_JOB_REMOVED`, khối `JOB_REMOVED`. Tin `PAUSED`/`CLOSED` (và tin quá hạn nộp) vẫn gửi được | R-E4, R-C1, T7 |
| Q2 | Yêu cầu `OPEN` khi tin bị gỡ sau đó: HR xử lý ở đâu? | Theo khuyến nghị: ở hồ sơ đơn FR-H09, vào bằng link của thông báo; không tự `DECLINED` | Mục 6 "Giới hạn đã biết", R-D6 |
| Q3 | Ứng viên có huỷ được yêu cầu không? | **Không** | Mục 6 |
| Q4 | Có thêm cột `job_id` không? | **Không** | Mục 4.1 |
| Q5 | Phản hồi HR có vào thông báo/email không? | **Không**; chỉ ở trang chi tiết đơn | R-N3, T22 |
| Q6 | Đúng mốc 168 giờ là còn hạn hay hết hạn? | **Còn hạn** (`now() <= changed_at + interval '168 hours'`) | R-T2, T2 |
| Q7 | Tab HR có hiện yêu cầu đã quyết không? | Có bộ lọc "Đang chờ" (mặc định) / "Tất cả" | Mục 4.3 H1, T17, UI.md 4e |
| Q8 | Có thêm nút vào thanh thao tác FR-H07 không? | **Không thêm nút**, NHƯNG sửa M3: đơn `REJECTED` có yêu cầu `OPEN` thì thanh thao tác không hiện "Không còn thao tác nào" mà hiện câu trỏ xuống khối yêu cầu | R-D7, T25, mục 7.2, 7.3 bước 7, UI.md 4f |
| Q9 | Chấp nhận thay đổi FR-U08 (M1, M2, M5) và FR-H12 R-F3 (M6)? | **Chấp nhận**. Thêm test hồi quy FR-H12: I2 vẫn 400 khi đơn `REJECTED` không có yêu cầu / có yêu cầu `ACCEPTED` / `DECLINED`, và khi đơn `HIRED` / `WITHDRAWN`. Đợt cuối ghi chú FR-C09 vào `docs/features/UV/U08` và `docs/features/HR/H12` | T11, mục 10 |
| Q10 | Sửa `UI_GUIDE.md` mục 7 lúc nào? | **Đợt cuối** — cùng đề xuất sửa `srs-guard` và dấu ★ ở `docs/features/README.md:935` | Mục 10 "Checklist đợt cuối" |

### Đồng bộ sau FR-U16 (30/10/2026)

Nhánh rebase từ `f0e6354` lên `main` tại `0d6dbef` (merge PR #36, FR-U16), không xung đột. Trạng thái đặc tả giữ
**ĐÃ DUYỆT**; không quy tắc nghiệp vụ, endpoint, DTO hay test nào đổi. Chỉ sửa trích dẫn và phần dữ liệu/seed dưới đây.
`UI.md` không cần sửa: bốn chỗ trích file:dòng (`HrJobEditPage.tsx`, `MessageComposer.tsx`, `ApplicationActionBar.tsx`,
`CandidateApplicationsPage.tsx`) không bị FR-U16 sửa. `JobPublicService` và `CandidateLayout` (FR-U16 có sửa) không được
đặc tả này trích ở đâu — không ảnh hưởng.

**Migration và số liệu**

| Chỗ | Trước | Sau |
|---|---|---|
| a26 | Cao nhất `V19__blind_review.sql`; V20 dành cho FR-U16 | Cao nhất `V20__saved_jobs.sql`; FR-C09 dùng **V21** (không đổi) |
| 9.1 `reset-demo-db.sql` tiền đề | "Can DB o V19 tro len" (`:86`) → V21 | Hiện là "Can DB o V20 tro len … chua co saved_jobs" (`:86-87`) → V21 |
| 9.1 `reset-demo-db.sql` số bảng | Không nêu | Comment `:81-82` "31 bang … V1..V20" → "32 bang … V1..V21". Đếm lại 30/10: 36 `CREATE TABLE` trong V1–V20 − `job_recommendations` (DROP ở V11) − 4 bảng `catalog_*` = 31, khớp đúng 31 tên trong câu `TRUNCATE` `:92-124` |
| 4.5 chốt chặn | Không nêu | Tầng 3 đòi V20 trở lên (`seed-demo-structural-3.sql:52`) → V21; tầng 1b đã là "V19 trở lên" (FR-U16 B11) → giữ nguyên |
| Mốc test | Không nêu | Full suite trên `main` `0d6dbef`: **2148** test xanh (`docs/walkthrough/fr-u16-saved-jobs.md:207`, `docs/ROADMAP.md:1224`) — mốc so sánh cho các đợt code |

**Trích dẫn file:dòng bị dời** (chỉ đổi số dòng, trừ chỗ ghi "ý nghĩa")

| Chỗ | Trước (`f0e6354`) | Sau (`0d6dbef`) |
|---|---|---|
| Đầu file — phụ thuộc | `docs/SRS.md:24`, `:37`, `:39`, `:42`, `:45`, `:53`; `:87` | `:25`, `:38`, `:40`, `:43`, `:46`, `:54`; `:88` |
| Đầu file — nhánh | FR-U16 "Chưa hoàn thành — `docs/SRS.md:60`", phải rebase | FR-U16 Đã hoàn thành (`docs/SRS.md:61`), đã rebase lên `0d6dbef` |
| a2 | `GlobalExceptionHandler.java:235-238` | `:248-251` (+13: FR-U16 thêm hai handler ở `:140-151`) |
| a3 | `GlobalExceptionHandler.java:243-246` | `:256-259` |
| a7 | `ApplicationService.java:115`, `:143` | `:118`, `:146` (+3: thêm field/tham số/gán `JobAvailabilityResolver`) |
| a16 | `ApplicationCandidateDetailResponse.java:14-22`; `getMyApplicationDetail` `:174-227`; quyền `:178-180`; comment R-D1 `:172-173` | `:15-23`; `:177-230`; `:181-183`; `:175-176`. **Ý nghĩa:** `job.availability` nay lấy qua `job/JobAvailabilityResolver.of` (`:205`), enum `JobAvailability` chuyển từ lồng trong DTO sang `job/JobAvailability.java` (DTO `:51` chỉ còn comment). Nội dung comment R-D1 không đổi; khối `reconsideration` của FR-C09 không dùng `JobAvailability` (tin xoá mềm xét bằng `jobs.deleted_at`, R-E4) |
| M1, mục 10 | comment `ApplicationService.java:172-173` | `:175-176` |
| M2 | `ApplicationCandidateDetailResponse` "KHÔNG thêm field nào khác" `:10-13` | `:11-14` (+1: import `com.recruitment.job.JobAvailability`) |
| M4 | `docs/SRS.md:101`, `:155` | `:102`, `:156` |
| 9.1 `seed-demo-structural-1b.sql` | `:87-99`, `job_applications` `:99` | `:88-100`, `:100` (+1: comment chốt chặn B11) |
| 9.1 `reset-demo-db.sql` | `:92-122`, `CASCADE` `:122` | `:92-124`, `:124` (+2: dòng `saved_jobs` `:115-116`; thêm `:115` vào danh sách comment "liệt kê cho tường minh") |
| Mục 10, Q10 | `docs/features/README.md:921` | `:935` |

Đã rà lại 30/10 trên `0d6dbef` mọi chỗ xoá `job_applications` (`*.sql`, `*.ps1`, `*.sh`, `*.java`): vẫn đúng ba chỗ của
mục 9.1 (`reset-test-data.sql:123` không đổi), FR-U16 không thêm chỗ nào.

**S1 — Đề xuất seed demo (chờ người dùng chốt, trước đợt cuối)**

- **Thêm vào tầng 3** (`seed-demo-structural-3.sql`, Phần B "Xem xét lại (FR-C09)"), **không** tạo tầng riêng. Lý do:
  header file ghi sẵn "FR-C09 them phan cua minh vao CUNG file nay" (`:6`) và README seed `:32-33` cũng vậy; tầng 3 chạy
  sau 2b nên không đụng chốt chặn L11 của 1b; idempotent sẵn theo dải `d3a00000-%`.
- **Dải id con** (cùng bảng mã `TTTT` của `chore-seed-final.md` K2, `0020` đã là `saved_jobs`): `d3a00000-0009-…` đơn
  ứng tuyển, `d3a00000-0010-…` lịch sử trạng thái (`NULL → PENDING`, `PENDING → REJECTED` lúc `now() - interval '2
  days'`), `d3a00000-0015-…` thông báo (`APPLICATION_STATUS_CHANGED` cho ứng viên, `RECONSIDERATION_REQUESTED` cho HR),
  **`d3a00000-0021-…` = `application_reconsideration_requests` (mã mới)** — bổ sung vào bảng "Dải id" của
  `db/seed/README.md` và bảng mã K2. Không có dòng nào trùng: tầng 3 hiện chỉ dùng `0004` và `0020`.
- **Ràng buộc khi chọn đơn** (chốt ở đợt cuối): đơn mới nên thuộc tin `OPEN` của công ty Hoàn Mỹ không bật ẩn danh để HR
  demo chính thấy tab; không dùng Phạm Quốc Bảo trên 4 tin đã lưu (giữ kịch bản "Đã ứng tuyển" của FR-U16); kiểm
  `uq_application_per_cycle`. Một đơn `REJECTED` mới làm đổi số phễu công ty ở README seed mục 6c — cập nhật cùng đợt.

### Sau Plan Mode 30/10/2026

Plan Mode (đợt 1) đối chiếu lại toàn bộ điểm đặc tả dựa vào trên `0d6dbef`: backend lõi (máy trạng thái, `sendInvitation`,
ẩn danh, quyền, `handleDataIntegrityViolation`, listener) khớp. Người dùng duyệt kế hoạch và **S1** (kèm hai yêu cầu: mốc
tương đối theo `now()`; đủ hai kịch bản) cùng ngày. Trạng thái đặc tả giữ **ĐÃ DUYỆT**. Các điểm dưới đây chốt theo
khuyến nghị:

| # | Nội dung | Chốt | Ghi ở |
|---|---|---|---|
| L1 | UI.md mục 6 yêu cầu làm mới H1 + E1 hồ sơ đơn khi "Mời phỏng vấn" lỗi, nhưng `interviewinvitation/queries.ts:73-78` chỉ làm mới I1 khi 409, không làm gì khi 400, và `extraInvalidateKeys` chỉ chạy `onSuccess` (`:68-72`) | Thêm `interviewinvitation/queries.ts` vào UI.md 5b: `onError` khi 409 (mọi mã) hoặc 400 `INVALID_APPLICATION_STATUS_TRANSITION` làm mới thêm `extraInvalidateKeys`. Vẫn không sửa `InterviewInvitationDialog.tsx` | UI.md 5b, mục 6; đợt 6 |
| L2 | `ApplicationCandidateDetailControllerIntegrationTest.java:337` khẳng định đúng tập khoá của E1 ứng viên — sẽ đỏ khi thêm khoá `reconsideration` (M2) | Sửa đúng khẳng định này (thêm `"reconsideration"`); không sửa khẳng định test cũ nào khác | Đợt 3 |
| L3 | `MessageService.normalizeLineBreaks` (`:385`) là package-private trong `messaging/` | Bản riêng 3 dòng `reconsideration/ReconsiderationText.normalize`, comment trỏ C06 R-M2; không đổi `messaging/`, không chuyển ra `common/` | R-V1; đợt 3 |
| L4 | Cách tránh vòng gói (mục 4.2) | `reconsideration/ReconsiderationReader` (`@Component`, chỉ đọc; phụ thuộc repository + `BlindReviewResolver` + `IdentityMaskSource`) dựng khối ứng viên/HR; `ApplicationService`, `ApplicationHrDetailService` inject reader. Vòng **import** giữa gói chấp nhận (tiền lệ `jobapplication ↔ notification`, repo không có ArchUnit), **không** vòng bean. Logic hạn 168 giờ chỉ ở `reconsideration/` | Mục 4.2 |
| L5 | Tên | Method R-S2 `ApplicationStatusService.reinviteAfterReconsideration(ownerId, applicationId)` (nạp đơn bằng `findById` **trước** `updateStatusIfCurrent` vì `clearAutomatically`); I2 gọi `ReconsiderationHrService.claimForAcceptance(applicationId, ownerId)`; endpoint đúng mục 4.3 | R-S2, R-S3 |
| L6 | T13 chỉ kiểm văn bản | Thêm: `ReflectionTestUtils` đọc `ALLOWED_TRANSITIONS`, tập khoá đúng `{PENDING, INTERVIEW_INVITED}`; câu SQL hạn chứa `'168 hours'`, không `'7 days'` (bổ sung, không thay khẳng định cũ) | T13 |
| L7 | `IdentityMasker.mask` (`:65`) trả chuỗi chuẩn hoá NFC khi đơn có ít nhất một giá trị danh tính | T18 so `reason`/`hrResponse` đã che sau NFC; hành vi không đổi | T18 |
| L8 | Kiểm script seed chạy trên DB docker local | Hỏi người dùng trước mỗi lần chạy. Đợt 2 chỉ chạy `reset-test-data.sql` + nạp lại 1b hai lần (backend lên V21 với scheduler tắt, key giả; không đọc `.env`); `reset-demo-db.sql` để đợt cuối | Mục 9.1 |
| L9 | Seed demo S1 cụ thể | HR `hr@demo.local`. **B1 — ứng viên tự gửi:** `bui.ngoc.mai@demo.local` → "Kế toán tổng hợp" (`d2000000-…-04`), nộp `now() - 3 days`, bị từ chối ở vòng hồ sơ lúc `now() - 1 day` (hạn ≈ `now() + 6 days` lúc nạp), **không** có yêu cầu (`AVAILABLE`). **B2 — HR xử lý:** `nguyen.hai.son@demo.local` → "Chuyên viên Marketing" (`d2000000-…-05`), nộp `now() - 5 days`, bị từ chối `now() - 2 days`, yêu cầu `OPEN` `requested_at = now() - 1 day`, lý do hai dòng, thông báo `RECONSIDERATION_REQUESTED` cho HR. Hai tin không bật ẩn danh, không có câu hỏi sàng lọc; Mai/Sơn chưa có đơn ở hai tin này. `resume_id` chọn bằng `SELECT` CV chính đã trích xuất (CV tầng 2 có UUID ngẫu nhiên); chốt chặn tầng 3 nâng lên V21 và kiểm đủ CV/tin/không trùng chu kỳ. Không dùng Phạm Quốc Bảo; không seed ẩn danh (T18 đã phủ). Phễu công ty 1: tổng +2, `REJECTED` 1 → 3 | Mục 4.5; đợt cuối |
| B1 | Rollback ở nhánh chấp nhận | `sendInvitation` là `@Transactional` (`InterviewInvitationService.java:138`) bao trọn claim (R-S3 (1)) → R-S2 → `createInvitation` (`:252-276`). Lỗi xảy ra **sau** claim có thật: `saveAndFlush` giấy mời (`:260`) vi phạm `uq_invitation_active` (`V16__interview_invitation_slots.sql:48`) khi đơn đã có giấy mời hiệu lực → 409 `INTERVIEW_INVITATION_CONFLICT` (`GlobalExceptionHandler.java:628-632`), cả transaction rollback. Gửi lần đầu không phát sự kiện riêng của giấy mời (`:163`); thông báo duy nhất là `ApplicationStatusChangedEvent`, xử lý `AFTER_COMMIT` (`NotificationEventListener.java:64-66`); email chỉ gửi từ dòng `notifications` `PENDING` (`NotificationMailScheduler.java:37-40`) ⇒ rollback thì 0 thông báo, 0 email | T10b (mục 7.1) |

Bảng đột biến tạm và cách lặp T5/T15: mục 7.4.
