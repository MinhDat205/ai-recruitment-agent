# FR-C06 — Nhắn tin theo đơn ứng tuyển

> Trạng thái: ĐÃ DUYỆT 10/10/2026

- Nhóm: Chung
- Tóm tắt: HR và ứng viên nhắn tin quanh **một** đơn ứng tuyển: tab "Trao đổi" ở trang hồ sơ đơn (HR) và
  trang chi tiết đơn (ứng viên), hộp thư "Tin nhắn" ở mỗi phía; mỗi tin có thể kèm một tệp; bên nhận được
  thông báo web + email; giao diện tự tải lại định kỳ.
- Phụ thuộc: FR-C03, FR-U01, FR-H09, FR-U08 — cả 4 đều **Đã hoàn thành** (`docs/SRS.md` mục 0, dòng 23, 45,
  37, 52).
- Nhánh: `feat/fr-c06-messaging`, tách từ `main` (`4376089`, đã gồm FR-H09 và FR-U08 qua PR #12).
- Mở rộng: FR-C03 (loại thông báo `NEW_MESSAGE`, email có liên kết cho loại này), FR-H09 (thêm tab), FR-U08
  (thêm tab).
- Chạm code cũ, **không đổi hành vi**: `ResumeService` (FR-U01) và `CompanyOwnerService` (FR-H01) chuyển sang
  bộ nhận dạng tệp dùng chung (R-C1); `ResumeList.tsx` dùng hàm định dạng dung lượng dùng chung (R-C3).

## 0. Đã đối chiếu code trước khi viết (CLAUDE.md §6)

Đối chiếu trên `main` tại `4376089` (09/10/2026).

### (a) Chưa có gì của nhắn tin

| # | Điều đã kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| a1 | Chưa có bảng tin nhắn | **Đúng** | 26 lệnh `CREATE TABLE` ở `V1`, `V5`, `V6`, `V8`; không có bảng nào tên chứa `message`/`conversation` |
| a2 | Số migration lớn nhất | `V11` | `V11__drop_job_recommendations.sql` → FR-C06 dùng `V12` |
| a3 | Chưa có package/route nhắn tin | **Đúng** | `backend/.../com/recruitment/` không có `messaging/`; `App.tsx:69-78,92-97` không có `/candidate/messages`, `/hr/messages` |
| a4 | Hai trang đơn chưa có tab Trao đổi | **Đúng** | `HrApplicationDetailPage.tsx:16` (`cv`, `explanation`, `history`); `CandidateApplicationDetailPage.tsx:16` (`info`, `history`) |
| a5 | Điều hướng chưa có mục "Tin nhắn" | **Đúng** | `CandidateLayout.tsx:21-38` (4 mục); `HrLayout.tsx:20-25` (4 mục) |

### (b) Kiểm quyền trên một đơn — hai quy ước có sẵn, FR-C06 dùng lại nguyên trạng

| Phía | Cách nạp đơn | Đơn không tồn tại | Đơn của bên khác | Bằng chứng |
|---|---|---|---|---|
| HR | `HrApplicationAccess.loadOwned(ownerId, applicationId)` | 404 `APPLICATION_NOT_FOUND` | **403** | `HrApplicationAccess.java:42-56`; HR chưa có công ty → 404 `COMPANY_NOT_FOUND` (`:43-45`) |
| Ứng viên | `JobApplicationRepository.findByIdAndCandidateId` | 404 `APPLICATION_NOT_FOUND` | **404** (cùng mã) | `JobApplicationRepository.java:18`; `ApplicationService.java:105-107` |

- RBAC theo tiền tố đường dẫn: `/api/hr/**` chỉ HR, `/api/candidates/**` chỉ CANDIDATE (`SecurityConfig.java:53-56`).
  FR-C06 đặt endpoint dưới hai tiền tố này, không thêm endpoint dùng chung hai vai trò.
- `HrApplicationAccess.loadOwned` nạp tin bằng `jobRepository.findById` (`:49-51`) — không lọc `deleted_at`,
  nên đơn vào tin đã xoá mềm/đã đóng vẫn truy cập được. Nhắn tin theo đúng hành vi đó (R-Q5).
- Mỗi công ty đúng một chủ: `uq_company_per_owner` (`V2__company_unique_owner.sql:2`), `Company.ownerId`
  (`Company.java:28-29`). "Phía HR" của một đơn = đúng một người dùng.

### (c) Lưu tệp và nhận dạng tệp

| # | Điều đã kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| c1 | FR-U01 kiểm magic bytes những loại nào | **Chỉ PDF, DOCX** | `ResumeService.java:33-34,219-227` |
| c2 | Kiểm magic bytes ảnh nằm ở đâu | Ở FR-H01 (logo): PNG, JPEG, WEBP | `CompanyOwnerService.java:24-27,133-144` |
| c3 | Hai chỗ trên có dùng chung code không | **Không** — hai bản `private static` riêng | `ResumeService.java:219-239`; `CompanyOwnerService.java:133-156` |
| c4 | Tệp riêng tư có bị phục vụ tĩnh không | **Không** — chỉ `/uploads/logos/**` | `StorageWebConfig.java:22-23`; `SecurityConfig.java:48-49` |
| c5 | Đọc tệp riêng tư | `StorageService.load(key)`, key dạng `{thư mục}/{tên tệp}` | `StorageService.java:25`; `LocalStorageService.java:66-77` (có chặn path traversal) |
| c6 | Giới hạn multipart toàn cục | 10MB/tệp, 10MB/request | `application.yml:40-43` |
| c7 | Tệp vượt giới hạn multipart | 400, mã **`INVALID_RESUME_FILE`**, câu "File vượt quá dung lượng cho phép" — dùng chung cho mọi upload | `GlobalExceptionHandler.java:83-87` |
| c8 | Mẫu tải tệp có kiểm quyền | `ContentDisposition.attachment().filename(tên, UTF_8)` | `ResumeHrController.java:32-42`; `ResumeCandidateController.java:74-83` |

`docs/features/README.md:365` ghi "kiểm định dạng bằng magic bytes của FR-U01 (PDF, DOCX, ảnh)" — c1/c2 cho thấy
phần ảnh thuộc FR-H01, không thuộc FR-U01. FR-C06 gom hai bản thành một (R-C1), không viết bản thứ ba.

### (d) Thông báo (FR-C03)

| # | Điều đã kiểm | Kết quả | Bằng chứng |
|---|---|---|---|
| d1 | Loại thông báo hiện có | 4 loại | `NotificationType.java:3-8`; frontend `features/notifications/types.ts:1-5` |
| d2 | Cột `notifications.type` có CHECK không | **Không** (`VARCHAR(50)`) → thêm loại mới không cần migration | `V1__init_schema.sql:343` |
| d3 | Cách tạo thông báo | Sự kiện publish trong transaction nghiệp vụ, listener `AFTER_COMMIT` + `REQUIRES_NEW`, lỗi không văng ra ngoài | `NotificationEventListener.java:54-64,109-121` |
| d4 | `entity_type`/`entity_id` | Luôn `"APPLICATION"` + `applicationId` | `NotificationEventListener.java:116-117` |
| d5 | Email thông báo | Tiêu đề = `title`, nội dung = `body`; **không có liên kết**; không có cấu hình địa chỉ frontend | `NotificationMailOrchestrator.java:70-74`; `application.yml:93-96` |
| d6 | Chuông hiển thị `body` nguyên văn và điều hướng theo `link` | **Đúng** | `NotificationDropdown.tsx:37,48` |
| d7 | Trang "Xem tất cả" có điều hướng theo `link` không | **Không** (nợ có sẵn) | `NotificationList.tsx:60-67` |
| d8 | Poller email | 5 giây/lần, nhặt `PENDING`; tắt trong test | `NotificationMailScheduler.java:16,37`; `application-test.yml:19-20` |

### (e) Tự tải lại và giới hạn tần suất

- Mẫu tự tải lại có giới hạn thời gian đã có: `useStallGuardedPolling` (`features/notifications/queries.ts:26-44`),
  chuông tải lại mỗi 15 giây, tự dừng sau 20 phút liên tục (`:16,24`).
- `RateLimitFilter` chỉ áp cho một danh sách đường dẫn POST cố định (`RateLimitFilter.java:131-157`), cấu hình
  ở `application.yml:126-133`; filter tắt trong test (`application-test.yml:35-36`), có test đơn vị riêng
  (`RateLimitFilterTest`).

### (f) Dữ liệu seed và script dọn

- `reset-test-data.sql:93-95` xoá tường minh từng bảng con của `job_applications`; `reset-demo-db.sql:86-102`
  liệt kê tường minh từng bảng trong `TRUNCATE`. Thêm bảng mới phải sửa cả hai (R-S).
- Bộ test có **một** HR (`minhdathr@gmail.com`). Muốn kiểm "HR công ty khác bị chặn" bằng HTTP phải dùng HR
  của bộ demo `hr@demo.local` / `Demo1234` (`db/seed/README.md` mục 4).
- Đơn test dùng được: A1 `e8…01` (PENDING), A2 `e8…02` (INTERVIEW_INVITED), A3 `e8…03` (WITHDRAWN) của Quốc
  Huy; A9 `e8…09` của Trần Bảo Ngọc (`seed-test.sql:358-386`). UUID bộ test đang dùng tiền tố `e0`…`ec`
  (`seed-test.sql:10`).

## 1. Mục đích

HR và ứng viên trao đổi trực tiếp về **một** đơn ứng tuyển ngay trong hệ thống, thay vì liên lạc ngoài hệ
thống: gửi tin, kèm tệp, biết khi bên kia trả lời, và xem lại toàn bộ lịch sử trao đổi gắn với đơn.

## 2. Luồng người dùng

**HR**
1. Mở trang hồ sơ đơn `/hr/applications/:id` → tab "Trao đổi" (tab cuối).
2. Đọc các tin cũ trước, mới sau. Nhập nội dung, tuỳ chọn kèm một tệp → "Gửi".
3. Mục "Tin nhắn" ở thanh điều hướng mở `/hr/messages`: danh sách các đơn đã có tin, đơn có tin mới nhất ở
   trên, hiện số tin chưa đọc. Bấm một dòng → mở tab "Trao đổi" của đơn đó.

**Ứng viên**
1. Mở trang chi tiết đơn `/candidate/applications/:id` → tab "Trao đổi" (tab cuối). Đọc và trả lời như HR.
2. Mục "Tin nhắn" mở `/candidate/messages`: danh sách tương tự, hiện tên công ty thay cho tên ứng viên.

**Cả hai phía**
- Bên nào cũng có thể gửi tin đầu tiên.
- Có tin mới → bên nhận có thông báo ở chuông và một email (R-N). Bấm thông báo → mở thẳng tab "Trao đổi".
- Đang mở tab "Trao đổi": tin mới của bên kia tự hiện sau tối đa 10 giây, không cần tải lại trang.
- Đơn đã rút (`WITHDRAWN`): vẫn xem được toàn bộ tin, không gửi thêm được.

**Hệ thống**
- Không gọi LLM/embedding ở bất kỳ bước nào.

## 3. Quy tắc nghiệp vụ

### 3.1 Phạm vi (R-P)

- **R-P1.** "Cuộc trao đổi" = tập tin nhắn của **một** đơn ứng tuyển. Không có bảng cuộc trao đổi riêng;
  khoá của cuộc trao đổi là `applicationId`. Một đơn có nhiều nhất một cuộc trao đổi là hệ quả trực tiếp,
  không cần ràng buộc.
- **R-P2.** Cuộc trao đổi "tồn tại" khi đơn có ít nhất một tin. Đơn chưa có tin không hiện trong hộp thư.
- **R-P3.** Thêm đúng một tab "Trao đổi" (khoá `messages`) vào **cuối** mỗi trang: HR `cv`, `explanation`,
  `history`, `messages`; ứng viên `info`, `history`, `messages`. Không đổi tab mặc định, không đổi tab cũ.
- **R-P4.** Hộp thư là danh sách liên kết sang tab "Trao đổi" của từng đơn. Không có giao diện hai cột, không
  đọc/gửi tin ngay trong hộp thư.
- **R-P5.** Tin đã gửi không sửa, không xoá, không thu hồi. Không có endpoint `PUT`/`DELETE` cho tin nhắn.
- **R-P6.** Không dựng nút "Soạn bằng AI" hay khoảng trống chờ cho FR-C07; không dựng phần che tên cho FR-H16.

### 3.2 Quyền truy cập (R-Q)

- **R-Q1. Phía HR:** mọi endpoint theo đơn nạp đơn bằng `HrApplicationAccess.loadOwned` — HR chưa có công ty
  404 `COMPANY_NOT_FOUND`, đơn không tồn tại 404 `APPLICATION_NOT_FOUND`, đơn của công ty khác **403**. Không
  viết bản kiểm quyền thứ hai.
- **R-Q2. Phía ứng viên:** mọi endpoint theo đơn nạp đơn bằng `findByIdAndCandidateId` — đơn không tồn tại
  và đơn của người khác trả **cùng** 404 `APPLICATION_NOT_FOUND`.
- **R-Q3.** Người gọi luôn lấy từ JWT (`authentication.getName()`), không nhận `senderId`/`senderRole` từ
  request.
- **R-Q4.** Tải tệp đính kèm (M4): kiểm quyền trên đơn như R-Q1/R-Q2 **trước**, rồi nạp tin bằng cả
  `messageId` **và** `applicationId`. Tin không tồn tại hoặc thuộc đơn khác → 404 `MESSAGE_NOT_FOUND`. Không
  có đường nào đọc tệp chỉ bằng `messageId` hay bằng khoá lưu trữ.
- **R-Q5.** Trạng thái tin tuyển dụng (tạm dừng, đóng, xoá mềm, nháp) không ảnh hưởng quyền xem và gửi tin.
- **R-Q6.** Hộp thư (M5) chỉ trả đơn thuộc người gọi: HR — đơn vào tin của công ty mình; ứng viên — đơn có
  `candidate_id` = mình. HR chưa có công ty → 404 `COMPANY_NOT_FOUND`.

### 3.3 Gửi và đọc tin (R-M)

- **R-M1.** Một tin gồm nội dung chữ, hoặc một tệp, hoặc cả hai. Thiếu cả hai → 400 `MESSAGE_EMPTY`.
- **R-M2. Chuẩn hoá duy nhất được phép:** đổi `\r\n` và `\r` thành `\n` (trình duyệt gửi form dạng CRLF).
  Ngoài ra không trim, không cắt, không lọc ký tự, không đổi nội dung. Nội dung chỉ gồm khoảng trắng
  (`isBlank()`) coi như không có nội dung chữ: nếu có tệp thì lưu `body = NULL`, nếu không thì R-M1.
- **R-M3.** Độ dài nội dung **sau** chuẩn hoá tối đa **4000** (`String.length()`); vượt → 400
  `MESSAGE_TOO_LONG`. 4000 hợp lệ, 4001 không.
- **R-M4.** Gửi được khi đơn ở `PENDING`, `INTERVIEW_INVITED`, `HIRED`, `REJECTED`. Đơn `WITHDRAWN` → 409
  `CONVERSATION_READ_ONLY`, không lưu gì (kể cả tệp).
- **R-M5. Thứ tự kiểm của M2:** quyền (R-Q1/R-Q2) → trạng thái đơn (R-M4) → nội dung (R-M1, R-M3) → tệp
  (R-F). Lỗi ở bước nào dừng ở bước đó.
- **R-M6.** Mỗi tin lưu: đơn, người gửi (`sender_id`), vai trò người gửi (`HR`/`CANDIDATE`, suy từ đường
  dẫn endpoint, không từ request), nội dung, thông tin tệp, thời điểm tạo, thời điểm đã đọc.
- **R-M7.** Danh sách tin của một đơn (M1) trả theo `created_at` tăng dần, cùng thời điểm thì theo `id`. Trả
  tối đa **200** tin gần nhất; nếu đơn có nhiều hơn thì `olderMessagesHidden = true`. Không phân trang.
  `unreadCount` đếm trên **toàn bộ** tin của đơn, không chỉ 200 tin được trả.
- **R-M8.** `canSend` do backend tính (`status != WITHDRAWN`); frontend không tự suy từ trạng thái đơn.
- **R-M9.** Giới hạn đã biết, chấp nhận: ứng viên rút đơn đúng lúc bên kia đang gửi thì có thể lọt một tin
  sau thời điểm rút (kiểm trạng thái và ghi tin không khoá chung). Không thêm khoá cho trường hợp này.

### 3.4 Tệp đính kèm (R-F)

- **R-F1.** Mỗi tin tối đa **một** tệp. Muốn gửi nhiều tệp thì gửi nhiều tin.
- **R-F2.** Loại tệp nhận: PDF, DOCX, PNG, JPEG, WEBP — xác định **chỉ** bằng magic bytes của nội dung
  (R-C1). Phần mở rộng và `Content-Type` do client gửi không được dùng để quyết định. Loại khác → 400
  `INVALID_MESSAGE_ATTACHMENT` "Định dạng không hợp lệ, chỉ nhận PDF, DOCX, PNG, JPEG hoặc WEBP".
- **R-F3.** Dung lượng tối đa **5MB** (5 × 1024 × 1024 byte): đúng 5MB hợp lệ, 5MB + 1 byte → 400
  `INVALID_MESSAGE_ATTACHMENT` "Tệp đính kèm vượt quá 5MB". Tệp 0 byte → 400 `INVALID_MESSAGE_ATTACHMENT`
  "Tệp đính kèm đang trống".
- **R-F4.** Tệp trên 10MB bị chặn ở tầng multipart trước khi tới service và nhận mã có sẵn
  `INVALID_RESUME_FILE` (mục 0.c7). **Không** sửa handler đó (đổi mã sẽ đổi hành vi FR-U01/FR-H01); frontend
  chặn trước ở 5MB (UI.md) và luôn hiện `message` của backend, không dựa vào mã.
- **R-F5.** Lưu bằng `StorageService.store("message-attachments", "{uuid}.{đuôi chuẩn}", …)`;
  cột `attachment_key` giữ khoá `message-attachments/{uuid}.{đuôi chuẩn}`. Đuôi chuẩn theo loại đã nhận dạng:
  PDF `pdf`, DOCX `docx`, PNG `png`, JPEG `jpg` (như logo, `CompanyOwnerService.java:138`), WEBP `webp`. Thư mục này không có resource handler,
  không `permitAll` — chỉ đọc được qua M4.
- **R-F6.** Tên tệp hiển thị = tên gốc do client gửi, lấy phần sau dấu `/` hoặc `\` cuối cùng, bỏ ký tự
  điều khiển; rỗng → `tep-dinh-kem`. **Ép đuôi theo loại đã nhận dạng:** nếu đuôi của tên (không phân biệt
  hoa/thường) không thuộc loại đó — PDF `pdf`; DOCX `docx`; PNG `png`; JPEG `jpg`, `jpeg`; WEBP `webp` — thì nối
  thêm `.{đuôi chuẩn}` (`x.html` có nội dung PDF → `x.html.pdf`; `anh.JPEG` giữ nguyên). Sau cùng cắt phần tên
  trước đuôi sao cho cả tên tối đa 255 ký tự. Tên này chỉ để hiển thị và đặt `Content-Disposition`, không
  dùng làm đường dẫn lưu. Người nhận vì vậy không bao giờ tải về tệp có đuôi khác loại đã nhận dạng.
- **R-F6b. Giới hạn đã biết:** chữ ký DOCX là `PK\x03\x04` (`ResumeService.java:34`) — trùng mọi tệp ZIP
  (`.zip`, `.xlsx`, `.jar`). Tệp như vậy được nhận với loại `DOCX` và tải về với đuôi `.docx` (R-F6). Không
  mở ZIP để kiểm cấu trúc DOCX; cùng giới hạn với CV ở FR-U01.
- **R-F7.** M4 trả tệp với `Content-Type` theo loại đã nhận dạng lúc gửi (cột `attachment_type`), luôn
  `Content-Disposition: attachment` (không `inline`), tên tệp mã hoá UTF-8 như `ResumeHrController.java:36-37`.
- **R-F8.** Tin không có tệp, hoặc tệp không còn trong kho lưu trữ → 404 `MESSAGE_ATTACHMENT_NOT_FOUND`.
- **R-F9.** Thứ tự ghi như `ResumeService.upload` (`:82-99`): lưu tệp trước, ghi dòng DB sau. Ghi DB lỗi thì
  tệp mồ côi nằm lại trong kho — chấp nhận, không dọn.
- **R-F10.** Không xem trước ảnh trong luồng tin; mọi tệp hiện dạng thẻ tải về.

### 3.5 Đã đọc (R-R)

- **R-R1.** Mỗi tin có `read_at`. Tin "chưa đọc" đối với một người = tin của **bên kia** có `read_at IS NULL`.
- **R-R2.** Đánh dấu đã đọc là thao tác riêng (M3, `PATCH`), **không** là tác dụng phụ của `GET` M1. M3 đặt
  `read_at = now()` cho mọi tin của bên kia trong đơn đang `read_at IS NULL`, bằng một câu `UPDATE` có điều
  kiện; gọi lại không đổi gì thêm; trả 204.
- **R-R3.** M3 đồng thời đánh dấu đã đọc mọi thông báo `NEW_MESSAGE` chưa đọc của người gọi có
  `entity_id = applicationId` (cần cho R-N4): đặt **cả** `is_read = TRUE` **và** `read_at = now()`, như
  `NotificationService.markRead`.
- **R-R4.** M3 dùng được ở mọi trạng thái đơn, kể cả `WITHDRAWN`.
- **R-R5.** Trạng thái đã đọc chỉ dùng để đếm tin chưa đọc cho **bên nhận**. Bên gửi không thấy "đã xem";
  `read_at` không có trong bất kỳ response nào.
- **R-R6. Giới hạn đã biết, chấp nhận:** M3 đánh dấu mọi tin chưa đọc tại thời điểm gọi, kể cả tin vừa tới
  sau lần M1 gần nhất và chưa kịp hiện. Tin đó vẫn hiện ở lần tải lại kế tiếp (≤ 10 giây, tab đang mở và
  đang hiển thị); chỉ là nó không từng được đếm là "chưa đọc". M3 không nhận mốc "tin cuối đã thấy".

### 3.6 Hộp thư (R-I)

- **R-I1.** Mỗi dòng là một đơn có ít nhất một tin, sắp theo thời điểm tin mới nhất giảm dần. Phân trang
  `page`/`size` như thông báo (mặc định 20, tối đa 50 — `NotificationService.java:19-20`).
- **R-I2.** Mỗi dòng có: đơn, tên tin tuyển dụng, tên bên kia (HR thấy họ tên ứng viên; ứng viên thấy **tên
  công ty**, không thấy họ tên hay email của HR), trạng thái đơn, thời điểm tin mới nhất, đoạn trích tin mới
  nhất (R-N2), tin mới nhất có phải của mình không, tin mới nhất có tệp không, số tin chưa đọc.
- **R-I3.** Đơn vào tin đã đóng/xoá mềm và đơn đã rút vẫn hiện (nhất quán với danh sách đơn của ứng viên,
  FR-U08 mục 0.c).
- **R-I4.** Hộp thư chỉ đọc: không gọi M3. Tin chỉ thành "đã đọc" khi người dùng mở tab "Trao đổi".

### 3.7 Thông báo và email (R-N)

- **R-N1.** Gửi tin thành công → publish `MessageSentEvent` **trong** transaction ghi tin; listener
  `AFTER_COMMIT` + `REQUIRES_NEW` tạo thông báo cho **bên nhận** (mẫu `NotificationEventListener.java:54-64`).
  Bên nhận: tin của HR → `job_applications.candidate_id`; tin của ứng viên → `companies.owner_id` của tin.
  Người gửi không nhận thông báo về tin của chính mình. Lỗi tạo thông báo không làm hỏng việc gửi tin.
- **R-N2. Đoạn trích:** nội dung chữ, gộp mọi dãy khoảng trắng (kể cả xuống dòng) thành một dấu cách, bỏ
  khoảng trắng hai đầu; dài quá 120 ký tự thì lấy 120 ký tự đầu + `…`. "Ký tự" ở đây là **code point** Unicode (không cắt đôi
  emoji/cặp surrogate), khác R-M3 đếm bằng `String.length()`. Tin không có chữ → không có đoạn trích.
  Một hàm duy nhất, dùng cho cả thông báo và hộp thư.
- **R-N3. Nội dung thông báo** (`type = NEW_MESSAGE`, `entity_type = "APPLICATION"`, `entity_id =
  applicationId`):

  | Bên nhận | `title` | `body` khi có chữ | `body` khi chỉ có tệp | `link` |
  |---|---|---|---|---|
  | Ứng viên | `Tin nhắn mới từ nhà tuyển dụng` | `{tên công ty} đã gửi tin nhắn về đơn ứng tuyển vị trí "{tên tin}": {đoạn trích}` | `{tên công ty} đã gửi một tệp đính kèm về đơn ứng tuyển vị trí "{tên tin}"` | `/candidate/applications/{id}?tab=messages` |
  | HR | `Tin nhắn mới từ ứng viên` | `Ứng viên {họ tên} đã gửi tin nhắn về đơn ứng tuyển vị trí "{tên tin}": {đoạn trích}` | `Ứng viên {họ tên} đã gửi một tệp đính kèm về đơn ứng tuyển vị trí "{tên tin}"` | `/hr/applications/{id}?tab=messages` |

- **R-N4. Gộp thông báo:** nếu bên nhận đang có một thông báo `NEW_MESSAGE` **chưa đọc** của chính đơn đó thì
  **không** tạo thông báo mới (và không có email mới). Thông báo được đọc khi bấm vào nó ở chuông, hoặc khi
  bên nhận mở tab "Trao đổi" (R-R3). Kiểm ở tầng service, **không** thêm unique index: hai tin gần như đồng
  thời có thể tạo hai thông báo — vô hại, chấp nhận. Đây là **ngoại lệ có chủ đích** với CLAUDE.md §4 ("chỉ
  một X đang hoạt động phải chốt ở DB"): R-N4 là quy tắc giảm thư, không phải bất biến nghiệp vụ; chốt ở DB
  buộc phải sửa bảng `notifications` của FR-C03 (Q4).
- **R-N4b. Phạm vi tác dụng của việc gộp:** chỉ gộp được khi bên nhận **chưa mở** cuộc trao đổi. Khi bên nhận
  đang mở tab "Trao đổi", R-A3 đánh dấu đã đọc sau mỗi lần tải lại, nên mỗi tin tới sau đó vẫn sinh một
  thông báo và một email. Chấp nhận (Q4); không thêm điều kiện chặn theo thời gian.
- **R-N5. Email:** vẫn do `NotificationMailOrchestrator` gửi như mọi thông báo (tiêu đề = `title`, nội dung =
  `body`). Riêng `NEW_MESSAGE` có `link`: nội dung email = `body` + hai dấu xuống dòng + `Xem và trả lời tại:
  {app.frontend-base-url}{link}`. Thêm cấu hình `app.frontend-base-url` (mặc định `http://localhost:5173`).
  Email không đính kèm tệp. Email của 4 loại thông báo cũ **không đổi**.
- **R-N6.** Thông báo `NEW_MESSAGE` không chứa điểm, thứ hạng, rubric hay bất kỳ dữ liệu chấm nào; listener
  không đọc `scoring_runs`/`criterion_scores` (giữ đúng chú thích `NotificationContentBuilder.java:8-10`).

### 3.8 Tự tải lại (R-A)

- **R-A1.** Tab "Trao đổi" đang mở: tải lại M1 mỗi **10 giây**. Hộp thư đang mở: tải lại M5 mỗi **30 giây**.
  Không tải lại khi tab trình duyệt bị ẩn (mặc định của TanStack Query, không bật
  `refetchIntervalInBackground`).
- **R-A2.** Tự dừng sau **20 phút** liên tục, dùng lại `useStallGuardedPolling` (tách ra dùng chung, R-C4).
  Khi đã dừng hiện một dòng "Đã tạm dừng tự cập nhật." + nút "Tải lại" để chạy lại.
- **R-A3.** Sau mỗi lần M1 trả `unreadCount > 0` và tài liệu đang hiển thị (`document.visibilityState ===
  'visible'`), frontend gọi M3 rồi làm mới hộp thư và chuông.
- **R-A4.** Gửi tin thành công → làm mới M1 và M5 ngay, không chờ chu kỳ.
- **R-A5.** Không WebSocket, không SSE, không long-polling.

### 3.9 Giới hạn tần suất (R-L)

- **R-L1.** `POST` M2 ở cả hai phía vào nhóm mới `message` của `RateLimitFilter`, theo `userId` (khoá bucket
  `message:{userId}`, tách khỏi nhóm `llm-action` có khoá `llm:{userId}` — `RateLimitFilter.java:153`): sức chứa
  20, nạp lại 20/phút (`app.rate-limit.message.capacity`, `…refill-per-minute`). Vượt → 429
  `RATE_LIMIT_EXCEEDED` với câu có sẵn. Chỉ `POST` đúng hai mẫu đường dẫn
  `/api/hr/applications/*/messages` và `/api/candidates/applications/*/messages`; `GET`, `PATCH …/read` và
  các endpoint khác không bị giới hạn. Thêm nhóm buộc phải thêm tham số vào constructor package-private
  `RateLimitFilter(...)` (`:80-85`), nên helper `newFilter(...)` của `RateLimitFilterTest` (`:45-47`) được sửa
  chữ ký — **ngoại lệ đã duyệt** về sửa test có sẵn; không đổi kỳ vọng của ca test cũ nào.

### 3.10 Ràng buộc hiển thị (R-G)

- **R-G1.** Nội dung tin là lời của người, không phải AI: hiện văn bản thuần, giữ xuống dòng, không
  `dangerouslySetInnerHTML`, không tự biến URL thành liên kết, không khối "Do AI tạo".
- **R-G2.** Tab "Trao đổi" và hộp thư phía ứng viên không hiện điểm số, thứ hạng, tiêu chí, giải thích AI,
  ghi chú nội bộ; không hiện họ tên/email của HR (chỉ tên công ty).
- **R-G3.** Badge trạng thái đơn ở hộp thư dùng `ApplicationStatusBadge` (bảng trung tính). Số tin chưa đọc
  luôn kèm chữ cho trình đọc màn hình; không truyền đạt "chưa đọc" chỉ bằng màu.
- **R-G4.** 400/403/404 khi tải M1 hiện cùng một câu "Không tìm thấy cuộc trao đổi."; không phân biệt.

### 3.11 Thay đổi code dùng chung (R-C)

- **R-C1.** Thêm `storage/FileSignatures` — **một** chỗ nhận dạng loại tệp bằng magic bytes cho 5 loại (PDF,
  DOCX, PNG, JPEG, WEBP): `static Optional<DetectedFileType> detect(byte[] content)`, với enum mới
  `storage/DetectedFileType { PDF, DOCX, PNG, JPEG, WEBP }` có `extension()` trả đuôi chuẩn của R-F5. `storage/`
  không import `resume/`, `company/`, `messaging/`; mỗi nơi gọi tự ánh xạ sang kiểu của mình (`ResumeFileType`,
  đuôi logo, `AttachmentType`). Chữ ký chuyển nguyên từ `ResumeService.java:33-34` và
  `CompanyOwnerService.java:24-27`. `ResumeService` (vẫn chỉ nhận PDF/DOCX) và `CompanyOwnerService` (vẫn chỉ
  nhận PNG/JPEG/WEBP) chuyển sang gọi nó; xoá hai bản `private static` cũ. Thông báo lỗi, mã lỗi, đuôi tệp
  lưu của FR-U01/FR-H01 **không đổi**; toàn bộ test cũ của hai FR đó vẫn pass mà không sửa test.
- **R-C2.** Tab "Trao đổi" và hộp thư của hai phía dùng **chung** một bộ component ở `features/messages/`,
  nhận đường dẫn API gốc qua prop/tham số. `features/messages/` không import gì từ
  `features/applicationDetail`, `features/candidateApplicationDetail`, `features/scoring`.
- **R-C3.** `formatFileSize` chuyển từ `ResumeList.tsx:24-35` sang `lib/fileSize.ts`; `ResumeList` dùng bản
  chuyển, kết quả hiển thị không đổi. Không có bản thứ hai.
- **R-C4.** `useStallGuardedPolling` chuyển từ `features/notifications/queries.ts:26-44` sang
  `lib/useStallGuardedPolling.ts`; chuông dùng bản chuyển, hành vi không đổi.
- **R-C5.** Không sửa file trong `components/ui`. Không sửa DTO E1 của FR-H09/FR-U08.

### 3.12 Dữ liệu seed (R-S)

- **R-S1.** `seed-test.sql` thêm mục 14 "Tin nhắn" (UUID tiền tố `ed`, `ON CONFLICT (id) DO NOTHING`, mốc
  thời gian tuyệt đối tháng 10/2026, chỉ tin chữ, không tệp, không dòng thông báo):

  | Đơn | Tin | Mục đích soát |
  |---|---|---|
  | A2 (`INTERVIEW_INVITED`) | HR → UV đã đọc; UV → HR đã đọc; HR → UV **chưa đọc** | Luồng hai chiều; Quốc Huy có 1 tin chưa đọc |
  | A3 (`WITHDRAWN`) | UV → HR đã đọc; HR → UV đã đọc | Cuộc trao đổi chỉ còn xem |
  | A9 (Trần Bảo Ngọc) | UV → HR **chưa đọc** | HR có 1 tin chưa đọc |

  A1 không có tin (trạng thái rỗng). Mốc tin mới nhất: A3 ngày 05/10/2026, A2 ngày 07/10/2026, A9 ngày
  08/10/2026 — hộp thư HR vì vậy xếp **A9, A2, A3**; hộp thư Quốc Huy xếp **A2, A3**.
- **R-S2.** `reset-test-data.sql` xoá `application_messages` của `t_apps` trước khi xoá `job_applications`;
  `reset-demo-db.sql` thêm `application_messages` vào danh sách `TRUNCATE`; `db/seed/README.md` mục 8.4 thêm
  dòng soát tin nhắn. Bộ demo không thêm tin nhắn.

## 4. Dữ liệu & quyền truy cập

### 4.1 Migration `V12__application_messages.sql`

```sql
CREATE TABLE application_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    sender_id       UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    sender_role     VARCHAR(20) NOT NULL CHECK (sender_role IN ('HR', 'CANDIDATE')),
    body            TEXT,
    attachment_key  TEXT,
    attachment_name VARCHAR(255),
    attachment_type VARCHAR(10) CHECK (attachment_type IN ('PDF', 'DOCX', 'PNG', 'JPEG', 'WEBP')),
    attachment_size BIGINT,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_app_msg_has_content CHECK (body IS NOT NULL OR attachment_key IS NOT NULL),
    CONSTRAINT chk_app_msg_body_length CHECK (body IS NULL OR char_length(body) <= 4000),
    CONSTRAINT chk_app_msg_attachment_complete CHECK (
        (attachment_key IS NULL AND attachment_name IS NULL AND attachment_type IS NULL AND attachment_size IS NULL)
        OR (attachment_key IS NOT NULL AND attachment_name IS NOT NULL AND attachment_type IS NOT NULL
            AND attachment_size IS NOT NULL))
);
CREATE INDEX idx_app_msg_thread ON application_messages(application_id, created_at, id);
CREATE INDEX idx_app_msg_unread ON application_messages(application_id, sender_role) WHERE read_at IS NULL;
```

- `ON DELETE CASCADE` theo `application_status_history` (`V1__init_schema.sql:230`); `sender_id RESTRICT` theo
  `job_applications.candidate_id` (`V1:209`).
- Không có `updated_at`, `deleted_at`: tin không sửa, không xoá (R-P5). Cột duy nhất đổi sau khi tạo là
  `read_at`. Không thêm bảng này vào danh sách trigger `updated_at` (`V1:372-373`).
- `chk_app_msg_body_length` đếm bằng `char_length` (ký tự Unicode) nên luôn rộng hơn hoặc bằng giới hạn
  `String.length()` của R-M3 — là lưới an toàn, không phải nơi báo lỗi cho người dùng.
- Không đổi bảng `notifications` (mục 0.d2). Không đổi entity nào có sẵn.

### 4.2 Package và file backend

Package mới `messaging/` (CLAUDE.md §3: FR thật sự cần package riêng — không thuộc `jobapplication/` hay
`notification/`): `ApplicationMessage` (entity), `MessageSenderRole`, `AttachmentType`,
`ApplicationMessageRepository`, `MessageService`, **4 controller** — 2 theo đơn (`MessageHrController`,
`MessageCandidateController`) và 2 hộp thư (`ConversationHrController`, `ConversationCandidateController`) —
`MessageExcerpt`, `dto/`. Sự kiện
`MessageSentEvent` đặt ở `notification/` như các sự kiện có sẵn. Exception mới ở `common/exception/`.
Danh sách **đầy đủ** package dự án mà `messaging/` được import: `jobapplication/` (`HrApplicationAccess`,
`JobApplicationRepository`, `ApplicationStatus`), `company/` (nạp công ty theo `ownerId` cho M5 phía HR, tên công
ty), `job/` (tên tin), `user/` (họ tên ứng viên), `storage/`, `notification/` (chỉ để publish sự kiện),
`common/`. **Không** import `scoring/`, `ai/`, `resume/`, `rubric/`.

### 4.3 Endpoint

Tiền tố theo đơn: **HR** `/api/hr/applications/{applicationId}/messages` · **Ứng viên**
`/api/candidates/applications/{applicationId}/messages`. Hai controller theo đơn (M1–M4) và hai controller hộp
thư (M5) đều mỏng, gọi chung `MessageService`.

| # | Endpoint | Dùng ở | Thành công | Lỗi riêng |
|---|---|---|---|---|
| M1 | `GET {tiền tố}` | Tab Trao đổi | 200 `MessageThreadResponse` | — |
| M2 | `POST {tiền tố}` — form field `body` (tuỳ chọn), `file` (tuỳ chọn) | Khung soạn | 201 `MessageResponse` | 409 `CONVERSATION_READ_ONLY`; 400 `MESSAGE_EMPTY`, `MESSAGE_TOO_LONG`, `INVALID_MESSAGE_ATTACHMENT`; 429 `RATE_LIMIT_EXCEEDED` |
| M3 | `PATCH {tiền tố}/read` | Khi mở tab | 204 | — |
| M4 | `GET {tiền tố}/{messageId}/attachment` | Thẻ tệp | 200 + tệp | 404 `MESSAGE_NOT_FOUND`, `MESSAGE_ATTACHMENT_NOT_FOUND` |
| M5 | `GET /api/hr/messages/conversations?page&size` · `GET /api/candidates/messages/conversations?page&size` | Hộp thư | 200 `PageResponse<…>` | HR: 404 `COMPANY_NOT_FOUND` |

Lỗi chung của M1–M4: 401 `UNAUTHENTICATED`; 403 sai vai trò (filter chain); phía HR 404 `COMPANY_NOT_FOUND`,
404 `APPLICATION_NOT_FOUND`, 403 đơn công ty khác; phía ứng viên 404 `APPLICATION_NOT_FOUND`; 400 mặc định của
Spring khi id không phải UUID. Câu `message` tiếng Việt của từng mã lỗi mới: UI.md mục 7.

M2 không khai `consumes`: nhận `multipart/form-data` (giao diện) và `application/x-www-form-urlencoded` (tin
chỉ có chữ — dùng cho khối kiểm bằng PowerShell 5.1 ở 7.2, vì bản này không có `-Form`). Plan Mode phải xác
nhận bằng test rằng tham số `file` không bắt buộc nhận `null` với request urlencoded; không được thì dừng báo.

### 4.4 DTO

```java
// M1 - dung chung hai phia. KHONG them field nao khac.
public record MessageThreadResponse(
        boolean canSend,              // R-M8
        boolean olderMessagesHidden,  // R-M7
        int unreadCount,              // so tin cua BEN KIA chua doc (R-A3)
        List<MessageResponse> messages) {}

// M1, M2
public record MessageResponse(
        UUID id,
        MessageSenderRole senderRole, // HR | CANDIDATE
        boolean mine,                 // senderRole == vai tro nguoi goi
        String body,                  // nguyen van sau R-M2; null khi chi co tep
        Attachment attachment,        // null khi khong co tep
        Instant createdAt) {

    public record Attachment(String fileName, AttachmentType fileType, long fileSize) {}
}

// M5 phia HR
public record ConversationHrResponse(
        UUID applicationId, UUID jobId, String jobTitle, String candidateName,
        ApplicationStatus applicationStatus, Instant lastMessageAt, String lastMessageExcerpt,
        boolean lastMessageMine, boolean lastMessageHasAttachment, int unreadCount) {}

// M5 phia ung vien
public record ConversationCandidateResponse(
        UUID applicationId, UUID jobId, String jobTitle, String companyName,
        ApplicationStatus applicationStatus, Instant lastMessageAt, String lastMessageExcerpt,
        boolean lastMessageMine, boolean lastMessageHasAttachment, int unreadCount) {}
```

Khoá **cấm** ở mọi cấp lồng của mọi response trên (test T6, so khớp nguyên tên khoá): `senderId`, `readAt`,
`attachmentKey`, `fileUrl`, `candidateId`, `ownerId`, `email`, `totalScore`, `rank`, `criterionScores`,
`explanation`, `scoringRunId`, và các khoá cấm của CLAUDE.md §7: `verdict`, `label`, `isQualified`, `passed`,
`recommendation`. Riêng response phía ứng viên cấm thêm `candidateName`, `hrName`.

### 4.5 Thay đổi ở `notification/`

- `NotificationType` thêm `NEW_MESSAGE`; frontend `features/notifications/types.ts` thêm cùng giá trị.
- `MessageSentEvent(UUID applicationId, UUID jobId, UUID candidateId, boolean sentByHr, String excerpt,
  boolean hasAttachment)` — `excerpt` đã qua R-N2, `null` khi tin không có chữ.
- `NotificationContentBuilder.forNewMessage(…)` theo bảng R-N3; `NotificationEventListener.onMessageSent`
  theo R-N1, R-N4.
- `NotificationRepository` thêm: kiểm tồn tại thông báo `NEW_MESSAGE` chưa đọc theo
  (`userId`, `entityId`); `UPDATE` đánh dấu đã đọc theo (`userId`, `type`, `entityId`) cho R-R3.
- `NotificationMailOrchestrator`: thêm dòng liên kết cho `NEW_MESSAGE` (R-N5).

## 5. AI

Không dùng AI. Không gọi LLM/embedding, không thuộc danh sách ngoại lệ K3, không dùng và không xây K1–K4.
Soạn nháp bằng AI thuộc FR-C07 (FR-C07 tự thêm nút vào khung soạn và tự đọc các tin gần nhất qua K1).

## 6. Ngoài phạm vi

- Soạn nháp bằng AI (FR-C07); che tên ứng viên ở tab Trao đổi/hộp thư khi ẩn danh (FR-H16).
- Thời gian thực (WebSocket/SSE); chỉ báo "đang nhập"; hiển thị "đã xem" cho bên gửi (R-R5).
- Sửa, xoá, thu hồi tin; tìm kiếm trong tin nhắn; ghim, lưu trữ, tắt thông báo cuộc trao đổi.
- Nhiều tệp trong một tin; xem trước ảnh/PDF trong luồng tin; quét virus; dọn tệp mồ côi (R-F9).
- Gửi hàng loạt; nhắn tới ứng viên chưa nộp đơn; nhắn giữa các ứng viên hay giữa các HR.
- Số tin chưa đọc trên mục "Tin nhắn" của thanh điều hướng và trên nhãn tab "Trao đổi" (chuông thông báo đã
  báo tin mới; thêm số ở đây cần một lượt tải lại định kỳ trên mọi trang).
- Tin hệ thống tự sinh khi đơn đổi trạng thái; tin nhắn không thay thế thao tác đổi trạng thái của FR-H07.
- Tải thêm tin cũ hơn 200 tin gần nhất (R-M7).
- Sửa `NotificationList` để điều hướng theo `link` (nợ có sẵn, mục 0.d7); thêm liên kết vào email của 4 loại
  thông báo cũ.
- Đổi mã `INVALID_RESUME_FILE` của lỗi vượt giới hạn multipart (R-F4).
- Thêm tin nhắn vào bộ dữ liệu demo.

## 7. Xong khi

### 7.1 Test backend (Testcontainers)

Mỗi lớp tự tạo toàn bộ dữ liệu của mình (email duy nhất, prefix ≤ 27 ký tự — nợ `uniqueEmail` của FR-H09),
không đọc seed. Lớp nào khẳng định thứ tự thời gian giữa các tin thì **không** bọc `@Transactional` cấp lớp
(CLAUDE.md §3c). Tệp nhị phân dựng bằng mảng byte trong test, không thêm fixture.

| # | Test | Loại |
|---|---|---|
| T1 | HR gửi tin chữ vào đơn của công ty mình → 201; ứng viên chủ đơn gọi M1 thấy đúng 1 tin `mine = false`, `senderRole = HR`, `body` đúng; HR gọi M1 thấy `mine = true`. Chiều ngược lại (ứng viên gửi trước) tương tự | dương |
| T2 | Phía HR: đơn của công ty khác → M1, M2, M3, M4 đều **403**; UUID không tồn tại → 404 `APPLICATION_NOT_FOUND`; HR chưa có công ty → 404 `COMPANY_NOT_FOUND`. Sau các lời gọi M2/M3 bị chặn, bảng `application_messages` không đổi | âm |
| T3 | Phía ứng viên: đơn của ứng viên khác và UUID không tồn tại → M1, M2, M3, M4 đều 404 `APPLICATION_NOT_FOUND`, hai body cùng mã; bảng không đổi | âm |
| T4 | Sai vai trò: token ứng viên gọi `/api/hr/…/messages` → 403; token HR gọi `/api/candidates/…/messages` → 403; không token → 401 `UNAUTHENTICATED`; tương tự cho M5 | âm |
| T5 | Thứ tự: 3 tin gửi lần lượt (HR, UV, HR) → M1 trả đúng thứ tự gửi, `createdAt` không giảm | dương |
| T6 | JSON của M1, M2, M5 hai phía (duyệt đệ quy mọi khoá) không chứa khoá cấm ở mục 4.4, so khớp nguyên tên khoá; response ứng viên của M5 không chứa họ tên và email của HR ở bất kỳ giá trị chuỗi nào | âm |
| T7 | Nội dung: `body` = 4000 ký tự → 201; 3999 → 201; 4001 → 400 `MESSAGE_TOO_LONG`; chỉ khoảng trắng, không tệp → 400 `MESSAGE_EMPTY`; không có cả `body` lẫn `file` → 400 `MESSAGE_EMPTY` | biên + âm |
| T8 | Nguyên văn: `body` = `"  <b>x</b> & y\r\ndòng 2\r\n"` → M1 trả `"  <b>x</b> & y\ndòng 2\n"` (chỉ đổi CRLF, giữ khoảng trắng đầu, thẻ HTML và `&`); 3999 ký tự + `\r\n` → 201 (sau chuẩn hoá đúng 4000) | dương + biên |
| T9 | Trạng thái đơn: gửi được ở `PENDING`, `INTERVIEW_INVITED`, `HIRED`, `REJECTED` (mỗi trạng thái một ca, cả hai phía); `WITHDRAWN` → 409 `CONVERSATION_READ_ONLY` cho cả hai phía, bảng không đổi, kho lưu trữ không có tệp mới; M1 của đơn `WITHDRAWN` → 200 với `canSend = false`, các trạng thái khác `canSend = true`. Thứ tự kiểm (R-M5): đơn `WITHDRAWN` + `body` rỗng → **409** (không phải 400); đơn của bên khác + `body` rỗng → 403/404 (không phải 400) | dương + âm |
| T10 | Tệp hợp lệ: gửi lần lượt PDF, DOCX, PNG, JPEG, WEBP (mảng byte có đúng chữ ký) kèm tên có dấu tiếng Việt → 201, `attachment.fileType` đúng loại, `fileName` đúng tên gốc, `fileSize` đúng; gửi tệp không kèm chữ → 201, `body = null` | dương |
| T11 | Tệp sai: nội dung GIF/văn bản thuần → 400 `INVALID_MESSAGE_ATTACHMENT`; tệp nội dung văn bản đặt tên `a.pdf` và `Content-Type: application/pdf` → 400 (không tin tên/`Content-Type`); tệp nội dung PDF đặt tên `a.txt` → 201 với `fileType = PDF`, `fileName = "a.txt.pdf"` (R-F6); nội dung ZIP đặt tên `x.html` → 201 với `fileType = DOCX`, `fileName = "x.html.docx"` (R-F6b); nội dung JPEG tên `anh.JPEG` → `fileName` giữ nguyên; tệp 0 byte → 400 | âm + dương |
| T12 | Dung lượng: 5MB − 1 byte → 201; đúng 5MB → 201; 5MB + 1 byte → 400 `INVALID_MESSAGE_ATTACHMENT` | biên |
| T13 | Tải tệp: người gửi và người nhận tải M4 → 200, nội dung byte trùng tệp đã gửi, `Content-Disposition` bắt đầu bằng `attachment`, `Content-Type` theo loại nhận dạng; `messageId` của đơn **khác** mà người gọi cũng có quyền → 404 `MESSAGE_NOT_FOUND`; tin không có tệp → 404 `MESSAGE_ATTACHMENT_NOT_FOUND`; tin có tệp nhưng tệp đã bị xoá khỏi kho lưu trữ (test tự xoá tệp theo khoá trong DB) → 404 `MESSAGE_ATTACHMENT_NOT_FOUND` (R-F8); `GET /uploads/message-attachments/{tên tệp}` có token hợp lệ → không trả 200 | dương + âm |
| T14 | Tên tệp: tên gốc `..\..\x/../báo cáo.pdf` → `fileName = "báo cáo.pdf"`; khoá lưu trong DB có dạng `message-attachments/{uuid}.pdf`, không chứa tên gốc | âm |
| T15 | Đã đọc: HR gửi 2 tin → M1 phía ứng viên `unreadCount = 2`, phía HR `unreadCount = 0`; ứng viên gọi M3 → 204, `unreadCount = 0`, `read_at` của 2 tin khác null; tin ứng viên tự gửi không bị M3 của chính họ đánh dấu; gọi M3 lần hai → 204, `read_at` không đổi; chỉ gọi M1 (không M3) **không** làm đổi `read_at`; M3 trên đơn `WITHDRAWN` còn tin chưa đọc → 204 và `unreadCount = 0` (R-R4); đơn có 201 tin của bên kia chưa đọc → `unreadCount = 201` dù M1 chỉ trả 200 tin và `olderMessagesHidden = true` (R-M7) | dương + âm + biên |
| T16 | Hộp thư: đơn chưa có tin không xuất hiện; hai đơn có tin sắp theo tin mới nhất trước; `unreadCount`, `lastMessageMine`, `lastMessageHasAttachment`, `lastMessageExcerpt` đúng; HR không thấy đơn của công ty khác, ứng viên không thấy đơn của người khác; `size = 51` → trả tối đa 50; đơn `WITHDRAWN` có tin vẫn hiện | dương + âm + biên |
| T17 | Đoạn trích (test đơn vị `MessageExcerpt`): 120 ký tự → giữ nguyên; 121 → 120 ký tự + `…`; 119 → giữ nguyên; `"a \n\n  b"` → `"a b"`; `null`/chỉ khoảng trắng → `null`; 119 chữ cái + 2 emoji ngoài BMP (121 code point) → 119 chữ cái + emoji thứ nhất **nguyên vẹn** + `…` (không cắt đôi cặp surrogate) | biên |
| T18 | Thông báo: HR gửi tin → ứng viên có đúng 1 thông báo `NEW_MESSAGE`, `title`/`body`/`link` đúng bảng R-N3, `entityId = applicationId`, `emailStatus = PENDING`; HR **không** có thông báo nào mới. Chiều ngược lại: người nhận là chủ công ty | dương + âm |
| T19 | Gộp thông báo: HR gửi 3 tin liên tiếp → ứng viên vẫn chỉ 1 thông báo `NEW_MESSAGE` chưa đọc; ứng viên gọi M3 → thông báo đó `isRead = true` **và** `readAt` khác null; HR gửi tin thứ 4 → có thông báo `NEW_MESSAGE` thứ hai. Thông báo loại khác của cùng đơn không bị M3 đánh dấu đã đọc | dương + âm |
| T20 | Thông báo chỉ có tệp: `body` theo cột "chỉ có tệp" của R-N3, không có đoạn trích; tin có chữ dài 300 ký tự → `body` kết thúc bằng 120 ký tự đầu + `…` | dương + biên |
| T21 | Email (mở rộng `NotificationMailOrchestratorIntegrationTest`): thông báo `NEW_MESSAGE` → nội dung email kết thúc bằng `Xem và trả lời tại: http://localhost:5173/candidate/applications/{id}?tab=messages`; thông báo `APPLICATION_STATUS_CHANGED` có `link` → nội dung email **bằng đúng** `body`, không có dòng liên kết | dương + âm |
| T22 | `FileSignaturesTest` (đơn vị): 5 chữ ký đúng → đúng loại; mảng ngắn hơn chữ ký, mảng rỗng, `RIFF` không kèm `WEBP` ở offset 8 → rỗng. Toàn bộ test có sẵn của FR-U01 và FR-H01 pass **không sửa** | dương + âm |
| T23 | `RateLimitFilterTest` thêm: `POST` hai mẫu đường dẫn M2 dùng nhóm `message` theo `userId` — yêu cầu thứ 20 qua, thứ 21 nhận 429; `GET` cùng đường dẫn và `PATCH …/read` không bị giới hạn; nhóm `message` (khoá `message:`) không dùng chung bucket với nhóm `llm-action` (khoá `llm:`): dùng hết 20 lượt `message` thì lượt `llm-action` của cùng `userId` vẫn qua. Các ca test cũ giữ nguyên kỳ vọng (chỉ `newFilter(...)` đổi chữ ký, R-L1) | biên + âm |
| T24 | M2 với `Content-Type: application/x-www-form-urlencoded` và chỉ field `body` → 201 (mục 4.3) | dương |
| T25 | Tin tuyển dụng không còn mở (R-Q5): đơn vào tin `CLOSED`, và đơn vào tin đã xoá mềm (`deleted_at` khác null) → cả hai phía M1 200, M2 201; đơn vẫn xuất hiện ở M5 | dương |
| T26 | Lỗi tạo thông báo không làm hỏng gửi tin (R-N1): lớp test riêng thay `NotificationRepository` bằng spy ném `RuntimeException` ở `save` → M2 vẫn 201, dòng `application_messages` đã lưu, M1 thấy tin | âm |

Tầng test không gọi LLM thật; FR này không cần stub `ChatModel` mới.

Kiểm tĩnh (R-N6, mục 4.2): tìm `import com.recruitment.(scoring|ai)` trong
`backend/src/main/java/com/recruitment/messaging` → 0 dòng; tìm `scoring` trong `NotificationContentBuilder.java`
và `MessageSentEvent.java` → chỉ còn dòng chú thích có sẵn (`NotificationContentBuilder.java:9`).

### 7.2 Kiểm bằng HTTP — Windows PowerShell 5.1

Nạp **cả** seed demo và seed test (`db/seed/README.md` mục 1 và 8). Backend chạy ở `localhost:8080`. Dán **cả
khối** vào **một** cửa sổ PowerShell. Khối này có gửi 1 tin vào đơn A1 — chạy xong thì chạy lại
`reset-test-data.sql` rồi nạp lại seed test.

```powershell
$base = 'http://localhost:8080'

function Get-Token([string]$Email, [string]$Password) {
    $body = @{ email = $Email; password = $Password } | ConvertTo-Json
    (Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' -Body $body).accessToken
}

# In "<ma HTTP> <phuong thuc> <duong dan> <than loi neu co>" cho ca thanh cong lan 4xx.
function Test-Call([string]$Method, [string]$Path, [string]$Token, $Form) {
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    try {
        if ($null -ne $Form) {
            $r = Invoke-WebRequest -UseBasicParsing -Method $Method -Uri "$base$Path" -Headers $headers -Body $Form
        } else {
            $r = Invoke-WebRequest -UseBasicParsing -Method $Method -Uri "$base$Path" -Headers $headers
        }
        '{0} {1} {2}' -f [int]$r.StatusCode, $Method, $Path
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

$tokenHr     = Get-Token 'minhdathr@gmail.com' '12345678'
$tokenHrDemo = Get-Token 'hr@demo.local' 'Demo1234'
$tokenUv     = Get-Token 'quochuyuv@gmail.com' '12345678'
$tokenLan    = Get-Token 'le.thi.lan@test.local' '12345678'

$a1 = 'e8000000-0000-0000-0000-000000000001'  # don A1 cua Quoc Huy, PENDING
$a3 = 'e8000000-0000-0000-0000-000000000003'  # don A3 cua Quoc Huy, WITHDRAWN
$missing = '00000000-0000-0000-0000-000000000000'
$hrA1 = "/api/hr/applications/$a1/messages"
$uvA1 = "/api/candidates/applications/$a1/messages"
$uvA3 = "/api/candidates/applications/$a3/messages"

'--- 1. Duong: HR test va Quoc Huy doc cuoc trao doi cua don A1 (mong doi 200 x2)'
Test-Call 'Get' $hrA1 $tokenHr $null
Test-Call 'Get' $uvA1 $tokenUv $null

'--- 2. Am: HR cong ty khac doc va gui vao don A1 (mong doi 403 x2)'
Test-Call 'Get' $hrA1 $tokenHrDemo $null
Test-Call 'Post' $hrA1 $tokenHrDemo @{ body = 'Xin chao' }

'--- 3. Am: ung vien khac doc va gui vao don A1 (mong doi 404 APPLICATION_NOT_FOUND x2)'
Test-Call 'Get' $uvA1 $tokenLan $null
Test-Call 'Post' $uvA1 $tokenLan @{ body = 'Xin chao' }

'--- 4. Am: don khong ton tai (mong doi 404 APPLICATION_NOT_FOUND x2)'
Test-Call 'Get' "/api/hr/applications/$missing/messages" $tokenHr $null
Test-Call 'Get' "/api/candidates/applications/$missing/messages" $tokenUv $null

'--- 5. Am: sai vai tro (mong doi 403 x2) va khong token (mong doi 401 UNAUTHENTICATED)'
Test-Call 'Get' $hrA1 $tokenUv $null
Test-Call 'Get' $uvA1 $tokenHr $null
Test-Call 'Get' $uvA1 $null $null

'--- 6. Am: hop thu sai vai tro (mong doi 403 x2)'
Test-Call 'Get' '/api/hr/messages/conversations' $tokenUv $null
Test-Call 'Get' '/api/candidates/messages/conversations' $tokenHr $null

'--- 7. Am: gui vao don da rut (mong doi 409 CONVERSATION_READ_ONLY)'
Test-Call 'Post' $uvA3 $tokenUv @{ body = 'Xin chao' }

'--- 8. Am: tin rong (mong doi 400 MESSAGE_EMPTY)'
Test-Call 'Post' $uvA1 $tokenUv @{ body = '   ' }

'--- 9. Duong: Quoc Huy gui 1 tin vao don A1 (mong doi 201), HR danh dau da doc (mong doi 204)'
Test-Call 'Post' $uvA1 $tokenUv @{ body = 'Xin chao, toi muon hoi ve tien do ho so.' }
Test-Call 'Patch' "$hrA1/read" $tokenHr $null

'--- 10. Duong: hop thu hai phia (mong doi 200 x2)'
Test-Call 'Get' '/api/hr/messages/conversations' $tokenHr $null
Test-Call 'Get' '/api/candidates/messages/conversations' $tokenUv $null
```

Mong đợi: nhóm 1 hai dòng `200`; nhóm 2 hai dòng `403`; nhóm 3 và 4 mỗi nhóm hai dòng `404 …
{"error":"APPLICATION_NOT_FOUND",…}`; nhóm 5 `403`, `403`, `401 … UNAUTHENTICATED`; nhóm 6 hai dòng `403`; nhóm
7 `409 … CONVERSATION_READ_ONLY`; nhóm 8 `400 … MESSAGE_EMPTY`; nhóm 9 `201` rồi `204`; nhóm 10 hai dòng `200`.

**Bắt buộc ở đợt code cuối:** chạy thật khối trên, dán **nguyên văn** output vào báo cáo đợt. Lệnh sai cú pháp
hoặc cho kết quả khác "Mong đợi" vì lệnh viết sai thì dừng, đề xuất sửa mục này, chờ duyệt lại.

### 7.3 Frontend

- `npm run build` + `npm run lint` sạch.
- Tìm `dangerouslySetInnerHTML` trong `frontend/src` → 0 dòng.
- Tìm `features/(applicationDetail|candidateApplicationDetail|scoring)` trong `frontend/src/features/messages`
  → 0 dòng (R-C2).
- Tìm `totalScore|rank|criterionScores|explanation` trong `frontend/src/features/messages`,
  `frontend/src/pages/HrMessagesPage.tsx`, `frontend/src/pages/CandidateMessagesPage.tsx` → 0 dòng.
- Tìm `function formatFileSize` trong `frontend/src` → **đúng 1** dòng, ở `lib/fileSize.ts` (R-C3).
- Tìm `function useStallGuardedPolling` trong `frontend/src` → **đúng 1** dòng, ở
  `lib/useStallGuardedPolling.ts` (R-C4).
- Tìm `WebSocket|EventSource` trong `frontend/src` → 0 dòng (R-A5).
- Lệnh tìm token cũ của Phase 2.1b (`docs/ROADMAP.md`, "Xong khi" của Phase 2.1b) vẫn trả 0 dòng.

### 7.4 Soát tay (desktop ≥ `lg`, 768px và 375px) — seed test, mật khẩu `12345678`

| Cần thấy | Tài khoản → nơi | Mong đợi |
|---|---|---|
| Rỗng | Quốc Huy → A1 → tab Trao đổi | Câu trạng thái rỗng, khung soạn dùng được |
| Hai chiều, có tin chưa đọc | Quốc Huy → A2 → tab Trao đổi | 3 tin đúng thứ tự, tin của mình bên phải; mở tab xong thì hộp thư hết số chưa đọc của A2 |
| Chỉ còn xem | Quốc Huy → A3 → tab Trao đổi | 2 tin; không có khung soạn; dòng "Đơn đã rút…" |
| Hộp thư ứng viên | Quốc Huy → Tin nhắn | 2 dòng theo thứ tự **A2, A3** (R-S1), hiện tên công ty, A2 có số chưa đọc `1`; không hiện tên HR |
| Hộp thư HR | HR test → Tin nhắn | 3 dòng theo thứ tự **A9, A2, A3** (R-S1), A9 có số chưa đọc `1`, hiện họ tên ứng viên + badge trạng thái |
| Gửi chữ nhiều dòng | HR test → A2 | Tin hiện ngay, giữ xuống dòng; nhập `<b>x</b>` hiện đúng chuỗi đó, không in đậm |
| Gửi tệp | HR test → A2, kèm 1 PDF < 5MB | Thẻ tệp có tên + dung lượng; bấm tải về đúng tệp; đăng nhập Quốc Huy tải được cùng tệp |
| Tệp sai | Chọn tệp `.txt`; chọn tệp > 5MB | Câu lỗi tại khung soạn, nội dung đang nhập còn nguyên, không gửi |
| Ép đuôi tên tệp | HR test → A2, gửi một tệp PDF đã đổi tên thành `thu.html` | Thẻ tệp hiện `thu.html.pdf`; tải về đúng tên đó |
| Tự cập nhật | Hai trình duyệt: HR test và Quốc Huy cùng mở A2 | Tin của bên này hiện ở bên kia trong ≤ 10 giây, không tải lại trang |
| Thông báo + email | HR test **không** mở tab Trao đổi của A1 (đang ở trang khác). Quốc Huy gửi tin ở A1 → HR test | Chuông HR có "Tin nhắn mới từ ứng viên", bấm mở thẳng tab Trao đổi của A1; MailHog (`localhost:8025`) có email kèm dòng liên kết; gửi thêm 2 tin khi HR chưa đọc → không có thông báo/email mới |
| Rút đơn giữa chừng | Quốc Huy rút A1 khi HR đang mở tab Trao đổi của A1 → HR bấm Gửi | Câu lỗi 409, khung soạn biến mất, badge đầu trang thành "Đã rút đơn" |
| Không tìm thấy | `/hr/applications/00000000-0000-0000-0000-000000000000?tab=messages` | Câu "Không tìm thấy đơn ứng tuyển." của trang (E1), không gọi M1 |
| Tab cũ | Cả hai trang | HR: 3 tab cũ đúng nhãn và thứ tự "CV & điểm", "Giải thích", "Lịch sử", rồi mới tới "Trao đổi"; mở không có `?tab=` hoặc `?tab=abc` → về "CV & điểm". Ứng viên: "Thông tin đơn", "Lịch sử", rồi "Trao đổi"; `?tab=abc` → về "Thông tin đơn". `git diff` của đợt 5 không chạm file nào của các tab cũ |
| Điều hướng | 768px, 1024px, 1280px, hai phía | Mục "Tin nhắn" hiện, sáng ở trang hộp thư; thanh điều hướng ứng viên không tràn, không xuống dòng |
| Trang CV | Quốc Huy → Hồ sơ và CV | Cột dung lượng tệp hiện như trước (R-C3); tải CV lên vẫn chặn đúng loại (R-C1) |
| Logo công ty | HR test → Hồ sơ công ty, tải logo PNG và tệp `.txt` | PNG nhận, `.txt` bị từ chối với câu cũ (R-C1) |

Xong chạy `reset-test-data.sql` rồi nạp lại seed test.

## 8. AI hay làm sai (dành cho người code)

- Tạo bảng `conversations` "cho đúng mô hình chat" (R-P1), hoặc thêm cột `conversation_id`.
- Viết kiểm quyền mới thay vì dùng `HrApplicationAccess.loadOwned`/`findByIdAndCandidateId`; "sửa cho đồng
  bộ" 403 phía HR thành 404 hoặc ngược lại (R-Q1, R-Q2).
- Một endpoint dùng chung hai vai trò kiểu `/api/messages/**` rồi tự phân nhánh theo role (mục 0.b).
- Nhận `senderRole`/`senderId` từ request (R-Q3).
- Tải tệp chỉ bằng `messageId`, hoặc phục vụ tệp qua `/uploads/**` (R-Q4, R-F5); trả `attachmentKey`/URL tệp
  trong DTO.
- Tin loại tệp theo đuôi tên hoặc `Content-Type` của client (R-F2); trả tệp `inline` (R-F7).
- Viết bản kiểm magic bytes thứ ba trong `messaging/` thay vì gom về `FileSignatures`; hoặc khi gom thì cho
  CV nhận ảnh / logo nhận PDF, đổi câu lỗi cũ, sửa test cũ cho xanh (R-C1).
- `trim()` nội dung, lọc/escape HTML khi lưu, hoặc quên đổi CRLF nên tin 3999 ký tự + xuống dòng bị từ chối
  (R-M2, T8).
- Đánh dấu đã đọc ngay trong `GET` M1 (R-R2) — dự án đã có nợ "GET có tác dụng phụ ghi DB"
  (`docs/ROADMAP.md`, nợ `chore/seed-test`), không thêm cái thứ hai.
- M3 đánh dấu cả tin của chính người gọi, hoặc đánh dấu thông báo loại khác của cùng đơn (R-R1, R-R3).
- Trả `readAt`, hiện "Đã xem" cho bên gửi (R-R5).
- Quên R-R3 → thông báo `NEW_MESSAGE` không bao giờ được đọc nếu người dùng vào bằng hộp thư, và R-N4 chặn
  mọi thông báo về sau của đơn đó.
- Tạo thông báo cho chính người gửi; tạo thông báo trong cùng transaction với việc ghi tin thay vì
  `AFTER_COMMIT` + `REQUIRES_NEW` (R-N1); để lỗi tạo thông báo làm hỏng việc gửi tin.
- Thêm unique index cho R-N4, hoặc bỏ R-N4 "cho đơn giản" (mỗi tin một email); thêm điều kiện chặn theo thời
  gian để "sửa" R-N4b.
- Giữ nguyên tên gốc khi đuôi không khớp loại đã nhận dạng (R-F6); cắt đoạn trích bằng `substring` theo
  `char` làm vỡ emoji (R-N2); để `FileSignatures` trả `ResumeFileType`/`AttachmentType` (R-C1).
- Đếm `unreadCount` chỉ trên 200 tin được trả (R-M7); M3 chỉ đặt `is_read` mà quên `read_at` của thông báo
  (R-R3).
- Đổi kỳ vọng của ca test cũ trong `RateLimitFilterTest` thay vì chỉ sửa chữ ký `newFilter` (R-L1).
- Thêm dòng liên kết vào email của mọi loại thông báo (R-N5) — làm đỏ test email có sẵn.
- Đưa điểm, thứ hạng, tên tiêu chí vào `body` thông báo hay vào DTO hộp thư "cho HR tiện xem" (R-N6).
- Hiện họ tên/email HR cho ứng viên (R-I2, R-G2).
- Dùng `@Transactional` cấp lớp cho test thứ tự tin — `now()` của Postgres theo transaction, mọi tin cùng
  `created_at` (CLAUDE.md §3c).
- Dùng WebSocket/SSE, hoặc `refetchIntervalInBackground: true`, hoặc tải lại vô hạn không có ngưỡng dừng
  (R-A).
- Gọi M3 từ trang hộp thư (R-I4), hoặc gọi M3 khi tab trình duyệt đang ẩn (R-A3).
- Dựng nút "Soạn bằng AI" khoá sẵn, hoặc để chỗ trống cho nó (R-P6).
- Render nội dung tin bằng HTML, tự biến URL thành liên kết (R-G1).
- Viết riêng hai bộ component cho HR và ứng viên (R-C2); import từ thư mục tính năng phía HR vào
  `features/messages`.
- Sửa handler `MaxUploadSizeExceededException` để trả mã mới (R-F4).
- Thêm `application_messages` vào danh sách trigger `updated_at`, hoặc thêm `deleted_at` (mục 4.1).
- Sửa DTO E1 của FR-H09/FR-U08 để thêm số tin chưa đọc (R-C5, mục 6).
- Quên sửa `reset-test-data.sql`/`reset-demo-db.sql` (R-S2).
- Prefix `uniqueEmail` dài hơn 27 ký tự.

## 9. Kế hoạch đợt code

Mỗi đợt: dừng → báo cáo diff → chờ duyệt → một commit. Frontend: `npm run build` + `npm run lint` sau mỗi đợt
có sửa frontend.

**Ngoại lệ đã duyệt với CLAUDE.md §5** ("LUÔN chạy đầy đủ"; tiền lệ `U07/REQUIREMENT.md:418`, Q7): giữa các
đợt backend chỉ chạy `.\mvnw.cmd test-compile` và **riêng** các lớp test nêu ở cột Kiểm; full suite
`.\mvnw.cmd test` chạy **đúng một lần** ở đợt 7. Vì đợt 3 và 4 sửa code dùng chung, cột Kiểm của hai đợt đó
phải gồm **mọi** lớp test có sẵn của phần bị chạm (Plan Mode liệt kê tên lớp cụ thể bằng cách tìm trong
`backend/src/test`): đợt 3 — mọi lớp test nhắc tới `ResumeService`/upload CV và `CompanyOwnerService`/logo;
đợt 4 — mọi lớp trong `notification/` và `ratelimit/`. Full suite đỏ ở đợt 7 thì sửa trong đợt 7, không báo
xong.

| Đợt | Nội dung | Kiểm |
|---|---|---|
| 1 | Plan Mode: đối chiếu đặc tả đã duyệt với code (mục 0), xác nhận T24, chốt danh sách file | — |
| 2 | Backend lõi: `V12`, entity, `MessageService` gửi/đọc/đã đọc chỉ với tin chữ (M1–M3), hai controller theo đơn, exception + handler; T1–T9, T15, T24 | test-compile; `-Dtest` các lớp mới |
| 3 | Backend tệp + hộp thư: `FileSignatures` + chuyển `ResumeService`/`CompanyOwnerService` (R-C1), tệp ở M2, M4, M5, `MessageExcerpt`; T10–T14, T16, T17, T22 | test-compile; `-Dtest` lớp mới + lớp test có sẵn của FR-U01, FR-H01 |
| 4 | Backend thông báo + giới hạn: `NEW_MESSAGE`, sự kiện, gộp, email có liên kết, R-R3, nhóm `message` của `RateLimitFilter`; T18–T21, T23 | test-compile; `-Dtest` lớp thông báo + `RateLimitFilterTest` |
| 5 | Frontend tab: `features/messages`, `lib/fileSize.ts`, `lib/useStallGuardedPolling.ts`, tab "Trao đổi" ở hai trang, loại thông báo mới | build + lint; lệnh tìm 7.3 |
| 6 | Frontend hộp thư: hai trang, hai route, mục điều hướng hai phía | build + lint; lệnh tìm 7.3 |
| 7 | Đợt cuối: seed (R-S), full `.\mvnw.cmd test` (một lần), khối PowerShell 7.2 (dán output), `srs-guard`, `walkthrough`, trạng thái `ĐÃ HOÀN THÀNH`, SRS mục 0, ROADMAP, CLAUDE.md §3 (thêm `messaging/`), UI_GUIDE mục 7 (D5–D8 mục 10) | full suite; 7.2; 7.3 |

## 10. Tài liệu dùng chung sửa

**Trong commit đặc tả** (cùng lúc đổi hai file này sang `ĐÃ DUYỆT`), tìm theo nội dung câu cũ (D1–D4):

| # | File:dòng | Câu cũ (nguyên văn) | Câu mới |
|---|---|---|---|
| D1 | `docs/features/README.md:365` | `- Đính kèm dùng lại cơ chế lưu file và kiểm định dạng bằng magic bytes của FR-U01 (PDF, DOCX, ảnh; có giới hạn dung lượng). File chỉ tải được khi đã đăng nhập và có quyền trên cuộc trao đổi.` | `- Đính kèm (mỗi tin tối đa một tệp, 5MB) dùng lại cơ chế lưu file và kiểm định dạng bằng magic bytes của FR-U01 và FR-H01 (PDF, DOCX, PNG, JPEG, WEBP). File chỉ tải được khi đã đăng nhập và có quyền trên cuộc trao đổi.` |
| D2 | `docs/features/README.md:366` | `- Mỗi tin mới phát thông báo cho bên nhận qua FR-C03 (web + email). Email chứa nội dung tin và liên kết, không đính kèm file.` | `- Tin mới phát thông báo cho bên nhận qua FR-C03 (web + email), gộp lại khi bên nhận còn thông báo tin nhắn chưa đọc của cùng đơn. Email chứa đoạn trích nội dung tin và liên kết, không đính kèm file.` |
| D3 | `docs/features/README.md:927` | `\| Kênh email nhanh tách riêng (HR 2, UV 2) \| Email là tin nhắn trong cuộc trao đổi, được gửi thêm qua email bằng hạ tầng thông báo FR-C03 (C06, C07). \|` | `\| Kênh email nhanh tách riêng (HR 2, UV 2) \| Không có kênh email riêng: tin nhắn nằm trong cuộc trao đổi; bên nhận được báo qua thông báo FR-C03, email chỉ chứa đoạn trích và liên kết (C06, C07). \|` |
| D4 | `docs/SRS.md` mục 4, cuối bảng "Chức năng hiện có bị ảnh hưởng" (sau dòng FR-U04, không có câu cũ) | — | Thêm 2 dòng: `\| FR-U01 \| FR-C06 \| Nhận dạng loại tệp chuyển sang bộ dùng chung; hành vi không đổi \|` và `\| FR-H01 \| FR-C06 \| Nhận dạng loại tệp logo chuyển sang bộ dùng chung; hành vi không đổi \|` |

**Ở đợt code cuối** (đợt 7):

| # | File:dòng | Thay đổi |
|---|---|---|
| D5 | `docs/UI_GUIDE.md:341` | Mô tả màn hình thêm tab `"Trao đổi"` sau `"Lịch sử"`; cột FR giữ nguyên |
| D6 | `docs/UI_GUIDE.md:357` | Mô tả màn hình thêm tab `"Trao đổi"` sau `"Lịch sử"`; cột FR thêm `; FR-C06 (trao đổi)`; đoạn ghi chú dưới bảng bỏ `Trao đổi (FR-C06)` khỏi danh sách "các FR sau tự thêm" |
| D7 | `CLAUDE.md:64-67` | Thêm `messaging/` vào danh sách package |
| D8 | `docs/SRS.md:26` | `Chưa đặc tả` → `Đã hoàn thành` |

Dấu ★ ở cột "Hiện có / ★Mới" của `docs/UI_GUIDE.md` **không đổi** ở bất kỳ dòng nào: hai dòng hộp thư (342,
359) giữ ★Mới sau khi hoàn thành, đúng như hai dòng trang đơn (341, 357) của FR-H09/FR-U08 đã xong vẫn giữ ★Mới.

## 11. Điểm cần người duyệt chốt

Bản nháp đã chọn sẵn một phương án cho từng điểm; duyệt nguyên trạng nghĩa là đồng ý cả tám.

| # | Đã chọn | Đánh đổi |
|---|---|---|
| Q1 | Không có bảng cuộc trao đổi; khoá là `applicationId` (R-P1) | Đơn giản, không có race "một đơn một cuộc". Sau này muốn trao đổi không gắn đơn thì phải thêm bảng |
| Q2 | Một tệp mỗi tin, 5MB, 5 loại (R-F1–R-F3) | Gửi nhiều tệp phải gửi nhiều tin; đổi lại không cần bảng tệp riêng và không đụng giới hạn multipart 10MB |
| Q3 | Email chứa **đoạn trích** 120 ký tự + liên kết, không chứa toàn bộ tin (R-N2, R-N5) | Lệch câu chữ `README.md:366` ("chứa nội dung tin") — sửa ở D2. Lý do: `notifications.body` hiện nguyên văn trong chuông |
| Q4 | Gộp thông báo: còn thông báo tin nhắn chưa đọc của đơn thì không báo thêm; kiểm ở service, không unique index (R-N4, R-N4b) | Bên nhận **chưa mở** cuộc trao đổi thì cả loạt tin chỉ một email. Hai bên cùng mở tab thì vẫn khoảng một email mỗi tin. Ngoại lệ có chủ đích với CLAUDE.md §4; chốt ở DB sẽ phải sửa bảng `notifications` |
| Q5 | Không hiện số tin chưa đọc trên thanh điều hướng và nhãn tab (mục 6) | Người dùng dựa vào chuông và hộp thư; đổi lại không có thêm lượt tải lại định kỳ trên mọi trang |
| Q6 | Gom nhận dạng tệp của FR-U01/FR-H01 về `FileSignatures` (R-C1) | Chạm code hai FR đã xong; cổng an toàn là toàn bộ test cũ pass không sửa |
| Q7 | Full suite backend chỉ chạy một lần ở đợt 7; đợt 3 và 4 chạy mọi lớp test có sẵn của phần bị chạm (mục 9) | Tiết kiệm thời gian/token; lỗi chéo ngoài các lớp đó chỉ lộ ở đợt 7 và phải sửa dồn |
| Q8 | Ép đuôi tên tệp tải về theo loại đã nhận dạng; tệp ZIP vẫn lọt với nhãn DOCX (R-F6, R-F6b) | Tên tệp có thể thành `x.html.pdf`; đổi lại không ai tải về được tệp có đuôi lệch nội dung. Không kiểm cấu trúc bên trong ZIP |

## 12. Làm rõ sau Plan Mode (duyệt 10/10/2026)

- **L1 (mục 9).** Đợt 2 chạy T1, T5, T7, T8, T15, T24, phần M1–M3 của T2, T3, T4 và phần tin chữ của T9.
  Đợt 3 chạy T10–T14, T16, T17, T22, T25, T6 trọn vẹn (M1, M2, M5), phần M4 của T2, T3, phần M5 của T4 và
  phần tệp của T9. Đợt 4 chạy T18–T21, T23, T26.
- **L2 (R-R3, mục 4.2).** M3 đánh dấu thông báo đã đọc bằng cách publish `ConversationReadEvent` (đặt ở
  `notification/`). Listener là `@EventListener` thường, chạy đồng bộ trong **cùng** transaction của M3, không
  `REQUIRES_NEW`, không nuốt lỗi. `messaging/` vẫn chỉ import `notification/` để publish sự kiện.
- **L3 (đợt 2).** Tham số `file` được khai sẵn ở M2; trong đợt 2, tệp không rỗng tạm trả 400. Không test nào
  khẳng định hành vi tạm này; đợt 3 xoá hẳn nó.
- **L4 (R-F2, R-F3).** Thứ tự kiểm tệp: 0 byte → quá 5MB → sai loại. Không đọc được tệp (`IOException`) → 400
  `INVALID_MESSAGE_ATTACHMENT` "Không đọc được tệp đính kèm".
- **L5 (R-N5).** `application.yml` khai `app.frontend-base-url: ${FRONTEND_BASE_URL:http://localhost:5173}`;
  `application-test.yml` khai cứng `http://localhost:5173` để T21 không phụ thuộc biến môi trường của máy.
- **L6 (R-I1, R-F6).** Hai đơn trùng thời điểm tin mới nhất xếp theo `applicationId` giảm dần. "Ký tự điều
  khiển" là mọi ký tự có `Character.isISOControl`.
- **L7 (T26).** Dùng `@MockitoSpyBean`; chấp nhận lớp test này tạo một Spring context riêng.
- **L8 (R-S1).** Hai tin của A3 đặt vào 12/08/2026 và 13/08/2026, trước ngày A3 rút đơn (14/08/2026), vì
  backend chặn gửi tin vào đơn đã rút (R-M4); mốc "A3 ngày 05/10/2026" ở R-S1 bị thay bằng mốc này. Thứ tự
  hộp thư không đổi: HR xếp A9, A2, A3; Quốc Huy xếp A2, A3.
