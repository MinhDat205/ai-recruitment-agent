# FR-H12 — Giấy mời phỏng vấn nhiều khung giờ

> Trạng thái: ĐÃ HOÀN THÀNH (14/10/2026). Duyệt: 13/10/2026. Mọi quyết định ở mục 11 đã chốt 13/10/2026 (11.1: tám quyết định nền; 11.2:
> Q1–Q9; 11.3: năm điểm bổ sung B1–B5; 11.4: S1–S7 chốt sau `spec-review`).
>
> Làm rõ sau Plan Mode 13/10/2026: L1–L8 (L1: mục 4.5, D2; L2: R-K1, mục 8; L3: mục 4.2; L4: T3; L5: T13; L6: T21;
> L7: mục 9 đợt 2, 3; L8: T10). Làm rõ trong đợt 3 (14/10/2026): L9–L12 (L9: R-S6, T3; L10: kiểm tĩnh 7.1; L11: R-G3,
> kiểm tĩnh 7.1; L12: kiểm tĩnh 7.1). Làm rõ trong đợt 7 (14/10/2026): L13–L14 (L13: khối 7.2; L14: mục 4.5) — chi tiết ở mục 12. Mục 12 thắng
> chỗ viết cũ nếu lệch.

- Nhóm: HR
- Tóm tắt: HR gửi giấy mời phỏng vấn có **1–5 khung giờ**, mỗi khung gồm thời điểm bắt đầu và địa chỉ hoặc link
  phòng họp. Giấy mời một khung được coi là **đã chốt** ngay khi gửi (giữ đúng luồng cũ). Khi đơn đang
  `INTERVIEW_INVITED`, HR **gửi lại giấy mời**: giấy mời mới thay giấy mời đang hiệu lực, trạng thái đơn không đổi,
  giấy mời cũ giữ nguyên trong lịch sử, ứng viên nhận thông báo web (một dòng) + email kèm danh sách khung giờ. HR xem
  giấy mời hiệu lực và các giấy mời cũ ở tab mới "Giấy mời phỏng vấn" của trang hồ sơ đơn (FR-H09); ứng viên xem danh
  sách khung giờ **chỉ đọc** ở trang chi tiết đơn (FR-U08).
- Phụ thuộc: FR-H07, FR-C03 — cả hai **Đã hoàn thành** (`docs/SRS.md:35`, `:23`). Màn hình đặt ở FR-H09 và FR-U08 —
  **Đã hoàn thành** (`docs/SRS.md:37`, `:52`). Phụ thuộc ngầm FR-C07 (K1, mục 3.7) — **Đã hoàn thành**.
- Nhánh: `feat/fr-h12-multi-slot` (tạo từ `main` tại `78d12a9`, 13/10/2026).
- Mở rộng: FR-H07 (gửi giấy mời nhiều khung giờ, gửi lại giấy mời), FR-C03 (một loại thông báo mới cho ứng viên; cột
  `notifications.email_body` để email của loại đó kèm danh sách khung), FR-H09 (tab mới "Giấy mời phỏng vấn"), FR-U08 (khối giấy mời hiện danh sách
  khung giờ), FR-C07 (nghĩa của `InterviewSchedule` trong K1 và điều kiện hai tình huống soạn nháp — **thay đổi hành vi
  của FR đã hoàn thành**, mục 3.7).
- Bên nhận phần còn lại: **FR-U10** làm thao tác ứng viên chọn khung giờ (ghi cột khung đã chốt mà V16 tạo sẵn) và
  thông báo "ứng viên đã chốt lịch" cho HR.

## 0. Đã đối chiếu code trước khi viết (CLAUDE.md §6)

Đối chiếu trên `feat/fr-h12-multi-slot` tại `78d12a9` (13/10/2026; `HEAD` = `origin/main`, cây làm việc sạch).

### (a) Hiện trạng

| # | Điểm kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| a1 | Giấy mời lưu **một** thời điểm, **một** địa điểm | Đúng | `V1__init_schema.sql:239-250`: `scheduled_at TIMESTAMPTZ NOT NULL`, `location TEXT`; chỉ có `idx_invitation_app` (`:251`), **không** unique. Không migration nào sau V1 sửa bảng này |
| a2 | Mẫu giấy mời không có ngày giờ, có test canh giữ | Đúng | `interview_templates` (`V1:91-102`); `InterviewTemplate.java:16-20`; `InterviewTemplateOwnerIntegrationTest` |
| a3 | Placeholder của mẫu: đúng 3, render ở preview | Đúng | `InterviewInvitationService.java:37-43` (`{{candidateName}}`, `{{jobTitle}}`, `{{companyName}}`), `:71-89`, `:156-160`. Cột `interview_templates.address` không được đọc ở đâu |
| a4 | Gửi giấy mời = đổi trạng thái + lưu nguyên văn | Đúng | `InterviewInvitationService.sendInvitation` (`:91-121`): kiểm tương lai `:100`, `changeStatus(…, INTERVIEW_INVITED)` `:108`, lưu `subject`/`content` HR gửi `:110-118` |
| a5 | Máy trạng thái không cho gửi lần hai | Đúng | `ApplicationStatusService.java:33-35` (`INTERVIEW_INVITED` → chỉ `HIRED`/`REJECTED`); test `send_applicationAlreadyInterviewInvited_returns400` (`InterviewInvitationControllerIntegrationTest.java:390-401`) |
| a6 | `PATCH /status` chặn đặt thẳng `INTERVIEW_INVITED` | Đúng | `ApplicationStatusController.java:33-43` |
| a7 | Thông báo khi mời: chỉ câu đổi trạng thái, không kèm giờ | Đúng | `ApplicationStatusService.java:88` → `NotificationEventListener.onApplicationStatusChanged` (`NotificationEventListener.java:55-65`) → `NotificationContentBuilder.forStatusChanged` (`:21-28`). Email dùng nguyên `body`, chỉ `NEW_MESSAGE` được nối thêm dòng liên kết (`NotificationMailOrchestrator.java:58-65`) |
| a8 | Thêm loại thông báo không cần migration | Đúng | `notifications.type VARCHAR(50)`, không CHECK (`V1:343`); tiền lệ `NEW_MESSAGE` (`NotificationType.java:8-10`) |
| a9 | `notifications` chưa có chỗ lưu nội dung email riêng; email dựng từ `body` **lúc poller gửi** | Đúng | Bảng `notifications` (`V1:340-354`): `title`, `body`, `link`, không cột email; `NotificationMailOrchestrator.doProcess` (`:78-86`) đặt `setText(mailText(notification))`. `reset-test-data.sql:82-84` xoá thông báo theo `user_id` của người dùng test **hoặc** `entity_type = 'APPLICATION' AND entity_id` thuộc đơn test |
| a10 | Phía HR chưa có endpoint đọc giấy mời; trang hồ sơ đơn không hiện giấy mời | Đúng | `InterviewInvitationController.java` chỉ có `GET …/preview` và `POST`; `HrApplicationDetailPage.tsx:21` `VALID_TABS = ['cv','explanation','screening','history','messages']`, không tab nào hiện giấy mời |
| a11 | Ứng viên đọc giấy mời "mới nhất theo `created_at`" | Đúng | `InterviewInvitationService.java:146-153`; DTO 5 field (`CandidateInterviewInvitationResponse.java:7-8`) |
| a12 | Giao diện ứng viên hiện một thời điểm | Đúng | `InterviewInvitationDetails.tsx:48-62`, `InvitationSection.tsx:10` |
| a13 | Hộp thoại mời: một ô thời điểm, một ô địa điểm (tuỳ chọn) | Đúng | `InterviewInvitationDialog.tsx:25-33`, `:141-151`; nút mở ở `ApplicationActionBar.tsx:16-24`, `:41-45` |
| a14 | K1 lấy giờ phỏng vấn từ giấy mời mới nhất | Đúng | `ConversationContextAssembler.java:77-83`; `ConversationContext.java:22`, `:31`; dùng ở `MessageDraftService.java:126-134` |
| a15 | Điều kiện soạn nháp dựa trên "có giấy mời" | Đúng | `MessageDraftFacade.java:112-124`, `:157-160` (`findByApplicationIdOrderByCreatedAtDesc`) |
| a16 | Kiểm quyền dùng chung phía HR | Có | `HrApplicationAccess.loadOwned` (`HrApplicationAccess.java:42-56`): công ty trước → đơn → 403 |
| a17 | Khuôn "`UPDATE` có điều kiện + kiểm số dòng" cho trạng thái đơn | Có | `JobApplicationRepository.updateStatusIfCurrent` (`:30-35`, `@Modifying(clearAutomatically = true, flushAutomatically = true)`), dùng ở `ApplicationStatusService.java:76-79` (0 dòng → 409 `APPLICATION_STATUS_CONFLICT`) |
| a18 | Rút đơn ghi bằng đọc entity rồi `save` (không điều kiện trạng thái trong câu `UPDATE`) | Đúng | `ApplicationService.withdraw` (`ApplicationService.java:122-147`): `findByIdAndCandidateId` → kiểm `PENDING`/`INTERVIEW_INVITED` → `save` |
| a19 | Mọi `UPDATE job_applications` đổi `updated_at` qua trigger; cột này không được hiển thị hay dùng để sắp xếp | Đúng | Trigger `set_updated_at` (`V1:362-378`); `rg "updatedAt" frontend/src --glob "*.tsx"` → 0 dòng; `ORDER BY updated_at` duy nhất là của `candidate_profiles` (`CandidateProfileRepository.java:38`). Tiền lệ chấp nhận: FR-U09 Q19 |
| a20 | Seed test có 3 giấy mời, mỗi đơn một | Đúng | `db/seed/seed-test.sql:434-466` (A2, A9, A4), ghi thẳng cột `scheduled_at`/`location`; đếm ở `:671`. Tiền tố UUID `e0…ef` đã dùng hết (`seed-test.sql:10`, `db/seed/README.md:255`) |
| a21 | Seed demo không có giấy mời | Đúng | `seed-demo-structural.sql` không có `interview_invitations`, không đơn nào `INTERVIEW_INVITED`. `reset-demo-db.sql:94` `TRUNCATE` bảng `interview_invitations` |
| a22 | Migration kế tiếp | **V16** | Cao nhất `V15__job_ai_generations.sql`; V9 là migration Java (`backend/src/main/java/db/migration/V9__normalize_job_catalog_codes.java`) |
| a23 | Tiền lệ test migration bằng Flyway API + Testcontainers | Có | `JobCatalogMigrationTest.java:20-43` (`Flyway.configure().target(...)`) |
| a24 | Thống kê dựa trên lịch sử trạng thái, không dựa trên giấy mời | Đúng | `JobRepository.java:304`, `JobApplicationRepository.java:103` đọc `application_status_history.to_status = 'INTERVIEW_INVITED'` — gửi lại không ghi lịch sử nên không đổi số liệu |

### (b) Mâu thuẫn và chỗ lệch — đã chốt 13/10/2026

| # | Mâu thuẫn | Đã chốt |
|---|---|---|
| M1 | SRS định nghĩa `INTERVIEW_INVITED` là "Đã mời phỏng vấn **(có lịch hẹn)**" (`SRS.md:93`, `:148`; comment `ApplicationStatusController.java:33-36`), nhưng giấy mời nhiều khung chưa có lịch cố định | Đơn vẫn chuyển `INTERVIEW_INVITED` ngay khi gửi lần đầu. Đợt cuối sửa SRS thành nghĩa "đã gửi giấy mời 1–5 khung giờ"; nhãn hiển thị giữ "Đã mời phỏng vấn"; rà comment controller (mục 10, D1, D4) |
| M2 | `docs/features/README.md` mục FR-H12 ghi H12 "thông báo cho HR qua FR-C03" khi ứng viên chọn khung | Thao tác chọn và thông báo cho HR thuộc **FR-U10** (mục 3.9). Sửa câu README ở đợt cuối (D6) |
| M3 | README ghi "thêm chỗ chèn danh sách khung giờ khi render" mẫu | Danh sách khung giờ lưu và hiển thị **tách riêng**, không chèn vào thư; mẫu FR-H02 giữ nguyên quy tắc không có ngày giờ (R-C1) |
| M4 | FR-C07 R-K1-5 ghi "FR-H12 tự mở rộng `InterviewSchedule`" | **Không** đổi hình dạng record (`ConversationContextSignatureTest` giữ nguyên); chỉ đổi **nguồn**: khung đã chốt của giấy mời hiệu lực (R-K1x) |
| M5 | Gửi lại cần đơn đang `INTERVIEW_INVITED`, nhưng `sendInvitation` gọi `changeStatus` (a4, a5) | Gửi lại là endpoint riêng, không gọi `changeStatus`; test `send_applicationAlreadyInterviewInvited_returns400` giữ nguyên khẳng định (R-G1) |
| M6 | Email mời lần đầu không chứa giờ phỏng vấn (a7) | Giữ nguyên ở FR-H12 — giới hạn đã biết (mục 6). Chỉ email **gửi lại** kèm khung giờ (R-N3) |
| M7 | Tên FR: `SRS.md:40` ghi "Giấy mời nhiều khung giờ", README mục 12 ghi "Giấy mời phỏng vấn nhiều khung giờ" | Dùng "Giấy mời phỏng vấn nhiều khung giờ"; đợt cuối sửa `SRS.md:40` (B5, D1) |

## 1. Mục đích

Cho HR đề xuất vài lựa chọn thời gian/địa điểm trong một giấy mời để ứng viên chọn lịch phù hợp, bớt trao đổi qua lại;
và cho HR gửi lại giấy mời khi cần đổi lịch mà không phải động vào trạng thái đơn. Nội dung đã gửi luôn được giữ
nguyên văn để đối chiếu.

## 2. Luồng người dùng

**HR mời phỏng vấn (lần đầu)** — đơn `PENDING`, ở `/hr/applications/:id`
1. Bấm "Mời phỏng vấn" ở thanh thao tác (không đổi) → hộp thoại như hiện nay (tiêu đề, nội dung đã render từ mẫu, sửa
   được), phần thời điểm/địa điểm thay bằng **danh sách khung giờ**: mặc định một khung; "Thêm khung giờ" tới tối đa 5;
   xoá khung khi còn ≥ 2.
2. Mỗi khung: thời điểm bắt đầu và "Địa điểm hoặc link" (bắt buộc).
3. Bấm "Gửi lời mời" → đơn sang `INTERVIEW_INVITED`; tab "Giấy mời phỏng vấn" hiện giấy mời: một khung thì là đã chốt,
   nhiều khung thì "Ứng viên chưa chọn khung giờ."

**HR gửi lại giấy mời** — đơn `INTERVIEW_INVITED`
1. Ở tab "Giấy mời phỏng vấn", bấm "Gửi lại giấy mời" (kể cả khi ứng viên đã chốt khung).
2. Hộp thoại cùng biểu mẫu, điền sẵn tiêu đề và nội dung của giấy mời đang hiệu lực, khung giờ để trống; ghi rõ giấy
   mời hiện tại sẽ bị thay, trạng thái đơn không đổi, ứng viên nhận thông báo và email.
3. Gửi → giấy mời mới thành giấy mời hiệu lực; giấy mời cũ chuyển xuống "Giấy mời trước đó", nguyên văn.

**HR xem giấy mời** — tab "Giấy mời phỏng vấn", mọi trạng thái đơn: khung giờ, khung đã chốt (nếu có), tiêu đề, nội
dung thư, thời điểm gửi; mở danh sách giấy mời trước đó. Đơn chưa có giấy mời: câu trạng thái rỗng.

**Ứng viên xem giấy mời** — `/candidate/applications/:id`, tab "Thông tin đơn": khối giấy mời hiện danh sách khung giờ
**chỉ đọc**, đánh dấu khung đã chốt. Chưa có nút chọn (FR-U10). Khi HR gửi lại: thông báo web một dòng dẫn tới trang
chi tiết đơn, email kèm danh sách khung giờ.

**Hệ thống** — không gọi AI. Gửi lần đầu: đổi trạng thái + một thông báo đổi trạng thái như hiện nay. Gửi lại:
không đổi trạng thái, không ghi lịch sử trạng thái, một thông báo loại mới.

## 3. Quy tắc nghiệp vụ

### 3.1 Khung giờ (R-S)

- **R-S1.** Một giấy mời có **1–5** khung (`maxSlots = 5`, trả về ở API để frontend không tự hardcode, mục 4.4).
- **R-S2.** Khung gồm `scheduledAt` (thời điểm **bắt đầu**; không có thời lượng) và `location` (địa chỉ hoặc link,
  **bắt buộc** ở API — Q9).
- **R-S3.** Backend cắt `scheduledAt` về **đầu phút** (bỏ giây và phần nhỏ hơn giây) **trước** mọi kiểm tra và lưu giá
  trị đã cắt (Q1). Không từ chối giây lẻ: các test có sẵn của FR-H07 gửi `Instant.now().plus(…)` (có giây) vẫn hợp lệ.
- **R-S4.** Mỗi khung (sau R-S3) phải **sau** thời điểm server xử lý request (`> now`); khung trong phút hiện tại bị từ
  chối.
- **R-S5.** Không hai khung trùng thời điểm bắt đầu (sau R-S3) trong cùng giấy mời. Không kiểm trùng giữa các giấy mời
  hay giữa các ứng viên (mục 6).
- **R-S6.** `location`: bỏ khoảng trắng hai đầu; `null` hoặc rỗng sau khi bỏ → lỗi; dài tối đa 500 ký tự tính sau khi
  bỏ khoảng trắng; không chứa ký tự xuống dòng (giữ một dòng trong email, R-N3). Kiểm xuống dòng (`\r`, `\n`) trên giá
  trị **đã** bỏ khoảng trắng hai đầu (L9): xuống dòng ở đầu/cuối bị cắt cùng khoảng trắng (`"Tầng 3\n"` lưu `"Tầng 3"`);
  xuống dòng ở giữa bị từ chối. Cột DB cho phép `NULL` **chỉ** cho dữ
  liệu cũ chuyển từ V1 (giấy mời cũ có thể không có địa điểm — mục 4.1); mọi nơi hiển thị khung có `location` `NULL` ghi
  "Chưa có dữ liệu" (UI_GUIDE mục 4 "Dữ liệu thiếu").
- **R-S7.** Thứ tự hiển thị = thứ tự HR gửi lên (`display_order` 1…n); backend không tự sắp xếp lại.
- **R-S8.** Kiểm ở **service**, theo đúng thứ tự bảng dưới, trả lỗi đầu tiên gặp; `{n}`, `{m}` là số thứ tự khung
  (đếm từ 1 theo thứ tự gửi). Mọi lỗi → `400 INVALID_INTERVIEW_SCHEDULE` — **giữ mã lỗi có sẵn**
  (`GlobalExceptionHandler.java:249-253`, B1) — với câu tiếng Việt cố định; không lỗi nào ra 500 hay
  `NullPointerException`; không ghi gì xuống DB.

  | Trường hợp | `message` |
  |---|---|
  | `slots` là `null`, thiếu hẳn trong JSON, hoặc rỗng | "Vui lòng thêm ít nhất một khung giờ phỏng vấn." |
  | Nhiều hơn 5 khung | "Giấy mời có tối đa 5 khung giờ." |
  | Một phần tử là `null`, hoặc `scheduledAt` của nó là `null`/thiếu | "Khung giờ {n}: vui lòng chọn thời điểm phỏng vấn." |
  | Khung không ở tương lai (R-S4) | "Khung giờ {n}: thời điểm phỏng vấn phải ở trong tương lai." |
  | Khung trùng thời điểm với một khung đứng trước (R-S5) | "Khung giờ {n} trùng thời điểm với khung giờ {m}." |
  | `location` `null`, thiếu, rỗng hoặc chỉ khoảng trắng | "Khung giờ {n}: vui lòng nhập địa điểm hoặc link phỏng vấn." |
  | `location` > 500 ký tự sau khi bỏ khoảng trắng | "Khung giờ {n}: địa điểm tối đa 500 ký tự." |
  | `location` chứa `\r` hoặc `\n` | "Khung giờ {n}: địa điểm chỉ được nằm trên một dòng." |

  Hai dòng đầu kiểm trên cả danh sách; sau đó kiểm lần lượt khung 1 tới n, mỗi khung theo thứ tự dòng 3 → 8.
  `subject`/`content` giữ nguyên Bean Validation hiện có (`@NotBlank`, `@Size`,
  `InterviewInvitationSendRequest.java:14-18`) — lỗi trường đó trả trước khi vào service, như hiện nay.

### 3.2 Khung đã chốt (R-K)

- **R-K1.** Giấy mời có đúng 1 khung → khung đó **đã chốt ngay khi gửi**: `confirmed_slot_id` = khung đó,
  `confirmed_at` = `sent_at` (cùng một giá trị `Instant` Java). Giữ tương thích luồng cũ. **Thứ tự ghi (L2):**
  `confirmed_slot_id` là cột `UUID` thường (không quan hệ JPA); ghi theo ba bước: (1) giấy mời `saveAndFlush`, **chưa**
  có khung chốt; (2) các khung `saveAllAndFlush`; (3) giấy mời một khung: đặt `confirmedSlotId` + `confirmedAt` rồi flush
  (câu `UPDATE`). Áp cho cả I2 (R-F1) và I3 (R-G3 bước 6).
- **R-K2.** Giấy mời có ≥ 2 khung → gửi đi với `confirmed_slot_id`, `confirmed_at` = `NULL`. FR-H12 **không** có đường
  nào ghi hai cột này cho giấy mời nhiều khung; đường ghi là của FR-U10.
- **R-K3.** Khung đã chốt phải thuộc chính giấy mời đó; `confirmed_slot_id` và `confirmed_at` cùng `NULL` hoặc cùng khác
  `NULL` — chốt ở DB (mục 4.1).

### 3.3 Nội dung thư và tính bất biến (R-C)

- **R-C1.** `rendered_content` chỉ là thư HR đã sửa từ mẫu, lưu **nguyên văn** như request (như hiện nay). Backend
  **không** chèn danh sách khung giờ, thời điểm hay địa điểm vào `subject`/`rendered_content`. Mẫu FR-H02 và preview
  (`GET …/preview`) giữ nguyên, không thêm placeholder; `InterviewTemplateOwnerIntegrationTest` không sửa.
- **R-C2.** Khung giờ, tiêu đề, nội dung, thời điểm gửi của giấy mời đã gửi **không sửa được**: không endpoint sửa/xoá;
  entity khung giờ `@Immutable`; bảng khung giờ không có `updated_at`, không trigger. Cột của `interview_invitations` chỉ
  được ghi sau khi tạo ở hai chỗ: `superseded_at` (R-G3, khi gửi lại) và `confirmed_slot_id`/`confirmed_at` (FR-U10).
- **R-C3.** Sửa mẫu giấy mời sau khi gửi không đổi giấy mời đã gửi (giữ test `send_thenTemplateEditedAfterward_invitationContentUnchanged`).

### 3.4 Gửi lần đầu (R-F)

- **R-F1.** `POST /api/hr/applications/{id}/interview-invitation` (I2) giữ luồng hiện có, chỉ đổi phần lịch: trong
  **một** transaction: kiểm quyền (như hiện nay) → đòi mẫu giấy mời tồn tại (như hiện nay) → kiểm khung (R-S8) →
  `applicationStatusService.changeStatus(…, INTERVIEW_INVITED)` (giữ nguyên, tự ghi lịch sử và phát
  `ApplicationStatusChangedEvent`) → tạo giấy mời (`superseded_at` `NULL`) + các khung + R-K1/R-K2.
- **R-F2.** Thông báo và email của lần gửi đầu **giữ nguyên** như hiện nay (`APPLICATION_STATUS_CHANGED`, câu đổi trạng
  thái, không kèm giờ — M6). FR-H12 không phát thêm thông báo nào ở I2.
- **R-F3.** Đơn không `PENDING` → giữ hành vi hiện có: `400 INVALID_APPLICATION_STATUS_TRANSITION`, không tạo dòng
  nào (test `send_applicationAlreadyInterviewInvited_returns400` giữ nguyên khẳng định, chỉ đổi thân request sang dạng
  khung giờ).
  *Sửa bởi FR-C09 (31/10/2026, C09 REQUIREMENT M6, R-S3): đơn `REJECTED` có yêu cầu xem xét lại `OPEN` gửi giấy mời
  được — trong cùng transaction yêu cầu chuyển `ACCEPTED` rồi đơn đi cạnh riêng `REJECTED → INTERVIEW_INVITED`
  (`ApplicationStatusService.reinviteAfterReconsideration`, không qua `ALLOWED_TRANSITIONS`); thua tranh chấp → 409
  `RECONSIDERATION_NOT_OPEN`. Mọi ca khác của R-F3 giữ nguyên 400 (đơn `REJECTED` không có yêu cầu hoặc yêu cầu đã
  `ACCEPTED`/`DECLINED`, đơn `HIRED`, `WITHDRAWN` — C09 T11).*
- **R-F4.** Chốt cuối chống hai giấy mời hiệu lực là partial unique index `uq_invitation_active` (mục 4.1); vi phạm →
  `409 INTERVIEW_INVITATION_CONFLICT` (R-G6).

### 3.5 Gửi lại giấy mời (R-G)

- **R-G1.** Endpoint riêng `POST /api/hr/applications/{id}/interview-invitation/resend` (I3), cùng thân request với I2.
  **Không** gọi `ApplicationStatusService.changeStatus`, **không** ghi `application_status_history`, **không** phát
  `ApplicationStatusChangedEvent`, không đòi mẫu giấy mời tồn tại.
- **R-G2.** Chỉ khi đơn đang `INTERVIEW_INVITED`, **kể cả khi** giấy mời hiệu lực đã có khung chốt. Đơn ở trạng thái
  khác → `409 INVITATION_RESEND_NOT_ALLOWED`, không ghi gì, không thông báo. Chọn **409** chứ không 400 (B1): theo phân
  biệt ở `GlobalExceptionHandler.java:232-233`, 400 `INVALID_APPLICATION_STATUS_TRANSITION` dành cho "HR gửi sai bước
  chuyển trong request", còn đây là "tài nguyên không còn ở trạng thái cho phép hành động" — cùng nhóm với
  `ApplicationNotWithdrawableException` (409).
- **R-G3.** Một transaction ngắn, **đúng thứ tự dưới đây — bắt buộc**: (1) kiểm quyền `HrApplicationAccess.loadOwned`;
  (2) kiểm khung (R-S8) — "tương lai" (R-S4) so với một mốc `now` **riêng** lấy ngay tại bước này, vì bước 2 chạy
  trước khi khoá (L11); (3) **khoá hàng đơn và kiểm trạng thái trong cùng một câu** (Q2):
  `UPDATE job_applications SET status = status WHERE id = :id AND status = 'INTERVIEW_INVITED'` — method mới
  `JobApplicationRepository.lockIfInterviewInvited` (native), khai `@Modifying(clearAutomatically = true,
  flushAutomatically = true)` như `updateStatusIfCurrent` (`:30-35`); số dòng 0 → `409 INVITATION_RESEND_NOT_ALLOWED`;
  (4) lấy `now` **một lần** bằng Java — `superseded_at` của giấy mời cũ (bước 5) và `sent_at` của giấy mời mới (bước 6)
  dùng chung đúng mốc này (L11); (5) `UPDATE interview_invitations SET superseded_at = :now WHERE application_id
  = :id AND superseded_at IS NULL` (`@Modifying(clearAutomatically = true)`) — 0 dòng vẫn tiếp tục (đơn
  `INTERVIEW_INVITED` mà không có giấy mời chỉ có thể do dữ liệu ghi tay); (6) tạo giấy mời mới với `sent_at = :now`,
  `superseded_at` `NULL` + các khung + R-K1/R-K2, ghi bằng `saveAndFlush`; (7) phát `InterviewInvitationResentEvent`
  (R-N1). Không lời gọi ngoài nào trong transaction. **Không** dùng `SELECT … FOR SHARE`/`FOR UPDATE`/`@Lock` (Q2).

  **Khoá hàng đơn (bước 3) PHẢI chạy TRƯỚC mọi thao tác đọc hay đánh dấu giấy mời hiệu lực (bước 5, 6).** Bước (1) chỉ
  đọc đơn/Job/công ty để kiểm quyền, không đọc `interview_invitations`. Nếu đọc giấy mời hiệu lực trước khi khoá (vd
  nạp entity giấy mời hiệu lực rồi mới khoá, hoặc `findByApplicationIdAndSupersededAtIsNull` ở bước 1–2): hai lần gửi lại
  đồng thời cùng thấy giấy mời X là hiệu lực; lượt trước tạo Y và commit; lượt sau (sau khi được khoá) vẫn đánh dấu X
  theo dữ liệu đã đọc và tạo Z trong khi Y còn `superseded_at IS NULL` → vi phạm `uq_invitation_active` → lượt gửi lại
  hợp lệ bị trả lỗi `409 INTERVIEW_INVITATION_CONFLICT`. Đúng thứ tự thì câu (5) — một câu `UPDATE` theo `application_id`, chạy **sau**
  khi đã có khoá — luôn thấy giấy mời của lượt trước (T10a).
- **R-G4. Vì sao câu `UPDATE` ở bước (3) tuần tự hoá được với Trúng tuyển / Từ chối / Rút đơn.** Postgres (READ
  COMMITTED, mặc định) giữ khoá hàng do `UPDATE` lấy tới khi transaction kết thúc; một `UPDATE` khác trên **cùng hàng**
  phải chờ, rồi **đánh giá lại** điều kiện `WHERE` trên phiên bản hàng mới nhất đã commit trước khi ghi. Áp vào ba đường
  đổi trạng thái đơn đang có:

  | Đường đổi trạng thái | Câu ghi | Gửi lại chạy câu (3) **trước** | Đường kia commit **trước** |
  |---|---|---|---|
  | Trúng tuyển / Từ chối (`ApplicationStatusService.changeStatus`) | `updateStatusIfCurrent`: `UPDATE … SET status = :new WHERE id = :id AND status = 'INTERVIEW_INVITED'` (a17) | Câu kia chờ khoá; gửi lại commit; câu kia đánh giá lại `WHERE`, thấy vẫn `INTERVIEW_INVITED` → ghi `HIRED`/`REJECTED`. Thứ tự thật: gửi lại → kết quả cuối | Câu (3) chờ rồi đánh giá lại, thấy `HIRED`/`REJECTED` → 0 dòng → 409; không giấy mời nào được tạo |
  | Rút đơn (`ApplicationService.withdraw`) | `save` entity → `UPDATE … SET status = 'WITHDRAWN' … WHERE id = :id` (a18, không điều kiện trạng thái) | Câu kia chờ khoá rồi ghi `WITHDRAWN` sau khi gửi lại commit — thứ tự hợp lệ (`INTERVIEW_INVITED` → rút đơn được) | Câu (3) đánh giá lại, thấy `WITHDRAWN` → 0 dòng → 409 |
  | Gửi lại khác cùng lúc (hai tab HR) | Chính câu (3) | Lần sau chờ, đánh giá lại (vẫn `INTERVIEW_INVITED`) rồi chạy tiếp; câu (5) của nó là **câu lệnh mới** nên thấy giấy mời lần trước vừa tạo và đánh dấu `superseded_at` → cả hai 201, đúng một giấy mời hiệu lực | — |

  Không có thứ tự nào để giấy mời mới được tạo **sau khi** đơn đã có kết quả cuối hoặc đã rút. Hệ quả phụ: câu (3) làm
  trigger `set_updated_at` đổi `job_applications.updated_at` (a19) dù giá trị nghiệp vụ không đổi — chấp nhận theo tiền lệ
  FR-U09 Q19 (cột không được hiển thị hay dùng để sắp xếp); T5 loại trừ cột này khi so. Câu khai `status = status` chứ
  không `SET` cột khác để không có giá trị nghiệp vụ nào đổi.
- **R-G5.** Giấy mời cũ giữ nguyên **mọi** cột trừ `superseded_at`: khung, khung đã chốt, `confirmed_at`, tiêu đề, nội
  dung, `sent_at`, `sent_by`. Khung đã chốt của giấy mời cũ hết hiệu lực vì giấy mời không còn hiệu lực.
- **R-G6.** `uq_invitation_active` là chốt chặn cuối cho mọi đường còn lại (vd dữ liệu ghi tay); vi phạm →
  `GlobalExceptionHandler` dịch thành `409 INTERVIEW_INVITATION_CONFLICT` (thêm nhánh vào `handleDataIntegrityViolation`,
  `GlobalExceptionHandler.java:403-424`).

### 3.6 Giấy mời hiệu lực và đọc giấy mời (R-A)

- **R-A1.** Giấy mời hiệu lực của đơn = dòng `superseded_at IS NULL` (tối đa một, R-F4). **Không** xác định bằng
  `created_at`/`sent_at`. Mọi chỗ cần "giấy mời hiện tại" — phía ứng viên (I4), K1 (R-K1x), điều kiện soạn nháp
  (R-K2x) — dùng một method repository `findByApplicationIdAndSupersededAtIsNull`.
- **R-A2.** I1 (HR) trả **mọi** giấy mời của đơn: giấy mời hiệu lực trước, rồi các giấy mời cũ theo `superseded_at`
  giảm dần; khung theo `display_order`. Đơn chưa có giấy mời → `200` với danh sách rỗng (không 404). Không lọc theo
  trạng thái đơn hay trạng thái Job.
- **R-A3.** I4 (ứng viên, `GET /api/candidates/applications/{id}/interview-invitation`) trả **chỉ** giấy mời hiệu lực,
  khung có `id` (Q4 — FR-U10 dùng để gửi lựa chọn); không có → `404 INTERVIEW_INVITATION_NOT_FOUND` như hiện nay. Giữ
  nguyên kiểm sở hữu và quy ước 403 cho đơn của ứng viên khác (`InterviewInvitationService.java:123-140`). Ứng viên không
  thấy giấy mời cũ.
- **R-A4.** I1, I3 dưới `/api/hr/**`, kiểm bằng `HrApplicationAccess.loadOwned` (công ty trước → đơn → 403 đơn công ty
  khác). I2 giữ method kiểm quyền hiện có (`InterviewInvitationService.loadOwnedApplication`, `:164-176`) — không sửa thứ
  tự kiểm quyền của endpoint cũ.

### 3.7 K1 và FR-C07 — thay đổi hành vi của FR đã hoàn thành (R-K1x, R-K2x, R-K3x)

- **R-K1x.** `ConversationContextAssembler.forConversation` (`:77-83`): `InterviewSchedule` = (`scheduledAt`,
  `location`) của **khung đã chốt** của giấy mời hiệu lực; giấy mời hiệu lực chưa chốt hoặc không có giấy mời →
  `null`. **Không** lấy khung sớm nhất thay cho khung chốt; không đọc giấy mời cũ. Record `InterviewSchedule` giữ
  nguyên hai thành phần (`ConversationContextSignatureTest.java:55-56` không sửa). Sửa comment `ConversationContext.java:22`,
  `:31` cho đúng nghĩa mới.
- **R-K2x.** `MessageDraftFacade.unavailableReason` (`:112-124`) nhận hai cờ tính từ giấy mời hiệu lực thay cho một cờ
  `hasInvitation` (`:157-160`). **Không** thêm mã lý do mới (Q3): `DraftUnavailableReason` giữ ba giá trị.

  | Tình huống | Dùng được khi | Không dùng được |
  |---|---|---|
  | `INTERVIEW_REMINDER` (HR) | `INTERVIEW_INVITED` **và** giấy mời hiệu lực **có khung đã chốt** | `NO_ACTIVE_INTERVIEW` (gồm cả ca có giấy mời nhưng chưa chốt) |
  | `REQUEST_RESCHEDULE` (ứng viên) | `INTERVIEW_INVITED` **và** có giấy mời hiệu lực (chốt hay chưa) | `NO_ACTIVE_INTERVIEW` |
  | `THANK_AFTER_INTERVIEW` | Có giấy mời hiệu lực (như cũ — mọi đơn đã có giấy mời đều có đúng một giấy mời hiệu lực) | `NO_INTERVIEW` |

  Câu hiển thị của `NO_ACTIVE_INTERVIEW` giữ nguyên ("Chỉ dùng được khi đơn đang chờ phỏng vấn và đã có giấy mời.",
  `draftLabels.ts:23`) — với ca chưa chốt câu này không nêu đúng lý do; chấp nhận (Q3). Khi `REQUEST_RESCHEDULE` chạy
  với giấy mời chưa chốt, prompt nhận dòng lịch `"chưa có lịch"` có sẵn (`MessageDraftService.java:39`, `:126-129`);
  không đưa danh sách khung vào prompt.
- **R-K3x.** Test C07 bị ảnh hưởng (sửa ở đợt 4, mục 9): `ConversationContextAssemblerIntegrationTest` (helper
  `insertInvitation` `:219-231` đang gọi `setScheduledAt`/`setLocation`; test `forConversation_usesLatestInvitation_scheduledAtAndLocation`
  `:266-279` dựa trên `created_at` — đổi thành "dùng khung chốt của giấy mời hiệu lực, bỏ qua giấy mời cũ";
  `forConversation_invitationWithoutLocation_locationNull` `:281-288` giữ ý nghĩa với khung chốt `location` `NULL` —
  dữ liệu cũ, chèn bằng SQL); `MessageDraftIntegrationTestSupport` (helper `insertInvitation` `:411-421` thêm tham số số
  khung/đã chốt; `deleteInvitations` `:423-425` giữ nguyên, khung bị xoá theo `ON DELETE CASCADE`);
  `MessageDraftScenarioIntegrationTest.interviewReminderAndReschedule_onlyWhenInterviewInvitedWithInvitation`
  (`:102-125`) tách ca chốt/chưa chốt; `thankAfterInterview_onlyWhenInvitationExists` (`:128-145`) giữ nghĩa;
  `MessageDraftPromptIntegrationTest.t12_contextFields_interviewInHoChiMinhTime_noHrIdentity_bothSides` (`:90-117`) dùng
  giấy mời một khung (đã chốt) — kỳ vọng không đổi. `ConversationContextSignatureTest` **không** sửa và phải xanh.

### 3.8 Thông báo (R-N)

- **R-N1.** Loại mới `NotificationType.INTERVIEW_INVITATION_RESENT`, gửi cho **ứng viên** của đơn, chỉ khi gửi lại
  (I3). Sự kiện `InterviewInvitationResentEvent(applicationId, jobId, candidateId, List<SlotInfo> slots)` — khai trong
  gói `notification/`, `SlotInfo(Instant scheduledAt, String location)` theo `display_order` — phát **trong**
  transaction gửi lại (R-G3 bước 7), mang **giá trị khung vừa lưu** trong chính transaction đó. Listener
  `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`, lỗi không văng ra ngoài — mẫu
  `onApplicationStatusChanged` (`NotificationEventListener.java:55-65`); dựng `body` và `email_body` **một lần** lúc tạo
  dòng thông báo từ dữ liệu trong sự kiện, **không** truy vấn bảng giấy mời. Dòng thông báo: `entity_type =
  'APPLICATION'`, `entity_id = applicationId` — như mọi loại cũ (`NotificationEventListener.save`, `:153-165`).
  Phụ thuộc gói một chiều: `interviewinvitation/` → `notification/` (phát sự kiện, như `ApplicationStatusService`);
  `notification/` **không** import `interviewinvitation/` (kiểm tĩnh 7.1).
- **R-N2. Thông báo web — một dòng** (Q5), dựng ở `NotificationContentBuilder.forInvitationResent(Job, List<SlotInfo>,
  UUID applicationId, String frontendBaseUrl)` (hàm thuần, như các hàm hiện có): tiêu đề "Nhà tuyển dụng đã gửi lại
  giấy mời phỏng vấn"; `body` một câu, không ký tự xuống dòng: `Giấy mời phỏng vấn vị trí "{tiêu đề tin}" vừa được gửi
  lại với {n} khung giờ. Xem chi tiết trong đơn ứng tuyển.` (một khung: `… vừa được gửi lại với lịch phỏng vấn mới. Xem
  chi tiết trong đơn ứng tuyển.`); `link` = `/candidate/applications/{applicationId}` (trang FR-U08). `body` **không**
  chứa khung giờ.
- **R-N3. Email — kèm danh sách khung** (Q5). Cột mới `notifications.email_body TEXT NULL` (V16, mục 4.1). Cùng hàm
  `forInvitationResent` trả thêm `emailBody` (mở rộng record `Content` bằng thành phần `emailBody`, các hàm cũ trả
  `null`); listener ghi vào cột `email_body` của dòng thông báo. `frontendBaseUrl` lấy từ `app.frontend-base-url` (cùng
  thuộc tính `NotificationMailOrchestrator.java:49` đang đọc), tiêm vào listener. Khối `email_body`:

  ```
  {body}

  Khung giờ (giờ Việt Nam):
  1. 09:00 20/10/2026 — Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng
  2. 14:30 21/10/2026 — https://meet.example.com/abc-defg-hij

  Xem giấy mời tại: {frontendBaseUrl}/candidate/applications/{id}
  ```

  Giờ `HH:mm dd/MM/yyyy` theo `Asia/Ho_Chi_Minh` (cùng định dạng `MessageDraftService.java:42-43`); `location` `null`
  ghi "Chưa có dữ liệu" (không xảy ra với giấy mời mới — R-S6 — giữ cho hàm thuần không ném lỗi). Tiêu đề email =
  `title`. Không đưa `rendered_content` vào thông báo hay email.
- **R-N3a. Gửi email.** `NotificationMailOrchestrator.mailText` (`:58-65`): `email_body` khác `null` → gửi nguyên văn
  `email_body`; `null` → giữ đúng hành vi hiện có: `NEW_MESSAGE` = `body + "\n\nXem và trả lời tại: " + frontendBaseUrl
  + link`, mọi loại khác = `body` nguyên văn. Mọi loại cũ ghi `email_body = NULL` nên hành vi email cũ không đổi.
  `email_body` **không** trả ra API thông báo (`NotificationResponse` không thêm thành phần) — web chỉ hiện `body`.
  Email đã gửi được lưu nguyên văn trong `email_body` (không dựng lại lúc poller chạy). FR-U10 dùng lại cột này cho
  email "ứng viên đã chốt lịch" nếu cần.
- **R-N4.** Thông báo "ứng viên đã chốt lịch" cho HR **không** thuộc FR-H12 (FR-U10). Gửi lần đầu không đổi thông báo
  (R-F2).
- **R-N5.** Thông báo không chứa điểm, rubric, giải thích AI hay ghi chú HR (CLAUDE.md §8; comment
  `NotificationContentBuilder.java:7-9`).

### 3.9 Ranh giới với FR-U10 (R-B)

| Phần | FR-H12 | FR-U10 |
|---|---|---|
| Bảng `interview_invitation_slots`, cột `confirmed_slot_id`, `confirmed_at`, `superseded_at`, chuyển dữ liệu cũ (V16) | ✔ | |
| Gửi 1–5 khung (I2), gửi lại (I3), đọc phía HR (I1), tab "Giấy mời phỏng vấn" ở `/hr/applications/:id` | ✔ | |
| Đổi DTO I4 (kèm `id` khung — Q4) + `InterviewInvitationDetails.tsx`: danh sách khung **chỉ đọc**, đánh dấu khung đã chốt | ✔ | |
| Thông báo ứng viên khi HR gửi lại (`INTERVIEW_INVITATION_RESENT`), email kèm khung | ✔ | |
| Endpoint ứng viên chọn khung, ghi `confirmed_slot_id`/`confirmed_at`, chặn chọn lần hai, chặn khung đã qua | | ✔ |
| Nút/khối chọn khung ở `/candidate/applications/:id`; thay câu hướng dẫn Q8 | | ✔ |
| Thông báo "ứng viên đã chốt lịch" cho HR | | ✔ |

- **R-B1.** FR-H12 không thêm endpoint ứng viên nào ngoài việc đổi hình dạng I4; không có code nào ghi
  `confirmed_slot_id` cho giấy mời nhiều khung (R-K2).

*Sửa bởi FR-U10 (14/10/2026): ba dòng cột FR-U10 đã làm — endpoint `POST
/api/candidates/applications/{id}/interview-invitation/confirm-slot` ghi `confirmed_slot_id`/`confirmed_at` bằng một câu
`UPDATE` có điều kiện sau `lockIfInterviewInvited` (U10 R-W); phần chọn khung + hộp xác nhận ở `/candidate/applications/:id`,
thay câu Q8 (U10 R-U); thông báo `INTERVIEW_SLOT_CONFIRMED` cho chủ công ty (U10 R-N). Tab HR thêm dòng "Ứng viên đã chọn
lúc …" (U10 R-H1). Xem `docs/features/UV/U10/REQUIREMENT.md`.*

## 4. Dữ liệu & quyền truy cập

### 4.1 Migration `V16__interview_invitation_slots.sql`

Khung dưới là nội dung dự kiến; đợt 2 có thể sửa chi tiết cú pháp nhưng không đổi ràng buộc (sửa thì ghi vào mục 12).

```sql
-- FR-H12 - Giay moi phong van nhieu khung gio (REQUIREMENT.md muc 4.1).
-- Quy uoc CHECK (FR-H10 L7): Postgres coi CHECK ra NULL la DAT - cot duoc NULL co chu dich phai xu ly NULL tuong minh.

-- 1. Khung gio cua giay moi. BAT BIEN (R-C2): khong updated_at, khong trigger.
CREATE TABLE interview_invitation_slots (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invitation_id  UUID NOT NULL REFERENCES interview_invitations(id) ON DELETE CASCADE,
    display_order  SMALLINT NOT NULL CHECK (display_order BETWEEN 1 AND 5),
    -- Thoi diem BAT DAU, khong co thoi luong (R-S2). Service cat ve dau phut (R-S3); du lieu chuyen tu V1 giu
    -- nguyen gia tri cu nen KHONG dat CHECK giay = 0 o day.
    scheduled_at   TIMESTAMPTZ NOT NULL,
    -- NULL co chu dich CHI cho du lieu cu chuyen tu V1 (location V1 tuy chon). API moi bat buoc (R-S6); do dai 500
    -- kiem o service (du lieu cu khong gioi han) - khong CHECK.
    location       TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_invitation_slot_time  UNIQUE (invitation_id, scheduled_at),
    CONSTRAINT uq_invitation_slot_order UNIQUE (invitation_id, display_order),
    -- Dich cua FK ghep fk_invitation_confirmed_slot ben duoi.
    CONSTRAINT uq_invitation_slot_owner UNIQUE (invitation_id, id)
);

-- 2. Cot moi cua giay moi. Ca ba NULL co chu dich: chua chot (R-K2) / dang hieu luc (R-A1).
ALTER TABLE interview_invitations
    ADD COLUMN confirmed_slot_id UUID,
    ADD COLUMN confirmed_at      TIMESTAMPTZ,
    ADD COLUMN superseded_at     TIMESTAMPTZ;

-- 3. Chuyen du lieu cu: moi giay moi -> 1 khung tu scheduled_at/location cu, danh dau da chot (confirmed_at =
--    sent_at, sent_at NULL thi created_at).
INSERT INTO interview_invitation_slots (invitation_id, display_order, scheduled_at, location, created_at)
SELECT id, 1, scheduled_at, location, created_at FROM interview_invitations;

UPDATE interview_invitations i
SET confirmed_slot_id = s.id,
    confirmed_at      = COALESCE(i.sent_at, i.created_at)
FROM interview_invitation_slots s
WHERE s.invitation_id = i.id;

-- 4. Moi don giu DUNG MOT giay moi hieu luc (moi nhat theo created_at, hoa thi theo id). API cu khong tao duoc hai
--    giay moi cho mot don nen buoc nay du kien cap nhat 0 dong; created_at chi dung o day, MOT lan, de chon ban giu.
UPDATE interview_invitations i
SET superseded_at = now()
FROM (SELECT id, row_number() OVER (PARTITION BY application_id ORDER BY created_at DESC, id DESC) AS rn
      FROM interview_invitations) r
WHERE r.id = i.id AND r.rn > 1;

-- 5. Rang buoc.
CREATE UNIQUE INDEX uq_invitation_active ON interview_invitations(application_id) WHERE superseded_at IS NULL;

-- Khung da chot phai thuoc chinh giay moi do (R-K3). MATCH SIMPLE: confirmed_slot_id NULL thi khong kiem.
ALTER TABLE interview_invitations
    ADD CONSTRAINT fk_invitation_confirmed_slot
        FOREIGN KEY (id, confirmed_slot_id) REFERENCES interview_invitation_slots(invitation_id, id),
    -- Hai cot cung NULL hoac cung khac NULL. Bieu thuc chi dung IS NULL -> khong bao gio ra NULL.
    ADD CONSTRAINT chk_invitation_confirmed_pair
        CHECK ((confirmed_slot_id IS NULL) = (confirmed_at IS NULL));

-- 6. Bo cot cu - khong giu song song (muc 8).
ALTER TABLE interview_invitations DROP COLUMN scheduled_at, DROP COLUMN location;

-- 7. Noi dung email rieng cua mot thong bao (R-N3, R-N3a). NULL co chu dich: moi loai thong bao cu va moi dong da co
--    -> email giu hanh vi hien co (dung body). Khac NULL -> email gui NGUYEN VAN cot nay. Khong CHECK.
ALTER TABLE notifications ADD COLUMN email_body TEXT;
```

- Xoá giấy mời (chỉ xảy ra qua `ON DELETE CASCADE` từ `job_applications`, hoặc script seed) kéo theo xoá khung; không
  xoá khung trước giấy mời được vì `fk_invitation_confirmed_slot` tham chiếu ngược. Đợt 2 kiểm thật thứ tự này bằng T16
  và bằng chạy `reset-test-data.sql`.
- Giữ `idx_invitation_app`. `uq_invitation_slot_time`/`uq_invitation_slot_order` tạo index đủ cho đọc khung theo giấy
  mời; không thêm index khác.

### 4.2 Package và file backend

Không package mới; mọi thứ ở `interviewinvitation/` (CLAUDE.md §3), trừ phần thông báo ở `notification/`, câu khoá ở
`jobapplication/` và K1/C07 ở `aicontext/`, `messagedraft/`.

| File | Thay đổi |
|---|---|
| `interviewinvitation/InterviewInvitation.java` | Bỏ `scheduledAt`, `location`; thêm `confirmedSlotId`, `confirmedAt`, `supersededAt` |
| `interviewinvitation/InterviewInvitationSlot.java` (mới) | Entity `@Immutable` của `interview_invitation_slots`; `invitation_id` là cột `UUID` thường; `created_at` `@Column(insertable = false, updatable = false)`, **không** `@Generated` (khuôn `JobAiGeneration.java:44-47`, L3) |
| `interviewinvitation/InterviewInvitationSlotRepository.java` (mới) | Đọc khung theo một hay nhiều giấy mời, sắp `display_order` |
| `interviewinvitation/InterviewInvitationRepository.java` | Thêm `findByApplicationIdAndSupersededAtIsNull`, câu `UPDATE … superseded_at` (`@Modifying(clearAutomatically = true)`); giữ `findByApplicationIdOrderByCreatedAtDesc` cho test cũ nhưng **không** dùng trong `src/main` |
| `jobapplication/JobApplicationRepository.java` | Thêm `lockIfInterviewInvited` (R-G3 bước 3), đặt cạnh `updateStatusIfCurrent` |
| `interviewinvitation/InterviewInvitationService.java` | I2 đổi phần lịch; thêm I1, I3; I4 đọc giấy mời hiệu lực; một bộ kiểm khung dùng chung cho I2/I3 (R-S8) |
| `interviewinvitation/InterviewInvitationController.java` | Thêm I3; thêm mapping cho I1 (đường dẫn số nhiều, mục 4.3) |
| `interviewinvitation/dto/*` | Mục 4.4 |
| `common/exception/` | `InvalidInterviewScheduleException` nhận câu theo R-S8 (giữ mã); `InvitationResendNotAllowedException` (409); nhánh `uq_invitation_active` trong `GlobalExceptionHandler` |
| `notification/NotificationType.java`, `InterviewInvitationResentEvent.java` (mới, kèm `SlotInfo`), `Notification.java` (thêm `emailBody`), `NotificationEventListener.java`, `NotificationContentBuilder.java` (`Content` thêm `emailBody`), `NotificationMailOrchestrator.java` (`mailText` ưu tiên `email_body`) | R-N1–R-N3a. `notification/` **không** import `interviewinvitation/`; `NotificationResponse` không đổi |
| `aicontext/ConversationContextAssembler.java`, `ConversationContext.java` (chỉ comment) | R-K1x |
| `messagedraft/MessageDraftFacade.java` | R-K2x. `DraftUnavailableReason.java` **không** sửa (Q3) |

### 4.3 Endpoint

| # | Endpoint | Mô tả | Thành công | Lỗi riêng (ngoài 401/403/404 của R-A3, R-A4) |
|---|---|---|---|---|
| I1 | `GET /api/hr/applications/{applicationId}/interview-invitations` | Mọi giấy mời của đơn (R-A2) | 200 `InterviewInvitationListResponse` | — |
| I2 | `POST /api/hr/applications/{applicationId}/interview-invitation` | Gửi lần đầu (R-F) — **đổi thân request** | 201 `InterviewInvitationResponse` | 400 `INVALID_INTERVIEW_SCHEDULE` (R-S8); 400 trường `subject`/`content`; 400 `INVALID_APPLICATION_STATUS_TRANSITION`; 409 `APPLICATION_STATUS_CONFLICT` (có sẵn); 409 `INTERVIEW_INVITATION_CONFLICT` |
| I3 | `POST /api/hr/applications/{applicationId}/interview-invitation/resend` | Gửi lại (R-G) | 201 `InterviewInvitationResponse` | 400 `INVALID_INTERVIEW_SCHEDULE`; 400 trường; 409 `INVITATION_RESEND_NOT_ALLOWED`; 409 `INTERVIEW_INVITATION_CONFLICT` |
| — | `GET /api/hr/applications/{applicationId}/interview-invitation/preview` | Không đổi hành vi; response thêm `maxSlots` | 200 | — |
| I4 | `GET /api/candidates/applications/{applicationId}/interview-invitation` | Giấy mời hiệu lực (R-A3) — **đổi hình dạng response** | 200 `CandidateInterviewInvitationResponse` | 404 `INTERVIEW_INVITATION_NOT_FOUND` (có sẵn) |

Câu lỗi mới (tiếng Việt cố định):

| Mã | HTTP | `message` |
|---|---|---|
| `INVITATION_RESEND_NOT_ALLOWED` | 409 | "Chỉ gửi lại giấy mời được khi đơn đang ở trạng thái Đã mời phỏng vấn." |
| `INTERVIEW_INVITATION_CONFLICT` | 409 | "Giấy mời của đơn này vừa được thay đổi ở nơi khác. Vui lòng tải lại trang." |

Không endpoint `PUT`/`PATCH`/`DELETE` nào cho giấy mời hay khung giờ (R-C2).

### 4.4 DTO

```java
// Than request chung cua I2 va I3. slots KHONG @NotEmpty/@Size - kiem o service de tra dung cau theo khung (R-S8).
record InterviewInvitationSendRequest(
        List<SlotRequest> slots,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank @Size(max = 10000) String content) {
    record SlotRequest(Instant scheduledAt, String location) {}   // location KHONG @NotBlank - kiem o service (R-S8)
}

record InterviewInvitationResponse(            // I2, I3, phan tu cua I1
        UUID id, UUID applicationId,
        boolean active,                        // superseded_at IS NULL (R-A1)
        String subject, String renderedContent,
        Instant sentAt, UUID sentBy,
        Instant supersededAt,                  // null khi active
        UUID confirmedSlotId, Instant confirmedAt,   // null khi chua chot
        List<Slot> slots) {                    // theo display_order
    record Slot(UUID id, int displayOrder, Instant scheduledAt, String location) {}  // location null chi o du lieu cu
}

record InterviewInvitationListResponse(UUID applicationId, int maxSlots, List<InterviewInvitationResponse> invitations) {}

record InterviewInvitationPreviewResponse(     // co san, THEM maxSlots
        String subject, String content, String candidateName,
        boolean companyNameMismatch, String templateCompanyName, String currentCompanyName,
        int maxSlots) {}

// I4 - chi giay moi hieu luc. Khung CO id (Q4, FR-U10 dung de gui lua chon). KHONG id giay moi, applicationId,
// sentBy, supersededAt.
record CandidateInterviewInvitationResponse(
        String subject, String renderedContent, Instant sentAt,
        Instant confirmedAt,                   // null = chua chot
        List<Slot> slots) {
    record Slot(UUID id, int displayOrder, Instant scheduledAt, String location, boolean confirmed) {}
}
```

`List` chuẩn hoá `null` → `List.of()` trong compact constructor; số dùng `int` vì luôn có giá trị (không phải schema
JSON của LLM). Không field nào tên `verdict`, `label`, `isQualified`, `passed`, `recommendation` (CLAUDE.md §7).
Thân request cũ (`scheduledAt`/`location` ở gốc) không còn được nhận: `slots` thiếu → 400 dòng đầu bảng R-S8.

### 4.5 Seed kiểm thử tay — `db/seed/seed-test.sql`

Sửa ở đợt 2. UUID khung giờ dùng tiền tố mới `f0` (tiền tố `e0…ef` đã dùng hết): sửa comment đầu file "e0..ef" →
"e0..ef, f0" và `db/seed/README.md:255` cho khớp. Giờ hiển thị là giờ Việt Nam. Giấy mời ghi trước với
`confirmed_slot_id`/`confirmed_at` `NULL`, rồi khung, rồi một câu `UPDATE` đặt khung chốt (FK ghép, mục 4.1); mọi câu
vẫn idempotent.

| Giấy mời | Đơn | Hiệu lực | Khung (`display_order`: thời điểm — địa điểm) | Khung chốt |
|---|---|---|---|---|
| `ea…01` (hiện có) | A2 Quốc Huy, `INTERVIEW_INVITED` | **Không** — `superseded_at` = `sent_at` của `ea…04` | `f0…01`: hôm nay + 7 ngày 2 giờ — "Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng" (giá trị cũ) | `f0…01` |
| `ea…04` (mới, gửi lại) | A2 | Có; `sent_at` `2026-10-06 02:00:00+00` | `f0…04`: hôm nay + 8 ngày 2 giờ — cùng địa điểm | `f0…04` |
| `ea…02` (hiện có) | A9 Trần Bảo Ngọc, `INTERVIEW_INVITED` | Có | `f0…02`: hôm nay + 5 ngày 3 giờ — "Phỏng vấn trực tuyến qua Google Meet (đường dẫn gửi trước 1 ngày)" (giá trị cũ); `f0…05`: hôm nay + 6 ngày 2 giờ — "Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng"; `f0…06`: hôm nay + 6 ngày 7 giờ — "https://meet.example.com/abc-defg-hij" | **Chưa chốt** |
| `ea…03` (hiện có) | A4 Quốc Huy, `HIRED` | Có | `f0…03`: `2026-07-25 02:00:00+00` — "Tầng 3, 45 Bạch Đằng…" (giá trị cũ) | `f0…03` |

- A2 giữ khung chốt ở giấy mời hiệu lực để khối kiểm HTTP của FR-C07 (`C07/REQUIREMENT.md:472`, `:518`,
  `INTERVIEW_REMINDER` trên A2) vẫn chạy được.
- `ea…04` chép nguyên tiêu đề, nội dung của `ea…01`, `sent_by` = HR test. Thêm một thông báo `ec…17` loại
  `INTERVIEW_INVITATION_RESENT` cho Quốc Huy về A2: `body` một dòng đúng R-N2 (một khung), `link`
  `/candidate/applications/e8…02`, `entity_type = 'APPLICATION'`, `entity_id = e8…02` (đơn A2), `email_body` = khối
  R-N3 tương ứng với `f0…04` (dựng sẵn trong seed; giờ tính tương đối lúc nạp nên chuỗi giờ trong `email_body` dựng bằng
  `to_char((… ) AT TIME ZONE 'Asia/Ho_Chi_Minh', 'HH24:MI DD/MM/YYYY')`, URL dùng `http://localhost:5173`),
  `email_status = 'SKIPPED'`, chưa đọc.
- Seed không có khung `location` `NULL`: dữ liệu cũ không địa điểm chỉ kiểm bằng test (T14, T20); seed không ghi dữ liệu
  mà API mới không tạo được.
- Không thêm dòng `application_status_history` (gửi lại không ghi lịch sử).
- Câu đếm cuối file: `interview_invitations` 3 → 4, thêm `interview_invitation_slots` 6, `notifications` 16 → 17.
- `reset-test-data.sql`: giữ `DELETE FROM interview_invitations …` (`:95`), thêm comment: khung xoá theo `ON DELETE
  CASCADE` vì không xoá khung trước được (mục 4.1). Câu xoá thông báo (`:82-84`) **không cần** điều kiện mới: `ec…17`
  bị xoá vì `user_id` là Quốc Huy (người dùng test) và vì `entity_type = 'APPLICATION'` + `entity_id` là đơn test; thông
  báo loại mới gửi cho ứng viên demo về đơn vào tin test cũng bị xoá theo vế thứ hai (cùng `entity_type` như mọi loại cũ).
- `reset-demo-db.sql`: thêm `interview_invitation_slots` vào danh sách `TRUNCATE` (`:94`) cho đúng quy ước liệt kê tường
  minh của script (`:80-84`). Câu lệnh là `TRUNCATE … CASCADE` (`:85-103`) nên Postgres vẫn xoá theo bảng khung nếu thiếu
  tên — không phải lỗi (L1). File `seed-demo-*.sql` **không** sửa (không có giấy mời, a21); kiểm bằng
  chạy lại tuần tự `reset-demo-db.sql` → hai tầng seed demo → câu đếm ở cuối `seed-demo-structural.sql` không đổi.

**Ảnh hưởng của việc đổi giấy mời A9 (B3)** — soát bằng `rg -n "A9" docs db/seed` và `rg -n
"e8000000-0000-0000-0000-000000000009" docs db/seed` (13/10/2026). Không chỗ nào ngoài `seed-test.sql` dựa vào **giấy
mời** của A9:

| Chỗ nhắc A9 | Dựa vào | Bị ảnh hưởng? |
|---|---|---|
| `db/seed/seed-test.sql:353` (comment "A9 … INTERVIEW_INVITED (co giay moi)") | Giấy mời | **Có** — sửa thành "(co giay moi 3 khung, chua chot)" ở đợt 2 |
| `db/seed/seed-test.sql:434-437` (comment mục 11), `:450-458` (dòng `ea…02`) | Giấy mời | **Có** — viết lại theo bảng trên |
| `db/seed/seed-test.sql:423-425` (lịch sử), `:539`, `:569` (thông báo), `:580-582`, `:607-608` (tin nhắn), `:620`, `:653-660` (bản chụp sàng lọc) | Lịch sử, thông báo, tin nhắn, sàng lọc | Không |
| `docs/features/CHUNG/C06/REQUIREMENT.md:89`, `:324-327`, `:636`, `:779`; `C06/UI.md:185`, `:216`; `db/seed/README.md:336` | Tin nhắn, hộp thư | Không |
| `docs/features/HR/H10/REQUIREMENT.md:430`, `:436`, `:588`, `:673`; `docs/walkthrough/fr-h10-screening.md:27`, `:181`, `:185` | Bản chụp sàng lọc | Không |
| `docs/features/UV/U09/REQUIREMENT.md:571-598`, `:636`; `docs/walkthrough/fr-u09-screening-answer.md:207` | Sàng lọc, đồng ý lưu hồ sơ | Không |
| `docs/features/UV/U08/REQUIREMENT.md:448`, `:502`; `docs/walkthrough/fr-u08-application-detail.md:201-208` | Quyền truy cập (đơn của người khác → 403/404) | Không — mã lỗi không đổi |
| `db/seed/README.md:317`, `:342` (Trần Bảo Ngọc chưa có hồ sơ → bị đưa sang onboarding) | Tài khoản | Không, nhưng soát tay 7.4 phía ứng viên A9 phải bỏ qua màn onboarding trước |

`db/seed/README.md:337` (bảng "nên soát gì với tài khoản nào") thêm một dòng cho giấy mời nhiều khung/gửi lại ở đợt 2.

## 5. AI

Không dùng AI. Không gọi `ChatModel`/`EmbeddingModel`, không thuộc danh sách K3. Phần chạm FR-C07 (R-K1x, R-K2x) chỉ đổi
**dữ liệu** đưa vào K1 và điều kiện bật tình huống; không đổi prompt, không đổi phiên bản prompt.

## 6. Ngoài phạm vi

- Ứng viên chọn khung, thông báo "ứng viên đã chốt lịch" cho HR (FR-U10, bảng R-B).
- **Giới hạn đã biết — email mời lần đầu** không kèm giờ/khung giờ: email lần đầu vẫn chỉ là câu đổi trạng thái (R-F2,
  M6); ứng viên xem khung giờ ở trang chi tiết đơn.
- **Giới hạn đã biết — múi giờ** (B2): HR nhập thời điểm theo múi giờ của trình duyệt (`buildScheduledAtIso`,
  `InterviewInvitationDialog.tsx:37-44`), còn mọi nơi hiện khung (HR, ứng viên, email) theo giờ Việt Nam. HR ở múi giờ
  khác sẽ thấy giờ hiển thị lệch giờ đã nhập. Không xử lý ở FR-H12 (lệch này đã có từ FR-U08).
- **Địa điểm không thành liên kết bấm được** (B4): hiện như chữ thường ở mọi nơi. Muốn bấm được phải kiểm scheme URL
  (chỉ `https:`/`http:`, chặn `javascript:` …) trước khi tạo thẻ `<a>` — ngoài phạm vi.
- Danh sách khung giờ trong thông báo web (Q5: web một dòng; danh sách chỉ ở email và trang chi tiết đơn).
- Thời lượng buổi phỏng vấn; kiểm chồng lấn khoảng thời gian; trùng lịch giữa các giấy mời, các ứng viên hay lịch của
  HR; giữ chỗ khung giờ.
- Đồng bộ Google Calendar/Outlook, tệp `.ics`, tự tạo link phòng họp, nhắc lịch tự động.
- Sửa hay thu hồi giấy mời đã gửi; gửi lại khi đơn không `INTERVIEW_INVITED`; HR chốt hộ ứng viên một khung trong giấy
  mời nhiều khung (cách thay thế: gửi lại với một khung).
- Placeholder ngày giờ trong mẫu giấy mời; tự điền địa điểm từ `interview_templates.address`.
- Ứng viên xem giấy mời cũ.

## 7. Xong khi

### 7.1 Test backend (Testcontainers, full `./mvnw test` xanh)

| # | Test | Loại |
|---|---|---|
| T1 | I2 một khung → 201; DB: một giấy mời `superseded_at` `NULL`, một khung, `confirmed_slot_id` = khung đó, `confirmed_at` = `sent_at`; đơn `INTERVIEW_INVITED`; đúng 1 dòng lịch sử mới; đúng 1 thông báo `APPLICATION_STATUS_CHANGED`, không thông báo `INTERVIEW_INVITATION_RESENT`; `renderedContent` bằng đúng `content` gửi lên | dương |
| T2 | I2 với 2 và 5 khung → 201, chưa chốt, `display_order` 1…n đúng thứ tự gửi (gửi khung muộn trước khung sớm → giữ nguyên thứ tự); 6 khung → 400 "Giấy mời có tối đa 5 khung giờ."; `slots` `null`, thiếu khoá, `[]` → 400 câu dòng 1 R-S8. Mọi ca 400: không dòng giấy mời/khung nào, đơn vẫn `PENDING` | biên + âm |
| T3 | Khung (R-S3–R-S8) qua I2: `now − 1 phút` → 400 "Khung giờ 1: …tương lai."; `Instant.now()` → 400; `now + 2 phút` → 201; `10:00:45` lưu thành `10:00:00`; hai khung `10:00:10`, `10:00:50` cùng ngày → 400 "Khung giờ 2 trùng thời điểm với khung giờ 1."; `10:00` và `10:01` → 201; lỗi ở khung 3 báo đúng "Khung giờ 3"; phần tử `null` / `scheduledAt` `null` → 400, không 500 — test có sẵn `send_missingScheduledAt_returns400` (`InterviewInvitationControllerIntegrationTest.java:356-364`) giữ tên, đổi helper gửi `{"slots":[{"location":"…"}]}` và thêm khẳng định mã `INVALID_INTERVIEW_SCHEDULE` (trước đây là lỗi Bean Validation, L4); `location` `null`, thiếu khoá, `""`, `"  "` → 400 "Khung giờ {n}: vui lòng nhập địa điểm hoặc link phỏng vấn."; `" a "` → lưu `"a"`; 500 ký tự → 201, 501 → 400, `" " + 500 ký tự + " "` → 201; có `\n` (hoặc `\r`) ở giữa → 400; `"Tầng 3\n"` → 201, lưu `"Tầng 3"` (L9) | biên + âm |
| T4 | I2 lần hai trên đơn đã `INTERVIEW_INVITED` → 400, vẫn một giấy mời (test có sẵn `send_applicationAlreadyInterviewInvited_returns400` giữ nguyên khẳng định) | âm |
| T5 | I3 trên đơn có giấy mời một khung đã chốt, gửi 2 khung → 201: giấy mời cũ có `superseded_at` = `sent_at` của giấy mời mới, mọi cột khác và khung của nó y hệt trước (so từng cột); giấy mời mới hiệu lực, chưa chốt; đọc toàn bộ hàng `job_applications` bằng `JdbcTemplate` trước/sau → mọi cột giống hệt trừ `updated_at` (R-G4); số dòng `application_status_history` không đổi; không thông báo `APPLICATION_STATUS_CHANGED` mới; đúng 1 thông báo `INTERVIEW_INVITATION_RESENT` cho ứng viên, `email_status` `PENDING`, `entity_type` `APPLICATION`, `entity_id` = id đơn, `body` một dòng (không `\n`, không chứa giờ khung), `email_body` đúng khối R-N3 với hai khung vừa gửi (giờ Việt Nam, địa điểm, dòng "Xem giấy mời tại: …"); JSON của `GET` danh sách thông báo không có khoá `emailBody` | dương |
| T6 | I3 một khung → giấy mời mới đã chốt (R-K1) | dương |
| T7 | I3 khi đơn `PENDING`, `HIRED`, `REJECTED`, `WITHDRAWN` → 409 `INVITATION_RESEND_NOT_ALLOWED`; không ghi dòng nào, không thông báo, giấy mời cũ (nếu có) vẫn hiệu lực | âm |
| T8 | I3 đầu vào sai (0 khung, khung quá khứ, trùng, thiếu địa điểm) → 400 như T3; giấy mời cũ vẫn hiệu lực, không thông báo | âm |
| T9 | I3 khi giấy mời hiệu lực nhiều khung đã được chốt (đặt bằng SQL, giả lập FR-U10) → 201; giấy mời cũ giữ `confirmed_slot_id`, `confirmed_at` | dương |
| T10 | Tuần tự hoá (R-G4), ở lớp mới `InterviewInvitationResendConcurrencyIntegrationTest` **không** `@Transactional` toàn lớp (CLAUDE.md §3c; các lớp HTTP có sẵn đều `@Transactional` cấp lớp — L8), dữ liệu ngẫu nhiên riêng, như FR-H10 T5: (a) hai I3 song song cùng đơn → cả hai 201; cuối cùng 3 giấy mời, đúng một hiệu lực, hai giấy mời cũ có `superseded_at` khác `NULL`, 2 thông báo `INTERVIEW_INVITATION_RESENT`; (b) I3 song song với `PATCH /status` `HIRED`, lặp 10 lần trên 10 đơn → mỗi lần là một trong hai kết quả: {I3 201, PATCH 200} hoặc {PATCH 200, I3 409 `INVITATION_RESEND_NOT_ALLOWED`}; không 500; mọi đơn cuối cùng `HIRED`, có đúng một giấy mời hiệu lực; (c) như (b) với rút đơn phía ứng viên → {I3 201, rút 200} hoặc {rút 200, I3 409}; (d) **đơn luồng, chắc chắn** (bổ sung cho (b), vì (b) không phân biệt được cách "đọc trạng thái rồi ghi, không khoá"): transaction A (`TransactionTemplate`) gọi `JobApplicationRepository.lockIfInterviewInvited` trên đơn `INTERVIEW_INVITED` → 1 dòng, **giữ chưa commit**; luồng B gọi `updateStatusIfCurrent(id, INTERVIEW_INVITED, HIRED)` trong transaction riêng → B **chưa xong** sau 500 ms (`Future.get(500, MILLISECONDS)` ném `TimeoutException`); A commit → B xong trong 5 s, trả 1, đơn `HIRED`. Ca ngược: B (`HIRED`) commit trước → A `lockIfInterviewInvited` trả 0 | âm |
| T11 | Quyền I1, I3: HR công ty khác → 403; đơn không tồn tại → 404 `APPLICATION_NOT_FOUND`; HR chưa có công ty → 404 `COMPANY_NOT_FOUND` kể cả khi đơn không tồn tại; token ứng viên → 403; không token → 401. Thêm I1, I3 vào `HrApplicationCrossEndpointAccessIntegrationTest` | âm |
| T12 | I1: đơn chưa có giấy mời → 200, danh sách rỗng, `maxSlots` = 5; sau I2 rồi hai lần I3 → 3 phần tử: hiệu lực trước, rồi hai giấy mời cũ theo `superseded_at` giảm dần; khung đúng `display_order`; JSON không có khoá `verdict`/`label`/`isQualified`/`passed`/`recommendation` | dương |
| T13 | I4: sau I3 trả giấy mời **mới**; thêm ca đặt `created_at` của giấy mời **cũ** muộn hơn giấy mời mới bằng native update → I4 vẫn trả giấy mời mới (chứng minh không dựa vào `created_at`); khung theo thứ tự, mỗi khung có `id` đúng id trong DB, `confirmed` đúng khung, `confirmedAt`; đọc JSON bằng `ObjectMapper` (không so chuỗi — L5): gốc không có `id`, `applicationId`, `sentBy`, `supersededAt`, `scheduledAt`, mỗi phần tử `slots` có `id`. Test có sẵn `get_ownApplication_returnsInvitationWithFiveFieldsOnly` (`InterviewInvitationCandidateControllerIntegrationTest.java:233-249`, đang khẳng định chuỗi `doesNotContain("\"id\"")`) đổi tên thành `get_ownApplication_returnsActiveInvitationWithSlots`; ứng viên khác → 403 (giữ); đơn chưa có giấy mời → 404 (giữ); đơn `HIRED`/`REJECTED` sau phỏng vấn vẫn đọc được (giữ) | dương + âm |
| T14 | Migration V16 bằng Flyway API (mẫu `JobCatalogMigrationTest`): dựng DB tới V15, chèn giấy mời dạng cũ (có và không có `location`; một đơn có hai giấy mời khác `created_at`; một giấy mời `sent_at` `NULL`), migrate V16 → mỗi giấy mời đúng một khung bằng giá trị cũ (khung không địa điểm giữ `location` `NULL`), đã chốt, `confirmed_at` = `sent_at` (hoặc `created_at`); đơn hai giấy mời: mới nhất hiệu lực, cũ có `superseded_at`; cột `scheduled_at`, `location` không còn | dương |
| T15 | Ràng buộc DB (insert thẳng): hai khung cùng `scheduled_at` một giấy mời → `DataIntegrityViolationException`; cùng `scheduled_at` ở hai giấy mời khác nhau → được; `display_order` 0 và 6 → lỗi, 1 và 5 → được; giấy mời hiệu lực thứ hai cho cùng đơn → lỗi, thêm giấy mời có `superseded_at` → được; `confirmed_slot_id` trỏ khung của giấy mời khác → lỗi; chỉ một trong hai cột `confirmed_*` khác `NULL` → lỗi (cả hai chiều) | biên + âm |
| T16 | Xoá đơn (`DELETE FROM job_applications`, giả lập seed reset) có giấy mời đã chốt → xoá được, khung theo giấy mời bị xoá | dương |
| T17 | Bất biến: không route `PUT`/`PATCH`/`DELETE` dưới `…/interview-invitation(s)` (gọi thử → 405 hoặc 404, không 2xx); sửa mẫu sau khi gửi không đổi giấy mời (test có sẵn, đổi thân request) | âm |
| T18 | K1 (R-K1x): giấy mời hiệu lực đã chốt → `interview` = khung chốt; hiệu lực chưa chốt (3 khung) → `null`, kể cả khi giấy mời cũ đã chốt; không giấy mời → `null`. `ConversationContextSignatureTest` không sửa, xanh | dương + âm |
| T19 | Soạn nháp (R-K2x): `INTERVIEW_INVITED` + đã chốt → `INTERVIEW_REMINDER`, `REQUEST_RESCHEDULE` dùng được; + chưa chốt → `INTERVIEW_REMINDER` 409 `DRAFT_SCENARIO_UNAVAILABLE` lý do `NO_ACTIVE_INTERVIEW`, `REQUEST_RESCHEDULE` 200 và prompt chứa "chưa có lịch"; `THANK_AFTER_INTERVIEW` dùng được khi có giấy mời hiệu lực; mọi ca 409 không gọi AI; `DraftUnavailableReason` vẫn đúng ba giá trị | dương + âm |
| T20 | `NotificationContentBuilder.forInvitationResent` (test đơn vị): một khung và ba khung → `body` đúng câu R-N2, không `\n`; `emailBody` ba khung → đúng khối R-N3, khung `location` `null` ghi "Chưa có dữ liệu", khung `2026-10-20T18:30:00Z` hiện "01:30 21/10/2026", dòng "Xem giấy mời tại: {base}/candidate/applications/{id}" đúng URL; các hàm cũ (`forStatusChanged`, `forApplicationSubmitted`, `forApplicationWithdrawn`, `forAggregationFinished`, `forNewMessage`) trả `emailBody` `null` | dương |
| T21 | `NotificationMailOrchestrator` (R-N3a), `JavaMailSender` mock bắt `SimpleMailMessage`: (a) thông báo có `email_body` khác `null` → nội dung email = `email_body` nguyên văn, tiêu đề = `title`; (b) `NEW_MESSAGE`, `email_body` `null` → nội dung = `body + "\n\nXem và trả lời tại: " + url` (không đổi); (c) `APPLICATION_STATUS_CHANGED`, `email_body` `null` → nội dung = `body` nguyên văn (không đổi). (b), (c) là hai test **có sẵn** ở `NotificationMailOrchestratorIntegrationTest.java:200-230` (`processOne_newMessage_appendsReplyLinkWithFrontendBaseUrl`, `processOne_oldTypeWithLink_sendsBodyExactly_withoutReplyLink`) — giữ nguyên, phải xanh; chỉ thêm (a) vào lớp đó (L6) | dương |
| T22 | Đánh dấu đã đọc tin nhắn (FR-C06 R-R3) không chạm thông báo `INTERVIEW_INVITATION_RESENT` cùng đơn (lọc theo `type`) | âm |

Kiểm tĩnh (đợt cuối, kỳ vọng như ghi):

```bash
rg -n "findByApplicationIdOrderByCreatedAtDesc" backend/src/main -g "!**/scoring/**"   # 1 dong: khai bao trong repository (tu dot 4; dot 3: 3 dong - them ConversationContextAssembler, MessageDraftFacade - L11, L12)
rg -n "changeStatus" backend/src/main/java/com/recruitment/interviewinvitation   # dung 1 dong (I2)
rg -n "FOR SHARE|FOR UPDATE|@Lock" backend/src/main/java/com/recruitment/interviewinvitation backend/src/main/java/com/recruitment/jobapplication/JobApplicationRepository.java  # 0 dong (L10)
rg -n "interviewinvitation" backend/src/main/java/com/recruitment/notification   # 0 dong (R-N1: notification/ khong import interviewinvitation/)
rg -n "scheduledAt|getLocation" backend/src/main/java/com/recruitment/interviewinvitation/InterviewInvitation.java  # 0 dong
rg -n -i "emailBody|email_body" backend/src/main/java/com/recruitment/notification/dto  # 0 dong (khong tra ra API)
rg -n -i "verdict|isQualified|passed|recommendation" backend/src/main/java/com/recruitment/interviewinvitation      # 0 dong
```

### 7.2 Kiểm bằng HTTP — Windows PowerShell 5.1

Nạp seed demo + seed test (`db/seed/README.md` mục 8.1) **sau** khi có V16 và seed mục 4.5. Backend ở `localhost:8080`.
Dán cả khối vào **một** cửa sổ PowerShell. Bộ hàm `Get-Token` / `Invoke-Api` / `Test-Call` ở đầu khối chép **nguyên văn**
từ FR-H11 mục 7.2 (`docs/features/HR/H11/REQUIREMENT.md`): gửi body dạng byte UTF-8, đọc response theo UTF-8 — không
dùng hàm của FR-H10 (PowerShell 5.1 làm vỡ chữ tiếng Việt trong địa điểm/nội dung).

```powershell
$base = 'http://localhost:8080'

function Get-Token([string]$Email, [string]$Password) {
    $body = @{ email = $Email; password = $Password } | ConvertTo-Json
    (Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' -Body $body).accessToken
}

# Gui body dang byte UTF-8 (PowerShell 5.1 ma hoa chuoi Body theo ISO-8859-1 -> vo chu tieng Viet).
# $Json: $null (khong body) | chuoi (gui nguyen van, dung cho JSON hong) | hashtable (ConvertTo-Json -Depth 8).
# Tra ve doi tuong @{ Status; Text } - Text doc theo UTF-8 (C07 L13).
function Invoke-Api([string]$Method, [string]$Path, [string]$Token, $Json) {
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $params = @{ UseBasicParsing = $true; Method = $Method; Uri = "$base$Path"; Headers = $headers }
    if ($null -ne $Json) {
        $payload = if ($Json -is [string]) { $Json } else { ConvertTo-Json -InputObject $Json -Depth 8 }
        $params['ContentType'] = 'application/json; charset=utf-8'
        $params['Body'] = [Text.Encoding]::UTF8.GetBytes($payload)
    }
    try {
        $r = Invoke-WebRequest @params
        return @{ Status = [int]$r.StatusCode; Text = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray()) }
    } catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { return @{ Status = 0; Text = 'KHONG KET NOI DUOC' } }
        $text = $_.ErrorDetails.Message
        if (-not $text) {
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8)
            $text = $reader.ReadToEnd()
        }
        return @{ Status = [int]$resp.StatusCode; Text = $text }
    }
}

function Test-Call([string]$Method, [string]$Path, [string]$Token, $Json) {
    $r = Invoke-Api $Method $Path $Token $Json
    '{0} {1} {2} {3}' -f $r.Status, $Method, $Path, $r.Text
}

$tokenTest = Get-Token 'minhdathr@gmail.com' '12345678'
$tokenUv   = Get-Token 'quochuyuv@gmail.com' '12345678'

$a1   = '/api/hr/applications/e8000000-0000-0000-0000-000000000001'  # PENDING
$a2   = '/api/hr/applications/e8000000-0000-0000-0000-000000000002'  # INTERVIEW_INVITED, da gui lai 1 lan
$demo = '/api/hr/applications/3ec35ae2-c8ae-4100-a819-3b59f6948707'  # don cong ty demo
$t1 = (Get-Date).ToUniversalTime().AddDays(10).ToString("yyyy-MM-ddT02:00:00Z")
$t2 = (Get-Date).ToUniversalTime().AddDays(11).ToString("yyyy-MM-ddT07:30:00Z")
$past = (Get-Date).ToUniversalTime().AddDays(-1).ToString("yyyy-MM-ddT02:00:00Z")
$body2 = @{
    slots   = @(@{ scheduledAt = $t1; location = 'Phòng họp Hồ Gươm, tầng 5' },
                @{ scheduledAt = $t2; location = 'https://meet.example.com/abc-defg-hij' })
    subject = 'Thư mời phỏng vấn'
    content = 'Kính gửi bạn, mời bạn chọn một khung giờ.'
}
$bodyPast  = @{ slots = @(@{ scheduledAt = $past; location = 'Tầng 3' }); subject = 'Thư mời'; content = 'Nội dung' }
$bodyDup   = @{ slots = @(@{ scheduledAt = $t1; location = 'Tầng 3' }, @{ scheduledAt = $t1; location = 'Tầng 3' });
               subject = 'Thư mời'; content = 'Nội dung' }
$bodyNoLoc = @{ slots = @(@{ scheduledAt = $t1; location = '   ' }); subject = 'Thư mời'; content = 'Nội dung' }

'--- 1. Duong: HR doc giay moi A2 (mong doi 200, 2 giay moi: hieu luc + 1 cu)'
Test-Call 'GET' "$a2/interview-invitations" $tokenTest $null
'--- 2. Duong: gui lai A2 voi 2 khung (mong doi 201), roi doc lai I1 (mong doi 200, dia diem dung dau -> True)'
Test-Call 'POST' "$a2/interview-invitation/resend" $tokenTest $body2
$r = Invoke-Api 'GET' "$a2/interview-invitations" $tokenTest $null
'{0} doc lai I1 A2, co "Phòng họp Hồ Gươm, tầng 5": {1}' -f $r.Status, $r.Text.Contains('Phòng họp Hồ Gươm, tầng 5')
'--- 3. Am: gui lai don PENDING A1 (mong doi 409 INVITATION_RESEND_NOT_ALLOWED)'
Test-Call 'POST' "$a1/interview-invitation/resend" $tokenTest $body2
'--- 4. Am: khung qua khu, trung thoi diem, thieu dia diem (mong doi 400 INVALID_INTERVIEW_SCHEDULE x3)'
Test-Call 'POST' "$a1/interview-invitation" $tokenTest $bodyPast
Test-Call 'POST' "$a1/interview-invitation" $tokenTest $bodyDup
Test-Call 'POST' "$a1/interview-invitation" $tokenTest $bodyNoLoc
'--- 5. Am: don cong ty demo (mong doi 403 x2); ung vien goi endpoint HR (403); khong token (401)'
Test-Call 'GET'  "$demo/interview-invitations" $tokenTest $null
Test-Call 'POST' "$demo/interview-invitation/resend" $tokenTest $body2
Test-Call 'GET'  "$a2/interview-invitations" $tokenUv $null
Test-Call 'GET'  "$a2/interview-invitations" $null $null
'--- 6. Duong: ung vien doc giay moi A2 (mong doi 200, 2 khung co id, chua chot)'
Test-Call 'GET' '/api/candidates/applications/e8000000-0000-0000-0000-000000000002/interview-invitation' $tokenUv $null
'--- 7. Duong: gui lan dau A1 voi 2 khung (mong doi 201)'
Test-Call 'POST' "$a1/interview-invitation" $tokenTest $body2
```

Mong đợi: nhóm 1 `200`; nhóm 2 dòng đầu `201`, dòng thứ hai `200 doc lai I1 A2, co "Phòng họp Hồ Gươm, tầng 5": True`
(địa điểm đi qua request và response giữ đúng dấu tiếng Việt — chuỗi này không có trong seed, L13; `False` = vỡ mã hoá,
dừng và báo); 3 `409 …
INVITATION_RESEND_NOT_ALLOWED`; 4 ba dòng `400 … INVALID_INTERVIEW_SCHEDULE` (câu "Khung giờ 1: …tương lai.", "Khung giờ
2 trùng thời điểm với khung giờ 1.", "Khung giờ 1: vui lòng nhập địa điểm hoặc link phỏng vấn."); 5 `403`, `403`, `403`,
`401 … UNAUTHENTICATED`; 6 `200`; 7 `201`. Chạy xong: `reset-test-data.sql` rồi nạp lại seed test. **Bắt buộc ở đợt
cuối:** chạy thật, dán nguyên văn output; lệnh sai cú pháp thì dừng, đề xuất sửa mục này, chờ duyệt lại.

### 7.3 Frontend

- `npm run build` + `npm run lint` sạch.
- `rg -n "dangerouslySetInnerHTML" frontend/src` → 0 dòng.
- `rg -n "scheduledAt" frontend/src/features/interviewinvitation/types.ts` chỉ còn trong kiểu khung giờ (không ở gốc
  `CandidateInterviewInvitation`).
- Không hardcode giới hạn khung — chỉ quét **đúng các file FR-H12 viết hoặc sửa có logic khung** (UI.md mục 5a, 5b):

  ```bash
  rg -n "[<>]=? ?5\b|MAX_SLOTS" \
    frontend/src/features/interviewinvitation/InterviewSlotFields.tsx \
    frontend/src/features/interviewinvitation/InterviewInvitationDialog.tsx \
    frontend/src/features/interviewinvitation/InterviewSlotList.tsx \
    frontend/src/features/interviewinvitation/invitationLabels.ts \
    frontend/src/features/applicationDetail/InterviewInvitationTab.tsx
  ```

  Kỳ vọng 0 dòng. Nếu vẫn khớp nhầm (chuỗi không liên quan tới giới hạn khung), báo cáo đợt phải liệt kê **từng dòng
  khớp** kèm lý do bỏ qua; không tự sửa mẫu.
- Địa điểm không thành liên kết (B4), cùng quy tắc báo cáo khớp nhầm như dòng trên:

  ```bash
  rg -n "<a |href=" \
    frontend/src/features/interviewinvitation/InterviewSlotList.tsx \
    frontend/src/features/interviewinvitation/InterviewInvitationDetails.tsx \
    frontend/src/features/interviewinvitation/PreviousInvitationCard.tsx \
    frontend/src/features/applicationDetail/InterviewInvitationTab.tsx
  ```

- `git diff --stat main -- frontend/src/features/messageDraft frontend/src/features/applicationDetail/ApplicationActionBar.tsx`
  → 0 file (Q3; thanh thao tác không đổi, Q7).
- Tên file theo `UI.md` mục 5.

### 7.4 Soát tay (desktop ≥ `lg`, 768px, 375px) — seed test

| Trạng thái cần thấy | Tài khoản | Nơi | Mong đợi |
|---|---|---|---|
| Đơn chưa có giấy mời | `minhdathr@gmail.com` / `12345678` | `/hr/applications/e8000000-0000-0000-0000-000000000001?tab=invitation` (A1, `PENDING`) | Câu trạng thái rỗng của tab (UI.md 4c-3), không nút "Gửi lại" |
| Mời lần đầu nhiều khung | `minhdathr@gmail.com` | A1 → "Mời phỏng vấn" ở thanh thao tác | Thêm tới 5 khung thì nút "Thêm khung giờ" khoá kèm câu lý do; xoá khung; bỏ trống địa điểm → lỗi dưới đúng khung; gửi 2 khung → tab "Giấy mời phỏng vấn" hiện 2 khung + "Ứng viên chưa chọn khung giờ." |
| Mời lần đầu một khung | như trên (đơn PENDING khác, vd A6 `…06`) | — | Một khung có dấu "Đã chốt" |
| Giấy mời hiệu lực + giấy mời cũ | `minhdathr@gmail.com` | A2 `…02?tab=invitation` | Khung chốt của `ea…04`; "Giấy mời trước đó (1)" mở ra thấy `ea…01` nguyên văn, có dòng "Đã được thay bằng giấy mời gửi lúc 09:00 06/10/2026" |
| Giấy mời nhiều khung chưa chốt | `minhdathr@gmail.com` | A9 `…09?tab=invitation` | 3 khung theo thứ tự seed, khung 3 là link hiện như chữ thường (không bấm được); "Ứng viên chưa chọn khung giờ." |
| Soạn nháp C07 (soát **trước** dòng "Gửi lại") | `minhdathr@gmail.com` | A9 tab "Trao đổi" → soạn bằng AI | "Nhắc lịch phỏng vấn" khoá, câu lý do hiện có của `NO_ACTIVE_INTERVIEW`; A2: dùng được |
| Gửi lại | `minhdathr@gmail.com` | A9 → "Gửi lại giấy mời" | Tiêu đề, nội dung điền sẵn từ `ea…02`, một khung trống; câu cảnh báo thay giấy mời; gửi xong tab đổi sang giấy mời mới, badge trạng thái không đổi, tab "Lịch sử" không thêm mốc, thanh thao tác không đổi |
| Đơn đã có kết quả | `minhdathr@gmail.com` | A4 `…04?tab=invitation` (`HIRED`) | Tab chỉ đọc, không nút "Gửi lại giấy mời" |
| Ứng viên xem nhiều khung | `tran.bao.ngoc@test.local` / `12345678` (bỏ qua màn onboarding — `db/seed/README.md:317`) | `/candidate/applications/e8000000-0000-0000-0000-000000000009` (A9, trước khi HR gửi lại) | Ba khung chỉ đọc, không nút chọn; câu "Hãy nhắn cho nhà tuyển dụng qua tab Trao đổi để xác nhận khung giờ phù hợp." |
| Ứng viên xem khung đã chốt | `quochuyuv@gmail.com` | `/candidate/applications/e8000000-0000-0000-0000-000000000004` (A4) | Một khung có dấu "Đã chốt" (icon + chữ), không câu hướng dẫn |
| Thông báo gửi lại (web) | `quochuyuv@gmail.com` | Chuông → thông báo `ec…17` | Tiêu đề "Nhà tuyển dụng đã gửi lại giấy mời phỏng vấn", nội dung một dòng không có giờ; bấm mở `/candidate/applications/…02` |
| Email gửi lại | MailHog `localhost:8025` | Sau khi HR gửi lại A9 | Email tới ứng viên A9: câu một dòng, khối "Khung giờ (giờ Việt Nam):" đủ khung, dòng "Xem giấy mời tại: …" |
| 375px | cả hai | Các màn trên | Không cuộn ngang toàn trang; khung giờ trong hộp thoại xếp dọc; `TabsList` cuộn ngang được (6 tab) |

## 8. AI hay làm sai (dành cho người code)

- Chèn danh sách khung giờ, thời điểm hay địa điểm vào `rendered_content`/`subject`, hoặc thêm placeholder ngày giờ vào
  mẫu "cho email đẹp" (R-C1, M3).
- Gọi `applicationStatusService.changeStatus` (hay ghi `application_status_history`, phát `ApplicationStatusChangedEvent`)
  khi gửi lại; hoặc mở `ALLOWED_TRANSITIONS` cho `INTERVIEW_INVITED → INTERVIEW_INVITED` (R-G1, M5).
- Xác định giấy mời hiệu lực bằng `created_at`/`sent_at` (`findFirst` trên `OrderByCreatedAtDesc`) thay vì
  `superseded_at IS NULL` — sai khi hai dòng cùng `created_at` trong transaction test (CLAUDE.md §3c, R-A1, T13).
- Lấy khung **sớm nhất** làm lịch phỏng vấn trong K1 hay trong email khi chưa có khung chốt (R-K1x).
- Giữ song song cột `interview_invitations.scheduled_at`/`location` (đánh dấu deprecated, ghi đè bằng khung đầu…) —
  V16 bỏ hẳn hai cột (mục 4.1).
- Đổi hình dạng `InterviewSchedule` hay sửa `ConversationContextSignatureTest` cho "khớp" (R-K1x, M4).
- Thêm mã lý do mới vào `DraftUnavailableReason` hay sửa `draftLabels.ts` (Q3).
- Đặt khung chốt cho giấy mời nhiều khung ở FR-H12, hoặc thêm endpoint ứng viên chọn khung "cho trọn luồng" (R-K2, R-B1).
- Dùng `Instant.now()` hai lần (một cho `superseded_at`, một cho `sent_at`) hoặc `now()` của Postgres — hai mốc lệch nhau
  (R-G3 bước 4).
- Kiểm trạng thái bằng `findById` rồi mới ghi (không khoá hàng); hoặc dùng `SELECT … FOR SHARE`/`FOR UPDATE`/`@Lock`
  thay cho câu `UPDATE` có điều kiện (R-G3, R-G4, Q2).
- **Đọc giấy mời hiệu lực trước khi khoá hàng đơn** (vd gọi `findByApplicationIdAndSupersededAtIsNull` để "kiểm có giấy
  mời" hay để lấy id rồi mới `lockIfInterviewInvited`, rồi đánh dấu theo id đã đọc): hai lần gửi lại đồng thời, lượt sau
  đánh dấu bản cũ trong khi bản lượt trước vừa tạo vẫn hiệu lực → vi phạm `uq_invitation_active` → lỗi. Khoá (bước 3)
  luôn chạy trước bước 5–6; bước 5 là câu `UPDATE` theo `application_id`, không theo id đã đọc (R-G3, T10a).
- Câu khoá `SET` một cột khác (vd `updated_at = now()`) "cho tường minh", hoặc kiểm trạng thái **sau** khi đã đánh dấu
  `superseded_at` (R-G3 thứ tự bước).
- Tự xoá khung trước giấy mời trong script/test, hoặc thêm `DEFERRABLE` cho FK ghép "cho dễ seed" (mục 4.1, 4.5).
- Đặt `confirmedSlotId` lên giấy mời **trước khi** khung được INSERT (vd gán trước `saveAndFlush` giấy mời, hoặc gán rồi
  để Hibernate tự flush một lần): câu INSERT giấy mời bị hoãn tới flush và mang theo `confirmed_slot_id` → vi phạm FK
  ghép `fk_invitation_confirmed_slot`. Hoặc ánh xạ `confirmedSlotId` thành `@ManyToOne` (R-K1, L2).
- Đặt `@Generated` lên `created_at` của entity khung `@Immutable` → `UnsupportedLockAttemptException` (mục 4.2, L3).
- Đặt `@NotEmpty`/`@Size(max=5)` lên `slots` hay `@NotBlank` lên `location` — lỗi ra định dạng map trường thay vì câu
  theo khung (R-S8).
- So trùng khung bằng `Instant` gốc (khác giây vẫn "khác nhau"), hoặc cắt về phút sau khi kiểm; hoặc từ chối giây lẻ
  (làm đỏ test cũ của FR-H07, R-S3).
- Cho `location` rỗng đi qua ở API vì "cột cho phép NULL" (R-S6, Q9).
- Trả 404 cho đơn chưa có giấy mời ở I1 (R-A2), hoặc 404 thay vì 403 cho đơn công ty khác (R-A4).
- Đưa id giấy mời, `sentBy`, `supersededAt` hay giấy mời cũ vào I4; bỏ `id` của khung khỏi I4 (R-A3, mục 4.4).
- Viết lại câu thông báo đổi trạng thái hay thêm giờ vào email mời lần đầu (R-F2).
- Đưa danh sách khung vào `body` thông báo (hiện ở web); hoặc dựng email từ `body` mà không có khung (R-N2, R-N3).
- Dựng email lúc poller chạy bằng cách đọc lại bảng giấy mời/khung — làm `notification/` phụ thuộc `interviewinvitation/`
  và làm email đổi theo lần gửi lại sau; đúng là dựng `email_body` **một lần** lúc tạo thông báo từ dữ liệu trong sự kiện
  (R-N1, R-N3, kiểm tĩnh 7.1).
- Đặt `entity_type`/`entity_id` khác `APPLICATION`/`applicationId` cho loại thông báo mới — `reset-test-data.sql` không
  xoá được (R-N1, mục 4.5).
- Trả `email_body` ra API thông báo, hoặc để loại cũ ghi `email_body` khác `null` (đổi email cũ) (R-N3a, T21).
- Ghi thông báo gửi lại cho HR, hoặc ghi cả hai loại thông báo khi gửi lại (R-N1).
- Định dạng giờ trong email theo múi giờ server thay vì `Asia/Ho_Chi_Minh` (R-N3, T20).
- Tô xanh khung đã chốt, đỏ khung chưa chốt; dùng màu làm tín hiệu duy nhất (UI.md mục 10).
- Biến địa điểm có dạng URL thành thẻ `<a>` (B4).
- Đặt nút "Gửi lại giấy mời" vào thanh thao tác FR-H07 hay đổi các nút ở đó (Q7).
- Hardcode 5 ở frontend thay vì `maxSlots` (mục 4.4, 7.3).

## 9. Kế hoạch đợt code

Mỗi đợt: code xong → chạy kiểm ở cột Kiểm → dừng → báo cáo diff → chờ duyệt → một commit. Đợt 2–4:
`.\mvnw.cmd test-compile` rồi chỉ các lớp test của đợt bằng `-Dtest`; đợt 5–6: `npm run build` + `npm run lint`; đợt 7:
full `.\mvnw.cmd test` đúng một lần + build + lint.

| Đợt | Nội dung | Kiểm |
|---|---|---|
| 1 | Plan Mode: đối chiếu lại mục 0 trên HEAD lúc đó; chốt danh sách file | — |
| 2 | V16 (cả `notifications.email_body`); entity `InterviewInvitation` (bỏ 2 cột, thêm 3 cột), `InterviewInvitationSlot` + repository, `Notification.emailBody`; repository: `findByApplicationIdAndSupersededAtIsNull`, câu đánh dấu `superseded_at`, `JobApplicationRepository.lockIfInterviewInvited`. **Không đổi DTO/API (L7):** `InterviewInvitationService` giữ thân request cũ (`scheduledAt`/`location` ở gốc, kiểm như hiện nay) và ghi thành **1 khung đã chốt** theo ba bước R-K1; `InterviewInvitationResponse`, `CandidateInterviewInvitationResponse` giữ hình dạng, lấy giá trị từ khung chốt của giấy mời hiệu lực; `ConversationContextAssembler:82` đọc khung chốt của giấy mời hiệu lực. Hai helper test dùng setter entity (`ConversationContextAssemblerIntegrationTest:219-231`, `MessageDraftIntegrationTestSupport:411-421`) đổi sang chèn giấy mời + 1 khung chốt (giấy mời chèn sau đánh dấu giấy mời trước `superseded_at`) — chỉ helper, **không** đổi khẳng định. Seed test, `reset-test-data.sql`, `reset-demo-db.sql`, `db/seed/README.md`; T14, T15, T16 | test-compile; `-Dtest` lớp mới + `JobCatalogMigrationTest`. **Điều kiện xong (L7):** mọi lớp `InterviewInvitation*IntegrationTest` và các lớp đọc giấy mời có sẵn (`ApplicationHrDetailControllerIntegrationTest`, `ApplicationStatusControllerIntegrationTest`, `CandidateApplicationCrossEndpointAccessIntegrationTest`, `HrApplicationCrossEndpointAccessIntegrationTest`, `InterviewTemplateOwnerIntegrationTest`, `ConversationContext*Test`, `MessageDraft*Test`, `Notification*Test`) xanh **không sửa thân request**; chạy tay `reset-test-data.sql` + seed test, `reset-demo-db.sql` + hai tầng demo trên DB dev |
| 3 | Đổi thân request sang `slots` (L7) và hoàn thiện I1–I4: DTO mục 4.4, R-S3 (cắt phút), R-S8, địa điểm bắt buộc, mã lỗi, I1, I3 theo R-G3/R-G4; thông báo R-N (listener, builder, `mailText`); sửa thân request ở các test có sẵn (`InterviewInvitation*IntegrationTest`, `ApplicationHrDetailControllerIntegrationTest:261-268`, `ApplicationStatusControllerIntegrationTest:193-204`, `CandidateApplicationCrossEndpointAccessIntegrationTest:172-180`, `HrApplicationCrossEndpointAccessIntegrationTest`); T1–T13, T17, T20–T22 | test-compile; `-Dtest` các lớp vừa kể + `InterviewTemplateOwnerIntegrationTest`, các lớp test thông báo, `ApplicationStatus*Test` |
| 4 | K1/C07: R-K1x, R-K2x; sửa test C07 theo R-K3x; T18, T19 | test-compile; `-Dtest` `ConversationContext*Test`, `MessageDraft*Test` |
| 5 | Frontend HR: hộp thoại khung giờ (gửi lần đầu + gửi lại), tab "Giấy mời phỏng vấn" | build + lint |
| 6 | Frontend ứng viên: `InterviewInvitationDetails` danh sách khung; loại thông báo mới ở `features/notifications/types.ts` | build + lint; lệnh 7.3 |
| 7 | Đợt cuối: full `.\mvnw.cmd test`, khối 7.2 (dán output), kiểm tĩnh 7.1, sửa comment `ApplicationStatusController.java:33-36`, `srs-guard`, `walkthrough`, trạng thái `ĐÃ HOÀN THÀNH`, tài liệu mục 10 | full `mvnw test` (một lần); build + lint; 7.2; 7.3 |

## 10. Tài liệu dùng chung sẽ sửa (ở đợt cuối, không sửa trong commit đặc tả)

| # | File | Thay đổi |
|---|---|---|
| D1 | `docs/SRS.md` | Dòng 40: tên "Giấy mời nhiều khung giờ" → "Giấy mời phỏng vấn nhiều khung giờ" (B5, M7) và `Chưa đặc tả` → `Đã hoàn thành`; dòng 103 (bảng chức năng bổ sung) đổi tên cho thống nhất. FR-H07 (`:93`) và mục vòng đời (`:148`): "Đã mời phỏng vấn (có lịch hẹn)" → nghĩa "đã gửi giấy mời có 1–5 khung giờ"; nhãn hiển thị giữ "Đã mời phỏng vấn". Mục 4 dòng FR-C03 (`:172`): thêm "giấy mời được gửi lại" |
| D2 | `docs/ROADMAP.md:918` | Tick + tóm tắt; ghi giới hạn đã biết: email mời lần đầu không kèm giờ (M6); múi giờ nhập/hiển thị (B2); địa điểm không bấm được (B4). Ghi một dòng nợ (L1): "danh sách bảng của reset-demo-db.sql thiếu bảng V13–V15 (không gây lỗi nhờ CASCADE)" |
| D3 | `docs/UI_GUIDE.md` mục 7 | Dòng `/hr/applications/:id`: thêm tab "Giấy mời phỏng vấn" (khung giờ, khung đã chốt, gửi lại, giấy mời cũ) và `FR-H12`; bỏ "giấy mời nhiều khung giờ, gửi lại giấy mời (FR-H12)" khỏi ghi chú "FR sau tự thêm". Dòng `/candidate/applications/:id`: thêm `FR-H12 (khung giờ chỉ đọc)` |
| D4 | `ApplicationStatusController.java:33-36` (code, đợt 7) | Comment "(co lich hen)" → nghĩa mới, khớp D1 |
| D5 | `docs/features/CHUNG/C07/REQUIREMENT.md` (R-S1 dòng `:102`, `:107`; R-K1-5 `:213-217`; T7 `:399`) | Ghi chú "Sửa bởi FR-H12 (R-K1x, R-K2x)" cạnh từng dòng: `INTERVIEW_REMINDER` cần khung đã chốt; nguồn `InterviewSchedule` là khung chốt của giấy mời hiệu lực. `C07/UI.md` không đổi (Q3) |
| D6 | `docs/features/README.md` mục FR-H12 | M2, M3: bỏ câu "chỉ thêm chỗ chèn danh sách khung giờ khi render" và "thông báo cho HR qua FR-C03" (chuyển sang mục FR-U10); "Mở rộng FR-C03" → "thông báo ứng viên khi giấy mời được gửi lại"; "HR xem khung giờ ứng viên đã chọn ở hồ sơ đơn (H09)" → "ở tab Giấy mời phỏng vấn của hồ sơ đơn" |
| D7 | `CLAUDE.md` §3d | Dòng K1: thêm `H12` vào cột "Dùng bởi" (H12 đổi nguồn `InterviewSchedule` sang khung đã chốt của giấy mời hiệu lực, R-K1x). Không package mới (§3 không đổi) |
| D8 | `docs/features/UV/U08/REQUIREMENT.md:204` (R-D10), `:218` (R-T6); `docs/features/UV/U08/UI.md:261`, `:295` | Ghi chú "Sửa bởi FR-H12" cạnh từng dòng: DTO giấy mời đổi hình dạng (danh sách khung có `id`, `confirmedAt`); khối giấy mời hiện danh sách khung, khung chốt, địa điểm thiếu ghi "Địa điểm: Chưa có dữ liệu" thay vì bỏ dòng |

## 11. Quyết định — Đã chốt 13/10/2026

Mọi câu đã chốt. Phần thân viết theo cột "Quyết định"; cột "Phương án đã loại" giữ lại để tra lý do.

### 11.1 Tám quyết định nền

| # | Nội dung | Mục áp dụng |
|---|---|---|
| C1 | Ranh giới H12/U10: H12 gửi 1–5 khung, gửi lại, đọc phía HR + giao diện H09, sửa DTO + giao diện U08 chỉ đọc, V16 tạo sẵn cột khung chốt; U10 chọn khung + thông báo HR | Mục 3.9 |
| C2 | Schema V16: bảng khung (`scheduled_at` thời điểm bắt đầu, `location`, thứ tự), `UNIQUE(invitation_id, scheduled_at)`; cột khung chốt (FK) + `confirmed_at`, `superseded_at`, partial unique index một giấy mời hiệu lực; giấy mời hiệu lực = `superseded_at IS NULL`; chuyển dữ liệu cũ thành một khung đã chốt rồi bỏ cột cũ; sửa seed test; seed demo không ảnh hưởng | Mục 4.1, 4.5, R-A1 |
| C3 | 1–5 khung; tương lai; không trùng thời điểm bắt đầu tính tới phút; kiểm ở backend; một khung = chốt ngay; khung và nội dung đã gửi không sửa được | R-S, R-K1, R-C2 |
| C4 | `rendered_content` chỉ là thư; khung giờ lưu và hiển thị tách riêng; mẫu FR-H02 giữ nguyên | R-C1 |
| C5 | Gửi lại: chỉ khi `INTERVIEW_INVITED` (kể cả đã chốt); endpoint riêng, không `changeStatus`, không lịch sử; cùng transaction đánh dấu cũ + tạo mới; test `send_applicationAlreadyInterviewInvited_returns400` giữ nguyên; loại thông báo mới web + email kèm khung giờ; email lần đầu giữ nguyên (giới hạn đã biết) | R-G, R-N, mục 6 |
| C6 | Đơn chuyển `INTERVIEW_INVITED` ngay khi gửi lần đầu; đợt cuối sửa câu chữ SRS, nhãn giữ nguyên, rà comment controller | R-F1, D1, D4 |
| C7 | K1: `InterviewSchedule` = khung chốt của giấy mời hiệu lực, chưa chốt → `null`; `INTERVIEW_REMINDER` cần khung chốt, `REQUEST_RESCHEDULE` cần giấy mời hiệu lực; không lấy khung sớm nhất; ghi rõ thay đổi hành vi FR-C07 và test bị ảnh hưởng | Mục 3.7 |
| C8 | Giao diện: hộp thoại danh sách khung (thêm/xoá, tối đa 5, lỗi theo từng khung); H09 giấy mời hiệu lực + nút Gửi lại + giấy mời cũ; U08 danh sách khung chỉ đọc, đánh dấu khung chốt, chưa có nút chọn | `UI.md` |

### 11.2 Câu hỏi Q1–Q9

| # | Câu hỏi | Quyết định (Đã chốt) | Phương án đã loại / đánh đổi | Mục áp dụng |
|---|---|---|---|---|
| Q1 | `scheduledAt` có giây | **Đã chốt:** backend cắt về đầu phút trước khi kiểm và lưu; không từ chối giây lẻ | Từ chối giây lẻ: làm đỏ các test có sẵn của FR-H07 gửi `Instant.now().plus(…)` | R-S3, T3 |
| Q2 | Chặn race gửi lại ↔ Trúng tuyển/Từ chối/Rút đơn | **Đã chốt:** `UPDATE job_applications SET status = status WHERE id = ? AND status = 'INTERVIEW_INVITED'` trong cùng transaction, kiểm số dòng (0 → 409), theo khuôn `updateStatusIfCurrent` (chore/hardening); khoá chạy **trước** khi đọc/đánh dấu giấy mời hiệu lực | `SELECT … FOR SHARE`: chưa có tiền lệ trong dự án; chấp nhận race: giấy mời mới có thể ra đời sau khi đơn đã có kết quả. Đánh đổi: trigger đổi `updated_at` (tiền lệ FR-U09 Q19) | R-G3, R-G4, T5, T10 |
| Q3 | Lý do soạn nháp khi giấy mời chưa chốt | **Đã chốt:** không thêm mã mới; dùng `NO_ACTIVE_INTERVIEW` và câu hiện có | Mã `INTERVIEW_SLOT_NOT_CONFIRMED` + câu mới: thêm thay đổi vào FR-C07 (enum, `types.ts`, `draftLabels.ts`) | R-K2x, T19 |
| Q4 | DTO I4 có `id` khung | **Đã chốt:** có | Không có, để FR-U10 thêm: FR-U10 phải đổi lại DTO vừa đổi | R-A3, mục 4.4, T13 |
| Q5 | Thông báo gửi lại hiện khung giờ ở đâu | **Đã chốt:** web một dòng + liên kết tới trang chi tiết đơn (FR-U08); danh sách khung chỉ có trong email | Danh sách khung trong `body` (web hiện một đoạn dồn dòng); thêm `whitespace-pre-line` ở giao diện thông báo (đổi cách hiện thông báo tin nhắn cũ) | R-N2, R-N3, T20, T21 |
| Q6 | Hộp thoại "Gửi lại" điền sẵn gì | **Đã chốt:** tiêu đề và nội dung từ giấy mời đang hiệu lực (không render lại mẫu); khung giờ để trống | Render lại từ mẫu (preview): mất phần HR đã sửa ở lần gửi trước | Mục 2, UI.md 4b |
| Q7 | Chỗ đặt giấy mời trên trang hồ sơ đơn | **Đã chốt:** tab mới "Giấy mời phỏng vấn" (giấy mời hiệu lực, nút Gửi lại, giấy mời cũ), đặt **giữa "Sàng lọc" và "Lịch sử"**; thanh thao tác FR-H07 không đổi | Khối đầu tab "CV & điểm"; khối dưới đầu trang trên thanh tab | UI.md mục 2, 4c |
| Q8 | Câu cho ứng viên khi giấy mời nhiều khung chưa chốt | **Đã chốt:** "Hãy nhắn cho nhà tuyển dụng qua tab Trao đổi để xác nhận khung giờ phù hợp." (FR-U10 thay câu này) — *Sửa bởi FR-U10 (R-U2): câu đã được thay bằng câu hướng dẫn chọn "Chọn một khung giờ phù hợp rồi bấm "Xác nhận khung giờ". …"; mọi khung đã qua → câu "Các khung giờ trong giấy mời đã qua. …" (U10 UI.md mục 7)* | Câu dài hai ý "Nhà tuyển dụng đề xuất…" | UI.md 4d, mục 7 |
| Q9 | `location` bắt buộc không | **Đã chốt:** bắt buộc ở API (bỏ khoảng trắng hai đầu, không rỗng); cột cho phép `NULL` chỉ cho dữ liệu cũ; hiển thị "Chưa có dữ liệu" | Tuỳ chọn như V1: email/giấy mời có khung không biết đi đâu | R-S6, R-S8, T3 |

### 11.3 Năm điểm bổ sung

| # | Điểm | Quyết định (Đã chốt) | Mục áp dụng |
|---|---|---|---|
| B1 | Mã lỗi | Giữ `INVALID_INTERVIEW_SCHEDULE` cho mọi lỗi khung; gửi lại sai trạng thái → **409** `INVITATION_RESEND_NOT_ALLOWED` | R-S8, R-G2, mục 4.3 |
| B2 | Múi giờ nhập (trình duyệt) khác múi giờ hiển thị (Việt Nam) | Ghi giới hạn đã biết, không xử lý | Mục 6, D2 |
| B3 | Đổi giấy mời A9 trong seed test | Đồng ý; đã soát mọi chỗ nhắc A9 | Mục 4.5 |
| B4 | Địa điểm dạng link | Hiện chữ thường, không thành liên kết — muốn bấm được phải kiểm scheme URL, ngoài phạm vi | Mục 6, mục 8, UI.md mục 10 |
| B5 | Tên FR | "Giấy mời phỏng vấn nhiều khung giờ"; đợt cuối sửa `SRS.md:40` (và dòng `:103`) | M7, D1 |

### 11.4 Chốt sau `spec-review` (13/10/2026)

| # | Điểm | Quyết định (Đã chốt) | Phương án đã loại / lý do | Mục áp dụng |
|---|---|---|---|---|
| S1 | Email gửi lại kèm khung giờ lấy từ đâu | **Đã chốt:** V16 thêm `notifications.email_body TEXT NULL`; thông báo `INTERVIEW_INVITATION_RESENT` có `entity_type = 'APPLICATION'`, `entity_id = applicationId` như mọi loại cũ; `body` một dòng, `email_body` = khối R-N3 dựng **một lần** lúc tạo thông báo từ dữ liệu khung trong sự kiện; `mailText` gửi `email_body` khi khác `null`, ngược lại giữ hành vi cũ. Lý do: không phụ thuộc chéo package; email đã gửi được lưu nguyên văn; FR-U10 dùng lại được | Đọc khung qua `entity_id` lúc poller gửi: `notification/` phụ thuộc `interviewinvitation/`, `entity_type` mới làm `reset-test-data.sql` phải thêm điều kiện; không kèm khung trong email: trái Q5 | R-N1–R-N3a, 4.1 bước 7, 4.5, T5, T20, T21, kiểm tĩnh 7.1 |
| S2 | Thứ tự trong transaction gửi lại | **Đã chốt:** khoá hàng đơn (bước 3) chạy **trước** mọi thao tác đọc/đánh dấu giấy mời hiệu lực | Đọc giấy mời hiệu lực trước khi khoá: hai lần gửi lại đồng thời làm lượt sau vi phạm `uq_invitation_active` | R-G3, mục 8, T10a |
| S3 | Chứng minh khoá thật sự chặn `HIRED` | **Đã chốt:** thêm T10(d) đơn luồng, chắc chắn (A giữ khoá chưa commit, B bị chặn quá 500 ms, A commit thì B xong) | Chỉ dựa vào test song song T10(b): không phân biệt được cách "đọc rồi ghi, không khoá" | T10 |
| S4 | Tài liệu FR khác bị ghi đè | **Đã chốt:** D7 thêm H12 vào "Dùng bởi" của K1 (CLAUDE.md §3d); D8 ghi chú "Sửa bởi FR-H12" ở U08 | Để nguyên: người code FR sau tra §3d/U08 sẽ hiểu sai | Mục 10 |
| S5 | Hàm PowerShell của khối 7.2 | **Đã chốt:** chép nguyên `Get-Token`/`Invoke-Api`/`Test-Call` của FR-H11 mục 7.2; nhóm 2 đọc lại I1 kiểm địa điểm có dấu | Hàm của FR-H10: gửi chuỗi qua `Invoke-WebRequest -Body` của PowerShell 5.1, dễ vỡ chữ tiếng Việt khi đọc response | 7.2 |
| S6 | Mẫu `rg` của 7.3 | **Đã chốt:** chỉ quét đúng các file FR-H12; khớp nhầm thì báo cáo đợt liệt kê từng dòng và lý do bỏ qua | Quét cả thư mục: khớp nhầm code không thuộc H12 | 7.3 |
| S7 | Vị trí tab | **Đã chốt:** "Giấy mời phỏng vấn" giữa "Sàng lọc" và "Lịch sử" | — | Q7, UI.md mục 2 |

## 12. Làm rõ sau Plan Mode (duyệt 13/10/2026)

Đối chiếu lại trên `feat/fr-h12-multi-slot` tại `77dac9a` (chỉ khác `78d12a9` ở hai file đặc tả; mục 0 a1–a24 không dòng
nào lệch). Mục này thắng chỗ viết cũ ở các mục được dẫn nếu lệch.

- **L1 (mục 4.5, D2) — lý do sửa `reset-demo-db.sql`.** Câu cũ "thiếu thì `TRUNCATE` báo lỗi vì có FK" sai:
  `reset-demo-db.sql:85-103` là **một** câu `TRUNCATE … CASCADE`, Postgres tự truncate mọi bảng có FK trỏ tới bảng trong
  danh sách. Ba bảng V13–V15 (`job_screening_questions`, `application_screening_answers`, `job_ai_generations`) vắng trong
  danh sách dù comment `:80-81` ghi "toàn bộ 22 bảng" — danh sách không đầy đủ, **không phải lỗi**. Chốt: vẫn thêm
  `interview_invitation_slots` vào danh sách cho tường minh; sửa câu lý do ở 4.5; không sửa ba bảng V13–V15 ở FR-H12;
  D2 ghi một dòng nợ "danh sách bảng của reset-demo-db.sql thiếu bảng V13–V15 (không gây lỗi nhờ CASCADE)".
- **L2 (R-K1, mục 8) — thứ tự ghi khung chốt.** Ánh xạ `confirmed_slot_id` là cột `UUID` thường, không `@ManyToOne`, theo
  quy ước dự án (`Notification.java:18`; `InterviewInvitation.applicationId`). Hibernate không biết FK ghép
  `fk_invitation_confirmed_slot` (không `DEFERRABLE`), nên thứ tự SQL do code quyết định. Với `@UuidGenerator`, câu INSERT
  giấy mời bị hoãn tới flush và mang theo giá trị `confirmed_slot_id` lúc flush — gán khung chốt trước khi khung được
  INSERT thì vi phạm FK. Chốt ba bước: giấy mời `saveAndFlush` (chưa có khung chốt) → khung `saveAllAndFlush` → đặt khung
  chốt + flush (`UPDATE`); áp cho I2 và I3. Mục 8 thêm dòng tương ứng.
- **L3 (mục 4.2) — `@Immutable` và `created_at` của entity khung.** Hibernate 7.4.1 (`spring-boot-dependencies-4.1.0.pom:71`)
  chỉ kiểm bất biến ở flush/UPDATE, không chặn INSERT — tiền lệ chạy thật `ApplicationScreeningAnswer.java:24-27`
  (`ScreeningCaptureService.java:69` `saveAllAndFlush`), `JobAiGeneration.java:24` (`JobAiDraftSaveService.java:64`
  `saveAndFlush`). Bẫy đã gặp (`JobAiGeneration.java:44-47`): `@Generated` trên `created_at` của entity `@Immutable` làm
  Hibernate đọc lại cột sau INSERT, đặt khoá WRITE → `UnsupportedLockAttemptException`. Chốt: `created_at` của
  `InterviewInvitationSlot` dùng `@Column(insertable = false, updatable = false)`, không `@Generated`. (`InterviewInvitation`
  không `@Immutable` nên giữ `@Generated` như hiện có.)
- **L4 (T3) — test có sẵn "thiếu `scheduledAt`".** `send_missingScheduledAt_returns400`
  (`InterviewInvitationControllerIntegrationTest.java:356-364`) hiện chỉ kiểm 400 + không ghi dòng; lỗi cũ đến từ Bean
  Validation `@NotNull`. Với thân `slots` (đợt 3), ca này thành "khung 1 thiếu `scheduledAt`" → 400
  `INVALID_INTERVIEW_SCHEDULE`. Chốt: giữ tên test, đổi helper gửi `{"slots":[{"location":"…"}]}`, thêm khẳng định mã lỗi.
- **L5 (T13) — khẳng định chuỗi mâu thuẫn Q4.** `InterviewInvitationCandidateControllerIntegrationTest.java:247-248`
  khẳng định `json` không chứa `"id"`; khung trong I4 có `id` (Q4) nên khẳng định này đỏ dù gốc JSON đúng. Chốt: đọc JSON
  bằng `ObjectMapper`, kiểm gốc không có `id`/`applicationId`/`sentBy`/`supersededAt`/`scheduledAt`, mỗi phần tử `slots`
  có `id`; đổi tên test `get_ownApplication_returnsInvitationWithFiveFieldsOnly` →
  `get_ownApplication_returnsActiveInvitationWithSlots` (đợt 3).
- **L6 (T21) — hai ca đã có sẵn.** `NotificationMailOrchestratorIntegrationTest.java:200-230` đã có
  `processOne_newMessage_appendsReplyLinkWithFrontendBaseUrl` (T21b) và
  `processOne_oldTypeWithLink_sendsBodyExactly_withoutReplyLink` (T21c). Chốt: giữ nguyên hai test đó (phải xanh), chỉ
  thêm ca (a) `email_body` khác `null` vào cùng lớp.
- **L7 (mục 9 đợt 2, 3) — đợt 2 phải biên dịch được mà không đổi API.** Bỏ field `scheduledAt`/`location` khỏi entity làm
  vỡ biên dịch ở `InterviewInvitationService.java:112-113`, `:153`, `:192-193`, `ConversationContextAssembler.java:82`
  và hai helper test (`ConversationContextAssemblerIntegrationTest.java:222-223`,
  `MessageDraftIntegrationTestSupport.java:414-415`). Chốt: đợt 2 **không** đổi DTO/API — service giữ thân request cũ
  (`scheduledAt`/`location` ở gốc, kiểm như hiện nay) và ghi thành 1 khung đã chốt theo ba bước L2; hai DTO giữ hình dạng,
  lấy giá trị từ khung chốt của giấy mời hiệu lực; K1 đọc khung chốt của giấy mời hiệu lực. Hai helper test chỉ đổi cách
  chèn dữ liệu (giấy mời + 1 khung chốt; giấy mời chèn sau đánh dấu giấy mời trước là `superseded_at` để không vi phạm
  `uq_invitation_active`), không đổi khẳng định. Điều kiện xong đợt 2: mọi lớp `InterviewInvitation*` và các lớp đọc giấy
  mời có sẵn xanh **không sửa thân request**. Đổi thân request sang `slots` là việc của đợt 3.
- **L8 (T10) — lớp test riêng cho tuần tự hoá.** Các lớp HTTP liên quan đều `@Transactional` cấp lớp
  (`InterviewInvitationControllerIntegrationTest.java:39`, `InterviewInvitationCandidateControllerIntegrationTest.java:35`,
  `ApplicationHrDetailControllerIntegrationTest.java:69`, `ApplicationStatusControllerIntegrationTest.java:35`,
  `CandidateApplicationCrossEndpointAccessIntegrationTest.java:40`, `HrApplicationCrossEndpointAccessIntegrationTest.java:40`)
  — hai luồng song song không thấy dữ liệu của nhau. Chốt: T10(a–d) ở lớp mới
  `InterviewInvitationResendConcurrencyIntegrationTest`, không `@Transactional` cấp lớp.

Bổ sung trong đợt 3 (duyệt 14/10/2026):

- **L9 (R-S6, T3) — kiểm xuống dòng sau khi bỏ khoảng trắng hai đầu.** Bảng R-S8 đo độ dài trên giá trị đã bỏ khoảng
  trắng, nên mọi kiểm `location` (rỗng, độ dài, xuống dòng) dùng cùng giá trị đã bỏ khoảng trắng — cũng là giá trị được
  lưu. Hệ quả: xuống dòng ở đầu/cuối bị cắt cùng khoảng trắng (`"Tầng 3\n"` → lưu `"Tầng 3"`); xuống dòng (`\r` hoặc
  `\n`) ở giữa → 400 "Khung giờ {n}: địa điểm chỉ được nằm trên một dòng.". T3 thêm ca `"Tầng 3\n"` → 201, lưu
  `"Tầng 3"`.
- **L10 (kiểm tĩnh 7.1) — phạm vi lệnh `FOR SHARE|FOR UPDATE|@Lock`.** Quét cả thư mục `jobapplication/` khớp comment có
  sẵn của FR-U09 ở `ApplicationService.java:82` ("khoa hang jobs (FOR SHARE)") — không phải code của FR-H12. Chốt: thu
  phạm vi về `interviewinvitation/` + `jobapplication/JobApplicationRepository.java` (nơi có `lockIfInterviewInvited`),
  kỳ vọng 0 dòng.
- **L11 (R-G3, kiểm tĩnh 7.1) — hai mốc thời gian ở I3; kỳ vọng tạm thời của lệnh `findBy…CreatedAtDesc`.** Bước 2 kiểm
  "tương lai" (R-S4) bằng một mốc `now` riêng lấy tại bước 2, vì bước này chạy trước khi khoá hàng đơn; `superseded_at`
  của giấy mời cũ và `sent_at` của giấy mời mới dùng chung đúng một mốc lấy ở bước 4 (điều mục 8 cấm là dùng hai mốc
  cho hai cột này). Lệnh `findBy…CreatedAtDesc` (phạm vi theo L12): đợt 3 kỳ vọng 3 dòng (khai báo trong
  `InterviewInvitationRepository` + `ConversationContextAssembler` + `MessageDraftFacade`, hai chỗ sau đổi ở đợt 4); từ
  đợt 4 kỳ vọng 1 dòng.
- **L12 (kiểm tĩnh 7.1) — lệnh `findBy…CreatedAtDesc` loại gói `scoring/`.** Quét `backend/src/main` khớp thêm 6 dòng của
  `ScoringRunRepository.findByApplicationIdOrderByCreatedAtDesc` (method trùng tên, gói `scoring/`, không liên quan
  giấy mời). Chốt: giữ phạm vi `backend/src/main`, chỉ loại `scoring/` — `rg -n "findByApplicationIdOrderByCreatedAtDesc"
  backend/src/main -g "!**/scoring/**"`. Không thu về vài thư mục cụ thể: để lệnh vẫn bắt được code mới ở gói khác (vd
  `notification/`, FR-U10) chọn giấy mời theo `created_at`. Kỳ vọng: 3 dòng ở đợt 3 (đã xác nhận 14/10/2026), 1 dòng
  (khai báo trong repository) từ đợt 4.

Bổ sung trong đợt 7 (duyệt 14/10/2026):

- **L13 (khối 7.2, nhóm 2) — chuỗi kiểm dấu tiếng Việt phải chưa có trong seed.** Địa điểm khung 1 của `$body2` trước đây là
  `"Tầng 3, 45 Bạch Đằng"` — chuỗi này đã có sẵn trong địa điểm giấy mời A2 của seed (mục 4.5), nên dòng kiểm "đọc lại I1
  A2 có chuỗi" có thể ra `True` dù request gửi lại bị vỡ dấu. Chốt: đổi địa điểm khung 1 của `$body2` thành
  `"Phòng họp Hồ Gươm, tầng 5"` (không có trong seed); dòng kiểm nhóm 2 tìm đúng chuỗi này trong I1 của A2 và in
  `True`/`False`; "Mong đợi" sửa theo.
- **L14 (mục 4.5, R-C1) — câu trong mẫu giấy mời của seed test.** Mẫu `interview_templates` của `seed-test.sql` có câu
  "theo thời gian và địa điểm ghi trong thư này" — sai với R-C1 (khung giờ lưu và hiển thị tách khỏi thư, không chèn vào
  thư). Chốt: đổi câu trong **mẫu** thành "theo một trong các khung giờ phỏng vấn được gửi kèm"; giấy mời **đã gửi** trong
  seed (mục 11 của `seed-test.sql`) giữ nguyên câu cũ vì nội dung đã gửi bất biến (R-C2). Đã chạy lại
  `reset-test-data.sql` + `seed-test.sql` không lỗi (6 mẫu mang câu mới; 4 giấy mời đã gửi giữ câu cũ).
