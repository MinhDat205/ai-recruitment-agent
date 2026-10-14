# FR-U10 — Chọn khung giờ phỏng vấn

> Trạng thái: ĐÃ DUYỆT 14/10/2026.
>
> Quyết định Q1–Q13 (mục 11.2) và O1–O6 (mục 11.3) đã chốt 14/10/2026. Không còn điểm mở.

- Nhóm: Ứng viên
- Tóm tắt: Ở trang chi tiết đơn (FR-U08), ứng viên chọn **một** khung giờ trong giấy mời hiệu lực nhiều khung (FR-H12)
  rồi xác nhận. Chọn **một lần**, không tự đổi; muốn đổi thì nhắn HR để HR gửi lại giấy mời. Khung đã qua giờ không chọn
  được. Chủ công ty nhận thông báo web + email "ứng viên đã chọn lịch phỏng vấn". Trạng thái đơn **không đổi**.
- Phụ thuộc: FR-H12, FR-U08 — cả hai **Đã hoàn thành** (`docs/ROADMAP.md:918`; `docs/walkthrough/fr-u08-application-detail.md`).
  Phụ thuộc ngầm: FR-C03 (thông báo), FR-C07 (K1 đọc khung chốt — không sửa, mục 3.7).
- Nhánh: `feat/fr-u10-pick-slot` (tạo từ `main` tại `f4665a3`, 14/10/2026).
- Mở rộng: FR-C03 (một loại thông báo mới cho HR: `INTERVIEW_SLOT_CONFIRMED`); FR-H12 (khối giấy mời phía ứng viên có
  phần chọn khung, thay câu Q8 của H12; tab HR hiện thời điểm ứng viên chọn; sửa một câu chữ sai của H12 — mục 3.8).
- Không gọi AI. Không migration (Q-nền N3).

## 0. Đã đối chiếu code trước khi viết (CLAUDE.md §6)

Đối chiếu trên `feat/fr-u10-pick-slot` tại `f4665a3` (14/10/2026; cây làm việc chỉ có `cot.txt` chưa theo dõi — không
liên quan).

### (a) Hiện trạng

| # | Điểm kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| a1 | Cột lưu khung đã chốt có sẵn, V16 tạo cho U10 | Đúng | `V16__interview_invitation_slots.sql:23-26` (`confirmed_slot_id`, `confirmed_at`, `superseded_at`); `InterviewInvitation.java:52-61` (cột `UUID` thường, không `@ManyToOne`) |
| a2 | Ràng buộc DB quanh khung chốt | Có ba | FK ghép `fk_invitation_confirmed_slot (id, confirmed_slot_id) → slots(invitation_id, id)` và CHECK `chk_invitation_confirmed_pair` (`V16:53-58`); partial unique `uq_invitation_active` (`V16:48`) |
| a3 | Vi phạm FK ghép hiện ra **500** | Đúng | `GlobalExceptionHandler.java:410-451`: chỉ dịch các constraint đã biết, còn lại `throw ex` (`:450`). U10 phải chặn trước (R-W4) |
| a4 | Chưa có đường nào ghi `confirmed_*` cho giấy mời nhiều khung | Đúng | `InterviewInvitationService.createInvitation` (`:209-233`) chỉ chốt khi đúng 1 khung (`:226-230`); H12 R-K2, R-B1 |
| a5 | Khung giờ bất biến | Đúng | `InterviewInvitationSlot.java:22-27` (`@Immutable`, không setter); H12 R-C2 cho phép ghi `confirmed_*` sau khi tạo (FR-U10) |
| a6 | Giấy mời hiệu lực = `superseded_at IS NULL` | Đúng | `InterviewInvitationRepository.java:22` (`findByApplicationIdAndSupersededAtIsNull`); H12 R-A1 |
| a7 | Khoá hàng đơn + kiểm `INTERVIEW_INVITED` trong một câu | Có | `JobApplicationRepository.lockIfInterviewInvited` (`:57-61`, `@Modifying(clearAutomatically = true, flushAutomatically = true)`), dùng ở I3 (`InterviewInvitationService.java:155`) |
| a8 | Mọi đường ghi giấy mời và trạng thái đơn đi qua hàng `job_applications` | Đúng | I2: `changeStatus` → `updateStatusIfCurrent` (`InterviewInvitationService.java:133`, `JobApplicationRepository.java:30-35`); I3: `lockIfInterviewInvited` (`:155`); Trúng tuyển/Từ chối: `updateStatusIfCurrent` (`ApplicationStatusService.java:76`); Rút đơn: `save` entity (`ApplicationService.java:136-137`) |
| a9 | I4 (ứng viên đọc giấy mời) trả `id` khung, không trả `id` giấy mời | Đúng | `CandidateInterviewInvitationResponse.java:10-18`; dựng ở `InterviewInvitationService.getActiveInvitationForCandidate` (`:246-273`) |
| a10 | Quy ước quyền phía ứng viên của endpoint giấy mời: 403 cho đơn người khác | Đúng | `InterviewInvitationService.java:239-252`; `CandidateApplicationCrossEndpointAccessIntegrationTest.java:33`, `:188` (`…_404ExceptInvitation403`) |
| a11 | `/api/candidates/**` chặn role ở filter chain | Đúng | `SecurityConfig.java:55`; comment `InterviewInvitationCandidateController.java:11-13` |
| a12 | Đơn chuyển `INTERVIEW_INVITED` ngay khi gửi giấy mời | Đúng | `InterviewInvitationService.sendInvitation` (`:117-138`): `changeStatus` (`:133`) chạy trước `createInvitation` (`:137`), không phụ thuộc khung chốt |
| a13 | K1/C07 đọc khung chốt của giấy mời hiệu lực | Đúng | `ConversationContextAssembler.java:84-89`; `MessageDraftFacade.java:116-132`, `:171-176` (`INTERVIEW_REMINDER` cần `confirmed`) |
| a14 | Khuôn thông báo cho HR (chủ công ty) | Có | `NotificationEventListener.onApplicationWithdrawn` (`:102-115`, `AFTER_COMMIT` + `REQUIRES_NEW`, người nhận `company.getOwnerId()`); `save` (`:173-186`) |
| a15 | Khuôn sự kiện mang sẵn giá trị khung | Có | `InterviewInvitationResentEvent.java:11-20`; `notification/` không import `interviewinvitation/` (H12 kiểm tĩnh 7.1) |
| a16 | Cột `email_body` + mail gửi nguyên văn | Có | `V16:65`; `NotificationMailOrchestrator.mailText` (`:62-70`) |
| a17 | Định dạng giờ VN cho email | Có | `NotificationContentBuilder.SLOT_TIME` (`:19-20`), `MISSING_LOCATION` (`:21`) |
| a18 | Thêm loại thông báo không cần migration | Đúng | `NotificationType.java:3-13`; `notifications.type VARCHAR(50)` không CHECK (H12 a8) |
| a19 | Frontend khối giấy mời phía ứng viên chỉ đọc, câu Q8 chờ U10 thay | Đúng | `InterviewInvitationDetails.tsx:11-13`, `:40-50`; `invitationLabels.ts:35-36` |
| a20 | `InvitationSection` biết trạng thái đơn, `InterviewInvitationDetails` không | Đúng | `InvitationSection.tsx:10`, `:14`, `:37` |
| a21 | Câu chữ sai có từ H12 | Có | `invitationLabels.ts:20-21` "Ứng viên đã chốt khung …" hiện cả với giấy mời 1 khung tự chốt khi gửi (`InterviewInvitationDialog.tsx:187`) |
| a22 | Tab HR hiện khung chốt, chưa phân biệt "tự chốt" với "ứng viên chọn" | Đúng | `InterviewInvitationTab.tsx:86-89`; `PreviousInvitationCard.tsx:11-16` |
| a23 | Tab `invitation` có sẵn ở trang hồ sơ đơn HR | Đúng | `HrApplicationDetailPage.tsx:24` |
| a24 | Không có component radio/toast trong `components/ui` | Đúng | `frontend/src/components/ui/` có `button, card, checkbox, dialog, input, label, popover, select, sheet, table, tabs, textarea`; `rg "sonner\|useToast"` → 0 |
| a25 | Khuôn hộp xác nhận phía ứng viên | Có | `RevokeTalentPoolConsentDialog.tsx:25-87` (khoá hai nút khi gửi, lỗi trong hộp thoại, `onCloseAutoFocus`) |
| a26 | Thiếu trường trong body → 400 dạng map trường | Đúng | `GlobalExceptionHandler.handleValidation` (`:22-29`). Body JSON hỏng (vd `slotId` không phải UUID) là nợ có sẵn: server thật trả 401 (`ROADMAP.md:906-907`) |
| a27 | T17 của H12 chỉ quét đường dẫn HR | Đúng | `InterviewInvitationControllerIntegrationTest.java:1158-1180` — endpoint POST phía ứng viên không làm đỏ test này |
| a28 | Seed test: giấy mời chưa chốt duy nhất là A9 (Trần Bảo Ngọc, phải qua onboarding) | Đúng | `seed-test.sql:437-510`; `db/seed/README.md:317` |
| a29 | `reset-test-data.sql` xoá mọi đơn của user test | Đúng | `t_apps` theo `candidate_id` user test **hoặc** `job_id` tin test (`reset-test-data.sql:72-74`); giấy mời xoá ở `:97`, khung theo `ON DELETE CASCADE` |
| a30 | Migration cao nhất | V16 | `backend/src/main/resources/db/migration/V16__interview_invitation_slots.sql`; U10 **không** thêm V17 (N3) |

### (b) Mâu thuẫn / lệch — đã có hướng xử lý

| # | Điểm | Hướng xử lý |
|---|---|---|
| M1 | Đề bài khảo sát nhắc "đổi khung"; `docs/features/README.md:596` định hướng chọn một lần | Q2: chọn một lần, không tự đổi (R-W6) |
| M2 | Câu Q8 của H12 "Hãy nhắn cho nhà tuyển dụng qua tab Trao đổi để xác nhận khung giờ phù hợp." ghi rõ "FR-U10 thay câu này" (H12 REQUIREMENT Q8) | Thay theo UI.md mục 7 (R-U2) |
| M3 | `invitationLabels.ts:20-21` nói "Ứng viên đã chốt" cho cả giấy mời 1 khung tự chốt (a21) | Q9: sửa đúng câu đó (R-H2), không đụng câu khác của H12 |
| M4 | H12 mục 4.5: "seed không ghi dữ liệu mà API mới không tạo được"; Q10 cần khung đã qua trong seed | Ngoại lệ có chủ đích, ghi ở mục 4.5 — khung đã qua xảy ra tự nhiên khi thời gian trôi qua, chỉ API không tạo **ngay** được |

## 1. Mục đích

Cho ứng viên tự chốt lịch phỏng vấn từ các khung HR đề xuất, để HR không phải trao đổi qua lại; HR biết ngay khung
ứng viên đã chọn. Việc chọn chỉ ghi khung đã chốt của giấy mời, không đổi trạng thái đơn, không đổi nội dung giấy mời
đã gửi.

## 2. Luồng người dùng

**Ứng viên chọn khung** — `/candidate/applications/:id`, tab "Thông tin đơn", khối "Giấy mời phỏng vấn"
1. Mở trang chi tiết đơn (từ "Đơn ứng tuyển", hoặc bấm thông báo mời / gửi lại giấy mời — các thông báo đó đã trỏ về
   trang này).
2. Đơn `INTERVIEW_INVITED`, giấy mời hiệu lực chưa chốt, còn ít nhất một khung chưa qua: thấy câu hướng dẫn và danh sách
   khung dạng radio; khung đã qua hiện nhưng không chọn được.
3. Chọn một khung → bấm "Xác nhận khung giờ" → hộp xác nhận nêu khung đã chọn và ghi rõ sau khi xác nhận không tự đổi
   được → bấm "Xác nhận".
4. Thành công: hộp thoại đóng, khối chuyển sang trạng thái đã chốt (dấu "Đã chốt" trên khung, câu "Bạn đã chọn khung giờ
   này lúc …"). Không khung nào phù hợp: nhắn HR qua tab "Trao đổi" (C06; C07 có tình huống "Xin dời lịch").

**Ứng viên không chọn được** — mọi khung đã qua: không có radio, câu cố định hướng sang tab "Trao đổi" (Q6). Đơn không
`INTERVIEW_INVITED`: danh sách chỉ đọc (mục 11.3, O1).

**HR** — nhận thông báo web "Ứng viên đã chọn lịch phỏng vấn" + email; bấm thông báo mở
`/hr/applications/{id}?tab=invitation`: khung ứng viên chọn có dấu "Đã chốt", dòng "Ứng viên đã chọn lúc …" (Q9).
Muốn đổi lịch: "Gửi lại giấy mời" như H12 (không đổi).

**Hệ thống** — không gọi AI. Một transaction ngắn: kiểm quyền → khoá hàng đơn → ghi khung chốt bằng một câu `UPDATE` có
điều kiện → phát sự kiện. Không đổi trạng thái đơn, không ghi lịch sử trạng thái.

## 3. Quy tắc nghiệp vụ

### 3.1 Điều kiện chọn được (R-C)

- **R-C1.** Chỉ chủ đơn (ứng viên của `job_applications.candidate_id`) chọn được.
- **R-C2.** Đơn đang `INTERVIEW_INVITED` (đọc dưới khoá hàng, R-W2).
- **R-C3.** Đơn có giấy mời hiệu lực (`superseded_at IS NULL`).
- **R-C4.** Giấy mời hiệu lực **chưa chốt** (`confirmed_slot_id IS NULL`). Giấy mời 1 khung đã chốt ngay khi gửi (H12
  R-K1) nên không bao giờ chọn được.
- **R-C5.** Khung `slotId` thuộc **chính** giấy mời hiệu lực đó.
- **R-C6.** Khung **chưa qua**: `scheduled_at > now`, `now` lấy **một lần** bằng Java sau khi khoá (R-W3). Không có thời gian
  báo trước tối thiểu (Q5). Khung đã cắt về đầu phút (H12 R-S3): khung 10:00 không chọn được lúc 10:00:00 trở đi.
- **R-C7.** Trạng thái tin tuyển dụng **không** ảnh hưởng: tin `CLOSED`/`PAUSED` hay đã xoá mềm vẫn chọn được (Q13), như I3
  của H12 không kiểm tin.

### 3.2 Transaction chọn khung (R-W)

- **R-W1.** Method `InterviewInvitationService.confirmSlot(UUID candidateId, UUID applicationId, UUID slotId)`,
  `@Transactional`, **một** transaction ngắn, **đúng thứ tự dưới — bắt buộc**:
  1. **Kiểm quyền** (R-A1): `jobApplicationRepository.findById` → không có → 404 `APPLICATION_NOT_FOUND`; khác
     `candidateId` → 403. Bước này **chỉ** đọc `job_applications`, **không** đọc `interview_invitations`.
  2. **Khoá hàng đơn + kiểm trạng thái** trong cùng một câu: `jobApplicationRepository.lockIfInterviewInvited(applicationId)`
     (dùng lại nguyên method của H12, a7). 0 dòng → 409 `SLOT_SELECTION_NOT_ALLOWED`.
  3. **Lấy `now` một lần**: `Instant now = Instant.now()` — dùng cho cả điều kiện "chưa qua" lẫn `confirmed_at`.
  4. **Ghi khung chốt bằng một câu `UPDATE` có điều kiện** (method mới
     `InterviewInvitationRepository.confirmSlotIfAvailable`, native, `@Modifying(clearAutomatically = true,
     flushAutomatically = true)`):

     ```sql
     UPDATE interview_invitations i
     SET confirmed_slot_id = :slotId, confirmed_at = :now
     WHERE i.application_id = :applicationId
       AND i.superseded_at IS NULL
       AND i.confirmed_slot_id IS NULL
       AND EXISTS (SELECT 1 FROM interview_invitation_slots s
                   WHERE s.id = :slotId AND s.invitation_id = i.id AND s.scheduled_at > :now)
     ```

  5. **1 dòng** → đọc lại giấy mời hiệu lực + khung (persistence context đã bị xoá bởi `clearAutomatically`, lần đọc là
     `SELECT` thật) để dựng response (R-W7) và giá trị sự kiện. **0 dòng** → đọc lại để trả đúng mã (R-W5).
  6. **Phát sự kiện** `InterviewSlotConfirmedEvent` (R-N1) — chỉ khi bước 4 trả 1 dòng.
- **R-W2. Khoá (bước 2) PHẢI chạy TRƯỚC mọi thao tác đọc giấy mời hiệu lực.** Không gọi
  `findByApplicationIdAndSupersededAtIsNull`, không nạp entity giấy mời hay khung ở bước 1. Lý do: I2, I3, Trúng
  tuyển/Từ chối, Rút đơn đều ghi hàng `job_applications` (a8); có khoá rồi thì giấy mời hiệu lực không đổi được tới hết
  transaction, nên lần đọc ở bước 5 (nếu 0 dòng) phản ánh đúng lý do. Đọc trước khi khoá thì có thể chẩn đoán theo dữ
  liệu cũ (vd báo `INTERVIEW_SLOT_PASSED` trong khi giấy mời đã bị thay).
- **R-W3.** **Không** dùng `SELECT … FOR SHARE`/`FOR UPDATE`/`@Lock` (khuôn H12 Q2). **Không** ghi bằng cách nạp entity
  `InterviewInvitation` rồi `set…` + `flush` — Hibernate ghi lại mọi cột của giấy mời (không `@DynamicUpdate`), và mất điều
  kiện nguyên tử `confirmed_slot_id IS NULL`.
- **R-W4. Chặn trước FK.** Điều kiện `s.invitation_id = i.id` trong `EXISTS` bảo đảm câu `UPDATE` chỉ ghi khung của chính
  giấy mời đó, nên `fk_invitation_confirmed_slot` **không bao giờ** bị vi phạm qua đường này: `slotId` không tồn tại, của
  giấy mời cũ, hay của đơn người khác đều làm câu `UPDATE` trả 0 dòng → 409 (R-W5), **không** 500. Không thêm nhánh dịch
  `fk_invitation_confirmed_slot` vào `GlobalExceptionHandler` (vi phạm FK ở đây là lỗi lập trình, để 500).
- **R-W5. Chẩn đoán khi 0 dòng** — đọc theo thứ tự, trả lỗi đầu tiên khớp:

  | # | Điều kiện (đọc sau khoá) | Lỗi |
  |---|---|---|
  | 1 | Không có giấy mời hiệu lực | 404 `INTERVIEW_INVITATION_NOT_FOUND` (có sẵn) |
  | 2 | Giấy mời hiệu lực đã chốt (`confirmedSlotId != null`) — **kể cả** khi `slotId` đúng khung đã chốt (Q3) | 409 `INTERVIEW_SLOT_ALREADY_CONFIRMED` |
  | 3 | `slotId` không thuộc giấy mời hiệu lực (không tồn tại / giấy mời cũ / đơn khác — **không** phân biệt) | 409 `INTERVIEW_INVITATION_CONFLICT` (dùng lại mã + câu có sẵn của H12) |
  | 4 | Khung có `scheduled_at <= now` (cùng `now` của bước 3) | 409 `INTERVIEW_SLOT_PASSED` |
  | 5 | Không điều kiện nào ở trên (không thể xảy ra dưới khoá) | 409 `INTERVIEW_INVITATION_CONFLICT` — phòng thủ, không 500 |

  Đọc ở dòng 3 dùng `slotRepository.findById(slotId)` rồi so `invitationId` với id giấy mời hiệu lực — **không** để lộ
  khung của đơn khác qua câu lỗi (cùng một mã, cùng một câu cho mọi trường hợp dòng 3).
- **R-W6. Chọn một lần** (Q2). Không endpoint đổi/huỷ lựa chọn. Muốn đổi: ứng viên nhắn HR (C06), HR "Gửi lại giấy mời"
  (H12 I3 — giấy mời cũ giữ nguyên khung chốt, H12 R-G5). Chọn lần hai, kể cả đúng khung đã chốt → 409
  `INTERVIEW_SLOT_ALREADY_CONFIRMED`, **không** tạo thông báo (Q3).
- **R-W7. Response** = `CandidateInterviewInvitationResponse` (DTO I4, **không đổi hình dạng**) của giấy mời hiệu lực sau
  khi ghi, `200 OK`. Tách phần dựng DTO trong `getActiveInvitationForCandidate` (`:262-272`) thành một method private dùng
  chung cho I4 và C1 — hành vi I4 không đổi.
- **R-W8.** Không lời gọi ngoài nào trong transaction. Sự kiện phát trong transaction, listener xử lý `AFTER_COMMIT` (R-N1).

### 3.3 Không đổi trạng thái đơn (R-S)

- **R-S1.** C1 **không** gọi `ApplicationStatusService`, **không** ghi `application_status_history`, **không** phát
  `ApplicationStatusChangedEvent`. Kiểm tĩnh: `changeStatus` trong `interviewinvitation/` vẫn đúng **1** chỗ (I2).
- **R-S2.** Câu khoá `SET status = status` làm trigger `set_updated_at` đổi `job_applications.updated_at` — chấp nhận theo
  tiền lệ FR-U09 Q19 và H12 R-G4 (cột không hiển thị, không dùng sắp xếp). T1 so toàn hàng đơn trước/sau, loại trừ
  `updated_at`.
- **R-S3.** Giấy mời: chỉ hai cột `confirmed_slot_id`, `confirmed_at` đổi; `subject`, `rendered_content`, `sent_at`,
  `sent_by`, `superseded_at`, `created_at` và mọi khung giữ nguyên (H12 R-C2).

### 3.4 Cạnh tranh — tuần tự hoá qua khoá hàng đơn (R-R)

Postgres (READ COMMITTED) giữ khoá hàng do `UPDATE` lấy tới khi transaction kết thúc; `UPDATE` khác trên cùng hàng phải chờ
rồi đánh giá lại `WHERE` trên bản mới nhất đã commit (giải thích đầy đủ: H12 R-G4).

| Đường đồng thời | C1 khoá **trước** | Đường kia commit **trước** |
|---|---|---|
| C1 khác cùng đơn (bấm đúp, hai tab) | Lượt sau chờ, rồi `confirmed_slot_id IS NULL` sai → 0 dòng → 409 `INTERVIEW_SLOT_ALREADY_CONFIRMED`; đúng 1 thông báo | — |
| Gửi lại giấy mời (I3, `lockIfInterviewInvited`) | C1 ghi khung chốt vào giấy mời hiện tại rồi commit; I3 chạy tiếp, đánh dấu giấy mời đó `superseded_at` — giấy mời cũ **giữ** khung chốt (H12 R-G5); ứng viên nhận thông báo gửi lại, HR nhận thông báo đã chọn | C1 chờ, thấy giấy mời mới; `slotId` thuộc giấy mời cũ → 409 `INTERVIEW_INVITATION_CONFLICT` (hoặc `INTERVIEW_SLOT_ALREADY_CONFIRMED` nếu giấy mời mới 1 khung — dòng 2 R-W5 đứng trước dòng 3) |
| Trúng tuyển / Từ chối (`updateStatusIfCurrent`) | Câu kia chờ, đánh giá lại (vẫn `INTERVIEW_INVITED`) → ghi `HIRED`/`REJECTED`; cả hai thành công | `lockIfInterviewInvited` đánh giá lại → 0 dòng → 409 `SLOT_SELECTION_NOT_ALLOWED` |
| Rút đơn (`save` entity, không điều kiện trạng thái) | Câu `UPDATE` của rút đơn chờ rồi ghi `WITHDRAWN` — thứ tự hợp lệ | → 0 dòng → 409 `SLOT_SELECTION_NOT_ALLOWED` |

- **R-R1.** Cả hai thứ tự với gửi lại đều chấp nhận (Q8). Giới hạn đã biết (mục 6): HR đang mở hộp thoại "Gửi lại" không
  được báo rằng ứng viên vừa chọn; hộp thoại vẫn gửi được.
- **R-R2.** Không có thứ tự nào để khung chốt được ghi **sau khi** đơn đã có kết quả cuối, đã rút, hoặc vào giấy mời đã
  bị thay.

### 3.5 Quyền truy cập (R-A)

- **R-A1.** Theo khuôn I4 (a10, Q4): đơn không tồn tại → 404 `APPLICATION_NOT_FOUND`; đơn của ứng viên khác → **403**
  (`AccessDeniedException`). Không token → 401; token HR → 403 (filter chain `/api/candidates/**`, a11).
- **R-A2.** Lỗi 403/404 trả **trước** khi khoá, không ghi gì, không thông báo.

### 3.6 Thông báo (R-N)

- **R-N1.** Sự kiện `InterviewSlotConfirmedEvent(UUID applicationId, UUID jobId, UUID candidateId, Instant scheduledAt,
  String location)` — khai trong gói `notification/` (khuôn `InterviewInvitationResentEvent`, a15), mang **giá trị khung
  vừa chốt** đọc ở bước 5 R-W1. Phát trong transaction C1. Listener `onInterviewSlotConfirmed`:
  `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`, lỗi không văng ra ngoài — khuôn
  `onApplicationWithdrawn` (`NotificationEventListener.java:102-115`): nạp Job → Company → User ứng viên, người nhận
  `company.getOwnerId()`. `notification/` **không** import `interviewinvitation/`.
- **R-N2.** Loại mới `NotificationType.INTERVIEW_SLOT_CONFIRMED`. **Chỉ** chủ công ty nhận; ứng viên **không** nhận (Q7).
  `entity_type = 'APPLICATION'`, `entity_id = applicationId` (qua `save` có sẵn, `:173-186`) — `reset-test-data.sql` xoá
  được (a29).
- **R-N3. Web — một dòng** (Q7), dựng ở `NotificationContentBuilder.forSlotConfirmed(Job job, String candidateName,
  Instant scheduledAt, String location, UUID applicationId, String frontendBaseUrl)` (hàm thuần):
  - `title`: "Ứng viên đã chọn lịch phỏng vấn"
  - `body`: `Ứng viên {tên} đã chọn khung giờ phỏng vấn {HH:mm dd/MM/yyyy} (giờ Việt Nam) cho vị trí "{tiêu đề tin}".`
    — không ký tự xuống dòng, không địa điểm.
  - `link`: `/hr/applications/{applicationId}?tab=invitation`.
- **R-N4. Email** — `emailBody` (ghi vào `notifications.email_body`, gửi nguyên văn qua `mailText`, a16):

  ```
  {body}

  Địa điểm: {location}

  Xem giấy mời tại: {frontendBaseUrl}/hr/applications/{id}?tab=invitation
  ```

  Giờ dùng `SLOT_TIME` có sẵn (`Asia/Ho_Chi_Minh`, a17); `location` `null` → "Chưa có dữ liệu" (`MISSING_LOCATION` — không
  xảy ra với khung ứng viên chọn được, giữ cho hàm thuần không ném lỗi). Tiêu đề email = `title`. `email_body` **không**
  trả ra API thông báo (giữ H12 R-N3a).
- **R-N5.** Đúng **một** thông báo cho một lần chọn thành công; **không** thông báo khi C1 lỗi (mọi mã ở R-W5, R-A, 400).
- **R-N6.** Thông báo không chứa điểm, rubric, giải thích AI, ghi chú HR (CLAUDE.md §8). Không đưa `rendered_content` vào.

### 3.7 K1/C07 — không sửa code (R-K)

- **R-K1.** `ConversationContextAssembler.forConversation` (`:84-89`) đã đọc khung chốt của giấy mời hiệu lực: trước khi
  chọn `interview = null`, sau khi chọn = (`scheduledAt`, `location`) của khung đã chọn. **Không** sửa file này;
  `ConversationContextSignatureTest` không sửa.
- **R-K2.** `MessageDraftFacade.unavailableReason` (`:116-132`): sau khi chọn, `INTERVIEW_REMINDER` (HR) chuyển từ 409
  `NO_ACTIVE_INTERVIEW` sang dùng được; `REQUEST_RESCHEDULE` vẫn dùng được, prompt nhận giờ thật thay cho "chưa có lịch".
  Không sửa file; không đổi prompt hay phiên bản prompt.

### 3.8 Giao diện (R-U, R-H) — chi tiết ở `UI.md`

- **R-U1.** Phía ứng viên: phần chọn chỉ hiện khi đơn `INTERVIEW_INVITED` **và** giấy mời hiệu lực chưa chốt **và** còn ít
  nhất một khung chưa qua (so `scheduledAt` với giờ máy lúc render; backend vẫn là chốt chặn — R-C6).
- **R-U2.** Câu Q8 của H12 (`candidateUnconfirmed`) được thay bằng câu hướng dẫn chọn (UI.md mục 7); mọi khung đã qua → câu
  cố định hướng sang tab "Trao đổi" (Q6), không radio.
- **R-U3.** Giấy mời ≥ 2 khung đã chốt (tức ứng viên đã chọn — H12 không bao giờ chốt giấy mời nhiều khung) và đơn
  `INTERVIEW_INVITED`: phía ứng viên hiện "Bạn đã chọn khung giờ này lúc {confirmedAt}. Muốn đổi lịch, hãy nhắn cho nhà
  tuyển dụng qua tab Trao đổi." Đơn đã có kết quả: không câu (O1).
- **R-H1.** Phía HR (Q9): giấy mời ≥ 2 khung đã chốt hiện "Ứng viên đã chọn lúc {confirmedAt}" — ở giấy mời hiệu lực của tab
  và ở thẻ giấy mời trước đó (mục 11.3, O2). Suy từ `slots.length >= 2 && confirmedSlotId !== null`, không cần trường mới.
- **R-H2.** Sửa **đúng một** câu sai có từ H12 (`invitationLabels.ts:20-21`, M3): "Ứng viên đã chốt khung {t}. …" →
  "Giấy mời hiện tại đã chốt khung {t}. Khung này sẽ không còn hiệu lực sau khi gửi lại." Không sửa câu nào khác của H12.

## 4. Dữ liệu & quyền truy cập

### 4.1 Migration — **không có V17** (N3)

Mô hình V16 đủ (a1, a2):

| Yêu cầu | Chốt ở đâu |
|---|---|
| Khung chốt thuộc chính giấy mời | FK ghép `fk_invitation_confirmed_slot` (DB) + `EXISTS … s.invitation_id = i.id` (câu ghi, R-W4) |
| `confirmed_slot_id`, `confirmed_at` đi cặp | CHECK `chk_invitation_confirmed_pair` (DB) |
| Chốt một lần | Điều kiện `confirmed_slot_id IS NULL` trong **một** câu `UPDATE` — Postgres thực thi nguyên tử trên hàng, lượt đến sau đánh giá lại `WHERE` trên bản đã commit (R-R) |
| Chỉ chốt giấy mời hiệu lực | `superseded_at IS NULL` trong cùng câu |
| Khung chưa qua | `s.scheduled_at > :now` trong cùng câu |
| Ai đã chọn | Không cần cột `confirmed_by`: chỉ chủ đơn chọn được (R-A1), và "giấy mời ≥ 2 khung đã chốt" ⇔ ứng viên đã chọn |

Không trigger cấm sửa `confirmed_*` (dự án chưa có trigger nghiệp vụ ngoài `set_updated_at`).

### 4.2 Package và file backend

Không package mới: `interviewinvitation/` (endpoint, service, repository), `notification/` (sự kiện, listener, builder),
`common/exception/` (mã lỗi).

| File | Thay đổi |
|---|---|
| `interviewinvitation/InterviewInvitationCandidateController.java` | Thêm `@PostMapping("/confirm-slot")` (C1), `@Valid @RequestBody` |
| `interviewinvitation/dto/ConfirmInterviewSlotRequest.java` (mới) | `record ConfirmInterviewSlotRequest(@NotNull(message = "Vui lòng chọn một khung giờ.") UUID slotId)` |
| `interviewinvitation/InterviewInvitationService.java` | `confirmSlot` theo R-W1; tách method private dựng `CandidateInterviewInvitationResponse` (R-W7); không thêm dependency mới |
| `interviewinvitation/InterviewInvitationRepository.java` | `confirmSlotIfAvailable(applicationId, slotId, now)` — native, SQL đúng nguyên văn R-W1 bước 4 |
| `common/exception/SlotSelectionNotAllowedException.java`, `InterviewSlotAlreadyConfirmedException.java`, `InterviewSlotPassedException.java` (mới) | Câu cố định mục 4.4; `INTERVIEW_INVITATION_CONFLICT` cần một exception ném được từ service — `InterviewInvitationConflictException.java` (mới) mang **đúng** câu của nhánh `uq_invitation_active` (`GlobalExceptionHandler.java:446-448`); nhánh đó đổi sang dùng `new InterviewInvitationConflictException().getMessage()` (khuôn `uq_scoring_run_in_progress`, `:438-441`) để hai đường trả cùng một chuỗi |
| `common/exception/GlobalExceptionHandler.java` | Bốn handler mới (409) |
| `notification/NotificationType.java` | `INTERVIEW_SLOT_CONFIRMED` |
| `notification/InterviewSlotConfirmedEvent.java` (mới) | R-N1 |
| `notification/NotificationEventListener.java` | `onInterviewSlotConfirmed` (R-N1) |
| `notification/NotificationContentBuilder.java` | `forSlotConfirmed` (R-N3, R-N4) |

Không sửa: `jobapplication/*` (dùng lại `lockIfInterviewInvited`), `aicontext/*`, `messagedraft/*`,
`NotificationMailOrchestrator.java`, `NotificationResponse`, mọi migration.

### 4.3 Endpoint

| # | Endpoint | Mô tả | Thành công | Lỗi riêng |
|---|---|---|---|---|
| C1 | `POST /api/candidates/applications/{applicationId}/interview-invitation/confirm-slot` | Ứng viên chọn khung (R-W) | 200 `CandidateInterviewInvitationResponse` (I4, không đổi hình dạng) | 400 trường `slotId`; 403; 404 `APPLICATION_NOT_FOUND`; 409 `SLOT_SELECTION_NOT_ALLOWED`; 404 `INTERVIEW_INVITATION_NOT_FOUND`; 409 `INTERVIEW_SLOT_ALREADY_CONFIRMED`; 409 `INTERVIEW_INVITATION_CONFLICT`; 409 `INTERVIEW_SLOT_PASSED` |

Thứ tự kiểm = thứ tự liệt kê ở cột lỗi: Bean Validation (400) → R-A (404/403) → khoá (409 `SLOT_SELECTION_NOT_ALLOWED`) →
R-W5 dòng 1–5. Không thêm `PUT`/`PATCH`/`DELETE` nào (Q1).

### 4.4 Mã lỗi (câu tiếng Việt cố định)

| Mã | HTTP | `message` |
|---|---|---|
| (Bean Validation) | 400 | `{"slotId": "Vui lòng chọn một khung giờ."}` — định dạng map trường có sẵn (a26) |
| `SLOT_SELECTION_NOT_ALLOWED` | 409 | "Chỉ chọn được khung giờ khi đơn đang ở trạng thái Đã mời phỏng vấn." |
| `INTERVIEW_INVITATION_NOT_FOUND` | 404 | Có sẵn — "Đơn ứng tuyển chưa có giấy mời phỏng vấn: {id}" |
| `INTERVIEW_SLOT_ALREADY_CONFIRMED` | 409 | "Giấy mời này đã được chốt khung giờ. Muốn đổi lịch, hãy nhắn cho nhà tuyển dụng qua tab Trao đổi." |
| `INTERVIEW_INVITATION_CONFLICT` | 409 | Có sẵn — "Giấy mời của đơn này vừa được thay đổi ở nơi khác. Vui lòng tải lại trang." |
| `INTERVIEW_SLOT_PASSED` | 409 | "Khung giờ này đã qua. Vui lòng chọn khung giờ khác." |

Bốn exception mới đều mang câu cố định trong constructor, không nhận `String` tự do (khuôn
`InvitationResendNotAllowedException`). Không ghi cột lỗi nào xuống DB nên không cần `FormattedErrorCode`.

### 4.5 DTO

```java
// C1 - than request. KHONG id giay moi: backend tu xac dinh giay moi hieu luc duoi khoa (R-W2).
record ConfirmInterviewSlotRequest(@NotNull(message = "Vui lòng chọn một khung giờ.") UUID slotId) {}

// Response C1 = CandidateInterviewInvitationResponse cua I4 - KHONG doi hinh dang (H12 muc 4.4).
```

Không field nào tên `verdict`, `label`, `isQualified`, `passed`, `recommendation` (CLAUDE.md §7).

### 4.6 Seed kiểm thử tay — `db/seed/seed-test.sql` (Q10)

Sửa ở đợt 3. **Dải UUID mới `f1`** cho mọi dòng của U10 (comment đầu file "e0..ef, f0" → "e0..ef, f0, f1";
`db/seed/README.md:255` sửa theo). Mọi câu `ON CONFLICT (id) DO NOTHING`, idempotent.

| Bảng | id | Nội dung |
|---|---|---|
| `job_applications` | `f1000000-0000-0000-0000-000000000001` (A10) | Lê Thị Lan (`e0…03`) → J2 "Nhân viên Hành chính văn phòng" (`e2…02`, OPEN, 0 câu sàng lọc), CV R4 (`e7…04`), `recruitment_cycle` 1, `INTERVIEW_INVITED`, `ai_consent` TRUE, `applied_at` `2026-10-08 03:00:00+00`, `updated_at` `2026-10-09 02:00:00+00`, `screening_captured_at` = `applied_at` (tin không có câu hỏi → `NO_QUESTIONS`, như A2), `talent_pool_consent` FALSE |
| `application_status_history` | `f1…11`, `f1…12` | `NULL → PENDING` (Lan, `2026-10-08 03:00`); `PENDING → INTERVIEW_INVITED` (HR test, `2026-10-09 02:00`) |
| `interview_invitations` | `f1…21` | Đơn A10, tiêu đề/nội dung theo mẫu J2 với "Kính gửi Lê Thị Lan", `sent_at` = `created_at` = `2026-10-09 02:00:00+00`, `sent_by` HR test, `superseded_at` NULL, `confirmed_*` NULL |
| `interview_invitation_slots` | `f1…31` (thứ tự 1) | **Đã qua**: `date_trunc('day', now()) - INTERVAL '1 day' + INTERVAL '2 hours'` — "Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng" |
| | `f1…32` (thứ tự 2) | `date_trunc('day', now()) + INTERVAL '9 days 2 hours'` — "Phòng họp Sông Hàn, tầng 5" |
| | `f1…33` (thứ tự 3) | `date_trunc('day', now()) + INTERVAL '10 days 7 hours'` — "https://meet.example.com/lan-u10-test" |
| `notifications` | `f1…41` | HR test, `APPLICATION_SUBMITTED`, "Ứng viên Lê Thị Lan vừa ứng tuyển vị trí "Nhân viên Hành chính văn phòng"", link `/hr/applications/f1…01`, **đã đọc** (giữ số chưa đọc của HR test), `SKIPPED` |
| | `f1…42` | Lan, `APPLICATION_STATUS_CHANGED`, "… đã chuyển sang trạng thái: Đã mời phỏng vấn", link `/candidate/applications/f1…01`, chưa đọc, `SKIPPED` |

- **Ngoại lệ có chủ đích với H12 mục 4.5** ("seed không ghi dữ liệu mà API mới không tạo được"): khung `f1…31` ở quá khứ.
  API không tạo **ngay** được khung quá khứ (H12 R-S4), nhưng mọi giấy mời đều đi tới trạng thái này khi thời gian trôi
  qua — đây là trạng thái thật U10 phải xử lý (R-C6, Q6). Khung đã qua tính tương đối theo lúc nạp; nạp từ 11/10/2026 trở
  đi thì khung này luôn sau `sent_at` (`2026-10-09 02:00`). Comment tại chỗ ghi rõ ngoại lệ.
- Không thêm thông báo `INTERVIEW_SLOT_CONFIRMED` nào (chưa ai chọn).
- Giữ nguyên A9 (`ea…02`, 3 khung chưa chốt) và mọi dòng khác.
- **Câu đếm cuối file**: comment thêm "và dải f1: job_applications 1, application_status_history 2, interview_invitations
  1, interview_invitation_slots 3, notifications 2"; thêm năm dòng `UNION ALL SELECT … LIKE 'f1000000-%'` theo từng bảng.
  Số đếm của dải cũ không đổi.
- **`reset-test-data.sql` không cần sửa**: A10 thuộc user test (`t_apps` theo `candidate_id`, a29) → thông báo (`user_id`
  test và `entity_id` đơn test), giấy mời (khung theo `CASCADE`), lịch sử, đơn bị xoá. Đợt 3 kiểm thật: reset → nạp lại →
  câu đếm đúng; reset lần hai → `SELECT count(*) FROM job_applications WHERE id::text LIKE 'f1000000-%'` = 0.
- `db/seed/README.md` (đợt 3, cùng commit seed): mục 8 dải tiền tố; mục 8.3 ghi chú Lê Thị Lan "có đơn A10 đã mời phỏng
  vấn, giấy mời 3 khung chưa chốt (1 khung đã qua)"; mục 8.4 thêm dòng soát FR-U10; dòng "Thông báo đã đọc / chưa đọc"
  của HR test thành **7 / 4** (11 thông báo: thêm `f1…41` đã đọc, số chưa đọc giữ 4); Lê Thị Lan **1 chưa đọc** (`f1…42`,
  trước đó 0). Đợt 3 đếm lại bằng SQL sau khi nạp để xác nhận.

## 5. AI

Không dùng AI. Không gọi `ChatModel`/`EmbeddingModel`, không thuộc danh sách K3. Ảnh hưởng tới C07 chỉ là **dữ liệu** K1
thay đổi khi ứng viên chọn (R-K); không đổi prompt, không đổi phiên bản prompt.

## 6. Ngoài phạm vi

- Ứng viên đổi hoặc huỷ khung đã chọn (Q2); HR chốt hộ ứng viên một khung (vẫn dùng "Gửi lại" chỉ với khung đó — H12).
- Giữ chỗ khung giữa các ứng viên; kiểm trùng lịch giữa các giấy mời hay với lịch HR — hai ứng viên khác đơn có thể cùng
  chọn một giờ (README:576, H12 mục 6).
- Thời gian báo trước tối thiểu (Q5); nhắc lịch tự động; đồng bộ lịch, tệp `.ics`.
- Thông báo cho ứng viên khi chính họ chọn (Q7); thông báo cho HR khi mọi khung đã qua mà ứng viên chưa chọn (cần job nền).
- **Giới hạn đã biết — chọn trùng lúc HR gửi lại** (Q8, R-R1): cả hai thứ tự đều hợp lệ; HR đang mở hộp thoại "Gửi lại"
  không được báo ứng viên vừa chọn. Muốn báo phải đổi I3 (nhận phiên bản giấy mời mong đợi) — sửa hành vi H12, ngoài phạm vi.
- **Giới hạn đã biết — giờ máy ứng viên**: giao diện ẩn radio của khung đã qua theo giờ máy; máy lệch giờ thì có thể hiện
  khung đã qua là chọn được, backend trả 409 `INTERVIEW_SLOT_PASSED` (R-C6) và giao diện tải lại.
- **Giới hạn đã biết — `slotId` sai định dạng UUID**: body không đọc được rơi vào nợ có sẵn "body JSON hỏng trả 401 trên
  server thật" (`ROADMAP.md:906-907`), không sửa ở U10.
- Sửa thông báo/email lần mời đầu (H12 M6), múi giờ nhập của HR (H12 B2), địa điểm dạng link bấm được (H12 B4).

## 7. Xong khi

### 7.1 Test backend (Testcontainers, full `./mvnw test` xanh)

Lớp test: `InterviewSlotConfirmControllerIntegrationTest` (mới, `@Transactional` cấp lớp — T1–T5, T12, T13);
`InterviewSlotConfirmNotificationIntegrationTest` (mới, **không** `@Transactional` cấp lớp vì listener chạy
`AFTER_COMMIT` — T10, khuôn `InterviewInvitationResendIntegrationTest`); `InterviewSlotConfirmConcurrencyIntegrationTest`
(mới, **không** `@Transactional` cấp lớp — CLAUDE.md §3c; khuôn `InterviewInvitationResendConcurrencyIntegrationTest` —
T6–T8); thêm ca vào `ConversationContextAssemblerIntegrationTest` (T9), `NotificationContentBuilderTest` (T11),
`CandidateApplicationCrossEndpointAccessIntegrationTest` (T12). Mọi giấy mời nhiều khung tạo qua I2 (HTTP) với khung tương
lai; khung quá khứ đặt bằng native `UPDATE interview_invitation_slots SET scheduled_at = …` sau khi tạo (bảng không có
trigger; entity `@Immutable` chỉ chặn ghi qua ORM).

| # | Test | Loại |
|---|---|---|
| T1 | Giấy mời 3 khung, chọn khung 2 → 200; JSON đúng hình dạng I4 (gốc không `id`/`applicationId`/`sentBy`/`supersededAt`), `confirmedAt` khác null, đúng một phần tử `confirmed = true` là khung 2. DB: `confirmed_slot_id` = khung 2, `confirmed_at` trong [trước request, sau request] và > `sent_at`; mọi cột khác của giấy mời và cả 3 khung giữ nguyên (so từng cột); đọc toàn hàng `job_applications` bằng `JdbcTemplate` trước/sau → giống hệt trừ `updated_at` (R-S2); số dòng `application_status_history` không đổi; I1 phía HR trả `confirmedSlotId` = khung 2 | dương |
| T2 | Biên thời gian (R-C6) trên khung 1 của giấy mời 3 khung (đặt bằng native update): `now − 1 phút` → 409 `INTERVIEW_SLOT_PASSED`; `scheduled_at` = `Instant.now()` đọc ngay trước request (lúc service chạy `now` ≥ giá trị này) → 409 `INTERVIEW_SLOT_PASSED`; `now + 1 phút` → 200. Mỗi ca 409: `confirmed_*` vẫn NULL. Ca "đúng now" thực chất kiểm `scheduled_at ≤ now`: giá trị ghi trước request, `now` của service luôn ≥ giá trị đó — kết quả xác định; **không** inject `Clock` (dự án chưa có tiền lệ) | biên + âm |
| T3 | Mọi mã lỗi, mỗi ca khẳng định `code`, `message` đúng mục 4.4, DB không đổi (`confirmed_*` NULL nếu trước đó NULL), không thông báo: (a) `SLOT_SELECTION_NOT_ALLOWED` với đơn `PENDING` (chưa có giấy mời), `HIRED`, `REJECTED`, `WITHDRAWN` (ba đơn sau có giấy mời 3 khung chưa chốt); (b) 404 `INTERVIEW_INVITATION_NOT_FOUND`: đơn `INTERVIEW_INVITED` không có giấy mời (đặt trạng thái bằng SQL); (c) `INTERVIEW_SLOT_ALREADY_CONFIRMED`: giấy mời 1 khung (tự chốt), chọn đúng khung đó; giấy mời 3 khung đã chọn khung 2 rồi chọn khung 3; rồi chọn lại khung 2 — `confirmed_slot_id` vẫn khung 2, `confirmed_at` không đổi; (d) `INTERVIEW_INVITATION_CONFLICT`: khung của giấy mời cũ (sau I3 với 2 khung), khung của đơn ứng viên khác, `UUID.randomUUID()` — không ca nào 500; (e) 400 `{"slotId": "Vui lòng chọn một khung giờ."}` với `{"slotId": null}` và `{}`; (f) 403 đơn của ứng viên khác; 404 `APPLICATION_NOT_FOUND` đơn không tồn tại; token HR → 403; không token → 401 | âm |
| T4 | Thứ tự kiểm (R-W5, mục 4.3): đơn `HIRED` + giấy mời chưa chốt + khung đã qua → `SLOT_SELECTION_NOT_ALLOWED` (khoá trước); giấy mời hiệu lực đã chốt + `slotId` của giấy mời cũ → `INTERVIEW_SLOT_ALREADY_CONFIRMED` (dòng 2 trước dòng 3); `slotId` là khung **đã qua** của đơn khác → `INTERVIEW_INVITATION_CONFLICT` (dòng 3 trước dòng 4); đơn của ứng viên khác ở trạng thái `HIRED` → 403 (quyền trước khoá) | âm |
| T5 | Tin không ảnh hưởng (R-C7, Q13): đặt tin `CLOSED` rồi chọn → 200; đặt `deleted_at` của tin rồi chọn → 200 | dương |
| T6 | Hai C1 song song cùng đơn (cùng khung, và khác khung — mỗi kiểu lặp 10 lần trên 10 đơn): đúng một 200, một 409 `INTERVIEW_SLOT_ALREADY_CONFIRMED`; không 500; khung chốt là khung của lượt 200; đúng **một** thông báo `INTERVIEW_SLOT_CONFIRMED` mỗi đơn — đếm ngay sau khi hai luồng xong, **không** vòng thăm: listener `AFTER_COMMIT` chạy đồng bộ trên luồng commit trước khi method service trả về (tiền lệ H12 T10a; mục 13, L3) | âm |
| T7 | C1 song song với I3 (gửi lại 2 khung), lặp 10 lần trên 10 đơn — mỗi lần là một trong hai: {C1 200, I3 201: giấy mời cũ có `superseded_at` **và** `confirmed_slot_id` = khung đã chọn, giấy mời mới chưa chốt} hoặc {I3 201, C1 409 `INTERVIEW_INVITATION_CONFLICT`: giấy mời cũ `confirmed_*` NULL}; không 500; mỗi đơn đúng một giấy mời hiệu lực. **Thêm ca đơn luồng chắc chắn** (khuôn U09 T8, mục 13 L4): transaction A (`TransactionTemplate`) gọi `lockIfInterviewInvited` trên đơn, **giữ chưa commit**; luồng B gọi `confirmSlot`; A chờ tới khi B bị chặn — xác nhận bằng `pg_stat_activity` (trước mỗi lần thăm `SELECT pg_stat_clear_snapshot()`; điều kiện `datname = current_database() AND pid <> pg_backend_pid() AND wait_event_type = 'Lock' AND query ILIKE '%SET status = status%'`; thăm mỗi 50 ms, hạn 10 s, hết hạn thì đỏ) và khẳng định `futureB.isDone() == false`; A đánh dấu giấy mời hiện tại `superseded_at` + chèn giấy mời mới **2 khung, chưa chốt** (helper SQL — giấy mời 1 khung tự chốt sẽ làm B nhận `INTERVIEW_SLOT_ALREADY_CONFIRMED` theo R-W5 dòng 2) rồi commit → B xong trong 5 s với `INTERVIEW_INVITATION_CONFLICT`. Ca ngược: A chỉ khoá rồi commit không đổi gì → B 200 | âm |
| T8 | C1 song song với `PATCH /status` `HIRED` (và `REJECTED`), lặp 10 lần trên 10 đơn — {C1 200, PATCH 200} hoặc {PATCH 200, C1 409 `SLOT_SELECTION_NOT_ALLOWED`}; không 500; trạng thái cuối `HIRED`/`REJECTED`. Như vậy với rút đơn phía ứng viên → {C1 200, rút 200} hoặc {rút 200, C1 409}. Ca đơn luồng **thuận** (mục 13, L5): transaction A gọi `lockIfInterviewInvited` rồi `updateStatusIfCurrent(…, INTERVIEW_INVITED, HIRED)` trong cùng transaction, **giữ chưa commit**; luồng B gọi `confirmSlot`; xác nhận B bị chặn bằng `pg_stat_activity` như T7 và `futureB.isDone() == false`; A commit → B xong trong 5 s với 409 `SLOT_SELECTION_NOT_ALLOWED`, đơn `HIRED`, `confirmed_*` vẫn NULL. Ca **ngược**: `HIRED` commit trước → `confirmSlot` 409 `SLOT_SELECTION_NOT_ALLOWED`. (Ca "khoá của A chặn `updateStatusIfCurrent` của B" đã có ở H12 T10(d), không viết lại.) | âm |
| T9 | K1 (R-K1): giấy mời 3 khung chưa chốt → `forConversation(...).interview()` = `null`; sau C1 khung 2 → `interview` = (`scheduledAt`, `location`) của khung 2 (so đúng giá trị, không phải khung sớm nhất). `ConversationContextSignatureTest` không sửa, xanh | dương + âm |
| T10 | Thông báo (R-N): sau C1 200 → đúng **một** dòng `INTERVIEW_SLOT_CONFIRMED`, `user_id` = chủ công ty, `entity_type = 'APPLICATION'`, `entity_id` = id đơn, `link` = `/hr/applications/{id}?tab=invitation`, `email_status = 'PENDING'`, `title` đúng, `body` đúng câu R-N3 (giờ VN của khung đã chọn, không `\n`, không địa điểm), `email_body` đúng nguyên văn khối R-N4 (`app.frontend-base-url` của profile test); ứng viên **không** có thông báo mới; JSON `GET /api/notifications` của HR không có khoá `emailBody`. Sau một lượt C1 409 (`ALREADY_CONFIRMED`) và một lượt 409 (`PASSED`) → số thông báo không đổi | dương + âm |
| T11 | `NotificationContentBuilder.forSlotConfirmed` (test đơn vị): khung `2026-10-20T18:30:00Z` → "01:30 21/10/2026" trong `body` và `emailBody`; `location` `null` → "Địa điểm: Chưa có dữ liệu"; `body` không chứa `\n`; dòng cuối đúng URL; các hàm cũ (gồm `forInvitationResent`) không đổi (test có sẵn xanh) | dương |
| T12 | Quyền chéo: thêm C1 vào `CandidateApplicationCrossEndpointAccessIntegrationTest.otherCandidate_sameApplicationAcrossEndpoints_404ExceptInvitation403` — ứng viên khác → 403 (cùng quy ước "invitation 403"), đơn không đổi | âm |
| T13 | Bất biến route: `PUT`/`PATCH`/`DELETE` lên `…/interview-invitation/confirm-slot` → 404 hoặc 405, không 2xx | âm |

Kiểm tĩnh (đợt cuối, kỳ vọng như ghi):

```bash
rg -n "changeStatus" backend/src/main/java/com/recruitment/interviewinvitation        # dung 1 dong (I2) - R-S1
rg -n "ApplicationStatusRecorder|ApplicationStatusChangedEvent" backend/src/main/java/com/recruitment/interviewinvitation   # 0 dong
rg -n "FOR SHARE|FOR UPDATE|@Lock" backend/src/main/java/com/recruitment/interviewinvitation backend/src/main/java/com/recruitment/jobapplication/JobApplicationRepository.java   # 0 dong - R-W3
rg -n "interviewinvitation" backend/src/main/java/com/recruitment/notification      # 0 dong - R-N1
rg -n "findByApplicationIdOrderByCreatedAtDesc" backend/src/main -g "!**/scoring/**"  # 1 dong (khai bao) - giu ky vong H12
rg -n "confirmed_slot_id" backend/src/main/java                                      # 4 dong (L15): comment + @Column entity, SET + WHERE cua confirmSlotIfAvailable
rg -n -i "emailBody|email_body" backend/src/main/java/com/recruitment/notification/dto   # 0 dong
rg -n -i -w "verdict|isQualified|passed|recommendation" backend/src/main/java/com/recruitment/interviewinvitation   # 0 dong (-w: xem ghi chu duoi)
git diff --stat main -- backend/src/main/resources/db/migration                      # 0 file - N3, khong V17
git diff --stat main -- backend/src/main/java/com/recruitment/aicontext backend/src/main/java/com/recruitment/messagedraft backend/src/main/java/com/recruitment/jobapplication   # 0 file - R-K, R-S1
```

Ghi chú: không có `-w`, `passed` khớp tên `InterviewSlotPassedException`/mã `INTERVIEW_SLOT_PASSED` — không phải vi phạm
CLAUDE.md §7 (§7 cấm **cột/field** tên `passed`). Có `-w` thì hai tên đó không khớp (`_` và chữ liền là ký tự từ); dòng nào
vẫn khớp thì báo cáo đợt liệt kê kèm lý do (khuôn H12 S6) — xem mục 11.3, O6. Lệnh `exception` cũng chạy trên
`backend/src/main/java/com/recruitment/common/exception` với `-w`, kỳ vọng 0 dòng.

### 7.2 Kiểm bằng HTTP — Windows PowerShell 5.1

Nạp seed test mục 4.6 (sau `reset-test-data.sql`). Backend ở `localhost:8080`. Dán cả khối vào **một** cửa sổ PowerShell.
Bộ hàm `Get-Token` / `Invoke-Api` / `Test-Call` chép **nguyên văn** từ FR-H12 mục 7.2 (gửi/đọc UTF-8).

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

$tokenHr  = Get-Token 'minhdathr@gmail.com' '12345678'
$tokenLan = Get-Token 'le.thi.lan@test.local' '12345678'
$tokenUv  = Get-Token 'quochuyuv@gmail.com' '12345678'

$a10  = '/api/candidates/applications/f1000000-0000-0000-0000-000000000001'  # Lan, 3 khung chua chot (khung 1 da qua)
$a2   = '/api/candidates/applications/e8000000-0000-0000-0000-000000000002'  # Quoc Huy, 1 khung da chot
$a4   = '/api/candidates/applications/e8000000-0000-0000-0000-000000000004'  # Quoc Huy, HIRED
$a1   = '/api/candidates/applications/e8000000-0000-0000-0000-000000000001'  # Quoc Huy, PENDING
$none = '/api/candidates/applications/00000000-0000-0000-0000-000000000000'
$slotPast = 'f1000000-0000-0000-0000-000000000031'
$slot2    = 'f1000000-0000-0000-0000-000000000032'
$slot3    = 'f1000000-0000-0000-0000-000000000033'
$slotA9   = 'f0000000-0000-0000-0000-000000000005'   # khung cua giay moi A9 (don khac)

'--- 1. Duong: Lan doc giay moi A10 (mong doi 200, 3 khung, confirmedAt null)'
Test-Call 'GET' "$a10/interview-invitation" $tokenLan $null
'--- 2. Am: chon khung da qua (mong doi 409 INTERVIEW_SLOT_PASSED)'
Test-Call 'POST' "$a10/interview-invitation/confirm-slot" $tokenLan @{ slotId = $slotPast }
'--- 3. Am: chon khung cua giay moi don khac (mong doi 409 INTERVIEW_INVITATION_CONFLICT)'
Test-Call 'POST' "$a10/interview-invitation/confirm-slot" $tokenLan @{ slotId = $slotA9 }
'--- 4. Am: thieu slotId (mong doi 400 {"slotId":"Vui lòng chọn một khung giờ."})'
Test-Call 'POST' "$a10/interview-invitation/confirm-slot" $tokenLan '{}'
'--- 5. Am: Quoc Huy chon tren don cua Lan (mong doi 403); HR goi (403); khong token (401); don khong ton tai (404 APPLICATION_NOT_FOUND)'
Test-Call 'POST' "$a10/interview-invitation/confirm-slot" $tokenUv @{ slotId = $slot2 }
Test-Call 'POST' "$a10/interview-invitation/confirm-slot" $tokenHr @{ slotId = $slot2 }
Test-Call 'POST' "$a10/interview-invitation/confirm-slot" $null @{ slotId = $slot2 }
Test-Call 'POST' "$none/interview-invitation/confirm-slot" $tokenLan @{ slotId = $slot2 }
'--- 6. Duong: Lan chon khung 2 (mong doi 200, confirmedAt khac null, khung 2 confirmed=true)'
Test-Call 'POST' "$a10/interview-invitation/confirm-slot" $tokenLan @{ slotId = $slot2 }
'--- 7. Am: chon lan hai - khung 3, roi lai khung 2 (mong doi 409 INTERVIEW_SLOT_ALREADY_CONFIRMED ca hai)'
Test-Call 'POST' "$a10/interview-invitation/confirm-slot" $tokenLan @{ slotId = $slot3 }
Test-Call 'POST' "$a10/interview-invitation/confirm-slot" $tokenLan @{ slotId = $slot2 }
'--- 8. Duong: trang thai don van INTERVIEW_INVITED, lich su van 2 moc'
$r = Invoke-Api 'GET' $a10 $tokenLan $null
'{0} don A10 van INTERVIEW_INVITED: {1}' -f $r.Status, $r.Text.Contains('"status":"INTERVIEW_INVITED"')
Test-Call 'GET' "$a10/history" $tokenLan $null
'--- 9. Duong: HR doc I1 (confirmedSlotId = khung 2) va thong bao moi nhat (INTERVIEW_SLOT_CONFIRMED, khong co emailBody)'
$r = Invoke-Api 'GET' '/api/hr/applications/f1000000-0000-0000-0000-000000000001/interview-invitations' $tokenHr $null
'{0} I1 A10 co confirmedSlotId khung 2: {1}' -f $r.Status, $r.Text.Contains('"confirmedSlotId":"' + $slot2 + '"')
$r = Invoke-Api 'GET' '/api/notifications?page=0&size=1' $tokenHr $null
'{0} thong bao moi nhat la INTERVIEW_SLOT_CONFIRMED: {1}; co emailBody: {2}' -f $r.Status, $r.Text.Contains('INTERVIEW_SLOT_CONFIRMED'), $r.Text.Contains('emailBody')
'--- 10. Am: giay moi 1 khung da chot A2 (409 INTERVIEW_SLOT_ALREADY_CONFIRMED); don HIRED A4 va PENDING A1 (409 SLOT_SELECTION_NOT_ALLOWED)'
Test-Call 'POST' "$a2/interview-invitation/confirm-slot" $tokenUv @{ slotId = 'f0000000-0000-0000-0000-000000000004' }
Test-Call 'POST' "$a4/interview-invitation/confirm-slot" $tokenUv @{ slotId = 'f0000000-0000-0000-0000-000000000003' }
Test-Call 'POST' "$a1/interview-invitation/confirm-slot" $tokenUv @{ slotId = $slot2 }
```

Sau khối: kiểm email trong MailHog (`localhost:8025`) — thông báo `INTERVIEW_SLOT_CONFIRMED` tạo qua API có `email_status
= 'PENDING'` nên poller gửi thật tới MailHog: tiêu đề "Ứng viên đã chọn lịch phỏng vấn", nội dung đúng khối R-N4. Muốn
chạy lại khối: `reset-test-data.sql` + nạp lại seed.

### 7.3 Frontend

- `npm run build` + `npm run lint` sạch.
- Tên file theo `UI.md` mục 5.
- `git diff --stat main -- frontend/src/features/messageDraft frontend/src/features/applicationDetail/ApplicationActionBar.tsx frontend/src/components/ui`
  → 0 file.
- `git diff main -- frontend/src/features/interviewinvitation/invitationLabels.ts`: chỉ thêm khoá mới, thay `candidateUnconfirmed`
  (R-U2) và `resendConfirmedNotice` (R-H2); không dòng nào khác của H12 đổi.

### 7.4 Soát tay (desktop ≥ `lg`, 768px, 375px) — seed test mục 4.6

| Trạng thái cần thấy | Tài khoản | Nơi | Mong đợi |
|---|---|---|---|
| Có thể chọn, một khung đã qua | `le.thi.lan@test.local` / `12345678` | `/candidate/applications/f1000000-0000-0000-0000-000000000001` | Câu hướng dẫn chọn; 3 khung theo thứ tự, khung 1 có radio khoá + chữ "Đã qua"; khung 2, 3 chọn được; nút "Xác nhận khung giờ" |
| Bấm xác nhận khi chưa chọn | Lan | như trên | Câu "Vui lòng chọn một khung giờ." dưới danh sách, focus vào radio đầu tiên chọn được; không gọi API |
| Hộp xác nhận | Lan | Chọn khung 2 → "Xác nhận khung giờ" | Tiêu đề, khung đã chọn (giờ VN + địa điểm), câu "không tự đổi được"; "Huỷ" đóng, không đổi gì |
| Xác nhận thành công | Lan | Hộp thoại → "Xác nhận" | Hộp thoại đóng; radio biến mất; khung 2 có dấu "Đã chốt"; câu "Bạn đã chọn khung giờ này lúc …"; badge trạng thái đơn không đổi; tab "Lịch sử" không thêm mốc |
| HR nhận thông báo | `minhdathr@gmail.com` | Chuông → thông báo mới | "Ứng viên đã chọn lịch phỏng vấn", một dòng có giờ; bấm mở `/hr/applications/f1…01?tab=invitation` |
| Tab HR sau khi chọn | HR test | như trên | Khung 2 "Đã chốt", dòng "Ứng viên đã chọn lúc …"; không còn câu "Ứng viên chưa chọn khung giờ…" |
| Soạn nháp C07 sau khi chọn | HR test | A10 tab "Trao đổi" → soạn bằng AI | "Nhắc lịch phỏng vấn" dùng được (trước khi chọn: khoá, lý do `NO_ACTIVE_INTERVIEW`) — soát **trước** dòng "Xác nhận thành công" một lần, sau một lần |
| Câu sửa của H12 | HR test | A2 `…02?tab=invitation` → "Gửi lại giấy mời" | Khối lưu ý: "Giấy mời hiện tại đã chốt khung … Khung này sẽ không còn hiệu lực sau khi gửi lại." |
| Gửi lại sau khi ứng viên đã chọn | HR test | A10 → "Gửi lại giấy mời" 2 khung | "Giấy mời trước đó (1)" mở ra có khung 2 "Đã chốt" + "Ứng viên đã chọn lúc …"; phía Lan: giấy mời mới chưa chốt, có radio |
| Giấy mời 1 khung | `quochuyuv@gmail.com` | `/candidate/applications/e8000000-0000-0000-0000-000000000002` | Không radio, không nút, không câu "Bạn đã chọn…" (khung tự chốt), dấu "Đã chốt" |
| Đơn đã có kết quả | Quốc Huy | `…/e8000000-0000-0000-0000-000000000004` | Danh sách chỉ đọc như H12 |
| Mọi khung đã qua (Q6, O5) | Lan | Trên DB seed sạch (chưa chọn khung), chạy trên DB dev: `docker compose exec -T postgres psql -U recruitment -d recruitment -c "UPDATE interview_invitation_slots SET scheduled_at = date_trunc('minute', now()) - CASE id WHEN 'f1000000-0000-0000-0000-000000000032' THEN INTERVAL '2 hours' ELSE INTERVAL '1 hour' END WHERE id IN ('f1000000-0000-0000-0000-000000000032', 'f1000000-0000-0000-0000-000000000033');"` (hai mốc khác nhau — mục 13, L2) rồi tải lại `/candidate/applications/f1000000-0000-0000-0000-000000000001`. **Ngay sau khi soát**, đưa seed về gốc: `reset-test-data.sql` (lệnh `db/seed/README.md` mục 8.2) → `seed-test.sql` → `.\db\seed\install-test-files.ps1` (mục 8.1) | Không radio, không nút; câu "Các khung giờ trong giấy mời đã qua…" |
| Tab khác vừa chọn (409) | Lan, hai tab | Tab 1 xác nhận khung 2; tab 2 (chưa tải lại) xác nhận khung 3 | Hộp thoại tab 2 hiện câu backend `INTERVIEW_SLOT_ALREADY_CONFIRMED`; đóng hộp thoại thấy trạng thái đã chốt khung 2 |
| A9 (giữ nguyên) | `tran.bao.ngoc@test.local` (qua onboarding) | `…/e8000000-0000-0000-0000-000000000009` | 3 khung chọn được, không khung đã qua |
| 375px | cả hai | Các màn trên | Không cuộn ngang toàn trang; mỗi khung một dòng radio + giờ, địa điểm dòng dưới; nút "Xác nhận khung giờ" `w-full`; vùng chạm radio ≥ 48px |

## 8. AI hay làm sai (dành cho người code)

- Gọi `applicationStatusService.changeStatus`, ghi `application_status_history` hay phát `ApplicationStatusChangedEvent`
  khi chọn khung; thêm trạng thái mới kiểu "đã chốt lịch" (R-S1, CLAUDE.md §2 — đúng 5 trạng thái).
- **Đọc giấy mời hiệu lực trước khi khoá** (vd `findByApplicationIdAndSupersededAtIsNull` ở bước kiểm quyền "để trả 404
  sớm"), rồi ghi theo id đã đọc (R-W2).
- Nạp entity giấy mời rồi `setConfirmedSlotId` + `flush` thay cho câu `UPDATE` có điều kiện; hoặc kiểm "đã chốt chưa" bằng
  Java rồi mới ghi không điều kiện (R-W3) — mất chốt nguyên tử khi bấm đúp.
- Dùng `SELECT … FOR UPDATE`/`FOR SHARE`/`@Lock` (R-W3).
- Lấy `Instant.now()` hai lần (một cho điều kiện "chưa qua", một cho `confirmed_at`), hoặc dùng `now()` của Postgres (R-W1
  bước 3).
- So "chưa qua" bằng `>=` (khung đúng `now` phải bị chặn), hoặc cộng thời gian báo trước (Q5).
- Bỏ điều kiện `s.invitation_id = i.id` trong `EXISTS`, để FK ghép tự chặn → 500 (R-W4); hoặc thêm nhánh dịch
  `fk_invitation_confirmed_slot` sang 409 thay cho việc chặn ở câu ghi.
- Trả mã khác nhau cho "khung không tồn tại" và "khung của đơn khác" — lộ dữ liệu (R-W5 dòng 3).
- Coi chọn lại đúng khung đã chốt là 200 "idempotent" (Q3).
- Thêm endpoint đổi/huỷ lựa chọn, hay cho ứng viên chọn khi giấy mời 1 khung (R-W6, R-C4).
- Kiểm trạng thái tin (`CLOSED`, `deleted_at`) và chặn chọn (R-C7, Q13).
- Gửi thông báo cho ứng viên; tạo thông báo khi C1 lỗi; tạo hai thông báo khi bấm đúp (R-N2, R-N5).
- Để listener `notification/` truy vấn bảng giấy mời/khung thay vì dùng giá trị trong sự kiện (R-N1, kiểm tĩnh 7.1).
- Đưa địa điểm hay xuống dòng vào `body` web; định dạng giờ theo múi giờ server (R-N3, R-N4).
- Thêm field `id` giấy mời / `applicationId` vào DTO I4, hay đổi hình dạng response cho C1 (R-W7).
- Thêm migration (V17), cột `confirmed_by`, trigger (N3, mục 4.1).
- Sửa `ConversationContextAssembler`/`MessageDraftFacade` "cho chắc" (R-K).
- Viết lại các câu khác của H12 ngoài `resendConfirmedNotice` (R-H2); hardcode "giấy mời 1 khung" bằng cách so `confirmedAt
  === sentAt` thay cho `slots.length` (R-U3, R-H1).
- Tô xanh khung đã chọn, đỏ/xám mờ khung đã qua; dùng màu làm tín hiệu duy nhất (CLAUDE.md §8, UI.md mục 10).
- Chọn khung ngay khi bấm radio mà không qua hộp xác nhận (Q11).

## 9. Kế hoạch đợt code

Mỗi đợt: code xong → chạy kiểm ở cột Kiểm → dừng → báo cáo diff → chờ duyệt → một commit. Đợt 2–5 **không** chạy full
suite: backend `.\mvnw.cmd test-compile` rồi chỉ các lớp test của đợt bằng `-Dtest`; frontend `npm run build` + `npm run
lint`. Full `.\mvnw.cmd test` đúng **một** lần ở đợt 6.

| Đợt | Nội dung | Kiểm |
|---|---|---|
| 1 | Plan Mode: đối chiếu lại mục 0 trên HEAD lúc đó; chốt danh sách file; soát SQL R-W1 bước 4 (in ra để duyệt — quy ước dự án) và chạy thử câu đó trên Postgres dev với ba ca (khung hợp lệ, khung đã qua, khung giấy mời khác) | — |
| 2 | Backend chọn khung: `ConfirmInterviewSlotRequest`, C1 trên controller, `confirmSlot` (R-W1 bước 1–5, **chưa** phát sự kiện), `confirmSlotIfAvailable`, bốn exception + handler, tách method dựng DTO I4; T1–T5, T12, T13, T7–T8 (phần không cần thông báo), T9 | test-compile; `-Dtest` `InterviewSlotConfirm*IntegrationTest`, `InterviewInvitationCandidateControllerIntegrationTest`, `InterviewInvitationControllerIntegrationTest`, `InterviewInvitationResend*IntegrationTest`, `CandidateApplicationCrossEndpointAccessIntegrationTest`, `ConversationContext*Test`, `MessageDraft*Test` |
| 3 | Thông báo + seed: `INTERVIEW_SLOT_CONFIRMED`, `InterviewSlotConfirmedEvent`, listener, `forSlotConfirmed`, phát sự kiện ở bước 6; T6, T10, T11, hoàn tất T7–T8. Seed mục 4.6, `db/seed/README.md`; chạy tay `reset-test-data.sql` → `seed-test.sql` → câu đếm → reset lần hai (dải f1 về 0) → nạp lại | test-compile; `-Dtest` lớp của đợt 2 + `Notification*Test`; output câu đếm dán vào báo cáo |
| 4 | Frontend ứng viên: `InterviewSlotPicker`, `ConfirmInterviewSlotDialog`, sửa `InterviewInvitationDetails`, `InvitationSection`, `api.ts`, `queries.ts`, `types.ts`, `invitationLabels.ts` (khoá phía ứng viên, `candidateUnconfirmed`) | build + lint |
| 5 | Frontend HR: dòng "Ứng viên đã chọn lúc …" ở `InterviewInvitationTab` và `PreviousInvitationCard`; sửa `resendConfirmedNotice` (R-H2); `notifications/types.ts` | build + lint; lệnh 7.3 |
| 6 | Đợt cuối: full `.\mvnw.cmd test` (một lần); khối 7.2 (dán output); kiểm tĩnh 7.1; soát tay 7.4 (người dùng); `srs-guard`; `walkthrough`; trạng thái `ĐÃ HOÀN THÀNH`; tài liệu mục 10 | full `mvnw test`; build + lint; 7.2; 7.3 |

## 10. Tài liệu dùng chung sẽ sửa (ở đợt cuối, không sửa trong commit đặc tả)

| # | File | Thay đổi |
|---|---|---|
| D1 | `docs/SRS.md` | Dòng 53: `Chưa đặc tả` → `Đã hoàn thành`. Dòng 130 và mục 4 dòng FR-C03 (`:173`, đã có "ứng viên đã chốt lịch") giữ nguyên |
| D2 | `docs/ROADMAP.md:938` | Tick + tóm tắt; giới hạn đã biết: chọn trùng lúc HR gửi lại (Q8), giờ máy ứng viên, `slotId` sai định dạng (nợ body JSON hỏng có sẵn) |
| D3 | `docs/UI_GUIDE.md` mục 7 | Dòng `/candidate/applications/:id`: "giấy mời" → "giấy mời (chọn khung giờ — FR-U10)" (FR-U10 đã có ở cột FR). Dòng `/hr/applications/:id`: tab "Giấy mời phỏng vấn" thêm "thời điểm ứng viên chọn khung" và `FR-U10` ở cột FR |
| D4 | `docs/features/HR/H12/REQUIREMENT.md` | Ghi chú "Sửa bởi FR-U10" ở Q8 (mục 11.2: câu Q8 đã được thay) và R-B (mục 3.9: phần của U10 đã làm) |
| D5 | `docs/features/HR/H12/UI.md` | Ghi chú "Sửa bởi FR-U10" ở 4b (câu "Ứng viên đã chốt khung" → "Giấy mời hiện tại đã chốt khung"), 4c (dòng "Ứng viên đã chọn lúc …"), 4d (khối ứng viên có phần chọn), mục 7 (hai câu), mục 10 dòng "Không nút/ô chọn khung phía ứng viên (FR-U10)" |
| D6 | `docs/features/UV/U08/REQUIREMENT.md:221` (R-T6), `docs/features/UV/U08/UI.md:261` | Ghi chú "Sửa bởi FR-U10": khối giấy mời có phần chọn khung khi đơn `INTERVIEW_INVITED` và giấy mời chưa chốt |
| D7 | `docs/features/README.md` mục 13 | Không đổi nội dung (đã khớp: chọn một lần, thông báo HR, không đổi trạng thái). Chỉ soát lại ở đợt cuối |
| D8 | `CLAUDE.md` | Không đổi: không package mới (§3), không dùng K1–K4 (§3d) |

`db/seed/README.md` sửa ở đợt 3 cùng commit seed (mục 4.6), không chờ đợt cuối.

## 11. Đã chốt 14/10/2026

### 11.1 Quyết định nền

| # | Nội dung | Mục áp dụng |
|---|---|---|
| N1 | Ranh giới với H12: H12 tạo cột khung chốt, gửi lại, đọc phía HR, DTO I4 có `id` khung; U10 ghi khung chốt cho giấy mời nhiều khung, thông báo HR, giao diện chọn | Mục 0, 3.9 H12 |
| N2 | Transaction: kiểm quyền → `lockIfInterviewInvited` → `now` một lần → một câu `UPDATE` có điều kiện → 0 dòng đọc lại trả đúng mã → phát sự kiện; không đọc giấy mời hiệu lực trước khi khoá | R-W |
| N3 | Không V17: V16 đủ (FK ghép, CHECK đi cặp, `uq_invitation_active`; chốt một lần bằng `UPDATE … WHERE confirmed_slot_id IS NULL`) | Mục 4.1 |
| N4 | Không đổi trạng thái đơn, không ghi lịch sử | R-S |

### 11.2 Câu hỏi Q1–Q13

| # | Câu hỏi | Quyết định (Đã chốt) | Phương án đã loại / đánh đổi | Mục áp dụng |
|---|---|---|---|---|
| Q1 | Hình dạng endpoint | `POST …/interview-invitation/confirm-slot`, thân `{ "slotId" }`, 200 kèm DTO I4 | `PUT …/confirmed-slot`: đi ngược tinh thần "không PUT/PATCH" của H12 R-C2, gợi ý sửa được | 4.3, 4.5, R-W7 |
| Q2 | Ứng viên đổi khung | Chọn một lần, không tự đổi; muốn đổi nhắn HR để HR gửi lại | Cho đổi: phải định nghĩa lại "đã chốt", lịch sử lựa chọn, thông báo đổi | R-W6, mục 6 |
| Q3 | Chọn lần hai | 409 `INTERVIEW_SLOT_ALREADY_CONFIRMED`, kể cả đúng khung đã chốt; không thông báo | 200 idempotent khi trùng khung: thêm nhánh, rủi ro thông báo trùng | R-W5, R-W6, T3c, T6 |
| Q4 | Đơn của ứng viên khác | 403, theo khuôn I4 | 404 ẩn hoàn toàn (khuôn `withdraw`) | R-A1, T3f, T12 |
| Q5 | Thời gian báo trước tối thiểu | Không; chỉ `scheduled_at > now` | Ngưỡng (vd 2 giờ): thêm ngưỡng, biên, câu lỗi | R-C6, T2 |
| Q6 | Chưa chốt mà mọi khung đã qua | Không radio; câu cố định hướng sang tab Trao đổi | Thông báo HR tự động: cần job nền | R-U2, UI.md |
| Q7 | Thông báo cho HR | `INTERVIEW_SLOT_CONFIRMED` cho chủ công ty: web một dòng có giờ VN; `email_body` gồm giờ, địa điểm, link `?tab=invitation`; ứng viên không nhận | Chỉ `body`: email không có link | R-N |
| Q8 | Chọn trùng lúc HR gửi lại | Chấp nhận cả hai thứ tự; ghi giới hạn | Báo HR trong hộp thoại: phải đổi I3 (hành vi H12) | R-R, mục 6, T7 |
| Q9 | Phía HR hiện thời điểm ứng viên chọn | "Ứng viên đã chọn lúc {confirmedAt}" cho giấy mời ≥ 2 khung đã chốt; sửa câu sai `invitationLabels.ts:20-21`, chỉ câu đó | Không hiện: HR không phân biệt tự chốt / ứng viên chọn | R-H1, R-H2 |
| Q10 | Seed | Đơn mới của Lê Thị Lan vào J2, giấy mời 3 khung chưa chốt (1 đã qua, 2 còn hạn), dải `f1`; giữ A9; ngoại lệ có chủ đích với H12 4.5; `reset-test-data.sql` xoá được | Không thêm: soát tay phải qua onboarding A9, khung đã qua chỉ có test tự động | 4.6 |
| Q11 | Giao diện chọn | Radio trên danh sách khung, nút "Xác nhận khung giờ", hộp xác nhận ghi rõ không tự đổi được | Bấm khung là chốt: dễ bấm nhầm, không đảo ngược được | UI.md |
| Q12 | Bộ mã lỗi | `SLOT_SELECTION_NOT_ALLOWED`, `INTERVIEW_SLOT_ALREADY_CONFIRMED`, `INTERVIEW_INVITATION_CONFLICT` (dùng lại), `INTERVIEW_SLOT_PASSED`, 400 `slotId`, 404 `INTERVIEW_INVITATION_NOT_FOUND`; thứ tự kiểm theo R-W5 | Gộp ít mã: giao diện không nêu được đúng lý do | 4.3, 4.4, T3, T4 |
| Q13 | Tin đã đóng / xoá mềm | Vẫn cho chọn | Chặn: thêm điều kiện + mã lỗi, khác I3 | R-C7, T5 |

### 11.3 Điểm chốt sau `spec-review` (O1–O6, 14/10/2026)

| # | Điểm | Quyết định (Đã chốt) | Phương án đã loại / đánh đổi | Mục áp dụng |
|---|---|---|---|---|
| O1 | Đơn `HIRED`/`REJECTED` mà giấy mời hiệu lực chưa chốt — phía ứng viên hiện gì | Không hiện câu nào thêm; danh sách khung chỉ đọc giữ như H12 | Giữ câu "nhắn nhà tuyển dụng": vô nghĩa khi đơn đã có kết quả | Mục 2, R-U3, UI.md 4b-4 |
| O2 | Dòng "Ứng viên đã chọn lúc …" ở thẻ "Giấy mời trước đó" | Có — cùng quy tắc ≥ 2 khung đã chốt | Chỉ giấy mời hiệu lực: HR mất thông tin khi xem lịch sử | R-H1, UI.md 4d |
| O3 | Thiếu `slotId` | 400 dạng map trường (Bean Validation) `{"slotId": "Vui lòng chọn một khung giờ."}` theo khuôn có sẵn (a26) | Mã riêng `INVALID_SLOT_SELECTION`: thêm exception, lệch khuôn 400 hiện có | 4.4, 4.5, T3e |
| O4 | Nút "Xác nhận khung giờ" khi chưa chọn | Không khoá; chưa chọn khung thì báo lỗi tại chỗ, focus vào radio đầu tiên chọn được | Khoá nút (`disabled`): người dùng bàn phím/đọc màn hình không biết vì sao | UI.md 4a, mục 9 |
| O5 | Soát tay ca "mọi khung đã qua" | Câu SQL lùi giờ chạy tay trên DB dev, ghi nguyên văn ở 7.4, ngay sau khi soát chạy `reset-test-data.sql` + `seed-test.sql` + `install-test-files.ps1` | Thêm giấy mời thứ hai vào seed: phải thêm một đơn nữa (vướng `uq_application_per_cycle` của Lan với J1, J2), tăng ngoại lệ với H12 4.5 | 7.4 |
| O6 | Tên mã `INTERVIEW_SLOT_PASSED` và lệnh kiểm tĩnh `passed` | Giữ tên; kiểm tĩnh dùng `rg -w` (CLAUDE.md §7 chỉ cấm **cột/field** tên `passed`) | Đổi tên `INTERVIEW_SLOT_EXPIRED`: lệch mã đã chốt ở Q12 | 7.1 |

## 12. Điểm còn mở

Không còn điểm mở nào — O1–O6 đã chốt ở mục 11.3.

## 13. Làm rõ sau Plan Mode (duyệt 14/10/2026)

Đối chiếu lại trên `feat/fr-u10-pick-slot` tại `aed0165` (chỉ khác `f4665a3` ở hai file đặc tả U10; mục 0 a1–a30 không
tham chiếu file:dòng nào lệch). Thử SQL trên Postgres dev: chạy nguyên `V16__interview_invitation_slots.sql` trong **một**
transaction (DDL Postgres có transaction), dựng giấy mời 3 khung chưa chốt cho đơn A9, chạy từng ca bằng `SAVEPOINT`, rồi
`ROLLBACK` — sau đó `flyway_schema_history` vẫn `14`, bảng `interview_invitation_slots` không tồn tại. Mục này thắng chỗ
viết cũ ở các mục được dẫn nếu lệch.

- **L1 (môi trường; 7.2, 7.4, mục 9 đợt 3) — DB dev đang ở V14.** Chưa có V15, V16; seed test trên DB dev là bản trước
  FR-H12 (3 giấy mời `ea…`, cột `scheduled_at`/`location` cũ). Chốt: đợt 3 khởi động backend một lần để Flyway lên V16, rồi
  **bắt buộc** `reset-test-data.sql` trước khi nạp `seed-test.sql` (comment có sẵn `seed-test.sql:446-448`). Khối 7.2 và soát
  tay 7.4 cần cùng bước này.
- **L2 (7.4, O5) — câu SQL soát tay "mọi khung đã qua" cũ vi phạm unique.** Ràng buộc thật của `interview_invitation_slots`
  (đọc `pg_constraint` sau V16): `uq_invitation_slot_time UNIQUE (invitation_id, scheduled_at)`, `uq_invitation_slot_order`,
  `uq_invitation_slot_owner`, CHECK `display_order BETWEEN 1 AND 5`; **không** CHECK tròn phút, **không** trigger. Câu cũ đặt
  hai khung …32, …33 cùng `now() - INTERVAL '1 hour'` → tái hiện được `ERROR: duplicate key value violates unique constraint
  "uq_invitation_slot_time"`. Chốt câu mới (đã chạy thử: `UPDATE 2`, ba khung đều đã qua, thứ tự thời gian 31 < 32 < 33):

  ```sql
  UPDATE interview_invitation_slots
  SET scheduled_at = date_trunc('minute', now())
      - CASE id WHEN 'f1000000-0000-0000-0000-000000000032' THEN INTERVAL '2 hours' ELSE INTERVAL '1 hour' END
  WHERE id IN ('f1000000-0000-0000-0000-000000000032', 'f1000000-0000-0000-0000-000000000033');
  ```

  `date_trunc('minute', …)` không bắt buộc về DB (không CHECK) — giữ để khớp quy ước cắt về đầu phút của ứng dụng (H12 R-S3).
  Hai mốc mới không trùng khung …31 (02:00 UTC hôm qua; hai mốc mới thuộc hôm nay trừ 1–2 giờ). Bước đưa seed về gốc ngay
  sau khi soát giữ nguyên. Bảng 7.4 đã sửa theo.
- **L3 (T6) — không cần vòng thăm chờ listener.** `@TransactionalEventListener(AFTER_COMMIT)` chạy đồng bộ trên luồng commit,
  trước khi method service trả về cho nơi gọi; H12 T10a đếm thông báo ngay sau `future.get`. Chốt: T6 đếm thông báo ngay sau
  khi hai luồng xong. T6 đã sửa theo.
- **L4 (T7) — xác nhận "B đang bị chặn" bằng `pg_stat_activity`, không bằng mốc 500 ms.** Theo khuôn FR-U09 T8
  (`ScreeningCaptureConcurrencyIntegrationTest.awaitOtherSessionBlockedOnLock`): trước mỗi lần thăm `SELECT
  pg_stat_clear_snapshot()` (A đang trong transaction, Postgres giữ nguyên ảnh chụp `pg_stat_activity` tới hết transaction —
  U09 L9); điều kiện `datname = current_database() AND pid <> pg_backend_pid() AND wait_event_type = 'Lock' AND query ILIKE
  '%SET status = status%'` (câu `lockIfInterviewInvited` của B); thăm mỗi 50 ms, hạn 10 s, hết hạn thì đỏ; thêm khẳng định
  `futureB.isDone() == false`. `JdbcTemplate` trong `TransactionTemplate` của A dùng chung kết nối với transaction JPA. T7 đã
  sửa theo.
- **L5 (T8) — thêm ca đơn luồng chiều thuận.** A khoá (`lockIfInterviewInvited`) rồi `updateStatusIfCurrent(…, HIRED)` trong
  cùng transaction, giữ chưa commit; B `confirmSlot` bị chặn (xác nhận như L4); A commit → B 409 `SLOT_SELECTION_NOT_ALLOWED`.
  Giữ ca ngược ("`HIRED` commit trước"). T8 đã sửa theo.
- **L6 (R-W1 bước 4, T2) — `Instant` trong phép so sánh của native query.** Tham số `UUID` có tiền lệ (`lockIfInterviewInvited`);
  `Instant` có tiền lệ ở `SET` (`JobApplicationRepository.revokeTalentPoolConsentIfActive`, `:50`), chưa có ở phép so sánh
  `>`. T2 phủ ba mốc (−1 phút, đúng `now`, +1 phút): bind sai kiểu thì T2 đỏ ngay ở đợt 2.
- **L7 (R-W1 bước 4) — cờ `@Modifying`.** Giữ `clearAutomatically = true` (bước 5 phải đọc lại giấy mời/khung từ DB — câu
  `UPDATE` chạy ngoài ORM, entity còn trong persistence context sẽ trả bản cũ) và `flushAutomatically = true` (hiện không bắt
  buộc vì không có ghi ORM nào trước câu này; giữ phòng hờ như comment `JobApplicationRepository.java:27-29`). Ghi lý do vào
  comment của method.
- **L8 (ngoài phạm vi) — comment cũ của H12.** `InterviewInvitationResendConcurrencyIntegrationTest.java:99` ghi "1 khung, da
  chot" nhưng `request(2)` gửi 2 khung. Không sửa ở U10; ghi vào walkthrough. Test của U10 viết comment đúng.
- **L9 (UI.md mục 9) — focus ban đầu của hộp xác nhận.** `DialogFooter` đặt "Huỷ" trước "Xác nhận", nút X của shadcn render
  sau cùng → Radix tự focus "Huỷ", không cần `onOpenAutoFocus`. Soát tay kiểm lại ở đợt 4.
- **L10 (R-W5 dòng 3) — `slotRepository.findById`.** Đọc bình thường trên entity `@Immutable` (Hibernate chỉ chặn ghi); không
  cần method repository mới.
- **L11 (môi trường) — file thử nghiệm trong container.** Hai file `/tmp/u10-V16.sql`, `/tmp/u10-plan-sql.sql` trong container
  postgres (Plan Mode) đã xoá bằng `docker compose exec postgres rm -f …`.
- **L12 (môi trường) — script thử nghiệm.** `u10-plan-sql.sql` giữ trong scratchpad của phiên làm bằng chứng đợt 1, không đưa
  vào repo.
- **L13 (quy trình) — nơi ghi L1–L13.** Ghi vào mục này + sửa 7.4 (L2), T6 (L3), T7 (L4), T8 (L5) thành một commit tài liệu
  riêng trước đợt 2, như FR-H12 mục 12.

Bổ sung trong đợt 2 (14/10/2026):

- **L14 (R-W1 bước 3) — `now` cắt về micro giây.** `TIMESTAMPTZ` lưu tới micro giây; nếu `now` giữ nano giây thì giá trị JDBC
  bind vào câu `UPDATE` (làm tròn về micro) có thể khác giá trị Java dùng để phân loại lỗi ở R-W5 dòng 4 — vd khung đúng bằng
  `now` làm tròn lên: SQL thấy "đã qua", Java thấy "chưa qua" → rơi xuống dòng 5 (`INTERVIEW_INVITATION_CONFLICT`) thay vì
  `INTERVIEW_SLOT_PASSED`. Chốt: `Instant.now().truncatedTo(ChronoUnit.MICROS)`, một giá trị dùng cho cả câu `UPDATE`,
  `confirmed_at` và phân loại lỗi.
- **L15 (kiểm tĩnh 7.1) — số dòng `confirmed_slot_id`.** Kỳ vọng cũ "2 dòng" sai: thực tế 4 dòng — `InterviewInvitation.java:23`
  (comment có sẵn của H12), `:53` (`@Column`), và 2 dòng trong câu `confirmSlotIfAvailable` (`SET` và `WHERE`). Kỳ vọng ở 7.1
  sửa thành 4 dòng.
- **L16 (môi trường) — `rg` không có trong shell của máy dev.** Kiểm tĩnh đợt 2 chạy bằng công cụ tìm kiếm của phiên (cùng
  biểu thức) và `git diff --stat`; kết quả: `changeStatus` 1 dòng (`InterviewInvitationService.java:138`, I2), không
  `ApplicationStatusRecorder`/`ApplicationStatusChangedEvent`/`FOR SHARE`/`FOR UPDATE`/`@Lock` trong `interviewinvitation/`,
  0 dòng `verdict|isQualified|passed|recommendation` (khớp nguyên từ, không phân biệt hoa thường) trong `interviewinvitation/`
  và `common/exception/`, `git diff --stat main` của `db/migration`, `aicontext/`, `messagedraft/`, `jobapplication/`,
  `notification/` (main) rỗng.
- **L17 (T6–T8) — kết quả thử đột biến bỏ khoá.** Tạm vô hiệu `lockIfInterviewInvited` trong `confirmSlot`, chạy riêng
  `InterviewSlotConfirmConcurrencyIntegrationTest`: 4/10 đỏ — ba ca đơn luồng (T7 hai ca, T8 thuận: "không bị khoá chặn sau
  10000 ms") và T8 ngược (không ném `SlotSelectionNotAllowedException`). T6 vẫn xanh vì điều kiện nguyên tử `confirmed_slot_id
  IS NULL` của câu `UPDATE` tự chặn lượt thứ hai; T7/T8 song song (10 vòng) vẫn xanh vì cửa sổ race hẹp — đúng lý do giữ các
  ca đơn luồng (L4, L5). Đã hoàn nguyên, chạy lại lớp này 10/10 xanh.
- **L18 (T6, T7) — đếm thông báo dời sang đợt 3.** Đợt 2 chưa phát sự kiện (R-N1), nên khẳng định "đúng một thông báo" của T6
  và phần thông báo của T7 thêm vào lớp concurrency ở đợt 3, như mục 9.

Bổ sung trong đợt 3 (14/10/2026):

- **L19 (quy trình) — nguồn kế hoạch đợt 3.** File kế hoạch Plan Mode của đợt 1 không còn trong thư mục plan của phiên
  (chỉ còn plan FR-U09). Đợt 3 bám mục 9 (dòng "Đợt 3"), 3.6, 4.6 và mục 13 của file này.
- **L20 (R-N1, R-W1 bước 6) — giá trị khung cho sự kiện.** Lấy `scheduledAt`/`location` từ phần tử `confirmed = true` của
  DTO I4 vừa dựng ở bước 5 (đã đọc lại sau `clearAutomatically`), không thêm `slotRepository.findById`. Sự kiện phát sau khi
  dựng response, chỉ trên nhánh `UPDATE` trả 1 dòng.
- **L21 (T3, R-N5) — "không thông báo" của T3 không kiểm được trong lớp `@Transactional`.** Lớp
  `InterviewSlotConfirmControllerIntegrationTest` rollback mọi test nên listener `AFTER_COMMIT` không bao giờ chạy — đếm
  thông báo ở đó luôn 0, kể cả khi code sai. Không sửa T3; khẳng định thật đặt ở `InterviewSlotConfirmNotificationIntegrationTest`
  (không `@Transactional`): `PASSED` và `ALREADY_CONFIRMED` (T10 như đặc tả), thêm 403, 400, `INTERVIEW_INVITATION_CONFLICT`,
  `SLOT_SELECTION_NOT_ALLOWED`, và một ca rollback (gọi `confirmSlot` trong `TransactionTemplate` rồi `setRollbackOnly` → sự
  kiện đã phát nhưng 0 thông báo, `confirmed_*` vẫn NULL).
- **L22 (T6–T8) — phạm vi assert thông báo ở lớp concurrency.** Ngoài T6 (đúng 1/đơn) và T7 (1 nếu lượt chọn thành công, 0
  nếu 409; ứng viên đúng 1 `INTERVIEW_INVITATION_RESENT`), thêm cho T8 song song (1 nếu chọn thành công, 0 nếu 409) và ba ca
  đơn luồng (409 → 0, 200 → 1). Chỉ thêm assert và helper đếm; không sửa assert cũ.
- **L23 (ngoài phạm vi) — comment lỗi thời.** `NotificationMailOrchestrator.java:58` ghi "email_body khác null (hiện chỉ
  INTERVIEW_INVITATION_RESENT)"; từ U10 còn `INTERVIEW_SLOT_CONFIRMED`. Mục 4.2 không cho sửa file này (hành vi mail không
  đổi) — ghi vào walkthrough.
- **L24 (seed, mục 4.6) — dòng FR-U09 của `db/seed/README.md` mục 8.4.** "8 đơn còn lại 'không đồng ý'" đếm toàn bộ đơn
  test; A10 thêm một đơn `talent_pool_consent = FALSE` nên sửa thành 9. Comment mục 9 của `seed-test.sql` (phạm vi A1–A9)
  giữ nguyên.
- **L25 (môi trường) — DB dev vẫn ở V14 khi vào đợt 3** (`flyway_schema_history` cao nhất `14 talent pool consent`), như
  L1. Bước reset → seed → đếm → reset lần hai → nạp lại chờ người dùng khởi động backend một lần để Flyway lên V16.
  Sau khi người dùng khởi động backend: `flyway_schema_history` cao nhất `16 interview invitation slots`; reset → seed →
  câu đếm đúng (dải cũ không đổi, dải f1 1/2/1/3/2) → reset lần hai (dải f1 về 0 ở cả 5 bảng) → nạp lại; HR test 7 đã đọc /
  4 chưa đọc, Lê Thị Lan 0 / 1.
- **L26 (R-N5) — thử đột biến "phát sự kiện cả khi `UPDATE` trả 0 dòng": 0 ca đỏ, và đó là đúng.** Nhánh 0 dòng luôn ném
  exception → `@Transactional` rollback → listener `AFTER_COMMIT` không chạy, nên đột biến tương đương về hành vi: "không
  thông báo khi lỗi" được bảo đảm bởi rollback, không chỉ bởi vị trí câu `publishEvent`. Đột biến phụ quan sát được (bỏ phát
  sự kiện ở nhánh thành công) làm đỏ 9/14 ca của hai lớp `InterviewSlotConfirmNotification*`/`*Concurrency*` (T6a, T6b, T7
  song song, T7 ca ngược, T8a, T8b, T8c, hai ca T10) — test không rỗng. Đã hoàn nguyên cả hai, tìm dấu đột biến ra 0 kết quả,
  chạy lại hai lớp 14/14 xanh.

Bổ sung trong đợt 4 (14/10/2026):

- **L27 (UI.md mục 7) — chuỗi hướng dẫn chọn có ngoặc kép lồng.** `candidateUnconfirmed` khai bằng nháy đơn, giữ đúng ký tự
  `"` quanh "Xác nhận khung giờ" như UI.md mục 7, không escape thành ký tự khác.
- **L28 (UI.md 4c, mục 9) — focus sau khi xác nhận thành công.** Phần chọn bị gỡ nên Radix không còn trigger để trả focus.
  `onCloseAutoFocus` của hộp xác nhận: thành công → `preventDefault` rồi focus `h2` "Giấy mời phỏng vấn" (`tabIndex={-1}`, ref
  truyền từ `InvitationSection`); Huỷ/đóng → để Radix trả focus như thường (ngoại lệ ở L30).
- **L29 (UI.md mục 9) — `aria-invalid` trên `<fieldset>`.** Role `group` không hỗ trợ `aria-invalid`. Giữ `<fieldset>`/`<legend>`,
  đặt `role="radiogroup"` lên vùng chứa các radio, gắn `aria-invalid` và `aria-describedby` (trỏ tới câu lỗi `role="alert"`)
  lên vùng đó.
- **L30 (UI.md 5a, 4c) — hộp xác nhận đặt ở `InterviewInvitationDetails`, không ở `InterviewSlotPicker`.** `setQueryData` của
  mutation đổi I4 sang "đã chốt" ngay khi thành công → phần chọn bị gỡ trong khi hộp thoại còn mở; nếu hộp thoại nằm trong
  phần chọn thì bị gỡ theo (mất `onCloseAutoFocus`, mất callback `mutate`). Phần chọn vẫn là nơi mở hộp thoại (gọi
  `onRequestConfirm(slot)`). Hệ quả thêm: Huỷ/đóng sau một lỗi làm I4 đổi sang không còn phần chọn (vd 409
  `INTERVIEW_SLOT_ALREADY_CONFIRMED`) cũng focus `h2`, vì nút mở hộp thoại không còn.
- **L31 (UI.md mục 6, R-U1) — mốc "giờ máy" lấy từ `dataUpdatedAt` của query I4, không gọi `Date.now()` trong render.**
  `eslint-plugin-react-hooks` 7 (`react-hooks/purity`) báo lỗi `Date.now()` trong thân component. Mốc = lúc I4 tải xong lần
  gần nhất; I4 tải lại khi quay lại tab (mặc định TanStack) và sau mọi 409/404 của C1. Khác "lúc render" ở chỗ: trang để mở
  lâu không tải lại thì khung vừa qua vẫn hiện chọn được tới lần tải sau — backend chặn (409 `INTERVIEW_SLOT_PASSED`, R-C6),
  I4 tải lại, khung thành "Đã qua". **Đã duyệt 14/10/2026** (mốc `dataUpdatedAt`; trang mở lâu thì backend chặn bằng 409
  `INTERVIEW_SLOT_PASSED` rồi tải lại).
- **L32 (UI.md 4e) — hai nút hộp xác nhận cùng hàng ở 375px.** `DialogFooter` mặc định xếp dọc dưới `sm`
  (`RevokeTalentPoolConsentDialog` cũng vậy); UI.md 4e ghi rõ "cùng hàng" nên truyền `className="flex-row justify-end"`.
- **L33 (giới hạn đã biết) — 409 `SLOT_SELECTION_NOT_ALLOWED` khi đơn đã bị rút ở tab khác.** Mutation làm mới trang chi
  tiết đơn ngay lúc lỗi (UI.md 5b); đơn `WITHDRAWN` thì `InvitationSection` ẩn cả khối (FR-U08 R-T3) nên hộp thoại cùng câu
  lỗi biến mất theo, người dùng thấy badge "Đã rút" thay vì đọc câu lỗi. `HIRED`/`REJECTED` không bị (khối vẫn hiện).
- **L34 (UI.md 4c) — 403 không đổi câu riêng.** Lỗi 403 hiện câu `message` backend hoặc câu fallback "Xác nhận khung giờ thất
  bại…", không thay bằng "Không tìm thấy" — trang chi tiết đã chặn đơn người khác nên không xảy ra (UI.md 4c tự ghi).
- **L35 (CLAUDE.md §7) — tránh tên `passed` trong code mới.** Biến/khoá dùng `elapsed`/`slotElapsed` thay cho `passed`;
  chuỗi hiển thị vẫn là "Đã qua".

Bổ sung trong đợt 5 (14/10/2026):

- **L36 (R-H1) — điều kiện dòng HR thêm `confirmedAt !== null`.** Quy tắc vẫn là `slots.length >= 2 && confirmedSlotId !==
  null`; vế `confirmedAt !== null` chỉ để TypeScript thu hẹp kiểu `string | null` trước `formatDateTimeVi`. Không đổi hành vi:
  CHECK `chk_invitation_confirmed_pair` (V16) bảo đảm hai cột đi cặp. Không so `confirmedAt` với `sentAt`.
- **L37 (UI.md 5b) — một component cho dòng HR, đặt trong `PreviousInvitationCard.tsx`.** `CandidatePickedLine` khai và
  export ở `PreviousInvitationCard.tsx` (cạnh `InvitationLetter`, đã được `InterviewInvitationTab` import sẵn), dùng ở cả
  giấy mời hiệu lực và thẻ giấy mời trước đó — quy tắc R-H1 nằm một chỗ, không thêm file ngoài UI.md 5a/5b. Thẻ cũ bọc dòng
  meta + dòng HR trong một `div` `gap-1` để dòng HR nằm sát dưới "Gửi lúc … · Đã được thay bằng …" (như tab), các phần
  khác của thẻ giữ `gap-3`.
